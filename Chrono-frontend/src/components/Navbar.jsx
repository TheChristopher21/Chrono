/****************************************
 * Navbar.jsx · mit Hamburger-Menü
 ****************************************/
import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../context/LanguageContext';

// Importiere dein CSS:
import '../styles/Navbar.css';

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
        setTheme(prev => (prev === 'light' ? 'dark' : 'light'));
    };

    // Navbar UI
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
                    {/* Wenn nicht eingeloggt ODER public Page => Login+Register */}
                    {!authToken || isPublicPage ? (
                        <>
                            <li>
                                <Link to="/login">{t('navbar.login', 'Login')}</Link>
                            </li>
                            <li>
                                <Link to="/register">{t('navbar.register', 'Registrieren')}</Link>
                            </li>
                            <li>
                                <div className="brightness-control">
                                    <label htmlFor="brightness-slider">
                                        {t('navbar.brightness', 'Helligkeit')}
                                    </label>
                                    <input
                                        id="brightness-slider"
                                        type="range"
                                        min="0.5"
                                        max="1.5"
                                        step="0.01"
                                        value={brightness}
                                        onChange={(e) => setBrightness(Number(e.target.value))}
                                    />
                                    <span>{Math.round(brightness * 100)}%</span>
                                </div>
                            </li>
                            <li>
                                <button onClick={toggleTheme}>
                                    {theme === 'light'
                                        ? t('darkMode', 'Dark Mode')
                                        : t('lightMode', 'Light Mode')}
                                </button>
                            </li>
                        </>
                    ) : (
                        <>
                            {/* Andernfalls => private Links (Admin oder User) */}
                            {currentUser?.roles?.includes('ROLE_ADMIN') ? (
                                <>
                                    <li>
                                        <Link to="/admin">{t('navbar.adminStart', 'Admin‑Start')}</Link>
                                    </li>
                                    <li>
                                        <Link to="/admin/users">
                                            {t('navbar.userManagement', 'Benutzerverwaltung')}
                                        </Link>
                                    </li>
                                    <li>
                                        <Link to="/admin/change-password">
                                            {t('admin.changePasswordTitle', 'Passwort ändern')}
                                        </Link>
                                    </li>
                                </>
                            ) : (
                                <>
                                    <li>
                                        <Link to="/user">
                                            {t('navbar.myDashboard', 'Mein Dashboard')}
                                        </Link>
                                    </li>
                                    <li>
                                        <Link to="/profile">
                                            {t('navbar.profile', 'Mein Profil')}
                                        </Link>
                                    </li>
                                </>
                            )}

                            {/* Username + Logout */}
                            <li className="navbar-username">
                                {t('navbar.hi', 'Hi')}, {currentUser?.username}
                            </li>
                            <li>
                                <button className="navbar-logout" onClick={logout}>
                                    {t('navbar.logout', 'Logout')}
                                </button>
                            </li>
                            <li>
                                <div className="brightness-control">
                                    <label htmlFor="brightness-slider">
                                        {t('navbar.brightness', 'Helligkeit')}
                                    </label>
                                    <input
                                        id="brightness-slider"
                                        type="range"
                                        min="0.5"
                                        max="1.5"
                                        step="0.01"
                                        value={brightness}
                                        onChange={(e) => setBrightness(Number(e.target.value))}
                                    />
                                    <span>{Math.round(brightness * 100)}%</span>
                                </div>
                            </li>
                            <li>
                                <button onClick={toggleTheme}>
                                    {theme === 'light'
                                        ? t('darkMode', 'Dark Mode')
                                        : t('lightMode', 'Light Mode')}
                                </button>
                            </li>
                        </>
                    )}
                </ul>
            </nav>
        </div>
    );
};

export default Navbar;
