import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api';
import type { DeviceEventRow, DevicePerson, DeviceRow, UnmatchedFace } from '../types';
import DevicesPage from './DevicesPage';

const state = vi.hoisted(() => ({ role: 'ADMIN' as 'ADMIN' | 'MANAGER' | 'TEACHER' }));

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

const device: DeviceRow = {
  id: 1, name: 'Вход', model: 'DHI-ASI3213A-W', serialNumber: 'SN-1', location: 'Холл', host: '192.168.1.50',
  status: 'ACTIVE', lastSeenAt: '2026-10-26T03:05:00Z', secretRef: 'main', secretConfigured: true,
};

const events: DeviceEventRow[] = [
  { id: 1, deviceId: 1, deviceName: 'Вход', externalEventId: 'e1', externalPersonId: 'P-1', eventTime: '2026-10-26T03:05:00Z',
    confidence: 0.93, status: 'MATCHED', studentId: 11, studentName: 'Алия Ибраева', lessonId: 7, note: 'Приход отмечен' },
  { id: 2, deviceId: 1, deviceName: 'Вход', externalEventId: 'e2', externalPersonId: 'P-X', eventTime: '2026-10-26T03:06:00Z',
    confidence: 0.97, status: 'UNKNOWN_PERSON', studentId: null, studentName: null, lessonId: null, note: 'Лицо не привязано к ученику' },
];

function setup(devices: DeviceRow[] = [device]) {
  vi.mocked(api.get).mockImplementation(async (url: string) => {
    if (url === '/api/devices') return devices as never;
    if (url.startsWith('/api/device-events')) return events as never;
    if (url.endsWith('/persons')) return [{ externalPersonId: 'P-1', studentId: 11, studentName: 'Алия Ибраева' }] satisfies DevicePerson[] as never;
    if (url.endsWith('/unmatched')) return [{ externalPersonId: 'P-X', events: 3, lastSeenAt: '2026-10-26T03:06:00Z' }] satisfies UnmatchedFace[] as never;
    if (url.startsWith('/api/students')) return [
      { id: 11, fullName: 'Алия Ибраева', phone: null, parentName: null, parentPhone: null, status: 'ACTIVE' },
      { id: 12, fullName: 'Бакыт Осмонов', phone: null, parentName: null, parentPhone: null, status: 'ACTIVE' },
    ] as never;
    return [] as never;
  });
  vi.mocked(api.put).mockResolvedValue({} as never);
  vi.mocked(api.post).mockResolvedValue({ processed: 2, byStatus: { MATCHED: 1 } } as never);
  return render(<MemoryRouter><DevicesPage /></MemoryRouter>);
}

describe('устройства', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    state.role = 'ADMIN';
  });

  it('показывает устройства и предупреждает, если ключ не задан на сервере', async () => {
    setup([device, { ...device, id: 2, name: 'Запасной', serialNumber: 'SN-2', secretConfigured: false }]);

    expect(await screen.findByText('Вход')).toBeInTheDocument();
    expect(screen.getByText('SN-1')).toBeInTheDocument();
    expect(screen.getAllByText('Ключ не задан на сервере')).toHaveLength(1);
  });

  it('администратор может добавлять устройство, менеджер нет', async () => {
    setup();
    await screen.findByText('Вход');
    expect(screen.getByRole('button', { name: '+ Добавить устройство' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Изменить' })).toBeInTheDocument();
  });

  it('менеджер видит устройства и может привязывать лица, но не настраивать устройство', async () => {
    state.role = 'MANAGER';
    setup();
    await screen.findByText('Вход');

    expect(screen.queryByRole('button', { name: '+ Добавить устройство' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Изменить' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Отключить' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Лица' })).toBeInTheDocument();
  });

  it('отключает устройство', async () => {
    const user = userEvent.setup();
    setup();

    await user.click(await screen.findByRole('button', { name: 'Отключить' }));

    expect(api.put).toHaveBeenCalledWith('/api/devices/1/status', { status: 'DISABLED' });
  });

  it('незнакомое лицо можно привязать к ученику прямо из списка', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole('button', { name: 'Лица' }));

    expect(await screen.findByText('Устройство видело незнакомые лица')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Ученик для P-X'), '12');
    const row = screen.getByLabelText('Ученик для P-X').closest('tr') as HTMLElement;
    await user.click(within(row).getByRole('button', { name: 'Привязать' }));

    expect(api.put).toHaveBeenCalledWith('/api/devices/1/persons', { externalPersonId: 'P-X', studentId: 12 });
  });

  it('привязанное лицо можно отвязать', async () => {
    const user = userEvent.setup();
    setup();
    vi.mocked(api.del).mockResolvedValue(undefined as never);
    await user.click(await screen.findByRole('button', { name: 'Лица' }));

    await user.click(await screen.findByRole('button', { name: 'Отвязать' }));

    expect(api.del).toHaveBeenCalledWith('/api/devices/1/persons/P-1');
  });

  it('журнал событий показывает результат, уверенность и ссылку на занятие', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(screen.getByRole('tab', { name: 'События' }));

    // «Засчитано» есть и в фильтре статусов, поэтому ищем результат внутри таблицы
    const table = await screen.findByRole('table');
    expect(await within(table).findByText('Засчитано')).toBeInTheDocument();
    expect(within(table).getByText('Неизвестное лицо')).toBeInTheDocument();
    expect(within(table).getByText('93%')).toBeInTheDocument();
    expect(within(table).getByText('Алия Ибраева')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Занятие' })).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Занятие' })).toHaveAttribute('href', '/lessons/7');
  });

  it('«Разобрать заново» запускает повторный разбор за выбранные даты и сообщает итог', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(screen.getByRole('tab', { name: 'События' }));

    await user.click(await screen.findByRole('button', { name: 'Разобрать заново' }));

    expect(api.post).toHaveBeenCalledWith('/api/device-events/reprocess', expect.objectContaining({ from: expect.any(String), to: expect.any(String) }));
    expect(await screen.findByText(/Обработано событий: 2\. Засчитано: 1\./)).toBeInTheDocument();
  });

  it('фильтр по статусу попадает в запрос', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(screen.getByRole('tab', { name: 'События' }));
    await within(await screen.findByRole('table')).findByText('Засчитано');

    await user.selectOptions(screen.getByLabelText('Статус'), 'UNKNOWN_PERSON');

    const urls = vi.mocked(api.get).mock.calls.map(([u]) => u as string);
    expect(urls.some((u) => u.startsWith('/api/device-events') && u.includes('status=UNKNOWN_PERSON'))).toBe(true);
  });
});
