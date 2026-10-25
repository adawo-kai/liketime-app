# API

Все запросы, кроме входа и приёма событий устройств, требуют заголовок `Authorization: Bearer <токен>`.
Ошибки приходят в виде `{"message": "..."}`. Время занятий в ответах даётся по местному времени школы
(`date`, `startTime`, `endTime`) и в UTC (`startsAt`); в базе всё хранится в UTC.

Роли: **A** = ADMIN, **M** = MANAGER, **T** = TEACHER (только свои данные).

## Вход и пользователи

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `POST /api/auth/login` | все | Вход, возвращает токен. После 5 неудач подряд блокировка на 15 минут (429). |
| `GET /api/auth/me` | все | Текущий пользователь |
| `GET/POST /api/users`, `PATCH /api/users/{id}`, `PUT /api/users/{id}/password` | A | Управление пользователями |

## Ученики, преподаватели, группы

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `/api/students`, `/api/teachers` | A, M | Список, карточка, создание, изменение, отключение |
| `GET /api/groups`, `GET /api/groups/{id}`, `GET /api/groups/{id}/students` | A, M, T (свои) | Просмотр |
| `POST/PUT/DELETE /api/groups...`, `POST/DELETE /api/groups/{id}/students...` | A, M | Группы и состав |

## Расписание и занятия

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `GET /api/schedule/slots?groupId=` | A, M, T (свои) | Шаблон расписания группы |
| `POST/PUT/DELETE /api/schedule/slots`, `PUT /api/schedule/slots/{id}/activate` | A, M | Изменение шаблона (удаление отключает) |
| `POST /api/schedule/generate` `{from,to,groupId?}` | A, M | Создать занятия по шаблонам (не дублирует, сообщает накладки), до 93 дней |
| `GET /api/lessons?from=&to=&groupId=&teacherId=` | A, M, T (свои) | Занятия за период (до 100 дней) |
| `GET /api/lessons/{id}` | A, M, T (свои) | Занятие |
| `POST /api/lessons`, `PUT /api/lessons/{id}` | A, M | Разовое занятие, перенос, замена преподавателя |
| `PUT /api/lessons/{id}/status` `{status}` | A, M; T: только «проведено» | PLANNED / HELD / CANCELLED |
| `GET/POST /api/rooms`, `PUT /api/rooms/{id}` | A, M | Кабинеты |

Правила: преподаватель и кабинет не могут вести два занятия одновременно; нельзя перенести занятие, по
которому уже есть отметки; нельзя менять занятия в утверждённом зарплатном периоде.

## Посещаемость

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `GET /api/lessons/{id}/attendance` | A, M, T (свои) | Ученики занятия с отметками (состав группы на дату занятия) |
| `PUT /api/lessons/{id}/attendance/{studentId}` `{status,note?}` | A, M, T (свои) | Отметка: PRESENT, LATE, ABSENT, EXCUSED |
| `PUT /api/lessons/{id}/attendance` `{records:[...]}` | A, M, T (свои) | Несколько отметок сразу (всё или ничего) |
| `DELETE /api/lessons/{id}/attendance/{studentId}` | A, M, T (свои) | Снять отметку |

Правила: отмечать можно за 30 минут до начала и позже; преподаватель правит отметки не позже 3 дней
после занятия; отметка прихода превращает занятие в «Проведено» (если назначен преподаватель);
отменённое занятие отметок не принимает.

## Устройства

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `GET /api/devices`, `GET /api/devices/{id}/persons`, `/unmatched` | A, M | Устройства, привязанные и незнакомые лица |
| `PUT /api/devices/{id}/persons`, `DELETE /api/devices/{id}/persons/{personId}` | A, M | Привязать лицо к ученику, отвязать |
| `POST /api/devices`, `PUT /api/devices/{id}`, `PUT /api/devices/{id}/status` | A | Настройка устройств |
| `GET /api/device-events?from=&to=&status=`, `POST /api/device-events/reprocess` | A, M | Журнал событий, повторный разбор |
| `POST /api/ingest/devices/{serial}/events` | устройство (`X-Device-Key`) | Приём события |
| `POST /api/ingest/devices/{serial}/heartbeat` | устройство (`X-Device-Key`) | «Устройство на связи» |

## Зарплата

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `GET /api/salary/rules` | A, M | Ставки |
| `POST/PUT/DELETE /api/salary/rules...`, `PUT /api/salary/rules/{id}/activate` | A | Изменение ставок |
| `GET /api/salary/periods`, `GET /api/salary/periods/{id}` | A, M | Периоды и итоги по преподавателям |
| `POST /api/salary/periods`, `DELETE /api/salary/periods/{id}` | A | Создать период, удалить (если не утверждён и без корректировок) |
| `POST /api/salary/periods/{id}/calculate`, `/approve`, `/pay`, `/reopen` | A | Рассчитать, утвердить, выплатить, вернуть в расчёт |
| `POST /api/salary/periods/{id}/adjustments`, `DELETE /api/salary/adjustments/{id}` | A | Премии и удержания (с причиной) |
| `GET /api/salary/periods/{id}/teachers/{teacherId}` | A, M; T: только своё и после утверждения | Занятия и корректировки преподавателя |
| `GET /api/salary/my` | T | Свои утверждённые периоды |
| `GET /api/salary/periods/{id}/export.csv`, `/export-details.csv` | A, M | Выгрузка для Excel |

Расчёт: за каждое **проведённое** занятие с преподавателем сумма = число приходов (PRESENT и LATE) × ставка.
Ставка выбирается самая конкретная: для группы, затем для преподавателя, затем общая. Если ставки нет,
сумма 0, а период нельзя утвердить. Утвердить можно только закончившийся период; при утверждении расчёт
выполняется заново. Утверждённый период блокирует правку посещаемости и занятий за его даты.

## Отчёты и журнал

| Метод и адрес | Кто | Что делает |
|---|---|---|
| `GET /api/reports/attendance?from=&to=&groupId=`, `/attendance.csv` | A, M | Посещаемость учеников |
| `GET /api/audit?entity=&entityId=&limit=` | A | Журнал действий |
