import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth';
import { ApiError } from '../api';
import { ErrorBox } from '../ui';

export default function LoginPage() {
  const { signIn } = useAuth();
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(login.trim(), password);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401
        ? 'Неверный логин или пароль'
        : err instanceof ApiError ? err.message : 'Не удалось войти');
      setBusy(false);
    }
  }

  return (
    <div className="center">
      <form className="card login" onSubmit={submit}>
        <h1>LikeTime</h1>
        <p className="muted">Учёт посещаемости и зарплаты</p>
        <label className="field"><span>Логин</span>
          <input value={login} onChange={(e) => setLogin(e.target.value)} autoComplete="username" autoFocus required />
        </label>
        <label className="field"><span>Пароль</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete="current-password" required />
        </label>
        <ErrorBox message={error} />
        <button className="btn wide" type="submit" disabled={busy}>{busy ? 'Вход…' : 'Войти'}</button>
      </form>
    </div>
  );
}
