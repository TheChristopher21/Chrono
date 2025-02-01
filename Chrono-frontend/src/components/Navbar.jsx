// src/components/Navbar.jsx
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
                <Link to="/">Chrono</Link>
            </div>
            <ul className="navbar-links">
                {authToken ? (
                    <>
                        {isAdmin ? (
                            <li>
                                <Link to="/admin">Admin Panel</Link>
                            </li>
                        ) : (
                            <li>
                                <Link to="/user">Dashboard</Link>
                            </li>
                        )}
                        <li className="navbar-username">Hi, {currentUser?.username}</li>
                        <li>
                            <button className="navbar-logout" onClick={logout}>Logout</button>
                        </li>
                    </>
                ) : (
                    <>
                        <li><Link to="/login">Login</Link></li>
                        <li><Link to="/register">Register</Link></li>
                    </>
                )}
            </ul>
        </nav>
    );
};

export default Navbar;
