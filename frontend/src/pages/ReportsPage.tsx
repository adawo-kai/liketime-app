import { useState } from 'react';
import { api, downloadFile, type Group } from '../api';
import { addDays, formatPercent, monthRange, toIsoDate } from '../format';
import type { AttendanceReportRow } from '../types';
import { ErrorBox, useActionRunner, useLoad } from '../ui';

/** Отчёт по посещаемости: сколько занятий было у каждого ученика и как он на них ходил. */
export default function ReportsPage() {
  const today = toIsoDate(new Date());
  const [from, setFrom] = useState(monthRange(today).from);
  const [to, setTo] = useState(today);
  const [groupId, setGroupId] = useState('');
  const actions = useActionRunner();

  const query = `from=${from}&to=${to}${groupId ? `&groupId=${groupId}` : ''}`;
  const groups = useLoad(() => api.get<Group[]>('/api/groups'));
  const report = useLoad(() => api.get<AttendanceReportRow[]>(`/api/reports/attendance?${query}`), [query]);

  const rows = report.data ?? [];
  const totals = rows.reduce((t, r) => ({
    lessons: t.lessons + r.lessons, visits: t.visits + r.present + r.late, absent: t.absent + r.absent,
  }), { lessons: 0, visits: 0, absent: 0 });

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Отчёты</h1>
          <div className="muted">Посещаемость учеников за период. В отчёт входят начавшиеся занятия, отменённые не считаются.</div>
        </div>
        <button className="btn ghost" onClick={() => actions.run(() => downloadFile(`/api/reports/attendance.csv?${query}`, 'attendance.csv'))}>
          Скачать (CSV)
        </button>
      </div>
      <div className="toolbar">
        <label className="inline">С <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="inline">По <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <label className="inline">Группа
          <select value={groupId} onChange={(e) => setGroupId(e.target.value)}>
            <option value="">Все группы</option>
            {(groups.data ?? []).map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        </label>
        <button className="btn ghost small" onClick={() => { setFrom(addDays(today, -6)); setTo(today); }}>7 дней</button>
        <button className="btn ghost small" onClick={() => { const m = monthRange(today); setFrom(m.from); setTo(today); }}>Этот месяц</button>
      </div>
      <ErrorBox message={report.error ?? actions.error} />
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Группа</th><th>Ученик</th><th className="num">Занятий</th><th className="num">Пришёл</th><th className="num">Опоздал</th>
              <th className="num">Не пришёл</th><th className="num">Уважит.</th><th className="num">Не отмечено</th><th className="num">Посещаемость</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={`${r.groupId}-${r.studentId}`}>
                <td>{r.groupName}</td><td>{r.studentName}</td>
                <td className="num">{r.lessons}</td><td className="num">{r.present}</td><td className="num">{r.late}</td>
                <td className="num">{r.absent}</td><td className="num">{r.excused}</td>
                <td className="num">{r.unmarked > 0 ? <span title="Занятия без отметки">{r.unmarked}</span> : 0}</td>
                <td className="num"><b>{formatPercent(r.rate)}</b></td>
              </tr>
            ))}
            {!report.loading && rows.length === 0 && <tr><td colSpan={9} className="muted">За выбранный период занятий нет</td></tr>}
          </tbody>
          {rows.length > 0 && (
            <tfoot>
              <tr>
                <td colSpan={2}>Итого</td><td className="num">{totals.lessons}</td><td colSpan={5} />
                <td className="num">{totals.lessons === 0 ? '—' : formatPercent(totals.visits / totals.lessons)}</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </>
  );
}
