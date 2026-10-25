import { describe, expect, it } from 'vitest';
import {
  addDays, formatClock, formatConfidence, formatDate, formatDateTime, formatDayTitle, formatMoney, formatPercent,
  formatRange, formatTime, isoWeekday, monthRange, mondayOf, parseIsoDate, previousMonthRange, toIsoDate,
} from './format';

// 25 октября 2026 года - воскресенье, 26-е - понедельник

describe('даты', () => {
  it('toIsoDate и parseIsoDate обратимы и не сдвигают день из-за часового пояса', () => {
    expect(toIsoDate(parseIsoDate('2026-10-25'))).toBe('2026-10-25');
    expect(toIsoDate(parseIsoDate('2026-01-01'))).toBe('2026-01-01');
    expect(toIsoDate(new Date(2026, 11, 31))).toBe('2026-12-31');
  });

  it('addDays переходит через границы месяцев и года', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
    expect(addDays('2026-10-25', 0)).toBe('2026-10-25');
  });

  it('mondayOf возвращает понедельник той же недели, воскресенье относится к предыдущей', () => {
    expect(mondayOf('2026-10-26')).toBe('2026-10-26');
    expect(mondayOf('2026-10-28')).toBe('2026-10-26');
    expect(mondayOf('2026-11-01')).toBe('2026-10-26');
    expect(mondayOf('2026-10-25')).toBe('2026-10-19');
    expect(mondayOf('2026-01-01')).toBe('2025-12-29');
  });

  it('isoWeekday: понедельник 1, воскресенье 7', () => {
    expect(isoWeekday('2026-10-26')).toBe(1);
    expect(isoWeekday('2026-10-30')).toBe(5);
    expect(isoWeekday('2026-10-25')).toBe(7);
  });

  it('monthRange даёт первый и последний день месяца, включая високосный февраль', () => {
    expect(monthRange('2026-10-15')).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(monthRange('2026-02-10')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
    expect(monthRange('2028-02-10')).toEqual({ from: '2028-02-01', to: '2028-02-29' });
  });

  it('previousMonthRange берёт прошлый календарный месяц, в том числе через Новый год', () => {
    expect(previousMonthRange('2026-10-25')).toEqual({ from: '2026-09-01', to: '2026-09-30' });
    expect(previousMonthRange('2026-01-15')).toEqual({ from: '2025-12-01', to: '2025-12-31' });
    expect(previousMonthRange('2026-03-31')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
  });
});

describe('форматирование', () => {
  it('formatDate показывает день.месяц.год и прочерк для пустого значения', () => {
    expect(formatDate('2026-10-25')).toBe('25.10.2026');
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
  });

  it('formatTime убирает секунды', () => {
    expect(formatTime('09:00:00')).toBe('09:00');
    expect(formatTime('18:30')).toBe('18:30');
    expect(formatTime(null)).toBe('—');
  });

  it('formatRange и formatDayTitle', () => {
    expect(formatRange('2026-10-01', '2026-10-31')).toBe('01.10.2026 – 31.10.2026');
    expect(formatDayTitle('2026-10-26')).toBe('Понедельник, 26.10.2026');
    expect(formatDayTitle('2026-10-25')).toBe('Воскресенье, 25.10.2026');
  });

  it('formatMoney: пробелы между разрядами, запятая, два знака, сом', () => {
    const normalize = (s: string) => s.replace(/\s/g, ' ');
    expect(normalize(formatMoney(1234.5))).toBe('1 234,50 сом');
    expect(normalize(formatMoney(0))).toBe('0,00 сом');
    expect(normalize(formatMoney(100))).toBe('100,00 сом');
    expect(normalize(formatMoney(-20.5))).toContain('20,50 сом');
    expect(normalize(formatMoney(1234567.891))).toBe('1 234 567,89 сом');
  });

  it('время показывается по Бишкеку (UTC+6) независимо от часового пояса компьютера', () => {
    expect(formatDateTime('2026-10-25T04:15:00Z')).toBe('25.10 10:15');
    expect(formatDateTime('2026-10-25T20:30:00Z')).toBe('26.10 02:30');
    expect(formatClock('2026-10-25T04:15:00Z')).toBe('10:15');
    expect(formatDateTime(null)).toBe('—');
    expect(formatClock(null)).toBe('—');
  });

  it('проценты и уверенность распознавания', () => {
    expect(formatPercent(0.666)).toBe('67%');
    expect(formatPercent(1)).toBe('100%');
    expect(formatPercent(0)).toBe('0%');
    expect(formatConfidence(0.93)).toBe('93%');
    expect(formatConfidence(null)).toBe('—');
  });
});
