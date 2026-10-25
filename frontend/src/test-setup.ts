import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Без этого отрисованные в одном тесте компоненты оставались бы на странице и мешали следующему
afterEach(() => {
  cleanup();
});
