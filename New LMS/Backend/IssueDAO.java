package lms;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Issue book / return book / list records.  Everything is saved in the issue_records table. */
public class IssueDAO {

    public static final int LOAN_DAYS = 7;          // free days
    public static final int FINE_PER_DAY = 2;       // Rs. per day after LOAN_DAYS
    public static final int MAX_BOOKS_PER_MEMBER = 3;

    // ------------------------------------------------------------------ ISSUE
    public Map<String, Object> issueBook(int bookId, int memberId) throws SQLException {
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. member must exist
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM members WHERE member_id = ?")) {
                    ps.setInt(1, memberId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ApiException(404, "Student not found.");
                    }
                }

                // 2. member limit
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM issue_records WHERE member_id = ? AND status = 'ISSUED'")) {
                    ps.setInt(1, memberId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) >= MAX_BOOKS_PER_MEMBER) {
                            throw new ApiException(409, "Limit reached: a student can hold only "
                                    + MAX_BOOKS_PER_MEMBER + " books at a time.");
                        }
                    }
                }

                // 3. same book not already held by this member
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM issue_records WHERE member_id = ? AND book_id = ? AND status = 'ISSUED'")) {
                    ps.setInt(1, memberId);
                    ps.setInt(2, bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new ApiException(409, "This student already has this book.");
                    }
                }

                // 4. take one copy (only succeeds when a copy is available)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE books SET available_copies = available_copies - 1 "
                                + "WHERE book_id = ? AND available_copies > 0")) {
                    ps.setInt(1, bookId);
                    if (ps.executeUpdate() == 0) {
                        throw new ApiException(409, "Book is not available (all copies issued) or does not exist.");
                    }
                }

                // 5. save the issue record
                LocalDate today = LocalDate.now();
                LocalDate due = today.plusDays(LOAN_DAYS);
                int issueId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO issue_records (book_id, member_id, issue_date, due_date, status) "
                                + "VALUES (?, ?, ?, ?, 'ISSUED')", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, bookId);
                    ps.setInt(2, memberId);
                    ps.setDate(3, Date.valueOf(today));
                    ps.setDate(4, Date.valueOf(due));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        issueId = keys.getInt(1);
                    }
                }

                c.commit();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("issue_id", issueId);
                result.put("issue_date", today.toString());
                result.put("due_date", due.toString());
                result.put("message", "Book issued. Due on " + due + " (fine Rs. " + FINE_PER_DAY
                        + " per day after " + LOAN_DAYS + " days).");
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ----------------------------------------------------------------- RETURN
    /**
     * @param issueId          the record to close
     * @param daysKeptOverride demo option (staff only): pretend the book was kept this many days
     * @param onlyMemberId     when not null, the record must belong to this member
     */
    public Map<String, Object> returnBook(int issueId, Integer daysKeptOverride, Integer onlyMemberId)
            throws SQLException {
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try {
                int bookId;
                LocalDate issueDate;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT book_id, member_id, issue_date, status FROM issue_records "
                                + "WHERE issue_id = ? FOR UPDATE")) {
                    ps.setInt(1, issueId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ApiException(404, "Issue record not found.");
                        if (onlyMemberId != null && rs.getInt("member_id") != onlyMemberId) {
                            throw new ApiException(403, "This record belongs to another student.");
                        }
                        if (!"ISSUED".equals(rs.getString("status"))) {
                            throw new ApiException(409, "This book was already returned.");
                        }
                        bookId = rs.getInt("book_id");
                        issueDate = rs.getDate("issue_date").toLocalDate();
                    }
                }

                LocalDate returnDate = (daysKeptOverride != null)
                        ? issueDate.plusDays(daysKeptOverride)
                        : LocalDate.now();
                long daysKept = ChronoUnit.DAYS.between(issueDate, returnDate);
                long lateDays = Math.max(0, daysKept - LOAN_DAYS);
                long fine = lateDays * FINE_PER_DAY;

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE issue_records SET return_date = ?, fine_amount = ?, status = 'RETURNED' "
                                + "WHERE issue_id = ?")) {
                    ps.setDate(1, Date.valueOf(returnDate));
                    ps.setBigDecimal(2, java.math.BigDecimal.valueOf(fine));
                    ps.setInt(3, issueId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE books SET available_copies = available_copies + 1 WHERE book_id = ?")) {
                    ps.setInt(1, bookId);
                    ps.executeUpdate();
                }

                c.commit();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("issue_id", issueId);
                result.put("days_kept", daysKept);
                result.put("late_days", lateDays);
                result.put("fine", fine);
                result.put("message", fine == 0
                        ? "Book returned on time (" + daysKept + " days). No fine."
                        : "Book returned after " + daysKept + " days. Fine = " + lateDays + " x Rs. "
                                + FINE_PER_DAY + " = Rs. " + fine);
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------- LIST
    /**
     * @param memberId null = every member (staff view)
     * @param status   "ISSUED", "RETURNED" or "ALL"
     */
    public List<Map<String, Object>> list(Integer memberId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT i.issue_id, i.book_id, b.title, b.author, i.member_id, m.full_name AS member_name, "
                        + "i.issue_date, i.due_date, i.return_date, i.status, "
                        + "DATEDIFF(COALESCE(i.return_date, CURDATE()), i.issue_date) AS days_kept, "
                        + "CASE WHEN i.status = 'ISSUED' "
                        + "THEN GREATEST(0, DATEDIFF(CURDATE(), i.issue_date) - " + LOAN_DAYS + ") * " + FINE_PER_DAY
                        + " ELSE i.fine_amount END AS fine "
                        + "FROM issue_records i "
                        + "JOIN books b ON b.book_id = i.book_id "
                        + "JOIN members m ON m.member_id = i.member_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (memberId != null) {
            sql.append("AND i.member_id = ? ");
            params.add(memberId);
        }
        if ("ISSUED".equals(status) || "RETURNED".equals(status)) {
            sql.append("AND i.status = ? ");
            params.add(status);
        }
        sql.append("ORDER BY i.issue_id DESC");

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return Db.rows(rs);
            }
        }
    }
}
