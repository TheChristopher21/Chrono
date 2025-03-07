// src/context/LanguageContext.jsx
import React, { createContext, useContext, useState } from 'react';

// Übersetzungsobjekt: Definiere hier alle Schlüssel und deren Übersetzungen für Deutsch (de) und Englisch (en).
const translations = {
    de: {
        // Login
        "login.title": "Login",
        "login.username": "Benutzername",
        "login.password": "Passwort",
        "login.button": "Einloggen",
        "login.languageLabel": "Sprache:",
        // Register
        "register.title": "Registrieren",
        "register.username": "Benutzername",
        "register.password": "Passwort",
        "register.firstName": "Vorname",
        "register.lastName": "Nachname",
        "register.email": "E-Mail",
        "register.button": "Registrieren",
        // PersonalDataPage
        "personalData.title": "Mein Profil",
        "personalData.firstName": "Vorname",
        "personalData.lastName": "Nachname",
        "personalData.email": "E-Mail",
        "personalData.saveButton": "Speichern",
        "personalData.changePassword": "Passwort ändern",
        "personalData.currentPassword": "Aktuelles Passwort",
        "personalData.newPassword": "Neues Passwort",
        "personalData.errorLoading": "Fehler beim Laden der Profildaten",
        "personalData.errorUpdating": "Fehler beim Aktualisieren des Profils",
        "personalData.passwordChanged": "Passwort erfolgreich geändert",
        "personalData.errorChangingPassword": "Fehler beim Ändern des Passworts",
        // AdminDashboard
        "adminDashboard.titleWeekly": "Admin-Dashboard (Wochen Ansicht)",
        "adminDashboard.loggedInAs": "Eingeloggt als",
        "adminDashboard.adminPassword": "Admin Passwort",
        "adminDashboard.pleaseEnter": "bitte eingeben",
        "adminDashboard.timeTrackingCurrentWeek": "Zeit Erfassung - Aktuelle Woche",
        "adminDashboard.prevWeek": "Vorherige Woche",
        "adminDashboard.nextWeek": "Nächste Woche",
        "adminDashboard.noEntriesThisWeek": "Keine Einträge in dieser Woche",
        "adminDashboard.total": "Gesamt",
        "adminDashboard.hours": "Std",
        "adminDashboard.minutes": "Min",
        "adminDashboard.noValidDate": "Kein gültiges Datum ausgewählt",
        "adminDashboard.errorApproving": "Fehler beim Genehmigen",
        "adminDashboard.errorDenying": "Fehler beim Ablehnen",
        "adminDashboard.editFailed": "Bearbeiten fehlgeschlagen",
        "adminDashboard.approved": "Genehmigt",
        "adminDashboard.denied": "Abgelehnt",
        "adminDashboard.pending": "Offen",
        "adminDashboard.editButton": "Bearbeiten",
        "adminDashboard.editTrackingTitle": "Zeiterfassung bearbeiten",
        "adminDashboard.saveButton": "Speichern",
        "adminDashboard.cancelButton": "Abbrechen",
        "adminDashboard.vacationRequestsTitle": "Urlaubsanträge (Alle Benutzer)",
        "adminDashboard.noVacations": "Keine Urlaubsanträge gefunden",
        "adminDashboard.correctionRequestsTitle": "Korrekturanträge (Alle Benutzer)",
        "adminDashboard.acceptButton": "Accept",
        "adminDashboard.rejectButton": "Reject",
        "adminDashboard.vacationCalendarTitle": "Urlaubskalender",
        // AdminUserManagementPage
        "adminUserManagement.title": "Benutzerverwaltung",
        "adminUserManagement.newUser": "Neuen Benutzer anlegen",
        "adminUserManagement.editUser": "Benutzer bearbeiten",
        "adminUserManagement.username": "Benutzername",
        "adminUserManagement.firstName": "Vorname",
        "adminUserManagement.lastName": "Nachname",
        "adminUserManagement.email": "E-Mail",
        "adminUserManagement.role": "Rolle",
        "adminUserManagement.expectedWorkDays": "Expected Work Days",
        "adminUserManagement.breakDuration": "Break Duration (min)",
        "adminUserManagement.button.save": "Speichern",
        "adminUserManagement.button.cancel": "Abbrechen",
        "adminUserManagement.table.actions": "Aktionen",
        "adminUserManagement.table.edit": "Bearbeiten",
        "adminUserManagement.table.delete": "Löschen",
        "adminUserManagement.table.programCard": "Karte programmieren",
        "adminUserManagement.scheduleConfig": "Arbeitszeiten Konfiguration",
        "adminUserManagement.cycleLength": "Cycle Length (Wochen):",
        "adminUserManagement.week": "Woche",
        "adminUserManagement.color": "Farbe",
        "adminUserManagement.chooseColor": "Farbe auswählen",
        "adminUserManagement.currentPassword": "Aktuelles Passwort",
        "adminUserManagement.newPassword": "Neues Passwort",
        "adminUserManagement.userPassword": "User Password",
        "adminUserManagement.password": "Passwort",
        "adminUserManagement.errorLoadingUsers": "Fehler beim Laden der Benutzer",
        "adminUserManagement.errorAddingUser": "Fehler beim Anlegen des Benutzers",
        "adminUserManagement.errorUpdatingUser": "Fehler beim Updaten des Benutzers",
        "adminUserManagement.errorDeletingUser": "Fehler beim Löschen des Benutzers",
        "adminUserManagement.programCardSuccess": "Karte erfolgreich beschrieben",
        "adminUserManagement.programCardError": "Fehler beim Kartenbeschreiben",
        // Navbar
        "navbar.login": "Login",
        "navbar.register": "Registrieren",
        "navbar.logout": "Abmelden",
        "navbar.hi": "Hallo",
        "navbar.adminStart": "Admin Start",
        "navbar.userManagement": "Benutzerverwaltung",
        "navbar.myDashboard": "Mein Dashboard",
        "navbar.profile": "Profil"
    },
    en: {
        // Login
        "login.title": "Login",
        "login.username": "Username",
        "login.password": "Password",
        "login.button": "Log in",
        "login.languageLabel": "Language:",
        // Register
        "register.title": "Register",
        "register.username": "Username",
        "register.password": "Password",
        "register.firstName": "First Name",
        "register.lastName": "Last Name",
        "register.email": "Email",
        "register.button": "Register",
        // PersonalDataPage
        "personalData.title": "My Profile",
        "personalData.firstName": "First Name",
        "personalData.lastName": "Last Name",
        "personalData.email": "Email",
        "personalData.saveButton": "Save",
        "personalData.changePassword": "Change Password",
        "personalData.currentPassword": "Current Password",
        "personalData.newPassword": "New Password",
        "personalData.errorLoading": "Error loading profile data",
        "personalData.errorUpdating": "Error updating profile",
        "personalData.passwordChanged": "Password changed successfully",
        "personalData.errorChangingPassword": "Error changing password",
        // AdminDashboard
        "adminDashboard.titleWeekly": "Admin Dashboard (Weekly View)",
        "adminDashboard.loggedInAs": "Logged in as",
        "adminDashboard.adminPassword": "Admin Password",
        "adminDashboard.pleaseEnter": "please enter",
        "adminDashboard.timeTrackingCurrentWeek": "Time Tracking - Current Week",
        "adminDashboard.prevWeek": "Previous Week",
        "adminDashboard.nextWeek": "Next Week",
        "adminDashboard.noEntriesThisWeek": "No entries in this week",
        "adminDashboard.total": "Total",
        "adminDashboard.hours": "hrs",
        "adminDashboard.minutes": "min",
        "adminDashboard.noValidDate": "No valid date selected",
        "adminDashboard.errorApproving": "Error approving",
        "adminDashboard.errorDenying": "Error denying",
        "adminDashboard.editFailed": "Edit failed",
        "adminDashboard.approved": "Approved",
        "adminDashboard.denied": "Denied",
        "adminDashboard.pending": "Pending",
        "adminDashboard.editButton": "Edit",
        "adminDashboard.editTrackingTitle": "Edit Time Tracking",
        "adminDashboard.saveButton": "Save",
        "adminDashboard.cancelButton": "Cancel",
        "adminDashboard.vacationRequestsTitle": "Vacation Requests (All Users)",
        "adminDashboard.noVacations": "No vacation requests found",
        "adminDashboard.correctionRequestsTitle": "Correction Requests (All Users)",
        "adminDashboard.acceptButton": "Accept",
        "adminDashboard.rejectButton": "Reject",
        "adminDashboard.vacationCalendarTitle": "Vacation Calendar",
        // AdminUserManagementPage
        "adminUserManagement.title": "User Management",
        "adminUserManagement.newUser": "Create New User",
        "adminUserManagement.editUser": "Edit User",
        "adminUserManagement.username": "Username",
        "adminUserManagement.firstName": "First Name",
        "adminUserManagement.lastName": "Last Name",
        "adminUserManagement.email": "Email",
        "adminUserManagement.role": "Role",
        "adminUserManagement.expectedWorkDays": "Expected Work Days",
        "adminUserManagement.breakDuration": "Break Duration (min)",
        "adminUserManagement.button.save": "Save",
        "adminUserManagement.button.cancel": "Cancel",
        "adminUserManagement.table.actions": "Actions",
        "adminUserManagement.table.edit": "Edit",
        "adminUserManagement.table.delete": "Delete",
        "adminUserManagement.table.programCard": "Program Card",
        "adminUserManagement.scheduleConfig": "Schedule Configuration",
        "adminUserManagement.cycleLength": "Cycle Length (weeks):",
        "adminUserManagement.week": "Week",
        "adminUserManagement.color": "Color",
        "adminUserManagement.chooseColor": "Choose Color",
        "adminUserManagement.currentPassword": "Current Password",
        "adminUserManagement.newPassword": "New Password",
        "adminUserManagement.userPassword": "User Password",
        "adminUserManagement.password": "Password",
        "adminUserManagement.errorLoadingUsers": "Error loading users",
        "adminUserManagement.errorAddingUser": "Error adding user",
        "adminUserManagement.errorUpdatingUser": "Error updating user",
        "adminUserManagement.errorDeletingUser": "Error deleting user",
        "adminUserManagement.programCardSuccess": "Card programmed successfully",
        "adminUserManagement.programCardError": "Error programming card",
        // Navbar
        "navbar.login": "Login",
        "navbar.register": "Register",
        "navbar.logout": "Logout",
        "navbar.hi": "Hi",
        "navbar.adminStart": "Admin Start",
        "navbar.userManagement": "User Management",
        "navbar.myDashboard": "My Dashboard",
        "navbar.profile": "Profile"
    }
};

// Erstelle den Context
const LanguageContext = createContext({
    language: 'de',
    setLanguage: () => {}
});

// Provider, der den Sprachzustand verwaltet
export function LanguageProvider({ children }) {
    const [language, setLanguage] = useState('de'); // Standard: Deutsch
    return (
        <LanguageContext.Provider value={{ language, setLanguage }}>
            {children}
        </LanguageContext.Provider>
    );
}

// useTranslation-Hook: Gibt die Übersetzungsfunktion t(key) zurück
export function useTranslation() {
    const { language } = useContext(LanguageContext);
    function t(key) {
        return translations[language][key] || key;
    }
    return { t };
}

export { LanguageContext };
