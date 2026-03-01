import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import { useTranslation } from '../../context/LanguageContext';
import { useNotification } from '../../context/NotificationContext';
import api from '../../utils/api';
import VacationCalendarAdmin from '../../components/VacationCalendarAdmin';
import {
    formatDate,
    formatDateWithWeekday,
    formatLocalDateYMD,
    getMondayOfWeek,
    addDays,
    minutesToHHMM,
} from './adminDashboardUtils';
import '../../styles/AdminEmployeeOverviewScoped.css';

const isWorkDay = (dateObj) => {
    const day = dateObj.getDay();
    return day !== 0 && day !== 6;
};

const countVacationDays = (vacations) => {
    if (!Array.isArray(vacations) || vacations.length === 0) return 0;
    return vacations.reduce((sum, vacation) => {
        if (!vacation || vacation.usesOvertime) return sum;
        const start = new Date(`${vacation.startDate}T00:00:00`);
        const end = new Date(`${vacation.endDate}T00:00:00`);
        if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return sum;

        let days = 0;
        for (let cursor = new Date(start); cursor <= end; cursor.setDate(cursor.getDate() + 1)) {
            if (isWorkDay(cursor)) {
                days += vacation.halfDay ? 0.5 : 1;
            }
        }
        return sum + days;
    }, 0);
};

