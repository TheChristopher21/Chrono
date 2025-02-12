import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../styles/Navbar.css';

const Navbar = () => {
    const { authToken, logout, currentUser } = useAuth();
    const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN');

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
                                <li><Link to="/admin">Admin Start</Link></li>
                                <li><Link to="/admin/users">Benutzerverwaltung</Link></li>
                            </>
                        ) : (
                            <>
                                <li><Link to="/user">Mein Dashboard</Link></li>
                                <li><Link to="/profile">Profil</Link></li>
                            </>
                        )}
                        <li className="navbar-username">Hallo, {currentUser?.username}</li>
                        <li>
                            <button className="navbar-logout" onClick={logout}>Logout</button>
                        </li>
                    </>
                ) : (
                    <>
                        <li><Link to="/login">Login</Link></li>
                        <li><Link to="/register">Registrieren</Link></li>
                    </>
                )}
            </ul>
        </nav>
    );
};

export default Navbar;
