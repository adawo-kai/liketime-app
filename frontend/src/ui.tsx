import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from './api';

export function errorText(e: unknown): string {
  return e instanceof ApiError || e instanceof Error ? e.message : 'Что-то пошло не так';
}

/** Загружает данные при монтировании и по reload(). */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps);
  const reload = useCallback(() => {
    setLoading(true);
    run()
      .then((d) => { setData(d); setError(null); })
      .catch((e) => setError(errorText(e)))
      .finally(() => setLoading(false));
  }, [run]);

  useEffect(reload, [reload]);
  return { data, error, loading, reload };
}

export function ErrorBox({ message }: { message: string | null }) {
  return message ? <div className="alert" role="alert">{message}</div> : null;
}

export function StatusBadge({ active }: { active: boolean }) {
  return <span className={active ? 'badge ok' : 'badge off'}>{active ? 'Активен' : 'Неактивен'}</span>;
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="field"><span>{label}</span>{children}</label>;
}

/** Форма в модальном окне: показывает ошибку сервера и блокирует кнопку во время отправки. */
export function FormModal({ title, submitLabel, onClose, onSubmit, children }: {
  title: string; submitLabel: string; onClose: () => void;
  onSubmit: () => Promise<void>; children: ReactNode;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handle(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit();
      onClose();
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handle}>
        {children}
        <ErrorBox message={error} />
        <div className="actions">
          <button type="button" className="btn ghost" onClick={onClose}>Отмена</button>
          <button type="submit" className="btn" disabled={busy}>{busy ? 'Сохранение…' : submitLabel}</button>
        </div>
      </form>
    </Modal>
  );
}

export function blankToNull(v: string): string | null {
  const t = v.trim();
  return t === '' ? null : t;
}

// ----- общие элементы для экранов расписания, зарплаты и отчётов -----

export function Tabs<T extends string>({ tabs, value, onChange }: {
  tabs: { id: T; label: string }[]; value: T; onChange: (id: T) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.id} role="tab" aria-selected={t.id === value} type="button"
                className={t.id === value ? 'tab active' : 'tab'} onClick={() => onChange(t.id)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export type Tone = 'ok' | 'off' | 'warn' | 'info' | 'danger';

export function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

/** Запускает действие и показывает его ошибку; удобно для кнопок «Рассчитать», «Отменить» и т. п. */
export function useActionRunner() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = useCallback(async (action: () => Promise<unknown>): Promise<boolean> => {
    setError(null);
    setBusy(true);
    try {
      await action();
      return true;
    } catch (e) {
      setError(errorText(e));
      return false;
    } finally {
      setBusy(false);
    }
  }, []);

  return { error, busy, run, clearError: () => setError(null) };
}
