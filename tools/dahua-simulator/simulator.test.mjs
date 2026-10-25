// Запуск: node --test
import assert from 'node:assert/strict';
import test from 'node:test';
import { buildEvent, planScenario } from './simulator.mjs';

const NOW = new Date('2026-10-26T04:00:00.000Z');
const ids = () => {
  let n = 0;
  return () => `id-${++n}`;
};

test('событие получает время от now с учётом смещения', () => {
  const event = buildEvent({ personId: 'P-1', offsetSeconds: -90, confidence: 0.9 }, NOW, ids());

  assert.deepEqual(event, {
    eventId: 'id-1', personId: 'P-1', time: '2026-10-26T03:58:30.000Z', confidence: 0.9,
  });
});

test('без смещения и уверенности время равно now, а confidence не добавляется', () => {
  const event = buildEvent({ personId: 'P-2' }, NOW, ids());

  assert.equal(event.time, NOW.toISOString());
  assert.equal('confidence' in event, false);
});

test('заданный eventId сохраняется', () => {
  assert.equal(buildEvent({ personId: 'P-1', eventId: 'custom' }, NOW, ids()).eventId, 'custom');
});

test('без personId событие не строится', () => {
  assert.throws(() => buildEvent({ offsetSeconds: 0 }, NOW, ids()), /personId/);
  assert.throws(() => buildEvent({ personId: '' }, NOW, ids()), /personId/);
});

test('repeat повторяет то же самое событие с тем же eventId', () => {
  const plan = planScenario({ events: [{ personId: 'P-1', eventId: 'dup', repeat: 3 }, { personId: 'P-2' }] }, NOW, ids());

  assert.equal(plan.length, 4);
  assert.deepEqual(plan.slice(0, 3).map((p) => p.body.eventId), ['dup', 'dup', 'dup']);
  assert.deepEqual(plan.slice(0, 3).map((p) => p.resend), [false, true, true]);
  assert.equal(plan[3].body.personId, 'P-2');
  assert.equal(plan[3].index, 2);
});

test('пустой сценарий отклоняется', () => {
  assert.throws(() => planScenario({ events: [] }, NOW, ids()), /events/);
  assert.throws(() => planScenario({}, NOW, ids()), /events/);
});

test('у разных событий без eventId идентификаторы разные', () => {
  const plan = planScenario({ events: [{ personId: 'P-1' }, { personId: 'P-1' }] }, NOW, ids());

  assert.notEqual(plan[0].body.eventId, plan[1].body.eventId);
});
