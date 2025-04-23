// src/pages/Login.jsx
import React, { useState, useEffect, useContext, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import '../styles/Login.css';
import api from "../utils/api.js";
import { LanguageContext, useTranslation } from '../context/LanguageContext';
import { Howl } from 'howler';
import stampMp3 from '/sounds/stamp.mp3';

const stampSound = new Howl({
    src: [stampMp3],
    volume: 0.5
});

function parseHex16(hexString) {
    if (!hexString) return null;
    const clean = hexString.replace(/\s+/g, '');
    if (clean.length !== 32) return null;
    let output = '';
    for (let i = 0; i < 16; i++) {
        const byteHex = clean.slice(i * 2, i * 2 + 2);
        const val = parseInt(byteHex, 16);
        if (val !== 0) {
            output += String.fromCharCode(val);
        }
    }
    return output;
}

const Login = () => {
    const navigate = useNavigate();
    const { login } = useAuth();
    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [punchMessage, setPunchMessage] = useState('');
    const { language, setLanguage } = useContext(LanguageContext);
    const { t } = useTranslation();
    const lastPunchRef = useRef(0);

    useEffect(() => {
        const interval = setInterval(() => {
            doNfcCheck();
        }, 1000);
        return () => clearInterval(interval);
    }, []);

    async function doNfcCheck() {
        try {
            const response = await fetch(import.meta.env.VITE_APIURL + '/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card') return;
            if (json.status === 'error') {
                console.error("NFC read error:", json.message);
                return;
            }
            if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    const now = Date.now();
                    if (now - lastPunchRef.current < 60000) {
                        setPunchMessage("Bitte 1 Minute warten, bevor erneut gestempelt wird.");
                        setTimeout(() => setPunchMessage(''), 3000);
                        return;
                    }
                    lastPunchRef.current = now;
                    showPunchMessage(`Eingestempelt: ${cardUser}`);
                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser },
                        });
                        console.log("Punch executed for", cardUser);
                    } catch (err) {
                        console.error("Punch error:", err);
                    }
                }
            }
        } catch (err) {
            console.error("NFC fetch error:", err);
        }
    }

    function showPunchMessage(msg) {
        setPunchMessage(msg);
        stampSound.play();
        setTimeout(() => setPunchMessage(''), 3000);
    }

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        const trimmedUsername = form.username.trim();
        const res = await login(trimmedUsername, form.password);
        if (res.success) {
            if (res.user.roles && res.user.roles.includes('ROLE_ADMIN')) {
                navigate('/admin', { replace: true });
            } else if (res.user.isPercentage) {
                navigate('/percentage-punch', { replace: true });
            } else {
                navigate('/user', { replace: true });
            }
        } else {
            setError('Login fehlgeschlagen. Bitte Zugangsdaten prüfen.');
        }
    };


    return (
        <>
            <Navbar />
            <div className="login-page-2col">
                <div className="login-left">
                    <div className="login-left-content">
                        <h1>{t("login.title", "Willkommen zurück!")}</h1>
                        <p>{t("Melde dich an, um fortzufahren.")}</p>
                        {error && <p className="error-message">{error}</p>}
                        {punchMessage && <div className="punch-message">{punchMessage}</div>}
                        <div className="language-switch">
                            <label>{t("login.languageLabel", "Sprache")}</label>
                            <select
                                value={language}
                                onChange={(e) => setLanguage(e.target.value)}
                            >
                                <option value="de">DE</option>
                                <option value="en">EN</option>
                            </select>
                        </div>
                        <form onSubmit={handleSubmit}>
                            <label htmlFor="username">{t("login.username", "Benutzername")}</label>
                            <input
                                type="text"
                                id="username"
                                name="username"
                                value={form.username}
                                onChange={handleChange}
                                placeholder={t("login.username", "Benutzername")}
                                required
                            />
                            <label htmlFor="password">{t("login.password", "Passwort")}</label>
                            <input
                                type="password"
                                id="password"
                                name="password"
                                value={form.password}
                                onChange={handleChange}
                                placeholder={t("login.password", "Passwort")}
                                required
                            />
                            <button type="submit">
                                {t("login.button", "Login")}
                            </button>
                        </form>
                        <div className="register-cta">
                            <span>{t("No account", "Noch kein Account?")}</span>
                            <a href="/Registration.jsx">
                                {t("Register here", "Hier registrieren")}
                            </a>
                        </div>
                    </div>
                </div>
                <div className="login-right">
                    <div className="colorful-area"></div>
                </div>
            </div>
        </>
    );
};

export default Login;
