// src/pages/LandingPage.jsx
import React, { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/LandingPageScoped.css";
import { useTranslation } from "../context/LanguageContext";
import { useNotification } from "../context/NotificationContext";
import { useAuth } from "../context/AuthContext";
import api from "../utils/api";
import { trackAnalyticsSignal } from "../utils/analytics";

const LinkedinIcon = () => (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M6.94 8.75H3.56v10.69h3.38V8.75zM5.25 7.29a1.95 1.95 0 1 0-.02-3.9 1.95 1.95 0 0 0 .02 3.9zM20.44 13.58c0-3.1-1.65-4.54-3.85-4.54a3.32 3.32 0 0 0-3.01 1.66h-.05V8.75h-3.24v10.69h3.38v-5.29c0-1.39.26-2.74 1.99-2.74 1.7 0 1.72 1.59 1.72 2.83v5.2h3.38l-.32-5.86z" />
    </svg>
);

const InstagramIcon = () => (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M7.7 2.7h8.6a5 5 0 0 1 5 5v8.6a5 5 0 0 1-5 5H7.7a5 5 0 0 1-5-5V7.7a5 5 0 0 1 5-5zm0 1.9a3.1 3.1 0 0 0-3.1 3.1v8.6a3.1 3.1 0 0 0 3.1 3.1h8.6a3.1 3.1 0 0 0 3.1-3.1V7.7a3.1 3.1 0 0 0-3.1-3.1H7.7zm4.3 3.17a4.23 4.23 0 1 1 0 8.46 4.23 4.23 0 0 1 0-8.46zm0 1.9a2.33 2.33 0 1 0 0 4.66 2.33 2.33 0 0 0 0-4.66zm4.56-2.77a.99.99 0 1 1 0 1.98.99.99 0 0 1 0-1.98z" />
    </svg>
);

const TrustPill = ({ icon, label, value }) => (
    <li className="lp-trust-pill">
        <span className="lp-trust-icon" aria-hidden="true">{icon}</span>
        <div>
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    </li>
);

const ProductMockup = ({ t }) => (
    <div className="lp-product-mockup lp-real-dashboard-mockup" aria-label={t("landing.productMockup.label", "Chrono Produktvorschau")}>
        <div className="lp-mockup-topbar">
            <span className="lp-window-dot"></span>
            <span className="lp-window-dot"></span>
            <span className="lp-window-dot"></span>
            <strong>Chrono</strong>
        </div>
        <div className="lp-real-dashboard-shell">
            <header className="lp-real-dashboard-header">
                <div>
                    <h3>{t("landing.realMockup.header", "Willkommen, Christopher")}</h3>
                    <div className="lp-real-personal-info">
                        <span><strong>{t("landing.realMockup.username", "Benutzername")}:</strong> christopher</span>
                        <span><strong>{t("landing.realMockup.balance", "Überstundensaldo")}:</strong> +05:15</span>
                    </div>
                </div>
                <span className="lp-real-primary-btn">{t("landing.realMockup.print", "Bericht drucken")}</span>
            </header>

            <section className="lp-real-content-section">
                <h4>{t("landing.realMockup.weeklyOverview", "Wochenübersicht")}</h4>

                <div className="lp-real-punch-section">
                    <strong>{t("landing.realMockup.manualPunch", "Manuelles Stempeln")}</strong>
                    <span>{t("landing.realMockup.punchButton", "Jetzt stempeln")}</span>
                </div>

                <div className="lp-real-week-navigation">
                    <span>{t("landing.realMockup.prevWeek", "← Vorige Woche")}</span>
                    <strong>27.04.2026</strong>
                    <span>{t("landing.realMockup.nextWeek", "Nächste Woche →")}</span>
                    <em>{t("landing.realMockup.currentWeek", "Aktuelle Woche")}</em>
                </div>

                <div className="lp-real-summary-grid">
                    <div>
                        <span>{t("landing.realMockup.actual", "Ist-Zeit")}</span>
                        <strong>38:15</strong>
                    </div>
                    <div>
                        <span>{t("landing.realMockup.expected", "Soll-Zeit")}</span>
                        <strong>34:00</strong>
                    </div>
                    <div>
                        <span>{t("landing.realMockup.weekBalance", "Wochensaldo")}</span>
                        <strong className="lp-real-positive">+04:15</strong>
                    </div>
                </div>

                <div className="lp-real-week-grid">
                    <article className="lp-real-day-card">
                        <header>
                            <strong>{t("landing.realMockup.monday", "Montag")}</strong>
                            <span>27.04.</span>
                        </header>
                        <ul>
                            <li><span>{t("landing.realMockup.clockIn", "Einstempeln")}</span><strong>07:58</strong></li>
                            <li><span>{t("landing.realMockup.clockOut", "Ausstempeln")}</span><strong>12:04</strong></li>
                            <li><span>{t("landing.realMockup.clockIn", "Einstempeln")}</span><strong>12:42</strong></li>
                            <li><span>{t("landing.realMockup.clockOut", "Ausstempeln")}</span><strong>17:01</strong></li>
                        </ul>
                        <footer>
                            <span>{t("landing.realMockup.worked", "Gearbeitet")}</span>
                            <strong>08:25</strong>
                        </footer>
                    </article>

                    <article className="lp-real-day-card lp-real-project-day">
                        <header>
                            <strong>{t("landing.realMockup.tuesday", "Dienstag")}</strong>
                            <span>28.04.</span>
                        </header>
                        <div className="lp-real-badges">
                            <span>{t("landing.realMockup.projectBadge", "Kundenprojekt")}</span>
                        </div>
                        <ul>
                            <li><span>{t("landing.realMockup.clockIn", "Einstempeln")}</span><strong>08:10</strong></li>
                            <li><span>{t("landing.realMockup.clockOut", "Ausstempeln")}</span><strong>16:44</strong></li>
                        </ul>
                        <footer>
                            <span>{t("landing.realMockup.worked", "Gearbeitet")}</span>
                            <strong>08:04</strong>
                        </footer>
                    </article>
                </div>
            </section>
        </div>
    </div>
);

const AdminDashboardMockup = ({ t }) => (
    <div className="lp-product-mockup lp-admin-dashboard-mockup" aria-label={t("landing.adminMockup.label", "Chrono Admin-Dashboard Vorschau")}>
        <div className="lp-mockup-topbar">
            <span className="lp-window-dot"></span>
            <span className="lp-window-dot"></span>
            <span className="lp-window-dot"></span>
            <strong>Chrono</strong>
        </div>
        <div className="lp-admin-dashboard-shell">
            <header className="lp-admin-dashboard-header">
                <div>
                    <h3>{t("landing.adminMockup.title", "Admin-Dashboard")}</h3>
                    <p>{t("landing.adminMockup.loggedIn", "Eingeloggt als admin")}</p>
                </div>
                <span>{t("landing.adminMockup.command", "Befehlspalette öffnen")}</span>
            </header>

            <div className="lp-admin-module-row">
                <span>{t("landing.adminMockup.moduleUsers", "Benutzerverwaltung")}</span>
                <span>{t("landing.adminMockup.moduleSchedule", "Dienstplan")}</span>
                <span>{t("landing.adminMockup.modulePayroll", "Abrechnungen")}</span>
            </div>

            <section className="lp-admin-kpi-grid" aria-label={t("landing.adminMockup.kpiLabel", "Aktuelle Kennzahlen")}>
                <article className="lp-admin-kpi-card lp-admin-kpi-warning">
                    <span>{t("landing.adminMockup.pendingRequests", "Offene Anträge")}</span>
                    <strong>4</strong>
                    <em>{t("landing.adminMockup.pendingMeta", "Urlaub: 2 · Korrekturen: 2")}</em>
                </article>
                <article className="lp-admin-kpi-card lp-admin-kpi-positive">
                    <span>{t("landing.adminMockup.averageOvertime", "Ø Überstundensaldo")}</span>
                    <strong>+02:35</strong>
                    <em>{t("landing.adminMockup.averageMeta", "Grundlage: 18")}</em>
                </article>
                <article className="lp-admin-kpi-card lp-admin-kpi-critical">
                    <span>{t("landing.adminMockup.negativeBalances", "Negative Salden")}</span>
                    <strong>1</strong>
                    <em>{t("landing.adminMockup.negativeMeta", "sarah: -00:25")}</em>
                </article>
                <article className="lp-admin-kpi-card lp-admin-kpi-info">
                    <span>{t("landing.adminMockup.topOvertime", "Höchster Saldo")}</span>
                    <strong>+12:40</strong>
                    <em>{t("landing.adminMockup.topMeta", "marc")}</em>
                </article>
            </section>

            <section className="lp-admin-week-panel">
                <div className="lp-admin-section-header">
                    <div>
                        <h4>{t("landing.adminMockup.weekTitle", "Team-Wochenübersicht")}</h4>
                        <p>{t("landing.adminMockup.weekRange", "27.04.2026 - 03.05.2026")}</p>
                    </div>
                    <span>{t("landing.adminMockup.print", "Drucken")}</span>
                </div>
                <div className="lp-admin-week-navigation">
                    <span>{t("landing.adminMockup.prevWeek", "← Vorige Woche")}</span>
                    <strong>27.04.2026</strong>
                    <span>{t("landing.adminMockup.nextWeek", "Nächste Woche →")}</span>
                </div>
                <div className="lp-admin-table" aria-hidden="true">
                    <div className="lp-admin-table-head">
                        <span>{t("landing.adminMockup.employee", "Mitarbeitende")}</span>
                        <span>Mo</span>
                        <span>Di</span>
                        <span>Mi</span>
                        <span>{t("landing.adminMockup.balance", "Saldo")}</span>
                    </div>
                    <div className="lp-admin-table-row">
                        <strong>Christopher</strong>
                        <span>08:25</span>
                        <span>08:04</span>
                        <span>{t("landing.adminMockup.vacation", "Urlaub")}</span>
                        <em>+04:15</em>
                    </div>
                    <div className="lp-admin-table-row is-warning">
                        <strong>Sarah</strong>
                        <span>07:55</span>
                        <span>08:10</span>
                        <span>08:00</span>
                        <em>-00:25</em>
                    </div>
                    <div className="lp-admin-table-row">
                        <strong>Marc</strong>
                        <span>{t("landing.adminMockup.sick", "Krankheit")}</span>
                        <span>08:20</span>
                        <span>08:15</span>
                        <em>+01:10</em>
                    </div>
                </div>
            </section>
        </div>
    </div>
);

const FounderCard = ({ t }) => (
    <aside className="lp-founder-card" aria-label={t("landing.founder.label", "Gründerhinweis")}>
        <img
            src="/img/Ich.png"
            alt={t("landing.hero.photoAlt", "Christopher Siefert")}
            loading="lazy"
        />
        <div>
            <strong>{t("landing.founder.title", "Entwickelt von Christopher Siefert")}</strong>
            <p>{t("landing.founder.text", "Für Unternehmen, die klare Prozesse statt Excel-Chaos wollen.")}</p>
        </div>
    </aside>
);

const FounderTrustSection = ({ t }) => {
    const trustSignals = [
        t("landing.founderTrust.signal1", "Direkte Betreuung bei Demo, Einführung und Fragen"),
        t("landing.founderTrust.signal2", "Schnelle Weiterentwicklung aus echtem Kundenfeedback"),
        t("landing.founderTrust.signal3", "Unterstützung beim Wechsel aus bestehenden Programmen"),
    ];

    return (
        <section className="lp-section lp-founder-trust-section" aria-labelledby="founder-trust-title">
            <div className="lp-container lp-founder-trust-layout">
                <div className="lp-founder-trust-media">
                    <img src="/img/Ich.png" alt={t("landing.hero.photoAlt", "Christopher Siefert")} loading="lazy" />
                    <div>
                        <strong>Christopher Siefert</strong>
                        <span>{t("landing.founderTrust.role", "Gründer & Entwickler von Chrono")}</span>
                    </div>
                </div>

                <div className="lp-founder-trust-copy">
                    <span className="lp-kicker">{t("landing.founderTrust.kicker", "Persönlich betreut")}</span>
                    <h2 id="founder-trust-title">
                        {t("landing.founderTrust.title", "Entwickelt in der Schweiz - mit direktem Ansprechpartner.")}
                    </h2>
                    <p>
                        {t(
                            "landing.founderTrust.text",
                            "Chrono-Logisch wird von Christopher Siefert in Mogelsberg, St. Gallen entwickelt. Ziel ist eine zentrale, verständliche und praxisnahe Plattform für Unternehmen, die Zeit, HR, Lohnprozesse, CRM, Finanzen, Lager und Reporting in einem System bündeln möchten."
                        )}
                    </p>
                    <ul className="lp-founder-trust-list" role="list">
                        {trustSignals.map((signal) => (
                            <li key={signal}>{signal}</li>
                        ))}
                    </ul>
                    <div className="lp-founder-trust-actions">
                        <a className="lp-btn lp-primary" href="#kontakt">
                            {t("landing.founderTrust.contact", "Kontakt aufnehmen")}
                        </a>
                        <Link className="lp-btn lp-secondary" to="/ueber-chrono-logisch">
                            {t("landing.founderTrust.about", "Über Chrono-Logisch")}
                        </Link>
                    </div>
                </div>
            </div>
        </section>
    );
};

const DashboardPreviewVisual = ({ type, t }) => {
    const weekDays = [
        t("landing.features.visual.mondayShort", "Mo"),
        t("landing.features.visual.tuesdayShort", "Di"),
        t("landing.features.visual.wednesdayShort", "Mi"),
        t("landing.features.visual.thursdayShort", "Do"),
        t("landing.features.visual.fridayShort", "Fr"),
        t("landing.features.visual.saturdayShort", "Sa"),
        t("landing.features.visual.sundayShort", "So"),
    ];

    if (type === "week") {
        return (
            <div className="lp-dashboard-preview lp-dashboard-preview-week" aria-hidden="true">
                <div className="lp-preview-kpis">
                    <span><em>{t("landing.dashboardPreview.actual", "Ist-Zeit")}</em><strong>38:15</strong></span>
                    <span><em>{t("landing.dashboardPreview.expected", "Soll-Zeit")}</em><strong>34:00</strong></span>
                    <span><em>{t("landing.dashboardPreview.balance", "Wochensaldo")}</em><strong className="is-positive">+04:15</strong></span>
                </div>
                <div className="lp-preview-day-card">
                    <header>
                        <strong>{t("landing.dashboardPreview.monday", "Montag, 27.04.")}</strong>
                        <span>{t("landing.dashboardPreview.worked", "08:25 gearbeitet")}</span>
                    </header>
                    <ul>
                        <li><span>{t("landing.dashboardPreview.clockIn", "Einstempeln")}</span><strong>07:58</strong></li>
                        <li><span>{t("landing.dashboardPreview.clockOut", "Ausstempeln")}</span><strong>12:04</strong></li>
                        <li><span>{t("landing.dashboardPreview.clockIn", "Einstempeln")}</span><strong>12:42</strong></li>
                        <li><span>{t("landing.dashboardPreview.clockOut", "Ausstempeln")}</span><strong>17:01</strong></li>
                    </ul>
                    <footer>{t("landing.dashboardPreview.correctionButton", "Korrektur beantragen")}</footer>
                </div>
            </div>
        );
    }

    if (type === "corrections") {
        return (
            <div className="lp-dashboard-preview lp-dashboard-preview-corrections" aria-hidden="true">
                <div className="lp-preview-panel-header">
                    <strong>{t("landing.dashboardPreview.correctionPanel", "Korrekturanträge")}</strong>
                    <span>{t("landing.dashboardPreview.openCorrections", "2 offen")}</span>
                </div>
                <ul>
                    <li>
                        <span className="is-pending">{t("landing.dashboardPreview.pending", "Offen")}</span>
                        <strong>28.04.2026</strong>
                        <p>{t("landing.dashboardPreview.correctionOne", "Ausstempeln 16:44 auf 17:01 ändern")}</p>
                    </li>
                    <li>
                        <span className="is-approved">{t("landing.dashboardPreview.approved", "Genehmigt")}</span>
                        <strong>25.04.2026</strong>
                        <p>{t("landing.dashboardPreview.correctionTwo", "Vergessene Mittagspause ergänzt")}</p>
                    </li>
                </ul>
            </div>
        );
    }

    return (
        <div className="lp-dashboard-preview lp-dashboard-preview-vacation" aria-hidden="true">
            <div className="lp-preview-panel-header">
                <strong>{t("landing.dashboardPreview.vacationMonth", "April 2026")}</strong>
                <span>{t("landing.dashboardPreview.openVacation", "1 Antrag offen")}</span>
            </div>
            <div className="lp-preview-calendar">
                {weekDays.map((day) => <strong key={day}>{day}</strong>)}
                {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13].map((day) => (
                    <span key={day} className={[3, 4, 10].includes(day) ? "is-vacation" : [8].includes(day) ? "is-sick" : ""}></span>
                ))}
            </div>
            <div className="lp-preview-request-row">
                <span>{t("landing.dashboardPreview.vacationRequest", "Urlaub 02.05. - 03.05.")}</span>
                <strong>{t("landing.dashboardPreview.inReview", "in Prüfung")}</strong>
            </div>
        </div>
    );
};

