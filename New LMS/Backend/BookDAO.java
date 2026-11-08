package lms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class BookDAO {

    /** Search by title / author / category. Empty text returns every book. */
    public List<Map<String, Object>> search(String text) throws SQLException {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        String sql = "SELECT book_id, title, author, category, total_copies, available_copies "
                + "FROM books WHERE title LIKE ? OR author LIKE ? OR category LIKE ? "
                + "ORDER BY category, title";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                return Db.rows(rs);
            }
        }
    }

    public void add(String title, String author, String category, int copies) throws SQLException {
        String sql = "INSERT INTO books (title, author, category, total_copies, available_copies) VALUES (?,?,?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, category);
            ps.setInt(4, copies);
            ps.setInt(5, copies);
            ps.executeUpdate();
        }
    }
}
