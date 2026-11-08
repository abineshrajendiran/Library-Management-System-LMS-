package lms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Login for the two modules: staff and member. */
public class AuthDAO {

    /** Returns {id, name, role} when the login is correct, otherwise null. */
    public Map<String, Object> login(String role, String username, String password) throws SQLException {
        String sql;
        if ("staff".equals(role)) {
            sql = "SELECT staff_id AS id, full_name FROM staff WHERE username = ? AND password_hash = ?";
        } else if ("member".equals(role)) {
            sql = "SELECT member_id AS id, full_name FROM members WHERE username = ? AND password_hash = ?";
        } else {
            return null;
        }
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, Db.sha256(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("name", rs.getString("full_name"));
                user.put("role", role);
                return user;
            }
        }
    }
}
