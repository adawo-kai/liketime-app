import type {
  AttendanceStatus, EventStatus, LessonStatus, PeriodStatus,
} from './types';

/** Школа работает по времени Бишкека. Моменты времени (UTC) показываем в нём независимо от настроек компьютера. */
export const SCHOOL_TIME_ZONE = 'Asia/Bishkek';

export const WEEKDAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];
export const WEEKDAYS_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

export const LESSON_STATUS_LABELS: Record<LessonStatus, string> = {
  PLANNED: 'Запланировано',
  HELD: 'Проведено',
  CANCELLED: 'Отменено',
};

export const ATTENDANCE_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Пришёл',
  LATE: 'Опоздал',
  ABSENT: 'Не пришёл',
  EXCUSED: 'Уважительная',
};

export const PERIOD_STATUS_LABELS: Record<PeriodStatus, string> = {
  OPEN: 'Открыт',
  CALCULATED: 'Рассчитан',
  APPROVED: 'Утверждён',
  PAID: 'Выплачен',
};

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  MATCHED: 'Засчитано',
  UNKNOWN_PERSON: 'Неизвестное лицо',
  LOW_CONFIDENCE: 'Низкая уверенность',
  NO_LESSON: 'Нет занятия',
  IGNORED: 'Не учтено',
};

// ----- даты (строки YYYY-MM-DD, без часовых поясов) -----

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** Дата в виде YYYY-MM-DD по местному календарю компьютера */
export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Разбирает YYYY-MM-DD как полночь местного календаря (а не UTC), чтобы дни не «съезжали» */
export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const d = parseIsoDate(iso);
  d.setDate(d.getDate() + days);
  return toIsoDate(d);
}

/** Понедельник недели, в которую попадает дата (неделя начинается с понедельника) */
export function mondayOf(iso: string): string {
  const d = parseIsoDate(iso);
  const shift = (d.getDay() + 6) % 7;
  return addDays(iso, -shift);
}

/** Первый и последний день месяца, в который попадает дата */
export function monthRange(iso: string): { from: string; to: string } {
  const d = parseIsoDate(iso);
  const from = new Date(d.getFullYear(), d.getMonth(), 1);
  const to = new Date(d.getFullYear(), d.getMonth() + 1, 0);
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

/** Предыдущий календарный месяц относительно даты */
export function previousMonthRange(iso: string): { from: string; to: string } {
  const d = parseIsoDate(iso);
  return monthRange(toIsoDate(new Date(d.getFullYear(), d.getMonth() - 1, 15)));
}

/** День недели по ISO: 1 = понедельник ... 7 = воскресенье */
export function isoWeekday(iso: string): number {
  return ((parseIsoDate(iso).getDay() + 6) % 7) + 1;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.split('-');
  return `${d}.${m}.${y}`;
}

/** "09:00:00" -> "09:00" */
export function formatTime(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : '—';
}

export function formatRange(from: string, to: string): string {
  return `${formatDate(from)} – ${formatDate(to)}`;
}

/** Заголовок дня: "Понедельник, 27.10.2026" */
export function formatDayTitle(iso: string): string {
  return `${WEEKDAYS[isoWeekday(iso) - 1]}, ${formatDate(iso)}`;
}

/** Момент времени (ISO с зоной) -> "27.10 09:15" по времени школы */
export function formatDateTime(instant: string | null | undefined): string {
  if (!instant) return '—';
  const parts = new Intl.DateTimeFormat('ru-RU', {
    timeZone: SCHOOL_TIME_ZONE, day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(new Date(instant));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('day')}.${get('month')} ${get('hour')}:${get('minute')}`;
}

/** Только время по Бишкеку: "09:15" */
export function formatClock(instant: string | null | undefined): string {
  if (!instant) return '—';
  return formatDateTime(instant).split(' ')[1];
}

/** 1234.5 -> "1 234,50 сом" */
export function formatMoney(value: number): string {
  const text = new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
  return `${text} сом`;
}

export function formatPercent(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

/** Уверенность распознавания 0..1 -> "93%" */
export function formatConfidence(value: number | null): string {
  return value === null ? '—' : formatPercent(value);
}
