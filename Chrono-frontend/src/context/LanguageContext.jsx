// src/context/LanguageContext.jsx
import React, { createContext, useState } from 'react';

const translations = {
    de: {
        login: {
            title: "Login",
            username: "Benutzername",
            password: "Passwort",
            button: "Einloggen",
            languageLabel: "Sprache:",
            error: "Login fehlgeschlagen. Bitte Zugangsdaten prüfen.",
            testStampMessage: "Test-Stempel ausgeführt",
            testStampButton: "Test-Stempel"
        },
        register: {
            title: "Registrieren",
            username: "Benutzername",
            password: "Passwort",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            button: "Registrieren"
        },
        personalData: {
            title: "Mein Profil",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            saveButton: "Speichern",
            changePassword: "Passwort ändern",
            currentPassword: "Aktuelles Passwort",
            newPassword: "Neues Passwort",
            errorLoading: "Fehler beim Laden der Profildaten",
            errorUpdating: "Fehler beim Aktualisieren des Profils",
            passwordChanged: "Passwort erfolgreich geändert",
            errorChangingPassword: "Fehler beim Ändern des Passworts"
        },
        adminDashboard: {
            titleWeekly: "Admin-Dashboard (Wochen Ansicht)",
            loggedInAs: "Eingeloggt als",
            adminPassword: "Admin Passwort",
            pleaseEnter: "bitte eingeben",
            timeTrackingCurrentWeek: "Zeiterfassung - Aktuelle Woche",
            prevWeek: "Vorherige Woche",
            nextWeek: "Nächste Woche",
            noEntriesThisWeek: "Keine Einträge in dieser Woche",
            total: "Gesamt",
            hours: "Std",
            minutes: "Min",
            expected: "Soll",
            noValidDate: "Kein gültiges Datum ausgewählt",
            errorApproving: "Fehler beim Genehmigen",
            errorDenying: "Fehler beim Ablehnen",
            editFailed: "Bearbeiten fehlgeschlagen",
            approved: "Genehmigt",
            denied: "Abgelehnt",
            pending: "Offen",
            editButton: "Bearbeiten",
            editTrackingTitle: "Zeiterfassung bearbeiten",
            button: {
                save: "Speichern",
                cancel: "Abbrechen",
                print: "Bericht drucken"
            },
            vacationRequestsTitle: "Urlaubsanträge (Alle Benutzer)",
            noVacations: "Keine Urlaubsanträge gefunden",
            correctionRequestsTitle: "Korrekturanträge (Alle Benutzer)",
            acceptButton: "Accept",
            rejectButton: "Reject",
            vacationCalendarTitle: "Urlaubskalender",
            forDate: "",
            monthlyOverview: "Monatsübersicht",
            noEntriesThisMonth: "Keine Einträge in diesem Monat",
            startDate: "Startdatum",
            endDate: "Enddatum",
            printReportTitle: "Bericht erstellen"
        },
        userManagement: {
            title: "Benutzerverwaltung",
            newUser: "Neuen Benutzer anlegen",
            editUser: "Benutzer bearbeiten",
            username: "Benutzername",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            role: "Rolle",
            expectedWorkDays: "Erwartete Arbeitstage",
            breakDuration: "Pausendauer (Min)",
            button: {
                save: "Speichern",
                cancel: "Abbrechen"
            },
            table: {
                actions: "Aktionen",
                edit: "Bearbeiten",
                delete: "Löschen",
                programCard: "Karte programmieren"
            },
            scheduleConfig: "Arbeitszeiten Konfiguration",
            cycleLength: "Cycle Length (Wochen):",
            week: "Woche",
            isHourly: "Stundenbasiert:",
            color: "Farbe",
            chooseColor: "Farbe auswählen",
            currentPassword: "Aktuelles Passwort",
            newPassword: "Neues Passwort",
            userPassword: "Benutzerpasswort",
            password: "Passwort",
            errorLoadingUsers: "Fehler beim Laden der Benutzer",
            errorAddingUser: "Fehler beim Anlegen des Benutzers",
            errorUpdatingUser: "Fehler beim Updaten des Benutzers",
            errorDeletingUser: "Fehler beim Löschen des Benutzers",
            programCardSuccess: "Karte erfolgreich beschrieben",
            programCardError: "Fehler beim Kartenbeschreiben",
            noUsers: "Keine Benutzer gefunden.",
            errorLoadingTracks: "Fehler beim Laden der Zeiterfassungen",
            errorLoadingVacations: "Fehler beim Laden der Urlaubsanträge",
            errorLoadingCorrections: "Fehler beim Laden der Korrekturanträge"
        },
        navbar: {
            login: "Login",
            register: "Registrieren",
            logout: "Abmelden",
            hi: "Hallo",
            adminStart: "Admin Start",
            userManagement: "Benutzerverwaltung",
            myDashboard: "Mein Dashboard",
            profile: "Profil"
        },
        // Top-Level Schlüssel für den UserDashboard-Bereich
        title: "Mein Dashboard",
        usernameLabel: "Benutzer",
        notLoggedIn: "Nicht eingeloggt",
        expectedWorkHours: "Erwartete Arbeitszeit/Tag",
        diffToday: "Differenz heute",
        overallDiff: "Gesamtdifferenz",
        weekDiff: "Wöchentliche Differenz",
        punchMessage: "Eingestempelt",
        manualPunchTitle: "Manuelles Einstempeln",
        manualPunchButton: "Einstempeln",
        manualPunchMessage: "Manuell eingestempelt",
        manualPunchError: "Fehler beim manuellen Einstempeln",
        vacationSubmitSuccess: "Urlaubsantrag wurde erfolgreich eingereicht",
        vacationSubmitError: "Fehler beim Einreichen des Urlaubsantrags",
        weeklyOverview: "Wochenübersicht",
        noEntries: "Keine Einträge",
        totalHours: "Gesamtstunden im Monat",
        weekday: "Wochentag",
        date: "Datum",
        workTime: "Arbeitszeit",
        printReportButton: "Zeiten drucken",
        selectPeriod: "Zeitraum auswählen",
        startDate: "Startdatum",
        endDate: "Enddatum",
        cancel: "Abbrechen",
        vacationTitle: "Urlaub beantragen",
        vacationSubmitButton: "Urlaubsantrag absenden",
        myVacations: "Meine Urlaubsanträge",
        to: "bis",
        vacationCalendarTitle: "Urlaubskalender",
        printReportTitle: "Bericht erstellen",
        prevMonth: "Vorheriger Monat",
        nextMonth: "Nächster Monat",
        prevWeek: "Vorherige Woche",
        nextWeek: "Nächste Woche",
        allMonths: "Alle Monate",
        month: "Monat",
        hours: "Std",
        minutes: "Min",
        expected: "Soll",
        // Wochentage
        days: {
            monday: "Montag",
            tuesday: "Dienstag",
            wednesday: "Mittwoch",
            thursday: "Donnerstag",
            friday: "Freitag",
            saturday: "Samstag",
            sunday: "Sonntag"
        },
        // Zusätzliche Keys für die Stundenansicht und Notizen
        hourlyDashboard: {
            title: "Stundenbasierte Ansicht",
            mode: "Stundenbasiert"
        },
        dailyNotePlaceholder: "Tagesnotiz",
        printReportError: "Fehler beim Erstellen des Berichts",
        loading: "Lade..."
    },
    en: {
        login: {
            title: "Login",
            username: "Username",
            password: "Password",
            button: "Log in",
            languageLabel: "Language:",
            error: "Login failed. Please check your credentials.",
            testStampMessage: "Test stamp executed",
            testStampButton: "Test Stamp"
        },
        register: {
            title: "Register",
            username: "Username",
            password: "Password",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            button: "Register"
        },
        personalData: {
            title: "My Profile",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            saveButton: "Save",
            changePassword: "Change Password",
            currentPassword: "Current Password",
            newPassword: "New Password",
            errorLoading: "Error loading profile data",
            errorUpdating: "Error updating profile",
            passwordChanged: "Password changed successfully",
            errorChangingPassword: "Error changing password"
        },
        adminDashboard: {
            titleWeekly: "Admin Dashboard (Weekly View)",
            loggedInAs: "Logged in as",
            adminPassword: "Admin Password",
            pleaseEnter: "please enter",
            timeTrackingCurrentWeek: "Time Tracking - Current Week",
            prevWeek: "Previous Week",
            nextWeek: "Next Week",
            noEntriesThisWeek: "No entries for this week",
            total: "Total",
            hours: "hrs",
            minutes: "min",
            expected: "Expected",
            noValidDate: "No valid date selected",
            errorApproving: "Error approving",
            errorDenying: "Error denying",
            editFailed: "Edit failed",
            approved: "Approved",
            denied: "Denied",
            pending: "Pending",
            editButton: "Edit",
            editTrackingTitle: "Edit Time Tracking",
            button: {
                save: "Save",
                cancel: "Cancel",
                print: "Print Report"
            },
            vacationRequestsTitle: "Vacation Requests (All Users)",
            noVacations: "No vacation requests found",
            correctionRequestsTitle: "Correction Requests (All Users)",
            acceptButton: "Accept",
            rejectButton: "Reject",
            vacationCalendarTitle: "Vacation Calendar",
            forDate: "",
            monthlyOverview: "Monthly Overview",
            noEntriesThisMonth: "No entries for this month",
            startDate: "Start Date",
            endDate: "End Date",
            printReportTitle: "Generate Report"
        },
        userManagement: {
            title: "User Management",
            newUser: "Create New User",
            editUser: "Edit User",
            username: "Username",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            role: "Role",
            expectedWorkDays: "Expected Work Days",
            breakDuration: "Break Duration (min)",
            button: {
                save: "Save",
                cancel: "Cancel"
            },
            table: {
                actions: "Actions",
                edit: "Edit",
                delete: "Delete",
                programCard: "Program Card"
            },
            scheduleConfig: "Work Hours Configuration",
            cycleLength: "Cycle Length (Weeks):",
            week: "Week",
            isHourly: "Hourly:",
            color: "Color",
            chooseColor: "Choose Color",
            currentPassword: "Current Password",
            newPassword: "New Password",
            userPassword: "User Password",
            password: "Password",
            errorLoadingUsers: "Error loading users",
            errorAddingUser: "Error adding user",
            errorUpdatingUser: "Error updating user",
            errorDeletingUser: "Error deleting user",
            programCardSuccess: "Card successfully programmed",
            programCardError: "Error programming card",
            noUsers: "No users found.",
            errorLoadingTracks: "Error loading time entries",
            errorLoadingVacations: "Error loading vacation requests",
            errorLoadingCorrections: "Error loading correction requests"
        },
        navbar: {
            login: "Login",
            register: "Register",
            logout: "Logout",
            hi: "Hi",
            adminStart: "Admin Start",
            userManagement: "User Management",
            myDashboard: "My Dashboard",
            profile: "Profile"
        },
        // Top-Level keys for the User Dashboard
        title: "My Dashboard",
        usernameLabel: "User",
        notLoggedIn: "Not logged in",
        expectedWorkHours: "Expected Work Hours per Day",
        diffToday: "Difference Today",
        overallDiff: "Overall Difference",
        weekDiff: "Weekly Difference",
        punchMessage: "Stamped",
        manualPunchTitle: "Manual Stamping",
        manualPunchButton: "Stamp",
        manualPunchMessage: "Manually stamped",
        manualPunchError: "Error during manual stamping",
        vacationSubmitSuccess: "Vacation request submitted successfully",
        vacationSubmitError: "Error submitting vacation request",
        weeklyOverview: "Weekly Overview",
        noEntries: "No entries",
        totalHours: "Total Hours in Month",
        weekday: "Weekday",
        date: "Date",
        workTime: "Work Time",
        printReportButton: "Print Times",
        selectPeriod: "Select Period",
        startDate: "Start Date",
        endDate: "End Date",
        cancel: "Cancel",
        vacationTitle: "Request Vacation",
        vacationSubmitButton: "Submit Vacation Request",
        myVacations: "My Vacation Requests",
        to: "to",
        vacationCalendarTitle: "Vacation Calendar",
        printReportTitle: "Generate Report",
        prevMonth: "Previous Month",
        nextMonth: "Next Month",
        prevWeek: "Previous Week",
        nextWeek: "Next Week",
        allMonths: "All Months",
        month: "Month",
        hours: "hrs",
        minutes: "min",
        expected: "Expected",
        // Wochentage
        days: {
            monday: "Monday",
            tuesday: "Tuesday",
            wednesday: "Wednesday",
            thursday: "Thursday",
            friday: "Friday",
            saturday: "Saturday",
            sunday: "Sunday"
        },
        // Zusätzliche Keys für die Stundenansicht und Notizen
        hourlyDashboard: {
            title: "Hourly Dashboard",
            mode: "Hourly"
        },
        dailyNotePlaceholder: "Daily note",
        printReportError: "Error generating report",
        loading: "Loading..."
    }
};

export const LanguageContext = createContext();

export const LanguageProvider = ({ children }) => {
    const [language, setLanguage] = useState("de");

    const t = (key) => {
        const keys = key.split(".");
        let translation = translations[language];
        for (const k of keys) {
            if (translation[k]) {
                translation = translation[k];
            } else {
                return key;
            }
        }
        return translation;
    };

    return (
        <LanguageContext.Provider value={{ language, setLanguage, t }}>
            {children}
        </LanguageContext.Provider>
    );
};

export const useTranslation = () => {
    const { t } = React.useContext(LanguageContext);
    return { t };
};
