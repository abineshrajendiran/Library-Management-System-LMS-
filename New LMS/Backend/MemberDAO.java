package lms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

public class MemberDAO {

    public List<Map<String, Object>> list() throws SQLException {
        String sql = "SELECT m.member_id, m.username, m.full_name, m.email, m.department, m.created_at, "
                + "(SELECT COUNT(*) FROM issue_records i WHERE i.member_id = m.member_id AND i.status = 'ISSUED') AS books_held "
                + "FROM members m ORDER BY m.member_id";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return Db.rows(rs);
        }
    }

    public void add(String username, String password, String fullName, String email, String department)
            throws SQLException {
        String sql = "INSERT INTO members (username, password_hash, full_name, email, department) VALUES (?,?,?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, Db.sha256(password));
            ps.setString(3, fullName);
            ps.setString(4, email);
            ps.setString(5, department);
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ApiException(409, "Username '" + username + "' already exists.");
        }
    }
}
