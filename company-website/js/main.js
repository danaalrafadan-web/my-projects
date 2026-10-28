document.addEventListener('DOMContentLoaded', () => {
  const header = document.getElementById('header');
  const menuToggle = document.getElementById('menuToggle');
  const nav = document.getElementById('navMenu');
  const navLinks = document.querySelectorAll('.nav-link');
  const toTop = document.getElementById('toTop');

  // السنة الحالية في الفوتر
  document.getElementById('year').textContent = new Date().getFullYear();

  // قائمة الجوال
  const closeMenu = () => {
    nav.classList.remove('open');
    menuToggle.classList.remove('open');
    menuToggle.setAttribute('aria-expanded', 'false');
  };
  menuToggle.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    menuToggle.classList.toggle('open', open);
    menuToggle.setAttribute('aria-expanded', String(open));
  });
  nav.querySelectorAll('a').forEach(a => a.addEventListener('click', closeMenu));

  // تمييز الرابط النشط + ظل الهيدر + زر العودة للأعلى
  const sections = [...navLinks].map(l => document.querySelector(l.getAttribute('href')));
  const onScroll = () => {
    const y = window.scrollY;
    header.classList.toggle('scrolled', y > 10);
    toTop.classList.toggle('show', y > 500);
    let current = 0;
    sections.forEach((s, i) => { if (s && s.offsetTop - 120 <= y) current = i; });
    navLinks.forEach((l, i) => l.classList.toggle('active', i === current));
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();
  toTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

  // ظهور العناصر عند التمرير
  const revealEls = document.querySelectorAll('.service-card, .why-item, .mini-card, .section-title, .contact-form');
  revealEls.forEach(el => el.classList.add('reveal'));
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); }
    });
  }, { threshold: 0.15 });
  revealEls.forEach(el => io.observe(el));

  // العدادات
  const counters = document.querySelectorAll('.counter');
  const counterIO = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (!e.isIntersecting) return;
      const el = e.target;
      const target = +el.dataset.target;
      const start = performance.now();
      const step = now => {
        const p = Math.min((now - start) / 1500, 1);
        el.textContent = Math.floor(p * target);
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      counterIO.unobserve(el);
    });
  }, { threshold: 0.5 });
  counters.forEach(c => counterIO.observe(c));

  // سلايدر آراء العملاء
  const slides = document.querySelectorAll('.slide');
  const dotsWrap = document.getElementById('dots');
  let index = 0;
  let timer;
  slides.forEach((_, i) => {
    const d = document.createElement('button');
    d.className = 'dot' + (i === 0 ? ' active' : '');
    d.setAttribute('aria-label', `الرأي رقم ${i + 1}`);
    d.addEventListener('click', () => { go(i); restart(); });
    dotsWrap.appendChild(d);
  });
  const dots = dotsWrap.querySelectorAll('.dot');
  const go = i => {
    index = (i + slides.length) % slides.length;
    slides.forEach((s, k) => s.classList.toggle('active', k === index));
    dots.forEach((d, k) => d.classList.toggle('active', k === index));
  };
  const restart = () => { clearInterval(timer); timer = setInterval(() => go(index + 1), 6000); };
  document.getElementById('nextSlide').addEventListener('click', () => { go(index + 1); restart(); });
  document.getElementById('prevSlide').addEventListener('click', () => { go(index - 1); restart(); });
  restart();

  // نموذج التواصل: تحقق من الحقول ثم فتح برنامج البريد برسالة جاهزة
  const form = document.getElementById('contactForm');
  const status = document.getElementById('formStatus');
  const CONTACT_EMAIL = 'info@example.com';
  const rules = {
    name: v => v.trim().length >= 3 || 'الرجاء إدخال الاسم (3 أحرف على الأقل)',
    email: v => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()) || 'الرجاء إدخال بريد إلكتروني صحيح',
    subject: v => v !== '' || 'الرجاء اختيار الموضوع',
    message: v => v.trim().length >= 10 || 'الرسالة قصيرة جدًا (10 أحرف على الأقل)'
  };
  const validate = field => {
    const result = rules[field.name](field.value);
    const wrap = field.closest('.field');
    const ok = result === true;
    wrap.classList.toggle('invalid', !ok);
    wrap.querySelector('.error').textContent = ok ? '' : result;
    return ok;
  };
  Object.keys(rules).forEach(name => {
    form.elements[name].addEventListener('input', e => validate(e.target));
  });
  form.addEventListener('submit', e => {
    e.preventDefault();
    const allOk = Object.keys(rules).map(n => validate(form.elements[n])).every(Boolean);
    if (!allOk) {
      status.className = 'form-status';
      status.textContent = 'يرجى تصحيح الحقول المحددة.';
      return;
    }
    const f = form.elements;
    const body = `الاسم: ${f.name.value}\nالبريد: ${f.email.value}\n\n${f.message.value}`;
    window.location.href = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(f.subject.value)}&body=${encodeURIComponent(body)}`;
    status.className = 'form-status success';
    status.textContent = 'شكرًا لك! تم تجهيز رسالتك في برنامج البريد لإرسالها.';
    form.reset();
  });
});
