// src/pages/Login.jsx
import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import '../styles/Login.css';
import { useTranslation } from '../context/LanguageContext';
import { getDefaultLandingPage } from '../utils/pageAccess.js';

const Login = () => {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const { t } = useTranslation();

    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');

    const handleChange = (e) =>
        setForm({ ...form, [e.target.name]: e.target.value });

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        const res = await login(form.username.trim(), form.password);

        if (!res.success) {
            setError(res.message || t('login.error', 'Login fehlgeschlagen'));
            return;
        }

        const user = res.user || {};
        const next = new URLSearchParams(location.search).get('next');

        if (next) {
            navigate(next, { replace: true });
        } else {
            navigate(getDefaultLandingPage(user), { replace: true });
        }
    };

    return (
        <div className="login-page scoped-login">
            <Navbar />

            <main className="login-hero" aria-labelledby="login-title">
                <section className="login-auth-panel">
                    <Link className="login-panel-brand" to="/">Chrono</Link>

                    <div className="login-copy">
                        <h1 id="login-title" className="login-h1">
                            {t('login.title', 'Willkommen zurück!')}
                        </h1>
                        <p className="login-lead">
                            {t('login.intro', 'Melde dich an, um fortzufahren.')}
                        </p>
                    </div>

                    {error && <p className="error-message" role="alert">{error}</p>}

                    <form onSubmit={handleSubmit} className="login-form">
                        <label htmlFor="username">{t('login.username', 'Benutzername')}</label>
                        <input
                            id="username"
                            name="username"
                            autoComplete="username"
                            required
                            value={form.username}
                            onChange={handleChange}
                            placeholder={t('login.usernamePh', 'z. B. max.meier')}
                        />

                        <label htmlFor="password">{t('login.password', 'Passwort')}</label>
                        <input
                            type="password"
                            id="password"
                            name="password"
                            autoComplete="current-password"
                            required
                            value={form.password}
                            onChange={handleChange}
                            placeholder="********"
                        />

                        <button type="submit" className="login-btn login-primary">
                            {t('login.button', 'Login')}
                        </button>
                    </form>
                </section>
            </main>
        </div>
    );
};

export default Login;
