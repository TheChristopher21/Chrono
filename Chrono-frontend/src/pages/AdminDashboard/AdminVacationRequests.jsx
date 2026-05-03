import React, { useState, useEffect, useCallback } from 'react';
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from 'prop-types';
import { formatDate } from './adminDashboardUtils';
import api from '../../utils/api';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
import { getUserDisplayName, getUserSearchText } from '../../utils/userDisplay';
// Stelle sicher, dass die AdminDashboardScoped.css importiert wird, wenn sie hier benötigt wird,
// oder dass die Styles global oder von AdminDashboard.jsx geerbt werden.
// import '../../styles/AdminDashboardScoped.css'; // Ist typischerweise in AdminDashboard.jsx

const isPendingVacation = (vacation) => {
    const status = String(vacation?.status || '').toLowerCase();
    if (['pending', 'open', 'ausstehend', 'offen'].includes(status)) {
        return true;
    }
    return !vacation?.approved && !vacation?.denied;
};

const AdminVacationRequests = ({
                                   t,
                                   allVacations,
                                   handleApproveVacation,
                                   handleDenyVacation,
                                   onReloadVacations, // Callback zum Neuladen der Urlaubsdaten
                                   openSignal,
                                   canManage,
                                   users,
                               }) => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const [isExpanded, setIsExpanded] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');

    // State für das Lösch-Modal
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [vacationToDelete, setVacationToDelete] = useState(null);
    const [decisionNotes, setDecisionNotes] = useState({});

    function toggleExpansion() {
        setIsExpanded(!isExpanded);
    }

    useEffect(() => {
        if (typeof openSignal === 'number' && openSignal > 0) {
            setIsExpanded(true);
        }
    }, [openSignal]);

    function handleSearch(e) {
        setSearchTerm(e.target.value);
    }

    const openDeleteModal = (vacation) => {
        if (!canManage) {
            notify(t('adminDashboard.readOnlyPermissions', 'Nur Ansicht: Dieser Benutzer darf das Admin-Dashboard sehen, aber keine Freigaben oder Änderungen ausführen.'), 'warning');
            return;
        }
        setVacationToDelete(vacation);
        setShowDeleteModal(true);
    };

    const handleDeleteVacation = async () => {
        if (!vacationToDelete) {
            notify(t('adminVacation.delete.noSelection', 'Kein Urlaub zum Löschen ausgewählt.'), 'error');
            return;
        }
        if (!currentUser || !currentUser.username) {
            notify(t('errors.notLoggedIn', 'Admin nicht eingeloggt oder Benutzername fehlt.'), 'error');
            return;
        }

        try {
            await api.delete(`/api/vacation/${vacationToDelete.id}`, {
                params: { // Wichtig: adminUsername und adminPassword als Request-Parameter senden
                    adminUsername: currentUser.username,
                },
            });
            notify(t('adminVacation.delete.success', 'Urlaubsantrag erfolgreich gelöscht.'), 'success');
            setShowDeleteModal(false);
            setVacationToDelete(null);
            if (onReloadVacations) {
                onReloadVacations(); // Urlaubsdaten neu laden, um die Liste zu aktualisieren
            }
        } catch (err) {
            console.error('Error deleting vacation request:', err);
            const errorMsg = err.response?.data?.message || err.response?.data || err.message || t('errors.unknownError', "Unbekannter Fehler");
            notify(t('adminVacation.delete.error', 'Fehler beim Löschen des Urlaubsantrags:') + ` ${errorMsg}`, 'error');
        }
    };

    const getDecisionNoteKey = useCallback((id) => `vacation-${id}`, []);

    const handleDecisionNoteChange = useCallback((id, value) => {
        const key = getDecisionNoteKey(id);
        setDecisionNotes((prev) => ({
            ...prev,
            [key]: value,
        }));
    }, [getDecisionNoteKey]);

    const filteredVacations = allVacations.filter((v) => {
        const normalizedSearch = searchTerm.toLowerCase();
        return getUserSearchText(v.username, users).includes(normalizedSearch) ||
            (formatDate(v.startDate) || '').includes(searchTerm) ||
            (formatDate(v.endDate) || '').includes(searchTerm);
    });

    const sortedVacations = [...filteredVacations].sort((a, b) => b.id - a.id);

    const pendingCount = allVacations.filter(isPendingVacation).length;
    const hasPending = pendingCount > 0;
    const isScrollable = sortedVacations.length >= 10;

    return (
        <> {/* Stellt sicher, dass CSS-Variablen verfügbar sind */}
            <section className={`vacation-section content-section${!isExpanded ? ' is-collapsed' : ''}${(!isExpanded && hasPending) ? ' has-pending' : ''}`}> {/* Allgemeine Klasse für Sektionen */}
                <div
                    className="section-header"
                    onClick={toggleExpansion}
                    role="button"
                    aria-expanded={isExpanded}
                    tabIndex={0}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            toggleExpansion();
                        }
                    }}
                >
                    <div className="section-header-main">
                        <h3 className="section-title">
                            {t('adminDashboard.vacationRequestsTitle', 'Urlaubsanträge')}
                        </h3>
                        {hasPending && (
                            <span className="pending-indicator" aria-label={`${pendingCount} ${t('adminDashboard.pendingRequestsOpen', 'offen')}`}>
                                <span className="pending-dot" aria-hidden="true" />
                                {pendingCount} {t('adminDashboard.pendingRequestsOpen', 'offen')}
                            </span>
                        )}
                    </div>
                    <span className="toggle-icon">
                        {isExpanded ? '▲' : '▼'}
                    </span>
                </div>

                {isExpanded && (
                    <div className="section-content">
                        <input
                            type="text"
                            placeholder={t('adminDashboard.searchUserPlaceholder', 'Suche nach Benutzer oder Datum...')}
                            value={searchTerm}
                            onChange={handleSearch}
                            className="search-input"
                        />
                        {sortedVacations.length === 0 ? (
                            <p>{t('adminDashboard.noVacationRequests', 'Keine Urlaubsanträge gefunden.')}</p>
                        ) : (
                            <div className={`vacation-requests-container${isScrollable ? ' scroll-limited' : ''}`}>
                            <ul className="item-list vacation-request-list">
                                {sortedVacations.map((v) => {
                                    const isPending = isPendingVacation(v);
                                    const status = v.approved
                                        ? t('adminDashboard.statusApproved', 'Genehmigt')
                                        : v.denied
                                            ? t('adminDashboard.statusDenied', 'Abgelehnt')
                                            : t('adminDashboard.statusPending', 'Ausstehend');
                                    const statusClass = v.approved
                                        ? 'status-approved'
                                            : v.denied
                                                ? 'status-denied'
                                                : 'status-pending';

                                    return (
                                        <li key={v.id} className={`list-item vacation-item ${statusClass}`}>
                                            <div className="item-info">
                                                <strong className="username">{getUserDisplayName(v.username, users, t('adminVacation.unknownUser', 'Unbekannt'))}</strong>
                                                <span>
                                                    {formatDate(v.startDate)} - {formatDate(v.endDate)}
                                                </span>
                                                <span className={`status-badge ${statusClass}`}>{status}</span>
                                                {v.halfDay && <span className="info-badge">{t('adminDashboard.halfDayShort', '½ Tag')}</span>}
                                                {v.usesOvertime && <span className="info-badge overtime-badge">🌙 {t('adminDashboard.overtimeVacationShort', 'ÜS')}</span>}
                                                {v.adminNote && (
                                                    <span>
                                                        <strong>{t('userDashboard.adminNote', 'Admin-Notiz')}:</strong> {v.adminNote}
                                                    </span>
                                                )}
                                            </div>
                                            <div className="item-actions">
                                                {canManage && isPending && (
                                                    <>
                                                        <input
                                                            type="text"
                                                            value={decisionNotes[getDecisionNoteKey(v.id)] || ''}
                                                            onChange={(event) => handleDecisionNoteChange(v.id, event.target.value)}
                                                            placeholder={t('userDashboard.adminNote', 'Admin-Notiz')}
                                                            className="vacation-admin-note-input"
                                                        />
                                                        <button
                                                            className="button-confirm-small"
                                                            onClick={() => {
                                                                const note = (decisionNotes[getDecisionNoteKey(v.id)] || '').trim();
                                                                handleApproveVacation(v.id, note);
                                                                setDecisionNotes((prev) => {
                                                                    const next = { ...prev };
                                                                    delete next[getDecisionNoteKey(v.id)];
                                                                    return next;
                                                                });
                                                            }}
                                                            title={t('adminDashboard.approveButtonTitle', 'Urlaubsantrag genehmigen')}
                                                        >
                                                            {t('adminDashboard.approveButton', 'Genehmigen')}
                                                        </button>
                                                        <button
                                                            className="button-deny-small"
                                                            onClick={() => {
                                                                const note = (decisionNotes[getDecisionNoteKey(v.id)] || '').trim();
                                                                handleDenyVacation(v.id, note);
                                                                setDecisionNotes((prev) => {
                                                                    const next = { ...prev };
                                                                    delete next[getDecisionNoteKey(v.id)];
                                                                    return next;
                                                                });
                                                            }}
                                                            title={t('adminDashboard.rejectButtonTitle', 'Urlaubsantrag ablehnen')}
                                                        >
                                                            {t('adminDashboard.rejectButton', 'Ablehnen')}
                                                        </button>
                                                    </>
                                                )}
                                                <button
                                                    className="button-delete-small"
                                                    onClick={() => canManage ? openDeleteModal(v) : notify(t('adminDashboard.readOnlyPermissions', 'Nur Ansicht: Dieser Benutzer darf das Admin-Dashboard sehen, aber keine Freigaben oder Änderungen ausführen.'), 'warning')}
                                                    disabled={!canManage}
                                                    title={t('adminVacation.delete.buttonTitle', 'Urlaubsantrag löschen')}
                                                >
                                                    🗑️ <span className="button-text-mobile-hidden">{t('delete', 'Löschen')}</span>
                                                </button>
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                            </div>
                        )}
                    </div>
                )}
            </section>

            {/* Modal zum Bestätigen des Löschens */}
            {showDeleteModal && vacationToDelete && (
                <ModalOverlay visible className="">
                    <div className="modal-content delete-confirmation-modal"> {/* Eigene Klasse für spezifisches Styling */}
                        <h3>{t('adminVacation.delete.confirmTitle', 'Urlaub löschen bestätigen')}</h3>
                        <p>
                            {t('adminVacation.delete.confirmMessage', 'Möchten Sie den Urlaubsantrag von')}
                            <strong> {getUserDisplayName(vacationToDelete.username, users, t('adminVacation.unknownUser', 'Unbekannt'))} </strong>
                            ({formatDate(vacationToDelete.startDate)} - {formatDate(vacationToDelete.endDate)}){' '}
                            {t('adminVacation.delete.irreversible', 'wirklich unwiderruflich löschen?')}
                        </p>
                        {vacationToDelete.usesOvertime && vacationToDelete.approved && (
                            <p className="warning-text">
                                {t('adminVacation.delete.overtimeReversalInfo', 'Bei genehmigten Überstundenurlauben werden die abgezogenen Stunden dem Benutzerkonto wieder gutgeschrieben.')}
                            </p>
                        )}
                        {!vacationToDelete.usesOvertime && vacationToDelete.approved && (
                            <p className="info-text">
                                {t('adminVacation.delete.regularVacationInfo', 'Dies ist ein regulärer Urlaub. Die Tage werden dem Jahresurlaubskonto wieder gutgeschrieben (effektiv durch Neuberechnung der Resturlaubstage).')}
                            </p>
                        )}


                        <div className="modal-buttons"> {/* Wiederverwendung aus VacationCalendarAdmin */}
                            <button onClick={handleDeleteVacation} className="button-danger"> {/* Spezifische Klasse für Lösch-Button */}
                                {t('adminVacation.delete.confirmDeleteButton', 'Ja, löschen')}
                            </button>
                            <button onClick={() => setShowDeleteModal(false)} className="button-cancel">
                                {t('cancel', 'Abbrechen')}
                            </button>
                        </div>
                    </div>
                </ModalOverlay>
            )}
        </>
    );
};

AdminVacationRequests.propTypes = {
    t: PropTypes.func.isRequired,
    allVacations: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number.isRequired,
            username: PropTypes.string,
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            usesOvertime: PropTypes.bool,
            halfDay: PropTypes.bool
        })
    ).isRequired,
    handleApproveVacation: PropTypes.func.isRequired,
    handleDenyVacation: PropTypes.func.isRequired,
    onReloadVacations: PropTypes.func.isRequired, // Wichtig für die Aktualisierung der Liste
    openSignal: PropTypes.number,
    canManage: PropTypes.bool,
    users: PropTypes.arrayOf(PropTypes.object),
};

AdminVacationRequests.defaultProps = {
    openSignal: 0,
    canManage: true,
    users: [],
};

export default AdminVacationRequests;
