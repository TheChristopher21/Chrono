// AdminVacationRequests.jsx
import 'react';
import { formatDate } from './adminDashboardUtils';
import PropTypes from 'prop-types';
import '../../styles/AdminDashboardScoped.css';

const AdminVacationRequests = ({
                                   t,
                                   allVacations,
                                   handleApproveVacation,
                                   handleDenyVacation
                               }) => {
    return (
        <div className="admin-dashboard scoped-dashboard">
        <section className="vacation-section">
            <h3>{t('adminDashboard.vacationRequestsTitle')}</h3>
            {allVacations.length === 0 ? (
                <p>{t('adminDashboard.noVacations')}</p>
            ) : (
                <ul className="vacation-list">
                    {allVacations.map(v => (
                        <li key={v.id} className="vacation-item">
              <span className="vacation-text">
                <strong>{v.username}</strong>: {formatDate(v.startDate)} – {formatDate(v.endDate)}{' '}
                  {v.usesOvertime && <em style={{ color: '#007BFF' }}>🌙 Überstundenfrei</em>}{' '}
                  {v.approved ? (
                      <span className="approved">{t('adminDashboard.approved')}</span>
                  ) : v.denied ? (
                      <span className="denied">{t('adminDashboard.denied')}</span>
                  ) : (
                      <span className="pending">{t('adminDashboard.pending')}</span>
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
        </section>
        </div>
    );
};

AdminVacationRequests.propTypes = {
    t: PropTypes.func.isRequired,
    allVacations: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.number,
        username: PropTypes.string,
        startDate: PropTypes.string,
        endDate: PropTypes.string,
        approved: PropTypes.bool,
        denied: PropTypes.bool,
        usesOvertime: PropTypes.bool
    })).isRequired,
    handleApproveVacation: PropTypes.func.isRequired,
    handleDenyVacation: PropTypes.func.isRequired
};

export default AdminVacationRequests;
