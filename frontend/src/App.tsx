import { useState } from 'react';
import { Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import { ROLE_LABELS, type Role } from './api';
import ChangePasswordModal from './ChangePasswordModal';
import LoginPage from './pages/LoginPage';
import GroupsPage from './pages/GroupsPage';
import GroupDetailPage from './pages/GroupDetailPage';
import StudentsPage from './pages/StudentsPage';
import TeachersPage from './pages/TeachersPage';
import UsersPage from './pages/UsersPage';
import SchedulePage from './pages/SchedulePage';
import LessonPage from './pages/LessonPage';
import RoomsPage from './pages/RoomsPage';
import DevicesPage from './pages/DevicesPage';
import SalaryPage from './pages/SalaryPage';
import SalaryPeriodPage from './pages/SalaryPeriodPage';
import MySalaryPage from './pages/MySalaryPage';
import ReportsPage from './pages/ReportsPage';
import AuditPage from './pages/AuditPage';

interface NavItem { to: string; label: string; roles: Role[] }

export const NAV: NavItem[] = [
  { to: '/schedule', label: 'Расписание', roles: ['ADMIN', 'MANAGER'] },
  { to: '/schedule', label: 'Мои занятия', roles: ['TEACHER'] },
  { to: '/groups', label: 'Группы', roles: ['ADMIN', 'MANAGER'] },
  { to: '/groups', label: 'Мои группы', roles: ['TEACHER'] },
  { to: '/students', label: 'Ученики', roles: ['ADMIN', 'MANAGER'] },
  { to: '/teachers', label: 'Преподаватели', roles: ['ADMIN', 'MANAGER'] },
  { to: '/devices', label: 'Устройства', roles: ['ADMIN', 'MANAGER'] },
  { to: '/salary', label: 'Зарплата', roles: ['ADMIN', 'MANAGER'] },
  { to: '/my-salary', label: 'Моя зарплата', roles: ['TEACHER'] },
  { to: '/reports', label: 'Отчёты', roles: ['ADMIN', 'MANAGER'] },
  { to: '/rooms', label: 'Кабинеты', roles: ['ADMIN', 'MANAGER'] },
  { to: '/users', label: 'Пользователи', roles: ['ADMIN'] },
  { to: '/audit', label: 'Журнал действий', roles: ['ADMIN'] },
];

export default function App() {
  const { me, loading, signOut } = useAuth();
  const [changingPassword, setChangingPassword] = useState(false);

  if (loading) return <div className="center muted">Загрузка…</div>;
  if (!me) return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );

  const staff = me.role !== 'TEACHER';
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">LikeTime</div>
        <nav>
          {NAV.filter((n) => n.roles.includes(me.role)).map((n) => (
            <NavLink key={n.label} to={n.to} className={({ isActive }) => (isActive ? 'active' : '')}>{n.label}</NavLink>
          ))}
        </nav>
        <div className="user">
          <div className="name">{me.fullName}</div>
          <div className="muted small">{ROLE_LABELS[me.role]}</div>
          <button className="btn ghost small" onClick={() => setChangingPassword(true)}>Сменить пароль</button>
          <button className="btn ghost small" onClick={signOut}>Выйти</button>
        </div>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/lessons/:id" element={<LessonPage />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:id" element={<GroupDetailPage />} />
          {staff && <Route path="/students" element={<StudentsPage />} />}
          {staff && <Route path="/teachers" element={<TeachersPage />} />}
          {staff && <Route path="/devices" element={<DevicesPage />} />}
          {staff && <Route path="/salary" element={<SalaryPage />} />}
          {staff && <Route path="/salary/:id" element={<SalaryPeriodPage />} />}
          {staff && <Route path="/reports" element={<ReportsPage />} />}
          {staff && <Route path="/rooms" element={<RoomsPage />} />}
          {me.role === 'TEACHER' && <Route path="/my-salary" element={<MySalaryPage />} />}
          {me.role === 'ADMIN' && <Route path="/users" element={<UsersPage />} />}
          {me.role === 'ADMIN' && <Route path="/audit" element={<AuditPage />} />}
          <Route path="*" element={<Navigate to="/schedule" replace />} />
        </Routes>
      </main>
      {changingPassword && <ChangePasswordModal onClose={() => setChangingPassword(false)} />}
    </div>
  );
}
