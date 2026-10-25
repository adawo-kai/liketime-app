import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth';
import { api } from '../api';
import { ATTENDANCE_LABELS, formatClock, formatDate, formatTime, LESSON_STATUS_LABELS } from '../format';
import type { AttendanceRow, AttendanceStatus, Lesson, LessonStatus, Roster } from '../types';
import { Badge, ErrorBox, useActionRunner, useLoad } from '../ui';

const STATUSES: AttendanceStatus[] = ['PRESENT', 'LATE', 'ABSENT', 'EXCUSED'];

export default function LessonPage() {
  const { id } = useParams();
  const { me } = useAuth();
  const staff = me?.role !== 'TEACHER';
  const actions = useActionRunner();

  const lesson = useLoad(() => api.get<Lesson>(`/api/lessons/${id}`), [id]);
  const roster = useLoad(() => api.get<Roster>(`/api/lessons/${id}/attendance`), [id]);

  const cancelled = lesson.data?.status === 'CANCELLED';
  const rows = roster.data?.students ?? [];
  const unmarkedMembers = rows.filter((r) => r.member && r.status === null);

  function reloadAll() {
    lesson.reload();
    roster.reload();
  }

  async function mark(row: AttendanceRow, status: AttendanceStatus, note?: string | null) {
    const text = note !== undefined ? note : row.note;
    if (await actions.run(() => api.put(`/api/lessons/${id}/attendance/${row.studentId}`, { status, note: text }))) reloadAll();
  }

  async function clear(row: AttendanceRow) {
    if (await actions.run(() => api.del(`/api/lessons/${id}/attendance/${row.studentId}`))) reloadAll();
  }

  async function markAllPresent() {
    const records = unmarkedMembers.map((r) => ({ studentId: r.studentId, status: 'PRESENT' as const }));
    if (records.length === 0) return;
    if (await actions.run(() => api.put(`/api/lessons/${id}/attendance`, { records }))) reloadAll();
  }

  async function setStatus(status: LessonStatus) {
    if (await actions.run(() => api.put(`/api/lessons/${id}/status`, { status }))) reloadAll();
  }

  const l = lesson.data;
  return (
    <>
      <p><Link to="/schedule">← К расписанию</Link></p>
      <ErrorBox message={lesson.error ?? roster.error ?? actions.error} />
      {l && (
        <div className="page-head">
          <div>
            <h1>{l.groupName}</h1>
            <div className="muted">
              {formatDate(l.date)}, {formatTime(l.startTime)}–{formatTime(l.endTime)}
              {' · '}{l.teacherName ?? 'преподаватель не назначен'}
              {l.roomName && <> · {l.roomName}</>}
            </div>
            <div style={{ marginTop: 8 }}>
              <Badge tone={l.status === 'HELD' ? 'ok' : l.status === 'CANCELLED' ? 'off' : 'info'}>
                {LESSON_STATUS_LABELS[l.status]}
              </Badge>
            </div>
          </div>
          <div className="row-actions">
            {l.status === 'PLANNED' && <button className="btn ghost" onClick={() => setStatus('HELD')}>Занятие проведено</button>}
            {staff && l.status === 'PLANNED' && <button className="btn ghost danger" onClick={() => setStatus('CANCELLED')}>Отменить занятие</button>}
            {staff && l.status === 'CANCELLED' && <button className="btn ghost" onClick={() => setStatus('PLANNED')}>Вернуть в план</button>}
          </div>
        </div>
      )}

      {l && !l.teacherId && (
        <div className="notice">
          Занятию не назначен преподаватель, поэтому оно не попадёт в расчёт зарплаты. Назначьте преподавателя группе или занятию.
        </div>
      )}
      {cancelled && <div className="notice info">Занятие отменено, посещаемость не ведётся.</div>}

      {!cancelled && (
        <div className="toolbar">
          <button className="btn" onClick={markAllPresent} disabled={unmarkedMembers.length === 0 || actions.busy}>
            Отметить всех пришедшими ({unmarkedMembers.length})
          </button>
          <span className="muted small">Пришли: {rows.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length} из {rows.filter((r) => r.member).length}</span>
        </div>
      )}

      <div className="card table-wrap">
        <table>
          <thead><tr><th>Ученик</th><th>Отметка</th><th>Заметка</th><th>Источник</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <AttendanceLine key={r.studentId} row={r} disabled={cancelled || actions.busy}
                              onMark={mark} onClear={clear} />
            ))}
            {!roster.loading && rows.length === 0 && (
              <tr><td colSpan={5} className="muted">В группе на эту дату нет учеников</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

function AttendanceLine({ row, disabled, onMark, onClear }: {
  row: AttendanceRow; disabled: boolean;
  onMark: (row: AttendanceRow, status: AttendanceStatus, note?: string | null) => void;
  onClear: (row: AttendanceRow) => void;
}) {
  const [note, setNote] = useState(row.note ?? '');

  function saveNote() {
    const text = note.trim();
    if (row.status !== null && text !== (row.note ?? '')) onMark(row, row.status, text === '' ? null : text);
  }

  return (
    <tr>
      <td>
        {row.fullName}
        {!row.member && <div className="muted small">Вышел из группы</div>}
      </td>
      <td>
        <div className="status-btns">
          {STATUSES.map((s) => (
            <button key={s} type="button" disabled={disabled} aria-pressed={row.status === s}
                    className={row.status === s ? `on ${s}` : ''} onClick={() => onMark(row, s, note.trim() === '' ? null : note.trim())}>
              {ATTENDANCE_LABELS[s]}
            </button>
          ))}
        </div>
      </td>
      <td>
        <input className="note" value={note} maxLength={255} disabled={disabled || row.status === null}
               placeholder={row.status === null ? 'Сначала отметьте' : 'Заметка'} aria-label={`Заметка: ${row.fullName}`}
               onChange={(e) => setNote(e.target.value)} onBlur={saveNote} />
      </td>
      <td className="nowrap">
        {row.source === 'DEVICE' && <Badge tone="info">Устройство {formatClock(row.checkInAt)}</Badge>}
        {row.source === 'MANUAL' && <Badge tone="off">Вручную</Badge>}
      </td>
      <td className="row-actions">
        {row.status !== null && <button className="btn ghost small" disabled={disabled} onClick={() => onClear(row)}>Снять</button>}
      </td>
    </tr>
  );
}
