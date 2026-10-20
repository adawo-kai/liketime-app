import { Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import { ROLE_LABELS, type Role } from './api';
import LoginPage from './pages/LoginPage';
import GroupsPage from './pages/GroupsPage';
import GroupDetailPage from './pages/GroupDetailPage';
import StudentsPage from './pages/StudentsPage';
import TeachersPage from './pages/TeachersPage';
import UsersPage from './pages/UsersPage';

interface NavItem { to: string; label: string; roles: Role[] }

const NAV: NavItem[] = [
  { to: '/groups', label: 'Группы', roles: ['ADMIN', 'MANAGER'] },
  { to: '/groups', label: 'Мои группы', roles: ['TEACHER'] },
  { to: '/students', label: 'Ученики', roles: ['ADMIN', 'MANAGER'] },
  { to: '/teachers', label: 'Преподаватели', roles: ['ADMIN', 'MANAGER'] },
  { to: '/users', label: 'Пользователи', roles: ['ADMIN'] },
];

export default function App() {
  const { me, loading, signOut } = useAuth();

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
          <button className="btn ghost small" onClick={signOut}>Выйти</button>
        </div>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:id" element={<GroupDetailPage />} />
          {staff && <Route path="/students" element={<StudentsPage />} />}
          {staff && <Route path="/teachers" element={<TeachersPage />} />}
          {me.role === 'ADMIN' && <Route path="/users" element={<UsersPage />} />}
          <Route path="*" element={<Navigate to="/groups" replace />} />
        </Routes>
      </main>
    </div>
  );
}
