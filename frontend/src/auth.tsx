import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, getToken, login as apiLogin, setToken, setUnauthorizedHandler, type Me } from './api';

interface AuthState {
  me: Me | null;
  loading: boolean;
  signIn: (login: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState<boolean>(getToken() !== null);

  const signOut = useCallback(() => {
    setToken(null);
    setMe(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    if (getToken() === null) return;
    api.get<Me>('/api/auth/me')
      .then(setMe)
      .catch(() => setToken(null))
      .finally(() => setLoading(false));
  }, [signOut]);

  const signIn = useCallback(async (loginName: string, password: string) => {
    setMe(await apiLogin(loginName, password));
  }, []);

  return <AuthContext.Provider value={{ me, loading, signIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth вне AuthProvider');
  return ctx;
}
