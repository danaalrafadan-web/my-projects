package attendance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * خادم الويب: يقدّم صفحات الموقع + واجهة REST API مربوطة بقاعدة Oracle.
 * يُشغَّل:  java -cp "out;lib/ojdbc11.jar" attendance.Server
 */
public class Server {
    record Session(String empId, String role) {}

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Path WEB = Paths.get(System.getenv().getOrDefault("WEB_DIR", "../web")).toAbsolutePath().normalize();
    private static final Set<String> ACTIONS = Set.of("IN", "BREAK_START", "BREAK_END", "OUT", "OT_START", "OT_END");
    private static final Db DB = new Db();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/api/login",           ex -> api(ex, Server::login));
        s.createContext("/api/logout",          ex -> api(ex, Server::logout));
        s.createContext("/api/status",          ex -> api(ex, Server::status));
        s.createContext("/api/punch",           ex -> api(ex, Server::punch));
        s.createContext("/api/admin/summary",   ex -> api(ex, Server::summary));
        s.createContext("/api/admin/employees", ex -> api(ex, Server::employees));
        s.createContext("/api/admin/lookups",   ex -> api(ex, Server::lookups));
        s.createContext("/", Server::staticFile);
        s.setExecutor(Executors.newFixedThreadPool(8));
        s.start();
        System.out.println("Attendance server running: http://localhost:" + port + "  (web: " + WEB + ")");
    }

    // ---------------- البنية ----------------

    interface Handler { String handle(HttpExchange ex) throws Exception; }

    static class HttpError extends Exception {
        final int code;
        HttpError(int code, String msg) { super(msg); this.code = code; }
    }

    private static void api(HttpExchange ex, Handler h) throws IOException {
        int code = 200;
        String body;
        try {
            body = h.handle(ex);
        } catch (HttpError e) {
            code = e.code; body = Json.obj("error", e.getMessage());
        } catch (SQLIntegrityConstraintViolationException e) {
            code = 409; body = Json.obj("error", "البيانات مسجلة مسبقاً");
        } catch (Exception e) {
            e.printStackTrace();
            code = 500; body = Json.obj("error", "خطأ في الخادم أو قاعدة البيانات");
        }
        send(ex, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) q.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
                             URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    private static void requirePost(HttpExchange ex) throws HttpError {
        if (!"POST".equals(ex.getRequestMethod())) throw new HttpError(405, "الطريقة غير مسموحة");
    }

    private static Session auth(HttpExchange ex, boolean admin) throws HttpError {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        Session s = h == null ? null : SESSIONS.get(h.replace("Bearer ", "").trim());
        if (s == null) throw new HttpError(401, "انتهت الجلسة، سجّل الدخول من جديد");
        if (admin && !"ADMIN".equals(s.role())) throw new HttpError(403, "هذه الصفحة للمدير فقط");
        return s;
    }

    // ---------------- الموظف ----------------

    private static String login(HttpExchange ex) throws Exception {
        requirePost(ex);
        Map<String, String> in = Json.parse(body(ex));
        Db.Employee e = DB.login(in.get("empId"), in.get("pin"));
        if (e == null) throw new HttpError(401, "الرقم الوظيفي أو الرمز السري غير صحيح");
        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, new Session(e.id(), e.role()));
        return "{\"token\":" + Json.str(token) + ",\"empId\":" + Json.str(e.id()) +
               ",\"name\":" + Json.str(e.name()) + ",\"role\":" + Json.str(e.role()) +
               ",\"dept\":" + Json.str(e.dept()) + "}";
    }

    private static String logout(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h != null) SESSIONS.remove(h.replace("Bearer ", "").trim());
        return "{}";
    }

    private static String status(HttpExchange ex) throws Exception {
        return statusJson(auth(ex, false).empId());
    }

    private static String punch(HttpExchange ex) throws Exception {
        requirePost(ex);
        Session s = auth(ex, false);
        Map<String, String> in = Json.parse(body(ex));
        String action = in.getOrDefault("action", "");
        if (!ACTIONS.contains(action)) throw new HttpError(400, "إجراء غير معروف");

        // التحقق يتم في الخادم بوقت الخادم، وليس بوقت جهاز الموظف
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Db.Punch> today = DB.punches(s.empId(), now.toLocalDate());
        if (!Rules.allowed(today, now.toLocalTime(), DB.shiftOf(s.empId())).contains(action))
            throw new HttpError(409, "هذا الإجراء غير متاح في الوقت الحالي");

        String device = in.getOrDefault("device", "WEB");
        DB.insertPunch(s.empId(), action, device.length() > 20 ? device.substring(0, 20) : device, now);
        return statusJson(s.empId());
    }

    private static String statusJson(String empId) throws Exception {
        Db.Shift sh = DB.shiftOf(empId);
        LocalTime now = LocalTime.now().withNano(0);
        List<Db.Punch> p = DB.punches(empId, LocalDate.now());
        String allowed = Rules.allowed(p, now, sh).stream().map(Json::str).collect(Collectors.joining(","));
        String punches = p.stream()
            .map(x -> "{\"action\":" + Json.str(x.action()) + ",\"time\":" + Json.str(x.time().toString()) + "}")
            .collect(Collectors.joining(","));
        return "{\"serverTime\":" + Json.str(now.toString()) +
               ",\"date\":" + Json.str(LocalDate.now().toString()) +
               ",\"shift\":{\"name\":" + Json.str(sh.name()) + ",\"start\":" + Json.str(sh.start()) +
               ",\"breakTime\":" + Json.str(sh.breakTime()) + ",\"breakMinutes\":" + sh.breakMinutes() +
               ",\"end\":" + Json.str(sh.end()) + "}" +
               ",\"allowed\":[" + allowed + "],\"punches\":[" + punches + "]}";
    }

    // ---------------- المدير ----------------

    private static String summary(HttpExchange ex) throws Exception {
        auth(ex, true);
        String d = query(ex).get("date");
        LocalDate date = d == null || d.isBlank() ? LocalDate.now() : LocalDate.parse(d);
        StringBuilder sb = new StringBuilder("{\"date\":").append(Json.str(date)).append(",\"rows\":[");
        boolean first = true;
        for (Db.Daily r : DB.dailySummary(date)) {
            if (!first) sb.append(',');
            first = false;
            String st = Rules.status(r);
            sb.append("{\"empId\":").append(Json.str(r.empId()))
              .append(",\"name\":").append(Json.str(r.name()))
              .append(",\"dept\":").append(Json.str(r.dept()))
              .append(",\"shift\":").append(Json.str(r.shiftStart() + " - " + r.shiftEnd()))
              .append(",\"in\":").append(Json.str(r.in()))
              .append(",\"breakStart\":").append(Json.str(r.breakStart()))
              .append(",\"breakEnd\":").append(Json.str(r.breakEnd()))
              .append(",\"out\":").append(Json.str(r.otEnd() != null ? r.otEnd() : r.out()))
              .append(",\"status\":").append(Json.str(st))
              .append(",\"statusLabel\":").append(Json.str(Rules.statusLabel(st)))
              .append(",\"late\":").append(Rules.lateMinutes(r))
              .append(",\"breakMin\":").append(Rules.breakMinutes(r))
              .append(",\"overtime\":").append(Rules.overtimeMinutes(r))
              .append('}');
        }
        return sb.append("]}").toString();
    }

    private static String employees(HttpExchange ex) throws Exception {
        auth(ex, true);
        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> in = Json.parse(body(ex));
            String id = in.getOrDefault("empId", "").trim(), name = in.getOrDefault("name", "").trim();
            String pin = in.getOrDefault("pin", "");
            if (id.isEmpty() || name.isEmpty() || pin.length() < 4)
                throw new HttpError(400, "أكمل البيانات (الرمز السري 4 أرقام على الأقل)");
            DB.addEmployee(id, name, Integer.parseInt(in.getOrDefault("deptId", "1")),
                           Integer.parseInt(in.getOrDefault("shiftId", "1")), in.get("role"), pin);
        }
        String rows = DB.employees().stream().map(e ->
            "{\"empId\":" + Json.str(e.id()) + ",\"name\":" + Json.str(e.name()) + ",\"role\":" + Json.str(e.role()) +
            ",\"dept\":" + Json.str(e.dept()) + ",\"shiftId\":" + e.shiftId() + ",\"active\":" + e.active() + "}")
            .collect(Collectors.joining(","));
        return "{\"rows\":[" + rows + "]}";
    }

    private static String lookups(HttpExchange ex) throws Exception {
        auth(ex, true);
        String depts = DB.departments().stream()
            .map(d -> "{\"id\":" + d.id() + ",\"name\":" + Json.str(d.name()) + "}").collect(Collectors.joining(","));
        String shifts = DB.shifts().stream()
            .map(s -> "{\"id\":" + s.id() + ",\"name\":" + Json.str(s.name() + " (" + s.start() + " - " + s.end() + ")") + "}")
            .collect(Collectors.joining(","));
        return "{\"departments\":[" + depts + "],\"shifts\":[" + shifts + "]}";
    }

    // ---------------- الملفات الثابتة ----------------

    private static final Map<String, String> TYPES = Map.of(
        "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
        "js", "application/javascript; charset=utf-8", "json", "application/json; charset=utf-8",
        "svg", "image/svg+xml", "png", "image/png", "webmanifest", "application/manifest+json");

    private static void staticFile(HttpExchange ex) throws IOException {
        String p = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (p.endsWith("/")) p += "index.html";
        Path file = WEB.resolve(p.substring(1)).normalize();
        if (!file.startsWith(WEB) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "404".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.substring(name.lastIndexOf('.') + 1);
        send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(file));
    }
}
