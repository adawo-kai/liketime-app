import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, type Group, type Teacher } from '../api';
import { addDays, formatDayTitle, formatRange, formatTime, LESSON_STATUS_LABELS, mondayOf, toIsoDate } from '../format';
import type { GenerateResult, Lesson, LessonStatus, Room } from '../types';
import { Badge, ErrorBox, Field, FormModal, Modal, useActionRunner, useLoad, type Tone } from '../ui';

const STATUS_TONE: Record<LessonStatus, Tone> = { PLANNED: 'info', HELD: 'ok', CANCELLED: 'off' };

export default function SchedulePage() {
  const { me } = useAuth();
  const staff = me?.role !== 'TEACHER';
  const [weekStart, setWeekStart] = useState(() => mondayOf(toIsoDate(new Date())));
  const [groupId, setGroupId] = useState('');
  const [generating, setGenerating] = useState(false);
  const [creating, setCreating] = useState(false);
  const actions = useActionRunner();

  const weekEnd = addDays(weekStart, 6);
  const query = `from=${weekStart}&to=${weekEnd}${groupId ? `&groupId=${groupId}` : ''}`;
  const lessons = useLoad(() => api.get<Lesson[]>(`/api/lessons?${query}`), [query]);
  const groups = useLoad(() => (staff ? api.get<Group[]>('/api/groups') : Promise.resolve([] as Group[])), [staff]);

  const byDay = useMemo(() => {
    const map = new Map<string, Lesson[]>();
    for (const l of lessons.data ?? []) {
      map.set(l.date, [...(map.get(l.date) ?? []), l]);
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [lessons.data]);

  async function setStatus(lesson: Lesson, status: LessonStatus) {
    if (await actions.run(() => api.put(`/api/lessons/${lesson.id}/status`, { status }))) lessons.reload();
  }

  const today = toIsoDate(new Date());

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{staff ? 'Расписание' : 'Мои занятия'}</h1>
          <div className="muted">Неделя: {formatRange(weekStart, weekEnd)}</div>
        </div>
        {staff && (
          <div className="row-actions">
            <button className="btn ghost" onClick={() => setGenerating(true)}>Создать занятия по расписанию</button>
            <button className="btn" onClick={() => setCreating(true)}>+ Разовое занятие</button>
          </div>
        )}
      </div>

      <div className="toolbar">
        <button className="btn ghost small" onClick={() => setWeekStart(addDays(weekStart, -7))}>← Предыдущая</button>
        <button className="btn ghost small" onClick={() => setWeekStart(mondayOf(today))}>Эта неделя</button>
        <button className="btn ghost small" onClick={() => setWeekStart(addDays(weekStart, 7))}>Следующая →</button>
        {staff && (
          <label className="inline">Группа
            <select value={groupId} onChange={(e) => setGroupId(e.target.value)}>
              <option value="">Все группы</option>
              {(groups.data ?? []).filter((g) => g.status === 'ACTIVE').map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
          </label>
        )}
      </div>

      <ErrorBox message={lessons.error ?? actions.error} />
      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Время</th><th>Группа</th><th>Преподаватель</th><th>Кабинет</th><th className="num">Пришли</th><th>Статус</th><th /></tr>
          </thead>
          <tbody>
            {byDay.map(([date, items]) => (
              <DayRows key={date} date={date} items={items} staff={staff} today={today} onStatus={setStatus} />
            ))}
            {!lessons.loading && byDay.length === 0 && (
              <tr><td colSpan={7} className="muted">
                {staff ? 'На этой неделе занятий нет. Добавьте расписание группам и нажмите «Создать занятия по расписанию».'
                  : 'На этой неделе у вас нет занятий'}
              </td></tr>
            )}
          </tbody>
        </table>
      </div>

      {generating && (
        <GenerateModal defaultFrom={weekStart} defaultTo={weekEnd} groups={(groups.data ?? []).filter((g) => g.status === 'ACTIVE')}
                       onClose={() => setGenerating(false)} onDone={lessons.reload} />
      )}
      {creating && (
        <LessonForm groups={(groups.data ?? []).filter((g) => g.status === 'ACTIVE')} defaultDate={weekStart}
                    onClose={() => setCreating(false)} onSaved={lessons.reload} />
      )}
    </>
  );
}

function DayRows({ date, items, staff, today, onStatus }: {
  date: string; items: Lesson[]; staff: boolean; today: string; onStatus: (l: Lesson, s: LessonStatus) => void;
}) {
  return (
    <>
      <tr className="day-row"><td colSpan={7}>{formatDayTitle(date)}{date === today && ' · сегодня'}</td></tr>
      {items.map((l) => (
        <tr key={l.id}>
          <td className="nowrap">{formatTime(l.startTime)}–{formatTime(l.endTime)}</td>
          <td><Link to={`/lessons/${l.id}`}>{l.groupName}</Link>{l.note && <div className="muted small">{l.note}</div>}</td>
          <td>{l.teacherName ?? <Badge tone="warn">Не назначен</Badge>}</td>
          <td>{l.roomName ?? '—'}</td>
          <td className="num">{l.visits}</td>
          <td><Badge tone={STATUS_TONE[l.status]}>{LESSON_STATUS_LABELS[l.status]}</Badge></td>
          <td className="row-actions">
            <Link className="btn ghost small" to={`/lessons/${l.id}`}>Журнал</Link>
            {staff && l.status === 'PLANNED' && (
              <button className="btn ghost small" onClick={() => onStatus(l, 'CANCELLED')}>Отменить</button>
            )}
            {staff && l.status === 'CANCELLED' && (
              <button className="btn ghost small" onClick={() => onStatus(l, 'PLANNED')}>Вернуть</button>
            )}
          </td>
        </tr>
      ))}
    </>
  );
}

function GenerateModal({ defaultFrom, defaultTo, groups, onClose, onDone }: {
  defaultFrom: string; defaultTo: string; groups: Group[]; onClose: () => void; onDone: () => void;
}) {
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(defaultTo);
  const [groupId, setGroupId] = useState('');
  const [result, setResult] = useState<GenerateResult | null>(null);
  const actions = useActionRunner();

  async function generate() {
    const ok = await actions.run(async () => {
      setResult(await api.post<GenerateResult>('/api/schedule/generate', {
        from, to, groupId: groupId === '' ? null : Number(groupId),
      }));
    });
    if (ok) onDone();
  }

  if (result) {
    return (
      <Modal title="Занятия созданы" onClose={onClose}>
        <p>Создано занятий: <b>{result.created}</b>. Уже были: {result.alreadyExisted}.</p>
        {result.conflicts.length > 0 && (
          <div className="notice">
            Не создано из-за накладок ({result.conflicts.length}):
            <ul>{result.conflicts.map((c) => <li key={c}>{c}</li>)}</ul>
          </div>
        )}
        <div className="actions"><button className="btn" onClick={onClose}>Готово</button></div>
      </Modal>
    );
  }

  return (
    <Modal title="Создать занятия по расписанию" onClose={onClose}>
      <p className="muted small">
        Занятия создаются по шаблонам расписания групп. Уже существующие занятия не дублируются,
        поэтому можно запускать повторно.
      </p>
      <Field label="С"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required /></Field>
      <Field label="По"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} required /></Field>
      <Field label="Группа">
        <select value={groupId} onChange={(e) => setGroupId(e.target.value)}>
          <option value="">Все активные группы</option>
          {groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
        </select>
      </Field>
      <ErrorBox message={actions.error} />
      <div className="actions">
        <button className="btn ghost" onClick={onClose}>Отмена</button>
        <button className="btn" onClick={generate} disabled={actions.busy || !from || !to}>
          {actions.busy ? 'Создание…' : 'Создать'}
        </button>
      </div>
    </Modal>
  );
}

function LessonForm({ groups, defaultDate, onClose, onSaved }: {
  groups: Group[]; defaultDate: string; onClose: () => void; onSaved: () => void;
}) {
  const teachers = useLoad(() => api.get<Teacher[]>('/api/teachers'));
  const rooms = useLoad(() => api.get<Room[]>('/api/rooms'));
  const [groupId, setGroupId] = useState('');
  const [date, setDate] = useState(defaultDate);
  const [startTime, setStartTime] = useState('09:00');
  const [endTime, setEndTime] = useState('10:00');
  const [teacherId, setTeacherId] = useState('');
  const [roomId, setRoomId] = useState('');
  const [note, setNote] = useState('');

  async function submit() {
    if (groupId === '') throw new Error('Выберите группу');
    await api.post('/api/lessons', {
      groupId: Number(groupId), date, startTime, endTime,
      teacherId: teacherId === '' ? null : Number(teacherId),
      roomId: roomId === '' ? null : Number(roomId),
      note: note.trim() === '' ? null : note.trim(),
    });
    onSaved();
  }

  return (
    <FormModal title="Разовое занятие" submitLabel="Создать" onClose={onClose} onSubmit={submit}>
      <Field label="Группа">
        <select value={groupId} onChange={(e) => setGroupId(e.target.value)} required>
          <option value="">Выберите…</option>
          {groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
        </select>
      </Field>
      <Field label="Дата"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
      <div className="inline-form">
        <Field label="Начало"><input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required /></Field>
        <Field label="Конец"><input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} required /></Field>
      </div>
      <Field label="Преподаватель (по умолчанию из группы)">
        <select value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
          <option value="">Как у группы</option>
          {(teachers.data ?? []).filter((t) => t.status === 'ACTIVE').map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
        </select>
      </Field>
      <Field label="Кабинет">
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          <option value="">Не указан</option>
          {(rooms.data ?? []).map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select>
      </Field>
      <Field label="Заметка"><input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} /></Field>
    </FormModal>
  );
}
