// src/components/Navbar.jsx
import React, { useContext, useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../styles/Navbar.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN');
    const { t } = useTranslation();

    // State für die Helligkeit (Standard 1 => 100%)
    const [brightness, setBrightness] = useState(1);

    // Beim Start gespeicherte Helligkeit laden
    useEffect(() => {
        const saved = localStorage.getItem('appBrightness');
        if (saved) {
            setBrightness(Number(saved));
        }
    }, []);

    // Global CSS-Variable anpassen und in localStorage speichern
    useEffect(() => {
        document.documentElement.style.setProperty("--app-brightness", brightness);
        localStorage.setItem('appBrightness', brightness);
    }, [brightness]);

    return (
        <nav className="navbar">
            <div className="navbar-brand">
                <Link to="/" className="navbar-logo">Chrono</Link>
            </div>
            <ul className="navbar-links">
                {authToken ? (
                    <>
                        {isAdmin ? (
                            <>
                                <li><Link to="/admin">{t("navbar.adminStart")}</Link></li>
                                <li><Link to="/admin/users">{t("navbar.userManagement")}</Link></li>
                            </>
                        ) : (
                            <>
                                <li><Link to="/user">{t("navbar.myDashboard")}</Link></li>
                                <li><Link to="/profile">{t("navbar.profile")}</Link></li>
                            </>
                        )}
                        <li className="navbar-username">{t("navbar.hi")}, {currentUser?.username}</li>
                        <li>
                            <button className="navbar-logout" onClick={logout}>
                                {t("navbar.logout")}
                            </button>
                        </li>
                        {/* Helligkeitsregler in der Navbar */}
                        <li>
                            <div className="brightness-control">
                                <label htmlFor="brightness-slider">{t("Helligkeit")}</label>
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
                    </>
                ) : (
                    <>
                        <li><Link to="/login">{t("navbar.login")}</Link></li>
                        <li><Link to="/register">{t("navbar.register")}</Link></li>
                    </>
                )}
            </ul>
        </nav>
    );
};

export default Navbar;
