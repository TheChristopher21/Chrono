import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { getMobilePagesForContext, isAdminUser } from "../utils/pageAccess.js";
import "../styles/MobileTabBar.css";

const MobileTabBar = () => {
    const location = useLocation();
    const { currentUser } = useAuth();
    const { pathname } = location;

    const inAdminArea = pathname.startsWith("/admin");
    const context = inAdminArea || isAdminUser(currentUser) ? "admin" : "user";
    const navItems = getMobilePagesForContext(currentUser, context);
    const inKnownArea = navItems.some((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))
        || pathname === "/workspace/supply-chain";

    if (!inKnownArea || !navItems.length) {
        return null;
    }

    return (
        <>
            <div className="mobile-tab-spacer" aria-hidden="true" />
            <nav className="mobile-tab-bar" aria-label="Mobile Navigation">
                {navItems.map((item) => (
                    <NavLink
                        key={item.key}
                        to={item.path}
                        className={({ isActive }) => `mobile-tab-link${isActive ? " is-active" : ""}`}
                        aria-label={item.label}
                    >
                        <span className="mobile-tab-icon" aria-hidden="true">
                            {item.icon}
                        </span>
                        <span className="mobile-tab-label">{item.label}</span>
                    </NavLink>
                ))}
            </nav>
        </>
    );
};

export default MobileTabBar;
