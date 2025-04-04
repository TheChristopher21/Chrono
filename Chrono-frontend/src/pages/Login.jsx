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

// Sound für Punch
const stampSound = new Howl({
    src: [stampMp3],
    volume: 0.5
});

// Liest aus 32-stelligem Hex den Usernamen
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

    // Hier speichern wir den Zeitpunkt (ms seit 1970) des letzten Punch
    const lastPunchRef = useRef(0);

    // NFC-Check läuft im Intervall
    useEffect(() => {
        const interval = setInterval(() => {
            doNfcCheck();
        }, 1000);
        return () => clearInterval(interval);
    }, []);

    // NFC-Lesen => Punch
    async function doNfcCheck() {
        try {
            const response = await fetch('http://localhost:8080/api/nfc/read/1');
            if (!response.ok) return;
            const json = await response.json();
            if (json.status === 'no-card') return;
            if (json.status === 'error') {
                console.error("NFC read => error:", json.message);
                return;
            }
            if (json.status === 'success') {
                const cardUser = parseHex16(json.data);
                if (cardUser) {
                    // check: war der letzte Punch < 60s her?
                    const now = Date.now();
                    if (now - lastPunchRef.current < 60_000) {
                        // < 1 min => brich ab und sag: "Bitte kurz warten"
                        setPunchMessage(`Bitte 1 Minute warten, bevor erneut gestempelt wird.`);
                        setTimeout(() => setPunchMessage(''), 3000);
                        return;
                    }
                    // sonst normal punch
                    lastPunchRef.current = now;
                    showPunchMessage(`Eingestempelt: ${cardUser}`);
                    try {
                        await api.post('/api/timetracking/punch', null, {
                            params: { username: cardUser },
                        });
                        console.log("Punch executed for", cardUser);
                    } catch (err) {
                        console.error("Punch-Fehler:", err);
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

    useEffect(() => {
        if (process.env.NODE_ENV === 'test') {
            const testUsername = 'testuser';
            const testPassword = 'testpass';
            login(testUsername, testPassword).then((res) => {
                if (res.success) {
                    navigate('/user');
                } else {
                    setError('Test-Login fehlgeschlagen');
                }
            });
        }
    }, [login, navigate]);

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        const trimmedUsername = form.username.trim();
        const res = await login(trimmedUsername, form.password);
        if (res.success) {
            if (res.user.roles && res.user.roles.includes('ROLE_ADMIN')) {
                navigate('/admin');
            } else {
                navigate('/user');
            }
        } else {
            setError('Login fehlgeschlagen. Bitte Zugangsdaten prüfen.');
        }
    };

    return (
        <>
            <Navbar />
            <div className="login-container card">
                <h2>{t("login.title")}</h2>
                {error && <p className="error-message">{error}</p>}
                {punchMessage && (
                    <div className="punch-message">{punchMessage}</div>
                )}
                <div className="language-switch">
                    <label>{t("login.languageLabel")}</label>
                    <select value={language} onChange={(e) => setLanguage(e.target.value)}>
                        <option value="de">DE</option>
                        <option value="en">EN</option>
                    </select>
                </div>
                <form onSubmit={handleSubmit}>
                    <input
                        type="text"
                        name="username"
                        value={form.username}
                        onChange={handleChange}
                        placeholder={t("login.username")}
                        required
                    />
                    <input
                        type="password"
                        name="password"
                        value={form.password}
                        onChange={handleChange}
                        placeholder={t("login.password")}
                        required
                    />
                    <button type="submit">{t("login.button")}</button>
                </form>

                <hr />
            </div>
        </>
    );
};

export default Login;
