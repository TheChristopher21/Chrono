import api from './api';

export const ANALYTICS_EXCLUDED_KEY = 'chronoAnalyticsExcluded';
const VISITOR_ID_KEY = 'chronoAnalyticsVisitorId';
const SESSION_ID_KEY = 'chronoAnalyticsSessionId';

const createId = () => {
    if (window.crypto?.randomUUID) {
        return window.crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
};

export const isAnalyticsExcluded = () => localStorage.getItem(ANALYTICS_EXCLUDED_KEY) === 'true';

export const setAnalyticsExcluded = (excluded) => {
    if (excluded) {
        localStorage.setItem(ANALYTICS_EXCLUDED_KEY, 'true');
    } else {
        localStorage.removeItem(ANALYTICS_EXCLUDED_KEY);
    }
};

export const getAnalyticsVisitorId = () => {
    let visitorId = localStorage.getItem(VISITOR_ID_KEY);
    if (!visitorId) {
        visitorId = createId();
        localStorage.setItem(VISITOR_ID_KEY, visitorId);
    }
    return visitorId;
};

export const getAnalyticsSessionId = () => {
    let sessionId = sessionStorage.getItem(SESSION_ID_KEY);
    if (!sessionId) {
        sessionId = createId();
        sessionStorage.setItem(SESSION_ID_KEY, sessionId);
    }
    return sessionId;
};

const cleanAnalyticsText = (value, maxLength = 160) => {
    if (!value) return null;
    const text = String(value).replace(/\s+/g, ' ').trim();
    if (!text) return null;
    return text.length > maxLength ? text.slice(0, maxLength) : text;
};

/**
 * Records a stable funnel milestone in the existing click analytics stream.
 * The backend currently supports pageview/click events only, so explicit
 * milestones use a durable label instead of relying on changing button copy.
 */
export const trackAnalyticsSignal = (label, target = null) => {
    if (typeof window === 'undefined' || isAnalyticsExcluded()) {
        return Promise.resolve();
    }

    const cleanLabel = cleanAnalyticsText(label);
    if (!cleanLabel) return Promise.resolve();

    return api.post('/api/public/analytics/events', {
        eventType: 'click',
        visitorId: getAnalyticsVisitorId(),
        sessionId: getAnalyticsSessionId(),
        path: window.location.pathname || '/',
        pageTitle: cleanAnalyticsText(document.title, 256),
        referrer: document.referrer || null,
        elementLabel: cleanLabel,
        elementTarget: cleanAnalyticsText(target, 512),
        language: navigator.language || null,
        viewportWidth: window.innerWidth,
        viewportHeight: window.innerHeight,
    }).catch(() => {
        // Funnel measurement must never interrupt the product experience.
    });
};
