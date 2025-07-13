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
            const [profileRes, summariesRes, vacationsRes, corrRes, sickRes, holiRes] = await Promise.all([
                api.get(`/api/users/profile/${currentUser.username}`),
                api.get(`/api/timetracking/history?username=${currentUser.username}`),
                api.get('/api/vacation/my'),
                api.get(`/api/correction/my?username=${currentUser.username}`),
                api.get('/api/sick-leave/my'),
                api.get('/api/holidays')
            ]);
            setUserProfile(profileRes.data);
            setDailySummaries(summariesRes.data);
            setVacationRequests(vacationsRes.data);
            setCorrectionRequests(corrRes.data);
            setSickLeaves(sickRes.data);
            setHolidays(holiRes.data);
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
