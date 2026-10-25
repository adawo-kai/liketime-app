import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, downloadFile } from '../api';
import { formatDate, formatMoney, formatRange, PERIOD_STATUS_LABELS } from '../format';
import type { CalculationResult, PeriodSummary, TeacherDetail, TeacherTotal } from '../types';
import { Badge, ErrorBox, Field, FormModal, Modal, useActionRunner, useLoad } from '../ui';
import { PERIOD_TONE } from './SalaryPage';

export default function SalaryPeriodPage() {
  const { id } = useParams();
  const { me } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  const navigate = useNavigate();
  const [detail, setDetail] = useState<TeacherTotal | null>(null);
  const [calcInfo, setCalcInfo] = useState<CalculationResult | null>(null);
  const actions = useActionRunner();

  const summary = useLoad(() => api.get<PeriodSummary>(`/api/salary/periods/${id}`), [id]);
  const period = summary.data?.period;
  const editable = period?.status === 'OPEN' || period?.status === 'CALCULATED';

  async function act(path: string, after?: () => void) {
    setCalcInfo(null);
    if (await actions.run(() => api.post(`/api/salary/periods/${id}/${path}`, {}))) {
      after?.();
      summary.reload();
    }
  }

  async function calculate() {
    setCalcInfo(null);
    const ok = await actions.run(async () => {
      setCalcInfo(await api.post<CalculationResult>(`/api/salary/periods/${id}/calculate`, {}));
    });
    if (ok) summary.reload();
  }

  async function download(file: string, fallbackName: string) {
    await actions.run(() => downloadFile(`/api/salary/periods/${id}/${file}`, fallbackName));
  }

  async function remove() {
    if (!window.confirm('Удалить период? Расчёт будет потерян.')) return;
    if (await actions.run(() => api.del(`/api/salary/periods/${id}`))) navigate('/salary');
  }

  return (
    <>
      <p><Link to="/salary">← К зарплате</Link></p>
      <ErrorBox message={summary.error ?? actions.error} />
      {period && (
        <>
          <div className="page-head">
            <div>
              <h1>Период {formatRange(period.startsOn, period.endsOn)}</h1>
              <div style={{ marginTop: 6 }}>
                <Badge tone={PERIOD_TONE[period.status]}>{PERIOD_STATUS_LABELS[period.status]}</Badge>
                {period.approvedBy && <span className="muted small"> · утвердил {period.approvedBy}</span>}
              </div>
            </div>
            <div className="row-actions">
              <button className="btn ghost" onClick={() => download('export.csv', 'salary.csv')}>Скачать итоги (CSV)</button>
              <button className="btn ghost" onClick={() => download('export-details.csv', 'salary-details.csv')}>Скачать детали (CSV)</button>
            </div>
          </div>

          {isAdmin && (
            <div className="toolbar">
              {editable && <button className="btn" onClick={calculate} disabled={actions.busy}>Рассчитать</button>}
              {editable && <button className="btn ghost" onClick={() => act('approve')} disabled={actions.busy}>Утвердить</button>}
              {period.status === 'APPROVED' && <button className="btn" onClick={() => act('pay')} disabled={actions.busy}>Отметить выплаченным</button>}
              {period.status === 'APPROVED' && <button className="btn ghost" onClick={() => act('reopen')} disabled={actions.busy}>Вернуть в расчёт</button>}
              {editable && <button className="btn ghost danger" onClick={remove} disabled={actions.busy}>Удалить период</button>}
            </div>
          )}

          {editable && (
            <div className="notice info">
              Расчёт берёт проведённые занятия периода и считает приходы учеников. После утверждения посещаемость и занятия
              этого периода блокируются. Перед утверждением расчёт выполняется заново.
            </div>
          )}
          {period.status === 'APPROVED' && (
            <div className="notice info">Период утверждён: изменить посещаемость и занятия за эти даты нельзя. Чтобы исправить данные, верните период в расчёт.</div>
          )}
          {calcInfo && (
            <div className="notice info">Расчёт выполнен: строк начисления {calcInfo.lines}, на сумму {formatMoney(calcInfo.accrued)}.</div>
          )}
          {summary.data!.linesWithoutRule > 0 && (
            <div className="notice">
              Для занятий без правила оплаты ({summary.data!.linesWithoutRule}) начислено 0. Добавьте ставку на вкладке «Ставки» и
              пересчитайте период: пока правил не хватает, период нельзя утвердить.
            </div>
          )}

          <div className="cards">
            <div className="card stat"><div className="label">Начислено за приходы</div><div className="value">{formatMoney(period.accrued)}</div></div>
            <div className="card stat"><div className="label">Корректировки</div><div className="value">{formatMoney(period.adjustments)}</div></div>
            <div className="card stat"><div className="label">К выплате</div><div className="value">{formatMoney(period.total)}</div></div>
          </div>

          <div className="card table-wrap">
            <table>
              <thead>
                <tr><th>Преподаватель</th><th className="num">Занятий</th><th className="num">Приходов</th><th className="num">Начислено</th><th className="num">Корректировки</th><th className="num">К выплате</th><th /></tr>
              </thead>
              <tbody>
                {summary.data!.teachers.map((t) => (
                  <tr key={t.teacherId}>
                    <td>{t.teacherName}</td>
                    <td className="num">{t.lessons}</td>
                    <td className="num">{t.visits}</td>
                    <td className="num">{formatMoney(t.accrued)}</td>
                    <td className="num">{formatMoney(t.adjustments)}</td>
                    <td className="num"><b>{formatMoney(t.total)}</b></td>
                    <td className="row-actions"><button className="btn ghost small" onClick={() => setDetail(t)}>Подробнее</button></td>
                  </tr>
                ))}
                {summary.data!.teachers.length === 0 && (
                  <tr><td colSpan={7} className="muted">Начислений нет. Нажмите «Рассчитать» или проверьте, что занятия периода проведены и отмечены.</td></tr>
                )}
              </tbody>
              {summary.data!.teachers.length > 0 && (
                <tfoot>
                  <tr><td>Итого</td><td /><td /><td className="num">{formatMoney(period.accrued)}</td>
                    <td className="num">{formatMoney(period.adjustments)}</td><td className="num">{formatMoney(period.total)}</td><td /></tr>
                </tfoot>
              )}
            </table>
          </div>
        </>
      )}
      {detail && period && (
        <DetailModal periodId={period.id} teacher={detail} canAdjust={isAdmin && editable}
                     onClose={() => setDetail(null)} onChanged={summary.reload} />
      )}
    </>
  );
}

