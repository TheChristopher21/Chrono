import React, { useState } from 'react';
import PropTypes from 'prop-types';
import { formatDate } from './adminDashboardUtils';
import '../../styles/AdminDashboardScoped.css';

const AdminVacationRequests = ({
                                   t,
                                   allVacations,
                                   handleApproveVacation,
                                   handleDenyVacation
                               }) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');

    function toggleExpansion() {
        setIsExpanded(!isExpanded);
    }

    function handleSearch(e) {
        setSearchTerm(e.target.value);
    }

    const filteredVacations = allVacations.filter((v) =>
        v.username.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="admin-dashboard scoped-dashboard">
            <section className="vacation-section">
                <div
                    className="flex justify-between items-center cursor-pointer mb-4"
                    onClick={toggleExpansion}
                >
                    <h3 className="m-0">
                        {t('adminDashboard.vacationRequestsTitle')}
                    </h3>
                    <span className="text-xl">
            {isExpanded ? '▲' : '▼'}
          </span>
                </div>

                {isExpanded && (
                    <div className="vacations-content">
                        <div
                            className="vacation-search-bar flex items-center mb-4 gap-2"
                        >
                            <label className="font-semibold">
                                {t('search') || 'Suchen:'}
                            </label>
                            <input
                                type="text"
                                value={searchTerm}
                                onChange={handleSearch}
                                placeholder={
                                    t('adminDashboard.searchUser') || 'Benutzer suchen...'
                                }
                                className="max-w-xs p-2 border rounded"
                            />
                        </div>

                        {filteredVacations.length === 0 ? (
                            <p>{t('adminDashboard.noVacations')}</p>
                        ) : (
                            <ul className="vacation-list space-y-2 max-h-80 overflow-y-auto border-t pt-2">
                                {filteredVacations.map((v) => {
                                    const statusConfig = v.approved
                                        ? { color: 'bg-green-100', icon: '✔️', text: t('adminDashboard.approved') }
                                        : v.denied
                                            ? { color: 'bg-red-100', icon: '❌', text: t('adminDashboard.denied') }
                                            : { color: 'bg-yellow-100', icon: '⏳', text: t('adminDashboard.pending') };
                                    return (
                                        <li key={v.id} className={`${statusConfig.color} p-3 rounded shadow flex justify-between items-center`}>
                                            <div>
                                                <strong>{v.username}</strong>: {formatDate(v.startDate)} – {formatDate(v.endDate)}{' '}
                                                {v.usesOvertime && (
                                                    <em className="text-blue-600">
                                                        {t('overtimeVacationIcon', '🌙 Überstundenfrei')}
                                                    </em>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <span>{statusConfig.icon}</span>
                                                <span className="font-semibold">{statusConfig.text}</span>
                                                {!v.approved && !v.denied && (
                                                    <>
                                                        <button
                                                            className="ml-2 bg-green-500 text-white px-2 py-1 rounded"
                                                            onClick={() => handleApproveVacation(v.id)}
                                                        >
                                                            {t('adminDashboard.acceptButton')}
                                                        </button>
                                                        <button
                                                            className="ml-2 bg-red-500 text-white px-2 py-1 rounded"
                                                            onClick={() => handleDenyVacation(v.id)}
                                                        >
                                                            {t('adminDashboard.rejectButton')}
                                                        </button>
                                                    </>
                                                )}
                                            </div>
                                        </li>
                                    );
                                })}
                            </ul>
                        )}
                    </div>
                )}
            </section>
        </div>
    );
};

AdminVacationRequests.propTypes = {
    t: PropTypes.func.isRequired,
    allVacations: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number,
            username: PropTypes.string,
            startDate: PropTypes.string,
            endDate: PropTypes.string,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            usesOvertime: PropTypes.bool
        })
    ).isRequired,
    handleApproveVacation: PropTypes.func.isRequired,
    handleDenyVacation: PropTypes.func.isRequired
};

export default AdminVacationRequests;
