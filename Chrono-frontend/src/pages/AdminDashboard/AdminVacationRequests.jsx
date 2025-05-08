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
                    className="vacation-header"
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        cursor: 'pointer',
                        marginBottom: '1rem'
                    }}
                    onClick={toggleExpansion}
                >
                    <h3 style={{ margin: 0 }}>
                        {t('adminDashboard.vacationRequestsTitle')}
                    </h3>
                    <span className="vacation-toggle-icon" style={{ fontSize: '1.3rem' }}>
            {isExpanded ? '▲' : '▼'}
          </span>
                </div>

                {isExpanded && (
                    <div className="vacations-content">
                        <div
                            className="vacation-search-bar"
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                marginBottom: '1rem',
                                gap: '0.5rem'
                            }}
                        >
                            <label style={{ fontWeight: 600 }}>
                                {t('search') || 'Suchen:'}
                            </label>
                            <input
                                type="text"
                                value={searchTerm}
                                onChange={handleSearch}
                                placeholder={
                                    t('adminDashboard.searchUser') || 'Benutzer suchen...'
                                }
                                style={{ maxWidth: '240px' }}
                            />
                        </div>

                        {filteredVacations.length === 0 ? (
                            <p>{t('adminDashboard.noVacations')}</p>
                        ) : (
                            <ul
                                className="vacation-list scrollable-list"
                                style={{
                                    maxHeight: '320px',
                                    overflowY: 'auto',
                                    overscrollBehavior: 'contain',
                                    WebkitOverflowScrolling: 'touch',
                                    borderTop: '1px solid var(--c-line)',
                                    paddingTop: '0.5rem'
                                }}
                            >
                                {filteredVacations.map((v) => (
                                    <li key={v.id} className="vacation-item">
                    <span className="vacation-text">
                      <strong>{v.username}</strong>: {formatDate(v.startDate)} –{' '}
                        {formatDate(v.endDate)}{' '}
                        {v.usesOvertime && (
                            <em style={{ color: '#007BFF' }}>
                                {t('overtimeVacationIcon', '🌙 Überstundenfrei')}
                            </em>
                        )}{' '}
                        {v.approved ? (
                            <span className="approved">
                          {t('adminDashboard.approved')}
                        </span>
                        ) : v.denied ? (
                            <span className="denied">
                          {t('adminDashboard.denied')}
                        </span>
                        ) : (
                            <span className="pending">
                          {t('adminDashboard.pending')}
                        </span>
                        )}
                    </span>

                                        {!v.approved && !v.denied && (
                                            <span className="vacation-buttons">
                        <button
                            className="approve-btn"
                            onClick={() => handleApproveVacation(v.id)}
                        >
                          {t('adminDashboard.acceptButton')}
                        </button>
                        <button
                            className="reject-btn"
                            onClick={() => handleDenyVacation(v.id)}
                        >
                          {t('adminDashboard.rejectButton')}
                        </button>
                      </span>
                                        )}
                                    </li>
                                ))}
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
