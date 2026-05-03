/****************************************
 * Navbar.jsx · kompakt mit Dropdowns & Icons (Aug 2025)
 ****************************************/
import React, { useState, useEffect, useContext, useRef, useMemo } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LanguageContext, useTranslation } from '../context/LanguageContext';
import styles from '../styles/Navbar.module.css';
import api from '../utils/api';
import { getUserDisplayName } from '../utils/userDisplay';
import {
    getDefaultLandingPage,
    getRouteForPage,
    hasPageAccess,
    isAdminUser,
    isSuperAdminUser,
} from '../utils/pageAccess.js';
import ChangelogModal from './ChangelogModal';

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

const platformItemCodes = {
    adminUsers: 'PE',
    adminSchedule: 'DI',
    adminPayslips: 'LO',
    adminAccounting: 'FI',
    banking: 'ZA',
    adminProjects: 'PR',
    crm: 'CM',
    supplyChain: 'SC',
    adminKnowledge: 'KI',
    chronoTwo: 'C2',
    companySettings: 'AD',
    companyManagement: 'FM',
};

const getPlatformItemCode = (item) => {
    if (platformItemCodes[item.key]) return platformItemCodes[item.key];
    return item.label.slice(0, 2).toUpperCase();
};

function useClickOutside(ref, onOutside) {
    useEffect(() => {
        function handle(event) {
            if (ref.current && !ref.current.contains(event.target)) onOutside?.();
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
    const navigate = useNavigate();

    const isSuperAdmin = isSuperAdminUser(currentUser);
    const isAdmin = isAdminUser(currentUser);

    const publicRoutes = !authToken ? ['/', '/login', '/register'] : ['/login', '/register'];
    const isPublicPage = publicRoutes.includes(location.pathname);

    const [theme, setTheme] = useState(() => localStorage.getItem('theme') || 'light');
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }, [theme]);
    const toggleTheme = () => setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));

    const [latestChangelog, setLatestChangelog] = useState(null);
    const [showChangelogModal, setShowChangelogModal] = useState(false);
    const [hasNewUpdate, setHasNewUpdate] = useState(false);

    useEffect(() => {
        const checkChangelog = async () => {
            if (!authToken) return;
            try {
                const response = await api.get('/api/changelog/latest');
                if (response.data) {
                    setLatestChangelog(response.data);
                    const seenVersion = localStorage.getItem('seenChangelogVersion');
                    if (response.data.version !== seenVersion) setHasNewUpdate(true);
                }
            } catch (error) {
                console.error('Konnte Changelog nicht abrufen', error);
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

    const [openAdmin, setOpenAdmin] = useState(false);
    const [openUser, setOpenUser] = useState(false);
    const [openLang, setOpenLang] = useState(false);

    const adminRef = useRef(null);
    const userRef = useRef(null);
    const langRef = useRef(null);
    const navToggleRef = useRef(null);

    useClickOutside(adminRef, () => setOpenAdmin(false));
    useClickOutside(userRef, () => setOpenUser(false));
    useClickOutside(langRef, () => setOpenLang(false));

    const closeMobileNav = () => {
        if (navToggleRef.current) navToggleRef.current.checked = false;
        setOpenLang(false);
        setOpenAdmin(false);
        setOpenUser(false);
    };

    const marketingSections = [
        { key: '#platform', label: t('navbar.platform', 'Plattform') },
        { key: '#features', label: t('navbar.solutions', 'Lösungen') },
        { key: '#preise', label: t('navbar.pricing', 'Preise') },
    ];

    const marketingPages = [
        { to: '/arbeitszeit-rechner', label: t('navbar.workTimeCalculator', 'Arbeitszeit-Rechner') },
    ];

    const activeMarketingSection = location.pathname === '/' ? (location.hash || '#home') : null;

    useEffect(() => {
        if (location.pathname !== '/') return;
        if (!location.hash) {
            window.scrollTo({ top: 0, behavior: 'smooth' });
            return;
        }
        const sectionId = location.hash.replace('#', '');
        const element = document.getElementById(sectionId);
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, [location.pathname, location.hash]);

    const handleSectionNavigation = (event, hash) => {
        event.preventDefault();
        closeMobileNav();
        const target = hash === '#home' ? '/#home' : `/${hash}`;
        const shouldReplace = location.pathname === '/' && location.hash === hash;
        if (shouldReplace) {
            const sectionId = hash.replace('#', '');
            const element = document.getElementById(sectionId);
            if (element) {
                element.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            return;
        }
        navigate(target);
    };

    const handleLogout = () => {
        closeMobileNav();
        logout();
        navigate('/login', { replace: true });
    };

    const canOpenPage = useMemo(() => (pageKey, requiredAccess = 'VIEW') => {
        if (!currentUser) return false;
        return hasPageAccess(currentUser, pageKey, requiredAccess);
    }, [currentUser]);

    const adminMenuItems = useMemo(() => ([
        { key: 'adminDashboard', to: '/admin/dashboard', label: t('navbar.adminStart', 'Admin-Start') },
        { key: 'adminUsers', to: '/admin/users', label: t('navbar.userManagement', 'Benutzerverwaltung') },
        { key: 'adminProjects', to: '/admin/projects', label: t('navbar.workManagement', 'Kunden · Projekte · Aufgaben') },
        { key: 'adminAccounting', to: '/admin/accounting', label: t('navbar.accounting', 'Finanzbuchhaltung') },
        { key: 'supplyChain', to: getRouteForPage('supplyChain'), label: t('navbar.supplyChain', 'Supply Chain') },
        { key: 'chronoTwo', to: '/admin/chrono-two', label: 'Chrono 2.0' },
        { key: 'crm', to: '/admin/crm', label: t('navbar.crm', 'CRM & Marketing') },
        { key: 'banking', to: '/admin/banking', label: t('navbar.banking', 'Zahlungsverkehr') },
        { key: 'adminPayslips', to: '/admin/payslips', label: t('navbar.payslips', 'Abrechnungen') },
        { key: 'adminSchedule', to: '/admin/schedule', label: t('navbar.schedulePlanner', 'Dienstplan') },
        { key: 'adminKnowledge', to: '/admin/knowledge', label: t('navbar.knowledge', 'Firmen KI Wissen') },
        { key: 'companySettings', to: '/admin/company-settings', label: t('navbar.companySettings', 'Firmeneinstellungen') },
        { key: 'companyManagement', to: '/admin/company', label: t('navbar.companyManagement', 'Firmen') },
    ]).filter((item) => canOpenPage(item.key)), [canOpenPage, t]);

    const dashboardTarget = getDefaultLandingPage(currentUser);
    const workspaceMenuItems = useMemo(() => {
        if (!adminMenuItems.length) {
            return [];
        }
        if (!dashboardTarget || dashboardTarget === '/' || isAdmin || isSuperAdmin) {
            return adminMenuItems;
        }
        if (adminMenuItems.some((item) => item.to === dashboardTarget)) {
            return adminMenuItems;
        }
        return [
            { key: 'dashboardHome', to: dashboardTarget, label: t('navbar.myDashboard', 'Mein Dashboard') },
            ...adminMenuItems,
        ];
    }, [adminMenuItems, dashboardTarget, isAdmin, isSuperAdmin, t]);
    const dashboardMenuItem = useMemo(() => {
        const dashboardItem = workspaceMenuItems.find((item) => item.key === 'dashboardHome' || item.key === 'adminDashboard');
        if (dashboardItem) {
            return dashboardItem;
        }
        if (dashboardTarget && dashboardTarget !== '/') {
            return { key: 'dashboardHome', to: dashboardTarget, label: t('navbar.myDashboard', 'Mein Dashboard') };
        }
        return null;
    }, [dashboardTarget, t, workspaceMenuItems]);
    const platformMenuGroups = useMemo(() => {
        const itemsByKey = new Map(workspaceMenuItems.map((item) => [item.key, item]));
        const pick = (keys) => keys.map((key) => itemsByKey.get(key)).filter(Boolean);
        return [
            {
                key: 'timeTeam',
                title: t('navbar.platformTimeTeam', 'Zeit & Team'),
                subtitle: t('navbar.platformPeoplePlanning', 'Personal & Planung'),
                items: pick(['adminUsers', 'adminSchedule', 'adminPayslips']),
            },
            {
                key: 'finance',
                title: t('navbar.platformPayrollFinance', 'Lohn & Finanzen'),
                subtitle: t('navbar.platformCashflow', 'Geldfluss'),
                items: pick(['adminAccounting', 'banking']),
            },
            {
                key: 'growth',
                title: t('navbar.platformGrowth', 'Wachstum'),
                subtitle: t('navbar.platformCustomersGoods', 'Kunden & Waren'),
                items: pick(['adminProjects', 'crm', 'supplyChain']),
            },
            {
                key: 'setup',
                title: t('navbar.platformSetup', 'Setup'),
                subtitle: t('navbar.platformAdministration', 'Administration'),
                items: pick(['adminKnowledge', 'chronoTwo', 'companySettings', 'companyManagement']),
            },
        ].filter((group) => group.items.length > 0);
    }, [t, workspaceMenuItems]);
    const groupedPlatformItemKeys = useMemo(
        () => new Set(platformMenuGroups.flatMap((group) => group.items.map((item) => item.key))),
        [platformMenuGroups]
    );
    const fallbackPlatformItems = useMemo(
        () => workspaceMenuItems.filter((item) => item.key !== dashboardMenuItem?.key && !groupedPlatformItemKeys.has(item.key)),
        [dashboardMenuItem, groupedPlatformItemKeys, workspaceMenuItems]
    );
    const showProfileLink = canOpenPage('personalData');
    const changePasswordTarget = canOpenPage('adminChangePassword') ? '/admin/change-password' : '/personal-data';
    const showChangePassword = canOpenPage('adminChangePassword') || canOpenPage('personalData');
    const userDisplayName = getUserDisplayName(currentUser);
    const userInitial = userDisplayName?.[0]?.toUpperCase() || currentUser?.username?.[0]?.toUpperCase() || 'U';
    const workspaceMenuLabel = t('navbar.platform', 'Plattform');

    const toggleWorkspaceMenu = () => {
        setOpenAdmin((prev) => !prev);
        setOpenUser(false);
        setOpenLang(false);
    };

    const platformMenu = workspaceMenuItems.length > 0 ? (
        <div className={`${styles['dropdown-menu']} ${styles['platform-menu']}`}>
            <div className={styles['platform-menu-header']}>
                <div>
                    <span className={styles['platform-kicker']}>Chrono</span>
                    <strong>{t('navbar.switchWorkspace', 'Arbeitsbereich wechseln')}</strong>
                </div>
                {dashboardMenuItem && (
                    <Link
                        to={dashboardMenuItem.to}
                        onClick={closeMobileNav}
                        className={styles['platform-dashboard-link']}
                    >
                        {t('navbar.goToDashboard', 'Zum Dashboard')}
                    </Link>
                )}
            </div>
            <div className={styles['platform-grid']}>
                {platformMenuGroups.map((group) => (
                    <section className={styles['platform-group']} key={group.key} aria-label={group.title}>
                        <span className={styles['platform-group-title']}>{group.title}</span>
                        <span className={styles['platform-group-subtitle']}>{group.subtitle}</span>
                        <div className={styles['platform-group-items']}>
                            {group.items.map((item) => (
                                <Link
                                    key={item.key}
                                    to={item.to}
                                    onClick={closeMobileNav}
                                    className={`${styles['platform-item']} ${location.pathname === item.to ? styles.activeLink : ''}`}
                                >
                                    <span className={styles['platform-item-code']} aria-hidden="true">
                                        {getPlatformItemCode(item)}
                                    </span>
                                    <span className={styles['platform-item-label']}>{item.label}</span>
                                </Link>
                            ))}
                        </div>
                    </section>
                ))}
            </div>
            {fallbackPlatformItems.length > 0 && (
                <div className={styles['platform-extra-items']}>
                    {fallbackPlatformItems.map((item) => (
                        <Link
                            key={item.key}
                            to={item.to}
                            onClick={closeMobileNav}
                            className={styles['platform-item']}
                        >
                            <span className={styles['platform-item-code']} aria-hidden="true">
                                {getPlatformItemCode(item)}
                            </span>
                            <span className={styles['platform-item-label']}>{item.label}</span>
                        </Link>
                    ))}
                </div>
            )}
        </div>
    ) : null;

    return (
        <div className={styles['scoped-navbar']}>
            <nav className={styles.navbar} aria-label="Hauptnavigation">
                <div className={styles['navbar-brand']}>
                    <Link to="/" className={styles['navbar-logo']}>
                        <img
                            className={styles['navbar-logo-image']}
                            src="/img/komplettesLogo.png"
                            alt="Chrono"
                        />
                    </Link>
                </div>

                <input
                    type="checkbox"
                    id="nav-toggle"
                    className={styles['nav-toggle']}
                    ref={navToggleRef}
                    onChange={() => setOpenLang(false)}
                />
                <label htmlFor="nav-toggle" className={styles['nav-toggle-label']} aria-label="Menü umschalten">
                    <span></span><span></span><span></span>
                </label>

                <ul className={styles['navbar-links']}>
                    {!authToken || isPublicPage ? (
                        <>
                            {marketingSections.map((section) => (
                                <li key={section.key}>
                                    <Link
                                        to={`/${section.key}`}
                                        onClick={(event) => handleSectionNavigation(event, section.key)}
                                        className={`${styles.marketingLink} ${activeMarketingSection === section.key ? styles.activeLink : ''}`}
                                    >
                                        {section.label}
                                    </Link>
                                </li>
                            ))}
                            {marketingPages.map((page) => (
                                <li key={page.to}>
                                    <Link
                                        to={page.to}
                                        onClick={closeMobileNav}
                                        className={`${styles.marketingLink} ${location.pathname === page.to ? styles.activeLink : ''}`}
                                    >
                                        {page.label}
                                    </Link>
                                </li>
                            ))}

                            <li className={styles['flex-spacer']} aria-hidden="true"></li>
                            <li>
                                <Link to="/login" onClick={closeMobileNav} className={styles.marketingLink}>
                                    {t('navbar.login', 'Login')}
                                </Link>
                            </li>
                            <li>
                                <Link to="/register" onClick={closeMobileNav} className={`${styles.marketingLink} ${styles.marketingCta}`}>
                                    {t('navbar.register', 'Registrieren')}
                                </Link>
                            </li>
                            <li>
                                <button
                                    className={styles['icon-btn']}
                                    onClick={toggleTheme}
                                    aria-label={theme === 'light' ? t('darkMode', 'Dark Mode') : t('lightMode', 'Light Mode')}
                                    title={theme === 'light' ? t('darkMode', 'Dark Mode') : t('lightMode', 'Light Mode')}
                                >
                                    <span className={styles['icon-wrap']}>{theme === 'light' ? <IconMoon/> : <IconSun/>}</span>
                                </button>
                            </li>
                            <li className={`${styles.dropdown} ${openLang ? styles.open : ''}`} ref={langRef}>
                                <button
                                    className={`${styles['dropdown-trigger']} ${styles.iconish}`}
                                    onClick={() => setOpenLang((prev) => !prev)}
                                    aria-haspopup="true"
                                    aria-expanded={openLang}
                                >
                                    🌐 <IconChevronDown/>
                                </button>
                                <div className={styles['dropdown-menu']}>
                                    <button className={`${styles['lang-item']} ${language === 'de' ? styles.active : ''}`} onClick={() => { setLanguage('de'); setOpenLang(false); }}>DE</button>
                                    <button className={`${styles['lang-item']} ${language === 'en' ? styles.active : ''}`} onClick={() => { setLanguage('en'); setOpenLang(false); }}>EN</button>
                                </div>
                            </li>
                        </>
                    ) : (
                        <>
                            {currentUser && (
                                <>
                                    <li>
                                        <Link
                                            to="/arbeitszeit-rechner"
                                            onClick={closeMobileNav}
                                            className={location.pathname === '/arbeitszeit-rechner' ? styles.activeLink : ''}
                                        >
                                            {t('navbar.workTimeCalculator', 'Arbeitszeit-Rechner')}
                                        </Link>
                                    </li>

                                    {workspaceMenuItems.length === 0 && (
                                        <li>
                                            <Link to={dashboardTarget}>
                                                {t('navbar.myDashboard', 'Mein Dashboard')}
                                            </Link>
                                        </li>
                                    )}
                                </>
                            )}

                            <li className={styles['flex-spacer']} aria-hidden="true"></li>

                            <li>
                                <button
                                    className={styles['icon-btn']}
                                    aria-label={t('navbar.whatsNew', 'Was ist neu?')}
                                    onClick={openChangelog}
                                >
                                    <span className={styles['icon-wrap']}>
                                        <IconBell/>
                                        {hasNewUpdate && <span className={styles['notification-badge']} aria-hidden="true"></span>}
                                    </span>
                                </button>
                            </li>

                            <li>
                                <button
                                    className={styles['icon-btn']}
                                    onClick={toggleTheme}
                                    aria-label={theme === 'light' ? t('darkMode', 'Dark Mode') : t('lightMode', 'Light Mode')}
                                    title={theme === 'light' ? t('darkMode', 'Dark Mode') : t('lightMode', 'Light Mode')}
                                >
                                    <span className={styles['icon-wrap']}>{theme === 'light' ? <IconMoon/> : <IconSun/>}</span>
                                </button>
                            </li>

                            <li className={`${styles.dropdown} ${openLang ? styles.open : ''}`} ref={langRef}>
                                <button
                                    className={`${styles['dropdown-trigger']} ${styles.iconish}`}
                                    onClick={() => setOpenLang((prev) => !prev)}
                                    aria-haspopup="true"
                                    aria-expanded={openLang}
                                >
                                    🌐 <IconChevronDown/>
                                </button>
                                <div className={styles['dropdown-menu']}>
                                    <button className={`${styles['lang-item']} ${language === 'de' ? styles.active : ''}`} onClick={() => { setLanguage('de'); setOpenLang(false); }}>DE</button>
                                    <button className={`${styles['lang-item']} ${language === 'en' ? styles.active : ''}`} onClick={() => { setLanguage('en'); setOpenLang(false); }}>EN</button>
                                </div>
                            </li>

                            <li className={`${styles.dropdown} ${openUser ? styles.open : ''}`} ref={userRef}>
                                <button
                                    className={`${styles['dropdown-trigger']} ${styles['user-trigger']}`}
                                    onClick={() => setOpenUser((prev) => !prev)}
                                    aria-haspopup="true"
                                    aria-expanded={openUser}
                                >
                                    <span className={styles.avatar} aria-hidden="true">{userInitial}</span>
                                    <span className={styles.username}>{userDisplayName || currentUser?.username}</span>
                                    <IconChevronDown/>
                                </button>
                                <div className={styles['dropdown-menu']}>
                                    {showProfileLink && (
                                        <Link to="/personal-data" onClick={() => setOpenUser(false)}>
                                            {t('navbar.profile', 'Mein Profil')}
                                        </Link>
                                    )}
                                    <Link to="/whats-new" onClick={() => setOpenUser(false)}>
                                        {t('navbar.history', 'Update-Verlauf')}
                                    </Link>
                                    {showChangePassword && (
                                        <Link to={changePasswordTarget} onClick={() => setOpenUser(false)}>
                                            {t('admin.changePasswordTitle', 'Passwort ändern')}
                                        </Link>
                                    )}
                                    <button className={styles['navbar-logout']} onClick={handleLogout}>
                                        {t('navbar.logout', 'Logout')}
                                    </button>
                                </div>
                            </li>
                        </>
                    )}
                </ul>

                {authToken && !isPublicPage && currentUser && workspaceMenuItems.length > 0 && (
                    <div
                        className={`${styles.dropdown} ${styles['platform-dropdown']} ${styles['navbar-center-launcher']} ${openAdmin ? styles.open : ''}`}
                        ref={adminRef}
                    >
                        <button
                            className={`${styles['dropdown-trigger']} ${styles['platform-trigger']}`}
                            onClick={toggleWorkspaceMenu}
                            aria-haspopup="true"
                            aria-expanded={openAdmin}
                        >
                            {workspaceMenuLabel} <IconChevronDown/>
                        </button>
                        {platformMenu}
                    </div>
                )}
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
