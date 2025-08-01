import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import api from '../utils/api';

export const useUserData = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const [userProfile, setUserProfile] = useState(null);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [vacationRequests, setVacationRequests] = useState([]);
    const [correctionRequests, setCorrectionRequests] = useState([]);
    const [sickLeaves, setSickLeaves] = useState([]);
    const [holidays, setHolidays] = useState([]);

    const fetchData = useCallback(async () => {
        if (!currentUser?.username) return;
        try {
            const results = await Promise.allSettled([

                api.get(`/api/users/profile/${currentUser.username}`),
                api.get(`/api/timetracking/history?username=${currentUser.username}`),
                api.get('/api/vacation/my'),
                api.get(`/api/correction/my?username=${currentUser.username}`),
                api.get('/api/sick-leave/my'),
                api.get('/api/holidays', {
                    params: { year: new Date().getFullYear() }
                })
            ]);

            const [profileRes, summariesRes, vacationsRes, corrRes, sickRes, holiRes] = results;

            if (profileRes.status === 'fulfilled') setUserProfile(profileRes.value.data);
            if (summariesRes.status === 'fulfilled') setDailySummaries(summariesRes.value.data);
            if (vacationsRes.status === 'fulfilled') setVacationRequests(vacationsRes.value.data);
            if (corrRes.status === 'fulfilled') setCorrectionRequests(corrRes.value.data);
            if (sickRes.status === 'fulfilled') setSickLeaves(sickRes.value.data);
            if (holiRes.status === 'fulfilled') setHolidays(holiRes.value.data);

            if (results.some(r => r.status === 'rejected')) {
                console.error('Einige Daten konnten nicht geladen werden:', results);
                notify('Fehler beim Laden einiger Benutzerdaten.', 'error');
            }

        } catch (err) {
            console.error('Fehler beim Laden der Benutzerdaten:', err);
            notify('Fehler beim Laden der Benutzerdaten.', 'error');
        }
    }, [currentUser, notify]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    return { userProfile, dailySummaries, vacationRequests, correctionRequests, sickLeaves, holidays, refreshData: fetchData };
};
