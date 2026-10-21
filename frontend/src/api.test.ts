// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, getToken, login, setToken, setUnauthorizedHandler } from './api';

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
