import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app">
      <a href="#main" className="visually-hidden">
        Skip to content
      </a>
      <header className="site-header">
        <div className="wrap">
          <Link to="/" className="brand" aria-label="stayline home">
            <span className="brand-mark" aria-hidden="true">
              S
            </span>
            stayline
          </Link>
          <nav className="site-nav" aria-label="Main">
            <NavLink to="/" end>
              Hotels
            </NavLink>
            {user && <NavLink to="/bookings">My bookings</NavLink>}
            {user?.isAdmin && <NavLink to="/admin">Admin</NavLink>}
            {user ? (
              <>
                <span className="who">{user.name}</span>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    signOut();
                    navigate('/');
                  }}
                >
                  Sign out
                </button>
              </>
            ) : (
              <Link to="/login" className="btn btn-accent" style={{ minHeight: 38, padding: '8px 18px' }}>
                Sign in
              </Link>
            )}
          </nav>
        </div>
      </header>

      <main id="main" className="page">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="wrap">
          <span>stayline</span>
          <span>Spring Boot · PostGIS · pgvector · Amazon Bedrock</span>
        </div>
      </footer>
    </div>
  );
}
