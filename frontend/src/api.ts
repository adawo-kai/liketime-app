const TOKEN_KEY = 'liketime.token';

export type Role = 'ADMIN' | 'MANAGER' | 'TEACHER';
export type Status = 'ACTIVE' | 'INACTIVE';

export interface Me { id: number; login: string; fullName: string; role: Role }
export interface UserRow { id: number; login: string; fullName: string; role: Role; enabled: boolean }
export interface Student {
  id: number; fullName: string; phone: string | null;
  parentName: string | null; parentPhone: string | null; status: Status;
}
export interface Teacher { id: number; userId: number | null; fullName: string; phone: string | null; status: Status }
export interface Group { id: number; name: string; teacherId: number | null; status: Status; activeStudents: number }
export interface Member { studentId: number; fullName: string; joinedOn: string; leftOn: string | null }

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Администратор',
  MANAGER: 'Менеджер',
  TEACHER: 'Преподаватель',
};

export function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token); else localStorage.removeItem(TOKEN_KEY);
  } catch { /* хранилище недоступно: работаем без запоминания входа */ }
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

// Вызывается при ответе 401 на запрос с токеном (токен истёк или пользователь отключён)
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) { onUnauthorized = handler; }

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте, что backend запущен.');
  }

  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    let message = `Ошибка ${response.status}`;
    if (response.status === 403) message = 'Недостаточно прав для этого действия';
    try {
      const data = await response.json();
      if (data?.message) message = data.message;
    } catch { /* тела ответа нет */ }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body ?? {}),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body ?? {}),
  patch: <T>(url: string, body: unknown) => request<T>('PATCH', url, body),
  del: (url: string) => request<void>('DELETE', url),
};

export async function login(loginName: string, password: string): Promise<Me> {
  const token = await api.post<{ accessToken: string }>('/api/auth/login', { login: loginName, password });
  setToken(token.accessToken);
  return api.get<Me>('/api/auth/me');
}

/** Имя файла из заголовка Content-Disposition (понимает и filename*=UTF-8''..., и filename="...") */
export function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header);
  if (encoded) {
    try { return decodeURIComponent(encoded[1].trim()); } catch { /* падаем дальше на простой вариант */ }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header);
  return plain ? plain[1].trim() : null;
}

/**
 * Скачивает файл (например, CSV для Excel). Обычная ссылка не подойдёт: запрос должен нести токен входа,
 * поэтому файл загружается через fetch и отдаётся браузеру как вложение.
 */
export async function downloadFile(url: string, fallbackName: string): Promise<void> {
  const token = getToken();
  let response: Response;
  try {
    response = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте, что backend запущен.');
  }
  if (!response.ok) {
    if (response.status === 401 && token) onUnauthorized();
    let message = response.status === 403 ? 'Недостаточно прав для этого действия' : `Ошибка ${response.status}`;
    try {
      const data = await response.json();
      if (data?.message) message = data.message;
    } catch { /* тела ответа нет */ }
    throw new ApiError(response.status, message);
  }
  const blob = await response.blob();
  const name = filenameFromDisposition(response.headers.get('Content-Disposition')) ?? fallbackName;
  const link = document.createElement('a');
  const objectUrl = URL.createObjectURL(blob);
  link.href = objectUrl;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}
