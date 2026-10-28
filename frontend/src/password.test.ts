import { describe, expect, it } from 'vitest';
import { passwordBytes, validateNewPassword } from './password';

const OK_CURRENT = 'old-password-1';
const OK_NEW = 'new-password-2';

describe('проверка нового пароля', () => {
  it('верно заполненная форма проходит', () => {
    expect(validateNewPassword(OK_CURRENT, OK_NEW, OK_NEW)).toBeNull();
  });

  it('нужен текущий пароль', () => {
    expect(validateNewPassword('', OK_NEW, OK_NEW)).toBe('Введите текущий пароль');
  });

  it('новый пароль не короче 10 символов', () => {
    expect(validateNewPassword(OK_CURRENT, '123456789', '123456789')).toContain('не короче 10');
    expect(validateNewPassword(OK_CURRENT, '1234567890', '1234567890')).toBeNull();
  });

  it('пароль из одних пробелов отклоняется', () => {
    expect(validateNewPassword(OK_CURRENT, '          ', '          ')).toContain('пробелов');
  });

  it('длина считается в байтах: русская буква занимает два', () => {
    expect(passwordBytes('abc')).toBe(3);
    expect(passwordBytes('ы')).toBe(2);
    expect(validateNewPassword(OK_CURRENT, 'a'.repeat(72), 'a'.repeat(72))).toBeNull();
    expect(validateNewPassword(OK_CURRENT, 'a'.repeat(73), 'a'.repeat(73))).toContain('слишком длинный');
    expect(validateNewPassword(OK_CURRENT, 'ы'.repeat(36), 'ы'.repeat(36))).toBeNull();
    expect(validateNewPassword(OK_CURRENT, 'ы'.repeat(37), 'ы'.repeat(37))).toContain('слишком длинный');
  });

  it('новый пароль должен отличаться от текущего', () => {
    expect(validateNewPassword(OK_NEW, OK_NEW, OK_NEW)).toBe('Новый пароль должен отличаться от текущего');
  });

  it('повтор должен совпадать', () => {
    expect(validateNewPassword(OK_CURRENT, OK_NEW, 'other-password-3')).toBe('Пароли не совпадают');
  });
});
