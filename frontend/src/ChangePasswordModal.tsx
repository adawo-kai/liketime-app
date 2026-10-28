import { useState, type FormEvent } from 'react';
import { changePassword } from './api';
import { validateNewPassword } from './password';
import { errorText, ErrorBox, Field, Modal } from './ui';

/** Окно «Сменить пароль»: доступно любому вошедшему пользователю. */
export default function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const problem = validateNewPassword(current, next, repeat);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await changePassword(current, next);
      setDone(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <Modal title="Пароль изменён" onClose={onClose}>
        <p>Новый пароль сохранён. На других устройствах, где вы были в системе, нужно будет войти заново.</p>
        <div className="actions"><button className="btn" onClick={onClose}>Готово</button></div>
      </Modal>
    );
  }

  return (
    <Modal title="Сменить пароль" onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Текущий пароль">
          <input type="password" autoComplete="current-password" value={current}
                 onChange={(e) => setCurrent(e.target.value)} autoFocus />
        </Field>
        <Field label="Новый пароль (не короче 10 символов)">
          <input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </Field>
        <Field label="Повторите новый пароль">
          <input type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
        </Field>
        <ErrorBox message={error} />
        <div className="actions">
          <button type="button" className="btn ghost" onClick={onClose}>Отмена</button>
          <button type="submit" className="btn" disabled={busy}>{busy ? 'Сохранение…' : 'Сменить пароль'}</button>
        </div>
      </form>
    </Modal>
  );
}
