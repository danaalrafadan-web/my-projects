-- نظام الحضور الذكي - مخطط Supabase
-- شغّل هذا الملف كاملاً من: Supabase Dashboard > SQL Editor

-- ---------- الموظفون (ربط الرقم الوظيفي بالاسم وبالحساب) ----------
create table if not exists public.employees (
  emp_id     text primary key check (emp_id ~ '^[0-9]{1,10}$'),
  full_name  text not null check (length(trim(full_name)) >= 2),
  user_id    uuid unique references auth.users(id) on delete set null,
  role       text not null default 'employee' check (role in ('employee','admin')),
  created_at timestamptz not null default now()
);

-- أدمن رئيسي واحد فقط على مستوى النظام
create unique index if not exists one_admin_only on public.employees ((role)) where role = 'admin';

-- الاسم يُثبَّت عند أول تسجيل ولا يمكن تغييره، والدور لا يتغير إلا من SQL Editor
create or replace function public.lock_employee() returns trigger language plpgsql as $$
begin
  if new.full_name is distinct from old.full_name then raise exception 'لا يمكن تعديل اسم الموظف بعد التسجيل'; end if;
  if new.emp_id is distinct from old.emp_id then raise exception 'لا يمكن تعديل الرقم الوظيفي'; end if;
  if new.role is distinct from old.role and current_user not in ('postgres','supabase_admin') then
    raise exception 'غير مصرح بتغيير الصلاحيات';
  end if;
  return new;
end $$;
drop trigger if exists lock_employee on public.employees;
create trigger lock_employee before update on public.employees for each row execute function public.lock_employee();

-- عند إنشاء حساب جديد: الرقم الوظيفي لا يُقبل إلا مع الاسم المطابق له
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_emp  text := trim(new.raw_user_meta_data->>'emp_id');
  v_name text := regexp_replace(trim(new.raw_user_meta_data->>'full_name'), '\s+', ' ', 'g');
  v_row  public.employees;
begin
  if v_emp is null or v_name is null or v_emp = '' or v_name = '' then
    raise exception 'الرقم الوظيفي واسم الموظف مطلوبان';
  end if;
  select * into v_row from public.employees where emp_id = v_emp for update;
  if not found then
    insert into public.employees(emp_id, full_name, user_id) values (v_emp, v_name, new.id);
  elsif v_row.user_id is not null then
    raise exception 'هذا الرقم الوظيفي مرتبط بحساب آخر';
  elsif lower(v_row.full_name) <> lower(v_name) then
    raise exception 'الاسم لا يطابق الرقم الوظيفي المسجّل';
  else
    update public.employees set user_id = new.id where emp_id = v_emp;
  end if;
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

-- تسجيل الدخول بالرقم الوظيفي بدل الإيميل: يرجع الإيميل المرتبط
create or replace function public.email_for_emp(p_emp text) returns text
language sql security definer set search_path = public, auth stable as $$
  select u.email from public.employees e join auth.users u on u.id = e.user_id where e.emp_id = trim(p_emp);
$$;
grant execute on function public.email_for_emp(text) to anon, authenticated;

create or replace function public.is_admin() returns boolean
language sql security definer set search_path = public stable as $$
  select exists(select 1 from public.employees where user_id = auth.uid() and role = 'admin');
$$;

-- ---------- إعدادات الدوام (صف واحد، يعدّله الأدمن فقط) ----------
create table if not exists public.settings (
  id          int primary key default 1 check (id = 1),
  shift_start time not null default '08:00',
  break_time  time not null default '11:00',
  break_len   int  not null default 30 check (break_len >= 0),
  shift_end   time not null default '15:00',
  updated_at  timestamptz not null default now()
);
insert into public.settings(id) values (1) on conflict do nothing;

-- ---------- سجل الحركات ----------
create table if not exists public.attendance (
  id         bigint generated always as identity primary key,
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  emp_id     text not null references public.employees(emp_id),
  day        date not null,
  type       text not null check (type in ('in','breakStart','breakEnd','out','otStart','otEnd')),
  time       text not null check (time ~ '^[0-2][0-9]:[0-5][0-9]$'),
  created_at timestamptz not null default now()
);
create index if not exists attendance_user_day on public.attendance(user_id, day);

-- ---------- الحماية (Row Level Security) ----------
alter table public.employees  enable row level security;
alter table public.settings   enable row level security;
alter table public.attendance enable row level security;

drop policy if exists emp_read on public.employees;
create policy emp_read on public.employees for select to authenticated
  using (user_id = auth.uid() or public.is_admin());

drop policy if exists set_read on public.settings;
create policy set_read on public.settings for select to authenticated using (true);
drop policy if exists set_admin on public.settings;
create policy set_admin on public.settings for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

drop policy if exists att_read on public.attendance;
create policy att_read on public.attendance for select to authenticated
  using (user_id = auth.uid() or public.is_admin());
drop policy if exists att_insert on public.attendance;
create policy att_insert on public.attendance for insert to authenticated
  with check (user_id = auth.uid()
    and emp_id = (select emp_id from public.employees where user_id = auth.uid()));

-- ---------- تعيين الأدمن الرئيسي (مرة واحدة) ----------
-- بعد إنشاء حسابك من الموقع، غيّر الرقم وشغّل السطر التالي:
-- update public.employees set role = 'admin' where emp_id = '9000';
