import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getToken, setToken, setUnauthorizedHandler } from '../api/client';
import * as endpoints from '../api/endpoints';

export interface SessionUser {
  email: string;
  name: string;
  isAdmin: boolean;
}

interface AuthState {
  user: SessionUser | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Reads the JWT payload for display only (name, role, expiry). The server verifies the signature on
 * every request and decides access; nothing here is trusted for security.
 */
function userFromToken(token: string | null): SessionUser | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const binary = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    const json = JSON.parse(new TextDecoder().decode(bytes)); // UTF-8 safe for non-ASCII names
    if (typeof json.exp === 'number' && json.exp * 1000 < Date.now()) return null;
    return {
      email: json.sub,
      name: json.name || json.sub,
      isAdmin: json.role === 'ROLE_ADMIN',
    };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => {
    const u = userFromToken(getToken());
    if (!u) setToken(null);
    return u;
  });

  const signOut = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => setUnauthorizedHandler(signOut), [signOut]);

  const signIn = useCallback(async (email: string, password: string) => {
    const { token } = await endpoints.login(email, password);
    setToken(token);
    setUser(userFromToken(token));
  }, []);

  const value = useMemo(() => ({ user, signIn, signOut }), [user, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
