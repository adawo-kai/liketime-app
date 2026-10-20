import { useState } from 'react';
import { useAuth } from '../auth';
import { api, ROLE_LABELS, type Role, type UserRow } from '../api';
import { errorText, ErrorBox, Field, FormModal, StatusBadge, useLoad } from '../ui';

const ROLES: Role[] = ['ADMIN', 'MANAGER', 'TEACHER'];

export default function UsersPage() {
  const { me } = useAuth();
  const users = useLoad(() => api.get<UserRow[]>('/api/users'));
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<UserRow | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function patch(u: UserRow, body: { role?: Role; enabled?: boolean }) {
    setActionError(null);
    try {
      await api.patch(`/api/users/${u.id}`, body);
      users.reload();
    } catch (e) { setActionError(errorText(e)); users.reload(); }
  }

  return (
    <>
      <div className="page-head">
        <h1>Пользователи</h1>
        <button className="btn" onClick={() => setCreating(true)}>+ Создать пользователя</button>
      </div>
      <ErrorBox message={users.error ?? actionError} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Логин</th><th>Имя</th><th>Роль</th><th>Статус</th><th /></tr></thead>
          <tbody>
            {users.data?.map((u) => {
              const self = u.login === me?.login;
              return (
                <tr key={u.id}>
                  <td>{u.login}{self && <span className="muted small"> (вы)</span>}</td>
                  <td>{u.fullName}</td>
                  <td>
                    <select value={u.role} disabled={self} onChange={(e) => patch(u, { role: e.target.value as Role })}>
                      {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                    </select>
                  </td>
                  <td><StatusBadge active={u.enabled} /></td>
                  <td className="row-actions">
                    <button className="btn ghost small" onClick={() => setResetting(u)}>Сбросить пароль</button>
                    {!self && (
                      <button className="btn ghost small" onClick={() => patch(u, { enabled: !u.enabled })}>
                        {u.enabled ? 'Отключить' : 'Включить'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {creating && <CreateUserForm onClose={() => setCreating(false)} onSaved={users.reload} />}
      {resetting && <ResetPasswordForm user={resetting} onClose={() => setResetting(null)} />}
    </>
  );
}

function CreateUserForm({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [login, setLogin] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<Role>('TEACHER');
  const [password, setPassword] = useState('');

  async function submit() {
    await api.post('/api/users', { login: login.trim(), fullName: fullName.trim(), role, password });
    onSaved();
  }

  return (
    <FormModal title="Новый пользователь" submitLabel="Создать" onClose={onClose} onSubmit={submit}>
      <Field label="Логин (a-z, 0-9, . _ -, от 3 символов)">
        <input value={login} onChange={(e) => setLogin(e.target.value)} pattern="[a-z0-9._\-]{3,64}" required autoFocus />
      </Field>
      <Field label="Имя"><input value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={150} required /></Field>
      <Field label="Роль">
        <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
      </Field>
      <Field label="Пароль (10–72 символа)">
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
               minLength={10} maxLength={72} autoComplete="new-password" required />
      </Field>
    </FormModal>
  );
}

function ResetPasswordForm({ user, onClose }: { user: UserRow; onClose: () => void }) {
  const [password, setPassword] = useState('');

  return (
    <FormModal title={`Новый пароль для ${user.login}`} submitLabel="Сменить пароль" onClose={onClose}
               onSubmit={() => api.put(`/api/users/${user.id}/password`, { password })}>
      <Field label="Новый пароль (10–72 символа)">
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
               minLength={10} maxLength={72} autoComplete="new-password" required autoFocus />
      </Field>
    </FormModal>
  );
}
