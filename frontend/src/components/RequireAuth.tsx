import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Notice } from './Notice';

export default function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  if (admin && !user.isAdmin) {
    return (
      <div className="wrap" style={{ paddingTop: 48 }}>
        <Notice kind="error">This page is for admins only.</Notice>
      </div>
    );
  }
  return <>{children}</>;
}
