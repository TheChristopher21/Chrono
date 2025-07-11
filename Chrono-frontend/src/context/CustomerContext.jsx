import React, { createContext, useContext, useEffect, useState } from 'react';
import api from '../utils/api';
import { useNotification } from './NotificationContext';
import { useTranslation } from './LanguageContext';

export const CustomerContext = createContext();

export const CustomerProvider = ({ children }) => {
    const [customers, setCustomers] = useState([]);
    const { notify } = useNotification();
    const { t } = useTranslation();

    const fetchCustomers = async () => {
        try {
            const res = await api.get('/api/customers');
            setCustomers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error loading customers', err);
            notify(t('customerSaveError', 'Fehler beim Laden der Kunden'), 'error');
        }
    };

    const createCustomer = async (name) => {
        try {
            const res = await api.post('/api/customers', { name: name.trim() });
            setCustomers(prev => [...prev, res.data]);
            return res.data;
        } catch (err) {
            console.error('Error creating customer', err);
            notify(t('customerSaveError', 'Fehler beim Anlegen'), 'error');
            throw err;
        }
    };

    const updateCustomer = async (id, name) => {
        try {
            const res = await api.put(`/api/customers/${id}`, { name: name.trim() });
            setCustomers(prev => prev.map(c => c.id === id ? res.data : c));
            return res.data;
        } catch (err) {
            console.error('Error updating customer', err);
            notify(t('customerSaveError', 'Fehler beim Speichern'), 'error');
            throw err;
        }
    };

    const deleteCustomer = async (id) => {
        try {
            await api.delete(`/api/customers/${id}`);
            setCustomers(prev => prev.filter(c => c.id !== id));
        } catch (err) {
            console.error('Error deleting customer', err);
            notify(t('customerSaveError', 'Fehler beim Löschen'), 'error');
            throw err;
        }
    };

    useEffect(() => { fetchCustomers(); }, []);

    return (
        <CustomerContext.Provider value={{ customers, fetchCustomers, createCustomer, updateCustomer, deleteCustomer }}>
            {children}
        </CustomerContext.Provider>
    );
};

export const useCustomers = () => useContext(CustomerContext);
