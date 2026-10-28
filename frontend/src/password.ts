// Требования к паролю совпадают с серверными (backend: PasswordPolicy), чтобы ошибку было видно сразу, без запроса.

export const MIN_PASSWORD_LENGTH = 10;
/** Предел BCrypt: считается в байтах, русская буква занимает 2 байта */
export const MAX_PASSWORD_BYTES = 72;

export function passwordBytes(password: string): number {
  return new TextEncoder().encode(password).length;
}

/** Возвращает текст ошибки или null, если форма заполнена верно */
export function validateNewPassword(current: string, next: string, repeat: string): string | null {
  if (current === '') return 'Введите текущий пароль';
  if (next.length < MIN_PASSWORD_LENGTH) return `Новый пароль должен быть не короче ${MIN_PASSWORD_LENGTH} символов`;
  if (next.trim() === '') return 'Пароль не может состоять из одних пробелов';
  if (passwordBytes(next) > MAX_PASSWORD_BYTES) {
    return `Пароль слишком длинный: не более ${MAX_PASSWORD_BYTES} байт (около 72 латинских или 36 русских букв)`;
  }
  if (next === current) return 'Новый пароль должен отличаться от текущего';
  if (next !== repeat) return 'Пароли не совпадают';
  return null;
}