const AdminEmployeeOverviewPage = () => {
    const { username: encodedUsername } = useParams();
    const username = decodeURIComponent(encodedUsername || '');
    const { t } = useTranslation();
    const { notify } = useNotification();

    const [loading, setLoading] = useState(true);
    const [users, setUsers] = useState([]);
    const [dailySummaries, setDailySummaries] = useState([]);
    const [vacations, setVacations] = useState([]);
    const [corrections, setCorrections] = useState([]);
    const [trackingBalances, setTrackingBalances] = useState([]);

    const fetchAllData = useCallback(async () => {
        setLoading(true);
        try {
            const [usersRes, summariesRes, vacationsRes, correctionsRes, balancesRes] = await Promise.all([
                api.get('/api/admin/users'),
                api.get('/api/admin/timetracking/all-summaries'),
                api.get('/api/vacation/all'),
                api.get('/api/correction/all'),
                api.get('/api/admin/timetracking/admin/tracking-balances'),
            ]);

            setUsers(Array.isArray(usersRes.data) ? usersRes.data : []);
            setDailySummaries(Array.isArray(summariesRes.data) ? summariesRes.data : []);
            setVacations(Array.isArray(vacationsRes.data) ? vacationsRes.data : []);
            setCorrections(Array.isArray(correctionsRes.data) ? correctionsRes.data : []);
            setTrackingBalances(Array.isArray(balancesRes.data) ? balancesRes.data : []);
        } catch (error) {
            console.error('Fehler beim Laden der Mitarbeiter-Übersicht:', error);
            notify(t('adminEmployeeOverview.fetchError', 'Mitarbeiter-Übersicht konnte nicht geladen werden.'), 'error');
        } finally {
            setLoading(false);
        }
    }, [notify, t]);

    useEffect(() => {
        fetchAllData();
    }, [fetchAllData]);

    const employee = useMemo(
        () => users.find((user) => user?.username === username) || null,
        [users, username],
    );

    const employeeSummaries = useMemo(
        () => dailySummaries
            .filter((entry) => entry?.username === username)
            .sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0)),
        [dailySummaries, username],
    );

    const currentMonday = useMemo(() => getMondayOfWeek(new Date()), []);
    const weekDates = useMemo(
        () => Array.from({ length: 7 }, (_, index) => formatLocalDateYMD(addDays(currentMonday, index))),
        [currentMonday],
    );

    const weeklySummaries = useMemo(
        () => employeeSummaries.filter((entry) => weekDates.includes(entry?.date)),
        [employeeSummaries, weekDates],
    );

    const employeeBalance = useMemo(
        () => trackingBalances.find((entry) => entry?.username === username) || null,
        [trackingBalances, username],
    );

    const employeeVacations = useMemo(
        () => vacations.filter((vacation) => vacation?.username === username),
        [vacations, username],
    );

    const employeeCorrections = useMemo(
        () => corrections
            .filter((correction) => correction?.username === username)
            .sort((a, b) => new Date(b.requestDate || 0) - new Date(a.requestDate || 0)),
        [corrections, username],
    );

    const vacationStats = useMemo(() => {
        const approved = employeeVacations.filter((vacation) => vacation?.approved);
        const planned = employeeVacations.filter((vacation) => !vacation?.approved && !vacation?.denied);
        const annual = Number(employee?.annualVacationDays) || 0;
        const takenDays = countVacationDays(approved);
        const plannedDays = countVacationDays(planned);

        return {
            annual,
            takenDays,
            plannedDays,
            availableDays: Math.max(annual - takenDays - plannedDays, 0),
        };
    }, [employee, employeeVacations]);

    const totalWorkedWeekMinutes = useMemo(
        () => weeklySummaries.reduce((sum, entry) => sum + (entry?.workedMinutes || 0), 0),
        [weeklySummaries],
    );

    const totalBreakWeekMinutes = useMemo(
        () => weeklySummaries.reduce((sum, entry) => sum + (entry?.breakMinutes || 0), 0),
        [weeklySummaries],
    );

    const correctionStats = useMemo(() => ({
        pending: employeeCorrections.filter((correction) => !correction.approved && !correction.denied).length,
        approved: employeeCorrections.filter((correction) => correction.approved).length,
        denied: employeeCorrections.filter((correction) => correction.denied).length,
    }), [employeeCorrections]);

    return (
        <div className="employee-overview-page">
            <Navbar />
            <main className="employee-overview-main">
                <section className="employee-overview-header card-style">
                    <div>
                        <h1>{t('adminEmployeeOverview.title', 'Mitarbeiter-Übersicht')}</h1>
                        <p>
                            {t('adminEmployeeOverview.subtitle', 'Alle relevanten Informationen auf einen Blick für')}{' '}
                            <strong>{username}</strong>
                        </p>
                    </div>
                    <Link className="back-to-dashboard-button" to="/admin/dashboard">
                        {t('adminEmployeeOverview.backToDashboard', 'Zurück zum Dashboard')}
                    </Link>
                </section>

                {loading ? (
                    <section className="card-style employee-overview-state">
                        {t('loading', 'Lade...')}
                    </section>
                ) : !employee ? (
                    <section className="card-style employee-overview-state">
                        {t('adminEmployeeOverview.notFound', 'Mitarbeiter nicht gefunden.')}
                    </section>
                ) : (
                    <>
                        <section className="employee-overview-kpis">
                            <article className="card-style kpi-card">
                                <h3>{t('adminEmployeeOverview.timeWeek', 'Zeiterfassung – aktuelle Woche')}</h3>
                                <p>{t('actualHours', 'Ist')}: <strong>{minutesToHHMM(totalWorkedWeekMinutes)}</strong></p>
                                <p>{t('breakTime', 'Pause')}: <strong>{minutesToHHMM(totalBreakWeekMinutes)}</strong></p>
                                <p>{t('balanceTotal', 'Gesamtsaldo')}: <strong>{minutesToHHMM(employeeBalance?.trackingBalance || 0)}</strong></p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>{t('vacationTitle', 'Urlaubstage')}</h3>
                                <p>{t('remainingVacation', 'Verfügbar')}: <strong>{vacationStats.availableDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                                <p>{t('adminEmployeeOverview.plannedVacation', 'Geplant')}: <strong>{vacationStats.plannedDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                                <p>{t('myVacations', 'Genommen')}: <strong>{vacationStats.takenDays.toFixed(1)} {t('daysLabel', 'Tage')}</strong></p>
                            </article>
                            <article className="card-style kpi-card">
                                <h3>{t('correctionRequests', 'Anträge')}</h3>
                                <p>{t('adminEmployeeOverview.pending', 'Offen')}: <strong>{correctionStats.pending}</strong></p>
                                <p>{t('adminEmployeeOverview.approved', 'Genehmigt')}: <strong>{correctionStats.approved}</strong></p>
                                <p>{t('adminEmployeeOverview.denied', 'Abgelehnt')}: <strong>{correctionStats.denied}</strong></p>
                            </article>
                        </section>

                        <section className="employee-overview-grid">
                            <article className="card-style employee-overview-card calendar-card">
                                <h2>{t('adminEmployeeOverview.calendarTitle', 'Kalender & direkte Aktionen')}</h2>
                                <p className="card-subtitle">{t('adminEmployeeOverview.calendarSubtitle', 'Urlaub/Krank direkt für diesen Mitarbeiter erfassen.')}</p>
                                <VacationCalendarAdmin
                                    vacationRequests={employeeVacations}
                                    onReloadVacations={fetchAllData}
                                    companyUsers={users}
                                    focusUsername={username}
                                />
                            </article>

                            <article className="card-style employee-overview-card">
                                <h2>{t('adminEmployeeOverview.latestTracking', 'Letzte Zeiterfassungs-Tage')}</h2>
                                <div className="compact-list">
                                    {employeeSummaries.slice(0, 8).map((entry) => (
                                        <div className="compact-list-item" key={`${entry.username}-${entry.date}`}>
                                            <span>{formatDateWithWeekday(new Date(`${entry.date}T00:00:00`))}</span>
                                            <span>{minutesToHHMM(entry.workedMinutes || 0)} / {minutesToHHMM(entry.breakMinutes || 0)}</span>
                                        </div>
                                    ))}
                                    {employeeSummaries.length === 0 && (
                                        <p className="empty-state">{t('adminEmployeeOverview.noTrackingData', 'Keine Zeiterfassungen vorhanden.')}</p>
                                    )}
                                </div>
                            </article>

                            <article className="card-style employee-overview-card">
                                <h2>{t('adminEmployeeOverview.vacationRequestsTitle', 'Urlaubsanträge')}</h2>
                                <div className="compact-list">
                                    {employeeVacations.slice().sort((a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0)).slice(0, 8).map((vacation) => (
                                        <div className="compact-list-item" key={`vac-${vacation.id}`}>
                                            <span>{formatDate(new Date(`${vacation.startDate}T00:00:00`))} – {formatDate(new Date(`${vacation.endDate}T00:00:00`))}</span>
                                            <span className={`status-pill ${vacation.approved ? 'ok' : vacation.denied ? 'bad' : 'pending'}`}>
                                                {vacation.approved ? t('approved', 'Genehmigt') : vacation.denied ? t('denied', 'Abgelehnt') : t('pending', 'Offen')}
                                            </span>
                                        </div>
                                    ))}
                                    {employeeVacations.length === 0 && (
                                        <p className="empty-state">{t('adminEmployeeOverview.noVacationRequests', 'Keine Urlaubsanträge vorhanden.')}</p>
                                    )}
                                </div>
                            </article>

                            <article className="card-style employee-overview-card">
                                <h2>{t('adminEmployeeOverview.correctionsTitle', 'Korrekturanträge')}</h2>
                                <div className="compact-list">
                                    {employeeCorrections.slice(0, 8).map((correction) => (
                                        <div className="compact-list-item" key={`corr-${correction.id}`}>
                                            <span>{correction.requestDate ? formatDate(new Date(correction.requestDate)) : '-'}</span>
                                            <span className={`status-pill ${correction.approved ? 'ok' : correction.denied ? 'bad' : 'pending'}`}>
                                                {correction.approved ? t('approved', 'Genehmigt') : correction.denied ? t('denied', 'Abgelehnt') : t('pending', 'Offen')}
                                            </span>
                                        </div>
                                    ))}
                                    {employeeCorrections.length === 0 && (
                                        <p className="empty-state">{t('adminEmployeeOverview.noCorrections', 'Keine Korrekturanträge vorhanden.')}</p>
                                    )}
                                </div>
                            </article>
                        </section>
                    </>
                )}
            </main>
        </div>
    );
};

export default AdminEmployeeOverviewPage;
