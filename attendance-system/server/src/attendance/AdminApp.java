package attendance;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * لوحة تحكم المدير - تطبيق سطح مكتب بواجهة رسومية (Java Swing).
 * يُشغَّل:  java -cp "out;lib/ojdbc11.jar" attendance.AdminApp
 */
public class AdminApp extends JFrame {
    private static final Color NAVY = new Color(0x14284b);
    private static final Font FONT = new Font("Tahoma", Font.PLAIN, 14);

    private final Db db = new Db();
    private final JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
    private final DefaultTableModel dailyModel = model("الرقم", "الاسم", "القسم", "الدوام", "الحضور", "الاستراحة", "الانصراف", "الحالة", "التأخير (د)", "إضافي (د)");
    private final DefaultTableModel empModel = model("الرقم", "الاسم", "القسم", "الصلاحية", "رقم الدوام", "نشط");
    private final DefaultTableModel shiftModel = new DefaultTableModel(new Object[]{"الرقم", "الاسم", "البداية", "الاستراحة", "مدة الاستراحة", "النهاية"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c > 0; }
    };
    private final JLabel stats = new JLabel(" ");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            UIManager.put("defaultFont", FONT);
            if (login()) new AdminApp().setVisible(true);
            else System.exit(0);
        });
    }

    /** دخول المدير بالرقم الوظيفي والرمز السري من قاعدة البيانات. */
    private static boolean login() {
        JTextField id = new JTextField();
        JPasswordField pin = new JPasswordField();
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        p.add(new JLabel("الرقم الوظيفي")); p.add(id);
        p.add(new JLabel("الرمز السري"));   p.add(pin);
        p.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        while (true) {
            int r = JOptionPane.showConfirmDialog(null, p, "دخول المدير", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;
            try {
                Db.Employee e = new Db().login(id.getText(), new String(pin.getPassword()));
                if (e != null && "ADMIN".equals(e.role())) return true;
                JOptionPane.showMessageDialog(null, "بيانات غير صحيحة أو الحساب ليس مديراً");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "تعذر الاتصال بقاعدة البيانات:\n" + ex.getMessage());
                return false;
            }
        }
    }

    public AdminApp() {
        super("لوحة تحكم الحضور والانصراف");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 650);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("  نظام الحضور والانصراف - لوحة المدير");
        title.setOpaque(true); title.setBackground(NAVY); title.setForeground(Color.WHITE);
        title.setFont(FONT.deriveFont(Font.BOLD, 18f));
        title.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("الحضور اليومي", dailyTab());
        tabs.addTab("الموظفون", employeesTab());
        tabs.addTab("أوقات الدوام", shiftsTab());

        add(title, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        loadDaily(); loadEmployees(); loadShifts();
    }

    // ---------------- الحضور اليومي ----------------

    private JPanel dailyTab() {
        JButton refresh = button("عرض"), export = button("تصدير CSV");
        refresh.addActionListener(e -> loadDaily());
        export.addActionListener(e -> exportCsv());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(new JLabel("التاريخ (YYYY-MM-DD):")); top.add(dateField); top.add(refresh); top.add(export);
        stats.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table(dailyModel)), BorderLayout.CENTER);
        p.add(stats, BorderLayout.SOUTH);
        return p;
    }

    private void loadDaily() {
        try {
            List<Db.Daily> rows = db.dailySummary(LocalDate.parse(dateField.getText().trim()));
            dailyModel.setRowCount(0);
            int present = 0, absent = 0, late = 0;
            for (Db.Daily d : rows) {
                String st = Rules.status(d);
                int lateMin = Rules.lateMinutes(d);
                if ("ABSENT".equals(st)) absent++; else present++;
                if (lateMin > 0) late++;
                dailyModel.addRow(new Object[]{d.empId(), d.name(), d.dept(), d.shiftStart() + " - " + d.shiftEnd(),
                    s(d.in()), d.breakStart() == null ? "—" : s(d.breakStart()) + " - " + s(d.breakEnd()),
                    s(d.otEnd() != null ? d.otEnd() : d.out()), Rules.statusLabel(st), lateMin, Rules.overtimeMinutes(d)});
            }
            stats.setText("الإجمالي: " + rows.size() + "    الحاضرون: " + present + "    الغائبون: " + absent + "    المتأخرون: " + late);
        } catch (Exception ex) { error(ex); }
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("attendance-" + dateField.getText().trim() + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(fc.getSelectedFile(), StandardCharsets.UTF_8)) {
            w.print('﻿'); // لكي يفتح Excel العربي بشكل صحيح
            for (int c = 0; c < dailyModel.getColumnCount(); c++) w.print((c > 0 ? "," : "") + dailyModel.getColumnName(c));
            w.println();
            for (int r = 0; r < dailyModel.getRowCount(); r++) {
                for (int c = 0; c < dailyModel.getColumnCount(); c++)
                    w.print((c > 0 ? "," : "") + "\"" + String.valueOf(dailyModel.getValueAt(r, c)).replace("\"", "\"\"") + "\"");
                w.println();
            }
            JOptionPane.showMessageDialog(this, "تم حفظ التقرير");
        } catch (Exception ex) { error(ex); }
    }

    // ---------------- الموظفون ----------------

    private JPanel employeesTab() {
        JButton add = button("إضافة موظف"), toggle = button("تفعيل / إيقاف");
        JTable t = table(empModel);
        add.addActionListener(e -> addEmployee());
        toggle.addActionListener(e -> {
            int r = t.getSelectedRow();
            if (r < 0) return;
            try {
                db.setActive((String) empModel.getValueAt(r, 0), !"نعم".equals(empModel.getValueAt(r, 5)));
                loadEmployees();
            } catch (Exception ex) { error(ex); }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(add); top.add(toggle);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private void loadEmployees() {
        try {
            empModel.setRowCount(0);
            for (Db.Employee e : db.employees())
                empModel.addRow(new Object[]{e.id(), e.name(), e.dept(), "ADMIN".equals(e.role()) ? "مدير" : "موظف",
                                             e.shiftId(), e.active() ? "نعم" : "لا"});
        } catch (Exception ex) { error(ex); }
    }

    private void addEmployee() {
        try {
            JTextField id = new JTextField(), name = new JTextField();
            JPasswordField pin = new JPasswordField();
            JComboBox<String> dept = new JComboBox<>(), shift = new JComboBox<>(), role = new JComboBox<>(new String[]{"موظف", "مدير"});
            List<Db.Dept> depts = db.departments();
            List<Db.Shift> shifts = db.shifts();
            depts.forEach(d -> dept.addItem(d.name()));
            shifts.forEach(s -> shift.addItem(s.name() + " (" + s.start() + " - " + s.end() + ")"));

            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(new JLabel("الرقم الوظيفي")); p.add(id);
            p.add(new JLabel("الاسم"));         p.add(name);
            p.add(new JLabel("القسم"));         p.add(dept);
            p.add(new JLabel("الدوام"));        p.add(shift);
            p.add(new JLabel("الصلاحية"));      p.add(role);
            p.add(new JLabel("الرمز السري"));   p.add(pin);
            p.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

            if (JOptionPane.showConfirmDialog(this, p, "إضافة موظف", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            String pinText = new String(pin.getPassword());
            if (id.getText().isBlank() || name.getText().isBlank() || pinText.length() < 4) {
                JOptionPane.showMessageDialog(this, "أكمل البيانات (الرمز السري 4 أرقام على الأقل)");
                return;
            }
            db.addEmployee(id.getText().trim(), name.getText().trim(),
                           depts.get(dept.getSelectedIndex()).id(), shifts.get(shift.getSelectedIndex()).id(),
                           role.getSelectedIndex() == 1 ? "ADMIN" : "EMP", pinText);
            loadEmployees(); loadDaily();
        } catch (Exception ex) { error(ex); }
    }

    // ---------------- أوقات الدوام ----------------

    private JPanel shiftsTab() {
        JButton save = button("حفظ التعديلات");
        JTable t = table(shiftModel);
        save.addActionListener(e -> {
            if (t.isEditing()) t.getCellEditor().stopCellEditing();
            try {
                for (int r = 0; r < shiftModel.getRowCount(); r++) {
                    db.updateShift(new Db.Shift(Integer.parseInt(String.valueOf(shiftModel.getValueAt(r, 0))),
                        String.valueOf(shiftModel.getValueAt(r, 1)), hhmm(shiftModel.getValueAt(r, 2)),
                        hhmm(shiftModel.getValueAt(r, 3)), Integer.parseInt(String.valueOf(shiftModel.getValueAt(r, 4)).trim()),
                        hhmm(shiftModel.getValueAt(r, 5))));
                }
                JOptionPane.showMessageDialog(this, "تم حفظ أوقات الدوام");
                loadShifts(); loadDaily();
            } catch (Exception ex) { error(ex); }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(new JLabel("عدّل الأوقات بصيغة HH:MM ثم اضغط حفظ")); top.add(save);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private void loadShifts() {
        try {
            shiftModel.setRowCount(0);
            for (Db.Shift s : db.shifts())
                shiftModel.addRow(new Object[]{s.id(), s.name(), s.start(), s.breakTime(), s.breakMinutes(), s.end()});
        } catch (Exception ex) { error(ex); }
    }

    private static String hhmm(Object o) {
        String v = String.valueOf(o).trim();
        if (!v.matches("([01]\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException("وقت غير صحيح: " + v);
        return v;
    }

    // ---------------- أدوات ----------------

    private static DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }

    private static JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(28);
        t.setFont(FONT);
        t.getTableHeader().setFont(FONT.deriveFont(Font.BOLD));
        t.setAutoCreateRowSorter(true);
        return t;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT);
        return b;
    }

    private static String s(Object o) { return o == null ? "—" : o.toString(); }

    private void error(Exception ex) {
        JOptionPane.showMessageDialog(this, "خطأ: " + ex.getMessage(), "خطأ", JOptionPane.ERROR_MESSAGE);
    }
}
