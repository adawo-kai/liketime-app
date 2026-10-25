import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, type Group, type Teacher } from '../api';
import {
  formatDate, formatMoney, formatRange, PERIOD_STATUS_LABELS, previousMonthRange, toIsoDate,
} from '../format';
import type { PeriodStatus, SalaryPeriod, SalaryRule } from '../types';
import { Badge, ErrorBox, Field, FormModal, Tabs, useActionRunner, useLoad, type Tone } from '../ui';

type Tab = 'periods' | 'rules';

export const PERIOD_TONE: Record<PeriodStatus, Tone> = { OPEN: 'info', CALCULATED: 'warn', APPROVED: 'ok', PAID: 'off' };

export default function SalaryPage() {
  const [tab, setTab] = useState<Tab>('periods');
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Зарплата</h1>
          <div className="muted">Начисления преподавателям: за каждый приход ученика на проведённое занятие</div>
        </div>
      </div>
      <Tabs<Tab> tabs={[{ id: 'periods', label: 'Периоды' }, { id: 'rules', label: 'Ставки' }]} value={tab} onChange={setTab} />
      {tab === 'periods' ? <PeriodsTab /> : <RulesTab />}
    </>
  );
}

function PeriodsTab() {
  const { me } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  const [creating, setCreating] = useState(false);
  const periods = useLoad(() => api.get<SalaryPeriod[]>('/api/salary/periods'));

  return (
    <>
      {isAdmin && <div className="toolbar"><button className="btn" onClick={() => setCreating(true)}>+ Новый период</button></div>}
      <ErrorBox message={periods.error} />
      <div className="card table-wrap">
        <table>
          <thead>
            <tr><th>Период</th><th>Статус</th><th className="num">Начислено</th><th className="num">Корректировки</th><th className="num">К выплате</th><th /></tr>
          </thead>
          <tbody>
            {periods.data?.map((p) => (
              <tr key={p.id}>
                <td className="nowrap"><Link to={`/salary/${p.id}`}>{formatRange(p.startsOn, p.endsOn)}</Link></td>
                <td><Badge tone={PERIOD_TONE[p.status]}>{PERIOD_STATUS_LABELS[p.status]}</Badge></td>
                <td className="num">{formatMoney(p.accrued)}</td>
                <td className="num">{formatMoney(p.adjustments)}</td>
                <td className="num"><b>{formatMoney(p.total)}</b></td>
                <td className="row-actions"><Link className="btn ghost small" to={`/salary/${p.id}`}>Открыть</Link></td>
              </tr>
            ))}
            {periods.data?.length === 0 && (
              <tr><td colSpan={6} className="muted">Периодов пока нет{isAdmin && '. Создайте период (обычно календарный месяц) и рассчитайте его.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {creating && <PeriodForm onClose={() => setCreating(false)} onSaved={periods.reload} />}
    </>
  );
}

function PeriodForm({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const previous = previousMonthRange(toIsoDate(new Date()));
  const [from, setFrom] = useState(previous.from);
  const [to, setTo] = useState(previous.to);

  async function submit() {
    await api.post('/api/salary/periods', { startsOn: from, endsOn: to });
    onSaved();
  }

  return (
    <FormModal title="Новый зарплатный период" submitLabel="Создать" onClose={onClose} onSubmit={submit}>
      <p className="muted small">По умолчанию предложен прошлый месяц. Периоды не должны пересекаться.</p>
      <Field label="С"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required /></Field>
      <Field label="По"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} required /></Field>
    </FormModal>
  );
}

function RulesTab() {
  const { me } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  const [editing, setEditing] = useState<SalaryRule | 'new' | null>(null);
  const actions = useActionRunner();
  const rules = useLoad(() => api.get<SalaryRule[]>('/api/salary/rules'));

  async function toggle(r: SalaryRule) {
    const ok = await actions.run(() => (r.active ? api.del(`/api/salary/rules/${r.id}`) : api.put(`/api/salary/rules/${r.id}/activate`)));
    if (ok) rules.reload();
  }

  const scope = (r: SalaryRule) => {
    if (r.groupName && r.teacherName) return `${r.groupName} · ${r.teacherName}`;
    if (r.groupName) return `Группа: ${r.groupName}`;
    if (r.teacherName) return `Преподаватель: ${r.teacherName}`;
    return 'Все преподаватели и группы';
  };

  return (
    <>
      <div className="notice info">
        Ставка — сумма за один приход одного ученика. Если подходят несколько правил, берётся самое конкретное:
        правило для группы важнее правила для преподавателя, оно важнее общего. {!isAdmin && 'Менять ставки может только администратор.'}
      </div>
      {isAdmin && <div className="toolbar"><button className="btn" onClick={() => setEditing('new')}>+ Новая ставка</button></div>}
      <ErrorBox message={rules.error ?? actions.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Для кого</th><th className="num">За приход</th><th>Действует</th><th>Статус</th>{isAdmin && <th />}</tr></thead>
          <tbody>
            {rules.data?.map((r) => (
              <tr key={r.id}>
                <td className="wrap">{scope(r)}{r.comment && <div className="muted small">{r.comment}</div>}</td>
                <td className="num">{formatMoney(r.amount)}</td>
                <td className="nowrap">с {formatDate(r.effectiveFrom)}{r.effectiveTo ? ` по ${formatDate(r.effectiveTo)}` : ' без срока'}</td>
                <td><Badge tone={r.active ? 'ok' : 'off'}>{r.active ? 'Действует' : 'Отключена'}</Badge></td>
                {isAdmin && (
                  <td className="row-actions">
                    <button className="btn ghost small" onClick={() => setEditing(r)}>Изменить</button>
                    <button className="btn ghost small" onClick={() => toggle(r)}>{r.active ? 'Отключить' : 'Включить'}</button>
                  </td>
                )}
              </tr>
            ))}
            {rules.data?.length === 0 && <tr><td colSpan={5} className="muted">Ставок пока нет: без них зарплата не рассчитывается</td></tr>}
          </tbody>
        </table>
      </div>
      {editing && <RuleForm rule={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={rules.reload} />}
    </>
  );
}

function RuleForm({ rule, onClose, onSaved }: { rule: SalaryRule | null; onClose: () => void; onSaved: () => void }) {
  const teachers = useLoad(() => api.get<Teacher[]>('/api/teachers'));
  const groups = useLoad(() => api.get<Group[]>('/api/groups'));
  const [teacherId, setTeacherId] = useState('');
  const [groupId, setGroupId] = useState('');
  const [amount, setAmount] = useState(rule ? String(rule.amount) : '100');
  const [from, setFrom] = useState(rule?.effectiveFrom ?? toIsoDate(new Date()));
  const [to, setTo] = useState(rule?.effectiveTo ?? '');
  const [comment, setComment] = useState(rule?.comment ?? '');

  async function submit() {
    const value = Number(amount.replace(',', '.'));
    if (!Number.isFinite(value) || value < 0) throw new Error('Введите сумму не меньше нуля');
    const common = { amount: value, effectiveFrom: from, effectiveTo: to === '' ? null : to, comment: comment.trim() || null };
    if (rule) await api.put(`/api/salary/rules/${rule.id}`, common);
    else {
      await api.post('/api/salary/rules', {
        ...common,
        teacherId: teacherId === '' ? null : Number(teacherId),
        groupId: groupId === '' ? null : Number(groupId),
      });
    }
    onSaved();
  }

  return (
    <FormModal title={rule ? 'Изменить ставку' : 'Новая ставка'} submitLabel={rule ? 'Сохранить' : 'Создать'}
               onClose={onClose} onSubmit={submit}>
      {!rule && (
        <>
          <Field label="Преподаватель">
            <select value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
              <option value="">Все</option>
              {(teachers.data ?? []).filter((t) => t.status === 'ACTIVE').map((t) => <option key={t.id} value={t.id}>{t.fullName}</option>)}
            </select>
          </Field>
          <Field label="Группа">
            <select value={groupId} onChange={(e) => setGroupId(e.target.value)}>
              <option value="">Все</option>
              {(groups.data ?? []).map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
            </select>
          </Field>
        </>
      )}
      {rule && <p className="muted small">Для кого действует ставка, после создания не меняется. Чтобы изменить, создайте новую.</p>}
      <Field label="Сумма за один приход, сом">
        <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
      </Field>
      <Field label="Действует с"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required /></Field>
      <Field label="Действует по (необязательно)"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
      <Field label="Комментарий"><input value={comment} onChange={(e) => setComment(e.target.value)} maxLength={255} /></Field>
    </FormModal>
  );
}
