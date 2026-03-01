import { NavLink, useLocation } from "react-router-dom";
import "../styles/MobileTabBar.css";

const USER_NAV = [
    { to: "/dashboard", label: "Start", icon: "🏠" },
    { to: "/percentage-punch", label: "Zeit", icon: "⏱️" },
    { to: "/payslips", label: "Lohn", icon: "📄" },
    { to: "/personal-data", label: "Profil", icon: "👤" },
];

const ADMIN_NAV = [
    { to: "/admin/dashboard", label: "Admin", icon: "📊" },
    { to: "/admin/projects", label: "Projekte", icon: "🗂️" },
    { to: "/admin/users", label: "Team", icon: "👥" },
    { to: "/admin/analytics", label: "Reports", icon: "📈" },
];

const USER_PATHS = USER_NAV.map((item) => item.to);

function MobileTabBar() {
    const location = useLocation();
    const { pathname } = location;

    const inAdminArea = pathname.startsWith("/admin");
    const inUserArea = USER_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));

    if (!inAdminArea && !inUserArea) {
        return null;
    }

    const navItems = inAdminArea ? ADMIN_NAV : USER_NAV;

    return (
        <>
            <div className="mobile-tab-spacer" aria-hidden="true" />
            <nav className="mobile-tab-bar" aria-label="Mobile Navigation">
                {navItems.map((item) => (
                    <NavLink
                        key={item.to}
                        to={item.to}
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
}

export default MobileTabBar;
