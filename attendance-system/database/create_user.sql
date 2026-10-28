-- شغّله بحساب SYSTEM مرة وحدة قبل schema.sql
-- (Oracle XE: الاتصال على XEPDB1)
CREATE USER attendance IDENTIFIED BY attendance
  DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE TO attendance;
