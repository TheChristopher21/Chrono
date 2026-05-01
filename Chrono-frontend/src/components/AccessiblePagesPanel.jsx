import React, { useEffect, useMemo, useState } from "react";
import PropTypes from "prop-types";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { getDashboardPagesForContext } from "../utils/pageAccess.js";
import "../styles/AccessiblePagesPanel.css";

const getStoredCollapsedState = (storageKey) => {
    try {
        return window.localStorage.getItem(storageKey) === "collapsed";
    } catch {
        return false;
    }
};

const AccessiblePagesPanel = ({ context, title, subtitle }) => {
    const { currentUser } = useAuth();
    const location = useLocation();
    const userKey = currentUser?.id ?? currentUser?.username ?? currentUser?.email ?? "anonymous";
    const storageKey = useMemo(
        () => `chrono.accessiblePagesPanel.${context}.${userKey}`,
        [context, userKey]
    );
    const [collapsed, setCollapsed] = useState(() => getStoredCollapsedState(storageKey));
    const pages = getDashboardPagesForContext(currentUser, context)
        .filter((page) => page.path !== location.pathname);
    const panelContentId = `accessible-pages-panel-${context}-content`;

    useEffect(() => {
        setCollapsed(getStoredCollapsedState(storageKey));
    }, [storageKey]);

    if (!pages.length) {
        return null;
    }

    const toggleCollapsed = () => {
        setCollapsed((currentValue) => {
            const nextValue = !currentValue;
            try {
                window.localStorage.setItem(storageKey, nextValue ? "collapsed" : "expanded");
            } catch {
                // Ignore storage errors so the panel remains usable in restricted browser modes.
            }
            return nextValue;
        });
    };

    return (
        <section className={`accessible-pages-panel card${collapsed ? " is-collapsed" : ""}`}>
            <div className="accessible-pages-panel__head">
                <div>
                    <p className="accessible-pages-panel__eyebrow">Zugängliche Seiten</p>
                    <h3>{title}</h3>
                </div>
                <div className="accessible-pages-panel__actions">
                    {subtitle ? <p className="accessible-pages-panel__subtitle">{subtitle}</p> : null}
                    <button
                        type="button"
                        className="accessible-pages-panel__toggle"
                        onClick={toggleCollapsed}
                        aria-expanded={!collapsed}
                        aria-controls={panelContentId}
                        aria-label={collapsed ? "Seiten ausklappen" : "Seiten einklappen"}
                        title={collapsed ? "Ausklappen" : "Einklappen"}
                    >
                        <span className="accessible-pages-panel__chevron" aria-hidden="true" />
                    </button>
                </div>
            </div>

            {!collapsed ? (
                <div className="accessible-pages-panel__grid" id={panelContentId}>
                    {pages.map((page) => (
                        <Link key={page.key} to={page.path} className="accessible-pages-panel__card">
                            <span className="accessible-pages-panel__icon">{page.icon}</span>
                            <div>
                                <strong>{page.label}</strong>
                                <p>{page.description}</p>
                            </div>
                        </Link>
                    ))}
                </div>
            ) : null}
        </section>
    );
};

AccessiblePagesPanel.propTypes = {
    context: PropTypes.oneOf(["user", "admin"]).isRequired,
    title: PropTypes.string.isRequired,
    subtitle: PropTypes.string,
};

AccessiblePagesPanel.defaultProps = {
    subtitle: "",
};

export default AccessiblePagesPanel;
