import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, type Group, type Member, type Student, type Teacher } from '../api';
import { errorText, ErrorBox, Field, FormModal, useLoad } from '../ui';

export default function GroupDetailPage() {
  const { id } = useParams();
  const { me } = useAuth();
  const staff = me?.role !== 'TEACHER';
  const [showHistory, setShowHistory] = useState(false);
  const [adding, setAdding] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const group = useLoad(() => api.get<Group>(`/api/groups/${id}`), [id]);
  const members = useLoad(() => api.get<Member[]>(`/api/groups/${id}/students?activeOnly=${!showHistory}`), [id, showHistory]);
  const teachers = useLoad(() => (staff ? api.get<Teacher[]>('/api/teachers') : Promise.resolve([] as Teacher[])), [staff]);

  const active = group.data?.status === 'ACTIVE';
  const teacher = teachers.data?.find((t) => t.id === group.data?.teacherId);

  async function remove(m: Member) {
    setActionError(null);
    try {
      await api.del(`/api/groups/${id}/students/${m.studentId}`);
      members.reload();
      group.reload();
    } catch (e) { setActionError(errorText(e)); }
  }

  function refresh() { members.reload(); group.reload(); }

  return (
    <>
      <p><Link to="/groups">← К списку групп</Link></p>
      <ErrorBox message={group.error ?? members.error ?? actionError} />
      {group.data && (
        <div className="page-head">
          <div>
            <h1>{group.data.name}</h1>
            <div className="muted">
              {staff && <>Преподаватель: {teacher?.fullName ?? 'не назначен'} · </>}
              Учеников: {group.data.activeStudents}{!active && ' · В архиве'}
            </div>
          </div>
          {staff && active && <button className="btn" onClick={() => setAdding(true)}>+ Добавить ученика</button>}
        </div>
      )}
      <div className="toolbar">
        <label className="check"><input type="checkbox" checked={showHistory}
          onChange={(e) => setShowHistory(e.target.checked)} /> Показать вышедших (история)</label>
      </div>
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Ученик</th><th>В группе с</th><th>Вышел</th>{staff && <th />}</tr></thead>
          <tbody>
            {members.data?.map((m) => (
              <tr key={`${m.studentId}-${m.joinedOn}`}>
                <td>{m.fullName}</td>
                <td>{m.joinedOn}</td>
                <td>{m.leftOn ?? '—'}</td>
                {staff && (
                  <td className="row-actions">
                    {m.leftOn === null && <button className="btn ghost small" onClick={() => remove(m)}>Убрать</button>}
                  </td>
                )}
              </tr>
            ))}
            {members.data?.length === 0 && <tr><td colSpan={4} className="muted">В группе пока нет учеников</td></tr>}
          </tbody>
        </table>
      </div>
      {adding && <AddStudentForm groupId={id!} currentIds={(members.data ?? []).filter((m) => m.leftOn === null).map((m) => m.studentId)}
                                 onClose={() => setAdding(false)} onSaved={refresh} />}
    </>
  );
}

function AddStudentForm({ groupId, currentIds, onClose, onSaved }: {
  groupId: string; currentIds: number[]; onClose: () => void; onSaved: () => void;
}) {
  const students = useLoad(() => api.get<Student[]>('/api/students?status=ACTIVE'));
  const [studentId, setStudentId] = useState('');
  const [joinedOn, setJoinedOn] = useState('');

  const options = (students.data ?? []).filter((s) => !currentIds.includes(s.id));

  async function submit() {
    if (studentId === '') throw new Error('Выберите ученика');
    await api.post(`/api/groups/${groupId}/students`, {
      studentId: Number(studentId), joinedOn: joinedOn === '' ? null : joinedOn,
    });
    onSaved();
  }

  return (
    <FormModal title="Добавить ученика в группу" submitLabel="Добавить" onClose={onClose} onSubmit={submit}>
      <ErrorBox message={students.error} />
      <Field label="Ученик">
        <select value={studentId} onChange={(e) => setStudentId(e.target.value)} required>
          <option value="">Выберите…</option>
          {options.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
        </select>
      </Field>
      <Field label="Дата вступления (по умолчанию сегодня)">
        <input type="date" value={joinedOn} onChange={(e) => setJoinedOn(e.target.value)} />
      </Field>
    </FormModal>
  );
}
