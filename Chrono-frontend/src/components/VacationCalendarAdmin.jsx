import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdminScoped.css';
import api from '../utils/api';
import { useAuth } from '../context/AuthContext';
import { useNotification } from '../context/NotificationContext';
import { useTranslation } from '../context/LanguageContext';

function getContrastYIQ(hexcolor) {
    hexcolor = hexcolor.replace("#", "");
    const r = parseInt(hexcolor.substr(0, 2), 16);
    const g = parseInt(hexcolor.substr(2, 2), 16);
    const b = parseInt(hexcolor.substr(4, 2), 16);
    const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
    return (yiq >= 128) ? '#000' : '#fff';
}

const VacationCalendarAdmin = ({ vacationRequests, onReloadVacations }) => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    // Modal & Formular-States
    const [showModal, setShowModal] = useState(false);
    const [newVacationUser, setNewVacationUser] = useState('');
    const [newVacationHalfDay, setNewVacationHalfDay] = useState(false);

    // Admin-Passwort eingeben
    const [adminPassword, setAdminPassword] = useState('');

    const [users, setUsers] = useState([]);
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    useEffect(() => {
        fetchAllUsers();
    }, []);

    async function fetchAllUsers() {
        try {
            const res = await api.get('/api/admin/users');
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error fetching users:', err);
        }
    }

    /** Format yyyy-MM-dd */
    function formatYMD(d) {
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    /**
     * Prüft, ob ein Vacation-Objekt innerhalb eines Tages "date" liegt
     */
    function vacationInRange(vac, start, end) {
        const vacStart = new Date(vac.startDate);
        const vacEnd = new Date(vac.endDate);
        vacStart.setHours(0, 0, 0, 0);
        vacEnd.setHours(0, 0, 0, 0);
        return vacEnd >= start && vacStart <= end;
    }

    /**
     * Erstellt via Admin einen neuen Urlaubseintrag
     */
    async function handleCreateVacation() {
        if (!newVacationUser) {
            notify(t("adminVacation.noUserSelected") || "Bitte einen Benutzer auswählen");
            return;
        }
        if (!startDate || !endDate) {
            notify(t("adminVacation.datesMissing") || "Bitte Start- und Enddatum angeben");
            return;
        }
        if (!adminPassword) {
            notify(t("adminVacation.adminPassMissing") || "Bitte Admin-Passwort eingeben");
            return;
        }

        const startStr = formatYMD(new Date(startDate));
        const endStr = formatYMD(new Date(endDate));

        try {
            await api.post('/api/vacation/adminCreate', null, {
                params: {
                    adminUsername: currentUser.username,
                    adminPassword,
                    username: newVacationUser,
                    startDate: startStr,
                    endDate: endStr,
                    halfDay: newVacationHalfDay
                }
            });
            notify(t("adminVacation.createdSuccess") || "Urlaub erfolgreich (admin) erstellt und direkt genehmigt");

            if (onReloadVacations) {
                onReloadVacations();
            }
            setShowModal(false);
        } catch (err) {
            console.error('Error creating vacation (adminCreate)', err);
            notify(t("adminVacation.createError") || "Fehler beim Anlegen des Admin-Urlaubs");
        }
    }

    // Kalenderkacheln: Marker für Urlaube
    function tileContent({ date, view }) {
        if (view === 'month') {
            const day = new Date(date);
            day.setHours(0, 0, 0, 0);

            const vacs = vacationRequests.filter((vac) => vacationInRange(vac, day, day));
            if (vacs.length > 0) {
                return (
                    <div className="vacation-markers">
                        {vacs.map((vac, index) => {
                            const bgColor = vac.color || '#ccc';
                            const textColor = getContrastYIQ(bgColor);
                            return (
                                <div
                                    key={index}
                                    className="vacation-marker"
                                    style={{ backgroundColor: bgColor, color: textColor }}
                                    title={vac.username}
                                >
                                    {vac.username}
                                </div>
                            );
                        })}
                    </div>
                );
            }
        }
        return null;
    }

    return (
        <div className="vacation-calendar-admin scoped-vacation">
            <h2>{t('adminVacation.title') || 'Admin Urlaubs Kalender'}</h2>
            <div className="admin-password-input">
                <label>{t('adminVacation.adminPasswordLabel') || 'Admin Passwort'}: </label>
                <input
                    type="password"
                    placeholder={t('adminVacation.adminPasswordLabel') || 'Admin Passwort'}
                    value={adminPassword}
                    onChange={(e) => setAdminPassword(e.target.value)}
                />
            </div>

            <Calendar
                value={new Date()}
                tileContent={tileContent}
            />

            <button onClick={() => setShowModal(true)} style={{ marginTop: '1rem' }}>
                {t('adminVacation.createVacationButton') || 'Urlaub erstellen'}
            </button>

            {showModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>{t('adminVacation.modalTitle') || 'Urlaub'}</h3>

                        <form>
                            <label>
                                {t('adminVacation.userSelection') || 'Benutzer Auswahl'}:
                                <select
                                    value={newVacationUser}
                                    onChange={(e) => setNewVacationUser(e.target.value)}
                                >
                                    <option value="">
                                        {t('adminVacation.selectUserPlaceholder') || 'Bitte Benutzer auswählen'}
                                    </option>
                                    {users.map((u) => (
                                        <option key={u.id} value={u.username}>
                                            {u.username}
                                        </option>
                                    ))}
                                </select>
                            </label>

                            <label>
                                {t('adminVacation.startDateLabel') || 'Start'}:
                                <input
                                    type="date"
                                    value={startDate}
                                    onChange={(e) => setStartDate(e.target.value)}
                                    required
                                />
                            </label>

                            <label>
                                {t('adminVacation.endDateLabel') || 'Ende'}:
                                <input
                                    type="date"
                                    value={endDate}
                                    onChange={(e) => setEndDate(e.target.value)}
                                    required
                                />
                            </label>

                            <label>
                                {t('adminVacation.halfDayLabel') || 'Halbtags Urlaub'}:
                                <input
                                    type="checkbox"
                                    checked={newVacationHalfDay}
                                    onChange={(e) => setNewVacationHalfDay(e.target.checked)}
                                />
                            </label>

                            <label>
                                {t('adminVacation.adminPasswordLabel') || 'AdminPasswort'}:
                                <input
                                    type="password"
                                    value={adminPassword}
                                    onChange={(e) => setAdminPassword(e.target.value)}
                                    required
                                />
                            </label>

                            <div className="modal-buttons">
                                <button type="button" onClick={handleCreateVacation}>
                                    {t('adminVacation.confirmButton') || 'Bestätigen'}
                                </button>
                                <button type="button" onClick={() => setShowModal(false)}>
                                    {t('adminVacation.cancelButton') || 'Abbrechen'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

VacationCalendarAdmin.propTypes = {
    vacationRequests: PropTypes.array.isRequired,
    onReloadVacations: PropTypes.func
};

export default VacationCalendarAdmin;
