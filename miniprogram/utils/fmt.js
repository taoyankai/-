// utils/fmt.js —— 时间与文本格式化
//
// 后端返回的是 LocalDateTime 序列化结果（形如 2026-09-16T08:22:00）。
// 注意 iOS 不支持 new Date('2026-09-16 08:22:00') 这种带空格的格式，
// 所以统一把 '-' 换成 '/'，带 'T' 的原生可解析。

function toDate(v) {
  if (!v && v !== 0) return null;
  if (v instanceof Date) return v;
  if (typeof v === 'number') return new Date(v);
  const s = String(v);
  const d = new Date(s.indexOf('T') > -1 ? s : s.replace(/-/g, '/'));
  return isNaN(d.getTime()) ? null : d;
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** 时间戳 / 字符串 → 'YYYY-MM-DD HH:mm'；withTime=false 时只到日 */
function fmt(v, withTime) {
  const d = toDate(v);
  if (!d) return '—';
  const day = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  if (withTime === false) return day;
  return day + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

/** 手机号脱敏：138****0000 */
function maskPhone(p) {
  const s = String(p || '');
  return s.length === 11 ? s.slice(0, 3) + '****' + s.slice(-4) : s;
}

/** 距离承诺发货截止的剩余天数：正数还有几天，负数已超期 */
function remainDays(deadline) {
  const d = toDate(deadline);
  if (!d) return 0;
  const diff = d.getTime() - Date.now();
  return Math.ceil(diff / 86400000);
}

/** 待发货订单的倒计时文案 */
function remainText(deadline) {
  const d = toDate(deadline);
  if (!d) return '';
  const diff = d.getTime() - Date.now();
  if (diff < 0) {
    const over = Math.ceil(-diff / 86400000);
    return '已超期 ' + over + ' 天（请尽快发货）';
  }
  const days = Math.ceil(diff / 86400000);
  return '承诺发货截止 ' + fmt(d, false) + '，剩余 ' + days + ' 天';
}

module.exports = {
  toDate: toDate,
  fmt: fmt,
  maskPhone: maskPhone,
  remainDays: remainDays,
  remainText: remainText
};
