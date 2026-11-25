import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';
import { useAuth } from './AuthContext';
import { useRefreshOnMutation } from '../hooks/useRefreshOnMutation.js';
import { getCompanyFeatureList, hasCompanyContext, hasPageAccess, hasProjectsFeature } from '../utils/pageAccess.js';

export const CustomerContext = createContext();

const logUnexpectedError = (label, error) => {
    if (![401, 403].includes(error?.response?.status)) {
        console.error(label, error);
    }
};

export const CustomerProvider = ({ children }) => {
    const [customers, setCustomers] = useState([]);
    const [customersLoading, setCustomersLoading] = useState(false);
    const [customersError, setCustomersError] = useState(null);
    const { notify } = useNotification();
    const { t } = useTranslation();
    const { authToken, currentUser } = useAuth();
    const requestSequenceRef = useRef(0);
    const companyContextKey = currentUser?.company?.id ?? currentUser?.companyId ?? null;
    const activeCompanyContextRef = useRef(companyContextKey);
    activeCompanyContextRef.current = companyContextKey;
    const companyFeatures = getCompanyFeatureList(currentUser);
    const customerDataEnabled = Boolean(
        hasProjectsFeature(currentUser) || (
            hasCompanyContext(currentUser) && companyFeatures.includes('crm')
        )
    );
    const canReadCustomers = customerDataEnabled && ['dashboard', 'adminCustomers', 'adminProjects', 'crm']
        .some((pageKey) => hasPageAccess(currentUser, pageKey, 'VIEW'));


    const fetchCustomers = useCallback(async () => {
        const requestSequence = ++requestSequenceRef.current;
        if (!canReadCustomers) {
            setCustomers([]);
            setCustomersError(null);
            setCustomersLoading(false);
            return;
        }
        setCustomersLoading(true);
        setCustomersError(null);
        try {
            const res = await api.get('/api/customers');
            if (requestSequence === requestSequenceRef.current) {
                setCustomers(Array.isArray(res.data) ? res.data : []);
            }
        } catch (err) {
            if (requestSequence === requestSequenceRef.current) {
                logUnexpectedError('Error loading customers', err);
                const authorizationFailed = [401, 403].includes(err?.response?.status);
                if (authorizationFailed) {
                    setCustomers([]);
                } else {
                    notify(t('customer.loadError', 'Kunden konnten nicht geladen werden.'), 'error');
                }
                setCustomersError(authorizationFailed
                    ? t('project.access.changed', 'Deine Berechtigung hat sich geändert. Bitte lade die Seite neu.')
                    : t('customer.loadError', 'Kunden konnten nicht geladen werden.'));
            }
        } finally {
            if (requestSequence === requestSequenceRef.current) {
                setCustomersLoading(false);
            }
        }
    }, [canReadCustomers, companyContextKey, notify, t]);

    const createCustomer = useCallback(async (name) => {
        const mutationCompanyKey = activeCompanyContextRef.current;
        try {
            const res = await api.post('/api/customers', { name: name.trim() });
            if (mutationCompanyKey === activeCompanyContextRef.current) {
                setCustomers(prev => [...prev, res.data]);
            }
            return res.data;
        } catch (err) {
            throw err;
        }
    }, []);

    const updateCustomer = useCallback(async (id, name) => {
        const mutationCompanyKey = activeCompanyContextRef.current;
        try {
            const res = await api.put(`/api/customers/${id}`, { name: name.trim() });
            if (mutationCompanyKey === activeCompanyContextRef.current) {
                setCustomers(prev => prev.map(c => c.id === id ? res.data : c));
            }
            return res.data;
        } catch (err) {
            throw err;
        }
    }, []);

    const deleteCustomer = useCallback(async (id) => {
        const mutationCompanyKey = activeCompanyContextRef.current;
        try {
            await api.delete(`/api/customers/${id}`);
            if (mutationCompanyKey === activeCompanyContextRef.current) {
                setCustomers(prev => prev.filter(c => c.id !== id));
            }
        } catch (err) {
            throw err;
        }
    }, []);

    useRefreshOnMutation(['customers'], fetchCustomers, {
        enabled: Boolean(authToken && canReadCustomers),
        refreshOnLocalMutation: false,
        refreshOnFocus: true,
        focusThrottleMs: 30_000,
    });

    useEffect(() => {
        requestSequenceRef.current += 1;
        setCustomers([]);
        setCustomersError(null);
        setCustomersLoading(false);

        if (authToken && canReadCustomers) {
            fetchCustomers();
        }
    }, [fetchCustomers, authToken, companyContextKey, canReadCustomers]);



    return (
        <CustomerContext.Provider value={{
            customers,
            customersLoading,
            customersError,
            fetchCustomers,
            createCustomer,
            updateCustomer,
            deleteCustomer
        }}>
            {children}
        </CustomerContext.Provider>
    );
};

export const useCustomers = () => useContext(CustomerContext);
