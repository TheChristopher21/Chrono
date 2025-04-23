/****************************************
 * Navbar.jsx  · scoped
 ****************************************/
import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../styles/Navbar.css';
import { useTranslation } from '../context/LanguageContext';

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const { t } = useTranslation();
    const location = useLocation();

    // Öffentliche Routen
    const publicRoutes = ['/login', '/register'];
    const isPublicPage = publicRoutes.includes(location.pathname);

    /* ----------------------------  App‑State  --------------------------- */
    const [brightness, setBrightness] = useState(1);
    useEffect(() => {
        const saved = localStorage.getItem('appBrightness');
        if (saved) setBrightness(Number(saved));
    }, []);
    useEffect(() => {
        document.documentElement.style.setProperty('--app-brightness', brightness);
        localStorage.setItem('appBrightness', brightness);
    }, [brightness]);

    const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }, [theme]);
    const toggleTheme = () => setTheme(prev => (prev === 'light' ? 'dark' : 'light'));

    /* ----------------------------  Render  ------------------------------ */
    const NavContent = ({ children }) => (
        <nav className="navbar">
            {children}
        </nav>
    );

    return (
        <div className="scoped-navbar">
            {(!authToken || isPublicPage) ? (
                <NavContent>
                    <div className="navbar-brand">
                        <Link to="/" className="navbar-logo">Chrono</Link>
                    </div>
                    <ul className="navbar-links">
                        <li><Link to="/login">{t('navbar.login', 'Login')}</Link></li>
                        <li><Link to="/register">{t('navbar.register', 'Register')}</Link></li>
                        <li>
                            <div className="brightness-control">
                                <label htmlFor="brightness-slider">{t('Helligkeit', 'Helligkeit')}</label>
                                <input
                                    id="brightness-slider"
                                    type="range" min="0.5" max="1.5" step="0.01"
                                    value={brightness}
                                    onChange={e => setBrightness(Number(e.target.value))}
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
                    </ul>
                </NavContent>
            ) : (
                <NavContent>
                    <div className="navbar-brand">
                        <Link to="/" className="navbar-logo">Chrono</Link>
                    </div>
                    <ul className="navbar-links">
                        {currentUser?.roles?.includes('ROLE_ADMIN') ? (
                            <>
                                <li><Link to="/admin">{t('navbar.adminStart', 'Admin‑Start')}</Link></li>
                                <li><Link to="/admin/users">{t('navbar.userManagement', 'Benutzerverwaltung')}</Link></li>
                                <li><Link to="/admin/change-password">
                                    {t('admin.changePasswordTitle', 'Passwort ändern')}
                                </Link></li>
                            </>
                        ) : (
                            <>
                                <li><Link to="/user">{t('navbar.myDashboard', 'Mein Dashboard')}</Link></li>
                                <li><Link to="/profile">{t('navbar.profile', 'Mein Profil')}</Link></li>
                            </>
                        )}
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
                                <label htmlFor="brightness-slider">{t('navbar.brightness', 'Helligkeit')}</label>
                                <input
                                    id="brightness-slider"
                                    type="range" min="0.5" max="1.5" step="0.01"
                                    value={brightness}
                                    onChange={e => setBrightness(Number(e.target.value))}
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
                    </ul>
                </NavContent>
            )}
        </div>
    );
};

export default Navbar;
