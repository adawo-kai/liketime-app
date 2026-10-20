import { useState } from 'react';
import { api, type Student } from '../api';
import { blankToNull, errorText, ErrorBox, Field, FormModal, StatusBadge, useLoad } from '../ui';

export default function StudentsPage() {
  const [query, setQuery] = useState('');
  const [onlyActive, setOnlyActive] = useState(true);
  const [editing, setEditing] = useState<Student | 'new' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, error, loading, reload } = useLoad(
    () => api.get<Student[]>(`/api/students?q=${encodeURIComponent(query)}${onlyActive ? '&status=ACTIVE' : ''}`),
    [query, onlyActive],
  );

  async function toggle(s: Student) {
    setActionError(null);
    try {
      if (s.status === 'ACTIVE') await api.del(`/api/students/${s.id}`);
      else await api.put(`/api/students/${s.id}/activate`);
      reload();
    } catch (e) { setActionError(errorText(e)); }
  }

  return (
    <>
      <div className="page-head">
        <h1>Ученики</h1>
        <button className="btn" onClick={() => setEditing('new')}>+ Добавить ученика</button>
      </div>
      <div className="toolbar">
        <input placeholder="Поиск по имени" value={query} onChange={(e) => setQuery(e.target.value)} />
        <label className="check"><input type="checkbox" checked={onlyActive}
          onChange={(e) => setOnlyActive(e.target.checked)} /> Только активные</label>
      </div>
      <ErrorBox message={error ?? actionError} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Имя</th><th>Телефон</th><th>Родитель</th><th>Статус</th><th /></tr></thead>
          <tbody>
            {data?.map((s) => (
              <tr key={s.id}>
                <td>{s.fullName}</td>
                <td>{s.phone ?? '—'}</td>
                <td>{s.parentName ?? '—'}{s.parentPhone ? `, ${s.parentPhone}` : ''}</td>
                <td><StatusBadge active={s.status === 'ACTIVE'} /></td>
                <td className="row-actions">
                  <button className="btn ghost small" onClick={() => setEditing(s)}>Изменить</button>
                  <button className="btn ghost small" onClick={() => toggle(s)}>
                    {s.status === 'ACTIVE' ? 'Отключить' : 'Включить'}
                  </button>
                </td>
              </tr>
            ))}
            {data?.length === 0 && <tr><td colSpan={5} className="muted">Никого не найдено</td></tr>}
            {loading && !data && <tr><td colSpan={5} className="muted">Загрузка…</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && (
        <StudentForm student={editing === 'new' ? null : editing}
                     onClose={() => setEditing(null)} onSaved={reload} />
      )}
    </>
  );
}

function StudentForm({ student, onClose, onSaved }: { student: Student | null; onClose: () => void; onSaved: () => void }) {
  const [fullName, setFullName] = useState(student?.fullName ?? '');
  const [phone, setPhone] = useState(student?.phone ?? '');
  const [parentName, setParentName] = useState(student?.parentName ?? '');
  const [parentPhone, setParentPhone] = useState(student?.parentPhone ?? '');

  async function submit() {
    const body = {
      fullName: fullName.trim(), phone: blankToNull(phone),
      parentName: blankToNull(parentName), parentPhone: blankToNull(parentPhone),
    };
    if (student) await api.put(`/api/students/${student.id}`, body);
    else await api.post('/api/students', body);
    onSaved();
  }

  return (
    <FormModal title={student ? 'Изменить ученика' : 'Новый ученик'} submitLabel="Сохранить"
               onClose={onClose} onSubmit={submit}>
      <Field label="ФИО"><input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={150} required autoFocus /></Field>
      <Field label="Телефон"><input value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={20} /></Field>
      <Field label="Родитель"><input value={parentName} onChange={(e) => setParentName(e.target.value)} maxLength={150} /></Field>
      <Field label="Телефон родителя"><input value={parentPhone} onChange={(e) => setParentPhone(e.target.value)} maxLength={20} /></Field>
    </FormModal>
  );
}
