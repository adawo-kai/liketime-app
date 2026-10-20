import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, type Group, type Teacher } from '../api';
import { errorText, ErrorBox, Field, FormModal, StatusBadge, useLoad } from '../ui';

export default function GroupsPage() {
  const { me } = useAuth();
  const staff = me?.role !== 'TEACHER';
  const [creating, setCreating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const groups = useLoad(() => api.get<Group[]>('/api/groups'));
  const teachers = useLoad(() => (staff ? api.get<Teacher[]>('/api/teachers') : Promise.resolve([] as Teacher[])), [staff]);
  const teacherName = (id: number | null) => teachers.data?.find((t) => t.id === id)?.fullName ?? '—';

  async function toggle(g: Group) {
    setActionError(null);
    try {
      if (g.status === 'ACTIVE') await api.del(`/api/groups/${g.id}`);
      else await api.put(`/api/groups/${g.id}/restore`);
      groups.reload();
    } catch (e) { setActionError(errorText(e)); }
  }

  return (
    <>
      <div className="page-head">
        <h1>{staff ? 'Группы' : 'Мои группы'}</h1>
        {staff && <button className="btn" onClick={() => setCreating(true)}>+ Создать группу</button>}
      </div>
      <ErrorBox message={groups.error ?? actionError} />
      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Название</th>{staff && <th>Преподаватель</th>}<th>Учеников</th><th>Статус</th>{staff && <th />}</tr>
          </thead>
          <tbody>
            {groups.data?.map((g) => (
              <tr key={g.id}>
                <td><Link to={`/groups/${g.id}`}>{g.name}</Link></td>
                {staff && <td>{teacherName(g.teacherId)}</td>}
                <td>{g.activeStudents}</td>
                <td><StatusBadge active={g.status === 'ACTIVE'} /></td>
                {staff && (
                  <td className="row-actions">
                    <button className="btn ghost small" onClick={() => toggle(g)}>
                      {g.status === 'ACTIVE' ? 'В архив' : 'Восстановить'}
                    </button>
                  </td>
                )}
              </tr>
            ))}
            {groups.data?.length === 0 && (
              <tr><td colSpan={5} className="muted">{staff ? 'Групп пока нет' : 'За вами пока не закреплено ни одной группы'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {creating && (
        <GroupForm teachers={(teachers.data ?? []).filter((t) => t.status === 'ACTIVE')}
                   onClose={() => setCreating(false)} onSaved={groups.reload} />
      )}
    </>
  );
}

function GroupForm({ teachers, onClose, onSaved }: { teachers: Teacher[]; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState('');
  const [teacherId, setTeacherId] = useState('');

  async function submit() {
    await api.post('/api/groups', { name: name.trim(), teacherId: teacherId === '' ? null : Number(teacherId) });
    onSaved();
  }

  return (
    <FormModal title="Новая группа" submitLabel="Создать" onClose={onClose} onSubmit={submit}>
      <Field label="Название"><input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus /></Field>
      <Field label="Преподаватель">
        <select value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
          <option value="">Не назначен</option>
          {teachers.map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
        </select>
      </Field>
    </FormModal>
  );
}