function DetailModal({ periodId, teacher, canAdjust, onClose, onChanged }: {
  periodId: number; teacher: TeacherTotal; canAdjust: boolean; onClose: () => void; onChanged: () => void;
}) {
  const detail = useLoad(() => api.get<TeacherDetail>(`/api/salary/periods/${periodId}/teachers/${teacher.teacherId}`),
    [periodId, teacher.teacherId]);
  const [adding, setAdding] = useState(false);
  const actions = useActionRunner();

  async function removeAdjustment(adjustmentId: number) {
    if (await actions.run(() => api.del(`/api/salary/adjustments/${adjustmentId}`))) {
      detail.reload();
      onChanged();
    }
  }

  return (
    <Modal title={teacher.teacherName} onClose={onClose}>
      <ErrorBox message={detail.error ?? actions.error} />
      <TeacherDetailView data={detail.data} canAdjust={canAdjust} onRemove={removeAdjustment} />
      {canAdjust && (
        <div className="toolbar" style={{ marginTop: 12 }}>
          <button className="btn ghost" onClick={() => setAdding(true)}>+ Премия или удержание</button>
        </div>
      )}
      <div className="actions"><button className="btn" onClick={onClose}>Закрыть</button></div>
      {adding && (
        <AdjustmentForm periodId={periodId} teacherId={teacher.teacherId} onClose={() => setAdding(false)}
                        onSaved={() => { detail.reload(); onChanged(); }} />
      )}
    </Modal>
  );
}

/** Занятия и корректировки преподавателя за период; используется и в кабинете преподавателя */
export function TeacherDetailView({ data, canAdjust = false, onRemove }: {
  data: TeacherDetail | null; canAdjust?: boolean; onRemove?: (adjustmentId: number) => void;
}) {
  if (!data) return null;
  return (
    <>
      <div className="card table-wrap" style={{ marginBottom: 12 }}>
        <table>
          <thead><tr><th>Дата</th><th>Группа</th><th className="num">Приходов</th><th className="num">Ставка</th><th className="num">Сумма</th></tr></thead>
          <tbody>
            {data.lines.map((l) => (
              <tr key={l.lessonId}>
                <td className="nowrap">{formatDate(l.date)}</td>
                <td>{l.groupName}{!l.hasRule && <> <Badge tone="danger">нет ставки</Badge></>}</td>
                <td className="num">{l.visits}</td>
                <td className="num">{formatMoney(l.unitAmount)}</td>
                <td className="num">{formatMoney(l.amount)}</td>
              </tr>
            ))}
            {data.lines.length === 0 && <tr><td colSpan={5} className="muted">Начислений за занятия нет</td></tr>}
          </tbody>
        </table>
      </div>
      {data.adjustments.length > 0 && (
        <div className="card table-wrap" style={{ marginBottom: 12 }}>
          <table>
            <thead><tr><th>Корректировка</th><th className="num">Сумма</th>{canAdjust && <th />}</tr></thead>
            <tbody>
              {data.adjustments.map((a) => (
                <tr key={a.id}>
                  <td className="wrap">{a.reason}<div className="muted small">{a.createdBy}</div></td>
                  <td className="num">{formatMoney(a.amount)}</td>
                  {canAdjust && <td className="row-actions"><button className="btn ghost small" onClick={() => onRemove?.(a.id)}>Удалить</button></td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="cards">
        <div className="card stat"><div className="label">Начислено</div><div className="value">{formatMoney(data.accrued)}</div></div>
        <div className="card stat"><div className="label">Корректировки</div><div className="value">{formatMoney(data.adjustmentsTotal)}</div></div>
        <div className="card stat"><div className="label">К выплате</div><div className="value">{formatMoney(data.total)}</div></div>
      </div>
    </>
  );
}

function AdjustmentForm({ periodId, teacherId, onClose, onSaved }: {
  periodId: number; teacherId: number; onClose: () => void; onSaved: () => void;
}) {
  const [kind, setKind] = useState<'bonus' | 'penalty'>('bonus');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');

  async function submit() {
    const value = Number(amount.replace(',', '.'));
    if (!Number.isFinite(value) || value <= 0) throw new Error('Введите сумму больше нуля');
    await api.post(`/api/salary/periods/${periodId}/adjustments`, {
      teacherId, amount: kind === 'bonus' ? value : -value, reason: reason.trim(),
    });
    onSaved();
  }

  return (
    <FormModal title="Премия или удержание" submitLabel="Добавить" onClose={onClose} onSubmit={submit}>
      <Field label="Тип">
        <select value={kind} onChange={(e) => setKind(e.target.value as 'bonus' | 'penalty')}>
          <option value="bonus">Премия (плюс)</option>
          <option value="penalty">Удержание (минус)</option>
        </select>
      </Field>
      <Field label="Сумма, сом"><input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required /></Field>
      <Field label="Причина (обязательно)"><input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} required /></Field>
    </FormModal>
  );
}
