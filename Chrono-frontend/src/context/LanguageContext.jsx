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
        errors: {
            fetchHolidaysError: "Fehler beim Laden der Feiertage:",
            fetchSickLeaveError: "Fehler beim Laden der Krankmeldungen:",
            userNotLoaded: "Benutzer nicht geladen.",
            unknownError: "Unbekannter Fehler.",
            notLoggedIn: "Nicht eingeloggt.",
            fetchUsersError: "Fehler beim Laden der Benutzer.",
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
        // Landing
        // ----------------------------------------------------------------------
        landing: {
            hero: {
                badge: "Fair · Klar · Zuverlässig",
                title: "Zeit erfassen, fair abrechnen – einfach, klar, zuverlässig.",
                sub: "Chrono hilft Teams in der Schweiz & Deutschland, Zeiten korrekt zu erfassen, Urlaub sauber zu managen und Löhne sicher abzurechnen.",
                chips: {
                    server: "🇨🇭 Schweizer Server",
                    gdpr: "🔐 DSGVO-konform",
                    noExcel: "🧮 Kein Excel-Chaos",
                },
            },
            cta: {
                try: "Kostenlos testen",
                login: "Anmelden",
                demo: "Demo ansehen",
            },
            features: {
                title: "Alles drin, was du brauchst – ohne Ballast.",
                sub: "Fokussiert auf das Wesentliche: Zeiterfassung, Abrechnung, Urlaub und klare Admin-Prozesse.",
                items: [
                    {
                        title: "Arbeitszeit & Lohn in einem",
                        bullets: [
                            "Arbeitsstunden festhalten",
                            "Urlaub und Extra-Zeit sehen",
                            "Alles an einem Ort",
                        ],
                    },
                    {
                        title: "Lohnzettel für CH & DE",
                        bullets: [
                            "Als Datei speichern",
                            "Angaben leicht ändern",
                            "Schweiz und Deutschland abgedeckt",
                        ],
                    },
                    {
                        title: "Stempeln mit Karte oder Web",
                        bullets: [
                            "Mit Karte ein- und ausstempeln",
                            "Auch im Browser oder Handy",
                            "Geht sogar ohne Internet",
                        ],
                    },
                    {
                        title: "Urlaub & Überstunden im Blick",
                        bullets: [
                            "Urlaub online beantragen",
                            "Resttage sofort sehen",
                            "Überstunden automatisch verrechnet",
                        ],
                    },
                    {
                        title: "Hinweise & Erinnerungen",
                        bullets: [
                            "Info bei neuem Lohnzettel",
                            "Hinweis bei Anträgen",
                            "Erinnerung, wenn du vergisst auszustempeln",
                        ],
                    },
                    {
                        title: "Sichere Daten in der Schweiz",
                        bullets: [
                            "Daten bleiben in der Schweiz",
                            "Alles ist geschützt",
                            "Nur Berechtigte sehen deine Daten",
                            "Zusätzlicher Login-Schutz möglich",
                        ],
                    },
                    {
                        title: "Team & Projekte verwalten",
                        bullets: [
                            "Mitarbeitende hinzufügen",
                            "Kunden und Projekte anlegen",
                            "Alles im Blick behalten",
                        ],
                    },
                    {
                        title: "Berichte zum Mitnehmen",
                        bullets: [
                            "Übersichtliche Dateien herunterladen",
                            "Schnell sehen, wer wie viel gearbeitet hat",
                            "Praktisch für Steuer und Abrechnung",
                        ],
                    },
                ],
            },
            steps: {
                title: "So startest du in 3 Schritten",
                items: [
                    {
                        title: "Registrieren",
                        text: "Kostenlos starten – ohne Kreditkarte.",
                    },
                    {
                        title: "Team & Projekte anlegen",
                        text: "Mitarbeitende, Projekte und Kunden hinzufügen.",
                    },
                    {
                        title: "Loslegen",
                        text: "Stempeln, Urlaub beantragen, Abrechnung erstellen.",
                    },
                ],
            },
            contact: {
                title: "Kontakt aufnehmen",
                name: "Name",
                email: "E-Mail",
                msg: "Nachricht",
                placeholder: "Wie kann ich helfen?",
                send: "Senden",
                hint: "Antwort in der Regel innerhalb von 24h.",
                success: "Nachricht gesendet.",
                error: "Fehler beim Senden.",
            },
            footer: {
                imprint: "Impressum",
                privacy: "Datenschutz",
                terms: "AGB",
            },
            demoError: "Demo-Anmeldung fehlgeschlagen",
        },
        // ----------------------------------------------------------------------
        // Personal Data
        // ----------------------------------------------------------------------
        personalData: {
            title: "Mein Profil",
            firstName: "Vorname",
            lastName: "Nachname",
            email: "E-Mail",
            address: "Adresse",
            mobilePhone: "Handynummer",
            landlinePhone: "Festnetz (optional)",
            civilStatus: "Zivilstand",
            children: "Kinder",
            bankAccount: "Bankverbindung",
            emailNotifications: "E-Mail-Benachrichtigungen",
            saveButton: "Speichern",
            saved: "Daten erfolgreich gespeichert.",
            changePassword: "Passwort ändern",
            currentPassword: "Aktuelles Passwort",
            newPassword: "Neues Passwort",
            errorLoading: "Fehler beim Laden der Profildaten",
            errorUpdating: "Fehler beim Aktualisieren des Profils",
            passwordChanged: "Passwort erfolgreich geändert",
            errorChangingPassword: "Fehler beim Ändern des Passworts",
            calendarFeed: "Kalender-Feed",
            calendarFeedInfo: "Nutze diese URL, um deinen Kalender zu abonnieren.",
            exportButton: "Exportieren",
            exportModalTitle: "Kalender exportieren",
            exportGoogle: "In Google Calendar öffnen",
            exportOutlookApple: "In Outlook/Apple abonnieren",
            exportDownload: "ICS-Datei herunterladen",
            exportCopyLink: "Link kopieren",
            copyLink: "Link kopieren",
            linkCopied: "Link kopiert",
        },
        // ----------------------------------------------------------------------
        // Company Settings
        // ----------------------------------------------------------------------
        companySettings: {
            title: "Firmenparameter",
            save: "Speichern",
            saved: "Einstellungen gespeichert",
            saveError: "Fehler beim Speichern"
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
            correctionApprovedMsg: "Korrektur genehmigt",
            correctionDeniedMsg: "Korrektur abgelehnt",
            correctionErrorMsg: "Fehler bei Korrektur",
            correctionsTitle: "Korrekturanträge",
            searchUserPlaceholder: "Suche nach Benutzer oder Datum...",
            noVacationRequests: "Keine Urlaubsanträge gefunden.",
            statusApproved: "Genehmigt",
            statusDenied: "Abgelehnt",
            statusPending: "Ausstehend",
            approveButtonTitle: "Urlaubsantrag genehmigen",
            approveButton: "Genehmigen",
            rejectButtonTitle: "Urlaubsantrag ablehnen",
            vacationApprovedMsg: "Urlaub genehmigt.",
            vacationApproveErrorMsg: "Fehler beim Genehmigen des Urlaubs: ",
            vacationDeniedMsg: "Urlaub abgelehnt.",
            vacationDenyErrorMsg: "Fehler beim Ablehnen des Urlaubs: ",
            noValidDateOrUser: "Kein gültiges Datum oder Benutzer ausgewählt.",
            editSuccessfulMsg: "Zeiten erfolgreich bearbeitet.",
            importTimeTrackingButton: "Zeiten importieren",
            reloadDataButton: "Daten neu laden",
            sickLeaveDeleteSuccess: "Krankmeldung erfolgreich gelöscht.",
            holidayOptionUpdateSuccess: "Feiertagsoption erfolgreich aktualisiert.",
            jumpToDate: "Datum auswählen",
            allVisibleUsersHiddenOrNoData: "Alle sichtbaren Benutzer sind ausgeblendet oder es sind keine Daten für die aktuelle Woche vorhanden.",
            noUserDataForWeek: "Keine Benutzerdaten für diese Woche.",
            holidayOptionLabel: "Option:",
            holidayOption: {
                pending: "Ausstehend",
                deduct: "Soll reduzieren",
                doNotDeduct: "Soll nicht reduzieren",
            },
            needsCorrectionTooltip: "Automatisch beendet und unkorrigiert",
            entrySource: {
                autoSuffix: " (Auto)",
                adminSuffix: " (AdmK)",
                userSuffix: " (UsrK)",
                importSuffix: " (Imp)",
            },
            deleteSickLeaveTitle: "Krankmeldung löschen",
            holidayOptionPendingTooltip: "Feiertagsoption ausstehend",
            deleteSickLeaveConfirmTitle: "Krankmeldung löschen bestätigen",
            deleteSickLeaveConfirmMessage: "Möchten Sie die Krankmeldung für",
            deleteSickLeaveIrreversible: " wirklich löschen? Das Tagessoll und der Saldo werden neu berechnet.",
            printUserTimesTitle: "Zeiten drucken für",
            searchByUser: "Nach Benutzer suchen…",
            resetFilters: "Filter zurücksetzen",
            statusAPPROVED: "Genehmigt",
            statusDENIED: "Abgelehnt",
            statusPENDING: "Ausstehend",
            correctionRequestFor: "Antrag für",
            adminComment: "Admin-Kommentar",
            onVacation: "Im Urlaub",
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
            address: "Adresse",
            birthDate: "Geburtsdatum",
            entryDate: "Eintrittsdatum",
            country: "Land",
            email: "E-Mail",
            mobilePhone: "Handynummer",
            landlinePhone: "Festnetz (optional)",
            role: "Rolle",
            taxClass: "Steuerklasse",
            department: "Abteilung",
            tarifCode: "Tarifcode",
            canton: "Kanton",
            civilStatus: "Zivilstand",
            children: "Kinder",
            religion: "Religion",
            healthInsurance: "Krankenkasse",
            bankAccount: "Bankverbindung",
            socialSecurityNumber: "AHV-Nr.",
            personnelNumber: "Personalnummer",
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
            deleteConfirmMessage: "Soll der Benutzer wirklich gelöscht werden? Der Benutzer wird deaktiviert, und seine Daten bleiben bis zu einem Jahr gespeichert, bevor sie endgültig gelöscht werden.",
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
            customerManagement: "Kunden",
            projectManagement: "Projekte",
            companyManagement: "Firmen",
            payslips: "Abrechnungen",
            schedulePlanner: "Dienstplan",
            knowledge: "Dokumente",
            payments: "Zahlungen",
            companySettings: "Firmeneinstellungen",
            myDashboard: "Mein Dashboard",
            chatbot: "Chatbot",
            profile: "Profil",
            whatsNew: "Was ist neu?",
            history: "Update-Verlauf",
            brightness: "Helligkeit",
            languageLabel: "Sprache",
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
        noCustomer: "Kein Kunde",
        noProject: "Kein Projekt",
        recentCustomers: "Zuletzt verwendet",
        customerSaved: "Kunde gespeichert",
        customerSaveError: "Fehler beim Speichern des Kunden",
        projectSaveError: "Fehler beim Speichern des Projekts",
        customerLabel: "Kunde",
        projectLabel: "Projekt",
        applyForDay: "Speichern",
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
        overtimeBalance: "Überstundenkonto",
        overtimeBalanceInfo: "Summe der Überstunden abzüglich genutzter Stunden.",
        noCorrections: "Keine Korrekturanträge vorhanden",
        dailyNoteSaved: "Tagesnotiz gespeichert.",
        dailyNoteError: "Fehler beim Speichern der Tagesnotiz.",
        weeklyHours: "Gesamtstunden (aktuelle Woche)",
        monthlyHours: "Gesamtstunden (Monat)",
        estimatedEarnings: "Gesch\u00e4tzter Verdienst",
        expectedWeekInfo: "Ihr Wochenziel basiert auf Ihrem Arbeitspensum und reduziert sich bei Abwesenheiten.",
        editNotes: "Notizen bearbeiten",
        addNotes: "Notizen hinzufügen",
        fillWorkTimesError: "Bitte Work Start und Work End ausfüllen",

        onVacation: "Im Urlaub",
        enterNotePlaceholder: "Notiz eingeben...",
        dailyNoteTitle: "Notiz",
        noNotePlaceholder: "Keine Notiz.",
        editNote: "Notiz bearbeiten",
        save: "Speichern",
        noTask: "Keine Aufgabe",
        assignCustomer: {
            editButton: "Kunden & Zeiten bearbeiten",
        },
        correction: {
            desiredChange: "Gewünschte Änderung",
            type: "Typ",
            time: "Zeit",
        },
        punchTypes: {
            START: "Start",
            ENDE: "Ende",
        },
        currentWeek: "Aktuelle Woche",

        // fehlende Übersetzungen
        actualTime: "Ist",
        breakTime: "Pause",
        start: "Start",
        end: "Ende",
        worked: "Geleistet",
        pause: "Pause",
        overtime: "Überstunden",
        total: "Gesamt",
        period: "Zeitraum",
        timeReportFor: "Zeitbericht für",
        correctionFor: "Korrektur für",
        welcome: "Willkommen",
        "errors.reportError": "Fehler beim Generieren des Berichts",
        userDashboard: {
            correctionSuccess: "Korrekturantrag erfolgreich eingereicht.",
        },
        adminCorrections: {
            header: {
                user: "Benutzer",
                date: "Antragsdatum",
                request: "Anfrage",
                reason: "Grund",
                status: "Status",
                actions: "Aktionen",
            },
        },
        hourlyDashboard: {
            title: "Stundenbasierte Ansicht",
            mode: "Stundenbasiert",
            addEntryFirst: "Bitte mindestens einen Korrektureintrag hinzufügen.",
            userNotFound: "Benutzer nicht gefunden, bitte erneut anmelden.",
        },

        percentageDashboard: {
            title: "Prozent-Dashboard",
            workPercentageLabel: "Arbeits-%",
        },

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
            endDateBeforeStart: "Das Enddatum darf nicht vor dem Startdatum liegen.",
            invalidOvertimeHours: "Bitte eine gültige positive Stundenzahl für den Überstundenabzug eingeben.",
            overtimeDeductionHoursLabel: "Abzuziehende Überstunden (in Stunden):",
            hoursPlaceholder: "z.B. 4 oder 8.5",
            usesOvertimeLabel: "Überstunden nutzen",
            halfDayDeductionNotice: "Hinweis: Für diesen halben Tag die entsprechenden Stunden für den halben Tag eintragen.",
            unknownUser: "Unbekannt",
            halfDayShort: "½",
            overtimeVacationShort: "ÜS",
            companyVacationLabel: "Betriebsurlaub",
            endDateLabel: "Enddatum",
            halfDayLabel: "Halbtags Urlaub",
            confirmButton: "Bestätigen",
            cancelButton: "Abbrechen",
            noUserSelected: "Bitte einen Benutzer auswählen",
            datesMissing: "Bitte Start- und Enddatum angeben",
            adminPassMissing: "Bitte Admin-Passwort eingeben",
            createdSuccess: "Urlaub erfolgreich (admin) erstellt und direkt genehmigt",
            createError: "Fehler beim Anlegen des Admin-Urlaubs",
            delete: {
                noSelection: "Kein Urlaub zum Löschen ausgewählt.",
                success: "Urlaubsantrag erfolgreich gelöscht.",
                error: "Fehler beim Löschen des Urlaubsantrags:",
                buttonTitle: "Urlaubsantrag löschen",
                confirmTitle: "Urlaub löschen bestätigen",
                confirmMessage: "Möchten Sie den Urlaubsantrag von",
                irreversible: "wirklich unwiderruflich löschen?",
                overtimeReversalInfo: "Bei genehmigten Überstundenurlauben werden die abgezogenen Stunden dem Benutzerkonto wieder gutgeschrieben.",
                regularVacationInfo: "Dies ist ein regulärer Urlaub. Die Tage werden dem Jahresurlaubskonto wieder gutgeschrieben (effektiv durch Neuberechnung der Resturlaubstage).",
                confirmDeleteButton: "Ja, löschen"
            },
        },
        adminCalendar: {
            title: "Admin Kalenderübersicht",
        },
        adminSickLeave: {
            modalTitle: "Krankheit für Benutzer melden",
            userSelection: "Benutzer Auswahl",
            selectUserPlaceholder: "Bitte Benutzer auswählen",
            startDateLabel: "Startdatum",
            endDateLabel: "Enddatum",
            halfDayLabel: "Halbtags krank",
            reportButton: "Krankheit melden",
            reportButtonModal: "Krankmeldung speichern",
            noUserSelected: "Bitte einen Benutzer auswählen",
            datesMissing: "Bitte Start- und Enddatum für die Krankmeldung angeben",
            endDateBeforeStart: "Das Enddatum der Krankheit darf nicht vor dem Startdatum liegen.",
            reportSuccess: "Krankmeldung erfolgreich eingetragen.",
            reportError: "Fehler beim Eintragen der Krankmeldung:",
            unknownUser: "Unbekannt",
            halfDayShort: "½",
        },
        calendarLocale: "de-DE",
        requestVacationButton: "Urlaub beantragen",
        vacationModalTitle: "Urlaubsantrag stellen",
        fromDate: "Von",
        toDate: "Bis",
        dayScope: "Zeitraum",
        vacationType: "Urlaubsart",
        submitButton: "Absenden",
        cancelButton: "Abbrechen",
        comment: "Kommentar (optional)",
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
        sickLeave: {
            modalTitle: "Krankheit melden",
            halfDayLabel: "Halbtags",
            commentPlaceholder: "Grund, Arztbesuch etc.",
            fullDay: "Ganztags krank",
            halfDay: "Halbtags krank",
            halfDayOneDay: "Halbtägige Krankmeldung nur für einen Tag.",
            missingDates: "Bitte Start- und Enddatum angeben.",
            invalidDates: "Enddatum darf nicht vor Startdatum liegen.",
            reportButtonShort: "Krank melden",
            reportSuccess: "Krankmeldung eingereicht.",
            reportError: "Fehler beim Einreichen der Krankmeldung.",
            sickShort: "K",
            sick: "Krank",
        },
        // ----------------------------------------------------------------------
        // PrintReport
        // ----------------------------------------------------------------------
        printReport: {
            title: "Zeitenbericht",
            userLabel: "User",
            periodLabel: "Zeitraum",
            summaryWork: "Gesamte Arbeitszeit",
            summaryBreak: "Gesamte Pausenzeit",
            date: "Datum",
            workStart: "Work-Start",
            breakStart: "Break-Start",
            breakEnd: "Break-End",
            workEnd: "Work-End",
            pause: "Pause",
            total: "Arbeit",
            punches: "Stempelungen",
            note: "Notiz",
            overview: "Übersicht",
            worked: "Gearbeitet",
            blocks: "Arbeitsblöcke",
            workLabel: "Arbeit",
            breaks: "Pausen",
            open: "OFFEN",
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
            newsletterTitle: "Bleib informiert!",
            newsletterText: "Updates & Tipps direkt in dein Postfach.",
            newsletterPlaceholder: "Deine E-Mail",
            newsletterButton: "Abonnieren",
            contactTitle: "Kontakt",
            contactName: "Name",
            contactEmail: "E-Mail",
            contactMessage: "Nachricht",
            contactButton: "Absenden",
            contactSuccess: "Nachricht gesendet!",
            contactError: "Fehler beim Senden.",
        },

        // Falls du "search", "darkMode", "lightMode" usw. nutzt
        search: "Suchen",
        darkMode: "Dark Mode",
        lightMode: "Light Mode",
        page: "Seite",
        of: "von",
        for: "für",
        ok: "OK",
        done: "Erledigt",
        sessionExpired: "Session abgelaufen. Bitte erneut einloggen.",
        impressum: "Impressum",
        agb: "AGB",
        instagram: "Instagram",
        changelogModal: {
            whatsNew: "Was ist neu in Version",
            published: "Veröffentlicht am",
            close: "Schließen",
        },
        whatsNewPage: {
            title: "Alle Änderungen und Updates",
            loading: "Lade Verlauf...",
        },
        payslips: {
            title: "Meine Gehaltsabrechnungen",
            pendingTitle: "Offene Gehaltsabrechnungen",
            approvedTitle: "Freigegebene Gehaltsabrechnungen",
            approve: "Freigeben",
            delete: "Löschen",
            deleteConfirm: "Abrechnung wirklich löschen?",
            reopen: "Zurückziehen",
            approveAll: "Alle freigeben",
            exportCsv: "CSV Export",
            backup: "Backup",
            print: "Drucken",
            period: "Zeitraum",
            user: "Benutzer",
            gross: "Brutto",
            net: "Netto",
            payoutDate: "Auszahlungsdatum",
            filterName: "Name",
            start: "Startdatum",
            end: "Enddatum",
            filter: "Filtern",
            printError: "PDF konnte nicht geladen werden",
            saveLogo: "Logo speichern",
            logoSaved: "Logo gespeichert",
            logoSaveError: "Fehler beim Speichern",
            employerContrib: "Arbeitgeberbeiträge",
            employerTotal: "Summe",
            scheduleDay: "Automatisch am Tag",
            scheduleButton: "Planen",
            scheduleAll: "Automatische Abrechnung für alle aktivieren",
            editPayout: "Datum ändern",
            enterPayoutDate: "Auszahlungsdatum eingeben",
            generateManual: "Manuell erstellen",
            selectUser: "Benutzer wählen",
            payoutOvertime: "Überstunden auszahlen",
            overtimeHours: "Überstunden (Std.)",
            generate: "Erstellen",
            logoUploadTitle: "Logo hochladen",
            selectFile: "Datei wählen",
            myPayslips: "Meine Lohnabrechnungen"

        },
        schedulePlanner: {
            title: "Dienstplan",
            auto: "Automatisch",
            save: "Speichern",
            prevWeek: "Vorherige Woche",
            nextWeek: "Nächste Woche",
            copyWeeks: "Kopieren",
            weekShort: "KW",
            userOnVacation: "Dieser Mitarbeiter ist an diesem Tag im Urlaub"

        },
        knowledge: {
            managementTitle: "Wissensdokumente",
            createTitle: "Neues Dokument",
            titleLabel: "Titel",
            contentLabel: "Inhalt",
            accessLabel: "Zugriff",
            accessAll: "Alle",
            accessAdmin: "Nur Admins",
            listTitle: "Dokumente",
            createSuccess: "Dokument gespeichert",
            createError: "Fehler beim Speichern",
            deleteConfirm: "Dokument wirklich löschen?",
            deleteError: "Fehler beim Löschen",
            noDocs: "Keine Dokumente"
        },
        quickStart: {
            title: "Quick Start",
            profile: "Profil ausfüllen",
            punch: "Erste Zeiterfassung",
            vacation: "Urlaub beantragen",
            progress: "erledigt",
        },
        impressumPage: {
            title: "Impressum",
            address: "<strong>Chrono-Logisch</strong><br />Einzelunternehmen<br />Inhaber: Christopher Siefert<br />Lettenstrasse 20<br />CH-9122 Mogelsberg",
            contact: "Telefon: <a href=\"tel:+41765467960\">+41 76 546 79 60</a><br />E-Mail: <a href=\"mailto:siefertchristopher@chrono-logisch.ch\">siefertchristopher@chrono-logisch.ch</a>",
            responsible: "Verantwortlich für den Inhalt dieser Website: <br />Christopher Siefert (Inhaber)",
            liability: "<strong>Haftungsausschluss:</strong><br />Für die Inhalte externer Links übernehmen wir keine Haftung. Für den Inhalt der verlinkten Seiten sind ausschließlich deren Betreiber verantwortlich.",
            copyright: "<strong>Copyright:</strong><br />Sämtliche Inhalte (Texte, Bilder, Grafiken) auf dieser Website sind urheberrechtlich geschützt. Jegliche Nutzung ohne ausdrückliche Zustimmung ist untersagt.",
            stand: "<em>Stand: Mai 2025</em>",
        },
        privacyPage: {
            title: "Datenschutzerklärung für Chrono",
            content: `
                <p><strong>Stand: 10. September 2025</strong></p>
                <p>Wir freuen uns über Ihr Interesse an unserer Zeiterfassungsanwendung Chrono. Der Schutz Ihrer persönlichen Daten ist uns ein wichtiges Anliegen. Nachfolgend informieren wir Sie ausführlich über den Umgang mit Ihren Daten.</p>
                <h2>1. Verantwortliche Stelle</h2>
                <p>Verantwortlich für die Datenerhebung, -verarbeitung und -nutzung im Sinne der Datenschutz-Grundverordnung (DSGVO) ist:<br/>
                <strong>Chrono</strong><br/>
                Lettenstrasse 20<br/>
                9122 Mogelsberg<br/>
                Schweiz</p>
                <p><strong>E-Mail:</strong> siefertchristopher@chrono-logisch.ch<br/>
                <strong>Telefon:</strong> +41 764699122</p>
                <p>Weitere Informationen finden Sie in unserem <a href="/impressum">Impressum</a>.</p>
                <h2>2. Art, Zweck und Umfang der Datenverarbeitung</h2>
                <h3>a) Bei Besuch der Webseite</h3>
                <p>Bei jedem Aufruf unserer Webseite erfasst unser System automatisiert Daten wie Browsertyp, Betriebssystem, Referrer URL, Hostname des zugreifenden Rechners, Uhrzeit der Serveranfrage und IP-Adresse. Diese Daten sind technisch erforderlich, um die Anwendung anzuzeigen und die Stabilität und Sicherheit zu gewährleisten.</p>
                <h3>b) Bei Registrierung und Nutzung eines Benutzerkontos</h3>
                <p>Zur Nutzung von Chrono müssen Sie sich registrieren. Dabei erheben wir Daten wie Benutzername, E-Mail-Adresse, verschlüsseltes Passwort sowie weitere optionale Profildaten. Diese Daten sind zur Verwaltung des Benutzerkontos und zur Vertragserfüllung erforderlich.</p>
                <h3>c) Im Rahmen der Zeiterfassung und Arbeitsorganisation</h3>
                <p>Chrono verarbeitet Arbeitszeiten, Projektzuordnungen, Korrekturanträge, NFC-Kartendaten sowie Krankmeldungen und Urlaubsanträge. Die Verarbeitung dient der Vertragserfüllung.</p>
                <h3>d) Im Rahmen der Lohnabrechnung</h3>
                <p>Sofern das Lohnmodul genutzt wird, verarbeiten wir Gehaltsdaten, Steuerinformationen und Bankverbindungen. Diese Daten werden vertraulich behandelt.</p>
                <h3>e) Bei Nutzung des KI-Chatbots und der Wissensdatenbank</h3>
                <p>Anfragen an den Chatbot werden verarbeitet, um passende Antworten zu liefern und den Dienst zu verbessern.</p>
                <h3>f) Bei Kontaktaufnahme</h3>
                <p>Übermittelte Daten (z.B. Name, E-Mail, Inhalt der Anfrage) werden zur Bearbeitung gespeichert.</p>
                <h2>3. Cookies und Lokaler Speicher</h2>
                <p>Wir verwenden lokalen Speicher, um Einstellungen wie Sprache oder Theme zu speichern. Diese Informationen werden nicht an unsere Server übertragen.</p>
                <h2>4. Weitergabe von Daten an Dritte</h2>
                <p>Eine Weitergabe erfolgt nur, wenn dies gesetzlich vorgeschrieben ist oder zur Vertragserfüllung notwendig wird, etwa an Zahlungsdienstleister oder nach Zustimmung an Google Calendar. Mit allen Dienstleistern bestehen Auftragsverarbeitungsverträge.</p>
                <h2>5. Ihre Rechte als betroffene Person</h2>
                <p>Sie haben das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Widerspruch, Datenübertragbarkeit sowie ein Beschwerderecht bei einer Aufsichtsbehörde.</p>
                <h2>6. Datensicherheit</h2>
                <p>Wir treffen technische und organisatorische Maßnahmen, um Ihre Daten vor Verlust oder unbefugtem Zugriff zu schützen und verbessern diese laufend.</p>
                <h2>7. Änderung dieser Datenschutzerklärung</h2>
                <p>Wir behalten uns vor, diese Erklärung anzupassen, damit sie den aktuellen rechtlichen Anforderungen entspricht oder Änderungen unserer Leistungen umzusetzen.</p>
            `,
        },
        agbPage: {
            title: "Allgemeine Geschäftsbedingungen (AGB) für Chrono",
            content: `
                <p><strong>Stand: 10. September 2025</strong></p>
                <h2>1. Geltungsbereich</h2>
                <p>Diese Allgemeinen Geschäftsbedingungen (AGB) regeln das Vertragsverhältnis zwischen Chrono und den Nutzern der SaaS-Anwendung "Chrono". Abweichende Bedingungen des Kunden werden nicht anerkannt, es sei denn, der Anbieter stimmt ihrer Geltung ausdrücklich zu.</p>
                <h2>2. Leistungsgegenstand</h2>
                <p>Der Anbieter stellt die Software zur Zeiterfassung, Projektverwaltung, Urlaubs- und Abwesenheitsplanung sowie optional zur Lohnabrechnung über das Internet bereit. Der Funktionsumfang richtet sich nach dem gebuchten Leistungspaket. Der Anbieter kann Funktionen ändern, erweitern oder einschränken, solange die Kernfunktionalität erhalten bleibt.</p>
                <h2>3. Vertragsschluss und Testphase</h2>
                <p>Der Vertrag kommt durch Online-Registrierung und Annahme durch den Anbieter zustande. Kostenlos angebotene Testphasen gehen nicht automatisch in ein kostenpflichtiges Abo über.</p>
                <h2>4. Preise und Zahlungsbedingungen</h2>
                <p>Die Vergütung richtet sich nach der aktuellen Preisliste. Nutzungsgebühren sind je nach Abrechnungszeitraum im Voraus fällig. Bei Zahlungsverzug kann der Anbieter den Zugang sperren.</p>
                <h2>5. Pflichten des Kunden</h2>
                <p>Der Kunde muss seine Zugangsdaten geheim halten und ist für Aktivitäten über sein Konto verantwortlich. Er stellt sicher, dass eingegebene Daten keine Rechte Dritter verletzen.</p>
                <h2>6. Datenschutz und Datensicherheit</h2>
                <p>Der Anbieter verarbeitet personenbezogene Daten ausschließlich zur Vertragserfüllung unter Beachtung gesetzlicher Vorschriften. Weitere Informationen enthält die <a href="/datenschutz">Datenschutzerklärung</a>.</p>
                <h2>7. Verfügbarkeit und Wartung</h2>
                <p>Der Anbieter gewährleistet eine branchenübliche Verfügbarkeit und kann geplante Wartungsarbeiten durchführen, die nach Möglichkeit angekündigt werden.</p>
                <h2>8. Haftung</h2>
                <p>Der Anbieter haftet unbeschränkt bei Vorsatz oder grober Fahrlässigkeit sowie bei Verletzung von Leben, Körper oder Gesundheit. Bei leichter Fahrlässigkeit ist die Haftung auf typische vorhersehbare Schäden begrenzt.</p>
                <h2>9. Vertragslaufzeit und Kündigung</h2>
                <p>Der Vertrag läuft auf unbestimmte Zeit und kann vom Kunden jederzeit zum Ende des Abrechnungszeitraums gekündigt werden. Der Anbieter kann mit einer Frist von 30 Tagen kündigen.</p>
                <h2>10. Schlussbestimmungen</h2>
                <p>Es gilt Schweizer Recht. Gerichtsstand ist der Sitz des Anbieters. Unwirksame Bestimmungen berühren die Wirksamkeit der übrigen Bestimmungen nicht.</p>
            `,
        },
        registrationPage: {
            pricingTitle: "Pakete & Preisübersicht",
            pricingIntro: "Wählen Sie das Paket, das am besten passt.",
            monthlyBilling: "Monatliche Zahlung",
            yearlyBilling: "Jährliche Zahlung (2 Monate geschenkt!)",
            employeeCount: "Anzahl Mitarbeiter (1-100):",
            trainingTitle: "Optionales Add-on:",
            addTraining: "Zusätzliches Intensiv-Onboarding (1 Std. individuell)",
            footnote: "Alle Preise zzgl. MwSt.",
            sendRequest: "Registrierungsanfrage senden",
            sending: "Anfrage wird gesendet...",
            thanks: "Vielen Dank für Ihre Anfrage!",
            backHome: "Zurück zur Startseite"

        },
        notFound: {
            pageNotFound: "404 - Seite nicht gefunden",
        },


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
        errors: {
            fetchHolidaysError: "Error loading holidays:",
            fetchSickLeaveError: "Error loading sick leaves:",
            userNotLoaded: "User not loaded.",
            unknownError: "Unknown error.",
            notLoggedIn: "Not logged in.",
            fetchUsersError: "Error loading users.",
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
        // Landing
        // ----------------------------------------------------------------------
        landing: {
            hero: {
                badge: "Fair · Clear · Reliable",
                title: "Track time, invoice fairly – simple, clear, reliable.",
                sub: "Chrono helps teams in Switzerland & Germany track time accurately, manage vacation cleanly and process payroll securely.",
                chips: {
                    server: "🇨🇭 Swiss servers",
                    gdpr: "🔐 GDPR compliant",
                    noExcel: "🧮 No Excel chaos",
                },
            },
            cta: {
                try: "Try for free",
                login: "Log in",
                demo: "View demo",
            },
            features: {
                title: "Everything you need – no clutter.",
                sub: "Focused on the essentials: time tracking, payroll, vacation and clear admin processes.",
                items: [
                    {
                        title: "Time tracking & payroll in one",
                        bullets: [
                            "Record working hours",
                            "See vacation and overtime",
                            "Everything in one place",
                        ],
                    },
                    {
                        title: "Payslips for CH & DE",
                        bullets: [
                            "Save as file",
                            "Easily adjust details",
                            "Covers Switzerland and Germany",
                        ],
                    },
                    {
                        title: "Stamp via card or web",
                        bullets: [
                            "Clock in/out with card",
                            "Also in browser or phone",
                            "Even works offline",
                        ],
                    },
                    {
                        title: "Vacation & overtime at a glance",
                        bullets: [
                            "Apply for vacation online",
                            "See remaining days instantly",
                            "Overtime deducted automatically",
                        ],
                    },
                    {
                        title: "Notifications & reminders",
                        bullets: [
                            "Info on new payslip",
                            "Hint on requests",
                            "Reminder if you forget to clock out",
                        ],
                    },
                    {
                        title: "Secure data in Switzerland",
                        bullets: [
                            "Data stays in Switzerland",
                            "Everything is protected",
                            "Only authorized people see your data",
                            "Optional extra login protection",
                        ],
                    },
                    {
                        title: "Manage team & projects",
                        bullets: [
                            "Add employees",
                            "Create customers and projects",
                            "Keep everything in view",
                        ],
                    },
                    {
                        title: "Reports to go",
                        bullets: [
                            "Download clear files",
                            "Quickly see who worked how much",
                            "Useful for taxes and payroll",
                        ],
                    },
                ],
            },
            steps: {
                title: "Get started in 3 steps",
                items: [
                    {
                        title: "Register",
                        text: "Start for free – no credit card needed.",
                    },
                    {
                        title: "Set up team & projects",
                        text: "Add employees, projects and customers.",
                    },
                    {
                        title: "Get going",
                        text: "Clock time, request vacation, create payroll.",
                    },
                ],
            },
            contact: {
                title: "Get in touch",
                name: "Name",
                email: "Email",
                msg: "Message",
                placeholder: "How can I help?",
                send: "Send",
                hint: "Usually replies within 24h.",
                success: "Message sent.",
                error: "Error sending message.",
            },
            footer: {
                imprint: "Imprint",
                privacy: "Privacy",
                terms: "Terms",
            },
            demoError: "Demo login failed",
        },
        // ----------------------------------------------------------------------
        // Personal Data
        // ----------------------------------------------------------------------
        personalData: {
            title: "My Profile",
            firstName: "First Name",
            lastName: "Last Name",
            email: "Email",
            address: "Address",
            mobilePhone: "Mobile Phone",
            landlinePhone: "Landline (optional)",
            civilStatus: "Marital Status",
            children: "Children",
            bankAccount: "Bank Account",
            emailNotifications: "Email Notifications",
            saveButton: "Save",
            saved: "Data saved successfully.",
            changePassword: "Change Password",
            currentPassword: "Current Password",
            newPassword: "New Password",
            errorLoading: "Error loading profile data",
            errorUpdating: "Error updating profile",
            passwordChanged: "Password changed successfully",
            errorChangingPassword: "Error changing password",
            calendarFeed: "Calendar Feed",
            calendarFeedInfo: "Use this URL to subscribe to your calendar.",
            exportButton: "Export",
            exportModalTitle: "Export calendar",
            exportGoogle: "Open in Google Calendar",
            exportOutlookApple: "Subscribe in Outlook/Apple",
            exportDownload: "Download ICS file",
            exportCopyLink: "Copy link",
            copyLink: "Copy link",
            linkCopied: "Link copied",
        },
        // ----------------------------------------------------------------------
        // Company Settings
        // ----------------------------------------------------------------------
        companySettings: {
            title: "Company Parameters",
            save: "Save",
            saved: "Settings saved",
            saveError: "Error saving settings"
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
            correctionApprovedMsg: "Correction approved",
            correctionDeniedMsg: "Correction denied",
            correctionErrorMsg: "Error during correction",
            correctionsTitle: "Correction Requests",
            searchUserPlaceholder: "Search by user or date...",
            noVacationRequests: "No vacation requests found.",
            statusApproved: "Approved",
            statusDenied: "Denied",
            statusPending: "Pending",
            approveButtonTitle: "Approve vacation request",
            approveButton: "Approve",
            rejectButtonTitle: "Reject vacation request",
            vacationApprovedMsg: "Vacation approved.",
            vacationApproveErrorMsg: "Error approving vacation: ",
            vacationDeniedMsg: "Vacation denied.",
            vacationDenyErrorMsg: "Error denying vacation: ",
            noValidDateOrUser: "No valid date or user selected.",
            editSuccessfulMsg: "Times edited successfully.",
            importTimeTrackingButton: "Import times",
            reloadDataButton: "Reload data",
            sickLeaveDeleteSuccess: "Sick leave deleted successfully.",
            holidayOptionUpdateSuccess: "Holiday option updated successfully.",
            jumpToDate: "Select date",
            allVisibleUsersHiddenOrNoData: "All visible users are hidden or no data for the current week.",
            noUserDataForWeek: "No user data for this week.",
            holidayOptionLabel: "Option:",
            holidayOption: {
                pending: "Pending",
                deduct: "Deduct from target",
                doNotDeduct: "Do not deduct",
            },
            needsCorrectionTooltip: "Automatically ended and uncorrected",
            entrySource: {
                autoSuffix: " (Auto)",
                adminSuffix: " (AdmC)",
                userSuffix: " (UsrC)",
                importSuffix: " (Imp)",
            },
            deleteSickLeaveTitle: "Delete sick leave",
            holidayOptionPendingTooltip: "Holiday option pending",
            deleteSickLeaveConfirmTitle: "Confirm delete sick leave",
            deleteSickLeaveConfirmMessage: "Do you want to delete the sick leave for",
            deleteSickLeaveIrreversible: "? This will recalculate daily target and balance.",
            printUserTimesTitle: "Print times for",
            searchByUser: "Search by user…",
            resetFilters: "Reset filters",
            statusAPPROVED: "Approved",
            statusDENIED: "Denied",
            statusPENDING: "Pending",
            correctionRequestFor: "Request for",
            adminComment: "Admin comment",
            onVacation: "On vacation",
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
            address: "Address",
            birthDate: "Birth Date",
            entryDate: "Entry Date",
            country: "Country",
            email: "Email",
            mobilePhone: "Mobile Phone",
            landlinePhone: "Landline (optional)",
            role: "Role",
            taxClass: "Tax Class",
            department: "Department",
            tarifCode: "Tariff Code",
            canton: "Canton",
            civilStatus: "Civil Status",
            children: "Children",
            religion: "Religion",
            healthInsurance: "Health Insurance",
            bankAccount: "Bank Account",
            socialSecurityNumber: "Social Security Number",
            personnelNumber: "Personnel Number",
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
            deleteConfirmMessage: "Do you really want to delete this user? The account will be disabled and its data retained for up to one year before permanent deletion.",
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
            customerManagement: "Customers",
            projectManagement: "Projects",
            companyManagement: "Companies",
            payslips: "Payslips",
            schedulePlanner: "Schedule",
            knowledge: "Documents",
            payments: "Payments",
            companySettings: "Company settings",
            myDashboard: "My Dashboard",
            chatbot: "Chatbot",
            profile: "Profile",
            whatsNew: "What's New?",
            history: "Update History",
            brightness: "brightness",
            languageLabel: "Language",
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
        noCustomer: "No customer",
        noProject: "No project",
        recentCustomers: "Recent customers",
        customerSaved: "Customer saved",
        customerSaveError: "Error saving customer",
        projectSaveError: "Error saving project",
        customerLabel: "Customer",
        projectLabel: "Project",
        applyForDay: "Save",
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
        overtimeBalance: "Overtime Balance",
        noCorrections: "No correction requests found",
        dailyNoteSaved: "Daily note saved.",
        overtimeBalanceInfo: "Total overtime minus used or deducted hours.",
        dailyNoteError: "Error saving daily note.",
        weeklyHours: "Total hours (current week)",
        monthlyHours: "Total hours (month)",
        estimatedEarnings: "Estimated earnings",
        editNotes: "Edit notes",
        expectedWeekInfo: "Your weekly target is based on your work percentage and reduces when you are absent.",
        addNotes: "Add notes",
        fillWorkTimesError: "Please fill in Work Start and Work End",

        onVacation: "On vacation",
        enterNotePlaceholder: "Enter note...",
        dailyNoteTitle: "Note",
        noNotePlaceholder: "No note.",
        editNote: "Edit note",
        save: "Save",
        noTask: "No task",
        assignCustomer: {
            editButton: "Edit customers & times",
        },
        correction: {
            desiredChange: "Desired Change",
            type: "Type",
            time: "Time",
        },
        punchTypes: {
            START: "Start",
            ENDE: "End",
        },
        currentWeek: "Current Week",

        // missing translations
        actualTime: "Actual",
        breakTime: "Break Time",
        start: "Start",
        end: "End",
        worked: "Worked",
        pause: "Break",
        overtime: "Overtime",
        total: "Total",
        period: "Period",
        timeReportFor: "Time report for",
        correctionFor: "Correction for",
        welcome: "Welcome",
        "errors.reportError": "Error generating report",
        userDashboard: {
            correctionSuccess: "Correction request submitted successfully.",
        },
        adminCorrections: {
            header: {
                user: "User",
                date: "Request Date",
                request: "Request",
                reason: "Reason",
                status: "Status",
                actions: "Actions",
            },
        },

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
            endDateBeforeStart: "End date cannot be before start date.",
            invalidOvertimeHours: "Please enter a valid positive number of hours for overtime deduction.",
            overtimeDeductionHoursLabel: "Overtime deduction overall (hours):",
            hoursPlaceholder: "e.g. 4 or 8.5",
            usesOvertimeLabel: "Use overtime",
            halfDayDeductionNotice: "Note: enter the hours for the half day.",
            unknownUser: "Unknown",
            halfDayShort: "1/2",
            overtimeVacationShort: "OT",
            companyVacationLabel: "Company vacation",
            delete: {
                noSelection: "No vacation selected to delete.",
                success: "Vacation request deleted successfully.",
                error: "Error deleting vacation request:",
                buttonTitle: "Delete vacation request",
                confirmTitle: "Confirm delete vacation",
                confirmMessage: "Do you want to delete the vacation of",
                irreversible: "permanently delete?",
                overtimeReversalInfo: "For approved overtime vacations the deducted hours will be restored.",
                regularVacationInfo: "This is a regular vacation. The days will be credited back to annual vacation balance.",
                confirmDeleteButton: "Yes, delete"
            },
        },
        adminCalendar: {
            title: "Admin Calendar Overview",
        },
        adminSickLeave: {
            modalTitle: "Report Sick Leave",
            userSelection: "Select User",
            selectUserPlaceholder: "Please select user",
            startDateLabel: "Start Date",
            endDateLabel: "End Date",
            halfDayLabel: "Half-day Sick",
            reportButton: "Report Sick",
            reportButtonModal: "Save Sick Leave",
            noUserSelected: "Please select a user",
            datesMissing: "Please provide start and end dates",
            endDateBeforeStart: "End date cannot be before start date.",
            reportSuccess: "Sick leave reported successfully.",
            reportError: "Error reporting sick leave:",
            unknownUser: "Unknown",
            halfDayShort: "1/2",
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
        sickLeave: {
            modalTitle: "Report Sick Leave",
            halfDayLabel: "Half-day",
            commentPlaceholder: "Reason, doctor visit etc.",
            fullDay: "Full-day sick",
            halfDay: "Half-day sick",
            halfDayOneDay: "Half-day sick leave only for one day.",
            missingDates: "Please provide start and end dates.",
            invalidDates: "End date cannot be before start date.",
            reportButtonShort: "Report Sick",
            reportSuccess: "Sick leave submitted.",
            reportError: "Error submitting sick leave.",
            sickShort: "S",
            sick: "Sick",
        },
        calendarLocale: "en-US",
        requestVacationButton: "Request Vacation",
        vacationModalTitle: "Submit Vacation Request",
        fromDate: "From",
        toDate: "To",
        dayScope: "Period",
        vacationType: "Vacation Type",
        submitButton: "Submit",
        cancelButton: "Cancel",
        comment: "Comment (optional)",
        // ----------------------------------------------------------------------
        // PrintReport
        // ----------------------------------------------------------------------
        printReport: {
            title: "Time Report",
            userLabel: "User",
            periodLabel: "Period",
            summaryWork: "Total Work Time",
            summaryBreak: "Total Break Time",
            date: "Date",
            workStart: "Work Start",
            breakStart: "Break Start",
            breakEnd: "Break End",
            workEnd: "Work End",
            pause: "Break",
            total: "Work",
            punches: "Punches",
            note: "Note",
            overview: "Overview",
            worked: "Worked",
            blocks: "Work Blocks",
            workLabel: "Work",
            breaks: "Breaks",
            open: "OPEN",
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
            newsletterTitle: "Stay informed!",
            newsletterText: "Updates and tips straight to your inbox.",
            newsletterPlaceholder: "Your email",
            newsletterButton: "Subscribe",
            contactTitle: "Contact",
            contactName: "Name",
            contactEmail: "Email",
            contactMessage: "Message",
            contactButton: "Send",
            contactSuccess: "Message sent!",
            contactError: "Failed to send message.",
        },

        // Falls du "search", "darkMode", "lightMode" etc. nutzt
        search: "Search",
        darkMode: "Dark Mode",
        lightMode: "Light Mode",
        page: "Page",
        of: "of",
        for: "for",
        ok: "OK",
        done: "Done",
        sessionExpired: "Session expired. Please log in again.",
        impressum: "Impressum",
        agb: "Terms",
        instagram: "Instagram",
        changelogModal: {
            whatsNew: "What's new in version",
            published: "Published on",
            close: "Close",
        },
        whatsNewPage: {
            title: "All changes and updates",
            loading: "Loading history...",
        },
        payslips: {
            title: "My Payslips",
            pendingTitle: "Pending Payslips",
            approvedTitle: "Approved Payslips",
            approve: "Approve",
            delete: "Delete",
            deleteConfirm: "Delete payslip?",
            reopen: "Reopen",
            approveAll: "Approve All",
            exportCsv: "CSV Export",
            backup: "Backup",
            print: "Print",
            period: "Period",
            user: "User",
            gross: "Gross",
            net: "Net",
            payoutDate: "Payout date",
            filterName: "Name",
            start: "Start",
            end: "End",
            filter: "Filter",
            printError: "Failed to load PDF",
            saveLogo: "Save logo",
            logoSaved: "Logo saved",
            logoSaveError: "Failed to save logo",
            employerContrib: "Employer contributions",
            employerTotal: "Total",
            scheduleDay: "Automatically on day",
            scheduleButton: "Schedule",
            scheduleAll: "Enable automatic payslips for all",
            editPayout: "Edit date",
            enterPayoutDate: "Enter payout date",
            generateManual: "Generate manually",
            selectUser: "Select user",
            payoutOvertime: "Payout overtime",
            overtimeHours: "Overtime hours",
            generate: "Generate",
            logoUploadTitle: "Upload logo",
            selectFile: "Choose file",
            myPayslips: "My Payslips"

        },
        schedulePlanner: {
            title: "Schedule Planner",
            auto: "Auto Fill",
            save: "Save",
            prevWeek: "Prev Week",
            nextWeek: "Next Week",
            copyWeeks: "Copy",
            weekShort: "Week",
            userOnVacation: "This employee is on vacation on this day"

        },
        knowledge: {
            managementTitle: "Knowledge Documents",
            createTitle: "New Document",
            titleLabel: "Title",
            contentLabel: "Content",
            accessLabel: "Access",
            accessAll: "All",
            accessAdmin: "Admins only",
            listTitle: "Documents",
            createSuccess: "Document saved",
            createError: "Error while saving",
            deleteConfirm: "Delete this document?",
            deleteError: "Error while deleting",
            noDocs: "No documents"
        },
        quickStart: {
            title: "Quick Start",
            profile: "Complete your profile",
            punch: "First time tracking",
            vacation: "Request vacation",
            progress: "done",
        },
        impressumPage: {
            title: "Imprint",
            address: "<strong>Chrono-Logisch</strong><br />Sole proprietorship<br />Owner: Christopher Siefert<br />Lettenstrasse 20<br />CH-9122 Mogelsberg",
            contact: "Phone: <a href=\"tel:+41765467960\">+41 76 546 79 60</a><br />Email: <a href=\"mailto:siefertchristopher@chrono-logisch.ch\">siefertchristopher@chrono-logisch.ch</a>",
            responsible: "Responsible for this website:<br />Christopher Siefert (owner)",
            liability: "<strong>Disclaimer:</strong><br />We assume no liability for the content of external links. The operators of the linked pages are solely responsible for their content.",
            copyright: "<strong>Copyright:</strong><br />All content on this website is protected by copyright. Any use without explicit permission is prohibited.",
            stand: "<em>Updated: May 2025</em>",
        },
        privacyPage: {
            title: "Privacy Policy for Chrono",
            content: `
                <p><strong>Updated: 10 September 2025</strong></p>
                <p>We appreciate your interest in our time tracking application Chrono. Protecting your personal data is important to us. The following provides detailed information on how we handle your data.</p>
                <h2>1. Controller</h2>
                <p>The controller for data collection, processing and use under the GDPR is:<br/>
                <strong>Chrono</strong><br/>
                Lettenstrasse 20<br/>
                9122 Mogelsberg<br/>
                Switzerland</p>
                <p><strong>Email:</strong> siefertchristopher@chrono-logisch.ch<br/>
                <strong>Phone:</strong> +41 764699122</p>
                <p>Further details are available in our <a href="/impressum">imprint</a>.</p>
                <h2>2. Type, Purpose and Scope of Data Processing</h2>
                <h3>a) Visiting the website</h3>
                <p>When visiting our website we automatically collect data such as browser type, operating system, referrer URL, hostname of the accessing computer, time of the server request and IP address. These are technically necessary to display the site and ensure stability and security.</p>
                <h3>b) Registration and use of an account</h3>
                <p>To use Chrono you must register. We collect username, email address, encrypted password and optional profile data. These data are required to manage the account and fulfil the contract.</p>
                <h3>c) Time tracking and work organisation</h3>
                <p>Chrono processes working times, project assignments, correction requests, NFC card data as well as sick notes and vacation requests. Processing is necessary for contract fulfilment.</p>
                <h3>d) Payroll module</h3>
                <p>If the payroll module is used we process salary data, tax information and bank details. These data are treated confidentially.</p>
                <h3>e) AI chatbot and knowledge base</h3>
                <p>Requests to the chatbot are processed to deliver suitable answers and to improve the service.</p>
                <h3>f) Contacting us</h3>
                <p>Data transmitted via contact form or email (e.g. name, email, message content) are stored to process your enquiry.</p>
                <h2>3. Cookies and Local Storage</h2>
                <p>We use local storage to save preferences such as language or theme. This information is not transmitted to our servers.</p>
                <h2>4. Disclosure of Data to Third Parties</h2>
                <p>Data are only disclosed if required by law or necessary for contract fulfilment, e.g. to payment providers or after consent to Google Calendar. Data processing agreements exist with all service providers.</p>
                <h2>5. Your Rights</h2>
                <p>You have the right to access, rectify, delete or restrict processing of your data, the right to object, data portability and the right to lodge a complaint with a supervisory authority.</p>
                <h2>6. Data Security</h2>
                <p>We take technical and organisational measures to protect your data against loss or unauthorised access and continually improve these measures.</p>
                <h2>7. Changes to this Privacy Policy</h2>
                <p>We reserve the right to adjust this policy so that it always meets current legal requirements or to reflect changes to our services.</p>
            `,
        },
        agbPage: {
            title: "Terms and Conditions for Chrono",
            content: `
                <p><strong>Updated: 10 September 2025</strong></p>
                <h2>1. Scope</h2>
                <p>These terms and conditions govern the contractual relationship between Chrono and users of the "Chrono" SaaS application. Deviating conditions are not accepted unless expressly agreed by the provider.</p>
                <h2>2. Services</h2>
                <p>The provider offers software for time tracking, project management, vacation and absence planning and optionally payroll via the Internet. The scope depends on the selected package. The provider may modify functions as long as core functionality remains.</p>
                <h2>3. Contract and Trial</h2>
                <p>The contract is concluded through online registration and acceptance by the provider. Free trials do not automatically convert into paid subscriptions.</p>
                <h2>4. Prices and Payment</h2>
                <p>Fees are based on the current price list. Charges are due in advance for the chosen billing period. The provider may block access in case of payment default.</p>
                <h2>5. Customer Obligations</h2>
                <p>Customers must keep login data confidential and are responsible for all activities under their account. They must ensure that entered data do not violate third-party rights.</p>
                <h2>6. Data Protection and Security</h2>
                <p>The provider processes personal data solely for contract fulfilment in accordance with legal requirements. Further details are provided in the <a href="/datenschutz">privacy policy</a>.</p>
                <h2>7. Availability and Maintenance</h2>
                <p>The provider ensures customary availability and may perform planned maintenance, which will be announced where possible.</p>
                <h2>8. Liability</h2>
                <p>The provider is fully liable for intent or gross negligence and for injury to life, body or health. For slight negligence liability is limited to typical, foreseeable damages.</p>
                <h2>9. Term and Termination</h2>
                <p>The contract runs indefinitely and may be terminated by the customer at the end of the billing period. The provider may terminate with 30 days' notice.</p>
                <h2>10. Final Provisions</h2>
                <p>Swiss law applies. Place of jurisdiction is the provider's seat. Invalid provisions do not affect the validity of the remaining terms.</p>
            `,
        },
        registrationPage: {
            pricingTitle: "Packages & Pricing",
            pricingIntro: "Choose the package that fits best.",
            monthlyBilling: "Monthly billing",
            yearlyBilling: "Yearly billing (2 months free!)",
            employeeCount: "Number of employees (1-100):",
            trainingTitle: "Optional add-on:",
            addTraining: "Additional intensive onboarding (1h)",
            footnote: "All prices excl. VAT.",
            sendRequest: "Send registration request",
            sending: "Sending request...",
            thanks: "Thank you for your request!",
            backHome: "Back to homepage"
        },
        notFound: {
            pageNotFound: "404 - Page not found",
        },
        hourlyDashboard: {
            addEntryFirst: "Please add at least one correction entry.",
            userNotFound: "User not found, please log in again.",
        },
        percentageDashboard: {
            title: "Percentage Dashboard",
            workPercentageLabel: "Work %",
        },
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
