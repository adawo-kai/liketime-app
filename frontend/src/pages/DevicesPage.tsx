import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { api, type Student } from '../api';
import {
  EVENT_STATUS_LABELS, formatConfidence, formatDate, formatDateTime, toIsoDate,
} from '../format';
import type {
  DeviceEventRow, DevicePerson, DeviceRow, EventStatus, ReprocessResult, UnmatchedFace,
} from '../types';
import {
  Badge, ErrorBox, Field, FormModal, Modal, Tabs, useActionRunner, useLoad, type Tone,
} from '../ui';

type Tab = 'devices' | 'events';

const EVENT_TONE: Record<EventStatus, Tone> = {
  MATCHED: 'ok', UNKNOWN_PERSON: 'warn', LOW_CONFIDENCE: 'warn', NO_LESSON: 'info', IGNORED: 'off',
};

export default function DevicesPage() {
  const [tab, setTab] = useState<Tab>('devices');
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Устройства</h1>
          <div className="muted">Терминалы распознавания лиц Dahua и события, которые они присылают</div>
        </div>
      </div>
      <Tabs<Tab> tabs={[{ id: 'devices', label: 'Устройства' }, { id: 'events', label: 'События' }]} value={tab} onChange={setTab} />
      {tab === 'devices' ? <DevicesTab /> : <EventsTab />}
    </>
  );
}

