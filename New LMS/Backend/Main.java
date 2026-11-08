package lms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web server + REST API.
 *   Browser (HTML/CSS/JS)  --fetch-->  this server  --JDBC-->  MySQL tables
 */
public class Main {

    private static final Map<String, Map<String, Object>> SESSIONS = new ConcurrentHashMap<>();
    private static final Path FRONTEND = Db.BASE_DIR.resolve("frontend").toAbsolutePath().normalize();

    private static final AuthDAO authDAO = new AuthDAO();
    private static final BookDAO bookDAO = new BookDAO();
    private static final MemberDAO memberDAO = new MemberDAO();
    private static final IssueDAO issueDAO = new IssueDAO();

    public static void main(String[] args) throws Exception {
        // On Render (cloud) the PORT environment variable is set. On your PC it is not,
        // so the port comes from config.properties (server.port).
        String cloudPort = System.getenv("PORT");
        boolean cloud = cloudPort != null && !cloudPort.trim().isEmpty();
        int wantedPort = cloud
                ? Integer.parseInt(cloudPort.trim())
                : Integer.parseInt(Db.get("server.port", "8080"));

        // On your PC: if the port is busy (an old copy is still running), try the next ports.
        // In the cloud: use exactly the port given by the host.
        HttpServer server = null;
        int port = wantedPort;
        int tries = cloud ? 1 : 10;
        for (int i = 0; i < tries && server == null; i++) {
            port = wantedPort + i;
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            } catch (java.net.BindException e) {
                System.out.println("[WARN] Port " + port + " is busy.");
            }
        }
        if (server == null) {
            System.out.println("[ERROR] No free port starting from " + wantedPort + ".");
            System.out.println("        Stop old servers (red Terminate button in Eclipse) and run again.");
            return;
        }
        if (port != wantedPort) {
            System.out.println("[WARN] An old server is probably still running on port " + wantedPort + ".");
            System.out.println("       This new server uses port " + port + " - open http://localhost:" + port);
        }
        if (!Files.isDirectory(FRONTEND)) {
            System.out.println("[WARN] frontend folder not found at: " + FRONTEND);
        }
        server.createContext("/api/", Main::handleApi);
        server.createContext("/", Main::handleStatic);
        server.start();

        System.out.println("==============================================");
        System.out.println(" Library Management System is running");
        if (cloud) {
            System.out.println(" Running in the cloud on port " + port);
        } else {
            System.out.println(" Open in browser:  http://localhost:" + port);
        }
        System.out.println(" Press Ctrl+C to stop");
        System.out.println("==============================================");

