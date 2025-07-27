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
    const { login} = useAuth();
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

        // Stellen Sie sicher, dass res.user vorhanden ist und die Eigenschaften enthält.
        // Die Logik im AuthContext sollte dies bereits sicherstellen.
        const user = res.user || {}; // Fallback auf leeres Objekt, falls res.user undefiniert ist
        const roles = user.roles || [];
        const isPercentageUser = user.isPercentage || false;
        const isHourlyUser = user.isHourly || false; // NEU: Direkte Prüfung

        /* optional redirect */
        const next = new URLSearchParams(location.search).get('next');

        if (next) {
            navigate(next, { replace: true });
        } else if (roles.includes('ROLE_SUPERADMIN')) {
            navigate('/superadmin/companies', { replace: true });
        } else if (roles.includes('ROLE_ADMIN')) {
            navigate('/admin', { replace: true });
        } else if (isPercentageUser) {
            navigate('/percentage-punch', { replace: true });
        } else if (isHourlyUser) { // NEU: Explizite Weiterleitung für Stundenlöhner
            navigate('/user', { replace: true }); // Das UserDashboard rendert dann das HourlyDashboard
        } else { // Standard-Benutzer
            navigate('/user', { replace: true });
        }
    };

    /* ---------- UI ------------------------------------------------ */
    return (
        <div className="scoped-login"> {/* <-- Umfassender Wrapper, Flex-Kolumne */}
            <Navbar />

            {/* Hauptbereich (Form + Hintergrundbild) */}
            <div className="login-page-2col">
                <div className="login-left">
                    <div className="login-left-content">
                        <h1>{t('login.title', 'Willkommen zurück!')}</h1>
                        <p>{t('login.intro', 'Melde dich an, um fortzufahren.')}</p>

                        {error && <p className="error-message">{error}</p>}
                        {punchMsg && <div className="punch-message">{punchMsg}</div>}



                        <form onSubmit={handleSubmit}>
                            <label htmlFor="username">
                                {t('login.username', 'Benutzername')}
                            </label>
                            <input
                                id="username"
                                name="username"
                                required
                                value={form.username}
                                onChange={handleChange}
                            />

                            <label htmlFor="password">
                                {t('login.password', 'Passwort')}
                            </label>
                            <input
                                type="password"
                                id="password"
                                name="password"
                                required
                                value={form.password}
                                onChange={handleChange}
                            />

                            <button type="submit">{t('login.button', 'Login')}</button>
                        </form>

                        <div className="register-cta">
                            <span>{t('login.noAccount', 'Noch kein Account?')}</span>
                            <Link to="/register">{t('login.registerHere')}</Link>
                        </div>
                    </div>
                </div>

                <div className="login-right">
                    <div className="colorful-area" />
                </div>
            </div> {/* Ende login-page-2col */}

            {/* FOOTER-Bereich am Seitenende */}
            <div className="impressum-agb-footer">
                <Link to="/impressum">{t("impressum")}</Link>
                <Link to="/agb">{t("agb")}</Link>
                <a href="https://www.instagram.com/itschronologisch" target="_blank" rel="noopener noreferrer">{t("instagram", "Instagram")}</a>
            </div>
        </div>
    );
};

export default Login;