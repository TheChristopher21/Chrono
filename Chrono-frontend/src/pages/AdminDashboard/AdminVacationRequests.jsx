import React, { useState, useEffect, useCallback } from 'react';
import ModalOverlay from '../../components/ModalOverlay';
import PropTypes from 'prop-types';
import { formatDate } from './adminDashboardUtils';
import api from '../../utils/api';
import { useAuth } from '../../context/AuthContext';
import { useNotification } from '../../context/NotificationContext';
// Stelle sicher, dass die AdminDashboardScoped.css importiert wird, wenn sie hier benötigt wird,
// oder dass die Styles global oder von AdminDashboard.jsx geerbt werden.
// import '../../styles/AdminDashboardScoped.css'; // Ist typischerweise in AdminDashboard.jsx

const AdminVacationRequests = ({
                                   t,
                                   allVacations,
                                   handleApproveVacation,
                                   handleDenyVacation,
                                   onReloadVacations, // Callback zum Neuladen der Urlaubsdaten
                                   openSignal,
                               }) => {
    const { currentUser } = useAuth();
    const { notify } = useNotification();

    const [isExpanded, setIsExpanded] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');

    // State für das Lösch-Modal
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [vacationToDelete, setVacationToDelete] = useState(null);

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

    const filteredVacations = allVacations.filter((v) =>
        (v.username || t('adminVacation.unknownUser', 'Unbekannt')).toLowerCase().includes(searchTerm.toLowerCase()) ||
        (formatDate(v.startDate) || '').includes(searchTerm) ||
        (formatDate(v.endDate) || '').includes(searchTerm)
    );

    const sortedVacations = [...filteredVacations].sort((a, b) => b.id - a.id);

    const hasPending = allVacations.some(v => !v.approved && !v.denied);

    return (
        <> {/* Stellt sicher, dass CSS-Variablen verfügbar sind */}
            <section className={`vacation-section content-section${(!isExpanded && hasPending) ? ' has-pending' : ''}`}> {/* Allgemeine Klasse für Sektionen */}
                <div
                    className="section-header"
                    onClick={toggleExpansion}
                    role="button"
                    tabIndex={0}
                    onKeyPress={(e) => e.key === 'Enter' && toggleExpansion()}
                >
                    <h3 className="section-title">
                        {t('adminDashboard.vacationRequestsTitle', 'Urlaubsanträge')}
                    </h3>
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
                            <div className="vacation-requests-container" style={{ maxHeight: sortedVacations.length > 20 ? '70vh' : 'none' }}>
                            <ul className="item-list vacation-request-list">
                                {sortedVacations.map((v) => {
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
                                                <strong className="username">{v.username || t('adminVacation.unknownUser', 'Unbekannt')}</strong>
                                                <span>
                                                    {formatDate(v.startDate)} - {formatDate(v.endDate)}
                                                </span>
                                                <span className={`status-badge ${statusClass}`}>{status}</span>
                                                {v.halfDay && <span className="info-badge">{t('adminDashboard.halfDayShort', '½ Tag')}</span>}
                                                {v.usesOvertime && <span className="info-badge overtime-badge">🌙 {t('adminDashboard.overtimeVacationShort', 'ÜS')}</span>}
                                            </div>
                                            <div className="item-actions">
                                                {!v.approved && !v.denied && (
                                                    <>
                                                        <button
                                                            className="button-confirm-small"
                                                            onClick={() => handleApproveVacation(v.id)}
                                                            title={t('adminDashboard.approveButtonTitle', 'Urlaubsantrag genehmigen')}
                                                        >
                                                            {t('adminDashboard.approveButton', 'Genehmigen')}
                                                        </button>
                                                        <button
                                                            className="button-deny-small"
                                                            onClick={() => handleDenyVacation(v.id)}
                                                            title={t('adminDashboard.rejectButtonTitle', 'Urlaubsantrag ablehnen')}
                                                        >
                                                            {t('adminDashboard.rejectButton', 'Ablehnen')}
                                                        </button>
                                                    </>
                                                )}
                                                <button
                                                    className="button-delete-small"
                                                    onClick={() => openDeleteModal(v)}
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
                            <strong> {vacationToDelete.username || t('adminVacation.unknownUser', 'Unbekannt')} </strong>
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
};

AdminVacationRequests.defaultProps = {
    openSignal: 0,
};

export default AdminVacationRequests;