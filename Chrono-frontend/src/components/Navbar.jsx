import React from 'react';
import { Link } from 'react-router-dom';

const Navbar = () => {
    return (
        <nav className="w-full bg-gray-800 text-white p-4 flex justify-between">
            <div className="text-lg font-bold">
                <Link to="/">Chrono</Link>
            </div>
            <div>
                <Link to="/login" className="px-4">
                    Login
                </Link>
                <Link to="/register" className="px-4">
                    Register
                </Link>
            </div>
        </nav>
    );
};

export default Navbar;