        // Check the database connection at start-up so problems show immediately
        try (java.sql.Connection c = Db.getConnection()) {
            System.out.println("[OK] Connected to MySQL database.");
        } catch (SQLException e) {
            System.out.println("[ERROR] Cannot connect to MySQL: " + e.getMessage());
            System.out.println("        Check db.url / db.user / db.password in config.properties");
            System.out.println("        (or DB_URL / DB_USER / DB_PASSWORD environment variables)");
        }
    }

    // =================================================================== API
    private static void handleApi(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            send(ex, 204, "");
            return;
        }
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            Map<String, String> body = "POST".equals(method)
                    ? Json.parseObject(readBody(ex)) : Collections.<String, String>emptyMap();

            Object result;
            switch (method + " " + path) {
                case "POST /api/login":    result = login(body); break;
                case "GET /api/me":        result = auth(ex); break;
                case "GET /api/books":     auth(ex); result = bookDAO.search(query.get("q")); break;
                case "POST /api/books":    result = addBook(ex, body); break;
                case "GET /api/members":   requireStaff(ex); result = memberDAO.list(); break;
                case "POST /api/members":  result = addMember(ex, body); break;
                case "POST /api/issue":    result = issue(ex, body); break;
                case "POST /api/return":   result = returnBook(ex, body); break;
                case "GET /api/issues":    result = issues(ex, query); break;
                default: throw new ApiException(404, "Unknown API: " + method + " " + path);
            }
            send(ex, 200, Json.toJson(result));

        } catch (ApiException e) {
            send(ex, e.status, Json.toJson(error(e.getMessage())));
        } catch (IllegalArgumentException e) {
            send(ex, 400, Json.toJson(error(e.getMessage())));
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, Json.toJson(error("Database error: " + e.getMessage())));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, Json.toJson(error("Server error: " + e.getMessage())));
        }
    }

    // ------------------------------------------------------------ endpoints
    private static Object login(Map<String, String> b) throws SQLException {
        String role = need(b, "role");
        Map<String, Object> user = authDAO.login(role, need(b, "username"), need(b, "password"));
        if (user == null) throw new ApiException(401, "Wrong username or password.");
        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, user);
        Map<String, Object> out = new LinkedHashMap<>(user);
        out.put("token", token);
        return out;
    }

    private static Object addBook(HttpExchange ex, Map<String, String> b) throws SQLException {
        requireStaff(ex);
        int copies = needInt(b, "copies");
        if (copies < 1 || copies > 100) throw new ApiException(400, "Copies must be 1 to 100.");
        bookDAO.add(need(b, "title"), need(b, "author"), need(b, "category"), copies);
        return message("Book added.");
    }

    private static Object addMember(HttpExchange ex, Map<String, String> b) throws SQLException {
        requireStaff(ex);
        if (need(b, "password").length() < 4) throw new ApiException(400, "Password must be at least 4 characters.");
        memberDAO.add(need(b, "username"), need(b, "password"), need(b, "full_name"),
                b.get("email"), b.get("department"));
        return message("Student added.");
    }

    private static Object issue(HttpExchange ex, Map<String, String> b) throws SQLException {
        Map<String, Object> user = auth(ex);
        int bookId = needInt(b, "book_id");
        int memberId;
        if ("staff".equals(user.get("role"))) {
            memberId = needInt(b, "member_id");          // staff chooses the member
        } else {
            memberId = (Integer) user.get("id");         // member can only borrow for self
        }
        return issueDAO.issueBook(bookId, memberId);
    }

    private static Object returnBook(HttpExchange ex, Map<String, String> b) throws SQLException {
        Map<String, Object> user = auth(ex);
        int issueId = needInt(b, "issue_id");
        boolean staff = "staff".equals(user.get("role"));
        Integer override = null;
        if (staff && b.get("days_kept") != null && !b.get("days_kept").trim().isEmpty()) {
            override = needInt(b, "days_kept");
            if (override < 0 || override > 3650) throw new ApiException(400, "Days kept must be 0 to 3650.");
        }
        return issueDAO.returnBook(issueId, override, staff ? null : (Integer) user.get("id"));
    }

    private static Object issues(HttpExchange ex, Map<String, String> q) throws SQLException {
        Map<String, Object> user = auth(ex);
        Integer memberId = "staff".equals(user.get("role")) ? null : (Integer) user.get("id");
        String status = q.getOrDefault("status", "ALL").toUpperCase();
        List<Map<String, Object>> rows = issueDAO.list(memberId, status);
        return rows;
    }

    // ------------------------------------------------------------- security
    private static Map<String, Object> auth(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            Map<String, Object> user = SESSIONS.get(h.substring(7));
            if (user != null) return user;
        }
        throw new ApiException(401, "Please login first.");
    }

    private static void requireStaff(HttpExchange ex) {
        if (!"staff".equals(auth(ex).get("role"))) {
            throw new ApiException(403, "Only staff can do this.");
        }
    }

    // -------------------------------------------------------------- helpers
    private static String need(Map<String, String> b, String key) {
        String v = b.get(key);
        if (v == null || v.trim().isEmpty()) throw new ApiException(400, "Missing field: " + key);
        return v.trim();
    }

    private static int needInt(Map<String, String> b, String key) {
        try {
            return Integer.parseInt(need(b, key));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Field '" + key + "' must be a number.");
        }
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }

    private static Map<String, Object> message(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", msg);
        return m;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            try {
                String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), "UTF-8");
                String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), "UTF-8");
                map.put(k, v);
            } catch (IOException ignored) {
            }
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, status == 204 ? -1 : data.length);
        if (status != 204) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }
        ex.close();
    }

    // ========================================================== static files
    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file = FRONTEND.resolve(path.substring(1)).normalize();
        if (!file.startsWith(FRONTEND) || !Files.isRegularFile(file)) {
            byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
            ex.close();
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "application/octet-stream";
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
        ex.close();
    }
}