function DevicesTab() {
  const { me } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  const [editing, setEditing] = useState<DeviceRow | 'new' | null>(null);
  const [persons, setPersons] = useState<DeviceRow | null>(null);
  const actions = useActionRunner();
  const devices = useLoad(() => api.get<DeviceRow[]>('/api/devices'));

  async function toggle(d: DeviceRow) {
    const status = d.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    if (await actions.run(() => api.put(`/api/devices/${d.id}/status`, { status }))) devices.reload();
  }

  return (
    <>
      {isAdmin && (
        <div className="toolbar"><button className="btn" onClick={() => setEditing('new')}>+ Добавить устройство</button></div>
      )}
      <ErrorBox message={devices.error ?? actions.error} />
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Название</th><th>Серийный номер</th><th>Место</th><th>Последний сигнал</th><th>Статус</th><th /></tr></thead>
          <tbody>
            {devices.data?.map((d) => (
              <tr key={d.id}>
                <td>{d.name}{d.model && <div className="muted small">{d.model}</div>}</td>
                <td className="nowrap">{d.serialNumber}</td>
                <td>{d.location ?? '—'}</td>
                <td className="nowrap">{formatDateTime(d.lastSeenAt)}</td>
                <td>
                  <Badge tone={d.status === 'ACTIVE' ? 'ok' : 'off'}>{d.status === 'ACTIVE' ? 'Включено' : 'Отключено'}</Badge>
                  {!d.secretConfigured && (
                    <div><Badge tone="danger">Ключ не задан на сервере</Badge></div>
                  )}
                </td>
                <td className="row-actions">
                  <button className="btn ghost small" onClick={() => setPersons(d)}>Лица</button>
                  {isAdmin && <button className="btn ghost small" onClick={() => setEditing(d)}>Изменить</button>}
                  {isAdmin && (
                    <button className="btn ghost small" onClick={() => toggle(d)}>{d.status === 'ACTIVE' ? 'Отключить' : 'Включить'}</button>
                  )}
                </td>
              </tr>
            ))}
            {devices.data?.length === 0 && (
              <tr><td colSpan={6} className="muted">
                Устройств пока нет.{isAdmin ? ' Добавьте терминал, чтобы принимать его события.' : ' Устройства добавляет администратор.'}
              </td></tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && <DeviceForm device={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={devices.reload} />}
      {persons && <PersonsModal device={persons} onClose={() => setPersons(null)} />}
    </>
  );
}

function DeviceForm({ device, onClose, onSaved }: { device: DeviceRow | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(device?.name ?? '');
  const [model, setModel] = useState(device?.model ?? '');
  const [serial, setSerial] = useState(device?.serialNumber ?? '');
  const [location, setLocation] = useState(device?.location ?? '');
  const [host, setHost] = useState(device?.host ?? '');
  const [secretRef, setSecretRef] = useState(device?.secretRef ?? '');

  async function submit() {
    const common = {
      name: name.trim(), model: model.trim() || null, location: location.trim() || null,
      host: host.trim() || null, secretRef: secretRef.trim(),
    };
    if (device) await api.put(`/api/devices/${device.id}`, common);
    else await api.post('/api/devices', { ...common, serialNumber: serial.trim() });
    onSaved();
  }

  return (
    <FormModal title={device ? 'Изменить устройство' : 'Новое устройство'} submitLabel={device ? 'Сохранить' : 'Добавить'}
               onClose={onClose} onSubmit={submit}>
      <Field label="Название"><input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus /></Field>
      <Field label="Модель"><input value={model} onChange={(e) => setModel(e.target.value)} maxLength={100} placeholder="DHI-ASI3213A-W" /></Field>
      <Field label="Серийный номер">
        <input value={serial} onChange={(e) => setSerial(e.target.value)} maxLength={100} required disabled={device !== null} />
      </Field>
      <Field label="Место установки"><input value={location} onChange={(e) => setLocation(e.target.value)} maxLength={150} /></Field>
      <Field label="Адрес в сети"><input value={host} onChange={(e) => setHost(e.target.value)} maxLength={255} placeholder="192.168.1.50" /></Field>
      <Field label="Имя ключа устройства">
        <input value={secretRef} onChange={(e) => setSecretRef(e.target.value)} maxLength={100} required pattern="[A-Za-z0-9._\-]+" />
      </Field>
      <p className="muted small">
        Сам ключ в базе не хранится. Его значение задаётся в настройках сервера
        (<code>liketime.devices.secrets.{secretRef || 'имя'}</code>) и вводится в устройстве.
      </p>
    </FormModal>
  );
}

function PersonsModal({ device, onClose }: { device: DeviceRow; onClose: () => void }) {
  const persons = useLoad(() => api.get<DevicePerson[]>(`/api/devices/${device.id}/persons`), [device.id]);
  const unmatched = useLoad(() => api.get<UnmatchedFace[]>(`/api/devices/${device.id}/unmatched`), [device.id]);
  const students = useLoad(() => api.get<Student[]>('/api/students?status=ACTIVE'));
  const [personId, setPersonId] = useState('');
  const [studentId, setStudentId] = useState('');
  const actions = useActionRunner();

  const linkedStudents = new Set((persons.data ?? []).map((p) => p.studentId));
  const options = (students.data ?? []).filter((s) => !linkedStudents.has(s.id));

  async function link(externalPersonId: string, student: string) {
    if (student === '') return;
    const ok = await actions.run(() => api.put(`/api/devices/${device.id}/persons`, {
      externalPersonId, studentId: Number(student),
    }));
    if (ok) {
      setPersonId('');
      setStudentId('');
      persons.reload();
      unmatched.reload();
    }
  }

  async function unlink(p: DevicePerson) {
    if (await actions.run(() => api.del(`/api/devices/${device.id}/persons/${encodeURIComponent(p.externalPersonId)}`))) {
      persons.reload();
    }
  }

  return (
    <Modal title={`Лица: ${device.name}`} onClose={onClose}>
      <ErrorBox message={persons.error ?? unmatched.error ?? students.error ?? actions.error} />
      <p className="muted small">
        Лицо регистрируется на самом устройстве под идентификатором (Person ID). Здесь вы указываете, какому ученику он принадлежит.
      </p>

      {(unmatched.data?.length ?? 0) > 0 && (
        <>
          <h3 className="section-title" style={{ marginTop: 0 }}>Устройство видело незнакомые лица</h3>
          <div className="card table-wrap" style={{ marginBottom: 16 }}>
            <table>
              <thead><tr><th>ID на устройстве</th><th className="num">Событий</th><th>Привязать к ученику</th></tr></thead>
              <tbody>
                {unmatched.data!.map((u) => <UnmatchedLine key={u.externalPersonId} face={u} options={options} onLink={link} busy={actions.busy} />)}
              </tbody>
            </table>
          </div>
        </>
      )}

      <h3 className="section-title" style={{ marginTop: 0 }}>Привязанные лица</h3>
      <div className="card table-wrap" style={{ marginBottom: 16 }}>
        <table>
          <thead><tr><th>ID на устройстве</th><th>Ученик</th><th /></tr></thead>
          <tbody>
            {persons.data?.map((p) => (
              <tr key={p.externalPersonId}>
                <td>{p.externalPersonId}</td><td>{p.studentName}</td>
                <td className="row-actions"><button className="btn ghost small" onClick={() => unlink(p)}>Отвязать</button></td>
              </tr>
            ))}
            {persons.data?.length === 0 && <tr><td colSpan={3} className="muted">Пока ни одного лица не привязано</td></tr>}
          </tbody>
        </table>
      </div>

      <h3 className="section-title" style={{ marginTop: 0 }}>Привязать вручную</h3>
      <div className="inline-form">
        <Field label="ID на устройстве"><input value={personId} onChange={(e) => setPersonId(e.target.value)} maxLength={100} /></Field>
        <Field label="Ученик">
          <select value={studentId} onChange={(e) => setStudentId(e.target.value)}>
            <option value="">Выберите…</option>
            {options.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
          </select>
        </Field>
        <button className="btn" disabled={actions.busy || personId.trim() === '' || studentId === ''}
                onClick={() => link(personId.trim(), studentId)}>Привязать</button>
      </div>
      <div className="actions"><button className="btn ghost" onClick={onClose}>Закрыть</button></div>
    </Modal>
  );
}

function UnmatchedLine({ face, options, onLink, busy }: {
  face: UnmatchedFace; options: Student[]; onLink: (personId: string, studentId: string) => void; busy: boolean;
}) {
  const [studentId, setStudentId] = useState('');
  return (
    <tr>
      <td>{face.externalPersonId}<div className="muted small">последний раз {formatDateTime(face.lastSeenAt)}</div></td>
      <td className="num">{face.events}</td>
      <td>
        <div className="row-actions" style={{ justifyContent: 'flex-start' }}>
          <select value={studentId} onChange={(e) => setStudentId(e.target.value)} aria-label={`Ученик для ${face.externalPersonId}`}>
            <option value="">Выберите…</option>
            {options.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
          </select>
          <button className="btn small" disabled={busy || studentId === ''} onClick={() => onLink(face.externalPersonId, studentId)}>Привязать</button>
        </div>
      </td>
    </tr>
  );
}

function EventsTab() {
  const today = toIsoDate(new Date());
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [status, setStatus] = useState<'' | EventStatus>('');
  const [reprocessed, setReprocessed] = useState<ReprocessResult | null>(null);
  const actions = useActionRunner();

  const query = `from=${from}&to=${to}${status ? `&status=${status}` : ''}`;
  const events = useLoad(() => api.get<DeviceEventRow[]>(`/api/device-events?${query}`), [query]);

  async function reprocess() {
    setReprocessed(null);
    const ok = await actions.run(async () => {
      setReprocessed(await api.post<ReprocessResult>('/api/device-events/reprocess', { from, to }));
    });
    if (ok) events.reload();
  }

  return (
    <>
      <div className="toolbar">
        <label className="inline">С <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="inline">По <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <label className="inline">Статус
          <select value={status} onChange={(e) => setStatus(e.target.value as '' | EventStatus)}>
            <option value="">Все</option>
            {(Object.keys(EVENT_STATUS_LABELS) as EventStatus[]).map((s) => <option key={s} value={s}>{EVENT_STATUS_LABELS[s]}</option>)}
          </select>
        </label>
        <span className="spacer" />
        <button className="btn ghost" onClick={reprocess} disabled={actions.busy}>Разобрать заново</button>
      </div>
      <p className="muted small">
        «Разобрать заново» повторно сопоставляет события со статусами «Неизвестное лицо» и «Нет занятия» за выбранные даты:
        например, после привязки лица к ученику или создания пропущенного занятия.
      </p>
      <ErrorBox message={events.error ?? actions.error} />
      {reprocessed && (
        <div className="notice info">
          Обработано событий: {reprocessed.processed}. Засчитано: {reprocessed.byStatus.MATCHED ?? 0}.
        </div>
      )}
      <div className="card table-wrap">
        <table>
          <thead><tr><th>Время</th><th>Устройство</th><th>ID лица</th><th className="num">Уверенность</th><th>Результат</th><th>Ученик</th><th /></tr></thead>
          <tbody>
            {events.data?.map((e) => (
              <tr key={e.id}>
                <td className="nowrap">{formatDateTime(e.eventTime)}</td>
                <td>{e.deviceName}</td>
                <td>{e.externalPersonId ?? '—'}</td>
                <td className="num">{formatConfidence(e.confidence)}</td>
                <td>
                  <Badge tone={EVENT_TONE[e.status]}>{EVENT_STATUS_LABELS[e.status]}</Badge>
                  {e.note && <div className="muted small wrap">{e.note}</div>}
                </td>
                <td>{e.studentName ?? '—'}</td>
                <td className="row-actions">
                  {e.lessonId !== null && <Link className="btn ghost small" to={`/lessons/${e.lessonId}`}>Занятие</Link>}
                </td>
              </tr>
            ))}
            {events.data?.length === 0 && (
              <tr><td colSpan={7} className="muted">За {formatDate(from)}{from !== to && ` – ${formatDate(to)}`} событий нет</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
