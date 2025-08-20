/****************************************
 * Navbar.jsx · kompakt mit Dropdowns & Icons (Aug 2025)
 ****************************************/
import React, { useState, useEffect, useContext, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LanguageContext, useTranslation } from '../context/LanguageContext';
import '../styles/Navbar.css';
import api from '../utils/api';
import ChangelogModal from './ChangelogModal';

/* --------- Kleine Inline-Icons (SVG) --------- */
const IconChevronDown = () => (
    <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
);
const IconBell = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.172V11a6 6 0 0 0-12 0v3.172a2 2 0 0 1-.6 1.428L4 17h5m6 0a3 3 0 0 1-6 0m6 0H9" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);
const IconSun = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" strokeWidth="2"/>
        <path d="M12 2v2m0 16v2m10-10h-2M4 12H2m15.536-7.536-1.414 1.414M7.879 16.121l-1.414 1.414m12.728 0-1.414-1.414M7.879 7.879 6.465 6.465" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
);
const IconMoon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" fill="none" stroke="currentColor" strokeWidth="2"/>
    </svg>
);
const IconUser = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M20 21a8 8 0 1 0-16 0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        <circle cx="12" cy="7" r="3" fill="none" stroke="currentColor" strokeWidth="2"/>
    </svg>
);

/* --------- Hilfs-Hook: Klicks außerhalb schließen --------- */
function useClickOutside(ref, onOutside) {
    useEffect(() => {
        function handle(e) {
            if (ref.current && !ref.current.contains(e.target)) onOutside?.();
        }
        document.addEventListener('mousedown', handle);
        document.addEventListener('touchstart', handle, { passive: true });
        return () => {
            document.removeEventListener('mousedown', handle);
            document.removeEventListener('touchstart', handle);
        };
    }, [ref, onOutside]);
}

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const { t } = useTranslation();
    const { language, setLanguage } = useContext(LanguageContext);
    const location = useLocation();

    // Öffentliche Seiten bestimmen (wie gehabt)
    let publicRoutes = ['/login', '/register'];
    if (!authToken) publicRoutes.push('/');
    const isPublicPage = publicRoutes.includes(location.pathname);

    // Theme (Light / Dark)
    const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }, [theme]);
    const toggleTheme = () => setTheme(prev => (prev === 'light' ? 'dark' : 'light'));

    // Changelog
    const [latestChangelog, setLatestChangelog] = useState(null);
    const [showChangelogModal, setShowChangelogModal] = useState(false);
    const [hasNewUpdate, setHasNewUpdate] = useState(false);
    useEffect(() => {
        const checkChangelog = async () => {
            if (!authToken) return;
            try {
                const response = await api.get('/api/changelog/latest');
                if (response.data) {
                    const latest = response.data;
                    setLatestChangelog(latest);
                    const seenVersion = localStorage.getItem('seenChangelogVersion');
                    if (latest.version !== seenVersion) setHasNewUpdate(true);
                }
            } catch (e) {
                console.error('Konnte Changelog nicht abrufen', e);
            }
        };
        checkChangelog();
    }, [authToken]);
    const openChangelog = () => {
        setShowChangelogModal(true);
        if (latestChangelog) {
            localStorage.setItem('seenChangelogVersion', latestChangelog.version);
            setHasNewUpdate(false);
        }
    };

    // Dropdown-States
    const [openAdmin, setOpenAdmin] = useState(false);
    const [openUser, setOpenUser] = useState(false);
    const [openLang, setOpenLang] = useState(false);

    const adminRef = useRef(null);
    const userRef = useRef(null);
    const langRef = useRef(null);

    useClickOutside(adminRef, () => setOpenAdmin(false));
    useClickOutside(userRef, () => setOpenUser(false));
    useClickOutside(langRef, () => setOpenLang(false));

    const isSuperAdmin = !!currentUser?.roles?.includes('ROLE_SUPERADMIN');
    const isAdmin = !!currentUser?.roles?.includes('ROLE_ADMIN');
    const isPercentage = !!currentUser?.isPercentage;

    const userInitial = currentUser?.username?.[0]?.toUpperCase() || 'U';

    return (
        <div className="scoped-navbar">
            <nav className="navbar" aria-label="Hauptnavigation">
                {/* Logo */}
                <div className="navbar-brand">
                    <Link to="/" className="navbar-logo">Chrono</Link>
                </div>

                {/* Hamburger Toggle (mobil) */}
                <input type="checkbox" id="nav-toggle" className="nav-toggle" />
                <label htmlFor="nav-toggle" className="nav-toggle-label" aria-label="Menü umschalten">
                    <span></span><span></span><span></span>
                </label>

                {/* Links */}
                <ul className="navbar-links">
                    {!authToken || isPublicPage ? (
                        <>
                            <li><Link to="/login">{t('navbar.login', 'Login')}</Link></li>
                            <li><Link to="/register">{t('navbar.register', 'Registrieren')}</Link></li>
                        </>
                    ) : (
                        <>
                            {currentUser && (
                                <>
                                    {isSuperAdmin && (
                                        <li><Link to="/admin/company">{t('navbar.companyManagement','Firmen')}</Link></li>
                                    )}

                                    {isAdmin ? (
                                        <li className={`dropdown ${openAdmin ? 'open' : ''}`} ref={adminRef}>
                                            <button
                                                className="dropdown-trigger"
                                                onClick={() => setOpenAdmin(v => !v)}
                                                aria-haspopup="true"
                                                aria-expanded={openAdmin}
                                            >
                                                {t('navbar.admin','Admin')} <IconChevronDown/>
                                            </button>
                                            <div className="dropdown-menu">
                                                <Link to="/admin/dashboard" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.adminStart','Admin-Start')}
                                                </Link>
                                                <Link to="/admin/users" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.userManagement','Benutzerverwaltung')}
                                                </Link>
                                                {currentUser.customerTrackingEnabled && (
                                                    <>
                                                        <Link to="/admin/customers" onClick={() => setOpenAdmin(false)}>
                                                            {t('navbar.customerManagement','Kunden')}
                                                        </Link>
                                                        <Link to="/admin/projects" onClick={() => setOpenAdmin(false)}>
                                                            {t('navbar.projectManagement','Projekte')}
                                                        </Link>
                                                        <Link to="/admin/tasks" onClick={() => setOpenAdmin(false)}>
                                                            {t('navbar.taskManagement','Aufgaben')}
                                                        </Link>
                                                    </>
                                                )}
                                                <Link to="/admin/payslips" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.payslips','Abrechnungen')}
                                                </Link>
                                                <Link to="/admin/schedule" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.schedulePlanner','Dienstplan')}
                                                </Link>
                                                <Link to="/admin/knowledge" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.knowledge','Firmen KI Wissen')}
                                                </Link>
                                                <Link to="/admin/company-settings" onClick={() => setOpenAdmin(false)}>
                                                    {t('navbar.companySettings','Firmeneinstellungen')}
                                                </Link>
                                            </div>
                                        </li>
                                    ) : (
                                        <>
                                            <li>
                                                <Link to={isPercentage ? "/percentage-punch" : "/dashboard"}>
                                                    {t('navbar.myDashboard','Mein Dashboard')}
                                                </Link>
                                            </li>
                                            <li><Link to="/payslips">{t('navbar.payslips','Abrechnungen')}</Link></li>
                                        </>
                                    )}
                                </>
                            )}

                            {/* Flex-Spacer schiebt die Controls nach rechts */}
                            <li className="flex-spacer" aria-hidden="true"></li>

                            {/* Changelog als Glocke */}
                            <li>
                                <button
                                    className="icon-btn"
                                    aria-label={t('navbar.whatsNew','Was ist neu?')}
                                    onClick={openChangelog}
                                >
                  <span className="icon-wrap">
                    <IconBell/>
                      {hasNewUpdate && <span className="notification-badge" aria-hidden="true"></span>}
                  </span>
                                </button>
                            </li>

                            {/* Theme Toggle */}
                            <li>
                                <button
                                    className="icon-btn"
                                    onClick={toggleTheme}
                                    aria-label={theme === 'light' ? t('darkMode','Dark Mode') : t('lightMode','Light Mode')}
                                    title={theme === 'light' ? t('darkMode','Dark Mode') : t('lightMode','Light Mode')}
                                >
                                    <span className="icon-wrap">{theme === 'light' ? <IconMoon/> : <IconSun/>}</span>
                                </button>
                            </li>

                            {/* Sprache kompakt */}
                            <li className={`dropdown ${openLang ? 'open' : ''}`} ref={langRef}>
                                <button
                                    className="dropdown-trigger iconish"
                                    onClick={() => setOpenLang(v => !v)}
                                    aria-haspopup="true"
                                    aria-expanded={openLang}
                                >
                                    🌐 <IconChevronDown/>
                                </button>
                                <div className="dropdown-menu">
                                    <button className={`lang-item ${language==='de'?'active':''}`} onClick={() => { setLanguage('de'); setOpenLang(false); }}>DE</button>
                                    <button className={`lang-item ${language==='en'?'active':''}`} onClick={() => { setLanguage('en'); setOpenLang(false); }}>EN</button>
                                </div>
                            </li>

                            {/* User-Menü */}
                            <li className={`dropdown ${openUser ? 'open' : ''}`} ref={userRef}>
                                <button
                                    className="dropdown-trigger user-trigger"
                                    onClick={() => setOpenUser(v => !v)}
                                    aria-haspopup="true"
                                    aria-expanded={openUser}
                                >
                                    <span className="avatar" aria-hidden="true">{userInitial}</span>
                                    <span className="username">{currentUser?.username}</span>
                                    <IconChevronDown/>
                                </button>
                                <div className="dropdown-menu">
                                    {/* Für Nicht-Admin bleibt "Mein Profil" hier; für Admin optional zusätzlich */}
                                    {!isAdmin && (
                                        <Link to="/personal-data" onClick={() => setOpenUser(false)}>
                                            {t('navbar.profile','Mein Profil')}
                                        </Link>
                                    )}
                                    <Link to="/whats-new" onClick={() => setOpenUser(false)}>
                                        {t('navbar.history','Update-Verlauf')}
                                    </Link>
                                    <Link to="/admin/change-password" onClick={() => setOpenUser(false)}>
                                        {t('admin.changePasswordTitle','Passwort ändern')}
                                    </Link>
                                    <button className="navbar-logout" onClick={() => { setOpenUser(false); logout(); }}>
                                        {t('navbar.logout','Logout')}
                                    </button>
                                </div>
                            </li>
                        </>
                    )}
                </ul>
            </nav>

            {showChangelogModal && (
                <ChangelogModal
                    changelog={latestChangelog}
                    onClose={() => setShowChangelogModal(false)}
                />
            )}
        </div>
    );
};

export default Navbar;
