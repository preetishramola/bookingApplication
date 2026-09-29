import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import * as endpoints from '../api/endpoints';
import { ApiError, errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Notice } from '../components/Notice';

function useNext() {
  const [params] = useSearchParams();
  const next = params.get('next');
  // Only same-site paths, never an external URL
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const next = useNext();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(email.trim(), password);
      navigate(next, { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiError && (err.status === 401 || err.status === 403)
          ? 'That email and password don’t match.'
          : errorMessage(err),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="wrap">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Sign in</h1>
        {error && <Notice kind="error">{error}</Notice>}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            className="input"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <button type="submit" className="btn btn-dark btn-block" disabled={busy} style={{ minHeight: 50 }}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p>
          New here? <Link to={`/register${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`}>Create an account</Link>
        </p>
        <div className="demo-box">
          Demo account: <strong>alice@example.com</strong> / <strong>password123</strong>
        </div>
      </form>
    </div>
  );
}

export function RegisterPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const next = useNext();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await endpoints.register(name.trim(), email.trim(), password);
      await signIn(email.trim(), password);
      navigate(next, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setFieldErrors(err.fieldErrors);
      else setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="wrap">
      <form className="auth-card" onSubmit={onSubmit} noValidate>
        <h1>Create an account</h1>
        {error && <Notice kind="error">{error}</Notice>}
        <div className="field">
          <label htmlFor="name">Full name</label>
          <input id="name" className="input" autoComplete="name" required value={name} onChange={(e) => setName(e.target.value)} />
          {fieldErrors.name && <span className="field-error">{fieldErrors.name}</span>}
        </div>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            className="input"
            type="password"
            autoComplete="new-password"
            minLength={6}
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {fieldErrors.password ? (
            <span className="field-error">{fieldErrors.password}</span>
          ) : (
            <span className="hint">At least 6 characters.</span>
          )}
        </div>
        <button type="submit" className="btn btn-dark btn-block" disabled={busy} style={{ minHeight: 50 }}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p>
          Already have an account? <Link to={`/login${next !== '/' ? `?next=${encodeURIComponent(next)}` : ''}`}>Sign in</Link>
        </p>
      </form>
    </div>
  );
}
