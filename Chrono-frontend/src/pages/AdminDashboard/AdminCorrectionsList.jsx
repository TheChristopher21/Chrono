// src/pages/AdminDashboard/AdminCorrectionsList.jsx
import  { useState, useMemo } from "react";
import PropTypes from "prop-types";
import { formatDate, formatTime } from "./adminDashboardUtils";
import CorrectionDecisionModal from "./CorrectionDecisionModal";
import "../../styles/AdminDashboardScoped.css";

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
    const [isExpanded, setIsExpanded] = useState(false);
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

    const isScrollable = groupedAndFilteredCorrections.length > 20;

    return (
        <section className="correction-section content-section">
            <div
                className="section-header"
                onClick={() => setIsExpanded(!isExpanded)}
                role="button"
                tabIndex={0}
                onKeyPress={(e) => e.key === 'Enter' && setIsExpanded(!isExpanded)}
            >
                <h3 className="section-title">
                    {t('adminDashboard.correctionRequestsTitle', 'Korrekturanträge')}
                </h3>
                <span className="toggle-icon">{isExpanded ? '▲' : '▼'}</span>
            </div>

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
                        <button
                            onClick={() => {
                                setSearchTerm('');
                                setSearchDate('');
                            }}
                            className="button-reset-filter"
                        >
                            {t('adminDashboard.resetFilters', 'Filter zurücksetzen')}
                        </button>
                    </div>

                    {groupedAndFilteredCorrections.length === 0 ? (
                        <p>{t('adminCorrections.noRequestsFound', 'Keine Anträge gefunden.')}</p>
                    ) : (
                        <div
                            className="corrections-list-container"
                            style={{ maxHeight: isScrollable ? '70vh' : 'none' }}
                        >
                            <ul className="correction-request-list">
                                {groupedAndFilteredCorrections.map((group) => {
                                    const statusClass = `status-${group.status.toLowerCase()}`;
                                    return (
                                        <li
                                            key={group.id}
                                            className={`list-item correction-item ${statusClass}`}
                                        >
                                            <div className="item-info">
                                                <strong className="username">{group.username}</strong>
                                                <span className="request-date">{formatDate(group.requestDate)}</span>
                                                <div className="correction-details">
                                                    {group.entries.map((req) => (
                                                        <div className="request-detail" key={req.id}>
                                                            {req.originalTimestamp ? (
                                                                <span>
                                                                    <s className="original-time">
                                                                        {formatTime(req.originalTimestamp)} {req.originalPunchType}
                                                                    </s>{' '}
                                                                    &rarr;{' '}
                                                                    <strong className="desired-time">
                                                                        {formatTime(req.desiredTimestamp)} {req.desiredPunchType}
                                                                    </strong>
                                                                </span>
                                                            ) : (
                                                                <strong className="desired-time">
                                                                    Neu: {formatTime(req.desiredTimestamp)} {req.desiredPunchType}
                                                                </strong>
                                                            )}
                                                        </div>
                                                    ))}

                                                </div>
                                                <span className="reason-text">{group.reason}</span>
                                                <span className={`status-badge ${statusClass}`}>
                                                    {t(`status.${group.status.toLowerCase()}`, group.status)}
                                                </span>
                                            </div>
                                            <div className="item-actions">
                                                {group.status === 'PENDING' ? (
                                                    <>
                                                        <button
                                                            className="button-confirm-small"
                                                            onClick={() => openModal(group.entries.map((e) => e.id), 'approve')}
                                                            title={t('adminDashboard.acceptButton')}
                                                        >
                                                            {t('adminDashboard.approveButton', 'Genehmigen')}
                                                        </button>
                                                        <button
                                                            className="button-deny-small"
                                                            onClick={() => openModal(group.entries.map((e) => e.id), 'deny')}
                                                            title={t('adminDashboard.rejectButton')}
                                                        >
                                                            {t('adminDashboard.rejectButton', 'Ablehnen')}
                                                        </button>
                                                    </>
                                                ) : (
                                                    t('done', 'Erledigt')
                                                )}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        </div>
                    )}
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
        </section>
    );
};

AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.array.isRequired,
    onApprove: PropTypes.func.isRequired,
    onDeny: PropTypes.func.isRequired,
};

export default AdminCorrectionsList;
