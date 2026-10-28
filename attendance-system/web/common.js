// أدوات مشتركة بين صفحة الموظف وصفحة المدير
const $ = id => document.getElementById(id);

const session = {
  get(){ try{ return JSON.parse(localStorage.getItem('att_session') || 'null'); }catch(e){ return null; } },
  set(v){ try{ localStorage.setItem('att_session', JSON.stringify(v)); }catch(e){} },
  clear(){ try{ localStorage.removeItem('att_session'); }catch(e){} }
};

async function api(path, body){
  const s = session.get();
  const res = await fetch(path, {
    method: body ? 'POST' : 'GET',
    headers: Object.assign({'Content-Type':'application/json'}, s ? {Authorization:'Bearer ' + s.token} : {}),
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json().catch(() => ({error:'استجابة غير صالحة من الخادم'}));
  if(res.status === 401 && s){ session.clear(); location.reload(); }
  if(!res.ok) throw new Error(data.error || 'حدث خطأ');
  return data;
}

async function logout(){
  try{ await api('/api/logout', {}); }catch(e){}
  session.clear(); location.href = 'index.html';
}

// نوع الجهاز: تطبيق أندرويد يضيف AttendanceAndroid إلى User-Agent
function deviceType(){
  const ua = navigator.userAgent;
  if(ua.includes('AttendanceAndroid')) return 'ANDROID';
  if(/iPhone|iPad/.test(ua)) return 'IOS';
  if(/Android/.test(ua)) return 'ANDROID_WEB';
  return 'WEB';
}

const ACT = {
  IN:{t:'تسجيل حضور', s:'بداية يوم العمل', c:'in', i:'in', color:'var(--in)'},
  BREAK_START:{t:'بداية الاستراحة', s:'استراحة الدوام', c:'brk', i:'brk', color:'var(--brk)'},
  BREAK_END:{t:'نهاية الاستراحة', s:'العودة للعمل', c:'brk', i:'back', color:'var(--brk)'},
  OUT:{t:'تسجيل انصراف', s:'نهاية يوم العمل', c:'out', i:'out', color:'var(--out)'},
  OT_START:{t:'عمل إضافي', s:'بدء ساعات إضافية', c:'ot', i:'ot', color:'var(--ot)'},
  OT_END:{t:'إنهاء العمل الإضافي', s:'تسجيل الانصراف', c:'ot', i:'end', color:'var(--ot)'}
};
const ICON = {
  in:'<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l-5-5 5-5M5 12h11"/>',
  brk:'<path d="M17 8h1a4 4 0 0 1 0 8h-1M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4zM6 2v3M10 2v3M14 2v3"/>',
  back:'<path d="M9 14 4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3"/>',
  out:'<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
  ot:'<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/>',
  end:'<path d="M4 22V4M4 4h13l-2 4 2 4H4"/>'
};
const svg = p => `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const hm = t => t ? t.slice(0,5) : '—';
const dur = m => !m ? '—' : m >= 60 ? `${Math.floor(m/60)} س ${m%60} د` : `${m} د`;

if('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