const DashboardPreviewCard = ({ code, title, text, visual, active, t }) => (
    <article className={`lp-showcase-card lp-dashboard-preview-card ${active ? "is-active" : ""}`}>
        <div className="lp-card-topline">
            <span className="lp-chip-code">{code}</span>
            <span className="lp-dashboard-preview-tag">{t("landing.dashboardPreview.tag", "wie im Dashboard")}</span>
        </div>
        <h3>{title}</h3>
        <p>{text}</p>
        <DashboardPreviewVisual type={visual} t={t} />
    </article>
);

const RealProcessVisual = ({ type, t }) => {
    const rowSets = {
        teamAccess: [
            [t("landing.features.visual.users", "Mitarbeitende"), "18", t("landing.features.visual.active", "aktiv")],
            [t("landing.features.visual.roles", "Rollen"), t("landing.features.visual.roleMix", "Admin / Team / User"), t("landing.features.visual.permissions", "Rechte")],
            [t("landing.features.visual.pages", "Freigegebene Seiten"), "12", t("landing.features.visual.visible", "sichtbar")],
        ],
        schedule: [
            [t("landing.features.visual.mondayShort", "Mo"), t("landing.features.visual.earlyShift", "Frühdienst"), "07:00-15:30"],
            [t("landing.features.visual.tuesdayShort", "Di"), t("landing.features.visual.lateShift", "Spätdienst"), "12:00-20:00"],
            [t("landing.features.visual.print", "Drucken"), t("landing.features.visual.weekPlan", "Dienstplan"), "PDF"],
        ],
        payslips: [
            [t("landing.features.visual.payrollMonth", "Abrechnung März"), t("landing.features.visual.ready", "bereit"), "PDF"],
            [t("landing.features.visual.employeeDocs", "Mitarbeiterdokumente"), "18", t("landing.features.visual.export", "Export")],
            [t("landing.features.visual.archive", "Archiv"), "2026", t("landing.features.visual.checked", "geprüft")],
        ],
        projects: [
            [t("landing.features.visual.customer", "Kunde"), "Muster AG", t("landing.features.visual.active", "aktiv")],
            [t("landing.features.visual.project", "Projekt"), "Website Relaunch", "42:30"],
            [t("landing.features.visual.task", "Aufgabe"), "Support", "08:10"],
        ],
        accounting: [
            [t("landing.features.visual.receipts", "Belege"), "3", t("landing.features.visual.open", "offen")],
            [t("landing.features.visual.revenue", "Umsatz"), "12'480 CHF", t("landing.features.visual.month", "Monat")],
            [t("landing.features.visual.bookingExport", "Buchhaltung"), "CSV", t("landing.features.visual.export", "Export")],
        ],
        banking: [
            [t("landing.features.visual.payments", "Zahlungen"), "5", t("landing.features.visual.ready", "bereit")],
            [t("landing.features.visual.bank", "Bank"), t("landing.features.visual.releases", "Freigaben"), t("landing.features.visual.openPayments", "2 offen")],
            [t("landing.features.visual.status", "Status"), t("landing.features.visual.synced", "abgeglichen"), "OK"],
        ],
        supplyChain: [
            [t("landing.features.visual.stock", "Bestand"), t("landing.features.visual.stockItems", "12 Artikel"), t("landing.features.visual.lowStock", "2 tief")],
            [t("landing.features.visual.purchase", "Einkauf"), "4", t("landing.features.visual.open", "offen")],
            [t("landing.features.visual.service", "Service"), "2 Tickets", t("landing.features.visual.active", "aktiv")],
        ],
        knowledge: [
            [t("landing.features.visual.documents", "Dokumente"), "12", t("landing.features.visual.internal", "intern")],
            [t("landing.features.visual.guidelines", "Richtlinien"), t("landing.features.visual.vacation", "Urlaub"), t("landing.features.visual.active", "aktiv")],
            [t("landing.features.visual.knowledgeBase", "Wissensbasis"), t("landing.features.visual.ai", "KI"), t("landing.features.visual.searchable", "durchsuchbar")],
        ],
        chronoTwo: [
            ["Chrono 2.0", t("landing.features.visual.workspace", "Arbeitsbereich"), t("landing.features.visual.enabled", "aktiv")],
            [t("landing.features.visual.processes", "Prozesse"), t("landing.features.visual.new", "neu"), t("landing.features.visual.module", "Modul")],
            [t("landing.features.visual.preview", "Vorschau"), t("landing.features.visual.admin", "Admin"), "Beta"],
        ],
        settings: [
            [t("landing.features.visual.company", "Firma"), "CH / DE", t("landing.features.visual.ready", "bereit")],
            [t("landing.features.visual.workModels", "Arbeitsmodelle"), "Normal / %", t("landing.features.visual.configured", "konfiguriert")],
            [t("landing.features.visual.modules", "Module"), "11", t("landing.features.visual.assigned", "zugewiesen")],
        ],
    };

    if (type === "timeDashboard") {
        return (
            <div className="lp-process-visual lp-real-module-visual lp-real-module-time" aria-hidden="true">
                <div className="lp-real-module-flow">
                    <span>{t("landing.features.visual.clock", "Stempeln")}</span>
                    <i></i>
                    <span>{t("landing.features.visual.weekOverview", "Wochenübersicht")}</span>
                    <i></i>
                    <span>{t("landing.features.visual.correction", "Korrektur")}</span>
                </div>
                <div className="lp-real-module-kpis">
                    <span><strong>38:15</strong><small>{t("landing.features.visual.actualTime", "Ist-Zeit")}</small></span>
                    <span><strong>34:00</strong><small>{t("landing.features.visual.expectedTime", "Soll-Zeit")}</small></span>
                    <span><strong>+04:15</strong><small>{t("landing.features.visual.weekBalance", "Wochensaldo")}</small></span>
                </div>
                <div className="lp-real-week-table">
                    <div>
                        <strong>{t("landing.features.visual.monday", "Montag")}</strong>
                        <span>07:58</span>
                        <span>12:04</span>
                        <span>12:42</span>
                        <span>17:01</span>
                        <em>08:25</em>
                    </div>
                    <div>
                        <strong>{t("landing.features.visual.tuesday", "Dienstag")}</strong>
                        <span>08:10</span>
                        <span>16:44</span>
                        <span>{t("landing.features.visual.customerProject", "Kundenprojekt")}</span>
                        <em>08:04</em>
                    </div>
                </div>
            </div>
        );
    }

    if (type === "crm") {
        return (
            <div className="lp-process-visual lp-real-module-visual lp-real-module-pipeline" aria-hidden="true">
                {[
                    [t("landing.features.visual.leads", "Leads"), "4"],
                    [t("landing.features.visual.offers", "Angebote"), "2"],
                    [t("landing.features.visual.won", "Gewonnen"), "1"],
                ].map(([label, value]) => (
                    <div key={label}>
                        <span>{label}</span>
                        <strong>{value}</strong>
                    </div>
                ))}
            </div>
        );
    }

    const rows = rowSets[type] || rowSets.settings;

    return (
        <div className={`lp-process-visual lp-real-module-visual lp-real-module-${type}`} aria-hidden="true">
            <div className="lp-real-module-list">
                {rows.map(([label, value, meta]) => (
                    <div key={`${label}-${value}`}>
                        <span>{label}</span>
                        <strong>{value}</strong>
                        <em>{meta}</em>
                    </div>
                ))}
            </div>
            {type === "schedule" && (
                <div className="lp-real-schedule-strip">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            )}
            {type === "supplyChain" && (
                <div className="lp-real-stock-strip">
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            )}
        </div>
    );
};

