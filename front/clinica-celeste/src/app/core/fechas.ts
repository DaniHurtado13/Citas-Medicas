/* ---------- Utilidades de fecha (sin librerías) ---------- */

export const TODAY = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; })();

export function addDays(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}

// "2026-09-25" (LocalDate)
export function iso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function parseISO(str: string): Date {
  const [y, m, d] = str.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

// Lunes de la semana de una fecha
export function startOfWeek(date: Date): Date {
  const d = new Date(date);
  const dow = (d.getDay() + 6) % 7; // lunes = 0
  d.setDate(d.getDate() - dow);
  d.setHours(0, 0, 0, 0);
  return d;
}

/* LocalDateTime <-> partes */
export const fechaDe = (fechaHora: string) => fechaHora.slice(0, 10);
export const horaDe = (fechaHora: string) => fechaHora.slice(11, 16);
export const fechaHora = (fecha: string, hora: string) => `${fecha}T${hora}:00`;

export const nowHM = () => {
  const n = new Date();
  return `${String(n.getHours()).padStart(2, '0')}:${String(n.getMinutes()).padStart(2, '0')}`;
};
export const ahora = () => fechaHora(iso(new Date()), nowHM());

export const toMin = (hm: string) => { const [h, m] = hm.split(':').map(Number); return h * 60 + m; };
export const toHM = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

export const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/* ---------- Formato para mostrar (es-CO) ---------- */
export const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export const fmt = (d: Date | string, o: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('es-CO', o).format(typeof d === 'string' ? parseISO(d) : d);

export const longDate = (d: Date | string) => cap(fmt(d, { weekday: 'long', day: 'numeric', month: 'long' }));

export function fmtHora(h: string): string {
  let [H, M] = h.split(':').map(Number);
  const p = H >= 12 ? 'p. m.' : 'a. m.';
  H = H % 12 || 12;
  return `${H}:${String(M).padStart(2, '0')} ${p}`;
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
}

export function lastMonths(n: number) {
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(TODAY.getFullYear(), TODAY.getMonth() - (n - 1 - i), 1);
    return { d, l: cap(fmt(d, { month: 'short' }).replace('.', '')) };
  });
}

/* ---------- Meses ('2026-10') ---------- */
export const mesDe = (fecha: Date | string) => (typeof fecha === 'string' ? fecha : iso(fecha)).slice(0, 7);
export const primerDiaDe = (mes: string) => `${mes}-01`;
export function ultimoDiaDe(mes: string): string {
  const [y, m] = mes.split('-').map(Number);
  return iso(new Date(y, m, 0));
}
