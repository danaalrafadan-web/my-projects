package attendance;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** قواعد البصمة: أي زر يظهر حسب الوقت وآخر حركة. */
public final class Rules {
    private Rules() {}

    public static int minutes(String hhmm) {
        String[] p = hhmm.trim().split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static int minutes(LocalTime t) { return t.getHour() * 60 + t.getMinute(); }

    public static List<String> allowed(List<Db.Punch> punches, LocalTime now, Db.Shift s) {
        String last = punches.isEmpty() ? null : punches.get(punches.size() - 1).action();
        if (last == null) return List.of("IN");
        switch (last) {
            case "BREAK_START": return List.of("BREAK_END");
            case "OT_START":    return List.of("OT_END");
            case "OUT":
            case "OT_END":      return List.of();
            default: break;
        }
        int cur = minutes(now), brk = minutes(s.breakTime()), end = minutes(s.end());
        boolean hadBreak = punches.stream().anyMatch(p -> p.action().equals("BREAK_START"));
        List<String> a = new ArrayList<>();
        if (cur >= brk && cur < end && !hadBreak) a.add("BREAK_START");
        if (cur >= end) { a.add("OUT"); a.add("OT_START"); }
        return a;
    }

    public static String status(Db.Daily d) {
        if (d.in() == null) return "ABSENT";
        if (d.out() != null || d.otEnd() != null) return "LEFT";
        if (d.otStart() != null) return "OVERTIME";
        if (d.breakStart() != null && d.breakEnd() == null) return "BREAK";
        return "PRESENT";
    }

    public static String statusLabel(String s) {
        switch (s) {
            case "ABSENT":   return "غائب";
            case "LEFT":     return "انصرف";
            case "OVERTIME": return "عمل إضافي";
            case "BREAK":    return "في الاستراحة";
            default:         return "حاضر";
        }
    }

    public static int lateMinutes(Db.Daily d) {
        return d.in() == null ? 0 : Math.max(0, minutes(d.in()) - minutes(d.shiftStart()));
    }

    public static int breakMinutes(Db.Daily d) {
        return d.breakStart() == null || d.breakEnd() == null ? 0
             : (int) Duration.between(d.breakStart(), d.breakEnd()).toMinutes();
    }

    public static int overtimeMinutes(Db.Daily d) {
        return d.otStart() == null || d.otEnd() == null ? 0
             : (int) Duration.between(d.otStart(), d.otEnd()).toMinutes();
    }

    public static String actionLabel(String a) {
        switch (a) {
            case "IN":          return "تسجيل حضور";
            case "BREAK_START": return "بداية الاستراحة";
            case "BREAK_END":   return "نهاية الاستراحة";
            case "OUT":         return "تسجيل انصراف";
            case "OT_START":    return "بداية العمل الإضافي";
            case "OT_END":      return "نهاية العمل الإضافي";
            default:            return a;
        }
    }
}