const ProcessVisual = ({ type, t }) => {
    if (type === "time") {
        return (
            <div className="lp-process-visual lp-process-time" aria-hidden="true">
                <div className="lp-process-flow">
                    <span>{t("landing.features.visual.clock", "Stempeln")}</span>
                    <i></i>
                    <span>{t("landing.features.visual.approval", "Freigabe")}</span>
                    <i></i>
                    <span>{t("landing.features.visual.payroll", "Lohn")}</span>
                </div>
                <div className="lp-process-metrics">
                    <span><strong>38</strong><small>{t("landing.features.visual.clockedToday", "heute gestempelt")}</small></span>
                    <span><strong>98%</strong><small>{t("landing.features.visual.completeTimes", "Zeiten vollständig")}</small></span>
                </div>
                <div className="lp-process-chart">
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                </div>
            </div>
        );
    }

    if (type === "calendar") {
        const weekDays = [
            t("landing.features.visual.mondayShort", "Mo"),
            t("landing.features.visual.tuesdayShort", "Di"),
            t("landing.features.visual.wednesdayShort", "Mi"),
            t("landing.features.visual.thursdayShort", "Do"),
            t("landing.features.visual.fridayShort", "Fr"),
            t("landing.features.visual.saturdayShort", "Sa"),
        ];

        return (
            <div className="lp-process-visual lp-process-calendar" aria-hidden="true">
                <div className="lp-calendar-badge">{t("landing.features.visual.openApprovals", "3 Freigaben offen")}</div>
                <div className="lp-calendar-grid">
                    {weekDays.map((day) => <strong key={day}>{day}</strong>)}
                    {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11].map((day) => (
                        <span key={day} className={[2, 3, 7, 8].includes(day) ? "is-active" : ""}></span>
                    ))}
                </div>
            </div>
        );
    }

    if (type === "payroll") {
        return (
            <div className="lp-process-visual lp-process-document" aria-hidden="true">
                <div className="lp-document-sheet">
                    <span>{t("landing.features.visual.payrollRun", "Lohnlauf März")}</span>
                    <strong>{t("landing.features.visual.readyReview", "Bereit zur Prüfung")}</strong>
                    <div>
                        <em>PDF</em>
                        <em>Export</em>
                    </div>
                </div>
            </div>
        );
    }

    if (type === "reports") {
        return (
            <div className="lp-process-visual lp-process-report" aria-hidden="true">
                <span><strong>{t("landing.features.visual.expectedShort", "Soll")}</strong><i></i></span>
                <span><strong>{t("landing.features.visual.actualShort", "Ist")}</strong><i></i></span>
                <span><strong>{t("landing.features.visual.absences", "Abwesenheiten")}</strong><i></i></span>
            </div>
        );
    }

    if (type === "automation") {
        return (
            <div className="lp-process-visual lp-process-automation" aria-hidden="true">
                <span>{t("landing.features.visual.missingTime", "Fehlende Zeit")}</span>
                <i></i>
                <span>{t("landing.features.visual.reminder", "Erinnerung")}</span>
                <i></i>
                <span>{t("landing.features.visual.approval", "Freigabe")}</span>
            </div>
        );
    }

    if (type === "security") {
        return (
            <div className="lp-process-visual lp-process-roles" aria-hidden="true">
                <span>{t("landing.features.visual.admin", "Admin")}</span>
                <span>{t("landing.features.visual.manager", "Manager")}</span>
                <span>{t("landing.features.visual.employee", "Mitarbeiter")}</span>
            </div>
        );
    }

    return (
        <div className="lp-process-visual lp-process-phone" aria-hidden="true">
            <div>
                <span></span>
                <strong>08:10</strong>
                <em>{t("landing.features.visual.mobilePunch", "Stempeln")}</em>
            </div>
        </div>
    );
};

