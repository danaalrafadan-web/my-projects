package attendance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** طبقة الوصول إلى قاعدة بيانات Oracle عبر JDBC. */
public class Db {
    public record Employee(String id, String name, String role, String dept, int deptId, int shiftId, boolean active) {}
    public record Shift(int id, String name, String start, String breakTime, int breakMinutes, String end) {}
    public record Dept(int id, String name) {}
    public record Punch(String action, LocalTime time) {}
    public record Daily(String empId, String name, String dept, String shiftStart, String shiftEnd,
                        LocalTime in, LocalTime breakStart, LocalTime breakEnd,
                        LocalTime out, LocalTime otStart, LocalTime otEnd) {}

    private final String url  = env("ORACLE_URL",  "jdbc:oracle:thin:@localhost:1521/XEPDB1");
    private final String user = env("ORACLE_USER", "attendance");
    private final String pass = env("ORACLE_PASS", "attendance");

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    // ---------- الموظفون ----------

    private static final String EMP_SELECT =
        "SELECT e.emp_id, e.full_name, e.role, d.name, NVL(e.dept_id,0), NVL(e.shift_id,0), e.active " +
        "FROM employees e LEFT JOIN departments d ON d.dept_id = e.dept_id ";

    private static Employee emp(ResultSet r) throws SQLException {
        return new Employee(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                            r.getInt(5), r.getInt(6), "Y".equals(r.getString(7)));
    }

    public Employee login(String empId, String pin) throws SQLException {
        if (empId == null || pin == null) return null;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(EMP_SELECT + "WHERE e.emp_id = ? AND e.pin_hash = ? AND e.active = 'Y'")) {
            ps.setString(1, empId.trim());
            ps.setString(2, sha256(pin));
            try (ResultSet r = ps.executeQuery()) { return r.next() ? emp(r) : null; }
        }
    }

    public List<Employee> employees() throws SQLException {
        List<Employee> out = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery(EMP_SELECT + "ORDER BY e.emp_id")) {
            while (r.next()) out.add(emp(r));
        }
        return out;
    }

    public void addEmployee(String id, String name, int deptId, int shiftId, String role, String pin) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employees (emp_id, full_name, dept_id, shift_id, role, pin_hash) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id); ps.setString(2, name); ps.setInt(3, deptId); ps.setInt(4, shiftId);
            ps.setString(5, "ADMIN".equals(role) ? "ADMIN" : "EMP"); ps.setString(6, sha256(pin));
            ps.executeUpdate();
        }
    }

    public void setActive(String id, boolean active) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE employees SET active = ? WHERE emp_id = ?")) {
            ps.setString(1, active ? "Y" : "N"); ps.setString(2, id); ps.executeUpdate();
        }
    }

    // ---------- الأقسام والدوام ----------

    public List<Dept> departments() throws SQLException {
        List<Dept> out = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT dept_id, name FROM departments ORDER BY dept_id")) {
            while (r.next()) out.add(new Dept(r.getInt(1), r.getString(2)));
        }
        return out;
    }

    private static Shift shift(ResultSet r) throws SQLException {
        return new Shift(r.getInt(1), r.getString(2), r.getString(3).trim(), r.getString(4).trim(), r.getInt(5), r.getString(6).trim());
    }

    public List<Shift> shifts() throws SQLException {
        List<Shift> out = new ArrayList<>();
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet r = st.executeQuery(
                "SELECT shift_id, name, start_time, break_time, break_minutes, end_time FROM shifts ORDER BY shift_id")) {
            while (r.next()) out.add(shift(r));
        }
        return out;
    }

    public Shift shiftOf(String empId) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.shift_id, s.name, s.start_time, s.break_time, s.break_minutes, s.end_time " +
                "FROM shifts s JOIN employees e ON e.shift_id = s.shift_id WHERE e.emp_id = ?")) {
            ps.setString(1, empId);
            try (ResultSet r = ps.executeQuery()) {
                return r.next() ? shift(r) : new Shift(0, "افتراضي", "08:00", "11:00", 30, "15:00");
            }
        }
    }

    public void updateShift(Shift s) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE shifts SET name=?, start_time=?, break_time=?, break_minutes=?, end_time=? WHERE shift_id=?")) {
            ps.setString(1, s.name()); ps.setString(2, s.start()); ps.setString(3, s.breakTime());
            ps.setInt(4, s.breakMinutes()); ps.setString(5, s.end()); ps.setInt(6, s.id());
            ps.executeUpdate();
        }
    }

    // ---------- البصمة ----------

    public List<Punch> punches(String empId, LocalDate date) throws SQLException {
        List<Punch> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT action, action_time FROM attendance WHERE emp_id = ? AND att_date = ? ORDER BY action_time")) {
            ps.setString(1, empId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new Punch(r.getString(1), time(r, 2)));
            }
        }
        return out;
    }

    public void insertPunch(String empId, String action, String device, LocalDateTime when) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO attendance (emp_id, att_date, action, action_time, device) VALUES (?,?,?,?,?)")) {
            ps.setString(1, empId);
            ps.setDate(2, Date.valueOf(when.toLocalDate()));
            ps.setString(3, action);
            ps.setTimestamp(4, Timestamp.valueOf(when));
            ps.setString(5, device);
            ps.executeUpdate();
        }
    }

    /** ملخص يوم كامل لكل الموظفين (يشمل الغائبين). */
    public List<Daily> dailySummary(LocalDate date) throws SQLException {
        String sql =
            "SELECT e.emp_id, e.full_name, d.name, s.start_time, s.end_time, " +
            " MAX(CASE WHEN a.action='IN'          THEN a.action_time END), " +
            " MAX(CASE WHEN a.action='BREAK_START' THEN a.action_time END), " +
            " MAX(CASE WHEN a.action='BREAK_END'   THEN a.action_time END), " +
            " MAX(CASE WHEN a.action='OUT'         THEN a.action_time END), " +
            " MAX(CASE WHEN a.action='OT_START'    THEN a.action_time END), " +
            " MAX(CASE WHEN a.action='OT_END'      THEN a.action_time END) " +
            "FROM employees e " +
            "LEFT JOIN departments d ON d.dept_id = e.dept_id " +
            "LEFT JOIN shifts s ON s.shift_id = e.shift_id " +
            "LEFT JOIN attendance a ON a.emp_id = e.emp_id AND a.att_date = ? " +
            "WHERE e.active = 'Y' AND e.role = 'EMP' " +
            "GROUP BY e.emp_id, e.full_name, d.name, s.start_time, s.end_time ORDER BY e.emp_id";
        List<Daily> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new Daily(r.getString(1), r.getString(2), r.getString(3),
                        trim(r.getString(4)), trim(r.getString(5)),
                        time(r, 6), time(r, 7), time(r, 8), time(r, 9), time(r, 10), time(r, 11)));
            }
        }
        return out;
    }

    // ---------- أدوات ----------

    private static String trim(String s) { return s == null ? "08:00" : s.trim(); }

    private static LocalTime time(ResultSet r, int col) throws SQLException {
        Timestamp t = r.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toLocalTime().withNano(0);
    }

    public static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
