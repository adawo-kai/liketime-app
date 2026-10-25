import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { api } from './api';

const state = vi.hoisted(() => ({
  me: null as null | { id: number; login: string; fullName: string; role: 'ADMIN' | 'MANAGER' | 'TEACHER' },
}));

vi.mock('./auth', () => ({
  useAuth: () => ({ me: state.me, loading: false, signIn: vi.fn(), signOut: vi.fn() }),
}));

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    api: { get: vi.fn(), put: vi.fn(), post: vi.fn(), del: vi.fn(), patch: vi.fn() },
    downloadFile: vi.fn(),
  };
});

function renderAs(role: 'ADMIN' | 'MANAGER' | 'TEACHER', path = '/schedule') {
  state.me = { id: 1, login: 'user', fullName: 'Тестовый Пользователь', role };
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>);
}

const nav = () => screen.getAllByRole('link').map((a) => a.textContent);

describe('меню по ролям', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.get).mockResolvedValue([] as never);
  });

  it('администратор видит все разделы, включая пользователей и журнал действий', () => {
    renderAs('ADMIN');

    expect(nav()).toEqual([
      'Расписание', 'Группы', 'Ученики', 'Преподаватели', 'Устройства', 'Зарплата', 'Отчёты', 'Кабинеты',
      'Пользователи', 'Журнал действий',
    ]);
  });

  it('менеджер видит рабочие разделы без пользователей и журнала', () => {
    renderAs('MANAGER');

    expect(nav()).toEqual([
      'Расписание', 'Группы', 'Ученики', 'Преподаватели', 'Устройства', 'Зарплата', 'Отчёты', 'Кабинеты',
    ]);
  });

  it('преподаватель видит только свои занятия, группы и зарплату', () => {
    renderAs('TEACHER');

    expect(nav()).toEqual(['Мои занятия', 'Мои группы', 'Моя зарплата']);
  });

  it('преподаватель не открывает чужие разделы по прямой ссылке', async () => {
    renderAs('TEACHER', '/audit');

    expect(await screen.findByRole('heading', { name: 'Мои занятия' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Журнал действий' })).not.toBeInTheDocument();
  });

  it('менеджер не открывает журнал действий и пользователей по прямой ссылке', async () => {
    renderAs('MANAGER', '/audit');

    expect(await screen.findByRole('heading', { name: 'Расписание' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Журнал действий' })).not.toBeInTheDocument();
  });

  it('администратор открывает журнал действий', async () => {
    renderAs('ADMIN', '/audit');

    expect(await screen.findByRole('heading', { name: 'Журнал действий' })).toBeInTheDocument();
  });

  it('преподаватель открывает свою зарплату, а раздел зарплаты персонала ему недоступен', async () => {
    renderAs('TEACHER', '/my-salary');
    expect(await screen.findByRole('heading', { name: 'Моя зарплата' })).toBeInTheDocument();
  });

  it('без входа показывается страница входа', () => {
    state.me = null;
    render(<MemoryRouter initialEntries={['/salary']}><App /></MemoryRouter>);

    expect(screen.queryByRole('link', { name: 'Зарплата' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Войти/ })).toBeInTheDocument();
  });
});