const ProcessModuleCard = ({ code, label, title, text, visual, tone = "blue", primary = false, t }) => (
    <article className={`lp-process-card lp-process-${tone} ${primary ? "lp-process-primary" : ""}`}>
        <div className="lp-card-topline">
            <span className="lp-chip-code">{code}</span>
            <span className="lp-process-label">{label}</span>
        </div>
        <h3>{title}</h3>
        <p>{text}</p>
        <RealProcessVisual type={visual} t={t} />
    </article>
);

const ModulePill = ({ code, name, note }) => (
    <li className="lp-module-pill">
        <span>{code}</span>
        <strong>{name}</strong>
        {note ? <em>{note}</em> : null}
    </li>
);

const ModuleClusterCard = ({ title, text, modules }) => (
    <article className="lp-module-cluster-card">
        <header>
            <h3>{title}</h3>
            <span>{text}</span>
        </header>
        <ul className="lp-module-pill-list" role="list">
            {modules.map((module) => (
                <ModulePill key={`${module.code}-${module.name}`} {...module} />
            ))}
        </ul>
    </article>
);

const ModuleFlowStep = ({ n, title, text, modules }) => (
    <article className="lp-module-flow-step">
        <div className="lp-module-flow-step-head">
            <span className="lp-module-step-number">{n}</span>
            <p>{text}</p>
        </div>
        <h3>{title}</h3>
        <div className="lp-module-step-modules">
            {modules.map((module) => (
                <span key={module}>{module}</span>
            ))}
        </div>
    </article>
);

const StepCard = ({ n, title, text }) => (
    <article className="lp-step-card">
        <span className="lp-step-number"><span>{n}</span></span>
        <h3>{title}</h3>
        <p>{text}</p>
    </article>
);

const OutcomeCard = ({ code, title, before, after, t }) => (
    <article className="lp-outcome-card">
        <span className="lp-outcome-code" aria-hidden="true">{code}</span>
        <h3>{title}</h3>
        <div>
            <span>{t("landing.outcomes.beforeLabel", "Heute")}</span>
            <p>{before}</p>
        </div>
        <div className="lp-outcome-after">
            <span>{t("landing.outcomes.afterLabel", "Mit Chrono")}</span>
            <p>{after}</p>
        </div>
    </article>
);

const SecurityItem = ({ code, title, text }) => (
    <article className="lp-security-item">
        <span aria-hidden="true">{code}</span>
        <div>
            <h3>{title}</h3>
            <p>{text}</p>
        </div>
    </article>
);

const SavingsCalculator = ({ t }) => {
    const [employees, setEmployees] = useState(10);
    const [minutesPerWeek, setMinutesPerWeek] = useState(30);
    const [hourlyRate, setHourlyRate] = useState(60);

    const normalizedEmployees = Math.max(1, Number(employees) || 0);
    const normalizedMinutes = Math.max(0, Number(minutesPerWeek) || 0);
    const normalizedHourlyRate = Math.max(0, Number(hourlyRate) || 0);
    const monthlyAdminHours = normalizedEmployees * normalizedMinutes * 4.33 / 60;
    const currentCost = monthlyAdminHours * normalizedHourlyRate;
    const chronoBaseCost = normalizedEmployees * 5;
    const difference = Math.max(0, currentCost - chronoBaseCost);
    const formatMoney = (value) => new Intl.NumberFormat("de-CH", {
        style: "currency",
        currency: "CHF",
        maximumFractionDigits: 0,
    }).format(value);

    return (
        <div className="lp-calculator" aria-labelledby="savings-calculator-title">
            <div className="lp-calculator-copy">
                <span className="lp-kicker">{t("landing.calculator.kicker", "Aufwand sichtbar machen")}</span>
                <h2 id="savings-calculator-title">{t("landing.calculator.title", "Was kostet Ihre heutige Administration?")}</h2>
                <p>{t("landing.calculator.text", "Vergleichen Sie Ihren geschätzten monatlichen Verwaltungsaufwand mit dem Chrono-Basispaket.")}</p>
                <div className="lp-calculator-inputs">
                    <label>
                        <span>{t("landing.calculator.employees", "Mitarbeitende")}</span>
                        <input type="number" min="1" max="500" value={employees} onChange={(event) => setEmployees(event.target.value)} />
                    </label>
                    <label>
                        <span>{t("landing.calculator.minutes", "Admin-Minuten pro Person/Woche")}</span>
                        <input type="number" min="0" max="600" step="5" value={minutesPerWeek} onChange={(event) => setMinutesPerWeek(event.target.value)} />
                    </label>
                    <label>
                        <span>{t("landing.calculator.rate", "Interner Stundensatz (CHF)")}</span>
                        <input type="number" min="0" max="500" step="5" value={hourlyRate} onChange={(event) => setHourlyRate(event.target.value)} />
                    </label>
                </div>
            </div>
            <aside className="lp-calculator-result" aria-live="polite">
                <div>
                    <span>{t("landing.calculator.currentCost", "Geschätzter heutiger Aufwand")}</span>
                    <strong>{formatMoney(currentCost)}</strong>
                    <em>{monthlyAdminHours.toFixed(1)} {t("landing.calculator.hours", "Std./Monat")}</em>
                </div>
                <div>
                    <span>{t("landing.calculator.chronoCost", "Chrono Basis ab")}</span>
                    <strong>{formatMoney(chronoBaseCost)}</strong>
                    <em>{t("landing.calculator.perMonth", "pro Monat")}</em>
                </div>
                <div className="lp-calculator-difference">
                    <span>{t("landing.calculator.difference", "Rechnerische Differenz")}</span>
                    <strong>{formatMoney(difference)}</strong>
                </div>
                <p>{t("landing.calculator.disclaimer", "Orientierungswert, keine Einspargarantie. Zusatzmodule und Einführungsaufwand sind nicht berücksichtigt.")}</p>
            </aside>
        </div>
    );
};

