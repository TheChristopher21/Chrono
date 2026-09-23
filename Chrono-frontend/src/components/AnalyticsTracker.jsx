import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../utils/api';
import {
    getAnalyticsSessionId,
    getAnalyticsVisitorId,
    isAnalyticsExcluded,
    setAnalyticsExcluded,
} from '../utils/analytics';

const LAST_PAGEVIEW_KEY = 'chronoAnalyticsLastPageView';

const getPagePath = () => window.location.pathname || '/';

const readLastPageView = () => {
    try {
        return JSON.parse(sessionStorage.getItem(LAST_PAGEVIEW_KEY) || '{}');
    } catch (error) {
        return {};
    }
};

const cleanText = (value, maxLength = 160) => {
    if (!value) {
        return null;
    }
    const text = String(value).replace(/\s+/g, ' ').trim();
    if (!text) {
        return null;
    }
    return text.length > maxLength ? text.slice(0, maxLength) : text;
};

const isSuperAdmin = (user) => Boolean(user?.roles?.includes('ROLE_SUPERADMIN'));

const AnalyticsTracker = () => {
    const location = useLocation();
    const { currentUser } = useAuth();
    const currentUserIsSuperAdmin = isSuperAdmin(currentUser);

    useEffect(() => {
        if (currentUserIsSuperAdmin) {
            setAnalyticsExcluded(true);
        }
    }, [currentUserIsSuperAdmin]);

    const shouldTrack = () => !currentUserIsSuperAdmin && !isAnalyticsExcluded();

    const sendEvent = (eventType, details = {}) => {
        if (!shouldTrack()) {
            return;
        }

        const payload = {
            eventType,
            visitorId: getAnalyticsVisitorId(),
            sessionId: getAnalyticsSessionId(),
            path: getPagePath(),
            pageTitle: cleanText(document.title, 256),
            referrer: document.referrer || null,
            language: navigator.language || null,
            viewportWidth: window.innerWidth,
            viewportHeight: window.innerHeight,
            ...details,
        };

        api.post('/api/public/analytics/events', payload).catch(() => {
            // Analytics must never interrupt the product experience.
        });
    };

    useEffect(() => {
        if (!shouldTrack()) {
            return;
        }

        const pagePath = getPagePath();
        const now = Date.now();
        const lastPageView = readLastPageView();

        if (lastPageView.path === pagePath && now - Number(lastPageView.at || 0) < 1200) {
            return;
        }

        sessionStorage.setItem(LAST_PAGEVIEW_KEY, JSON.stringify({ path: pagePath, at: now }));
        const timer = window.setTimeout(() => sendEvent('pageview'), 250);
        return () => window.clearTimeout(timer);
    }, [location.pathname, currentUserIsSuperAdmin]);

    useEffect(() => {
        const handleClick = (event) => {
            if (!shouldTrack()) {
                return;
            }

            const target = event.target?.closest?.('[data-analytics-id], a, button, [role="button"]');
            if (!target) {
                return;
            }

            const label = cleanText(
                target.getAttribute('data-analytics-id')
                || target.getAttribute('aria-label')
                || target.getAttribute('title')
                || target.textContent
            );

            if (!label) {
                return;
            }

            let elementTarget = target.getAttribute('data-analytics-target') || target.getAttribute('href') || null;
            if (elementTarget?.startsWith(window.location.origin)) {
                elementTarget = elementTarget.slice(window.location.origin.length) || '/';
            }

            sendEvent('click', {
                elementLabel: label,
                elementTarget: cleanText(elementTarget, 512),
            });
        };

        document.addEventListener('click', handleClick, true);
        return () => document.removeEventListener('click', handleClick, true);
    }, [currentUserIsSuperAdmin]);

    return null;
};

export default AnalyticsTracker;
