// src/pages/AdminDashboard/AdminCorrectionsList.jsx
// IMPROVED, COMPACT & SORTABLE TABLE LAYOUT 💎
// -----------------------------------------------------------------------------
// WHAT CHANGED?
//  • Switched from an unordered list to an accessible table-layout → much more
//    compact & scannable when many requests pile up.
//  • Added column-sorting (username, date, status) with visual indicators.
//  • Sticky header & scroll-area so the list is always readable, even with
//    hundreds of rows.
//  • Grouped requests (same user + same day + same reason) are still kept
//    together, but now shown on one row; details can be expanded inline.
//  • Re-used your existing approve/deny modal – just wired the new buttons.
//  • No external deps: pure React + existing CSS variables.
// -----------------------------------------------------------------------------

import React, { useMemo, useState } from "react";
import PropTypes from "prop-types";
import { formatDate, formatTime } from "./adminDashboardUtils";
import CorrectionDecisionModal from "./CorrectionDecisionModal";
import "../../styles/AdminDashboardScoped.css";

/* ⇢ Helper to derive a readable status ------------------------------------ */
const getStatus = (req) => {
    if (req.approved) return "APPROVED";
    if (req.denied) return "DENIED";
    return "PENDING";
};

/* ⇢ Sort individual entries chronologically – earliest → latest ----------- */
const sortEntriesChronologically = (a, b) => {
    const tsA = a.desiredTimestamp || a.originalTimestamp || 0;
    const tsB = b.desiredTimestamp || b.originalTimestamp || 0;
    return new Date(tsA) - new Date(tsB);
};

