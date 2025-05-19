// src/context/LanguageContext.jsx
import React, { createContext, useState } from 'react';

const translations = {
    de: {
        // ----------------------------------------------------------------------
        // Login-Bereich
        // ----------------------------------------------------------------------
        login: {
            title: "Willkommen zurück!",
            username: "Benutzername",
            password: "Passwort",
            button: "Login",
            languageLabel: "Sprache",
            error: "Login fehlgeschlagen. Bitte Zugangsdaten prüfen.",
            testStampMessage: "Test-Stempel ausgeführt",
            testStampButton: "Test-Stempel",
            intro: "Melde dich an, um fortzufahren.",
            noAccount: "Noch kein Account?",
            registerHere: "Hier registrieren",
            waitMessage: "Bitte 1 Minute warten, bevor erneut gestempelt wird.",
            stamped: "Eingestempelt",
        },
        // ----------------------------------------------------------------------
        // Admin
        // ----------------------------------------------------------------------
        admin: {
            changePasswordTitle: "Passwort ändern",
            changePasswordButton: "Passwort ändern",
        },
        // ----------------------------------------------------------------------
        // Register
        // ----------------------------------------------------------------------
        register: {
            title: "Registrieren",
            username: "Benutzername",
            password: "Passwort",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            button: "Registrieren",
        },
        // ----------------------------------------------------------------------
        // Personal Data
        // ----------------------------------------------------------------------
        personalData: {
            title: "Mein Profil",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            saveButton: "Speichern",
            saved: "Daten erfolgreich gespeichert.",
            changePassword: "Passwort ändern",
            currentPassword: "Aktuelles Passwort",
            newPassword: "Neues Passwort",
            errorLoading: "Fehler beim Laden der Profildaten",
            errorUpdating: "Fehler beim Aktualisieren des Profils",
            passwordChanged: "Passwort erfolgreich geändert",
            errorChangingPassword: "Fehler beim Ändern des Passworts",
        },
        // ----------------------------------------------------------------------
        // Admin Dashboard
        // ----------------------------------------------------------------------
        adminDashboard: {
            titleWeekly: "Admin-Dashboard (Wochen Ansicht)",
            loggedInAs: "Eingeloggt als",
            adminPassword: "Admin Passwort",
            userPassword: "Benutzerpasswort",
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
            statusLabel: "Status",
            button: {
                save: "Speichern",
                cancel: "Abbrechen",
                print: "Bericht drucken",
            },
            printButton: "Zeiten drucken",   // de

            vacationRequestsTitle: "Urlaubsanträge (Alle Benutzer)",
            noVacations: "Keine Urlaubsanträge gefunden",
            correctionRequestsTitle: "Korrekturanträge (Alle Benutzer)",
            acceptButton: "Genehmigen",
            rejectButton: "Ablehnen",
            vacationCalendarTitle: "Urlaubskalender",
            forDate: "",
            monthlyOverview: "Monatsübersicht",
            noEntriesThisMonth: "Keine Einträge in diesem Monat",
            startDate: "Startdatum",
            endDate: "Enddatum",
            printReportTitle: "Bericht erstellen",
            searchUser: "Benutzer suchen...",
            noMatch: "Keine passenden Benutzer gefunden.",
            correctionItemLabel: "Korrektur vom",
            noEntries: "Keine Einträge",
            newEntryButton: "Zeiten Eintragen",
            expandAll: "Alle aufklappen",
            collapseAll: "Alle zuklappen",
            correctionModal: {
                approveTitle: "Korrektur genehmigen",
                denyTitle: "Korrektur ablehnen",
                commentLabel: "Kommentar für den Nutzer:",
                commentPlaceholder: "Warum genehmigst / lehnst du ab?",
                confirmButton: "Bestätigen",
            },
        },
        // ----------------------------------------------------------------------
        // User Management
        // ----------------------------------------------------------------------
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
            workPercentage: "Work Percentage",
            annualVacationDays: "Urlaubstage",
            button: {
                save: "Speichern",
                cancel: "Abbrechen",
            },
            table: {
                actions: "Aktionen",
                edit: "Bearbeiten",
                delete: "Löschen",
                programCard: "Karte programmieren",
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
            programCardSuccess: "Karte erfolgreich programmiert",
            programCardError: "Fehler beim Kartenprogrammieren",
            noUsers: "Keine Benutzer gefunden.",
            errorLoadingTracks: "Fehler beim Laden der Zeiterfassungen",
            errorLoadingVacations: "Fehler beim Laden der Urlaubsanträge",
            errorLoadingCorrections: "Fehler beim Laden der Korrekturanträge",

            // Neue Keys
            percentageTracking: "Prozentbasierte Zeiterfassung",
            deleteConfirmTitle: "Benutzer löschen",
            deleteConfirmMessage: "Soll der Benutzer wirklich gelöscht werden? (Alle Daten werden gelöscht inkl. Zeiten!)",
            nfcProgramStart: "Programmierung gestartet. Bitte Karte auflegen...",
            programCardErrorTimeout: "Zeitüberschreitung beim Kartenprogrammieren.",
            deleteConfirmConfirm: "Ja, löschen",
            deleteConfirmCancel: "Abbrechen",
        },
        // ----------------------------------------------------------------------
        // Navbar
        // ----------------------------------------------------------------------
        navbar: {
            login: "Login",
            register: "Registrieren",
            logout: "Abmelden",
            hi: "Hallo",
            adminStart: "Admin Start",
            userManagement: "Benutzerverwaltung",
            myDashboard: "Mein Dashboard",
            profile: "Profil",
            brightness: "Helligkeit",
        },
        // ----------------------------------------------------------------------
        // Dashboard title / corrections
        // ----------------------------------------------------------------------
        title: "Mein Dashboard",
        correctionRequests: "Korrekturanträge",
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
        remainingVacation: "Verbleibender Urlaub",
        daysLabel: "Tage",
        vacationType: "Urlaubsart",
        fullDay: "Ganztags",
        halfDay: "Halbtags",
        days: {
            monday: "Montag",
            tuesday: "Dienstag",
            wednesday: "Mittwoch",
            thursday: "Donnerstag",
            friday: "Freitag",
            saturday: "Samstag",
            sunday: "Sonntag",
        },
        hourlyDashboard: {
            title: "Stundenbasierte Ansicht",
            mode: "Stundenbasiert",
        },
        dailyNotePlaceholder: "Tagesnotiz",
        printReportError: "Fehler beim Erstellen des Berichts",
        loading: "Lade...",

        showWeeklyOnly: "Nur aktuelle Woche",
        showAll: "Alle anzeigen",
        workStart: "Work Start",
        breakStart: "Break Start",
        breakEnd: "Break End",
        workEnd: "Work End",
        reason: "Grund",
        submitCorrectionRequest: "Korrektur anfragen",
        submitCorrectionFor: "Korrekturantrag für",
        submitCorrection: "Antrag senden",
        correctionSubmitSuccess: "Korrekturantrag erfolgreich gestellt.",
        correctionSubmitError: "Fehler beim Absenden des Korrekturantrags.",
        errorLoadingProfile: "Fehler beim Laden des Nutzerprofils",
        missingDateRange: "Zeitraum fehlt",
        weekBalance: "Wochensaldo",
        monthBalance: "Monatssaldo",
        overallBalance: "Gesamtsaldo",
        noCorrections: "Keine Korrekturanträge vorhanden",
        dailyNoteSaved: "Tagesnotiz gespeichert.",
        dailyNoteError: "Fehler beim Speichern der Tagesnotiz.",
        weeklyHours: "Gesamtstunden (aktuelle Woche)",
        monthlyHours: "Gesamtstunden (Monat)",
        editNotes: "Notizen bearbeiten",
        addNotes: "Notizen hinzufügen",
        fillWorkTimesError: "Bitte Work Start und Work End ausfüllen",

        // ----------------------------------------------------------------------
        // AdminVacation (admin kalendar)
        // ----------------------------------------------------------------------
        adminVacation: {
            title: "Admin Urlaubs Kalender",
            adminPasswordLabel: "Admin-Passwort",
            createVacationButton: "Urlaub erstellen",
            modalTitle: "Urlaub",
            userSelection: "Benutzer Auswahl",
            selectUserPlaceholder: "Bitte Benutzer auswählen",
            startDateLabel: "Startdatum",
            endDateLabel: "Enddatum",
            halfDayLabel: "Halbtags Urlaub",
            confirmButton: "Bestätigen",
            cancelButton: "Abbrechen",
            noUserSelected: "Bitte einen Benutzer auswählen",
            datesMissing: "Bitte Start- und Enddatum angeben",
            adminPassMissing: "Bitte Admin-Passwort eingeben",
            createdSuccess: "Urlaub erfolgreich (admin) erstellt und direkt genehmigt",
            createError: "Fehler beim Anlegen des Admin-Urlaubs",
        },
        // ----------------------------------------------------------------------
        // vacation (normaler urlaubsflow)
        // ----------------------------------------------------------------------
        vacation: {
            missingDates: "Bitte Start- und Enddatum angeben.",
            invalidDates: "Enddatum darf nicht vor Startdatum liegen.",
            halfDayOneDay: "Halbtags gilt nur für einen einzigen Tag.",
            requestSuccess: "Urlaubsantrag eingereicht.",
            requestError: "Fehler beim Urlaubsantrag.",
            fullDay: "Ganztags",
            halfDay: "Halbtags",
            normalVacation: "Normaler Urlaub",
            overtimeVacation: "Überstundenfrei",
        },
        // ----------------------------------------------------------------------
        // PrintReport
        // ----------------------------------------------------------------------
        printReport: {
            title: "Zeitenbericht",
            userLabel: "User",
            periodLabel: "Zeitraum",
            date: "Datum",
            workStart: "Work-Start",
            breakStart: "Break-Start",
            breakEnd: "Break-End",
            workEnd: "Work-End",
            noEntries: "Keine Einträge",
            printButton: "Drucken",
            pdfButton: "PDF speichern",
        },
        // ----------------------------------------------------------------------
        // Landing Page
        // ----------------------------------------------------------------------
        landingPage: {
            headline: "Chrono-Logisch",
            subHeadline: "Zeiterfassung und NFC-Stempeln – ganz einfach.",
            loginButton: "Anmelden",
            registerButton: "Registrieren",
            whyTitle: "Warum Chrono-Logisch?",
            howItWorks: "So funktioniert's",
            featureNfcTitle: "NFC-Stempeln",
            featureNfcText: "Einfaches Ein- und Ausstempeln per NFC-Karte.",
            featureReportsTitle: "Automatische Berichte",
            featureReportsText: "Übersichtliche Auswertungen und PDF-Exports.",
            featureVacationTitle: "Urlaubsverwaltung",
            featureVacationText: "Urlaubstage im Blick, inkl. Genehmigungsprozess.",
            allRights: "Alle Rechte vorbehalten.",
            infoText: "Chrono-Logisch erkennt automatisch den richtigen Stempel, verhindert Dubletten und füllt vergessene Punch-Outs von selbst.",
            featuresTitle: "Alle Funktionen im Überblick",
            featuresSub: "Keine Gimmicks – nur Features, die deinen Alltag wirklich erleichtern.",
            ctaPrimary: "Kostenlos registrieren",
            featureSmartTitle: "Smart Punch",
            featureSmartText: "Erkennt automatisch Work Start, Break Start/End & Work End.",
            featureDirectTitle: "Direkter Punch",
            featureDirectText: "Vier feste Status gezielt wählbar – Übergangs-Logik inklusive.",
            featureDuplicateTitle: "Duplicate-Schutz",
            featureDuplicateText: "Einzigartiger DB-Index + Catch-Up-Logik verhindern Doppel-Clicks.",
            featureAutoPunchOutTitle: "Auto Punch Out",
            featureAutoPunchOutText: "Cron-Job 23:20 Uhr beendet vergessene Stempel automatisch.",
            featurePercentTitle: "Prozent-Punch",
            featurePercentText: "Teilzeit gibt Tages-% an – Ist/Soll-Delta sofort berechnet.",
            featureAdminTitle: "Korrekturen & Admin",
            featureAdminText: "Einträge editieren, ganzen Tag neu schreiben oder Notizen setzen.",
            featureHistoryTitle: "Berichte & Historie",
            featureHistoryText: "Tages-, Wochen-, Bereichs-Reports + vollständige Nutzer-History.",
            featureOvertimeTitle: "Überstunden-Mgmt",
            featureOvertimeText: "Persönliche Minuten-Balance auto-aktualisiert, Urlaub einbezogen.",
            stepsTitle: "In 3 Schritten startklar",
            step1Title: "Account anlegen",
            step1Text: "Firmen-Profil & Teams in wenigen Minuten.",
            step2Title: "NFC-Badges koppeln",
            step2Text: "Einmalig scannen, fertig.",
            step3Title: "Dashboard nutzen",
            step3Text: "Echtzeit-Insights & Abwesenheiten verwalten.",
            newsletterPlaceholder: "Deine E-Mail",
            newsletterButton: "Abonnieren",
        },

        // Falls du "search", "darkMode", "lightMode" usw. nutzt
        search: "Suchen",
        darkMode: "Dark Mode",
        lightMode: "Light Mode",
        impressum: "Impressum",
        agb: "AGB",
    },

    // =============== ENGLISCH ===============
    en: {
        // ----------------------------------------------------------------------
        // Login
        // ----------------------------------------------------------------------
        login: {
            title: "Welcome Back!",
            username: "Username",
            password: "Password",
            button: "Log in",
            languageLabel: "Language",
            error: "Login failed. Please check your credentials.",
            testStampMessage: "Test stamp executed",
            testStampButton: "Test Stamp",
            intro: "Sign in to continue.",
            noAccount: "No account yet?",
            registerHere: "Register here",
            waitMessage: "Please wait 1 minute before stamping again.",
            stamped: "Stamped",
        },
        // ----------------------------------------------------------------------
        // Admin
        // ----------------------------------------------------------------------
        admin: {
            changePasswordTitle: "Change Password",
            changePasswordButton: "Change Password",
        },
        // ----------------------------------------------------------------------
        // Register
        // ----------------------------------------------------------------------
        register: {
            title: "Register",
            username: "Username",
            password: "Password",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            button: "Register",
        },
        // ----------------------------------------------------------------------
        // Personal Data
        // ----------------------------------------------------------------------
        personalData: {
            title: "My Profile",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            saveButton: "Save",
            saved: "Data saved successfully.",
            changePassword: "Change Password",
            currentPassword: "Current Password",
            newPassword: "New Password",
            errorLoading: "Error loading profile data",
            errorUpdating: "Error updating profile",
            passwordChanged: "Password changed successfully",
            errorChangingPassword: "Error changing password",
        },
        // ----------------------------------------------------------------------
        // Admin Dashboard
        // ----------------------------------------------------------------------
        adminDashboard: {
            titleWeekly: "Admin Dashboard (Weekly View)",
            loggedInAs: "Logged in as",
            adminPassword: "Admin Password",
            userPassword: "User Password",
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
            statusLabel: "Status",
            button: {
                save: "Save",
                cancel: "Cancel",
                print: "Print Report",
            },
            printButton: "Print times",      // en
            vacationRequestsTitle: "Vacation Requests (All Users)",
            noVacations: "No vacation requests found",
            correctionRequestsTitle: "Correction Requests (All Users)",
            acceptButton: "Approve",
            rejectButton: "Reject",
            vacationCalendarTitle: "Vacation Calendar",
            forDate: "",
            monthlyOverview: "Monthly Overview",
            noEntriesThisMonth: "No entries for this month",
            startDate: "Start Date",
            endDate: "End Date",
            printReportTitle: "Generate Report",
            searchUser: "Search user...",
            noMatch: "No matching users found.",
            correctionItemLabel: "Correction for",
            noEntries: "No entries",
            newEntryButton: "Add Times",
            expandAll: "Expand All",
            collapseAll: "Collapse All",
            correctionModal: {
                approveTitle: "Approve Correction",
                denyTitle: "Deny Correction",
                commentLabel: "Comment for the user:",
                commentPlaceholder: "Why approve or deny?",
                confirmButton: "Confirm",
            },
        },
        // ----------------------------------------------------------------------
        // User Management
        // ----------------------------------------------------------------------
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
            workPercentage: "Work Percentage",
            annualVacationDays: "Vacation Days",
            button: {
                save: "Save",
                cancel: "Cancel",
            },
            table: {
                actions: "Actions",
                edit: "Edit",
                delete: "Delete",
                programCard: "Program Card",
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
            errorLoadingCorrections: "Error loading correction requests",

            percentageTracking: "Percentage-based tracking",
            deleteConfirmTitle: "Delete User",
            deleteConfirmMessage: "Do you really want to delete this user? (All data including times will be removed!)",
            nfcProgramStart: "Programming started. Please place the card...",
            programCardErrorTimeout: "Timeout while programming the card.",
            deleteConfirmConfirm: "Yes, delete",
            deleteConfirmCancel: "Cancel",
        },
        // ----------------------------------------------------------------------
        // Navbar
        // ----------------------------------------------------------------------
        navbar: {
            login: "Login",
            register: "Register",
            logout: "Logout",
            hi: "Hi",
            adminStart: "Admin Start",
            userManagement: "User Management",
            myDashboard: "My Dashboard",
            profile: "Profile",
            brightness: "brightness",
        },
        // ----------------------------------------------------------------------
        // Dashboard / corrections
        // ----------------------------------------------------------------------
        title: "My Dashboard",
        correctionRequests: "Correction Requests",
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
        remainingVacation: "Remaining Vacation",
        daysLabel: "days",
        vacationType: "Vacation Type",
        fullDay: "Full Day",
        halfDay: "Half Day",
        days: {
            monday: "Monday",
            tuesday: "Tuesday",
            wednesday: "Wednesday",
            thursday: "Thursday",
            friday: "Friday",
            saturday: "Saturday",
            sunday: "Sunday",
        },
        hourlyDashboard: {
            title: "Hourly Dashboard",
            mode: "Hourly",
        },
        dailyNotePlaceholder: "Daily note",
        printReportError: "Error generating report",
        loading: "Loading...",

        showWeeklyOnly: "Show current week only",
        showAll: "Show all",
        workStart: "Work Start",
        breakStart: "Break Start",
        breakEnd: "Break End",
        workEnd: "Work End",
        reason: "Reason",
        submitCorrectionRequest: "Request Correction",
        submitCorrectionFor: "Correction request for",
        submitCorrection: "Submit request",
        correctionSubmitSuccess: "Correction request submitted successfully.",
        correctionSubmitError: "Error submitting correction request.",
        errorLoadingProfile: "Error loading user profile",
        missingDateRange: "Date range missing",
        weekBalance: "Weekly Balance",
        monthBalance: "Monthly Balance",
        overallBalance: "Total Balance",
        noCorrections: "No correction requests found",
        dailyNoteSaved: "Daily note saved.",
        dailyNoteError: "Error saving daily note.",
        weeklyHours: "Total hours (current week)",
        monthlyHours: "Total hours (month)",
        editNotes: "Edit notes",
        addNotes: "Add notes",
        fillWorkTimesError: "Please fill in Work Start and Work End",

        // ----------------------------------------------------------------------
        // AdminVacation
        // ----------------------------------------------------------------------
        adminVacation: {
            title: "Admin Vacation Calendar",
            adminPasswordLabel: "Admin Password",
            createVacationButton: "Create Vacation",
            modalTitle: "Vacation",
            userSelection: "Select User",
            selectUserPlaceholder: "Please select user",
            startDateLabel: "Start Date",
            endDateLabel: "End Date",
            halfDayLabel: "Half-day Vacation",
            confirmButton: "Confirm",
            cancelButton: "Cancel",
            noUserSelected: "Please select a user",
            datesMissing: "Please provide start and end dates",
            adminPassMissing: "Please enter admin password",
            createdSuccess: "Vacation created (admin) and approved",
            createError: "Error creating admin vacation",
        },
        // ----------------------------------------------------------------------
        // Vacation
        // ----------------------------------------------------------------------
        vacation: {
            missingDates: "Please provide start and end date.",
            invalidDates: "End date cannot be before start date.",
            halfDayOneDay: "Half-day only valid for a single day.",
            requestSuccess: "Vacation request submitted.",
            requestError: "Error submitting vacation request.",
            fullDay: "Full day",
            halfDay: "Half day",
            normalVacation: "Normal vacation",
            overtimeVacation: "Overtime-based",
        },
        // ----------------------------------------------------------------------
        // PrintReport
        // ----------------------------------------------------------------------
        printReport: {
            title: "Time Report",
            userLabel: "User",
            periodLabel: "Period",
            date: "Date",
            workStart: "Work Start",
            breakStart: "Break Start",
            breakEnd: "Break End",
            workEnd: "Work End",
            noEntries: "No entries",
            printButton: "Print",
            pdfButton: "Save PDF",
        },
        // ----------------------------------------------------------------------
        // Landing Page
        // ----------------------------------------------------------------------
        landingPage: {
            headline: "Chrono-Logisch",
            subHeadline: "Time tracking and NFC stamping made easy.",
            ctaPrimary: "Register for free",
            loginButton: "Login",
            registerButton: "Register",
            whyTitle: "Why Chrono-Logisch?",
            howItWorks: "How it Works",
            featureNfcTitle: "NFC Stamping",
            infoText: "Chrono-Logisch automatically detects the correct punch, avoids duplicates and fills forgotten outs overnight.",
            featuresTitle: "All Features at a Glance",
            featuresSub: "No gimmicks – just features that really ease your day.",
            featureNfcText: "Simply clock in and out with an NFC card.",
            featureReportsTitle: "Automatic Reports",
            featureReportsText: "Clear evaluations and PDF exports.",
            featureVacationTitle: "Vacation Management",
            featureVacationText: "Keep track of vacation days, including approval workflow.",
            allRights: "All rights reserved.",
            featureSmartTitle: "Smart Punch",
            featureSmartText: "Automatically detects Work Start, Break Start/End and Work End.",
            featureDirectTitle: "Direct Punch",
            featureDirectText: "Four fixed statuses selectable with transition logic.",
            featureDuplicateTitle: "Duplicate Protection",
            featureDuplicateText: "Unique DB index and catch-up logic prevent double clicks.",
            featureAutoPunchOutTitle: "Auto Punch Out",
            featureAutoPunchOutText: "Cron job 23:20 ends forgotten punches automatically.",
            featurePercentTitle: "Percent Punch",
            featurePercentText: "Part time enters daily % – instant actual/target delta.",
            featureAdminTitle: "Corrections & Admin",
            featureAdminText: "Edit entries, rewrite whole days or add notes.",
            featureHistoryTitle: "Reports & History",
            featureHistoryText: "Daily, weekly, area reports plus full user history.",
            featureOvertimeTitle: "Overtime Mgmt",
            featureOvertimeText: "Personal minute balance auto updated, vacation included.",
            stepsTitle: "Ready in 3 steps",
            step1Title: "Create Account",
            step1Text: "Company profile & teams in minutes.",
            step2Title: "Link NFC badges",
            step2Text: "Scan once, done.",
            step3Title: "Use Dashboard",
            step3Text: "Realtime insights & absence management.",
            newsletterPlaceholder: "Your email",
            newsletterButton: "Subscribe",
        },

        // Falls du "search", "darkMode", "lightMode" etc. nutzt
        search: "Search",
        darkMode: "Dark Mode",
        lightMode: "Light Mode",
        impressum: "Impressum",
        agb: "Terms",
    },
};

export const LanguageContext = createContext();

export const LanguageProvider = ({ children }) => {
    // Standard: "de" oder "en" je nach Vorliebe
    const [language, setLanguage] = useState("de");

    /**
     * @param {string} key     z.B. "login.title"
     * @param {string} fallback (optional) Fallback-Text, wenn Key fehlt
     */
    const t = (key, fallback) => {
        const keys = key.split(".");
        let translation = translations[language];
        for (const k of keys) {
            if (translation[k] !== undefined) {
                translation = translation[k];
            } else {
                return fallback || key; // Falls gewünscht
            }
        }
        if (typeof translation === "object") {
            console.warn(
                `Translation for key "${key}" is an object. Expected a string.`
            );
            return JSON.stringify(translation);
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
