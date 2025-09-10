// src/pages/Login.jsx
import { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import '../styles/Login.css';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import { Howl } from 'howler';
import stampMp3 from '/sounds/stamp.mp3';

const stampSound = new Howl({ src: [stampMp3], volume: 0.5 });

/* Hex-Helper */
const parseHex16 = (hex) => {
    if (!hex) return null;
    const c = hex.replace(/\s+/g, '');
    if (c.length !== 32) return null;
    let out = '';
    for (let i = 0; i < 16; i++) {
        const val = parseInt(c.slice(i * 2, i * 2 + 2), 16);
        if (val) out += String.fromCharCode(val);
    }
    return out;
};

const Login = () => {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const { t } = useTranslation();

    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [punchMsg, setPunchMsg] = useState('');
    const lastPunchRef = useRef(0);

    /* ---------- NFC Poll ----------------------------------------- */
    useEffect(() => {
        const id = setInterval(checkNfc, 1000);
        return () => clearInterval(id);
    }, []);

    const checkNfc = async () => {
        try {
            const res = await fetch(`${import.meta.env.VITE_APIURL}/api/nfc/read/1`);
            if (!res.ok) return;
            const json = await res.json();
            if (json.status !== 'success') return;

            const cardUser = parseHex16(json.data);
            if (!cardUser) return;

            if (Date.now() - lastPunchRef.current < 60_000) {
                setPunchMsg(t('login.waitMessage', 'Bitte 1 Minute warten…'));
                setTimeout(() => setPunchMsg(''), 3000);
                return;
            }
            lastPunchRef.current = Date.now();
            showPunch(`${t('login.stamped', 'Eingestempelt')}: ${cardUser}`);

            await api.post('/api/timetracking/punch', null, {
                params: { username: cardUser },
            });
        } catch (e) {
            console.error('NFC error', e);
        }
    };

    const showPunch = (txt) => {
        setPunchMsg(txt);
        stampSound.play();
        setTimeout(() => setPunchMsg(''), 3000);
    };

    /* ---------- Form-Handling ------------------------------------ */
    const handleChange = (e) =>
        setForm({ ...form, [e.target.name]: e.target.value });

    const handleSubmit = async (e) => {
        e.preventDefault();
        const res = await login(form.username.trim(), form.password);

        if (!res.success) {
            setError(res.message || t('login.error', 'Login fehlgeschlagen'));
            return;
        }

        const user = res.user || {};
        const roles = user.roles || [];
        const isPercentageUser = user.isPercentage || false;

        const next = new URLSearchParams(location.search).get('next');

        if (next) {
            navigate(next, { replace: true });
        } else if (roles.includes('ROLE_SUPERADMIN')) {
            navigate('/admin/company', { replace: true });
        } else if (roles.includes('ROLE_ADMIN')) {
            navigate('/admin/dashboard', { replace: true });
        } else if (isPercentageUser) {
            navigate('/percentage-punch', { replace: true });
        } else {
            navigate('/dashboard', { replace: true });
        }
    };

    /* ---------- UI ------------------------------------------------ */
    return (
        <div className="login-page scoped-login">
            <Navbar />

            {/* Hintergrund-Ornamente passend zur Landing */}
            <div className="login-bg-orbs" aria-hidden="true" />

            {/* Zwei-Spalten Layout */}
            <main className="login-page-2col">
                {/* Linke Spalte: Glas-Karte mit Formular */}
                <section className="login-left" aria-labelledby="login-title">
                    <div className="login-left-content">
                        <h1 id="login-title" className="login-h1">
                            {t('login.title', 'Willkommen zurück!')}
                        </h1>
                        <p className="login-lead">
                            {t('login.intro', 'Melde dich an, um fortzufahren.')}
                        </p>

                        {error && <p className="error-message">{error}</p>}
                        {punchMsg && <div className="punch-message">{punchMsg}</div>}

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
                                placeholder="••••••••"
                            />

                            <button type="submit" className="login-btn login-primary">
                                {t('login.button', 'Anmelden')}
                            </button>
                        </form>

                        <div className="register-cta">
                            <span>{t('login.noAccount', 'Noch kein Account?')}</span>
                            <Link to="/register">{t('login.registerHere', 'Jetzt registrieren')}</Link>
                        </div>
                    </div>
                </section>

                {/* Rechte Spalte: Branding-Panel */}
                <aside className="login-right" aria-label="Markenbereich">
                    <div className="colorful-area">
                        <ul className="login-usp-chips" role="list">
                        </ul>
                    </div>
                </aside>
            </main>

            {/* Footer wie Landing */}
            <footer className="login-footer">
                <div className="login-foot-inner">
                    <span>© {new Date().getFullYear()} Chrono</span>
                    <nav className="login-foot-links" aria-label="Footer Navigation">
                        <Link to="/impressum">{t('impressum', 'Impressum')}</Link>
                        <Link to="/datenschutz">{t('datenschutz', 'Datenschutz')}</Link>
                        <Link to="/agb">{t('agb', 'AGB')}</Link>
                        <a
                            href="https://www.instagram.com/itschronologisch"
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            Instagram
                        </a>
                    </nav>
                </div>
            </footer>
        </div>
    );
};

export default Login;
