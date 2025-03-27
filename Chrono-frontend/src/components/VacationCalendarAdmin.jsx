// src/components/VacationCalendarAdmin.jsx
import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdmin.css';
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

    // Kalenderwert kann ein Date oder [startDate, endDate] sein (wegen selectRange)
    const [rangeValue, setRangeValue] = useState(new Date());
    const [selectingRange, setSelectingRange] = useState(false);

    // Modal & Formular-States
    const [showModal, setShowModal] = useState(false);
    const [vacationsOnSelectedRange, setVacationsOnSelectedRange] = useState([]);

    const [newVacationUser, setNewVacationUser] = useState('');
    const [newVacationHalfDay, setNewVacationHalfDay] = useState(false);

    // Admin-Passwort eingeben
    const [adminPassword, setAdminPassword] = useState('');

    const [users, setUsers] = useState([]);

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

    /**
     * Gibt zurück, ob ein Urlaubseintrag (vac) innerhalb der gewählten Range liegt
     */
    function vacationInRange(vac, start, end) {
        // Start/End aus dem Urlaub
        const vacStart = new Date(vac.startDate);
        const vacEnd = new Date(vac.endDate);

        // Normiere die Stunden auf 0
        vacStart.setHours(0, 0, 0, 0);
        vacEnd.setHours(0, 0, 0, 0);

        // Prüfen auf Überschneidung
        return vacEnd >= start && vacStart <= end;
    }

    /**
     * Callback, wenn man im Kalender eine neue Range auswählt
     */
    function handleCalendarChange(nextValue) {
        setRangeValue(nextValue);

        if (Array.isArray(nextValue) && nextValue.length === 2) {
            setSelectingRange(false);
            setShowModal(true);

            const [start, end] = nextValue;
            // Normiere auf 0 Uhr
            const normStart = new Date(start);
            const normEnd = new Date(end);
            normStart.setHours(0, 0, 0, 0);
            normEnd.setHours(0, 0, 0, 0);

            // Finde alle Urlaubseinträge, die in diesen Bereich fallen
            const vacsInRange = vacationRequests.filter(vac =>
                vacationInRange(vac, normStart, normEnd)
            );
            setVacationsOnSelectedRange(vacsInRange);

            // Reset der Form-Felder
            setNewVacationUser('');
            setNewVacationHalfDay(false);
        } else {
            // Solange der User noch am Ziehen ist, kann man "ausblenden" oder "ausgrauen"
            setSelectingRange(true);
        }
    }

    /**
     * Erstellt einen neuen Urlaubseintrag (adminCreate) zwischen [start, end]
     */
    async function handleCreateVacation() {
        if (!newVacationUser) {
            notify('Bitte einen Benutzer auswählen');
            return;
        }
        if (!Array.isArray(rangeValue) || rangeValue.length !== 2) {
            notify('Bitte zuerst einen Datumsbereich auswählen');
            return;
        }
        if (!adminPassword) {
            notify('Bitte Admin-Passwort eingeben');
            return;
        }

        const [start, end] = rangeValue;
        const startStr = formatYMD(start);
        const endStr = formatYMD(end);

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
            notify('Urlaub erfolgreich (admin) erstellt und direkt genehmigt');

            // Optional: Liste neu laden
            if (onReloadVacations) {
                onReloadVacations();
            }
            setShowModal(false);
        } catch (err) {
            console.error('Error creating vacation (adminCreate)', err);
            notify('Fehler beim Anlegen des Admin-Urlaubs');
        }
    }

    // Formatiert ein Date in yyyy-MM-dd
    function formatYMD(d) {
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    /**
     * Löscht einen existierenden Urlaub per DELETE
     * => param adminUsername, adminPassword
     */
    async function handleDeleteVacation(vacationId) {
        if (!adminPassword) {
            notify('Bitte Admin-Passwort eingeben');
            return;
        }
        try {
            await api.delete(`/api/vacation/${vacationId}`, {
                params: {
                    adminUsername: currentUser.username,
                    adminPassword
                }
            });
            notify('Urlaub erfolgreich gelöscht');
            setVacationsOnSelectedRange(prev => prev.filter(v => v.id !== vacationId));
            // Optional reload
            if (onReloadVacations) {
                onReloadVacations();
            }
        } catch (err) {
            console.error('Error deleting vacation', err);
            notify(`Fehler beim Löschen des Urlaubs (id=${vacationId})`);
        }
    }

    // Kalenderkacheln: Marker für vorhandene Urlaube
    function tileContent({ date, view }) {
        if (view === 'month') {
            // Finde Urlaube, die in diesen Tag fallen
            const day = new Date(date);
            day.setHours(0, 0, 0, 0);

            const vacs = vacationRequests.filter(vac =>
                vacationInRange(vac, day, day)
            );
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
        <div className="vacation-calendar-admin">
            <h2>Admin-Kalender (Bereich auswählen)</h2>
            <div className="admin-password-input">
                <label>Admin Passwort: </label>
                <input
                    type="password"
                    value={adminPassword}
                    onChange={(e) => setAdminPassword(e.target.value)}
                />
            </div>

            <Calendar
                onChange={handleCalendarChange}
                value={rangeValue}
                tileContent={tileContent}
                selectRange={true} // <-- Wichtig: Bereichsauswahl aktivieren
            />
            {selectingRange && (
                <p style={{ color: 'gray' }}>
                    Du bist dabei, einen Datumsbereich auszuwählen...
                </p>
            )}

            {/* Modal */}
            {showModal && Array.isArray(rangeValue) && rangeValue.length === 2 && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>
                            Urlaub im Bereich:{" "}
                            {rangeValue[0].toLocaleDateString("de-DE")} -{" "}
                            {rangeValue[1].toLocaleDateString("de-DE")}
                        </h3>

                        {vacationsOnSelectedRange.length > 0 ? (
                            <div>
                                <h4>Existierende Urlaube in diesem Bereich</h4>
                                <ul>
                                    {vacationsOnSelectedRange.map(vac => (
                                        <li key={vac.id}>
                                            <strong>{vac.username}</strong>{" "}
                                            {vac.halfDay ? "(Halber Tag)" : "(Ganzer Tag)"}
                                            {" - "}
                                            {vac.approved ? (
                                                <span style={{ color: "green" }}>Genehmigt</span>
                                            ) : vac.denied ? (
                                                <span style={{ color: "red" }}>Abgelehnt</span>
                                            ) : (
                                                <span style={{ color: "orange" }}>Offen</span>
                                            )}
                                            <button
                                                style={{ marginLeft: "10px" }}
                                                onClick={() => handleDeleteVacation(vac.id)}
                                            >
                                                Urlaub löschen
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        ) : (
                            <p>Keine Urlaubseinträge für diesen Zeitraum</p>
                        )}

                        <hr />
                        <h4>Neuen Urlaub anlegen (adminCreate)</h4>
                        <div className="form-group">
                            <label>Benutzer:</label>
                            <select
                                value={newVacationUser}
                                onChange={(e) => setNewVacationUser(e.target.value)}
                            >
                                <option value="">-- Nutzer wählen --</option>
                                {users.map((u) => (
                                    <option key={u.id} value={u.username}>
                                        {u.username}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="form-group">
                            <label>
                                <input
                                    type="checkbox"
                                    checked={newVacationHalfDay}
                                    onChange={(e) => setNewVacationHalfDay(e.target.checked)}
                                />
                                Halber Tag
                            </label>
                        </div>
                        <button onClick={handleCreateVacation}>
                            Urlaub erstellen (direkt genehmigt)
                        </button>

                        <div style={{ marginTop: "20px" }}>
                            <button onClick={() => setShowModal(false)}>Schließen</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

VacationCalendarAdmin.propTypes = {
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number,
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            color: PropTypes.string,
            username: PropTypes.string.isRequired,
            halfDay: PropTypes.bool,
        })
    ).isRequired,
    onReloadVacations: PropTypes.func,
};

export default VacationCalendarAdmin;
