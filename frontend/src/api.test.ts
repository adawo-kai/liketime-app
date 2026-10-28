// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError, api, changePassword, downloadFile, filenameFromDisposition, getToken, login, setToken, setUnauthorizedHandler,
} from './api';

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
  });
}

// Возвращает ошибку, из которой упал запрос, с правильным типом; если запрос успешен, тест падает
async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error('Ожидалась ошибка, но запрос завершился успешно');
}

describe('api client', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    setUnauthorizedHandler(() => {});
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('stores and clears the token in localStorage', () => {
    setToken('abc');
    expect(getToken()).toBe('abc');
    setToken(null);
    expect(getToken()).toBeNull();
  });

  it('sends Bearer token and JSON body when a token is stored', async () => {
    setToken('secret-token');
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    await api.post('/api/groups', { name: 'A' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/groups');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer secret-token');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ name: 'A' });
  });

  it('does not send Authorization header without a token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []));

    await api.get('/api/students');

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
  });

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    await expect(api.del('/api/groups/1')).resolves.toBeUndefined();
  });

  it('uses the server message for errors', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { message: 'Такая группа уже есть' }));

    const error = await rejection(api.post('/api/groups', { name: 'A' }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.message).toBe('Такая группа уже есть');
  });

  it('shows a readable message for 403 without a body', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 403 }));

    const error = await rejection(api.get('/api/users'));

    expect(error.message).toBe('Недостаточно прав для этого действия');
  });

  it('reports network failure as status 0', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await rejection(api.get('/api/students'));

    expect(error.status).toBe(0);
    expect(error.message).toContain('Нет связи с сервером');
  });

  it('calls the unauthorized handler on 401 when a token was sent', async () => {
    setToken('expired');
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValue(jsonResponse(401, { message: 'expired' }));

    await api.get('/api/auth/me').catch(() => undefined);

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not call the unauthorized handler on 401 for anonymous requests', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValue(jsonResponse(401, { message: 'Неверный логин или пароль' }));

    const error = await rejection(login('admin', 'wrong'));

    expect(handler).not.toHaveBeenCalled();
    expect(error.message).toBe('Неверный логин или пароль');
  });

  it('login stores the token and loads the current user', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 28800 }))
      .mockResolvedValueOnce(jsonResponse(200, { id: 1, login: 'admin', fullName: 'Админ', role: 'ADMIN' }));

    const me = await login('admin', 'pw-123456');

    expect(getToken()).toBe('new-token');
    expect(me.role).toBe('ADMIN');
    const [url] = fetchMock.mock.calls[1];
    expect(url).toBe('/api/auth/me');
  });
});

describe('filenameFromDisposition', () => {
  it('понимает filename*=UTF-8 с кодированием', () => {
    expect(filenameFromDisposition("attachment; filename*=UTF-8''salary-2026-10-01_2026-10-31.csv")).toBe('salary-2026-10-01_2026-10-31.csv');
    expect(filenameFromDisposition("attachment; filename*=UTF-8''%D0%B8%D1%82%D0%BE%D0%B3.csv")).toBe('итог.csv');
  });

  it('понимает обычный filename в кавычках и без них', () => {
    expect(filenameFromDisposition('attachment; filename="report 1.csv"')).toBe('report 1.csv');
    expect(filenameFromDisposition('attachment; filename=report.csv')).toBe('report.csv');
  });

  it('из реального ответа сервера берёт filename*, а не закодированный filename', () => {
    const header = "attachment; filename=\"=?UTF-8?Q?salary-2026-10-25=5F2026-10-25.csv?=\"; filename*=UTF-8''salary-2026-10-25_2026-10-25.csv";
    expect(filenameFromDisposition(header)).toBe('salary-2026-10-25_2026-10-25.csv');
  });

  it('возвращает null, если имени нет', () => {
    expect(filenameFromDisposition(null)).toBeNull();
    expect(filenameFromDisposition('attachment')).toBeNull();
  });
});

describe('downloadFile', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let clicked: HTMLAnchorElement[];

  beforeEach(() => {
    localStorage.clear();
    setUnauthorizedHandler(() => {});
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    URL.createObjectURL = vi.fn(() => 'blob:test');
    URL.revokeObjectURL = vi.fn();
    clicked = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this);
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('скачивает файл с токеном и берёт имя из заголовка', async () => {
    setToken('tok');
    fetchMock.mockResolvedValue(new Response('a;b', {
      status: 200, headers: { 'Content-Disposition': "attachment; filename*=UTF-8''salary.csv" },
    }));

    await downloadFile('/api/salary/periods/1/export.csv', 'fallback.csv');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/salary/periods/1/export.csv');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(clicked).toHaveLength(1);
    expect(clicked[0].download).toBe('salary.csv');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });

  it('использует запасное имя, если заголовка нет', async () => {
    fetchMock.mockResolvedValue(new Response('x', { status: 200 }));

    await downloadFile('/api/reports/attendance.csv', 'attendance.csv');

    expect(clicked[0].download).toBe('attendance.csv');
  });

  it('при ошибке бросает ApiError с понятным текстом и ничего не скачивает', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 403 }));

    const error = await downloadFile('/api/x.csv', 'x.csv').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.message).toBe('Недостаточно прав для этого действия');
    expect(clicked).toHaveLength(0);
  });

  it('при потере связи сообщает, что backend недоступен', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await downloadFile('/api/x.csv', 'x.csv').catch((e) => e);

    expect(error.status).toBe(0);
    expect(error.message).toContain('Нет связи с сервером');
  });
});

describe('changePassword', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    setUnauthorizedHandler(() => {});
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('отправляет PUT с обоими паролями и сохраняет выданный токен', async () => {
    setToken('old');
    fetchMock.mockResolvedValue(jsonResponse(200, { accessToken: 'brand-new', tokenType: 'Bearer', expiresIn: 28800 }));

    await changePassword('old-password-1', 'new-password-2');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/password');
    expect(init.method).toBe('PUT');
    expect(init.headers.Authorization).toBe('Bearer old');
    expect(JSON.parse(init.body)).toEqual({ currentPassword: 'old-password-1', newPassword: 'new-password-2' });
    expect(getToken()).toBe('brand-new');
  });

  it('неверный текущий пароль (400) не выкидывает пользователя из системы', async () => {
    setToken('old');
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValue(jsonResponse(400, { message: 'Текущий пароль указан неверно' }));

    const error = await changePassword('wrong', 'new-password-2').catch((e) => e);

    expect(error.message).toBe('Текущий пароль указан неверно');
    expect(handler).not.toHaveBeenCalled();
    expect(getToken()).toBe('old');
  });
});
