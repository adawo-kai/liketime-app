import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, downloadFile } from '../api';
import type { PeriodStatus, PeriodSummary, TeacherDetail } from '../types';
import SalaryPeriodPage from './SalaryPeriodPage';

const state = vi.hoisted(() => ({ role: 'ADMIN' as 'ADMIN' | 'MANAGER' | 'TEACHER' }));

vi.mock('../auth', () => ({
  useAuth: () => ({
    me: { id: 1, login: 'user', fullName: 'Пользователь', role: state.role },
    loading: false, signIn: vi.fn(), signOut: vi.fn(),
  }),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: { get: vi.fn(), put: vi.fn(), post: vi.fn(), del: vi.fn(), patch: vi.fn() },
    downloadFile: vi.fn(),
  };
});

function summary(status: PeriodStatus, linesWithoutRule = 0): PeriodSummary {
  return {
    period: {
      id: 5, startsOn: '2026-09-01', endsOn: '2026-09-30', status, calculatedAt: null, approvedAt: null,
      approvedBy: status === 'APPROVED' ? 'admin' : null, paidAt: null, paidBy: null,
      accrued: 300, adjustments: 50, total: 350,
    },
    teachers: [{ teacherId: 2, teacherName: 'Айгуль Ж.', lessons: 1, visits: 3, accrued: 300, adjustments: 50, total: 350 }],
    linesWithoutRule,
  };
}

const detail: TeacherDetail = {
  periodId: 5, startsOn: '2026-09-01', endsOn: '2026-09-30', status: 'CALCULATED', teacherId: 2, teacherName: 'Айгуль Ж.',
  lines: [{ lessonId: 9, date: '2026-09-10', groupId: 3, groupName: 'Английский A1', visits: 3, unitAmount: 100, amount: 300, hasRule: true }],
  adjustments: [{ id: 4, teacherId: 2, amount: 50, reason: 'Премия за открытый урок', createdBy: 'admin', createdAt: '2026-09-30T10:00:00Z' }],
  accrued: 300, adjustmentsTotal: 50, total: 350,
};

