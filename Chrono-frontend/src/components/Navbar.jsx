/****************************************
 * Navbar.jsx · mit Hamburger-Menü und Changelog
 ****************************************/
import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../context/LanguageContext';

// Importiere dein CSS:
import '../styles/Navbar.css';

// NEU START: Import für die neuen Komponenten und API
import api from '../utils/api';
import ChangelogModal from './ChangelogModal'; // Stellen Sie sicher, dass dieser Pfad korrekt ist
// NEU ENDE

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const { t } = useTranslation();
    const location = useLocation();

    // Bestimme, ob es sich um eine "öffentliche" Seite handelt
    let publicRoutes = ['/login', '/register'];
    if (!authToken) {
        publicRoutes.push('/');
    }
    const isPublicPage = publicRoutes.includes(location.pathname);

    // Helligkeits-Logik
    const [brightness, setBrightness] = useState(1);
    useEffect(() => {
        const saved = localStorage.getItem('appBrightness');
        if (saved) setBrightness(Number(saved));
    }, []);
    useEffect(() => {
        document.documentElement.style.setProperty('--app-brightness', brightness);
        localStorage.setItem('appBrightness', brightness);
    }, [brightness]);

    // Theme-Logik (Light / Dark)
    const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }, [theme]);
    const toggleTheme = () => {
        setTheme((prevTheme) => (prevTheme === 'light' ? 'dark' : 'light'));
    };

    // NEU START: Logik für den Changelog
    const [latestChangelog, setLatestChangelog] = useState(null);
    const [showChangelogModal, setShowChangelogModal] = useState(false);
    const [hasNewUpdate, setHasNewUpdate] = useState(false);

    useEffect(() => {
        const checkChangelog = async () => {
            if (!authToken) return; // Nur prüfen, wenn eingeloggt
            try {
                const response = await api.get('/api/changelog/latest');
                if (response.data) {
                    const latest = response.data;
                    setLatestChangelog(latest);
                    const seenVersion = localStorage.getItem('seenChangelogVersion');
                    if (latest.version !== seenVersion) {
                        setHasNewUpdate(true);
                    }
                }
            } catch (error) {
                console.error('Konnte Changelog nicht abrufen', error);
            }
        };
        checkChangelog();
    }, [authToken]);

    const handleChangelogClick = (e) => {
        e.preventDefault();
        setShowChangelogModal(true);
        if (latestChangelog) {
            localStorage.setItem('seenChangelogVersion', latestChangelog.version);
            setHasNewUpdate(false);
        }
    };
    // NEU ENDE

    return (
        <div className="scoped-navbar">
            <nav className="navbar">
                {/* Logo */}
                <div className="navbar-brand">
                    <Link to="/" className="navbar-logo">
                        Chrono
                    </Link>
                </div>

                {/* Hamburger Toggle */}
                <input type="checkbox" id="nav-toggle" className="nav-toggle" />
                <label htmlFor="nav-toggle" className="nav-toggle-label">
                    <span></span>
                    <span></span>
                    <span></span>
                </label>

                <ul className="navbar-links">
                    {!authToken || isPublicPage ? (
                        <>
                            {/* ... Ihre öffentlichen Links (Login, Register) bleiben unverändert ... */}
                            <li><Link to="/login">{t('navbar.login', 'Login')}</Link></li>
                            <li><Link to="/register">{t('navbar.register', 'Registrieren')}</Link></li>
                        </>
                    ) : (
                        <>
                            {/* Rollenspezifische Links */}
                            {currentUser?.roles?.includes('ROLE_SUPERADMIN') ? (
                                <li><Link to="/superadmin/companies">{t('navbar.companyManagement', 'Firmen')}</Link></li>
                            ) : currentUser?.roles?.includes('ROLE_ADMIN') ? (
                                <>
                                    <li><Link to="/admin">{t('navbar.adminStart', 'Admin‑Start')}</Link></li>
                                    <li><Link to="/admin/users">{t('navbar.userManagement', 'Benutzerverwaltung')}</Link></li>
                                    <li><Link to="/admin/customers">{t('navbar.customerManagement', 'Kunden')}</Link></li>
                                    <li><Link to="/admin/change-password">{t('admin.changePasswordTitle', 'Passwort ändern')}</Link></li>
                                </>
                            ) : (
                                <>
                                    <li><Link to="/user">{t('navbar.myDashboard', 'Mein Dashboard')}</Link></li>
                                    <li><Link to="/profile">{t('navbar.profile', 'Mein Profil')}</Link></li>
                                </>
                            )}

                            {/* NEU START: Changelog Links für eingeloggte User */}
                            <li>
                                <a href="#" onClick={handleChangelogClick} className="changelog-link">
                                    {t('navbar.whatsNew', 'Was ist neu?')}
                                    {hasNewUpdate && <span className="notification-badge"></span>}
                                </a>
                            </li>
                            <li>
                                <Link to="/whats-new">{t('navbar.history', 'Update-Verlauf')}</Link>
                            </li>
                            {/* NEU ENDE */}

                            {/* Username + Logout */}
                            <li className="navbar-username">
                                {t('navbar.hi', 'Hi')}, {currentUser?.username}
                            </li>
                            <li>
                                <button className="navbar-logout" onClick={logout}>
                                    {t('navbar.logout', 'Logout')}
                                </button>
                            </li>
                        </>
                    )}

                    {/* Helligkeit & Theme Toggle - für alle sichtbar */}
                    <li>
                        <div className="brightness-control">
                            <label htmlFor="brightness-slider">{t('navbar.brightness', 'Helligkeit')}</label>
                            <input
                                id="brightness-slider"
                                type="range" min="0.5" max="1.5" step="0.01"
                                value={brightness}
                                onChange={(e) => setBrightness(Number(e.target.value))}
                            />
                            <span>{Math.round(brightness * 100)}%</span>
                        </div>
                    </li>
                    <li>
                        <button onClick={toggleTheme}>
                            {theme === 'light' ? t('darkMode', 'Dark Mode') : t('lightMode', 'Light Mode')}
                        </button>
                    </li>
                </ul>
            </nav>

            {/* NEU START: Das Modal wird hier gerendert, wenn es aktiv ist */}
            {showChangelogModal && (
                <ChangelogModal
                    changelog={latestChangelog}
                    onClose={() => setShowChangelogModal(false)}
                />
            )}
            {/* NEU ENDE */}
        </div>
    );
};

export default Navbar;