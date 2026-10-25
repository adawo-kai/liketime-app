import { useState } from 'react';
import { api } from '../api';
import { formatMoney, formatRange, PERIOD_STATUS_LABELS } from '../format';
import type { MyPeriod, TeacherDetail } from '../types';
import { Badge, ErrorBox, Modal, useLoad } from '../ui';
import { TeacherDetailView } from './SalaryPeriodPage';
import { PERIOD_TONE } from './SalaryPage';

/** Кабинет преподавателя: его зарплата по утверждённым периодам. Черновые расчёты ему не показываются. */
export default function MySalaryPage() {
  const [open, setOpen] = useState<MyPeriod | null>(null);
  const periods = useLoad(() => api.get<MyPeriod[]>('/api/salary/my'));

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Моя зарплата</h1>
          <div className="muted">Здесь появляются периоды после того, как администратор их утвердит</div>
        </div>
      </div>
      <ErrorBox message={periods.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Период</th><th>Статус</th><th className="num">Начислено</th><th className="num">Корректировки</th><th className="num">К выплате</th><th /></tr></thead>
          <tbody>
            {periods.data?.map((p) => (
              <tr key={p.periodId}>
                <td className="nowrap">{formatRange(p.startsOn, p.endsOn)}</td>
                <td><Badge tone={PERIOD_TONE[p.status]}>{PERIOD_STATUS_LABELS[p.status]}</Badge></td>
                <td className="num">{formatMoney(p.accrued)}</td>
                <td className="num">{formatMoney(p.adjustments)}</td>
                <td className="num"><b>{formatMoney(p.total)}</b></td>
                <td className="row-actions"><button className="btn ghost small" onClick={() => setOpen(p)}>Подробнее</button></td>
              </tr>
            ))}
            {periods.data?.length === 0 && <tr><td colSpan={6} className="muted">Утверждённых периодов пока нет</td></tr>}
          </tbody>
        </table>
      </div>
      {open && <MyDetail period={open} onClose={() => setOpen(null)} />}
    </>
  );
}

function MyDetail({ period, onClose }: { period: MyPeriod; onClose: () => void }) {
  const detail = useLoad(() => api.get<TeacherDetail>(`/api/salary/periods/${period.periodId}/teachers/${period.teacherId}`),
    [period.periodId, period.teacherId]);
  return (
    <Modal title={`Период ${formatRange(period.startsOn, period.endsOn)}`} onClose={onClose}>
      <ErrorBox message={detail.error} />
      <TeacherDetailView data={detail.data} />
      <div className="actions"><button className="btn" onClick={onClose}>Закрыть</button></div>
    </Modal>
  );
}
