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
            fetchSickLeaveErrorAdmin: "Fehler beim Laden der Krankmeldungen für das Dashboard.",
            userNotLoaded: "Benutzer nicht geladen.",
            unknownError: "Unbekannter Fehler.",
            notLoggedIn: "Nicht eingeloggt.",
            fetchUsersError: "Fehler beim Laden der Benutzer.",
            fetchDailySummariesError: "Fehler beim Laden der Tagesübersichten.",
            fetchVacationsError: "Fehler beim Laden der Urlaubsanträge.",
            fetchCorrectionsError: "Fehler beim Laden der Korrekturanträge.",
            fetchBalancesError: "Fehler beim Laden der Salden (Überstundenkonto).",
            fetchHolidaysErrorForCanton: "Fehler beim Laden der Feiertage für diesen Kanton:",
            holidayOptionUpdateError: "Fehler beim Aktualisieren der Feiertagsoption:",
            genericError: "Ein Fehler ist aufgetreten.",
        },
        // ----------------------------------------------------------------------
        // Projekte, Kunden & Aufgaben (Admin)
        // ----------------------------------------------------------------------
        create: "Anlegen",
        delete: "Löschen",
        edit: "Bearbeiten",
        save: "Speichern",
        details: "Details",
        active: "Aktiv",
        inactive: "Inaktiv",
        refresh: "Aktualisieren",
        status: "Status",

        project: {
            management: {
                hubTitle: "Projekte, Kunden & Aufgaben",
                tabTitle: "Projekte",
                tablist: "Verwaltungsbereiche auswählen",
            },
            analytics: {
                error: "Fehler beim Laden der Projekt-Analytics",
            },
            hierarchy: {
                title: "Projekt-Hierarchie & KPIs",
                empty: "Keine Hierarchie vorhanden.",
                metrics: {
                    budgetTitle: "Budget Stunden",
                    actualTitle: "Gebuchte Stunden",
                    utilizationTitle: "Auslastung",
                    budgetLabel: "Budget",
                    actualLabel: "Ist",
                },
            },
            list: {
                title: "Bestehende Projekte",
                emptyTitle: "Noch keine Projekte",
                emptyDesc: "Lege oben ein neues Projekt an. Projekte können optional ein Budget in Minuten haben.",
            },
            create: {
                title: "Neues Projekt anlegen",
                noCustomersTitle: "Noch keine Kunden angelegt",
                noCustomersDesc: "Lege zuerst einen Kunden an, um Projekte zuordnen zu können.",
                form: "Projekt anlegen",
                nameLabel: "Projektname",
                namePlaceholder: "Name des neuen Projekts",
                customerLabel: "Kunde",
                customerPlaceholder: "Kunde auswählen...",
                parentLabel: "Übergeordnetes Projekt",
                noParent: "Kein übergeordnetes Projekt",
                budgetLabel: "Budget (Minuten)",
                budgetPlaceholder: "Budget (Minuten)",
                rateLabel: "Stundensatz (CHF)",
                ratePlaceholder: "Stundensatz (optional)",
                validationError: "Bitte Projektname und Kunde auswählen.",
                budgetInvalid: "Budget darf nicht negativ sein.",
                success: "Projekt erfolgreich angelegt!",
                error: "Fehler beim Anlegen des Projekts.",
            },
            edit: {
                form: "Projekt bearbeiten",
                nameLabel: "Projektname",
                customerLabel: "Kunde",
                customerPlaceholder: "Kunde auswählen...",
                parentLabel: "Übergeordnetes Projekt",
                noParent: "Kein übergeordnetes Projekt",
                budgetLabel: "Budget (Minuten)",
                budgetPlaceholder: "Budget (Minuten)",
                rateLabel: "Stundensatz (CHF)",
                ratePlaceholder: "Stundensatz (optional)",
            },
            update: {
                validationError: "Bitte Projektname und Kunde auswählen.",
                budgetInvalid: "Budget darf nicht negativ sein.",
                success: "Projekt erfolgreich gespeichert!",
                error: "Fehler beim Speichern des Projekts.",
            },
            delete: {
                confirm: "Sind Sie sicher, dass Sie dieses Projekt löschen möchten?",
                success: "Projekt erfolgreich gelöscht!",
                error: "Fehler beim Löschen des Projekts.",
            },
            integration: {
                title: "Automatisierte Integrationen",
                name: "Name der Integration",
                endpoint: "Ziel-URL / Endpoint",
                authHeader: "Auth-Header (optional)",
                authLabel: "Auth",
                active: "Aktiv",
                autoSync: "Auto-Sync",
                add: "Integration hinzufügen",
                none: "Noch keine Integrationen hinterlegt.",
                trigger: "Test-Übertragung",
                triggered: "Integration erfolgreich ausgelöst!",
                activate: "Aktivieren",
                deactivate: "Deaktivieren",
                startAuto: "Auto-Sync starten",
                stopAuto: "Auto-Sync stoppen",
                lastRun: "Letzte Ausführung",
                lastRunTitle: "Letzte Simulation",
                type: "Typ",
                nameRequired: "Name für die Integration angeben.",
                created: "Integration gespeichert!",
                updated: "Integration aktualisiert!",
                deleted: "Integration gelöscht!",
                deleteConfirm: "Integration wirklich löschen?",
                errorLoad: "Integrationen konnten nicht geladen werden.",
                errorCreate: "Integration konnte nicht gespeichert werden.",
                errorUpdate: "Integration konnte nicht aktualisiert werden.",
                errorDelete: "Integration konnte nicht gelöscht werden.",
                errorTrigger: "Integration konnte nicht ausgelöst werden.",
            },
            billing: {
                title: "Automatisierte Abrechnung",
                selectProject: "Projekt wählen",
                includeChildren: "Unterprojekte einbeziehen",
                overrideRate: "Override-Stundensatz (optional)",
                overrideApplied: "Override-Satz",
                generate: "Abrechnung erstellen",
                generated: "Abrechnung erstellt!",
                error: "Abrechnung konnte nicht erstellt werden.",
                validation: "Bitte Projekt und Zeitraum wählen.",
                projectColumn: "Projekt",
                taskColumn: "Aufgabe",
                minutes: "Minuten",
                amount: "Betrag",
                currency: "Währung",
                totalAmount: "Gesamtbetrag",
                totalHours: "Billable Stunden",
                projectRate: "Projekt-Satz",
            },
            audit: {
                title: "Compliance & Audit",
                user: "Benutzer",
                action: "Aktion",
                target: "Ziel",
                empty: "Noch keine Audit-Einträge vorhanden.",
                errorLoad: "Audit-Log konnte nicht geladen werden.",
            },
            budget: {
                unit: "Min",
            },
            rate: "Rate",
            parent: "Parent",
            noCustomer: "Kein Kunde zugewiesen",
        },

        customer: {
            management: {
                title: "Kunden",
            },
            create: {
                title: "Neuen Kunden anlegen",
                placeholder: "Name des neuen Kunden",
            },
            list: {
                title: "Bestehende Kunden",
            },
            createSuccess: "Kunde erfolgreich angelegt!",
            createError: "Fehler beim Anlegen des Kunden.",
            updateSuccess: "Kunde erfolgreich gespeichert!",
            updateError: "Fehler beim Speichern des Kunden.",
            deleteConfirm: "Sind Sie sicher, dass Sie diesen Kunden löschen möchten?",
            deleteSuccess: "Kunde erfolgreich gelöscht!",
            deleteError: "Fehler beim Löschen des Kunden.",
        },

        task: {
            management: {
                title: "Aufgaben",
            },
            projectSelection: "Projekt auswählen",
            noProjects: {
                title: "Noch keine Projekte vorhanden",
                description: "Lege zuerst ein Projekt an, um Aufgaben zu verwalten.",
            },
            budget: {
                unit: "Min",
            },
            billable: "Abrechenbar",
            create: {
                title: "Neue Aufgabe anlegen",
                namePlaceholder: "Name der neuen Aufgabe",
                budgetPlaceholder: "Budget (Minuten)",
                billable: "Abrechenbar",
                validationError: "Bitte Projekt auswählen und Namen eingeben.",
                success: "Aufgabe erfolgreich angelegt!",
                error: "Fehler beim Anlegen der Aufgabe.",
            },
            list: {
                title: "Bestehende Aufgaben",
                empty: "Noch keine Aufgaben für dieses Projekt",
                emptyHint: "Lege oben eine neue Aufgabe an, um loszulegen.",
            },
            edit: {
                budgetPlaceholder: "Budget (Minuten)",
                billable: "Abrechenbar",
            },
            update: {
                validationError: "Bitte Namen eingeben.",
                success: "Aufgabe erfolgreich gespeichert!",
                error: "Fehler beim Speichern der Aufgabe.",
            },
            delete: {
                confirm: "Sind Sie sicher, dass Sie diese Aufgabe löschen möchten?",
                success: "Aufgabe erfolgreich gelöscht!",
                error: "Fehler beim Löschen der Aufgabe.",
            },
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
                    {
                        title: "Persönlicher Support inklusive",
                        bullets: [
                            "Direkter Draht zum Chrono-Team",
                            "Antwort in der Regel am selben Werktag",
                            "Onboarding-Hilfe für dein gesamtes Team",
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
            analyticsButton: "Analyse-Seite öffnen",
            kpis: {
                sectionLabel: "Aktuelle Kennzahlen (kurzer Überblick)",
                pendingRequests: "Offene Anträge",
                vacationsShort: "Urlaub",
                correctionsShort: "Korrekturen",
                averageOvertime: "Ø Überstundensaldo (Durchschnitt)",
                sampleSizePrefix: "Grundlage: ",
                noBalances: "Keine Salden vorhanden",
                negativeBalances: "Negative Salden (Fehlzeit)",
                topOvertime: "Höchster Saldo (meiste Überstunden)",
                unknownUser: "Unbekannt",
                noPositive: "Keine positiven Überstände (Mehrarbeit)",
                noNegative: "Keine negativen Salden",
                actions: {
                    openVacations: "Zu Urlaubsanträgen",
                    openCorrections: "Zu Korrekturen",
                    focusIssues: "Problemfälle filtern",
                    openAnalytics: "Analytics öffnen",
                    focusNegative: "Negativsalden hervorheben",
                    focusPositive: "Überstunden-Topliste anzeigen",
                },
            },
            focusRibbon: {
                title: "Problemfokus",
                subtitleWithCount: "{count} Mitarbeitende benötigen Review.",
                subtitleClear: "Alle Nutzer ohne Warnungen in dieser Woche.",
                showAll: "Alle Problemfälle anzeigen",
                reset: "Filter zurücksetzen",
            },
            issueRibbon: {
                missing: "Fehlende Zeiten",
                incomplete: "Unvollständig",
                autoCompleted: "Automatisch beendet",
                holidayPending: "Feiertag offen",
            },
            issueFilters: {
                missing: "Fehlende Stempel (keine Zeiten)",
                incomplete: "Unvollständige Tage (z.B. Ende fehlt)",
                autoCompleted: "Automatisch beendet (noch prüfen)",
                holidayPending: "Feiertag offen (Entscheid fehlt)",
                showAll: "Alle anzeigen",
                onlyIssues: "Nur Problemfälle",
                groupLabel: "Problemtypen filtern",
                reset: "Alle Typen",
            },
            smartOverview: {
                title: "Wochenüberblick",
                subtitle: "Direkter Blick auf wichtige Kennzahlen und offene Themen.",
                showIssuesButton: "Problemfälle anzeigen",
                cards: {
                    active: {
                        title: "Aktive Personen",
                        subtitle: "mit Sollzeit in dieser Woche",
                    },
                    issues: {
                        title: "Problemfälle",
                        subtitle: "Benutzer mit Handlungsbedarf",
                    },
                    corrections: {
                        title: "Offene Korrekturen",
                        subtitle: "Tage prüfen (fehlend/unklar)",
                    },
                    negative: {
                        title: "Negative Salden",
                        subtitle: "Personen unter Soll",
                    },
                },
                quickFix: {
                    title: "Schnellkorrekturen",
                    subtitle: "Spring direkt zu den wichtigsten Problemen.",
                    empty: "Aktuell keine offenen Problemfälle – alles im grünen Bereich!",
                    action: "Öffnen",
                    labels: {
                        missing: "Fehlende Stempel",
                        incomplete: "Unvollständige Tage",
                        autoCompleted: "Automatisch beendet",
                        holidayPending: "Feiertag offen",
                    },
                },
            },
            actionStream: {
                title: "Priorisierte Aufgaben",
                counter: "{count} offen",
                empty: "Aktuell liegen keine offenen Aufgaben an.",
                vacationRequest: "Urlaubsantrag",
                correctionRequest: "Korrekturantrag",
                focusUser: "Im Wochenraster öffnen",
                openVacations: "Alle Urlaubsanträge öffnen",
                openCorrections: "Alle Korrekturen öffnen",
                morePending: "+{count} weitere Aufgaben",
            },
            manageHiddenUsersTooltip: "Ausgeblendete Benutzer verwalten",
            hideHiddenUsersList: "Liste verbergen",
            showHiddenUsersList: "Ausgeblendete zeigen",
            hiddenUsersTitle: "Ausgeblendete Benutzer",
            noHiddenUsers: "Aktuell sind keine Benutzer ausgeblendet.",
            unhideUser: "Einblenden",
            unhideAllUsers: "Alle einblenden",
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
            noIssues: "Keine Probleme",
            problemTooltips: {
                missingEntries: "Tag(e) ohne Eintrag",
                incompleteDays: "Tag(e) unvollständig (z.B. Ende fehlt)",
                autoCompletedDaysUncorrected: "Tag(e) automatisch beendet & unkorrigiert",
                holidayPending: "Feiertagsoption(en) ausstehend",
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
            halfDayShort: "½ Tag",
            overtimeVacationShort: "ÜS",
            onVacation: "Im Urlaub",
            correctionModal: {
                approveTitle: "Korrektur genehmigen",
                denyTitle: "Korrektur ablehnen",
                commentLabel: "Kommentar für den Nutzer:",
                commentPlaceholder: "Warum genehmigst / lehnst du ab?",
                confirmButton: "Bestätigen",
            },
        },
        adminAnalytics: {
            fetchError: "Analytics konnten nicht geladen werden.",
            title: "Analyse & Trends",
            subtitle: "Schneller Überblick über Auslastung (wie viel gearbeitet wird), Abwesenheiten (Urlaub oder krank) und Entwicklungen im Team.",
            loading: "Daten werden geladen …",
            errorMessage: "Beim Laden der Analytics-Daten ist ein Fehler aufgetreten. Bitte versuchen Sie es später erneut.",
            daysLabel: "Tage",
            overtimeAxis: "Überstunden (Stunden)",
            daysAxis: "Tage",
            overtimeTrend: {
                title: "Überstundentrend",
                average: "Ø Saldo",
                topPositive: "Höchster Saldo",
                topNegative: "Tiefster Saldo",
                empty: "Es liegen aktuell keine auswertbaren Überstundendaten vor.",
                legend: "Berücksichtigte Mitarbeiterinnen und Mitarbeiter",
            },
            filters: {
                title: "Filter",
                rangeLabel: "Zeitraum",
                currentRange: "Aktueller Zeitraum",
                rangeOption: {
                    fourWeeks: "Letzte 4 Wochen",
                    eightWeeks: "Letzte 8 Wochen",
                    twelveWeeks: "Letzte 12 Wochen",
                    twentyFourWeeks: "Letzte 24 Wochen",
                },
                weeksSuffix: "Wochen",
                userLabel: "Mitarbeitende",
                hint: "Keine Auswahl zeigt automatisch die Top 5 mit der größten Veränderung an.",
                selectAll: "Alle anzeigen",
                reset: "Auswahl zurücksetzen",
                currentSelection: "Aktuell angezeigt",
                noSelectableUsers: "Keine Mitarbeitenden verfügbar.",
                selectionEmpty: "Für die gewählten Personen liegen im Zeitraum keine Daten vor.",
                autoSelectionLabel: "Top 5 (automatisch)",
                allSelectedLabel: "Alle",
            },
            absence: {
                title: "Abwesenheiten pro Monat",
                vacation: "Urlaubstage",
                sick: "Krankheitstage",
                vacationTotal: "Urlaub gesamt",
                sickTotal: "Krankheit gesamt",
                empty: "Noch keine Abwesenheitsdaten im ausgewählten Zeitraum.",
            },
            vacationPie: {
                title: "Status der Urlaubsanträge",
                total: "Gesamt",
                regular: "Genehmigt",
                overtime: "Überstunden genutzt",
                pending: "Ausstehend (wartet auf Entscheidung)",
                denied: "Abgelehnt",
                empty: "Es wurden noch keine Urlaubsanträge erfasst.",
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
            includeInTimeTrackingLabel: "In Zeiterfassung & Übersichten anzeigen",
            includeInTimeTrackingEnabled: "Eingeschlossen in Zeitübersichten",
            includeInTimeTrackingDisabled: "Von Zeitübersichten ausgeschlossen",
            includeInTimeTrackingHint: "Admins ohne Arbeitszeiterfassung werden in Wochenansichten und Salden nicht angezeigt.",
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
        noTask: "Keine Aufgabe",
        assignCustomer: {
            editButton: "Kunden & Zeiten bearbeiten",
            projectTag: "Projektzeit",
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
                <p><strong>Stand: 20. Februar 2025</strong></p>
                <p>Wir freuen uns über Ihr Interesse an unserer Zeiterfassungsanwendung Chrono. Der Schutz Ihrer persönlichen Daten hat für uns höchste Priorität. Diese Datenschutzerklärung erläutert, welche personenbezogenen Daten wir verarbeiten, zu welchen Zwecken und welche Rechte Ihnen nach der Datenschutz-Grundverordnung (DSGVO) sowie dem revidierten Schweizer Datenschutzgesetz (revDSG) zustehen.</p>
                <h2>1. Verantwortliche Stelle und Kontakt</h2>
                <p>Verantwortlich für die Datenverarbeitung ist:<br/>
                <strong>Chrono</strong><br/>
                Lettenstrasse 20<br/>
                9122 Mogelsberg<br/>
                Schweiz</p>
                <p><strong>E-Mail:</strong> <a href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a><br/>
                <strong>Telefon:</strong> <a href="tel:+41764699122">+41 76 469 91 22</a></p>
                <p>Weitere Informationen finden Sie im <a href="/impressum">Impressum</a>.</p>
                <h2>2. Geltungsbereich und Rollenverteilung</h2>
                <p>Diese Datenschutzerklärung gilt für die Nutzung unserer Websites, Web- und Desktop-Anwendungen sowie für sonstige Kontaktaufnahmen mit uns als Anbieter.</p>
                <p>Nutzen Sie Chrono im Auftrag Ihres Arbeitgebers oder Auftraggebers, bleibt dieser in der Regel datenschutzrechtlich Verantwortlicher. Wir verarbeiten die dabei anfallenden Daten als Auftragsverarbeiter nach Art. 28 DSGVO bzw. Art. 9 revDSG auf Grundlage eines Vertrages zur Auftragsverarbeitung. In diesem Fall beachten Sie bitte zusätzlich die Datenschutzhinweise Ihres Arbeitgebers.</p>
                <h2>3. Kategorien personenbezogener Daten, Zwecke und Rechtsgrundlagen</h2>
                <ul>
                    <li><strong>Besuch unserer Websites und Schnittstellen</strong><br/>
                    Datenkategorien: IP-Adresse, Datum und Uhrzeit des Zugriffs, Browser- und Geräteinformationen, Referrer-URL, Fehler- und Sicherheitslogs.<br/>
                    Zwecke: Bereitstellung der Website, Gewährleistung von Stabilität und Sicherheit, Missbrauchserkennung, Fehleranalyse.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an einer sicheren Bereitstellung), Art. 31 Abs. 1 revDSG.</li>
                    <li><strong>Registrierung und Vertragsdurchführung</strong><br/>
                    Datenkategorien: Name, Firmenangaben, Benutzername, E-Mail-Adresse, Passwort (gehasht), Rollen- und Berechtigungsdaten, Einstellungen.<br/>
                    Zwecke: Einrichtung und Verwaltung des Benutzerkontos, Vertragserfüllung, Kundenbetreuung.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung oder vorvertragliche Maßnahmen), Art. 6 Abs. 1 lit. c DSGVO (gesetzliche Pflichten, z.&nbsp;B. steuerliche Nachweispflichten), Art. 31 Abs. 1 revDSG.</li>
                    <li><strong>Nutzung der Zeiterfassungs- und Organisationsfunktionen</strong><br/>
                    Datenkategorien: Arbeitszeitbuchungen, Projekt- und Tätigkeitszuordnungen, Abwesenheiten, Urlaubs- und Korrekturanträge, ggf. Dienstpläne, NFC-Kartennummern, Kommentare, Informationen zu Krankmeldungen.<br/>
                    Zwecke: Erfüllung des SaaS-Vertrages, Arbeitsorganisation, gesetzliche Nachweis- und Aufbewahrungspflichten nach Arbeits- und Sozialversicherungsrecht.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b DSGVO, Art. 6 Abs. 1 lit. c DSGVO (rechtliche Verpflichtungen), Art. 31 Abs. 1 revDSG. Soweit besondere Kategorien personenbezogener Daten (z.&nbsp;B. Gesundheitsdaten bei Krankmeldungen) verarbeitet werden, erfolgt dies auf Grundlage von Art. 9 Abs. 2 lit. b DSGVO sowie Art. 31 Abs. 2 revDSG.</li>
                    <li><strong>Lohn- und Abrechnungsmodul</strong><br/>
                    Datenkategorien: Gehaltsbestandteile, Steuer- und Sozialversicherungsmerkmale, Bankverbindung, Auszahlungsinformationen.<br/>
                    Zwecke: Erstellung von Lohnabrechnungen, Erfüllung arbeits-, steuer- und sozialversicherungsrechtlicher Pflichten, Nachweisführung.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b und lit. c DSGVO, Art. 9 Abs. 2 lit. b DSGVO, Art. 31 Abs. 1 und 2 revDSG.</li>
                    <li><strong>Zahlungsabwicklung</strong><br/>
                    Datenkategorien: Vertrags- und Rechnungsdaten, Zahlungsstatus, verkürzte Zahlungsinformationen (z.&nbsp;B. Kartentyp, letzte vier Stellen).<br/>
                    Zwecke: Abwicklung kostenpflichtiger Leistungen, Forderungsmanagement, Buchhaltung.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b und lit. f DSGVO (berechtigtes Interesse an effizienter Zahlungsabwicklung), Art. 6 Abs. 1 lit. c DSGVO (gesetzliche Aufbewahrungspflichten), Art. 31 Abs. 1 revDSG.</li>
                    <li><strong>Support- und Kommunikationsanfragen</strong><br/>
                    Datenkategorien: Name, Kontaktdaten, Inhalt der Anfrage, Metadaten der Kommunikation.<br/>
                    Zwecke: Bearbeitung und Dokumentation von Anfragen, Kundenservice, Verbesserung unserer Dienste.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b DSGVO (sofern die Anfrage auf einen Vertrag zielt), Art. 6 Abs. 1 lit. f DSGVO (berechtigtes Interesse an Support und Dokumentation), Art. 31 Abs. 1 revDSG.</li>
                    <li><strong>Monitoring, Sicherheit und Protokollierung</strong><br/>
                    Datenkategorien: System- und Audit-Logs, Nutzer-IDs, Zeitstempel technischer Aktionen.<br/>
                    Zwecke: Gewährleistung der Sicherheit unserer Systeme, Nachvollziehbarkeit administrativer Eingriffe, Erkennung und Abwehr von Cyberangriffen, Backup-Verwaltung.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. f DSGVO, Art. 32 DSGVO, Art. 31 Abs. 1 revDSG.</li>
                    <li><strong>KI-Chatbot (lokale Instanz)</strong><br/>
                    Datenkategorien: Inhalte Ihrer Eingaben und Antworten des Systems.<br/>
                    Zwecke: Beantwortung Ihrer Fragen zur Anwendung, Verbesserung der Hilfefunktion.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. b DSGVO (Vertragserfüllung), Art. 6 Abs. 1 lit. f DSGVO (Optimierung unserer Dienste), Art. 31 Abs. 1 revDSG. Anfragen werden ausschließlich auf unserer Infrastruktur verarbeitet; es findet keine Übermittlung an externe KI-Anbieter statt.</li>
                    <li><strong>Optionale Integrationen (z.&nbsp;B. Google Calendar)</strong><br/>
                    Datenkategorien: Kalenderereignisse, Synchronisationsmetadaten.<br/>
                    Zwecke: Synchronisation Ihrer Termine mit Chrono.<br/>
                    Rechtsgrundlagen: Art. 6 Abs. 1 lit. a DSGVO (Einwilligung), Art. 49 Abs. 1 lit. a DSGVO, Art. 31 Abs. 1 revDSG. Die Nutzung erfolgt ausschließlich nach separater Aktivierung durch Sie.</li>
                </ul>
                <h2>4. Empfänger und Auftragsverarbeiter</h2>
                <p>Wir übermitteln personenbezogene Daten nur, wenn eine Rechtsgrundlage dies erlaubt oder eine Einwilligung vorliegt. Zu den Kategorien von Empfängern gehören:</p>
                <ul>
                    <li><strong>Hosting- und Infrastrukturpartner:</strong> Rechenzentren in der Schweiz bzw. im Europäischen Wirtschaftsraum, die unsere Anwendung technisch betreiben. Der jeweils eingesetzte Anbieter ist in unserem Verzeichnis der Verarbeitungstätigkeiten dokumentiert und stellt angemessene technische und organisatorische Maßnahmen sicher.</li>
                    <li><strong>Zahlungsdienstleister:</strong> Stripe Payments Europe Ltd., 1 Grand Canal Street Lower, Dublin 2, Irland (Stripe). Stripe kann Daten an verbundene Unternehmen in den USA übermitteln. Wir haben mit Stripe die EU-Standardvertragsklauseln abgeschlossen.</li>
                    <li><strong>E-Mail- und Kommunikationsdienstleister:</strong> SMTP- und Support-Dienstleister, über die wir Support- und Systembenachrichtigungen versenden.</li>
                    <li><strong>IT-Dienstleister:</strong> Wartungs-, Hosting-, Backup- und Supportpartner, die uns bei der Bereitstellung der Anwendung unterstützen und vertraglich zur Vertraulichkeit verpflichtet sind.</li>
                    <li><strong>Behörden, Gerichte oder externe Berater:</strong> sofern dies zur Erfüllung gesetzlicher Pflichten oder zur Geltendmachung, Ausübung oder Verteidigung von Rechtsansprüchen erforderlich ist.</li>
                </ul>
                <h2>5. Übermittlungen in Drittländer</h2>
                <p>Eine Übermittlung in Staaten außerhalb der Schweiz bzw. des EWR findet grundsätzlich nur statt, wenn dort ein angemessenes Datenschutzniveau besteht oder geeignete Garantien vorliegen. Für Stripe greifen die EU-Standardvertragsklauseln sowie zusätzliche Schutzmaßnahmen. Optionale Integrationen wie Google Calendar (USA) setzen Ihre ausdrückliche Einwilligung voraus; dabei informieren wir Sie gesondert über mögliche Risiken.</p>
                <h2>6. Speicherdauer</h2>
                <ul>
                    <li>Kontodaten speichern wir für die Dauer der Vertragsbeziehung. Nach Vertragsende werden die Daten gemäß vertraglichen Vereinbarungen und gesetzlichen Aufbewahrungsfristen gelöscht oder anonymisiert.</li>
                    <li>Zeiterfassungs-, Projekt- und Abrechnungsdaten bewahren wir entsprechend arbeits-, steuer- und handelsrechtlicher Pflichten bis zu zehn Jahre auf.</li>
                    <li>Technische Logdaten und Sicherheitsprotokolle werden in der Regel spätestens nach 30 Tagen gelöscht, sofern keine Sicherheitsvorfälle eine längere Aufbewahrung erfordern.</li>
                    <li>Support- und Kommunikationsdaten löschen wir spätestens 24 Monate nach Abschluss des Vorgangs, sofern keine gesetzlichen Aufbewahrungspflichten entgegenstehen.</li>
                </ul>
                <p>Sobald der Zweck der Verarbeitung entfällt, prüfen wir im Rahmen unseres Löschkonzepts mindestens jährlich, ob eine Löschung oder Anonymisierung möglich ist.</p>
                <h2>7. Datensicherheit</h2>
                <p>Wir treffen angemessene technische und organisatorische Maßnahmen gemäß Art. 32 DSGVO und Art. 8 revDSG, um Ihre Daten vor Verlust, Missbrauch und unbefugtem Zugriff zu schützen. Dazu zählen u. a. verschlüsselte Datenübertragung (TLS), rollenbasierte Zugriffskonzepte, Protokollierung administrativer Zugriffe, regelmäßige Backups und Sicherheitsupdates.</p>
                <h2>8. Rechte der betroffenen Personen</h2>
                <p>Ihnen stehen die folgenden Rechte zu:</p>
                <ul>
                    <li>Auskunft über die bei uns gespeicherten personenbezogenen Daten (Art. 15 DSGVO, Art. 25 revDSG)</li>
                    <li>Berichtigung unrichtiger Daten (Art. 16 DSGVO, Art. 32 revDSG)</li>
                    <li>Löschung bzw. Einschränkung der Verarbeitung (Art. 17 und 18 DSGVO, Art. 32 revDSG)</li>
                    <li>Widerspruch gegen Verarbeitungen, die auf berechtigtem Interesse beruhen (Art. 21 DSGVO)</li>
                    <li>Datenübertragbarkeit (Art. 20 DSGVO, Art. 28 revDSG)</li>
                    <li>Widerruf erteilter Einwilligungen mit Wirkung für die Zukunft (Art. 7 Abs. 3 DSGVO, Art. 6 Abs. 6 revDSG)</li>
                </ul>
                <p>Zur Wahrnehmung Ihrer Rechte können Sie uns jederzeit unter <a href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a> kontaktieren. Wir beantworten Anfragen grundsätzlich innerhalb eines Monats.</p>
                <h2>9. Beschwerderecht</h2>
                <p>Sie haben das Recht, sich bei einer Datenschutzaufsichtsbehörde zu beschweren. Zuständig für die Schweiz ist der Eidgenössische Datenschutz- und Öffentlichkeitsbeauftragte (Feldeggweg 1, CH-3003 Bern, <a href="https://www.edoeb.admin.ch">www.edoeb.admin.ch</a>). Für EU-/EWR-Personen steht die Aufsichtsbehörde Ihres üblichen Aufenthaltsortes, Ihres Arbeitsplatzes oder des mutmaßlichen Verstoßes zur Verfügung.</p>
                <h2>10. Pflicht zur Bereitstellung von Daten</h2>
                <p>Bestimmte Daten sind für die Nutzung von Chrono erforderlich (z.&nbsp;B. Registrierungs- und Vertragsdaten). Ohne diese Angaben können wir die vertraglichen Leistungen nicht bereitstellen. Freiwillige Angaben kennzeichnen wir als solche.</p>
                <h2>11. Automatisierte Entscheidungsfindung</h2>
                <p>Es findet keine automatisierte Entscheidungsfindung oder Profiling im Sinne von Art. 22 DSGVO statt.</p>
                <h2>12. Änderungen dieser Datenschutzerklärung</h2>
                <p>Wir überarbeiten diese Datenschutzerklärung, wenn neue Funktionen eingeführt werden oder rechtliche Vorgaben sich ändern. Die jeweils aktuelle Version ist jederzeit unter <a href="/datenschutz">www.chrono-logisch.ch/datenschutz</a> abrufbar.</p>
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
            fetchSickLeaveErrorAdmin: "Error loading sick leaves for the dashboard.",
            userNotLoaded: "User not loaded.",
            unknownError: "Unknown error.",
            notLoggedIn: "Not logged in.",
            fetchUsersError: "Error loading users.",
            fetchDailySummariesError: "Error loading daily summaries.",
            fetchVacationsError: "Error loading vacation requests.",
            fetchCorrectionsError: "Error loading correction requests.",
            fetchBalancesError: "Error loading balances (overtime account).",
            fetchHolidaysErrorForCanton: "Error loading holidays for this canton:",
            holidayOptionUpdateError: "Error updating the holiday option:",
            genericError: "Something went wrong.",
        },

        // ----------------------------------------------------------------------
        // Projects, Customers & Tasks (Admin)
        // ----------------------------------------------------------------------
        create: "Create",
        delete: "Delete",
        edit: "Edit",
        save: "Save",
        details: "Details",
        active: "Active",
        inactive: "Inactive",
        refresh: "Refresh",
        status: "Status",

        project: {
            management: {
                hubTitle: "Projects, Customers & Tasks",
                tabTitle: "Projects",
                tablist: "Select management area",
            },
            analytics: {
                error: "Failed to load project analytics",
            },
            hierarchy: {
                title: "Project hierarchy & KPIs",
                empty: "No hierarchy available.",
                metrics: {
                    budgetTitle: "Budget hours",
                    actualTitle: "Booked hours",
                    utilizationTitle: "Utilization",
                    budgetLabel: "Budget",
                    actualLabel: "Actual",
                },
            },
            list: {
                title: "Existing projects",
                emptyTitle: "No projects yet",
                emptyDesc: "Create a new project above. Projects can optionally have a budget in minutes.",
            },
            create: {
                title: "Create new project",
                noCustomersTitle: "No customers yet",
                noCustomersDesc: "Create a customer first to assign projects.",
                form: "Create project",
                nameLabel: "Project name",
                namePlaceholder: "Name of the new project",
                customerLabel: "Customer",
                customerPlaceholder: "Select customer...",
                parentLabel: "Parent project",
                noParent: "No parent project",
                budgetLabel: "Budget (minutes)",
                budgetPlaceholder: "Budget (minutes)",
                rateLabel: "Hourly rate (CHF)",
                ratePlaceholder: "Hourly rate (optional)",
                validationError: "Please enter a project name and select a customer.",
                budgetInvalid: "Budget cannot be negative.",
                success: "Project created successfully!",
                error: "Failed to create project.",
            },
            edit: {
                form: "Edit project",
                nameLabel: "Project name",
                customerLabel: "Customer",
                customerPlaceholder: "Select customer...",
                parentLabel: "Parent project",
                noParent: "No parent project",
                budgetLabel: "Budget (minutes)",
                budgetPlaceholder: "Budget (minutes)",
                rateLabel: "Hourly rate (CHF)",
                ratePlaceholder: "Hourly rate (optional)",
            },
            update: {
                validationError: "Please enter a project name and select a customer.",
                budgetInvalid: "Budget cannot be negative.",
                success: "Project saved successfully!",
                error: "Failed to save project.",
            },
            delete: {
                confirm: "Are you sure you want to delete this project?",
                success: "Project deleted successfully!",
                error: "Failed to delete project.",
            },
            integration: {
                title: "Automated integrations",
                name: "Integration name",
                endpoint: "Target URL / endpoint",
                authHeader: "Auth header (optional)",
                authLabel: "Auth",
                active: "Active",
                autoSync: "Auto sync",
                add: "Add integration",
                none: "No integrations yet.",
                trigger: "Test transfer",
                triggered: "Integration triggered successfully!",
                activate: "Activate",
                deactivate: "Deactivate",
                startAuto: "Start auto sync",
                stopAuto: "Stop auto sync",
                lastRun: "Last execution",
                lastRunTitle: "Latest simulation",
                type: "Type",
                nameRequired: "Please provide a name for the integration.",
                created: "Integration saved!",
                updated: "Integration updated!",
                deleted: "Integration deleted!",
                deleteConfirm: "Delete integration?",
                errorLoad: "Failed to load integrations.",
                errorCreate: "Failed to save integration.",
                errorUpdate: "Failed to update integration.",
                errorDelete: "Failed to delete integration.",
                errorTrigger: "Failed to trigger integration.",
            },
            billing: {
                title: "Automated billing",
                selectProject: "Select project",
                includeChildren: "Include subprojects",
                overrideRate: "Override hourly rate (optional)",
                overrideApplied: "Override rate",
                generate: "Generate invoice",
                generated: "Invoice generated!",
                error: "Failed to generate invoice.",
                validation: "Please choose a project and time range.",
                projectColumn: "Project",
                taskColumn: "Task",
                minutes: "Minutes",
                amount: "Amount",
                currency: "Currency",
                totalAmount: "Total amount",
                totalHours: "Billable hours",
                projectRate: "Project rate",
            },
            audit: {
                title: "Compliance & audit",
                user: "User",
                action: "Action",
                target: "Target",
                empty: "No audit entries yet.",
                errorLoad: "Failed to load audit log.",
            },
            budget: {
                unit: "min",
            },
            rate: "Rate",
            parent: "Parent",
            noCustomer: "No customer assigned",
        },

        customer: {
            management: {
                title: "Customers",
            },
            create: {
                title: "Create new customer",
                placeholder: "Name of the new customer",
            },
            list: {
                title: "Existing customers",
            },
            createSuccess: "Customer created successfully!",
            createError: "Failed to create customer.",
            updateSuccess: "Customer saved successfully!",
            updateError: "Failed to save customer.",
            deleteConfirm: "Are you sure you want to delete this customer?",
            deleteSuccess: "Customer deleted successfully!",
            deleteError: "Failed to delete customer.",
        },

        task: {
            management: {
                title: "Tasks",
            },
            projectSelection: "Select project",
            noProjects: {
                title: "No projects yet",
                description: "Create a project first to manage tasks.",
            },
            budget: {
                unit: "min",
            },
            billable: "Billable",
            create: {
                title: "Create new task",
                namePlaceholder: "Name of the new task",
                budgetPlaceholder: "Budget (minutes)",
                billable: "Billable",
                validationError: "Please select a project and enter a name.",
                success: "Task created successfully!",
                error: "Failed to create task.",
            },
            list: {
                title: "Existing tasks",
                empty: "No tasks for this project yet",
                emptyHint: "Create a new task above to get started.",
            },
            edit: {
                budgetPlaceholder: "Budget (minutes)",
                billable: "Billable",
            },
            update: {
                validationError: "Please enter a name.",
                success: "Task saved successfully!",
                error: "Failed to save task.",
            },
            delete: {
                confirm: "Are you sure you want to delete this task?",
                success: "Task deleted successfully!",
                error: "Failed to delete task.",
            },
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
            analyticsButton: "Open analytics view",
            kpis: {
                sectionLabel: "Current key numbers (quick overview)",
                pendingRequests: "Open requests",
                vacationsShort: "Vacation",
                correctionsShort: "Corrections",
                averageOvertime: "Average overtime balance",
                sampleSizePrefix: "Based on: ",
                noBalances: "No balances available",
                negativeBalances: "Negative balances (missing time)",
                topOvertime: "Highest balance (most overtime)",
                unknownUser: "Unknown",
                noPositive: "No positive overtime (extra work)",
                noNegative: "No negative balances",
                actions: {
                    openVacations: "Go to vacation requests",
                    openCorrections: "Go to corrections",
                    focusIssues: "Filter issue cases",
                    openAnalytics: "Open analytics",
                    focusNegative: "Highlight negative balances",
                    focusPositive: "Show overtime leaders",
                },
            },
            focusRibbon: {
                title: "Issue focus",
                subtitleWithCount: "{count} people need review.",
                subtitleClear: "No warnings for any user this week.",
                showAll: "Show all issues",
                reset: "Reset filters",
            },
            issueRibbon: {
                missing: "Missing times",
                incomplete: "Incomplete",
                autoCompleted: "Auto-finished",
                holidayPending: "Holiday pending",
            },
            issueFilters: {
                missing: "Missing punches (no time record)",
                incomplete: "Incomplete days (end missing)",
                autoCompleted: "Auto-finished days (needs check)",
                holidayPending: "Holiday decision open",
                showAll: "Show everyone",
                onlyIssues: "Only show issues",
                groupLabel: "Filter issue types",
                reset: "All types",
            },
            smartOverview: {
                title: "Weekly overview",
                subtitle: "See key metrics and open issues at a glance.",
                showIssuesButton: "Show issue list",
                cards: {
                    active: {
                        title: "Active people",
                        subtitle: "with target time this week",
                    },
                    issues: {
                        title: "People with issues",
                        subtitle: "Need attention",
                    },
                    corrections: {
                        title: "Open corrections",
                        subtitle: "Days to review (missing/unclear)",
                    },
                    negative: {
                        title: "Negative balances",
                        subtitle: "Below expected hours",
                    },
                },
                quickFix: {
                    title: "Quick fixes",
                    subtitle: "Jump straight to the most relevant issues.",
                    empty: "No open issues right now – great job!",
                    action: "Open",
                    labels: {
                        missing: "Missing punches",
                        incomplete: "Incomplete days",
                        autoCompleted: "Auto-completed",
                        holidayPending: "Holiday decision open",
                    },
                },
            },
            actionStream: {
                title: "Prioritized tasks",
                counter: "{count} open",
                empty: "No open tasks right now.",
                vacationRequest: "Vacation request",
                correctionRequest: "Correction request",
                focusUser: "Open in week grid",
                openVacations: "Open all vacation requests",
                openCorrections: "Open all corrections",
                morePending: "+{count} more tasks",
            },
            manageHiddenUsersTooltip: "Manage hidden people",
            hideHiddenUsersList: "Hide list",
            showHiddenUsersList: "Show hidden people",
            hiddenUsersTitle: "Hidden people",
            noHiddenUsers: "No one is hidden right now.",
            unhideUser: "Show again",
            unhideAllUsers: "Show all",
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
            noIssues: "No issues",
            problemTooltips: {
                missingEntries: "Day(s) without a time entry",
                incompleteDays: "Day(s) incomplete (e.g., missing end)",
                autoCompletedDaysUncorrected: "Day(s) auto-finished and unchecked",
                holidayPending: "Holiday option still open",
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
            halfDayShort: "½ day",
            overtimeVacationShort: "OT",
            onVacation: "On vacation",
            correctionModal: {
                approveTitle: "Approve Correction",
                denyTitle: "Deny Correction",
                commentLabel: "Comment for the user:",
                commentPlaceholder: "Why approve or deny?",
                confirmButton: "Confirm",
            },
        },
        adminAnalytics: {
            fetchError: "Could not load analytics.",
            title: "Analytics & Trends",
            subtitle: "Quick overview of workload (how much people work), absences (vacation or sick) and team trends.",
            loading: "Loading data…",
            errorMessage: "An error occurred while loading analytics. Please try again later.",
            daysLabel: "Days",
            overtimeAxis: "Overtime (hours)",
            daysAxis: "Days",
            overtimeTrend: {
                title: "Overtime trend",
                average: "Ø balance",
                topPositive: "Highest balance",
                topNegative: "Lowest balance",
                empty: "No usable overtime data available right now.",
                legend: "People included in the chart",
            },
            filters: {
                title: "Filters",
                rangeLabel: "Timeframe",
                currentRange: "Current range",
                rangeOption: {
                    fourWeeks: "Last 4 weeks",
                    eightWeeks: "Last 8 weeks",
                    twelveWeeks: "Last 12 weeks",
                    twentyFourWeeks: "Last 24 weeks",
                },
                weeksSuffix: "weeks",
                userLabel: "Team members",
                hint: "With no selection the top 5 with the biggest change are shown automatically.",
                selectAll: "Show all",
                reset: "Reset selection",
                currentSelection: "Currently shown",
                noSelectableUsers: "No team members available.",
                selectionEmpty: "No data is available for the selected people in this range.",
                autoSelectionLabel: "Top 5 (automatic)",
                allSelectedLabel: "All",
            },
            absence: {
                title: "Absences per month",
                vacation: "Vacation days",
                sick: "Sick days",
                vacationTotal: "Vacation total",
                sickTotal: "Sick total",
                empty: "No absence data in the selected period yet.",
            },
            vacationPie: {
                title: "Status of vacation requests",
                total: "Total",
                regular: "Approved",
                overtime: "Paid with overtime",
                pending: "Pending (waiting for decision)",
                denied: "Denied",
                empty: "No vacation requests recorded yet.",
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
            includeInTimeTrackingLabel: "Show in time tracking & summaries",
            includeInTimeTrackingEnabled: "Included in time overviews",
            includeInTimeTrackingDisabled: "Excluded from time overviews",
            includeInTimeTrackingHint: "Admins who opt out won't appear in weekly views or balance tables.",
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
        noTask: "No task",
        assignCustomer: {
            editButton: "Edit customers & times",
            projectTag: "Project time",
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
                <p><strong>Updated: 20 February 2025</strong></p>
                <p>We appreciate your interest in Chrono, our time tracking application. Protecting your personal data is a top priority. This privacy notice explains what personal data we process, for which purposes and the rights you have under the EU General Data Protection Regulation (GDPR) and the revised Swiss Federal Act on Data Protection (revFADP).</p>
                <h2>1. Controller and contact</h2>
                <p>The controller is:<br/>
                <strong>Chrono</strong><br/>
                Lettenstrasse 20<br/>
                9122 Mogelsberg<br/>
                Switzerland</p>
                <p><strong>Email:</strong> <a href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a><br/>
                <strong>Phone:</strong> <a href="tel:+41764699122">+41 76 469 91 22</a></p>
                <p>Further company details are available in our <a href="/impressum">imprint</a>.</p>
                <h2>2. Scope and roles</h2>
                <p>This privacy policy applies to the use of our websites, web and desktop applications and to any communication with us as the provider.</p>
                <p>If you use Chrono on behalf of your employer or principal, that organisation usually remains the data controller. We process the related data as a processor in accordance with Art. 28 GDPR and Art. 9 revFADP based on a data processing agreement. In these cases, please also refer to your employer’s privacy notice.</p>
                <h2>3. Categories of personal data, purposes and legal bases</h2>
                <ul>
                    <li><strong>Visits to our websites and technical interfaces</strong><br/>
                    Categories: IP address, date and time of access, browser and device information, referrer URL, error and security logs.<br/>
                    Purposes: providing the website, ensuring stability and security, detecting misuse, troubleshooting.<br/>
                    Legal bases: Art. 6(1)(f) GDPR (legitimate interest in a secure service), Art. 31(1) revFADP.</li>
                    <li><strong>Registration and contract fulfilment</strong><br/>
                    Categories: name, company details, username, email address, password (hashed), role and permission data, settings.<br/>
                    Purposes: creating and administering user accounts, providing the service, customer care.<br/>
                    Legal bases: Art. 6(1)(b) GDPR (performance of a contract or steps prior to entering into a contract), Art. 6(1)(c) GDPR (legal obligations, e.g. tax record keeping), Art. 31(1) revFADP.</li>
                    <li><strong>Use of time tracking and organisation features</strong><br/>
                    Categories: time entries, project and task assignments, absences, vacation and correction requests, duty rosters, NFC card numbers, comments, information on sick notes.<br/>
                    Purposes: delivering the SaaS service, workforce management, statutory record-keeping obligations under labour and social security law.<br/>
                    Legal bases: Art. 6(1)(b) GDPR, Art. 6(1)(c) GDPR, Art. 31(1) revFADP. Where special categories of personal data (e.g. health data relating to sick notes) are processed we rely on Art. 9(2)(b) GDPR and Art. 31(2) revFADP.</li>
                    <li><strong>Payroll module</strong><br/>
                    Categories: salary components, tax and social security details, bank account information, payout data.<br/>
                    Purposes: creating payroll statements, fulfilling employment, tax and social security obligations, documentation.<br/>
                    Legal bases: Art. 6(1)(b) and (c) GDPR, Art. 9(2)(b) GDPR, Art. 31(1) and (2) revFADP.</li>
                    <li><strong>Payment processing</strong><br/>
                    Categories: contract and invoice data, payment status, truncated payment details (e.g. card type, last four digits).<br/>
                    Purposes: processing paid plans, receivables management, bookkeeping.<br/>
                    Legal bases: Art. 6(1)(b) and (f) GDPR (legitimate interest in efficient payment processes), Art. 6(1)(c) GDPR (statutory retention duties), Art. 31(1) revFADP.</li>
                    <li><strong>Support and communication</strong><br/>
                    Categories: name, contact details, content of your enquiry, communication metadata.<br/>
                    Purposes: handling and documenting requests, customer support, service improvement.<br/>
                    Legal bases: Art. 6(1)(b) GDPR (where the enquiry relates to a contract), Art. 6(1)(f) GDPR (legitimate interest in support and documentation), Art. 31(1) revFADP.</li>
                    <li><strong>Monitoring, security and logging</strong><br/>
                    Categories: system and audit logs, user IDs, timestamps of administrative actions.<br/>
                    Purposes: safeguarding our systems, traceability of administrative access, detecting and defending against cyber attacks, managing backups.<br/>
                    Legal bases: Art. 6(1)(f) GDPR, Art. 32 GDPR, Art. 31(1) revFADP.</li>
                    <li><strong>AI assistant (local instance)</strong><br/>
                    Categories: the content of your prompts and the responses returned by the system.<br/>
                    Purposes: answering your questions about the application, improving our help features.<br/>
                    Legal bases: Art. 6(1)(b) GDPR (contract performance), Art. 6(1)(f) GDPR (optimising our services), Art. 31(1) revFADP. Requests are processed solely on our infrastructure; no data are transmitted to external AI providers.</li>
                    <li><strong>Optional integrations (e.g. Google Calendar)</strong><br/>
                    Categories: calendar events and synchronisation metadata.<br/>
                    Purposes: synchronising appointments between Chrono and external services.<br/>
                    Legal bases: Art. 6(1)(a) GDPR (consent), Art. 49(1)(a) GDPR, Art. 31(1) revFADP. We only activate such integrations after you have given explicit consent.</li>
                </ul>
                <h2>4. Recipients and processors</h2>
                <p>We disclose personal data only if permitted by law or if you have given consent. Recipients and categories of processors include:</p>
                <ul>
                    <li><strong>Hosting and infrastructure partners:</strong> data centres located in Switzerland or the European Economic Area that operate our application. The providers we work with are documented in our record of processing activities and implement appropriate technical and organisational measures.</li>
                    <li><strong>Payment service provider:</strong> Stripe Payments Europe Ltd., 1 Grand Canal Street Lower, Dublin 2, Ireland (Stripe). Stripe may transfer data to affiliated companies in the United States. We have concluded the EU Standard Contractual Clauses with Stripe.</li>
                    <li><strong>Email and communication providers:</strong> SMTP and support services that deliver support tickets and system notifications on our behalf.</li>
                    <li><strong>IT service providers:</strong> maintenance, hosting, backup and support partners who assist us in running the application and who are contractually bound to confidentiality.</li>
                    <li><strong>Authorities, courts or external advisors:</strong> where necessary to comply with legal obligations or to establish, exercise or defend legal claims.</li>
                </ul>
                <h2>5. International data transfers</h2>
                <p>We only transfer personal data outside Switzerland or the EEA if an adequate level of protection is ensured or appropriate safeguards are in place. For Stripe we rely on the EU Standard Contractual Clauses and supplementary safeguards. Optional integrations such as Google Calendar (USA) require your explicit consent and we will inform you separately about potential risks.</p>
                <h2>6. Storage periods</h2>
                <ul>
                    <li>Account data are stored for the duration of the contractual relationship. After termination we delete or anonymise the data in line with contractual arrangements and statutory retention periods.</li>
                    <li>Time tracking, project and payroll data are retained for up to ten years to comply with labour, tax and commercial law obligations.</li>
                    <li>Technical log files and security logs are usually deleted after 30 days unless a security incident requires longer retention.</li>
                    <li>Support and communication records are deleted 24 months after the ticket has been closed unless statutory retention requirements apply.</li>
                </ul>
                <p>Once the processing purpose ceases to apply we review, at least annually, whether deletion or anonymisation is possible in line with our deletion concept.</p>
                <h2>7. Data security</h2>
                <p>We implement appropriate technical and organisational measures pursuant to Art. 32 GDPR and Art. 8 revFADP to protect your data against loss, misuse and unauthorised access. Measures include encrypted data transmission (TLS), role-based access control, logging of administrative access, regular backups and security updates.</p>
                <h2>8. Your rights</h2>
                <p>You have the following rights:</p>
                <ul>
                    <li>Access to the personal data we store about you (Art. 15 GDPR, Art. 25 revFADP)</li>
                    <li>Rectification of inaccurate data (Art. 16 GDPR, Art. 32 revFADP)</li>
                    <li>Erasure or restriction of processing (Art. 17 and 18 GDPR, Art. 32 revFADP)</li>
                    <li>Objection to processing based on legitimate interests (Art. 21 GDPR)</li>
                    <li>Data portability (Art. 20 GDPR, Art. 28 revFADP)</li>
                    <li>Withdrawal of consent with effect for the future (Art. 7(3) GDPR, Art. 6(6) revFADP)</li>
                </ul>
                <p>You can exercise your rights at any time by contacting <a href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a>. We usually respond within one month.</p>
                <h2>9. Right to lodge a complaint</h2>
                <p>You may lodge a complaint with a supervisory authority. In Switzerland the competent authority is the Federal Data Protection and Information Commissioner (Feldeggweg 1, CH-3003 Bern, <a href="https://www.edoeb.admin.ch">www.edoeb.admin.ch</a>). Individuals in the EU/EEA may also contact the supervisory authority at their habitual residence, place of work or the place of the alleged infringement.</p>
                <h2>10. Obligation to provide data</h2>
                <p>Certain information is required in order to use Chrono (e.g. registration and contract details). Without this data we are unable to provide the contractual services. Optional fields are marked accordingly.</p>
                <h2>11. Automated decision-making</h2>
                <p>We do not use automated decision-making or profiling within the meaning of Art. 22 GDPR.</p>
                <h2>12. Changes to this privacy policy</h2>
                <p>We update this privacy policy whenever new features are introduced or legal requirements change. The current version is always available at <a href="/datenschutz">www.chrono-logisch.ch/datenschutz</a>.</p>
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