function setup(status: PeriodStatus, linesWithoutRule = 0) {
  vi.mocked(api.get).mockImplementation(async (url: string) =>
    (url.includes('/teachers/') ? detail : summary(status, linesWithoutRule)) as never);
  vi.mocked(api.post).mockResolvedValue({ lines: 1, withoutRule: 0, accrued: 300 } as never);
  vi.mocked(api.del).mockResolvedValue(undefined as never);
  return render(
    <MemoryRouter initialEntries={['/salary/5']}>
      <Routes>
        <Route path="/salary/:id" element={<SalaryPeriodPage />} />
        <Route path="/salary" element={<div>Список периодов</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const names = (labels: string[]) => labels.map((name) => screen.queryByRole('button', { name }));

describe('зарплатный период', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    state.role = 'ADMIN';
  });

  it('показывает итоги и суммы по преподавателям в сомах', async () => {
    setup('CALCULATED');

    expect(await screen.findByRole('heading', { name: /01\.09\.2026 – 30\.09\.2026/ })).toBeInTheDocument();
    expect(screen.getByText('Айгуль Ж.')).toBeInTheDocument();
    expect(screen.getAllByText(/350,00\s+сом/).length).toBeGreaterThan(0);
    expect(screen.getByText('Рассчитан')).toBeInTheDocument();
  });

  it('у периода в работе администратору доступны расчёт, утверждение и удаление', async () => {
    setup('CALCULATED');
    await screen.findByText('Айгуль Ж.');

    const [calc, approve, remove, pay, reopen] =
      names(['Рассчитать', 'Утвердить', 'Удалить период', 'Отметить выплаченным', 'Вернуть в расчёт']);
    expect(calc).toBeInTheDocument();
    expect(approve).toBeInTheDocument();
    expect(remove).toBeInTheDocument();
    expect(pay).toBeNull();
    expect(reopen).toBeNull();
  });

  it('у утверждённого периода можно выплатить или вернуть в расчёт, но не пересчитать', async () => {
    setup('APPROVED');
    await screen.findByText('Айгуль Ж.');

    const [calc, approve, remove, pay, reopen] =
      names(['Рассчитать', 'Утвердить', 'Удалить период', 'Отметить выплаченным', 'Вернуть в расчёт']);
    expect(calc).toBeNull();
    expect(approve).toBeNull();
    expect(remove).toBeNull();
    expect(pay).toBeInTheDocument();
    expect(reopen).toBeInTheDocument();
    expect(screen.getByText(/Период утверждён: изменить посещаемость/)).toBeInTheDocument();
  });

  it('у выплаченного периода действий нет', async () => {
    setup('PAID');
    await screen.findByText('Айгуль Ж.');

    expect(names(['Рассчитать', 'Утвердить', 'Отметить выплаченным', 'Вернуть в расчёт', 'Удалить период']))
      .toEqual([null, null, null, null, null]);
  });

  it('менеджер видит итоги и может скачивать файлы, но не меняет период', async () => {
    state.role = 'MANAGER';
    setup('CALCULATED');
    await screen.findByText('Айгуль Ж.');

    expect(names(['Рассчитать', 'Утвердить', 'Удалить период'])).toEqual([null, null, null]);
    expect(screen.getByRole('button', { name: 'Скачать итоги (CSV)' })).toBeInTheDocument();
  });

  it('«Рассчитать» вызывает расчёт и сообщает результат', async () => {
    const user = userEvent.setup();
    setup('OPEN');

    await user.click(await screen.findByRole('button', { name: 'Рассчитать' }));

    expect(api.post).toHaveBeenCalledWith('/api/salary/periods/5/calculate', {});
    expect(await screen.findByText(/Расчёт выполнен: строк начисления 1/)).toBeInTheDocument();
  });

  it('«Утвердить» отправляет запрос на утверждение', async () => {
    const user = userEvent.setup();
    setup('CALCULATED');

    await user.click(await screen.findByRole('button', { name: 'Утвердить' }));

    expect(api.post).toHaveBeenCalledWith('/api/salary/periods/5/approve', {});
  });

  it('предупреждает о занятиях без правила оплаты', async () => {
    setup('CALCULATED', 2);

    expect(await screen.findByText(/Для занятий без правила оплаты \(2\) начислено 0/)).toBeInTheDocument();
  });

  it('ошибка утверждения от сервера видна пользователю', async () => {
    const user = userEvent.setup();
    setup('CALCULATED');
    vi.mocked(api.post).mockRejectedValue(new Error('Период ещё не закончился'));

    await user.click(await screen.findByRole('button', { name: 'Утвердить' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Период ещё не закончился');
  });

  it('«Подробнее» показывает занятия и корректировки преподавателя', async () => {
    const user = userEvent.setup();
    setup('CALCULATED');

    await user.click(await screen.findByRole('button', { name: 'Подробнее' }));

    expect(await screen.findByText('Премия за открытый урок')).toBeInTheDocument();
    expect(screen.getByText('Английский A1')).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith('/api/salary/periods/5/teachers/2');
    expect(screen.getByRole('button', { name: '+ Премия или удержание' })).toBeInTheDocument();
  });

  it('в утверждённом периоде корректировки менять нельзя', async () => {
    const user = userEvent.setup();
    setup('APPROVED');

    await user.click(await screen.findByRole('button', { name: 'Подробнее' }));

    expect(await screen.findByText('Премия за открытый урок')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Премия или удержание' })).not.toBeInTheDocument();
  });

  it('удержание отправляется отрицательной суммой', async () => {
    const user = userEvent.setup();
    setup('CALCULATED');
    await user.click(await screen.findByRole('button', { name: 'Подробнее' }));
    await user.click(await screen.findByRole('button', { name: '+ Премия или удержание' }));

    await user.selectOptions(screen.getByLabelText('Тип'), 'penalty');
    await user.type(screen.getByLabelText('Сумма, сом'), '20,5');
    await user.type(screen.getByLabelText('Причина (обязательно)'), 'Опоздание');
    await user.click(screen.getByRole('button', { name: 'Добавить' }));

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/salary/periods/5/adjustments',
      { teacherId: 2, amount: -20.5, reason: 'Опоздание' }));
  });

  it('скачивание CSV идёт через авторизованный запрос', async () => {
    const user = userEvent.setup();
    setup('CALCULATED');
    vi.mocked(downloadFile).mockResolvedValue(undefined);

    await user.click(await screen.findByRole('button', { name: 'Скачать итоги (CSV)' }));

    expect(downloadFile).toHaveBeenCalledWith('/api/salary/periods/5/export.csv', 'salary.csv');
  });
});
