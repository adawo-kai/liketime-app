#!/usr/bin/env node
// Симулятор устройства распознавания лиц (Dahua DHI-ASI3213A-W) для разработки без самого устройства.
// Отправляет события в тот же адрес, что и настоящее устройство, поэтому проходит весь путь:
// проверка ключа -> защита от дублей -> привязка лица к ученику -> поиск занятия -> отметка посещаемости.
//
// Примеры:
//   node simulator.mjs --serial SN-DEMO-1 --key $DEVICE_KEY --send P-1
//   node simulator.mjs --serial SN-DEMO-1 --scenario scenarios/basic.json
//   node simulator.mjs --serial SN-DEMO-1 --heartbeat
//
// Ключ устройства лучше передавать переменной окружения DEVICE_KEY, чтобы он не попадал в историю команд.

import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';

const HELP = `Использование: node simulator.mjs --serial <серийный номер> [параметры]

  --url <адрес>          адрес сервера (по умолчанию http://localhost:8080)
  --serial <номер>       серийный номер устройства, как он записан в системе
  --key <ключ>           ключ устройства (или переменная окружения DEVICE_KEY)
  --send <ID лица>       отправить одно событие для лица с таким ID на устройстве
  --confidence <0..1>    уверенность распознавания для --send (по умолчанию 0.95)
  --at <время ISO>       время события для --send (по умолчанию сейчас)
  --scenario <файл>      выполнить сценарий из JSON-файла
  --heartbeat            отправить сигнал «устройство на связи»
  --delay-ms <мс>        пауза между событиями сценария (по умолчанию 200)
  --help                 эта справка
`;

/**
 * Превращает описание события из сценария в тело запроса.
 * offsetSeconds считается от now: отрицательное значение это прошлое.
 */
export function buildEvent(spec, now, makeId = () => `sim-${randomUUID()}`) {
  if (!spec || typeof spec.personId !== 'string' || spec.personId === '') {
    throw new Error('В событии сценария нужно поле personId');
  }
  const at = new Date(now.getTime() + (spec.offsetSeconds ?? 0) * 1000);
  const body = {
    eventId: spec.eventId ?? makeId(),
    personId: spec.personId,
    time: at.toISOString(),
  };
  if (spec.confidence !== undefined && spec.confidence !== null) {
    body.confidence = spec.confidence;
  }
  return body;
}

/** Разворачивает сценарий в список запросов; repeat > 1 повторяет одно и то же событие (проверка защиты от дублей) */
export function planScenario(scenario, now, makeId) {
  if (!scenario || !Array.isArray(scenario.events) || scenario.events.length === 0) {
    throw new Error('В сценарии должен быть непустой список events');
  }
  const plan = [];
  scenario.events.forEach((spec, index) => {
    const body = buildEvent(spec, now, makeId);
    const repeat = Math.max(1, spec.repeat ?? 1);
    for (let i = 0; i < repeat; i++) {
      plan.push({ index: index + 1, note: spec.note ?? '', body, resend: i > 0 });
    }
  });
  return plan;
}

async function post(url, key, body) {
  const headers = { 'X-Device-Key': key };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(url, { method: 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: response.status, data };
}

function describeResult(result) {
  if (result.status >= 200 && result.status < 300) {
    const d = result.data ?? {};
    const parts = [d.status ?? 'OK'];
    if (d.duplicate) parts.push('повтор');
    if (d.lessonId) parts.push(`занятие #${d.lessonId}`);
    if (d.note) parts.push(d.note);
    return parts.join(' | ');
  }
  const message = typeof result.data === 'object' && result.data?.message ? result.data.message : JSON.stringify(result.data);
  return `ОШИБКА ${result.status}: ${message}`;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const { values } = parseArgs({
    options: {
      url: { type: 'string', default: 'http://localhost:8080' },
      serial: { type: 'string' },
      key: { type: 'string' },
      send: { type: 'string' },
      confidence: { type: 'string' },
      at: { type: 'string' },
      scenario: { type: 'string' },
      heartbeat: { type: 'boolean', default: false },
      'delay-ms': { type: 'string', default: '200' },
      help: { type: 'boolean', default: false },
    },
  });

  if (values.help) {
    console.log(HELP);
    return 0;
  }
  const key = values.key ?? process.env.DEVICE_KEY;
  if (!values.serial || !key) {
    console.error('Нужны --serial и ключ устройства (--key или переменная DEVICE_KEY).\n');
    console.error(HELP);
    return 2;
  }
  if (!values.send && !values.scenario && !values.heartbeat) {
    console.error('Укажите, что делать: --send, --scenario или --heartbeat.\n');
    console.error(HELP);
    return 2;
  }

  const base = `${values.url.replace(/\/+$/, '')}/api/ingest/devices/${encodeURIComponent(values.serial)}`;
  let failed = false;

  if (values.heartbeat) {
    const result = await post(`${base}/heartbeat`, key);
    console.log(`heartbeat: ${result.status === 204 ? 'OK' : describeResult(result)}`);
    failed ||= result.status !== 204;
  }

  if (values.send) {
    const body = buildEvent({
      personId: values.send,
      confidence: values.confidence !== undefined ? Number(values.confidence) : 0.95,
    }, values.at ? new Date(values.at) : new Date());
    if (values.at) body.time = new Date(values.at).toISOString();
    const result = await post(`${base}/events`, key, body);
    console.log(`${values.send}: ${describeResult(result)}`);
    failed ||= result.status >= 300;
  }

  if (values.scenario) {
    const scenario = JSON.parse(await readFile(values.scenario, 'utf8'));
    if (scenario.name) console.log(`Сценарий: ${scenario.name}`);
    const delay = Number(values['delay-ms']);
    for (const step of planScenario(scenario, new Date())) {
      const result = await post(`${base}/events`, key, step.body);
      const ok = result.status >= 200 && result.status < 300;
      console.log(`${ok ? '+' : '!'} #${step.index} ${step.body.personId}${step.resend ? ' (повторная отправка)' : ''}`
        + `${step.note ? ` [${step.note}]` : ''}: ${describeResult(result)}`);
      failed ||= !ok;
      await sleep(delay);
    }
  }

  return failed ? 1 : 0;
}

// Запуск только как программа, а не при импорте в тестах
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().then((code) => process.exit(code)).catch((error) => {
    console.error(`Не удалось выполнить: ${error.message}`);
    process.exit(1);
  });
}
