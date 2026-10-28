-- =============================================================
--  نظام الحضور والانصراف - قاعدة بيانات Oracle
--  شغّل هذا الملف بمستخدم attendance (انظر create_user.sql)
-- =============================================================

-- الأقسام
CREATE TABLE departments (
  dept_id   NUMBER        PRIMARY KEY,
  name      VARCHAR2(100) NOT NULL
);

-- أنواع الدوام (بداية، استراحة، نهاية)
CREATE TABLE shifts (
  shift_id      NUMBER       PRIMARY KEY,
  name          VARCHAR2(50) NOT NULL,
  start_time    CHAR(5)      NOT NULL,   -- HH:MI
  break_time    CHAR(5)      NOT NULL,
  break_minutes NUMBER(3)    DEFAULT 30 NOT NULL,
  end_time      CHAR(5)      NOT NULL
);

-- الموظفون
CREATE TABLE employees (
  emp_id     VARCHAR2(20)  PRIMARY KEY,
  full_name  VARCHAR2(150) NOT NULL,
  dept_id    NUMBER        REFERENCES departments(dept_id),
  shift_id   NUMBER        REFERENCES shifts(shift_id),
  role       VARCHAR2(10)  DEFAULT 'EMP' NOT NULL CHECK (role IN ('EMP','ADMIN')),
  pin_hash   VARCHAR2(64)  NOT NULL,     -- SHA-256 للرمز السري
  active     CHAR(1)       DEFAULT 'Y' NOT NULL CHECK (active IN ('Y','N')),
  created_at DATE          DEFAULT SYSDATE
);

-- حركات البصمة
CREATE TABLE attendance (
  att_id      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  emp_id      VARCHAR2(20) NOT NULL REFERENCES employees(emp_id),
  att_date    DATE         NOT NULL,
  action      VARCHAR2(12) NOT NULL
              CHECK (action IN ('IN','BREAK_START','BREAK_END','OUT','OT_START','OT_END')),
  action_time TIMESTAMP    NOT NULL,
  device      VARCHAR2(20),             -- WEB / ANDROID / IOS
  CONSTRAINT uq_attendance UNIQUE (emp_id, att_date, action)
);

CREATE INDEX ix_attendance_date ON attendance(att_date);

-- ملخص يومي لكل موظف (للتقارير)
CREATE OR REPLACE VIEW v_daily_summary AS
SELECT e.emp_id, e.full_name, d.name AS dept_name, a.att_date,
       MAX(CASE WHEN a.action = 'IN'          THEN a.action_time END) AS check_in,
       MAX(CASE WHEN a.action = 'BREAK_START' THEN a.action_time END) AS break_start,
       MAX(CASE WHEN a.action = 'BREAK_END'   THEN a.action_time END) AS break_end,
       MAX(CASE WHEN a.action = 'OUT'         THEN a.action_time END) AS check_out,
       MAX(CASE WHEN a.action = 'OT_START'    THEN a.action_time END) AS ot_start,
       MAX(CASE WHEN a.action = 'OT_END'      THEN a.action_time END) AS ot_end
FROM employees e
JOIN attendance a       ON a.emp_id = e.emp_id
LEFT JOIN departments d ON d.dept_id = e.dept_id
GROUP BY e.emp_id, e.full_name, d.name, a.att_date;

-- =============================================================
--  بيانات تجريبية  (الرمز السري لكل الحسابات: 1234)
-- =============================================================
INSERT INTO departments VALUES (1, 'تقنية المعلومات');
INSERT INTO departments VALUES (2, 'الموارد البشرية');
INSERT INTO departments VALUES (3, 'المالية');

INSERT INTO shifts VALUES (1, 'الدوام الصباحي', '08:00', '11:00', 30, '15:00');
INSERT INTO shifts VALUES (2, 'الدوام المسائي', '14:00', '17:00', 30, '21:00');

INSERT INTO employees (emp_id, full_name, dept_id, shift_id, role, pin_hash) VALUES
  ('9000', 'مدير النظام', 2, 1, 'ADMIN', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4');
INSERT INTO employees (emp_id, full_name, dept_id, shift_id, role, pin_hash) VALUES
  ('1001', 'أحمد العتيبي', 1, 1, 'EMP', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4');
INSERT INTO employees (emp_id, full_name, dept_id, shift_id, role, pin_hash) VALUES
  ('1002', 'سارة القحطاني', 1, 1, 'EMP', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4');
INSERT INTO employees (emp_id, full_name, dept_id, shift_id, role, pin_hash) VALUES
  ('1003', 'خالد الشمري', 3, 2, 'EMP', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4');

COMMIT;
