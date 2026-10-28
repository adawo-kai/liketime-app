import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, changePassword } from './api';
import ChangePasswordModal from './ChangePasswordModal';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  // Подменяем сам вызов смены пароля; как он ходит на сервер и хранит токен, проверяют тесты api.test.ts
  return { ...actual, changePassword: vi.fn() };
});

async function fill(user: ReturnType<typeof userEvent.setup>, current: string, next: string, repeat: string) {
  await user.type(screen.getByLabelText('Текущий пароль'), current);
  await user.type(screen.getByLabelText(/^Новый пароль/), next);
  await user.type(screen.getByLabelText('Повторите новый пароль'), repeat);
}

describe('окно «Сменить пароль»', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('при ошибке заполнения запрос не отправляется и причина понятна', async () => {
    const user = userEvent.setup();
    render(<ChangePasswordModal onClose={vi.fn()} />);

    await fill(user, 'old-password-1', 'short', 'short');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('не короче 10 символов');
    expect(changePassword).not.toHaveBeenCalled();
  });

  it('несовпадающий повтор не отправляется', async () => {
    const user = userEvent.setup();
    render(<ChangePasswordModal onClose={vi.fn()} />);

    await fill(user, 'old-password-1', 'new-password-2', 'new-password-3');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Пароли не совпадают');
    expect(changePassword).not.toHaveBeenCalled();
  });

  it('успех: отправляет оба пароля и сообщает об итоге', async () => {
    const user = userEvent.setup();
    vi.mocked(changePassword).mockResolvedValue(undefined);
    const onClose = vi.fn();
    render(<ChangePasswordModal onClose={onClose} />);

    await fill(user, 'old-password-1', 'new-password-2', 'new-password-2');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(changePassword).toHaveBeenCalledWith('old-password-1', 'new-password-2');
    expect(await screen.findByText('Пароль изменён')).toBeInTheDocument();
    expect(screen.getByText(/На других устройствах/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Готово' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('если текущий пароль неверный, показывается ответ сервера и окно остаётся открытым', async () => {
    const user = userEvent.setup();
    vi.mocked(changePassword).mockRejectedValue(new ApiError(400, 'Текущий пароль указан неверно'));
    render(<ChangePasswordModal onClose={vi.fn()} />);

    await fill(user, 'wrong-password-1', 'new-password-2', 'new-password-2');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Текущий пароль указан неверно');
    expect(screen.queryByText('Пароль изменён')).not.toBeInTheDocument();
  });

  it('при блокировке после серии ошибок показывает сообщение сервера', async () => {
    const user = userEvent.setup();
    vi.mocked(changePassword).mockRejectedValue(new ApiError(429, 'Слишком много неудачных попыток входа. Повторите через 15 мин.'));
    render(<ChangePasswordModal onClose={vi.fn()} />);

    await fill(user, 'old-password-1', 'new-password-2', 'new-password-2');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Повторите через 15 мин');
  });

  it('поля скрывают вводимые символы и подсказывают менеджеру паролей', () => {
    render(<ChangePasswordModal onClose={vi.fn()} />);

    expect(screen.getByLabelText('Текущий пароль')).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('Текущий пароль')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByLabelText(/^Новый пароль/)).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.getByLabelText('Повторите новый пароль')).toHaveAttribute('type', 'password');
  });

  it('«Отмена» закрывает окно без запроса', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ChangePasswordModal onClose={onClose} />);

    await user.click(screen.getByRole('button', { name: 'Отмена' }));

    expect(onClose).toHaveBeenCalled();
    expect(changePassword).not.toHaveBeenCalled();
  });
});