const LandingPage = () => {
    const { t } = useTranslation();
    const { notify } = useNotification();
    const { loginDemo } = useAuth();
    const location = useLocation();
    const navigate = useNavigate();
    const [contact, setContact] = useState({ name: "", email: "", message: "" });
    const [sending, setSending] = useState(false);
    const [demoLoading, setDemoLoading] = useState(false);
    const [contactStarted, setContactStarted] = useState(false);

    useEffect(() => {
        if (location.pathname !== "/preise") return;

        requestAnimationFrame(() => {
            document.getElementById("preise")?.scrollIntoView({ block: "start" });
        });
    }, [location.pathname]);

    useEffect(() => {
        const section = document.getElementById("features");
        if (!section || typeof IntersectionObserver === "undefined") return undefined;

        const storageKey = "chronoLandingModulesReached";
        if (sessionStorage.getItem(storageKey) === "true") return undefined;

        const observer = new IntersectionObserver((entries) => {
            if (!entries.some((entry) => entry.isIntersecting)) return;
            sessionStorage.setItem(storageKey, "true");
            trackAnalyticsSignal("landing_module_section_reached", "#features");
            observer.disconnect();
        }, { threshold: 0.2 });

        observer.observe(section);
        return () => observer.disconnect();
    }, []);

    const onChange = (e) => {
        if (!contactStarted) {
            setContactStarted(true);
            trackAnalyticsSignal("landing_contact_form_started", "#kontakt");
        }
        setContact({ ...contact, [e.target.name]: e.target.value });
    };

    const submitContact = async (e) => {
        e.preventDefault();
        if (sending) return;
        setSending(true);
        try {
            await api.post("/api/contact", contact);
            trackAnalyticsSignal("landing_contact_form_success", "#kontakt");
            notify(t("landing.contact.success", "Nachricht gesendet."), "success");
            setContact({ name: "", email: "", message: "" });
        } catch {
            trackAnalyticsSignal("landing_contact_form_error", "#kontakt");
            notify(t("landing.contact.error", "Fehler beim Senden."), "error");
        } finally {
            setSending(false);
        }
    };

    const handleDemo = async () => {
        if (demoLoading) return;
        setDemoLoading(true);
        try {
            const res = await loginDemo();
            if (res.success) {
                trackAnalyticsSignal("landing_interactive_demo_opened", "/demo-tour");
                navigate("/demo-tour", { replace: true });
            } else {
                trackAnalyticsSignal("landing_interactive_demo_error", "/demo-tour");
                notify(res.message || t("landing.demoError", "Demo-Anmeldung fehlgeschlagen"), "error");
            }
        } finally {
            setDemoLoading(false);
        }
    };

    const trustItems = [
        { icon: "TLS", label: t("landing.trust.transportLabel", "Übertragung"), value: t("landing.trust.transportValue", "TLS & HSTS") },
        { icon: "RB", label: t("landing.trust.accessLabel", "Zugriff"), value: t("landing.trust.accessValue", "Rollen & Rechte") },
        { icon: "CH", label: t("landing.trust.privacyLabel", "Datenschutz"), value: t("landing.trust.privacyValue", "revDSG & DSGVO") },
        { icon: "EX", label: t("landing.trust.exportLabel", "Daten"), value: t("landing.trust.exportValue", "Export geregelt") },
    ];

    const outcomes = [
        {
            code: "ZE",
            title: t("landing.outcomes.time.title", "Arbeitszeit ohne Excel"),
            before: t("landing.outcomes.time.before", "Stunden aus Listen, Nachträgen und Rückfragen zusammensuchen."),
            after: t("landing.outcomes.time.after", "Einmal erfassen und Soll/Ist, Salden sowie Korrekturen zentral prüfen."),
        },
        {
            code: "UR",
            title: t("landing.outcomes.leave.title", "Urlaub ohne Rückfragen"),
            before: t("landing.outcomes.leave.before", "Anträge, Resttage und Vertretungen über mehrere Kanäle klären."),
            after: t("landing.outcomes.leave.after", "Antrag, Übersicht und Freigabe in einem nachvollziehbaren Workflow."),
        },
        {
            code: "LO",
            title: t("landing.outcomes.payroll.title", "Lohnvorbereitung ohne Doppelerfassung"),
            before: t("landing.outcomes.payroll.before", "Zeit- und Abwesenheitsdaten für den Lohnlauf erneut übertragen."),
            after: t("landing.outcomes.payroll.after", "Geprüfte Daten aus demselben System für die Abrechnung weiterverwenden."),
        },
    ];

    const securityMeasures = [
        {
            code: "01",
            title: t("landing.security.transport.title", "Verschlüsselte Übertragung"),
            text: t("landing.security.transport.text", "TLS und HSTS schützen die Verbindung zwischen Browser und Chrono."),
        },
        {
            code: "02",
            title: t("landing.security.access.title", "Rollenbasierter Zugriff"),
            text: t("landing.security.access.text", "Rollen, Seitenfreigaben und administrative Zugriffe werden getrennt gesteuert."),
        },
        {
            code: "03",
            title: t("landing.security.contract.title", "Datenschutz vertraglich geregelt"),
            text: t("landing.security.contract.text", "revDSG und DSGVO werden berücksichtigt; bei Auftragsverarbeitung ist ein AVV/DPA vorgesehen."),
        },
        {
            code: "04",
            title: t("landing.security.export.title", "Daten bleiben exportierbar"),
            text: t("landing.security.export.text", "Der Datenexport während der Vertragslaufzeit und der Umgang nach Vertragsende sind in den AGB beschrieben."),
        },
    ];

    const dashboardPreviews = [
        {
            code: "WO",
            title: t("landing.showcase.week.title", "Wochenübersicht"),
            text: t("landing.showcase.week.text", "So sieht der echte Tagesfluss im User-Dashboard aus: Stempelzeiten, Soll/Ist und Tageskarte."),
            visual: "week",
            active: true,
        },
        {
            code: "KO",
            title: t("landing.showcase.corrections.title", "Korrekturen"),
            text: t("landing.showcase.corrections.text", "Korrekturanträge erscheinen im Dashboard als eigener Verlauf mit Status und Detailansicht."),
            visual: "corrections",
        },
        {
            code: "UR",
            title: t("landing.showcase.leave.title", "Urlaub & Abwesenheiten"),
            text: t("landing.showcase.leave.text", "Der Urlaubsbereich ist im Dashboard wirklich vorhanden: Kalender, Anträge und Abwesenheiten."),
            visual: "vacation",
        },
    ];

    const processModules = [
        {
            code: "ZE",
            label: t("landing.features.tracking.label", "Kernmodul"),
            tone: "cyan",
            title: t("landing.features.tracking.title", "Zeiterfassung & Projekte"),
            text: t("landing.features.tracking.text", "Stempeln per Browser, NFC oder App - direkt auf Kunde, Projekt oder Auftrag."),
            visual: "time",
            primary: true,
        },
        {
            code: "UR",
            label: t("landing.features.leave.label", "Freigaben"),
            tone: "mint",
            title: t("landing.features.leave.title", "Urlaub & Abwesenheiten"),
            text: t("landing.features.leave.text", "Anträge, Resttage, Krankheit und Überzeit in einem klaren Workflow."),
            visual: "calendar",
        },
        {
            code: "LO",
            label: t("landing.features.payroll.label", "CH & DE"),
            tone: "amber",
            title: t("landing.features.payroll.title", "Lohn & Finanzen"),
            text: t("landing.features.payroll.text", "Lohnläufe vorbereiten, prüfen und sauber exportieren."),
            visual: "payroll",
        },
        {
            code: "BI",
            label: t("landing.features.reporting.label", "Auswertung"),
            tone: "blue",
            title: t("landing.features.reporting.title", "Berichte"),
            text: t("landing.features.reporting.text", "Soll/Ist, Überstunden und Abwesenheiten auf einen Blick."),
            visual: "reports",
        },
        {
            code: "AU",
            label: t("landing.features.reminders.label", "Automatisch"),
            tone: "rose",
            title: t("landing.features.reminders.title", "Automationen"),
            text: t("landing.features.reminders.text", "Fehlende Zeiten, Erinnerungen und Freigaben laufen zusammen."),
            visual: "automation",
        },
        {
            code: "SI",
            label: t("landing.features.security.label", "Sicher"),
            tone: "blue",
            title: t("landing.features.security.title", "Sicherheit"),
            text: t("landing.features.security.text", "Rollen, Rechte und Datenzugriff sauber getrennt."),
            visual: "security",
        },
        {
            code: "MO",
            label: t("landing.features.access.label", "Mobil"),
            tone: "cyan",
            title: t("landing.features.access.title", "Mobil nutzbar"),
            text: t("landing.features.access.text", "Chrono läuft im Büro, unterwegs, am Tablet und auf dem Smartphone."),
            visual: "mobile",
        },
    ];

    const realProcessModules = [
        {
            code: "ZE",
            label: t("landing.features.real.dashboard.label", "Dashboard"),
            tone: "cyan",
            title: t("landing.features.real.dashboard.title", "Zeiterfassung & Wochenübersicht"),
            text: t("landing.features.real.dashboard.text", "Echtes User-Dashboard mit Stempeln, Soll/Ist, Wochensaldo, Tageskarten und Korrekturen."),
            visual: "timeDashboard",
            primary: true,
        },
        {
            code: "PE",
            label: t("landing.features.real.users.label", "Team & Rechte"),
            tone: "blue",
            title: t("landing.features.real.users.title", "Benutzerverwaltung"),
            text: t("landing.features.real.users.text", "Mitarbeitende, Rollen, freigegebene Seiten und Arbeitsmodelle verwalten."),
            visual: "teamAccess",
        },
        {
            code: "DI",
            label: t("landing.features.real.schedule.label", "Planung"),
            tone: "mint",
            title: t("landing.features.real.schedule.title", "Dienstplan"),
            text: t("landing.features.real.schedule.text", "Schichten planen, Wochen drucken und Einsatzzeiten sauber vorbereiten."),
            visual: "schedule",
        },
        {
            code: "LO",
            label: t("landing.features.real.payslips.label", "Abrechnung"),
            tone: "amber",
            title: t("landing.features.real.payslips.title", "Abrechnungen"),
            text: t("landing.features.real.payslips.text", "Lohnabrechnungen und Dokumente für Mitarbeitende bereitstellen."),
            visual: "payslips",
        },
        {
            code: "PR",
            label: t("landing.features.real.projects.label", "Kundenarbeit"),
            tone: "blue",
            title: t("landing.features.real.projects.title", "Kunden, Projekte & Aufgaben"),
            text: t("landing.features.real.projects.text", "Kunden, Projekte und Aufgaben mit gebuchten Zeiten verbinden."),
            visual: "projects",
        },
        {
            code: "FI",
            label: t("landing.features.real.accounting.label", "Buchhaltung"),
            tone: "amber",
            title: t("landing.features.real.accounting.title", "Finanzbuchhaltung"),
            text: t("landing.features.real.accounting.text", "Belege, Buchungen und Exporte für die Finanzprozesse bündeln."),
            visual: "accounting",
        },
        {
            code: "ZA",
            label: t("landing.features.real.banking.label", "Banking"),
            tone: "amber",
            title: t("landing.features.real.banking.title", "Zahlungsverkehr"),
            text: t("landing.features.real.banking.text", "Zahlungen vorbereiten, prüfen und für Banking-Prozesse freigeben."),
            visual: "banking",
        },
        {
            code: "CM",
            label: t("landing.features.real.crm.label", "Pipeline"),
            tone: "rose",
            title: t("landing.features.real.crm.title", "CRM & Marketing"),
            text: t("landing.features.real.crm.text", "Leads, Kundenkontakte und Pipeline im CRM-Modul führen."),
            visual: "crm",
        },
        {
            code: "SC",
            label: t("landing.features.real.supply.label", "Lager"),
            tone: "cyan",
            title: t("landing.features.real.supply.title", "Supply Chain"),
            text: t("landing.features.real.supply.text", "Bestände, Warenbewegungen, Einkauf, Verkauf und Service im Blick behalten."),
            visual: "supplyChain",
        },
        {
            code: "KI",
            label: t("landing.features.real.knowledge.label", "Wissen"),
            tone: "blue",
            title: t("landing.features.real.knowledge.title", "Firmenwissen"),
            text: t("landing.features.real.knowledge.text", "Dokumente, Richtlinien und internes Wissen zentral bereitstellen."),
            visual: "knowledge",
        },
        {
            code: "C2",
            label: t("landing.features.real.chronoTwo.label", "Zusatzmodul"),
            tone: "blue",
            title: t("landing.features.real.chronoTwo.title", "Chrono 2.0"),
            text: t("landing.features.real.chronoTwo.text", "Zusätzliche Chrono-Prozesse und neue Funktionen als eigenes Modul."),
            visual: "chronoTwo",
        },
        {
            code: "AD",
            label: t("landing.features.real.settings.label", "Setup"),
            tone: "blue",
            title: t("landing.features.real.settings.title", "Firmeneinstellungen"),
            text: t("landing.features.real.settings.text", "Firmendaten, Modulzugriffe und globale Einstellungen pflegen."),
            visual: "settings",
        },
    ];

    const moduleFlowSteps = [
        {
            n: "01",
            title: t("landing.features.map.flow.setup.title", "Setup"),
            text: t("landing.features.map.flow.setup.text", "Rechte, Modelle, Module"),
            modules: [
                t("landing.features.map.modules.companySettings", "Firmeneinstellungen"),
                t("landing.features.map.modules.userManagement", "Benutzerverwaltung"),
                t("landing.features.map.modules.companyKnowledge", "Firmenwissen"),
            ],
        },
        {
            n: "02",
            title: t("landing.features.map.flow.time.title", "Zeit & Planung"),
            text: t("landing.features.map.flow.time.text", "Arbeitszeit & Abwesenheiten"),
            modules: [
                t("landing.features.map.modules.timeTracking", "Zeiterfassung"),
                t("landing.features.map.modules.schedule", "Dienstplan"),
                t("landing.features.map.modules.absences", "Urlaub & Abwesenheiten"),
            ],
        },
        {
            n: "03",
            title: t("landing.features.map.flow.customer.title", "Kunden & Projekte"),
            text: t("landing.features.map.flow.customer.text", "Auftragsbezug"),
            modules: [
                t("landing.features.map.modules.projects", "Kunden, Projekte & Aufgaben"),
                t("landing.features.map.modules.crm", "CRM & Marketing"),
            ],
        },
        {
            n: "04",
            title: t("landing.features.map.flow.finance.title", "Lohn & Finanzen"),
            text: t("landing.features.map.flow.finance.text", "Abrechnung & Zahlung"),
            modules: [
                t("landing.features.map.modules.payslips", "Abrechnungen"),
                t("landing.features.map.modules.accounting", "Finanzbuchhaltung"),
                t("landing.features.map.modules.banking", "Zahlungsverkehr"),
            ],
        },
        {
            n: "05",
            title: t("landing.features.map.flow.operations.title", "Betrieb & Waren"),
            text: t("landing.features.map.flow.operations.text", "Warenfluss & Service"),
            modules: [
                t("landing.features.map.modules.supplyChain", "Supply Chain"),
                t("landing.features.map.modules.chronoTwo", "Chrono 2.0"),
            ],
        },
    ];

    const moduleFlowFacts = [
        {
            label: t("landing.features.map.facts.coreLabel", "Kern"),
            value: t("landing.features.map.facts.coreValue", "Zeit, Team, Rechte"),
        },
        {
            label: t("landing.features.map.facts.dataLabel", "Daten"),
            value: t("landing.features.map.facts.dataValue", "einmal erfassen, mehrfach nutzen"),
        },
        {
            label: t("landing.features.map.facts.addonLabel", "Add-ons"),
            value: t("landing.features.map.facts.addonValue", "nur aktivieren, was gebraucht wird"),
        },
    ];

    const moduleClusters = [
        {
            title: t("landing.features.map.clusters.core.title", "Zeit & Team"),
            text: t("landing.features.map.clusters.core.text", "Kern"),
            modules: [
                { code: "ZE", name: t("landing.features.map.modules.timeTracking", "Zeiterfassung"), note: t("landing.features.map.status.base", "Basis") },
                { code: "DI", name: t("landing.features.map.modules.schedule", "Dienstplan"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "UR", name: t("landing.features.map.modules.absences", "Urlaub & Abwesenheiten"), note: t("landing.features.map.status.available", "Verfügbar") },
                { code: "PE", name: t("landing.features.map.modules.userManagement", "Benutzerverwaltung"), note: t("landing.features.map.status.available", "Verfügbar") },
            ],
        },
        {
            title: t("landing.features.map.clusters.work.title", "Kunden & Projekte"),
            text: t("landing.features.map.clusters.work.text", "Kundenarbeit"),
            modules: [
                { code: "PR", name: t("landing.features.map.modules.projects", "Kunden, Projekte & Aufgaben"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "CM", name: t("landing.features.map.modules.crm", "CRM & Marketing"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "BI", name: t("landing.features.map.modules.analytics", "Analytics & Berichte"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
            ],
        },
        {
            title: t("landing.features.map.clusters.finance.title", "Lohn & Finanzen"),
            text: t("landing.features.map.clusters.finance.text", "Abschluss"),
            modules: [
                { code: "LO", name: t("landing.features.map.modules.payslips", "Abrechnungen"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "FI", name: t("landing.features.map.modules.accounting", "Finanzbuchhaltung"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "ZA", name: t("landing.features.map.modules.banking", "Zahlungsverkehr"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
            ],
        },
        {
            title: t("landing.features.map.clusters.operations.title", "Waren & Betrieb"),
            text: t("landing.features.map.clusters.operations.text", "Warenfluss"),
            modules: [
                { code: "SC", name: t("landing.features.map.modules.supplyChain", "Supply Chain"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "EK", name: t("landing.features.map.modules.purchase", "Einkauf"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "VK", name: t("landing.features.map.modules.sales", "Verkauf"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "SV", name: t("landing.features.map.modules.service", "Service"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
            ],
        },
        {
            title: t("landing.features.map.clusters.setup.title", "Setup & Wissen"),
            text: t("landing.features.map.clusters.setup.text", "Steuerung"),
            modules: [
                { code: "AD", name: t("landing.features.map.modules.companySettings", "Firmeneinstellungen"), note: t("landing.features.map.status.available", "Verfügbar") },
                { code: "KI", name: t("landing.features.map.modules.companyKnowledge", "Firmenwissen"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
                { code: "C2", name: t("landing.features.map.modules.chronoTwo", "Chrono 2.0"), note: t("landing.features.map.status.onRequest", "Auf Anfrage") },
            ],
        },
    ];

    const moduleRelations = [
        t("landing.features.map.relations.one", "Setup legt Rechte, Arbeitsmodelle und aktive Module fest."),
        t("landing.features.map.relations.two", "Zeitdaten werden geprüft und direkt weiterverwendet."),
        t("landing.features.map.relations.three", "Projekte, Lohn, CRM und Supply Chain nutzen dieselbe Datenbasis."),
    ];

    const steps = [
        {
            n: "01",
            title: t("landing.steps.register.title", "Registrieren"),
            text: t("landing.steps.register.text", "Konto anlegen und Grunddaten erfassen. Keine Kreditkarte nötig."),
        },
        {
            n: "02",
            title: t("landing.steps.setup.title", "Team & Bereiche einrichten"),
            text: t("landing.steps.setup.text", "Mitarbeitende, Module, Arbeitsmodelle und Projekte passend konfigurieren."),
        },
        {
            n: "03",
            title: t("landing.steps.start.title", "Direkt loslegen"),
            text: t("landing.steps.start.text", "Stempeln, Urlaub verwalten, Löhne vorbereiten und Prozesse bündeln."),
        },
    ];

    const pricingModules = [
        t("landing.pricing.modules.time", "Zeiterfassung"),
        t("landing.pricing.modules.leave", "Urlaub"),
        t("landing.pricing.modules.projects", "Projekte"),
        t("landing.pricing.modules.payroll", "Lohn optional"),
        t("landing.pricing.modules.crm", "CRM / Supply Chain optional"),
    ];

    const brandFaqs = [
        {
            question: t("landing.faq.what.question", "Was ist Chrono-Logisch?"),
            answer: t(
                "landing.faq.what.answer",
                "Chrono-Logisch ist eine Schweizer Unternehmensplattform für Arbeitszeit, Abwesenheiten und Lohnvorbereitung. Weitere Bereiche werden modular nach Bedarf ergänzt."
            ),
        },
        {
            question: t("landing.faq.spelling.question", "Wird Chrono-Logisch auch \"Chrono logisch\" geschrieben?"),
            answer: t(
                "landing.faq.spelling.answer",
                "Ja. Viele suchen nach \"Chrono logisch\" oder \"chrono logisch\". Die offizielle Schreibweise ist Chrono-Logisch."
            ),
        },
    ];

    return (
        <div className="landing-page scoped-landing">
            <Navbar />
            <main>
                <header className="lp-hero" id="home">
                    <div className="lp-hero-bg" aria-hidden="true">
                        <AdminDashboardMockup t={t} />
                    </div>
                    <div className="lp-hero-content">
                        <span className="lp-hero-badge">
                            {t("landing.hero.badge", "Schweizer Software für KMU")}
                        </span>
                        <h1>{t("landing.hero.title", "Arbeitszeit, Abwesenheiten und Lohnprozesse – ohne Excel-Chaos.")}</h1>
                        <p>
                            {t(
                                "landing.hero.text",
                                "Chrono verbindet Zeiterfassung, Urlaubsfreigaben und Lohnvorbereitung in einer zentralen Plattform. Für Schweizer und deutsche Teams, persönlich eingeführt und modular erweiterbar."
                            )}
                        </p>
                        <div className="lp-cta-buttons">
                            <Link
                                className="lp-btn lp-primary"
                                to="/register"
                                data-analytics-id="landing_demo_request_hero"
                                data-analytics-target="/register"
                            >
                                {t("landing.cta.try", "Kostenlose Demo anfragen")}
                            </Link>
                            <button
                                className="lp-btn lp-secondary"
                                type="button"
                                onClick={handleDemo}
                                disabled={demoLoading}
                                aria-busy={demoLoading}
                                data-analytics-id="landing_interactive_demo_hero"
                                data-analytics-target="/demo-tour"
                            >
                                {demoLoading
                                    ? t("landing.cta.demoLoading", "Demo wird geöffnet …")
                                    : t("landing.cta.demo", "Interaktive Produktdemo starten")}
                            </button>
                        </div>
                        <p className="lp-cta-note">{t("landing.cta.requestHint", "Persönliche Freischaltung · Rückmeldung in der Regel innerhalb eines Werktags")}</p>
                        <div className="lp-proof-line" aria-label={t("landing.hero.proofLabel", "Kurzüberblick")}>
                            <span>{t("landing.hero.proof1", "Ab CHF 5 pro Mitarbeitendem")}</span>
                            <span>{t("landing.hero.proof2", "Keine Kreditkarte im Anfrageprozess")}</span>
                            <span>{t("landing.hero.proof3", "Entwickelt in der Schweiz")}</span>
                        </div>
                        <div className="lp-hero-stats" aria-label={t("landing.hero.statsLabel", "Chrono Kennzahlen")}>
                            <div><strong>Live</strong><span>{t("landing.hero.stat1", "interaktive Demo")}</span></div>
                            <div><strong>1</strong><span>{t("landing.hero.stat2", "zentraler Datenfluss")}</span></div>
                            <div><strong>CH/DE</strong><span>{t("landing.hero.stat3", "persönlich betreut")}</span></div>
                        </div>
                    </div>
                    <FounderCard t={t} />
                </header>

                <section className="lp-trust-section" aria-label={t("landing.trust.title", "Vertrauen für den Alltag in CH & DE")}>
                    <div className="lp-container">
                        <ul className="lp-trust-grid" role="list">
                            {trustItems.map((item) => (
                                <TrustPill key={item.label} {...item} />
                            ))}
                        </ul>
                    </div>
                </section>

                <section className="lp-section lp-outcomes-section" id="outcomes" aria-labelledby="outcomes-title">
                    <div className="lp-container lp-section-heading">
                        <span className="lp-kicker">{t("landing.outcomes.kicker", "Drei klare Einstiege")}</span>
                        <h2 id="outcomes-title">{t("landing.outcomes.title", "Welcher Aufwand kostet Sie heute Zeit?")}</h2>
                        <p>{t("landing.outcomes.lead", "Chrono startet bei einem konkreten Problem – nicht bei einer möglichst langen Modulliste.")}</p>
                    </div>
                    <div className="lp-container lp-outcomes-grid">
                        {outcomes.map((outcome) => (
                            <OutcomeCard key={outcome.code} {...outcome} t={t} />
                        ))}
                    </div>
                </section>

                <section className="lp-section lp-showcase-section" id="platform">
                    <div className="lp-container lp-section-heading">
                        <span className="lp-kicker">{t("landing.showcase.kicker", "Produktgefühl")}</span>
                        <h2>{t("landing.showcase.title", "So sieht Chrono im Alltag aus.")}</h2>
                        <p>{t("landing.showcase.lead", "Nicht nur Text: Chrono führt Zeit, Abwesenheiten, Lohn und operative Prozesse in einer gemeinsamen Oberfläche zusammen.")}</p>
                    </div>
                    <div className="lp-container lp-showcase-layout">
                        <div className="lp-showcase-browser">
                            <ProductMockup t={t} />
                        </div>
                        <div className="lp-showcase-list">
                            {dashboardPreviews.map((item) => (
                                <DashboardPreviewCard key={item.code} {...item} t={t} />
                            ))}
                        </div>
                    </div>
                </section>

                <FounderTrustSection t={t} />

                <section className="lp-section lp-security-section" id="sicherheit" aria-labelledby="security-title">
                    <div className="lp-container lp-security-layout">
                        <div className="lp-section-heading lp-security-heading">
                            <span className="lp-kicker">{t("landing.security.kicker", "Sicherheit & Datenschutz")}</span>
                            <h2 id="security-title">{t("landing.security.title", "Klare Massnahmen statt pauschaler Versprechen.")}</h2>
                            <p>{t("landing.security.lead", "Für sensible Personal- und Lohndaten zählt, was technisch und vertraglich nachvollziehbar geregelt ist.")}</p>
                            <div className="lp-security-links">
                                <Link className="lp-btn lp-secondary" to="/datenschutz">{t("landing.security.privacyCta", "Datenschutz ansehen")}</Link>
                                <Link className="lp-btn lp-secondary" to="/agb">{t("landing.security.termsCta", "AGB & Datenexport")}</Link>
                            </div>
                        </div>
                        <div className="lp-security-grid">
                            {securityMeasures.map((measure) => (
                                <SecurityItem key={measure.code} {...measure} />
                            ))}
                        </div>
                    </div>
                </section>

                <section className="lp-section lp-process-section" id="features">
                    <div className="lp-container lp-process-container lp-section-heading">
                        <span className="lp-kicker">{t("landing.features.kicker", "Module")}</span>
                        <h2>{t("landing.features.title", "Module auf einen Blick.")}</h2>
                        <p>{t("landing.features.lead", "Zeit & Team ist der Kern. Alles Weitere wird je nach Prozess dazugeschaltet.")}</p>
                    </div>
                    <div className="lp-container lp-process-container lp-module-map" aria-label={t("landing.features.aria", "Chrono Modulübersicht")}>
                        <div className="lp-module-flow-panel">
                            <div className="lp-module-flow-copy">
                                <span>{t("landing.features.map.flowLabel", "Modul-Kompass")}</span>
                                <h3>{t("landing.features.map.flowTitle", "Erst Grundlage, dann Erweiterungen.")}</h3>
                                <ul className="lp-module-flow-facts" role="list">
                                    {moduleFlowFacts.map((fact) => (
                                        <li key={fact.label}>
                                            <strong>{fact.label}</strong>
                                            <span>{fact.value}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                            <div className="lp-module-flow-rail" role="list">
                                {moduleFlowSteps.map((step) => (
                                    <ModuleFlowStep key={step.n} {...step} />
                                ))}
                            </div>
                        </div>

                        <div className="lp-module-cluster-grid" role="list">
                            {moduleClusters.map((cluster) => (
                                <ModuleClusterCard key={cluster.title} {...cluster} />
                            ))}
                        </div>

                        <div className="lp-module-relation-panel">
                            <div>
                                <span>{t("landing.features.map.relationLabel", "Kurzlogik")}</span>
                                <h3>{t("landing.features.map.relationTitle", "Was muss man verstehen?")}</h3>
                                <p>{t("landing.features.map.relationText", "Eine Plattform, mehrere zuschaltbare Bereiche.")}</p>
                            </div>
                            <ol>
                                {moduleRelations.map((relation) => (
                                    <li key={relation}>{relation}</li>
                                ))}
                            </ol>
                        </div>
                    </div>
                    <div className="lp-container lp-process-container lp-process-cta">
                        <p>
                            <strong>{t("landing.features.ctaTitle", "Chrono wächst mit Ihrem Unternehmen.")}</strong>
                            <span>{t("landing.features.ctaText", "Starten Sie mit dem Kern aus Zeit, Team und Rechten - und schalten Sie danach genau die Prozessbereiche frei, die Ihr Unternehmen wirklich nutzt.")}</span>
                        </p>
                        <div>
                            <a className="lp-btn lp-secondary" href="#preise">{t("landing.features.modulesCta", "Alle Module ansehen")}</a>
                            <Link
                                className="lp-btn lp-primary"
                                to="/register"
                                data-analytics-id="landing_demo_request_modules"
                                data-analytics-target="/register"
                            >
                                {t("landing.cta.try", "Kostenlose Demo anfragen")}
                            </Link>
                        </div>
                    </div>
                </section>

                <section className="lp-section lp-steps-section" id="start">
                    <div className="lp-container lp-section-heading">
                        <span className="lp-kicker">{t("landing.steps.kicker", "Start")}</span>
                        <h2>{t("landing.steps.title", "So starten Sie mit Chrono.")}</h2>
                        <p>{t("landing.steps.lead", "In der Regel schnell startklar - ohne komplizierte Einführung und ohne Excel-Umwege.")}</p>
                    </div>
                    <div className="lp-container lp-steps-grid">
                        {steps.map((step) => (
                            <StepCard key={step.n} {...step} />
                        ))}
                    </div>
                </section>

                <section className="lp-section lp-calculator-section" id="rechner">
                    <div className="lp-container">
                        <SavingsCalculator t={t} />
                    </div>
                </section>

                <section className="lp-pricing-section" id="preise">
                    <div className="lp-container lp-pricing-layout">
                        <div className="lp-pricing-copy">
                            <span className="lp-kicker">{t("landing.pricing.kicker", "Preise")}</span>
                            <h2>{t("landing.pricing.title", "Modular ab 5 CHF pro Mitarbeitendem.")}</h2>
                            <p>
                                {t(
                                    "landing.pricing.text",
                                    "Sie zahlen nur für die Bereiche, die Ihr Unternehmen wirklich braucht. Weitere Module für Urlaub, Lohn, CRM oder Supply Chain lassen sich später ergänzen."
                                )}
                            </p>
                            <Link
                                className="lp-btn lp-primary"
                                to="/register"
                                data-analytics-id="landing_configurator_start_pricing"
                                data-analytics-target="/register"
                            >
                                {t("landing.pricing.cta", "Preise ansehen & Konfiguration starten")}
                            </Link>
                        </div>
                        <aside className="lp-pricing-card">
                            <span>{t("landing.pricing.cardEyebrow", "Startpaket")}</span>
                            <em>{t("landing.pricing.badge", "Beliebter Einstieg")}</em>
                            <strong>{t("landing.pricing.cardPrice", "ab 5 CHF")}</strong>
                            <p>{t("landing.pricing.cardUnit", "pro Mitarbeitendem / Monat")}</p>
                            <div className="lp-pricing-note">
                                {t("landing.pricing.note", "Ideal für kleine Teams, monatlich skalierbar.")}
                            </div>
                            <ul>
                                {pricingModules.map((module) => (
                                    <li key={module}>{module}</li>
                                ))}
                            </ul>
                            <Link
                                className="lp-btn lp-secondary"
                                to="/register"
                                data-analytics-id="landing_configurator_start_card"
                                data-analytics-target="/register"
                            >
                                {t("landing.pricing.cardCta", "Konfiguration starten")}
                            </Link>
                        </aside>
                    </div>
                </section>

                <section className="lp-section lp-faq-section" id="faq" aria-labelledby="chrono-faq-title">
                    <div className="lp-container lp-faq-layout">
                        <div className="lp-section-heading lp-faq-heading">
                            <span className="lp-kicker">{t("landing.faq.kicker", "Chrono-Logisch")}</span>
                            <h2 id="chrono-faq-title">{t("landing.faq.title", "Kurz erklärt.")}</h2>
                            <p>
                                {t(
                                    "landing.faq.lead",
                                    "Die wichtigsten Begriffe rund um Chrono-Logisch, Chrono logisch und die Schweizer Unternehmensplattform."
                                )}
                            </p>
                        </div>
                        <div className="lp-faq-list">
                            {brandFaqs.map((item) => (
                                <article className="lp-faq-item" key={item.question}>
                                    <h3>{item.question}</h3>
                                    <p>{item.answer}</p>
                                </article>
                            ))}
                        </div>
                    </div>
                </section>

                <section className="lp-contact-section" id="kontakt" aria-labelledby="kontakt-title">
                    <div className="lp-container lp-contact-layout">
                        <div className="lp-contact-copy">
                            <span className="lp-kicker">{t("landing.contact.kicker", "Demo & Kontakt")}</span>
                            <h2 id="kontakt-title">{t("landing.contact.title", "Lassen Sie uns über Ihr Unternehmen sprechen.")}</h2>
                            <p>
                                {t(
                                    "landing.contact.text",
                                    "Sie möchten Chrono zuerst sehen oder haben Fragen zur Einführung, Migration oder Lohnabrechnung in CH/DE? Schreiben Sie uns - wir melden uns in der Regel noch am selben Werktag."
                                )}
                            </p>
                            <div className="lp-contact-actions">
                                <button
                                    className="lp-btn lp-secondary"
                                    type="button"
                                    onClick={handleDemo}
                                    disabled={demoLoading}
                                    aria-busy={demoLoading}
                                    data-analytics-id="landing_interactive_demo_contact"
                                    data-analytics-target="/demo-tour"
                                >
                                    {demoLoading
                                        ? t("landing.cta.demoLoading", "Demo wird geöffnet …")
                                        : t("landing.contact.demo", "Interaktive Demo starten")}
                                </button>
                                <a className="lp-contact-mail" href="mailto:siefertchristopher@chrono-logisch.ch">siefertchristopher@chrono-logisch.ch</a>
                                <a className="lp-contact-mail" href="tel:+41765467960">+41 76 546 79 60</a>
                            </div>
                            <ul className="lp-contact-signals" aria-label={t("landing.contact.signalsLabel", "Kontakt Vorteile")}>
                                <li>{t("landing.contact.signal1", "Antwort meist am selben Werktag")}</li>
                                <li>{t("landing.contact.signal2", "Live-Demo mit echtem Workflow")}</li>
                                <li>{t("landing.contact.signal3", "Fokus auf Teams in CH & DE")}</li>
                            </ul>
                        </div>
                        <form className="lp-contact-form" onSubmit={submitContact}>
                            <div className="lp-form-row">
                                <label>
                                    <span>{t("landing.contact.name", "Name")}</span>
                                    <input name="name" type="text" required value={contact.name} onChange={onChange} />
                                </label>
                                <label>
                                    <span>{t("landing.contact.email", "E-Mail")}</span>
                                    <input name="email" type="email" required value={contact.email} onChange={onChange} />
                                </label>
                            </div>
                            <label>
                                <span>{t("landing.contact.msg", "Nachricht")}</span>
                                <textarea
                                    name="message"
                                    rows={5}
                                    required
                                    value={contact.message}
                                    onChange={onChange}
                                    placeholder={t("landing.contact.placeholder", "Wie können wir helfen?")}
                                />
                            </label>
                            <div className="lp-form-actions">
                                <button
                                    className="lp-btn lp-primary"
                                    type="submit"
                                    disabled={sending}
                                    data-analytics-id="landing_contact_form_submit"
                                    data-analytics-target="#kontakt"
                                >
                                    {sending ? t("landing.contact.sending", "Wird gesendet...") : t("landing.contact.send", "Nachricht senden")}
                                </button>
                                <span>{t("landing.contact.hint", "Antwort meist am selben Werktag.")}</span>
                            </div>
                        </form>
                    </div>
                </section>
            </main>

            <footer className="lp-footer">
                <div className="lp-container lp-footer-inner">
                    <img className="lp-footer-logo" src="/img/komplettesLogo.png" alt="Chrono" loading="lazy" />
                    <nav aria-label={t("landing.footer.navLabel", "Footer Navigation")}>
                        <Link to="/ueber-chrono-logisch">{t("landing.footer.about", "Über Chrono-Logisch")}</Link>
                        <Link to="/impressum">{t("landing.footer.imprint", "Impressum")}</Link>
                        <Link to="/datenschutz">{t("landing.footer.privacy", "Datenschutz")}</Link>
                        <Link to="/agb">{t("landing.footer.terms", "AGB")}</Link>
                    </nav>
                    <div className="lp-footer-social" aria-label={t("landing.footer.socialLabel", "Social Media")}>
                        <a
                            href="https://www.linkedin.com/in/christopher-siefert-20903b377/"
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label={t("landing.footer.linkedinLabel", "Christopher Siefert auf LinkedIn")}
                        >
                            <LinkedinIcon />
                            <span>LinkedIn</span>
                        </a>
                        <a
                            href="https://www.instagram.com/itschronologisch/"
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label={t("landing.footer.instagramLabel", "Chrono auf Instagram")}
                        >
                            <InstagramIcon />
                            <span>Instagram</span>
                        </a>
                    </div>
                    <span>© {new Date().getFullYear()}</span>
                </div>
            </footer>
        </div>
    );
};

export default LandingPage;
