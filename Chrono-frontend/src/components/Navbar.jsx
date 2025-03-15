import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../styles/Navbar.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN');
    const { t } = useTranslation();

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
