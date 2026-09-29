import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import AdminPage from './pages/AdminPage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import HotelPage from './pages/HotelPage';
import MyBookingsPage from './pages/MyBookingsPage';
import SearchPage from './pages/SearchPage';
import '@fontsource/schibsted-grotesk/400.css';
import '@fontsource/schibsted-grotesk/500.css';
import '@fontsource/schibsted-grotesk/600.css';
import '@fontsource/schibsted-grotesk/700.css';
import '@fontsource/schibsted-grotesk/800.css';
import './styles.css';

function NotFound() {
  return (
    <div className="wrap">
      <h1 className="page-title">Page not found</h1>
      <p>
        <Link to="/">Back to all stays</Link>
      </p>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<SearchPage />} />
            <Route path="hotels/:id" element={<HotelPage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
            <Route
              path="bookings"
              element={
                <RequireAuth>
                  <MyBookingsPage />
                </RequireAuth>
              }
            />
            <Route
              path="admin"
              element={
                <RequireAuth admin>
                  <AdminPage />
                </RequireAuth>
              }
            />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
