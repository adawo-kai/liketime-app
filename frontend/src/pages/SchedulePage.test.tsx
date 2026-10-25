import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api';
import type { Lesson } from '../types';
import SchedulePage from './SchedulePage';

const state = vi.hoisted(() => ({ role: 'MANAGER' as 'ADMIN' | 'MANAGER' | 'TEACHER' }));

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

function lesson(id: number, date: string, start: string, over: Partial<Lesson> = {}): Lesson {
  return {
    id, groupId: 3, groupName: `Группа ${id}`, teacherId: 2, teacherName: 'Айгуль Ж.', roomId: null, roomName: 'Кабинет 1',
    date, startTime: `${start}:00`, endTime: '10:00:00', startsAt: `${date}T03:00:00Z`, status: 'PLANNED', origin: 'GENERATED',
    note: null, visits: 0, ...over,
  };
}

const lessons: Lesson[] = [
  lesson(1, '2026-10-26', '09:00'),
  lesson(2, '2026-10-26', '11:00', { status: 'HELD', visits: 4 }),
  lesson(3, '2026-10-28', '15:00', { status: 'CANCELLED', teacherId: null, teacherName: null }),
];

function setup(data: Lesson[] = lessons) {
  vi.mocked(api.get).mockImplementation(async (url: string) => {
    if (url.startsWith('/api/lessons')) return data as never;
    return [] as never;
  });
  vi.mocked(api.put).mockResolvedValue({} as never);
  vi.mocked(api.post).mockResolvedValue({ created: 5, alreadyExisted: 1, conflicts: ['Группа 1, 27.10 09:00: преподаватель занят'] } as never);
  return render(<MemoryRouter><SchedulePage /></MemoryRouter>);
}

const lessonCalls = () => vi.mocked(api.get).mock.calls.map(([url]) => url as string).filter((u) => u.startsWith('/api/lessons'));

describe('расписание', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    state.role = 'MANAGER';
    // Среда 28 октября 2026: неделя с понедельника 26-го. Подменяем только дату, чтобы не ломать таймеры тестов.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 28, 12, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('запрашивает занятия текущей недели с понедельника по воскресенье', async () => {
    setup();
    await screen.findByText('Группа 1');

    expect(lessonCalls()[0]).toBe('/api/lessons?from=2026-10-26&to=2026-11-01');
    expect(screen.getByText(/Неделя: 26\.10\.2026 – 01\.11\.2026/)).toBeInTheDocument();
  });

  it('группирует занятия по дням и подписывает дни недели', async () => {
    setup();

    expect(await screen.findByText('Понедельник, 26.10.2026')).toBeInTheDocument();
    expect(screen.getByText(/Среда, 28\.10\.2026 · сегодня/)).toBeInTheDocument();
    const monday = screen.getByText('Понедельник, 26.10.2026').closest('tr') as HTMLElement;
    expect(monday).toBeInTheDocument();
  });

  it('показывает статусы, число пришедших и отсутствие преподавателя', async () => {
    setup();
    await screen.findByText('Группа 2');

    const held = screen.getByText('Группа 2').closest('tr') as HTMLElement;
    expect(within(held).getByText('Проведено')).toBeInTheDocument();
    expect(within(held).getByText('4')).toBeInTheDocument();
    const cancelled = screen.getByText('Группа 3').closest('tr') as HTMLElement;
    expect(within(cancelled).getByText('Отменено')).toBeInTheDocument();
    expect(within(cancelled).getByText('Не назначен')).toBeInTheDocument();
  });

  it('переключение недели меняет запрос', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Группа 1');

    await user.click(screen.getByRole('button', { name: 'Следующая →' }));
    await waitFor(() => expect(lessonCalls()).toContain('/api/lessons?from=2026-11-02&to=2026-11-08'));

    await user.click(screen.getByRole('button', { name: '← Предыдущая' }));
    await user.click(screen.getByRole('button', { name: '← Предыдущая' }));
    await waitFor(() => expect(lessonCalls()).toContain('/api/lessons?from=2026-10-19&to=2026-10-25'));

    await user.click(screen.getByRole('button', { name: 'Эта неделя' }));
    await waitFor(() => expect(lessonCalls()[lessonCalls().length - 1]).toBe('/api/lessons?from=2026-10-26&to=2026-11-01'));
  });

  it('менеджер отменяет запланированное занятие и может вернуть отменённое', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Группа 1');

    await user.click(within(screen.getByText('Группа 1').closest('tr') as HTMLElement).getByRole('button', { name: 'Отменить' }));
    expect(api.put).toHaveBeenCalledWith('/api/lessons/1/status', { status: 'CANCELLED' });

    await user.click(within(screen.getByText('Группа 3').closest('tr') as HTMLElement).getByRole('button', { name: 'Вернуть' }));
    expect(api.put).toHaveBeenCalledWith('/api/lessons/3/status', { status: 'PLANNED' });
  });

  it('создание занятий по расписанию показывает результат и накладки', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByText('Группа 1');

    await user.click(screen.getByRole('button', { name: 'Создать занятия по расписанию' }));
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    expect(api.post).toHaveBeenCalledWith('/api/schedule/generate', { from: '2026-10-26', to: '2026-11-01', groupId: null });
    expect(await screen.findByText('Занятия созданы')).toBeInTheDocument();
    expect(screen.getByText(/преподаватель занят/)).toBeInTheDocument();
  });

  it('преподаватель видит «Мои занятия» без кнопок создания и отмены', async () => {
    state.role = 'TEACHER';
    setup();
    await screen.findByText('Группа 1');

    expect(screen.getByRole('heading', { name: 'Мои занятия' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Создать занятия по расписанию' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ Разовое занятие' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Отменить' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Журнал' }).length).toBe(3);
  });

  it('если занятий нет, подсказывает, что делать', async () => {
    setup([]);

    expect(await screen.findByText(/На этой неделе занятий нет/)).toBeInTheDocument();
  });
});
