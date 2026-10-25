// Типы ответов API для расписания, посещаемости, устройств, зарплаты и отчётов.
// Базовые типы (пользователь, ученик, преподаватель, группа) лежат в api.ts.

export type LessonStatus = 'PLANNED' | 'HELD' | 'CANCELLED';
export type LessonOrigin = 'GENERATED' | 'MANUAL';

export interface Lesson {
  id: number;
  groupId: number;
  groupName: string;
  teacherId: number | null;
  teacherName: string | null;
  roomId: number | null;
  roomName: string | null;
  /** Местная дата занятия (Бишкек), YYYY-MM-DD */
  date: string;
  /** Местное время, HH:mm:ss */
  startTime: string;
  endTime: string;
  /** Тот же момент начала в UTC */
  startsAt: string;
  status: LessonStatus;
  origin: LessonOrigin;
  note: string | null;
  /** Сколько учеников пришли (пришёл + опоздал) */
  visits: number;
}

export interface Slot {
  id: number;
  groupId: number;
  /** 1 = понедельник ... 7 = воскресенье */
  weekday: number;
  startTime: string;
  endTime: string;
  roomId: number | null;
  active: boolean;
}

export interface Room { id: number; name: string }

export interface GenerateResult { created: number; alreadyExisted: number; conflicts: string[] }

export type AttendanceStatus = 'PRESENT' | 'LATE' | 'ABSENT' | 'EXCUSED';
export type AttendanceSource = 'MANUAL' | 'DEVICE';

export interface AttendanceRow {
  studentId: number;
  fullName: string;
  /** null: ученик ещё не отмечен */
  status: AttendanceStatus | null;
  source: AttendanceSource | null;
  checkInAt: string | null;
  note: string | null;
  /** false: ученик уже вышел из группы, но отметка у него осталась */
  member: boolean;
}

export interface Roster { lessonId: number; lessonStatus: LessonStatus; students: AttendanceRow[] }

export type DeviceStatus = 'ACTIVE' | 'DISABLED';

export interface DeviceRow {
  id: number;
  name: string;
  model: string | null;
  serialNumber: string;
  location: string | null;
  host: string | null;
  status: DeviceStatus;
  lastSeenAt: string | null;
  secretRef: string | null;
  /** false: секрет с таким именем не задан в конфигурации сервера, устройство не сможет подключиться */
  secretConfigured: boolean;
}

export interface DevicePerson { externalPersonId: string; studentId: number; studentName: string }
export interface UnmatchedFace { externalPersonId: string; events: number; lastSeenAt: string }

export type EventStatus = 'MATCHED' | 'UNKNOWN_PERSON' | 'LOW_CONFIDENCE' | 'NO_LESSON' | 'IGNORED';

export interface DeviceEventRow {
  id: number;
  deviceId: number;
  deviceName: string;
  externalEventId: string;
  externalPersonId: string | null;
  eventTime: string;
  confidence: number | null;
  status: EventStatus;
  studentId: number | null;
  studentName: string | null;
  lessonId: number | null;
  note: string | null;
}

export interface ReprocessResult { processed: number; byStatus: Partial<Record<EventStatus, number>> }

export type PeriodStatus = 'OPEN' | 'CALCULATED' | 'APPROVED' | 'PAID';

export interface SalaryRule {
  id: number;
  teacherId: number | null;
  teacherName: string | null;
  groupId: number | null;
  groupName: string | null;
  type: 'PER_VISIT';
  amount: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
  comment: string | null;
}

export interface SalaryPeriod {
  id: number;
  startsOn: string;
  endsOn: string;
  status: PeriodStatus;
  calculatedAt: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  paidAt: string | null;
  paidBy: string | null;
  accrued: number;
  adjustments: number;
  total: number;
}

export interface TeacherTotal {
  teacherId: number;
  teacherName: string;
  lessons: number;
  visits: number;
  accrued: number;
  adjustments: number;
  total: number;
}

export interface PeriodSummary { period: SalaryPeriod; teachers: TeacherTotal[]; linesWithoutRule: number }

export interface SalaryLineRow {
  lessonId: number;
  date: string;
  groupId: number;
  groupName: string;
  visits: number;
  unitAmount: number;
  amount: number;
  hasRule: boolean;
}

export interface Adjustment { id: number; teacherId: number; amount: number; reason: string; createdBy: string; createdAt: string }

export interface TeacherDetail {
  periodId: number;
  startsOn: string;
  endsOn: string;
  status: PeriodStatus;
  teacherId: number;
  teacherName: string;
  lines: SalaryLineRow[];
  adjustments: Adjustment[];
  accrued: number;
  adjustmentsTotal: number;
  total: number;
}

export interface MyPeriod {
  periodId: number;
  startsOn: string;
  endsOn: string;
  status: PeriodStatus;
  teacherId: number;
  accrued: number;
  adjustments: number;
  total: number;
}

export interface CalculationResult { lines: number; withoutRule: number; accrued: number }

export interface AttendanceReportRow {
  groupId: number;
  groupName: string;
  studentId: number;
  studentName: string;
  lessons: number;
  present: number;
  late: number;
  absent: number;
  excused: number;
  unmarked: number;
  /** Доля посещённых занятий, 0..1 */
  rate: number;
}

export interface AuditRow {
  id: number;
  actor: string | null;
  action: string;
  entity: string;
  entityId: number | null;
  details: unknown;
  createdAt: string;
}
