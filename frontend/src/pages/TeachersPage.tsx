import { useState } from 'react';
import { useAuth } from '../auth';
import { api, type Teacher, type UserRow } from '../api';
import { blankToNull, errorText, ErrorBox, Field, FormModal, StatusBadge, useLoad } from '../ui';

export default function TeachersPage() {
  const { me } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  const [editing, setEditing] = useState<Teacher | 'new' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const teachers = useLoad(() => api.get<Teacher[]>('/api/teachers'));
  // Список пользователей доступен только администратору, поэтому привязывать учётную запись может только он
  const users = useLoad(() => (isAdmin ? api.get<UserRow[]>('/api/users') : Promise.resolve([] as UserRow[])), [isAdmin]);

  async function toggle(t: Teacher) {
    setActionError(null);
    try {
      if (t.status === 'ACTIVE') await api.del(`/api/teachers/${t.id}`);
      else await api.put(`/api/teachers/${t.id}/activate`);
      teachers.reload();
    } catch (e) { setActionError(errorText(e)); }
  }

  const loginOf = (userId: number | null) => users.data?.find((u) => u.id === userId)?.login;

  return (
    <>
      <div className="page-head">
        <h1>Преподаватели</h1>
        <button className="btn" onClick={() => setEditing('new')}>+ Добавить преподавателя</button>
      </div>
      <ErrorBox message={teachers.error ?? actionError} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Имя</th><th>Телефон</th><th>Учётная запись</th><th>Статус</th><th /></tr></thead>
          <tbody>
            {teachers.data?.map((t) => (
              <tr key={t.id}>
                <td>{t.fullName}</td>
                <td>{t.phone ?? '—'}</td>
                <td>{t.userId === null ? 'Нет доступа' : (loginOf(t.userId) ?? 'Привязана')}</td>
                <td><StatusBadge active={t.status === 'ACTIVE'} /></td>
                <td className="row-actions">
                  <button className="btn ghost small" onClick={() => setEditing(t)}>Изменить</button>
                  <button className="btn ghost small" onClick={() => toggle(t)}>
                    {t.status === 'ACTIVE' ? 'Отключить' : 'Включить'}
                  </button>
                </td>
              </tr>
            ))}
            {teachers.data?.length === 0 && <tr><td colSpan={5} className="muted">Преподавателей пока нет</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && (
        <TeacherForm teacher={editing === 'new' ? null : editing} users={users.data ?? []} canLink={isAdmin}
                     takenUserIds={(teachers.data ?? []).map((t) => t.userId)}
                     onClose={() => setEditing(null)} onSaved={teachers.reload} />
      )}
    </>
  );
}

function TeacherForm({ teacher, users, canLink, takenUserIds, onClose, onSaved }: {
  teacher: Teacher | null; users: UserRow[]; canLink: boolean; takenUserIds: (number | null)[];
  onClose: () => void; onSaved: () => void;
}) {
  const [fullName, setFullName] = useState(teacher?.fullName ?? '');
  const [phone, setPhone] = useState(teacher?.phone ?? '');
  const [userId, setUserId] = useState<string>(teacher?.userId?.toString() ?? '');

  // Свободные учётные записи с ролью TEACHER (плюс текущая привязка при редактировании)
  const options = users.filter((u) => u.role === 'TEACHER'
    && (u.id === teacher?.userId || !takenUserIds.includes(u.id)));

  async function submit() {
    const body = {
      fullName: fullName.trim(), phone: blankToNull(phone),
      // Менеджер не видит список пользователей и не должен сбрасывать существующую привязку
      userId: canLink ? (userId === '' ? null : Number(userId)) : (teacher?.userId ?? null),
    };
    if (teacher) await api.put(`/api/teachers/${teacher.id}`, body);
    else await api.post('/api/teachers', body);
    onSaved();
  }

  return (
    <FormModal title={teacher ? 'Изменить преподавателя' : 'Новый преподаватель'} submitLabel="Сохранить"
               onClose={onClose} onSubmit={submit}>
      <Field label="ФИО"><input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={150} required autoFocus /></Field>
      <Field label="Телефон"><input value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={20} /></Field>
      {canLink && (
        <Field label="Учётная запись для входа">
          <select value={userId} onChange={(e) => setUserId(e.target.value)}>
            <option value="">Без доступа в систему</option>
            {options.map((u) => <option key={u.id} value={u.id}>{u.login} — {u.fullName}</option>)}
          </select>
        </Field>
      )}
    </FormModal>
  );
}