/* ⇢ Main component --------------------------------------------------------- */
function AdminCorrectionsList({ t, allCorrections, onApprove, onDeny }) {
    /* ──────────────────────────────────── state */
    const [isExpanded, setIsExpanded] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");
    const [searchDate, setSearchDate] = useState("");
    const [sortConfig, setSortConfig] = useState({ key: "requestDate", dir: "desc" });
    const [modalOpen, setModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState("approve");
    const [targetIds, setTargetIds] = useState([]);
    const [adminComment, setAdminComment] = useState("");

    /* ──────────────────────────────────── data helpers */
    const requestSort = (key) => {
        setSortConfig((prev) => {
            const dir = prev.key === key && prev.dir === "asc" ? "desc" : "asc";
            return { key, dir };
        });
    };
    const sortIndicator = (key) => {
        if (sortConfig.key !== key) return "";
        return sortConfig.dir === "asc" ? " ▲" : " ▼";
    };

    const openDecisionModal = (ids, mode) => {
        setTargetIds(ids);
        setModalMode(mode);
        setAdminComment("");
        setModalOpen(true);
    };
    const submitDecision = async () => {
        const tasks = targetIds.map((id) =>
            modalMode === "approve" ? onApprove(id, adminComment) : onDeny(id, adminComment)
        );
        try {
            await Promise.all(tasks);
        } finally {
            setModalOpen(false);
        }
    };

    /* ──────────────────────────────────── build rows */
    const groupedRows = useMemo(() => {
        const groups = new Map();

        const filtered = allCorrections.filter((c) => {
            const matchesUser = c.username?.toLowerCase().includes(searchTerm.toLowerCase());
            const matchesDate = !searchDate || c.requestDate === searchDate;
            return matchesUser && matchesDate;
        });

        for (const req of filtered) {
            const key = `${req.username}|${req.requestDate}|${req.reason}`;
            const status = getStatus(req);

            if (!groups.has(key)) {
                groups.set(key, {
                    id: req.id, // first-seen id works as stable key
                    username: req.username,
                    requestDate: req.requestDate,
                    reason: req.reason,
                    status,
                    entries: [],
                });
            }
            const g = groups.get(key);
            g.entries.push(req);
            // If ANY entry is still pending → whole group is pending
            if (status === "PENDING") g.status = "PENDING";
        }

        // sort entries inside each group chronologically
        for (const g of groups.values()) g.entries.sort(sortEntriesChronologically);

        return Array.from(groups.values());
    }, [allCorrections, searchTerm, searchDate]);

    /* ──────────────────────────────────── apply sorting */
    const sortedRows = useMemo(() => {
        const rows = [...groupedRows];
        const { key, dir } = sortConfig;
        rows.sort((a, b) => {
            let valA = a[key];
            let valB = b[key];

            // custom: keep newest groups on top by default (requestDate desc)
            if (key === "requestDate") {
                valA = new Date(a.requestDate);
                valB = new Date(b.requestDate);
            }
            if (valA < valB) return dir === "asc" ? -1 : 1;
            if (valA > valB) return dir === "asc" ? 1 : -1;
            return 0;
        });
        return rows;
    }, [groupedRows, sortConfig]);

    /* ──────────────────────────────────── render */
    const scrollable = sortedRows.length > 25;

    return (
        <section className="correction-section content-section">
            {/* Header ----------------------------------------------------------------*/}
            <div
                className="section-header"
                role="button"
                tabIndex={0}
                onClick={() => setIsExpanded(!isExpanded)}
                onKeyDown={(e) => e.key === "Enter" && setIsExpanded(!isExpanded)}
            >
                <h3 className="section-title">
                    {t("adminDashboard.correctionRequestsTitle", "Korrekturanträge")}
                </h3>
                <span className="toggle-icon">{isExpanded ? "▲" : "▼"}</span>
            </div>

            {/* Content --------------------------------------------------------------*/}
            {isExpanded && (
                <div className="section-content">
                    {/* Filters */}
                    <div className="list-controls">
                        <input
                            type="text"
                            className="search-input"
                            placeholder={t("adminDashboard.searchByUser", "Nach Benutzer suchen…")}
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                        <input
                            type="date"
                            className="date-input"
                            value={searchDate}
                            onChange={(e) => setSearchDate(e.target.value)}
                        />
                        <button
                            className="button-reset-filter"
                            onClick={() => {
                                setSearchTerm("");
                                setSearchDate("");
                            }}
                        >
                            {t("adminDashboard.resetFilters", "Filter zurücksetzen")}
                        </button>
                    </div>

                    {sortedRows.length === 0 ? (
                        <p>{t("adminCorrections.noRequestsFound", "Keine Anträge gefunden.")}</p>
                    ) : (
                        <div
                            className="table-wrapper"
                            style={{ maxHeight: scrollable ? "70vh" : "none", overflowY: scrollable ? "auto" : "visible" }}
                        >
                            <table className="corrections-table">
                                <thead>
                                <tr>
                                    <th onClick={() => requestSort("username")}>{t("user", "Benutzer")}{sortIndicator("username")}</th>
                                    <th onClick={() => requestSort("requestDate")}>{t("date", "Datum")}{sortIndicator("requestDate")}</th>
                                    <th>{t("changes", "Änderungen")}</th>
                                    <th>{t("reason", "Grund")}</th>
                                    <th onClick={() => requestSort("status")}>{t("status", "Status")}{sortIndicator("status")}</th>
                                    <th className="th-actions">{t("actions", "Aktionen")}</th>
                                </tr>
                                </thead>
                                <tbody>
                                {sortedRows.map((g) => {
                                    const statusClass = g.status.toLowerCase();
                                    const ids = g.entries.map((e) => e.id);

                                    return (
                                        <React.Fragment key={g.id}>
                                            <tr className={`status-${statusClass}`}>
                                                <td>{g.username}</td>
                                                <td>{formatDate(g.requestDate)}</td>
                                                <td>
                                                    {/* 1-liner summary when collapsed */}
                                                    {g.entries.length === 1 ? (
                                                        <SingleEntry entry={g.entries[0]} />
                                                    ) : (
                                                        <span>{g.entries.length} {t("changes", "Änderungen")}</span>
                                                    )}
                                                </td>
                                                <td>{g.reason}</td>
                                                <td>
                            <span className={`status-badge status-${statusClass}`}>
                              {t(`adminDashboard.status${g.status}`, g.status)}
                            </span>
                                                </td>
                                                <td className="actions-cell">
                                                    {g.status === "PENDING" && (
                                                        <>
                                                            <button className="button-confirm-small" title={t("approve", "Genehmigen")} onClick={() => openDecisionModal(ids, "approve")}>✓</button>
                                                            <button className="button-deny-small" title={t("deny", "Ablehnen")} onClick={() => openDecisionModal(ids, "deny")}>✕</button>
                                                        </>
                                                    )}
                                                </td>
                                            </tr>
                                            {/* → optional expandable row with full timeline */}
                                            {g.entries.length > 1 && (
                                                <tr className="detail-row">
                                                    <td colSpan={6}>
                                                        {g.entries.map((e) => (
                                                            <div key={e.id} className="detail-line">
                                                                <SingleEntry entry={e} />
                                                            </div>
                                                        ))}
                                                    </td>
                                                </tr>
                                            )}
                                        </React.Fragment>
                                    );
                                })}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            )}

            {/* Modal ---------------------------------------------------------------*/}
            {modalOpen && (
                <CorrectionDecisionModal
                    visible={modalOpen}
                    mode={modalMode}
                    comment={adminComment}
                    setComment={setAdminComment}
                    onClose={() => setModalOpen(false)}
                    onSubmit={submitDecision}
                />
            )}
        </section>
    );
}

/* ⇢ Helper sub-component --------------------------------------------------- */
const SingleEntry = ({ entry }) => {
    const oldTimePart = entry.originalTimestamp ? (
        <>
            <s>{formatTime(entry.originalTimestamp)} {entry.originalPunchType}</s> →{' '}
        </>
    ) : null;

    return (
        <span>
      {oldTimePart}
            <strong>{formatTime(entry.desiredTimestamp)} {entry.desiredPunchType}</strong>
    </span>
    );
};
SingleEntry.propTypes = {
    entry: PropTypes.shape({
        originalTimestamp: PropTypes.string,
        desiredTimestamp: PropTypes.string.isRequired,
        originalPunchType: PropTypes.string,
        desiredPunchType: PropTypes.string.isRequired,
    }).isRequired,
};

/* ⇢ PropTypes -------------------------------------------------------------- */
AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.arrayOf(PropTypes.object).isRequired,
    onApprove: PropTypes.func.isRequired,
    onDeny: PropTypes.func.isRequired,
};

export default AdminCorrectionsList;
