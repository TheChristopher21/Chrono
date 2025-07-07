// src/pages/AdminDashboard/AdminCorrectionsList.jsx
import React, { useState, useMemo } from "react";
import PropTypes from "prop-types";
import { formatDate, formatTime } from "./adminDashboardUtils";
import CorrectionDecisionModal from "./CorrectionDecisionModal";
import "../../styles/AdminDashboardScoped.css";

// Hilfsfunktion, um den Status aus den boolean-Werten abzuleiten
const getStatus = (req) => {
    if (req.approved) return 'APPROVED';
    if (req.denied) return 'DENIED';
    return 'PENDING';
};

const AdminCorrectionsList = ({
                                  t,
                                  allCorrections,
                                  onApprove,
                                  onDeny,
                              }) => {
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState("approve");
    // Wird jetzt ein Array von IDs für die Gruppenverarbeitung halten
    const [targetIds, setTargetIds] = useState([]);
    const [adminComment, setAdminComment] = useState("");
    const [isExpanded, setIsExpanded] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [searchDate, setSearchDate] = useState('');

    const openModal = (ids, mode) => {
        setTargetIds(ids);
        setModalMode(mode);
        setAdminComment("");
        setModalOpen(true);
    };

    const submitDecision = async () => {
        // Sendet für jede ID in der Gruppe eine Anfrage
        const decisionPromises = targetIds.map(id => {
            if (modalMode === "approve") {
                return onApprove(id, adminComment);
            } else {
                return onDeny(id, adminComment);
            }
        });
        try {
            await Promise.all(decisionPromises);
        } finally {
            setModalOpen(false);
        }
    };

    // NEUE LOGIK: Gruppiert einzelne Anträge zu einer logischen Einheit
    const groupedAndFilteredCorrections = useMemo(() => {
        const groups = new Map();
        const filtered = allCorrections.filter(c => {
            const matchesUser = c.username?.toLowerCase().includes(searchTerm.toLowerCase());
            const matchesDate = !searchDate || c.requestDate === searchDate;
            return matchesUser && matchesDate;
        });

        for (const req of filtered) {
            const groupKey = `${req.username}|${req.requestDate}|${req.reason}`;
            const currentStatus = getStatus(req);

            if (!groups.has(groupKey)) {
                groups.set(groupKey, {
                    id: req.id, // ID des ersten Eintrags als Key
                    username: req.username,
                    requestDate: req.requestDate,
                    reason: req.reason,
                    status: currentStatus,
                    entries: [],
                });
            }

            const group = groups.get(groupKey);
            group.entries.push(req);
            // Eine Gruppe ist PENDING, solange nicht alle Einträge bearbeitet sind.
            if (currentStatus === 'PENDING') {
                group.status = 'PENDING';
            }
        }

        return Array.from(groups.values()).sort((a, b) => b.id - a.id);
    }, [allCorrections, searchTerm, searchDate]);

    return (
        <div className="content-section">
            <header className="section-header" onClick={() => setIsExpanded(!isExpanded)}>
                <h3 className="section-title">{t('Kurrektur Anträge')}</h3>
                <span className="toggle-icon">{isExpanded ? '−' : '+'}</span>
            </header>

            {isExpanded && (
                <div className="section-content">
                    <div className="list-controls">
                        <input
                            type="text"
                            placeholder={t('adminDashboard.searchByUser', 'Nach Benutzer suchen...')}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="search-input"
                        />
                        <input
                            type="date"
                            value={searchDate}
                            onChange={(e) => setSearchDate(e.target.value)}
                            className="date-input"
                        />
                        <button onClick={() => { setSearchTerm(''); setSearchDate(''); }} className="button-reset-filter">
                            {t('adminDashboard.resetFilters', 'Filter zurücksetzen')}
                        </button>
                    </div>
                    <div className="corrections-list-container">
                        <table className="corrections-table">
                            <thead>
                            <tr>
                                <th>{t('adminCorrections.header.user', 'Benutzer')}</th>
                                <th>{t('adminCorrections.header.date', 'Antragsdatum')}</th>
                                <th>{t('adminCorrections.header.request', 'Anfrage')}</th>
                                <th className="reason-col">{t('adminCorrections.header.reason', 'Grund')}</th>
                                <th>{t('adminCorrections.header.status', 'Status')}</th>
                                <th>{t('adminCorrections.header.actions', 'Aktionen')}</th>
                            </tr>
                            </thead>
                            <tbody>
                            {groupedAndFilteredCorrections.length > 0 ? (
                                groupedAndFilteredCorrections.map(group => (
                                    <tr key={group.id}>
                                        <td data-label={t('adminCorrections.header.user')}>{group.username}</td>
                                        <td data-label={t('adminCorrections.header.date')}>{formatDate(group.requestDate)}</td>
                                        <td data-label={t('adminCorrections.header.request')}>
                                            {group.entries.map(req => (
                                                <div className="request-detail" key={req.id}>
                                                    {req.originalTimestamp ? (
                                                        <span>
                                                                <s className="original-time">{formatTime(req.originalTimestamp)} {req.originalPunchType}</s> &rarr; <strong className="desired-time">{formatTime(req.desiredTimestamp)} {req.desiredPunchType}</strong>
                                                            </span>
                                                    ) : (
                                                        <strong className="desired-time">Neu: {formatTime(req.desiredTimestamp)} {req.desiredPunchType}</strong>
                                                    )}
                                                </div>
                                            ))}
                                        </td>
                                        <td data-label={t('adminCorrections.header.reason')} className="reason-cell">{group.reason}</td>
                                        <td data-label={t('adminCorrections.header.status')}>
                                                <span className={`status-badge status-${group.status.toLowerCase()}`}>
                                                    {t(`status.${group.status.toLowerCase()}`, group.status)}
                                                </span>
                                        </td>
                                        <td data-label={t('adminCorrections.header.actions')}>
                                            {group.status === 'PENDING' ? (
                                                <div className="action-buttons">
                                                    <button onClick={() => openModal(group.entries.map(e => e.id), 'approve')} className="button-approve" title={t('adminDashboard.acceptButton')}>✓</button>
                                                    <button onClick={() => openModal(group.entries.map(e => e.id), 'deny')} className="button-deny" title={t('adminDashboard.rejectButton')}>×</button>
                                                </div>
                                            ) : t('done', 'Erledigt')}
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan="6" className="no-entries">{t('adminCorrections.noRequestsFound', 'Keine Anträge gefunden.')}</td>
                                </tr>
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
            <CorrectionDecisionModal
                visible={modalOpen}
                mode={modalMode}
                comment={adminComment}
                setComment={setAdminComment}
                onSubmit={submitDecision}
                onClose={() => setModalOpen(false)}
                t={t}
            />
        </div>
    );
};

AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.array.isRequired,
    onApprove: PropTypes.func.isRequired,
    onDeny: PropTypes.func.isRequired,
};

export default AdminCorrectionsList;