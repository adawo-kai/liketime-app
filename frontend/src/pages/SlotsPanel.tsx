import { useState } from 'react';
import { api } from '../api';
import { formatTime, WEEKDAYS } from '../format';
import type { Room, Slot } from '../types';
import { Badge, ErrorBox, Field, FormModal, useActionRunner, useLoad } from '../ui';

/** Расписание группы: повторяющиеся занятия по дням недели. По ним создаются занятия на выбранный период. */
export default function SlotsPanel({ groupId, canEdit }: { groupId: string; canEdit: boolean }) {
  const [editing, setEditing] = useState<Slot | 'new' | null>(null);
  const actions = useActionRunner();

  const slots = useLoad(() => api.get<Slot[]>(`/api/schedule/slots?groupId=${groupId}`), [groupId]);
  const rooms = useLoad(() => (canEdit ? api.get<Room[]>('/api/rooms') : Promise.resolve([] as Room[])), [canEdit]);
  const roomName = (id: number | null) => rooms.data?.find((r) => r.id === id)?.name ?? '—';

  async function toggle(slot: Slot) {
    const ok = await actions.run(() => (slot.active
      ? api.del(`/api/schedule/slots/${slot.id}`)
      : api.put(`/api/schedule/slots/${slot.id}/activate`)));
    if (ok) slots.reload();
  }

  return (
    <>
      <div className="page-head" style={{ marginTop: 24 }}>
        <h2 style={{ margin: 0 }}>Расписание группы</h2>
        {canEdit && <button className="btn" onClick={() => setEditing('new')}>+ Добавить занятие</button>}
      </div>
      <ErrorBox message={slots.error ?? actions.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>День</th><th>Время</th>{canEdit && <th>Кабинет</th>}<th>Статус</th>{canEdit && <th />}</tr></thead>
          <tbody>
            {slots.data?.map((s) => (
              <tr key={s.id}>
                <td>{WEEKDAYS[s.weekday - 1]}</td>
                <td className="nowrap">{formatTime(s.startTime)}–{formatTime(s.endTime)}</td>
                {canEdit && <td>{roomName(s.roomId)}</td>}
                <td><Badge tone={s.active ? 'ok' : 'off'}>{s.active ? 'Действует' : 'Отключено'}</Badge></td>
                {canEdit && (
                  <td className="row-actions">
                    <button className="btn ghost small" onClick={() => setEditing(s)}>Изменить</button>
                    <button className="btn ghost small" onClick={() => toggle(s)}>{s.active ? 'Отключить' : 'Включить'}</button>
                  </td>
                )}
              </tr>
            ))}
            {slots.data?.length === 0 && (
              <tr><td colSpan={5} className="muted">Расписания пока нет{canEdit && ': добавьте дни и время занятий'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && (
        <SlotForm groupId={groupId} slot={editing === 'new' ? null : editing} rooms={rooms.data ?? []}
                  onClose={() => setEditing(null)} onSaved={slots.reload} />
      )}
    </>
  );
}

function SlotForm({ groupId, slot, rooms, onClose, onSaved }: {
  groupId: string; slot: Slot | null; rooms: Room[]; onClose: () => void; onSaved: () => void;
}) {
  const [weekday, setWeekday] = useState(String(slot?.weekday ?? 1));
  const [startTime, setStartTime] = useState(slot ? formatTime(slot.startTime) : '09:00');
  const [endTime, setEndTime] = useState(slot ? formatTime(slot.endTime) : '10:00');
  const [roomId, setRoomId] = useState(slot?.roomId ? String(slot.roomId) : '');

  async function submit() {
    const body = {
      groupId: Number(groupId), weekday: Number(weekday), startTime, endTime,
      roomId: roomId === '' ? null : Number(roomId),
    };
    if (slot) await api.put(`/api/schedule/slots/${slot.id}`, body);
    else await api.post('/api/schedule/slots', body);
    onSaved();
  }

  return (
    <FormModal title={slot ? 'Изменить занятие в расписании' : 'Добавить занятие в расписание'}
               submitLabel={slot ? 'Сохранить' : 'Добавить'} onClose={onClose} onSubmit={submit}>
      <Field label="День недели">
        <select value={weekday} onChange={(e) => setWeekday(e.target.value)}>
          {WEEKDAYS.map((name, i) => <option key={name} value={i + 1}>{name}</option>)}
        </select>
      </Field>
      <div className="inline-form">
        <Field label="Начало"><input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required /></Field>
        <Field label="Конец"><input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} required /></Field>
      </div>
      <Field label="Кабинет">
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          <option value="">Не указан</option>
          {rooms.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select>
      </Field>
    </FormModal>
  );
}
