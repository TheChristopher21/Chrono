// src/pages/AdminUserManagement/AdminUserManagementPage.jsx
import React, { useState, useEffect, useCallback } from 'react';
import Navbar from '../../components/Navbar';
import { useAuth } from '../../context/AuthContext.jsx';
import { useNotification } from '../../context/NotificationContext';
import { useTranslation } from '../../context/LanguageContext';
import api from '../../utils/api';

import '../../styles/AdminUserManagementPageScoped.css';

import AdminUserList from './AdminUserList';
import AdminUserForm from './AdminUserForm';
import DeleteConfirmModal from './DeleteConfirmModal';
import { STANDARD_COLORS, defaultWeeklySchedule } from './adminUserManagementUtils';
import {
    ACCESS_MANAGE,
    hasPageAccess,
    normalizePagePermissionsForRole,
} from '../../utils/pageAccess.js';

const getTodayISOString = () => {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

const createUserFormState = (featureKeys = [], overrides = {}) => {
    const normalizedFeatureKeys = Array.isArray(featureKeys)
        ? featureKeys
        : featureKeys
            ? Object.values(featureKeys)
            : [];
    const roleName = overrides.roles?.[0] || 'ROLE_USER';

    return {
        username: '',
        firstName: '',
        lastName: '',
        address: '',
        department: '',
        birthDate: '',
        entryDate: '',
        country: 'DE',
        taxClass: '',
        tarifCode: '',
        canton: '',
        civilStatus: '',
        children: 0,
        religion: '',
        federalState: '',
        churchTax: false,
        gkvAdditionalRate: 0.0125,
        bankAccount: '',
        socialSecurityNumber: '',
        healthInsurance: '',
        personnelNumber: '',
        email: '',
        mobilePhone: '',
        landlinePhone: '',
        password: '',
        roles: [roleName],
        expectedWorkDays: 5.0,
        dailyWorkHours: 8.5,
        breakDuration: 30,
        annualVacationDays: 25,
        hourlyWage: null,
        monthlySalary: null,
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        scheduleEffectiveDate: getTodayISOString(),
        isHourly: false,
        isPercentage: false,
        employmentModelEffectiveFrom: getTodayISOString(),
        workPercentage: 100,
        trackingBalanceInMinutes: 0,
        includeInTimeTracking: true,
        companyId: null,
        companyFeatureKeys: normalizedFeatureKeys,
        pagePermissions: normalizePagePermissionsForRole(roleName, normalizedFeatureKeys, overrides.pagePermissions),
        ...overrides,
    };
};

const normalizeComparableValue = (value) => {
    if (value === undefined || value === '') {
        return null;
    }
    if (typeof value === 'number' && Number.isNaN(value)) {
        return null;
    }
    if (Array.isArray(value)) {
        return value.map(normalizeComparableValue);
    }
    if (value && typeof value === 'object') {
        return Object.keys(value)
            .sort()
            .reduce((acc, key) => {
                acc[key] = normalizeComparableValue(value[key]);
                return acc;
            }, {});
    }
    return value;
};

const areComparableValuesEqual = (left, right) => (
    JSON.stringify(normalizeComparableValue(left)) === JSON.stringify(normalizeComparableValue(right))
);

const primaryRole = (roles) => (Array.isArray(roles) && roles.length > 0 ? roles[0] : 'ROLE_USER');

const isSuperAdminUser = (user) => Array.isArray(user?.roles) && user.roles.includes('ROLE_SUPERADMIN');

const hasOnlyTimeTrackingVisibilityChanged = (originalUser, nextUser) => {
    if (!originalUser || !nextUser) {
        return false;
    }

    const originalVisibility = originalUser.includeInTimeTracking !== false;
    const nextVisibility = nextUser.includeInTimeTracking !== false;
    if (originalVisibility === nextVisibility) {
        return false;
    }

    const comparableFields = [
        'username',
        'firstName',
        'lastName',
        'address',
        'department',
        'birthDate',
        'entryDate',
        'country',
        'taxClass',
        'tarifCode',
        'canton',
        'civilStatus',
        'children',
        'religion',
        'federalState',
        'churchTax',
        'gkvAdditionalRate',
        'bankAccount',
        'socialSecurityNumber',
        'healthInsurance',
        'personnelNumber',
        'email',
        'mobilePhone',
        'landlinePhone',
        'expectedWorkDays',
        'dailyWorkHours',
        'breakDuration',
        'annualVacationDays',
        'scheduleCycle',
        'weeklySchedule',
        'scheduleEffectiveDate',
        'isHourly',
        'isPercentage',
        'workPercentage',
        'monthlySalary',
        'hourlyRate',
        'color',
    ];

    const originalRole = primaryRole(originalUser.roles);
    const nextRole = primaryRole(nextUser.roles);
    const originalPermissions = normalizePagePermissionsForRole(
        originalRole,
        originalUser.companyFeatureKeys,
        originalUser.pagePermissions
    );
    const nextPermissions = normalizePagePermissionsForRole(
        nextRole,
        nextUser.companyFeatureKeys || originalUser.companyFeatureKeys,
        nextUser.pagePermissions
    );

    return comparableFields.every((field) => (
        areComparableValuesEqual(originalUser[field], nextUser[field])
    ))
        && areComparableValuesEqual(originalRole, nextRole)
        && areComparableValuesEqual(originalPermissions, nextPermissions);
};

const AdminUserManagementPage = () => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [users, setUsers] = useState([]);
    const [editingUser, setEditingUser] = useState(null);
    const [isCreatingNewUser, setIsCreatingNewUser] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState({ show: false, userId: null, username: '' });
    const [programStatus, setProgramStatus] = useState("");
    const [programStatusTone, setProgramStatusTone] = useState("info");
    const baseFeatureKeys = currentUser?.companyFeatureKeys || [];
    const canManageAdminUsers = hasPageAccess(currentUser, 'adminUsers', ACCESS_MANAGE);

    const notifyAdminUsersReadOnly = useCallback(() => {
        notify(
            t(
                'userManagement.readOnlyPermissions',
                'Nur Ansicht: Dieser Benutzer darf die Benutzerverwaltung sehen, aber keine Änderungen ausführen.'
            ),
            'warning'
        );
    }, [notify, t]);

    const initialNewUserState = {
        username: '',
        firstName: '',
        lastName: '',
        address: '',
        department: '',
        birthDate: '',
        entryDate: '',
        country: 'DE',
        taxClass: '',
        tarifCode: '',
        canton: '',
        civilStatus: '',
        children: 0,
        religion: '',
        federalState: '',
        churchTax: false,
        gkvAdditionalRate: 0.0125,
        bankAccount: '',
        socialSecurityNumber: '',
        healthInsurance: '',
        personnelNumber: '',
        email: '',
        mobilePhone: '',
        landlinePhone: '',
        password: '',
        roles: ['ROLE_USER'],
        expectedWorkDays: 5.0, // Default für alle neuen User
        dailyWorkHours: 8.5,
        breakDuration: 30,
        annualVacationDays: 25,
        hourlyWage: null,
        monthlySalary: null,
        color: STANDARD_COLORS[0],
        scheduleCycle: 1,
        weeklySchedule: [{ ...defaultWeeklySchedule }],
        scheduleEffectiveDate: getTodayISOString(),
        isHourly: false,
        isPercentage: false,
        employmentModelEffectiveFrom: getTodayISOString(),
        workPercentage: 100, // Default, falls isPercentage true wird
        trackingBalanceInMinutes: 0,
        includeInTimeTracking: true,
        companyId: null // Wird später serverseitig gesetzt oder für Superadmin-Erstellung
    };

    const [currentUserFormData, setCurrentUserFormData] = useState({ ...initialNewUserState });

    useEffect(() => {
        setCurrentUserFormData((prev) => ({
            ...prev,
            companyFeatureKeys: prev.companyFeatureKeys || baseFeatureKeys,
            pagePermissions: normalizePagePermissionsForRole(
                prev.roles?.[0] || 'ROLE_USER',
                prev.companyFeatureKeys || baseFeatureKeys,
                prev.pagePermissions
            ),
        }));
    }, [baseFeatureKeys]);

    const fetchUsers = useCallback(async () => {
        try {
            const res = await api.get('/api/admin/users');
            const userList = Array.isArray(res.data) ? res.data : [];
            setUsers(userList.filter(user => !isSuperAdminUser(user)).map(user => ({
                ...user,
                includeInTimeTracking: user.includeInTimeTracking !== false,
                scheduleEffectiveDate: user.scheduleEffectiveDate ? user.scheduleEffectiveDate.toString() : getTodayISOString(),
                weeklySchedule: user.weeklySchedule && user.weeklySchedule.length > 0 ? user.weeklySchedule : [{ ...defaultWeeklySchedule }],
                scheduleCycle: user.scheduleCycle || 1,
                country: user.country || 'DE',
                tarifCode: user.tarifCode || '',
                canton: user.canton || '',
                civilStatus: user.civilStatus || '',
                children: user.children || 0,
                religion: user.religion || '',
                federalState: user.federalState || '',
                churchTax: !!user.churchTax,
                gkvAdditionalRate: user.gkvAdditionalRate ?? 0.0125,
                bankAccount: user.bankAccount || '',
                socialSecurityNumber: user.socialSecurityNumber || '',
                mobilePhone: user.mobilePhone || '',
                landlinePhone: user.landlinePhone || '',
                isHourly: user.isHourly || false,
                isPercentage: user.isPercentage || false,
                employmentModelEffectiveFrom: getTodayISOString(),
                workPercentage: user.workPercentage !== null && user.workPercentage !== undefined ? user.workPercentage : (user.isPercentage ? 100 : null),
                expectedWorkDays: user.expectedWorkDays !== null && user.expectedWorkDays !== undefined ? user.expectedWorkDays : (user.isHourly ? null : 5.0), // Default 5.0 if not hourly and not set
                trackingBalanceInMinutes: user.trackingBalanceInMinutes !== null && user.trackingBalanceInMinutes !== undefined ? user.trackingBalanceInMinutes : 0,
                roles: user.roles && user.roles.length > 0 ? user.roles : ['ROLE_USER'],
                companyFeatureKeys: user.companyFeatureKeys || baseFeatureKeys,
                pagePermissions: normalizePagePermissionsForRole(
                    user.roles?.[0] || 'ROLE_USER',
                    user.companyFeatureKeys || baseFeatureKeys,
                    user.pagePermissions
                )
            })));
        } catch (err) {
            console.error(t("userManagement.errorLoadingUsers", "Fehler beim Laden der Benutzer."), err);
            notify(t("userManagement.errorLoadingUsers", "Fehler beim Laden der Benutzer.") + `: ${err.message || ''}`, "error");
        }
    }, [baseFeatureKeys, t, notify]);

    useEffect(() => {
        fetchUsers();
    }, [fetchUsers]);

    const resetAndShowCreateForm = () => {
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        setEditingUser(null);
        setCurrentUserFormData({
            ...createUserFormState(baseFeatureKeys),
            scheduleEffectiveDate: getTodayISOString(),
            color: STANDARD_COLORS[Math.floor(Math.random() * STANDARD_COLORS.length)]
        });
        setIsCreatingNewUser(true);
    };

    const handleCancelForm = () => {
        setEditingUser(null);
        setIsCreatingNewUser(false);
        setCurrentUserFormData({ ...createUserFormState(baseFeatureKeys), scheduleEffectiveDate: getTodayISOString() });
    };

    const handleFormChange = (field, value) => {
        setCurrentUserFormData(prev => {
            const newState = { ...prev, [field]: value };

            if (field === 'country') {
                if (value === 'DE') {
                    newState.tarifCode = '';
                    newState.canton = '';
                } else if (value === 'CH') {
                    newState.taxClass = '';
                }
            }

            if (field === 'roles') {
                const nextRole = Array.isArray(value) && value.length > 0 ? value[0] : 'ROLE_USER';
                const featureKeys = prev.companyFeatureKeys || baseFeatureKeys;
                newState.pagePermissions = normalizePagePermissionsForRole(nextRole, featureKeys, prev.pagePermissions);
            }

            if (field === 'pagePermissions') {
                const nextRole = newState.roles?.[0] || 'ROLE_USER';
                const featureKeys = prev.companyFeatureKeys || baseFeatureKeys;
                newState.pagePermissions = normalizePagePermissionsForRole(nextRole, featureKeys, value);
            }

            if (field === 'isHourly') {
                if (value) { // Wird stündlich
                    newState.isPercentage = false;
                    newState.workPercentage = null; // oder 100, je nach Logik
                    newState.expectedWorkDays = null; // Stündliche haben dies nicht für die Soll-Berechnung
                    newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.0; // Standardwert für Stundenzettel
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else { // Wird NICHT stündlich
                    // Wenn isPercentage false ist, wird es Standard User, sonst Percentage
                    if (!newState.isPercentage) { // Standard User
                        newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                        newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                        newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                        newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
                    } else { // Bleibt Percentage User
                        newState.workPercentage = prev.workPercentage !== null && prev.workPercentage !== undefined ? prev.workPercentage : 100;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0; // Behalte oder setze Default
                        // dailyWorkHours etc. bleiben null für Percentage
                    }
                }
            } else if (field === 'isPercentage') {
                if (value) { // Wird prozentual
                    newState.isHourly = false;
                    newState.workPercentage = prev.workPercentage !== null && prev.workPercentage !== undefined ? prev.workPercentage : 100;
                    // ANPASSUNG HIER: expectedWorkDays beibehalten oder auf Standard setzen
                    newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                    newState.dailyWorkHours = null;
                    newState.scheduleCycle = null;
                    newState.weeklySchedule = null;
                    newState.scheduleEffectiveDate = null;
                } else { // Wird NICHT prozentual
                    // Wenn isHourly false ist, wird es Standard User
                    if (!newState.isHourly) { // Standard User
                        newState.workPercentage = null; // Oder 100, aber Standard User hat kein workPercentage-Feld direkt
                        newState.dailyWorkHours = prev.dailyWorkHours !== null && prev.dailyWorkHours !== undefined ? prev.dailyWorkHours : 8.5;
                        newState.expectedWorkDays = prev.expectedWorkDays !== null && prev.expectedWorkDays !== undefined ? prev.expectedWorkDays : 5.0;
                        newState.scheduleCycle = prev.scheduleCycle !== null && prev.scheduleCycle !== undefined ? prev.scheduleCycle : 1;
                        newState.weeklySchedule = (prev.weeklySchedule && prev.weeklySchedule.length > 0) ? prev.weeklySchedule : [{ ...defaultWeeklySchedule }];
                        newState.scheduleEffectiveDate = prev.scheduleEffectiveDate || getTodayISOString();
                    }
                    // Wenn isHourly true ist, bleibt es stündlich (wurde oben schon behandelt)
                }
            }
            return newState;
        });
    };

    const handleScheduleCycleChangeInForm = (newCycleInput) => {
        const newCycle = Number(newCycleInput);
        if (isNaN(newCycle) || newCycle < 1) return;

        setCurrentUserFormData(prev => {
            let newSchedule = prev.weeklySchedule ? [...prev.weeklySchedule] : [];
            if (!Array.isArray(newSchedule)) newSchedule = [];

            const currentLength = newSchedule.length;

            if (newCycle > currentLength) {
                for (let i = 0; i < (newCycle - currentLength); i++) {
                    newSchedule.push({ ...defaultWeeklySchedule });
                }
            } else if (newCycle < currentLength) {
                newSchedule = newSchedule.slice(0, newCycle);
            }
            return {...prev, scheduleCycle: newCycle, weeklySchedule: newSchedule};
        });
    };

    const handleWeeklyScheduleDayChangeInForm = (weekIndex, dayKey, dayValueInput) => {
        const dayValue = dayValueInput === '' ? null : Number(dayValueInput);

        setCurrentUserFormData(prev => {
            if (!prev.weeklySchedule || !Array.isArray(prev.weeklySchedule) || weekIndex >= prev.weeklySchedule.length) {
                const newGeneratedSchedule = [];
                const cycle = prev.scheduleCycle || 1;
                for (let i = 0; i < cycle; i++) {
                    newGeneratedSchedule.push(i === weekIndex ? { ...defaultWeeklySchedule, [dayKey]: dayValue } : { ...defaultWeeklySchedule });
                }
                return { ...prev, weeklySchedule: newGeneratedSchedule };
            }

            const newWeeklySchedule = prev.weeklySchedule.map((week, idx) => {
                if (idx === weekIndex) {
                    return { ...week, [dayKey]: dayValue };
                }
                return week;
            });
            return { ...prev, weeklySchedule: newWeeklySchedule };
        });
    };

    const handleSubmitForm = async (e) => {
        e.preventDefault();
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        const dataToSend = { ...currentUserFormData };

        if (dataToSend.roles && !Array.isArray(dataToSend.roles) && typeof dataToSend.roles === 'string') {
            dataToSend.roles = [dataToSend.roles];
        } else if (!dataToSend.roles || dataToSend.roles.length === 0) {
            dataToSend.roles = ["ROLE_USER"];
        }
        if (Object.prototype.hasOwnProperty.call(dataToSend, 'role')) {
            delete dataToSend.role;
        }
        dataToSend.pagePermissions = normalizePagePermissionsForRole(
            dataToSend.roles?.[0] || 'ROLE_USER',
            dataToSend.companyFeatureKeys || baseFeatureKeys,
            dataToSend.pagePermissions
        );
        dataToSend.includeInTimeTracking = dataToSend.includeInTimeTracking !== false;
        delete dataToSend.companyFeatureKeys;

        if (Object.prototype.hasOwnProperty.call(dataToSend, 'hourlyWage')) {
            dataToSend.hourlyRate = dataToSend.hourlyWage;
            delete dataToSend.hourlyWage;
        }

        // Sicherstellen, dass Felder für das Backend korrekt formatiert/gesetzt sind
        if (dataToSend.isHourly) {
            dataToSend.isPercentage = false;
            dataToSend.workPercentage = null;
            dataToSend.scheduleCycle = null;
            dataToSend.weeklySchedule = null;
            dataToSend.scheduleEffectiveDate = null;
            dataToSend.expectedWorkDays = null; // Explizit null für stündliche User
        } else if (dataToSend.isPercentage) {
            dataToSend.isHourly = false;
            dataToSend.dailyWorkHours = null;
            dataToSend.scheduleCycle = null;
            dataToSend.weeklySchedule = null;
            dataToSend.scheduleEffectiveDate = null;
            // expectedWorkDays wird für Percentage-User beibehalten/gesetzt
            if (dataToSend.expectedWorkDays === null || dataToSend.expectedWorkDays === undefined) {
                dataToSend.expectedWorkDays = 5.0; // Default, falls nicht gesetzt
            }
        } else { // Standard User
            dataToSend.isHourly = false;
            dataToSend.isPercentage = false;
            dataToSend.workPercentage = null; // Oder 100, aber DTO erwartet es so
            if (dataToSend.expectedWorkDays === null || dataToSend.expectedWorkDays === undefined) {
                dataToSend.expectedWorkDays = 5.0; // Default für Standard, falls nicht gesetzt
            }
        }


        if (isCreatingNewUser) {
            if (!dataToSend.password || dataToSend.password.trim() === '') {
                notify(t("userManagement.errorPasswordRequired", "Passwort ist für neue Benutzer erforderlich."), "error");
                return;
            }
            try {
                await api.post('/api/admin/users', dataToSend);
                notify(t("userManagement.userAddedSuccess", "Benutzer erfolgreich hinzugefügt!"), "success");
                fetchUsers();
                handleCancelForm();
            } catch (err) {
                console.error(t("userManagement.errorAddingUser"), err);
                notify(t("userManagement.errorAddingUser", "Fehler beim Hinzufügen des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
        } else if (editingUser) {
            try {
                const payloadForUpdate = { ...dataToSend, id: editingUser.id };
                if (hasOnlyTimeTrackingVisibilityChanged(editingUser, payloadForUpdate)) {
                    await api.patch(`/api/admin/users/${editingUser.id}/time-tracking-visibility`, {
                        includeInTimeTracking: payloadForUpdate.includeInTimeTracking,
                    });
                    notify(t("userManagement.userUpdatedSuccess", "Benutzer erfolgreich aktualisiert!"), "success");
                    fetchUsers();
                    handleCancelForm();
                    return;
                }

                const modelChanged = Boolean(editingUser.isHourly) !== Boolean(dataToSend.isHourly)
                    || Boolean(editingUser.isPercentage) !== Boolean(dataToSend.isPercentage);
                const snapshotChanged = editingUser.dailyWorkHours !== dataToSend.dailyWorkHours
                    || editingUser.expectedWorkDays !== dataToSend.expectedWorkDays
                    || editingUser.workPercentage !== dataToSend.workPercentage
                    || editingUser.scheduleCycle !== dataToSend.scheduleCycle
                    || editingUser.scheduleEffectiveDate !== dataToSend.scheduleEffectiveDate
                    || JSON.stringify(editingUser.weeklySchedule || []) !== JSON.stringify(dataToSend.weeklySchedule || []);
                if (!modelChanged && !snapshotChanged) {
                    delete payloadForUpdate.employmentModelEffectiveFrom;
                } else if (!payloadForUpdate.employmentModelEffectiveFrom) {
                    payloadForUpdate.employmentModelEffectiveFrom = getTodayISOString();
                }
                // Passwort wird nur gesendet, wenn es geändert werden soll (über separates Feld/Request im Backend)
                // Im AdminUserController wird 'newPassword' als RequestParam erwartet, nicht im Body des DTOs für Update.
                // Das DTO 'password' Feld ist hier also nicht direkt für Update gedacht.
                delete payloadForUpdate.password;

                await api.put(`/api/admin/users`, payloadForUpdate); // Ggf. newPassword als QueryParam anhängen, falls benötigt

                notify(t("userManagement.userUpdatedSuccess", "Benutzer erfolgreich aktualisiert!"), "success");
                fetchUsers();
                handleCancelForm();
            } catch (err) {
                console.error(t("userManagement.errorUpdatingUser"), err);
                notify(t("userManagement.errorUpdatingUser", "Fehler beim Aktualisieren des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
        }
    };

    const handleEditUser = (userToEdit) => {
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        setEditingUser(userToEdit);
        const featureKeys = userToEdit.companyFeatureKeys || baseFeatureKeys;
        setCurrentUserFormData({
            ...createUserFormState(featureKeys), // Start mit Defaults
            ...userToEdit,          // Überschreibe mit User-Daten
            password: '',           // Passwort nicht vorausfüllen für Bearbeitung
            includeInTimeTracking: userToEdit.includeInTimeTracking !== false,
            hourlyWage: userToEdit.hourlyRate ?? userToEdit.hourlyWage ?? null,
            roles: userToEdit.roles && userToEdit.roles.length > 0 ? userToEdit.roles : ['ROLE_USER'],
            companyFeatureKeys: featureKeys,
            scheduleEffectiveDate: userToEdit.scheduleEffectiveDate ? userToEdit.scheduleEffectiveDate.toString() : getTodayISOString(),
            weeklySchedule: userToEdit.weeklySchedule && userToEdit.weeklySchedule.length > 0
                ? userToEdit.weeklySchedule
                : [{ ...defaultWeeklySchedule }],
            scheduleCycle: userToEdit.scheduleCycle || 1,
            country: userToEdit.country || 'DE',
            tarifCode: userToEdit.tarifCode || '',
            canton: userToEdit.canton || '',
            civilStatus: userToEdit.civilStatus || '',
            children: userToEdit.children || 0,
            religion: userToEdit.religion || '',
            bankAccount: userToEdit.bankAccount || '',
            socialSecurityNumber: userToEdit.socialSecurityNumber || '',
            mobilePhone: userToEdit.mobilePhone || '',
            landlinePhone: userToEdit.landlinePhone || '',
            workPercentage: userToEdit.workPercentage !== null && userToEdit.workPercentage !== undefined ? userToEdit.workPercentage : (userToEdit.isPercentage ? 100 : null),
            expectedWorkDays: userToEdit.expectedWorkDays !== null && userToEdit.expectedWorkDays !== undefined ? userToEdit.expectedWorkDays : (userToEdit.isHourly ? null : 5.0),
            employmentModelEffectiveFrom: getTodayISOString(),
            monthlySalary: userToEdit.monthlySalary ?? null,
            pagePermissions: normalizePagePermissionsForRole(
                userToEdit.roles?.[0] || 'ROLE_USER',
                featureKeys,
                userToEdit.pagePermissions
            )
        });
        setIsCreatingNewUser(false);
    };

    const requestDeleteUser = (user) => {
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        setDeleteConfirm({ show: true, userId: user.id, username: user.username });
    };
    const cancelDelete = () => {
        setDeleteConfirm({ show: false, userId: null, username: '' });
    };
    const confirmDelete = async () => {
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        if (deleteConfirm.userId) {
            try {
                await api.delete(`/api/admin/users/${deleteConfirm.userId}`);
                notify(t("userManagement.userDeletedSuccess", "Benutzer erfolgreich gelöscht."), "success");
                fetchUsers(); // Liste neu laden
                if (editingUser && editingUser.id === deleteConfirm.userId) { // Falls der bearbeitete User gelöscht wurde
                    handleCancelForm(); // Formular zurücksetzen
                }
            } catch (err) {
                console.error(t("userManagement.errorDeletingUser"), err);
                notify(t("userManagement.errorDeletingUser", "Fehler beim Löschen des Benutzers.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, "error");
            }
            setDeleteConfirm({ show: false, userId: null, username: '' });
        }
    };

    async function handleProgramCard(user) {
        if (!canManageAdminUsers) {
            notifyAdminUsersReadOnly();
            return;
        }
        try {
            // Konvertiere Username zu Hex (max 16 ASCII Chars -> 32 Hex Chars)
            // Annahme: stringToHex16 ist korrekt implementiert und verfügbar
            // const hexUsername = stringToHex16(user.username); // stringToHex16 ist nicht in diesem Snippet definiert
            // Stattdessen senden wir den Username direkt und der Service kümmert sich ggf. um die Konvertierung, falls nötig, oder es wird anders gehandhabt.
            // Für den aktuellen NFCCommandService wird der 'data'-String direkt verwendet.
            const dataPayload = user.username; // Oder eine spezifische ID/Formatierung

            const payload = { type: "PROGRAM", data: dataPayload };
            const response = await api.post('/api/nfc/command', payload);

            if (response.data && response.data.id) {
                setProgramStatus(t("userManagement.nfcProgramStart", "Kartenprogrammierung gestartet. Bitte NFC-Karte auflegen..."));
                setProgramStatusTone("info");
                const commandId = response.data.id;
                let maxTries = 20; // Erhöhte Versuche für längere Wartezeit (20 * 1.5s = 30s)
                let delay = 1500;

                const pollStatus = async () => {
                    try {
                        const res = await api.get(`/api/nfc/command/status/${commandId}`);
                        if (res.data.status === "done") {
                            setProgramStatus(t("userManagement.programCardSuccess", "Karte erfolgreich programmiert!"));
                            setProgramStatusTone("success");
                            setTimeout(() => setProgramStatus(""), 7000);
                        } else if (res.data.status === "pending" && maxTries-- > 0) {
                            setProgramStatus(t(
                                "userManagement.nfcProgramProgress",
                                "Warte auf NFC-Agent... ({{count}} Versuche übrig)",
                                { count: maxTries }
                            ));
                            setProgramStatusTone("info");
                            setTimeout(pollStatus, delay);
                        } else {
                            setProgramStatus(t("userManagement.programCardErrorTimeout", "Zeitüberschreitung bei Kartenprogrammierung oder Befehl nicht gefunden."));
                            setProgramStatusTone("error");
                            setTimeout(() => setProgramStatus(""), 7000);
                        }
                    } catch (pollError) {
                        console.error("NFC poll error:", pollError);
                        setProgramStatus(t("userManagement.programCardErrorComm", "Kommunikationsfehler mit NFC-Agent."));
                        setProgramStatusTone("error");
                        setTimeout(() => setProgramStatus(""), 7000);
                    }
                };
                pollStatus();
            } else {
                setProgramStatusTone("error");
                notify(t("userManagement.programCardError", "Fehler beim Starten der Kartenprogrammierung."), 'error');
            }
        } catch (err) {
            console.error("Fehler beim Kartenprogrammieren:", err);
            setProgramStatusTone("error");
            notify(t("userManagement.programCardError", "Fehler beim Kartenprogrammieren.") + `: ${err.response?.data?.message || err.response?.data || err.message}`, 'error');
        }
    }


    return (
        <div className="admin-user-management-page scoped-dashboard">
            <Navbar />

            {programStatus && (
                <div className={`nfc-status-message ${programStatusTone}`}>{programStatus}</div>
            )}


            <header className="page-header">
                <h2>{t("userManagement.title")}</h2>
            </header>

            {!canManageAdminUsers && (
                <div className="nfc-status-message info">
                    {t(
                        'userManagement.readOnlyPermissions',
                        'Nur Ansicht: Dieser Benutzer darf die Benutzerverwaltung sehen, aber keine Änderungen ausführen.'
                    )}
                </div>
            )}

            {canManageAdminUsers && !isCreatingNewUser && !editingUser && (
                <div className="add-user-button-container">
                    <button onClick={resetAndShowCreateForm} className="button-primary add-user-button">
                        {t("userManagement.button.addNewUser", "Neuen Benutzer hinzufügen")}
                    </button>
                </div>
            )}

            {canManageAdminUsers && (editingUser || isCreatingNewUser) && (
                <AdminUserForm
                    t={t}
                    isEditing={!!editingUser}
                    userData={currentUserFormData}
                    originalUser={editingUser}
                    setUserData={handleFormChange}
                    onSubmit={handleSubmitForm}
                    onCancel={handleCancelForm}
                    onScheduleCycleChange={handleScheduleCycleChangeInForm}
                    onWeeklyScheduleDayChange={handleWeeklyScheduleDayChangeInForm}
                />
            )}

            <AdminUserList
                users={users}
                t={t}
                handleEditUser={handleEditUser}
                requestDeleteUser={requestDeleteUser}
                handleProgramCard={handleProgramCard}
                canManage={canManageAdminUsers}
            />

            <DeleteConfirmModal
                visible={deleteConfirm.show}
                title={t("userManagement.deleteConfirmTitle", "Benutzer löschen")}
                message={t("userManagement.deleteConfirmMessage", "Dieser Benutzer wird deaktiviert. Seine Daten bleiben bis zu einem Jahr gespeichert und werden danach endgültig gelöscht. Fortfahren?")}
                userName={deleteConfirm.username}
                onConfirm={confirmDelete}
                onCancel={cancelDelete}
            />
        </div>
    );
};

export default AdminUserManagementPage;
