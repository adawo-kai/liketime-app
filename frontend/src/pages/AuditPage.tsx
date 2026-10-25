import { useState } from 'react';
import { api } from '../api';
import { formatDateTime } from '../format';
import type { AuditRow } from '../types';
import { ErrorBox, useLoad } from '../ui';

const ENTITIES: { id: string; label: string }[] = [
  { id: '', label: 'Все' },
  { id: 'user', label: 'Пользователи' },
  { id: 'lesson', label: 'Занятия и посещаемость' },
  { id: 'slot', label: 'Расписание' },
  { id: 'device', label: 'Устройства' },
  { id: 'salary_rule', label: 'Ставки' },
  { id: 'salary_period', label: 'Зарплатные периоды' },
];

const ACTION_LABELS: Record<string, string> = {
  USER_CREATE: 'Создан пользователь',
  USER_UPDATE: 'Изменён пользователь',
  USER_PASSWORD_RESET: 'Сброшен пароль',
  LESSON_CREATE: 'Создано занятие',
  LESSON_UPDATE: 'Изменено занятие',
  LESSON_STATUS: 'Сменён статус занятия',
  LESSON_HELD: 'Занятие проведено (по приходу)',
  LESSONS_GENERATED: 'Занятия созданы по расписанию',
  SLOT_CREATE: 'Добавлено расписание',
  SLOT_UPDATE: 'Изменено расписание',
  SLOT_ACTIVATE: 'Расписание включено',
  SLOT_DEACTIVATE: 'Расписание отключено',
  ATTENDANCE_MARK: 'Отметка посещаемости',
  ATTENDANCE_CLEAR: 'Снята отметка',
  ATTENDANCE_DEVICE: 'Отметка с устройства',
  DEVICE_CREATE: 'Добавлено устройство',
  DEVICE_UPDATE: 'Изменено устройство',
  DEVICE_STATUS: 'Сменён статус устройства',
  DEVICE_LINK: 'Привязано лицо',
  DEVICE_UNLINK: 'Отвязано лицо',
  SALARY_RULE_CREATE: 'Создана ставка',
  SALARY_RULE_UPDATE: 'Изменена ставка',
  SALARY_RULE_ACTIVATE: 'Ставка включена',
  SALARY_RULE_DEACTIVATE: 'Ставка отключена',
  PERIOD_CREATE: 'Создан период',
  PERIOD_DELETE: 'Удалён период',
  PERIOD_CALCULATE: 'Рассчитан период',
  PERIOD_APPROVE: 'Утверждён период',
  PERIOD_PAID: 'Период выплачен',
  PERIOD_REOPEN: 'Период возвращён в расчёт',
  ADJUSTMENT_ADD: 'Добавлена корректировка',
  ADJUSTMENT_DELETE: 'Удалена корректировка',
};

/** Журнал действий: кто, когда и что изменил. Видит только администратор. */
export default function AuditPage() {
  const [entity, setEntity] = useState('');
  const [limit, setLimit] = useState('100');
  const log = useLoad(() => api.get<AuditRow[]>(`/api/audit?limit=${limit}${entity ? `&entity=${entity}` : ''}`), [entity, limit]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Журнал действий</h1>
          <div className="muted">Новые записи сверху. Пароли и ключи в журнал не попадают.</div>
        </div>
      </div>
      <div className="toolbar">
        <label className="inline">Раздел
          <select value={entity} onChange={(e) => setEntity(e.target.value)}>
            {ENTITIES.map((e) => <option key={e.id} value={e.id}>{e.label}</option>)}
          </select>
        </label>
        <label className="inline">Записей
          <select value={limit} onChange={(e) => setLimit(e.target.value)}>
            <option value="50">50</option><option value="100">100</option><option value="300">300</option><option value="500">500</option>
          </select>
        </label>
        <button className="btn ghost small" onClick={log.reload}>Обновить</button>
      </div>
      <ErrorBox message={log.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Когда</th><th>Кто</th><th>Действие</th><th>Объект</th><th>Подробности</th></tr></thead>
          <tbody>
            {log.data?.map((r) => (
              <tr key={r.id}>
                <td className="nowrap">{formatDateTime(`${r.createdAt}Z`)}</td>
                <td>{r.actor ?? '—'}</td>
                <td className="wrap">{ACTION_LABELS[r.action] ?? r.action}</td>
                <td className="nowrap">{r.entity}{r.entityId !== null && ` #${r.entityId}`}</td>
                <td>{r.details ? <pre className="json">{JSON.stringify(r.details)}</pre> : '—'}</td>
              </tr>
            ))}
            {!log.loading && log.data?.length === 0 && <tr><td colSpan={5} className="muted">Записей нет</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
