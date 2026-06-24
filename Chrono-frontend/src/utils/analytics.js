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
