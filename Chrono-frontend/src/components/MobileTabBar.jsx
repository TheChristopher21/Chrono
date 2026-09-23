import { useLocation } from "react-router-dom";
import WorkspaceLink from "./workspace/WorkspaceLink.jsx";
import { useAuth } from "../context/AuthContext.jsx";
import { useTranslation } from "../context/LanguageContext.jsx";
import { getMobilePagesForContext, isAdminUser } from "../utils/pageAccess.js";
import "../styles/MobileTabBar.css";

const MobileTabBar = () => {
    const location = useLocation();
    const { currentUser } = useAuth();
    const { t } = useTranslation();
    const { pathname } = location;

    const inAdminArea = pathname.startsWith("/admin");
    const context = inAdminArea || isAdminUser(currentUser) ? "admin" : "user";
    const navItems = getMobilePagesForContext(currentUser, context, t);
    const inKnownArea = navItems.some((item) => pathname === item.path || pathname.startsWith(`${item.path}/`))
        || pathname === "/workspace/supply-chain";

    if (/^\/pms(?:\/|$)/.test(pathname) || !inKnownArea || !navItems.length) {
        return null;
    }

    return (
        <>
            <div className="mobile-tab-spacer" aria-hidden="true" />
            <nav className="mobile-tab-bar" aria-label={t("mobileTabBar.ariaLabel", "Mobile Navigation")}>
                {navItems.map((item) => (
                    <WorkspaceLink
                        key={item.key}
                        to={item.path}
                        className={`mobile-tab-link${pathname === item.path || pathname.startsWith(`${item.path}/`) ? " is-active" : ""}`}
                        aria-current={pathname === item.path || pathname.startsWith(`${item.path}/`) ? 'page' : undefined}
                        aria-label={item.label}
                    >
                        <span className="mobile-tab-icon" aria-hidden="true">
                            {item.icon}
                        </span>
                        <span className="mobile-tab-label">{item.label}</span>
                    </WorkspaceLink>
                ))}
            </nav>
        </>
    );
};

export default MobileTabBar;
