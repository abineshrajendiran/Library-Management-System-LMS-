package lms;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** JDBC connection + small helpers shared by all DAO classes. */
public class Db {

    /** Project folder = the folder that contains config.properties and frontend/. */
    public static final Path BASE_DIR = findBaseDir();

    private static final Properties PROPS = new Properties();

    private static Driver mysqlDriver;

    static {
        Path file = BASE_DIR.resolve("config.properties");
        try (InputStream in = Files.newInputStream(file)) {
            PROPS.load(in);
            System.out.println("[OK] Settings loaded from: " + file);
        } catch (IOException e) {
            System.out.println("[INFO] config.properties not found in: " + BASE_DIR);
            System.out.println("       Using environment variables / default settings.");
        }
    }

    /**
     * Eclipse / VS Code / command line may start the program from different folders.
     * So we look in the working folder, next to the compiled classes (bin/ or out/),
     * and also inside a sub-folder named "lms".
     */
    private static Path findBaseDir() {
        List<Path> candidates = new ArrayList<>();
        Path work = Paths.get("").toAbsolutePath();
        candidates.add(work);
        if (work.getParent() != null) candidates.add(work.getParent());
        try {
            Path classes = Paths.get(Db.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(classes) ? classes : classes.getParent();
            if (dir != null) {
                candidates.add(dir);
                if (dir.getParent() != null) {
                    candidates.add(dir.getParent());
                    if (dir.getParent().getParent() != null) candidates.add(dir.getParent().getParent());
                }
            }
        } catch (Exception ignored) {
            // fall back to the working folder
        }

        // 1) folder that has config.properties (directly, or inside "lms")
        for (Path c : candidates) {
            if (Files.exists(c.resolve("config.properties"))) return c;
            if (Files.exists(c.resolve("lms").resolve("config.properties"))) return c.resolve("lms");
        }
        // 2) folder that has frontend (directly, or inside "lms")
        for (Path c : candidates) {
            if (Files.isDirectory(c.resolve("frontend"))) return c;
            if (Files.isDirectory(c.resolve("lms").resolve("frontend"))) return c.resolve("lms");
        }
        return candidates.get(0);
    }

    /**
     * Loads the MySQL driver.
     * First tries the normal classpath. If the JAR is not on the classpath,
     * it loads every .jar from the "lib" folder inside the project.
     */
    private static synchronized Driver loadDriver() throws SQLException {
        if (mysqlDriver != null) return mysqlDriver;

        // Way 1: JAR is on the classpath
        try {
            mysqlDriver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver")
                    .getDeclaredConstructor().newInstance();
            return mysqlDriver;
        } catch (Exception ignored) {
            // try the lib folder next
        }

        // Way 2: load the JAR from the lib folder
        Path lib = BASE_DIR.resolve("lib");
        List<URL> urls = new ArrayList<>();
        if (Files.isDirectory(lib)) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(lib, "*.jar")) {
                for (Path jar : jars) {
                    urls.add(jar.toUri().toURL());
                }
            } catch (IOException e) {
                throw new SQLException("Cannot read lib folder: " + lib, e);
            }
        }
        if (urls.isEmpty()) {
            throw new SQLException("MySQL driver not found. Put mysql-connector-j JAR in: " + lib
                    + "  (or add it in Eclipse: Build Path > Add External JARs)");
        }
        try {
            URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), Db.class.getClassLoader());
            mysqlDriver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, loader)
                    .getDeclaredConstructor().newInstance();
            return mysqlDriver;
        } catch (Exception e) {
            throw new SQLException("Could not load MySQL driver from " + lib + " : " + e, e);
        }
    }

    /**
     * Reads a setting. Order: 1) environment variable (db.url -> DB_URL),
     * 2) config.properties, 3) the default value.
     */
    public static String get(String key, String defaultValue) {
        String envName = key.toUpperCase().replace('.', '_');
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv;
        }
        return PROPS.getProperty(key, defaultValue);
    }

    public static Connection getConnection() throws SQLException {
        String url = get("db.url",
                "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata");
        String user = get("db.user", "root");
        String password = get("db.password", "");

        Properties info = new Properties();
        info.setProperty("user", user);
        info.setProperty("password", password);

        Connection con = loadDriver().connect(url, info);
        if (con == null) {
            throw new SQLException("Driver did not accept this URL: " + url);
        }
        return con;
    }

    /** Converts every row of a ResultSet into a Map (column name -> value). */
    public static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    /** SHA-256 hex of a password (same as MySQL SHA2(text,256)). */
    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}