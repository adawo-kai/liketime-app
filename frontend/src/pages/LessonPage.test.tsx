import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from '../api';
import type { Lesson, Roster } from '../types';
import LessonPage from './LessonPage';

const state = vi.hoisted(() => ({ role: 'TEACHER' as 'ADMIN' | 'MANAGER' | 'TEACHER' }));

vi.mock('../auth', () => ({
  useAuth: () => ({
    me: { id: 1, login: 'user', fullName: 'Пользователь', role: state.role },
    loading: false, signIn: vi.fn(), signOut: vi.fn(),
  }),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return { ...actual, api: { get: vi.fn(), put: vi.fn(), post: vi.fn(), del: vi.fn(), patch: vi.fn() } };
});

const lesson: Lesson = {
  id: 7, groupId: 3, groupName: 'Английский A1', teacherId: 2, teacherName: 'Айгуль Ж.', roomId: null, roomName: null,
  date: '2026-10-26', startTime: '09:00:00', endTime: '10:00:00', startsAt: '2026-10-26T03:00:00Z',
  status: 'PLANNED', origin: 'GENERATED', note: null, visits: 1,
};

const roster: Roster = {
  lessonId: 7, lessonStatus: 'PLANNED',
  students: [
    { studentId: 11, fullName: 'Алия Ибраева', status: null, source: null, checkInAt: null, note: null, member: true },
    { studentId: 12, fullName: 'Бакыт Осмонов', status: 'PRESENT', source: 'DEVICE', checkInAt: '2026-10-26T03:05:00Z', note: null, member: true },
    { studentId: 13, fullName: 'Вера Петрова', status: null, source: null, checkInAt: null, note: null, member: true },
    { studentId: 14, fullName: 'Гульнара Эшова', status: 'ABSENT', source: 'MANUAL', checkInAt: null, note: 'болеет', member: false },
  ],
};

function setup(overrides: { lesson?: Partial<Lesson>; roster?: Roster } = {}) {
  const lessonData = { ...lesson, ...overrides.lesson };
  vi.mocked(api.get).mockImplementation(async (url: string) =>
    (url.endsWith('/attendance') ? (overrides.roster ?? roster) : lessonData) as never);
  vi.mocked(api.put).mockResolvedValue({} as never);
  vi.mocked(api.del).mockResolvedValue(undefined as never);
  return render(
    <MemoryRouter initialEntries={['/lessons/7']}>
      <Routes><Route path="/lessons/:id" element={<LessonPage />} /></Routes>
    </MemoryRouter>,
  );
}

const rowOf = (name: string) => screen.getByText(name).closest('tr') as HTMLElement;

describe('журнал посещаемости занятия', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    state.role = 'TEACHER';
  });

  it('показывает занятие, учеников и источник отметки', async () => {
    setup();

    expect(await screen.findByRole('heading', { name: 'Английский A1' })).toBeInTheDocument();
    expect(screen.getByText(/26\.10\.2026, 09:00–10:00/)).toBeInTheDocument();
    expect(screen.getByText(/Айгуль Ж\./)).toBeInTheDocument();
    // Время прихода показывается по Бишкеку: 03:05 UTC = 09:05
    expect(within(rowOf('Бакыт Осмонов')).getByText('Устройство 09:05')).toBeInTheDocument();
    expect(within(rowOf('Гульнара Эшова')).getByText('Вручную')).toBeInTheDocument();
    expect(within(rowOf('Гульнара Эшова')).getByText('Вышел из группы')).toBeInTheDocument();
    expect(screen.getByText('Пришли: 1 из 3')).toBeInTheDocument();
  });

  it('отметка «Пришёл» отправляет запрос по ученику и перезагружает данные', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Алия Ибраева');

    await user.click(within(rowOf('Алия Ибраева')).getByRole('button', { name: 'Пришёл' }));

    expect(api.put).toHaveBeenCalledWith('/api/lessons/7/attendance/11', { status: 'PRESENT', note: null });
    await waitFor(() => expect(vi.mocked(api.get).mock.calls.length).toBeGreaterThan(2));
  });

  it('текущая отметка подсвечена, а повторное нажатие другой кнопки меняет статус', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Бакыт Осмонов');
    const row = rowOf('Бакыт Осмонов');

    expect(within(row).getByRole('button', { name: 'Пришёл' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(row).getByRole('button', { name: 'Опоздал' })).toHaveAttribute('aria-pressed', 'false');

    await user.click(within(row).getByRole('button', { name: 'Опоздал' }));
    expect(api.put).toHaveBeenCalledWith('/api/lessons/7/attendance/12', { status: 'LATE', note: null });
  });

  it('«Отметить всех пришедшими» отмечает только неотмеченных учеников группы', async () => {
    const user = userEvent.setup();
    setup();

    await user.click(await screen.findByRole('button', { name: 'Отметить всех пришедшими (2)' }));

    expect(api.put).toHaveBeenCalledWith('/api/lessons/7/attendance', {
      records: [{ studentId: 11, status: 'PRESENT' }, { studentId: 13, status: 'PRESENT' }],
    });
  });

  it('когда отмечены все, кнопка массовой отметки недоступна', async () => {
    setup({ roster: { ...roster, students: roster.students.map((s) => ({ ...s, status: s.status ?? 'PRESENT' })) } });

    expect(await screen.findByRole('button', { name: 'Отметить всех пришедшими (0)' })).toBeDisabled();
  });

  it('снимает отметку', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Бакыт Осмонов');

    await user.click(within(rowOf('Бакыт Осмонов')).getByRole('button', { name: 'Снять' }));

    expect(api.del).toHaveBeenCalledWith('/api/lessons/7/attendance/12');
  });

  it('показывает ошибку сервера, например что занятие ещё не началось', async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(api.put).mockRejectedValue(new ApiError(409, 'Занятие ещё не началось'));
    await screen.findByText('Алия Ибраева');

    await user.click(within(rowOf('Алия Ибраева')).getByRole('button', { name: 'Пришёл' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Занятие ещё не началось');
  });

  it('преподаватель не видит кнопку отмены занятия, менеджер видит', async () => {
    setup();
    await screen.findByRole('heading', { name: 'Английский A1' });
    expect(screen.queryByRole('button', { name: 'Отменить занятие' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Занятие проведено' })).toBeInTheDocument();
  });

  it('менеджер может отменить занятие', async () => {
    state.role = 'MANAGER';
    const user = userEvent.setup();
    setup();

    await user.click(await screen.findByRole('button', { name: 'Отменить занятие' }));

    expect(api.put).toHaveBeenCalledWith('/api/lessons/7/status', { status: 'CANCELLED' });
  });

  it('предупреждает, если у занятия нет преподавателя', async () => {
    setup({ lesson: { teacherId: null, teacherName: null } });

    expect(await screen.findByText(/не попадёт в расчёт зарплаты/)).toBeInTheDocument();
  });

  it('у отменённого занятия отметки заблокированы', async () => {
    setup({ lesson: { status: 'CANCELLED' } });

    expect(await screen.findByText(/Занятие отменено, посещаемость не ведётся/)).toBeInTheDocument();
    expect(within(rowOf('Алия Ибраева')).getByRole('button', { name: 'Пришёл' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: /Отметить всех пришедшими/ })).not.toBeInTheDocument();
  });

  it('сохраняет заметку при уходе из поля, если ученик уже отмечен', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Бакыт Осмонов');

    const input = within(rowOf('Бакыт Осмонов')).getByLabelText('Заметка: Бакыт Осмонов');
    await user.type(input, 'ушёл раньше');
    await user.tab();

    expect(api.put).toHaveBeenCalledWith('/api/lessons/7/attendance/12', { status: 'PRESENT', note: 'ушёл раньше' });
  });

  it('поле заметки недоступно, пока ученик не отмечен', async () => {
    setup();
    await screen.findByText('Алия Ибраева');

    expect(within(rowOf('Алия Ибраева')).getByLabelText('Заметка: Алия Ибраева')).toBeDisabled();
  });
});
