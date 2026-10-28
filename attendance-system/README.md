# نظام الحضور والانصراف

نظام بصمة متكامل يتكون من:

- موقع ويب
- خادم Java
- قاعدة بيانات Oracle
- لوحة مدير على سطح المكتب (Java Swing)
- تطبيق Android

## محتويات المجلد

```
attendance-system/
├── database/   سكربتات Oracle (إنشاء المستخدم + الجداول + بيانات تجريبية)
├── server/     خادم Java + لوحة المدير Swing
├── web/        صفحات الموقع (البصمة + لوحة المدير) + PWA
├── android/    مشروع Android Studio
└── docs/       وثيقة التحليل والتصميم (افتح analysis.html)
```

## المتطلبات

- **Java JDK 17 أو أحدث.**
- **Oracle Database XE 21c**، وهو مجاني.
- **ملف تعريف Oracle JDBC `ojdbc11.jar`:** نزّله من موقع Oracle وضعه في `server/lib/`.
- **Android Studio**، وتحتاجه لتطبيق الجوال فقط.

## خطوات التشغيل

### 1. قاعدة البيانات

1. ادخل بحساب SYSTEM وشغّل الملف `database/create_user.sql`.
2. ادخل بالمستخدم `attendance` وشغّل الملف `database/schema.sql`.

تقدر تشغّل الملفين من SQL Developer أو SQL*Plus.

### 2. الخادم والموقع (Windows)

```
cd server
javac -encoding UTF-8 -d out src/attendance/*.java
java -cp "out;lib/ojdbc11.jar" attendance.Server
```

بعدها افتح الموقع على: http://localhost:8080

| الحساب | الرقم الوظيفي | الرمز السري |
|---|---|---|
| مدير | 9000 | 1234 |
| موظف | 1001 | 1234 |

إعدادات الاتصال بقاعدة البيانات اختيارية. هذه المتغيرات وقيمها الافتراضية:

| المتغير | القيمة الافتراضية |
|---|---|
| `ORACLE_URL` | `jdbc:oracle:thin:@localhost:1521/XEPDB1` |
| `ORACLE_USER` | `attendance` |
| `ORACLE_PASS` | `attendance` |
| `PORT` | `8080` |

### 3. لوحة المدير على سطح المكتب (Java Swing)

```
java -cp "out;lib/ojdbc11.jar" attendance.AdminApp
```

### 4. تطبيق Android

1. افتح مجلد `android` في Android Studio.
2. اضغط Run.

على المحاكي يتصل التطبيق تلقائياً بالخادم اللي على كمبيوترك. على جوال حقيقي، غيّر قيمة `server_url` في الملف `app/src/main/res/values/strings.xml` إلى عنوان IP الكمبيوتر.

### 5. التثبيت على iPhone و Android و Windows (PWA)

افتح الموقع من متصفح الجهاز، ثم اختر **"إضافة إلى الشاشة الرئيسية"**.
