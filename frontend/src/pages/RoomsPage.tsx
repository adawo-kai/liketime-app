import { useState } from 'react';
import { api } from '../api';
import type { Room } from '../types';
import { ErrorBox, Field, FormModal, useLoad } from '../ui';

export default function RoomsPage() {
  const [editing, setEditing] = useState<Room | 'new' | null>(null);
  const rooms = useLoad(() => api.get<Room[]>('/api/rooms'));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Кабинеты</h1>
          <div className="muted">Кабинет можно указать в расписании группы и в занятии. Система не даст поставить два занятия в один кабинет одновременно.</div>
        </div>
        <button className="btn" onClick={() => setEditing('new')}>+ Добавить кабинет</button>
      </div>
      <ErrorBox message={rooms.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Название</th><th /></tr></thead>
          <tbody>
            {rooms.data?.map((r) => (
              <tr key={r.id}>
                <td>{r.name}</td>
                <td className="row-actions"><button className="btn ghost small" onClick={() => setEditing(r)}>Переименовать</button></td>
              </tr>
            ))}
            {rooms.data?.length === 0 && <tr><td colSpan={2} className="muted">Кабинетов пока нет</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && <RoomForm room={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={rooms.reload} />}
    </>
  );
}

function RoomForm({ room, onClose, onSaved }: { room: Room | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(room?.name ?? '');

  async function submit() {
    if (room) await api.put(`/api/rooms/${room.id}`, { name: name.trim() });
    else await api.post('/api/rooms', { name: name.trim() });
    onSaved();
  }

  return (
    <FormModal title={room ? 'Переименовать кабинет' : 'Новый кабинет'} submitLabel={room ? 'Сохранить' : 'Добавить'}
               onClose={onClose} onSubmit={submit}>
      <Field label="Название"><input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus /></Field>
    </FormModal>
  );
}
