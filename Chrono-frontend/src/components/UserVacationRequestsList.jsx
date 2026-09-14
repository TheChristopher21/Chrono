import PropTypes from 'prop-types';

const resolveVacationStatus = (request, t) => {
    if (request?.approved) {
        return {
            label: t('adminDashboard.statusApproved', 'Genehmigt'),
            className: 'status-approved'
        };
    }
    if (request?.denied) {
        return {
            label: t('adminDashboard.statusDenied', 'Abgelehnt'),
            className: 'status-denied'
        };
    }
    return {
        label: t('adminDashboard.statusPending', 'Ausstehend'),
        className: 'status-pending'
    };
};

const getAdminNote = (request) => {
    return request?.adminNote || request?.adminComment || request?.comment || request?.note || '';
};

function UserVacationRequestsList({ vacationRequests, t }) {
    const sortedRequests = [...(vacationRequests || [])]
        .filter((request) => request?.startDate && request?.endDate)
        .sort((a, b) => `${b.startDate}`.localeCompare(`${a.startDate}`));

    return (
        <div className="user-vacation-requests-list" style={{ marginTop: '1rem' }}>
            <h4>{t('userDashboard.myVacationRequests', 'Meine Urlaubsanträge')}</h4>

            {sortedRequests.length === 0 ? (
                <p>{t('adminDashboard.noVacationRequests', 'Keine Urlaubsanträge gefunden.')}</p>
            ) : (
                <ul className="item-list vacation-request-list">
                    {sortedRequests.map((request) => {
                        const status = resolveVacationStatus(request, t);
                        const adminNote = getAdminNote(request);

                        return (
                            <li key={request.id} className={`list-item vacation-item ${status.className}`}>
                                <div className="item-info">
                                    <span>
                                        <strong>{request.startDate}</strong> - <strong>{request.endDate}</strong>
                                    </span>
                                    <span className={`status-badge ${status.className}`}>{status.label}</span>
                                    <span>
                                        <strong>{t('userDashboard.adminNote', 'Admin-Notiz')}:</strong>{' '}
                                        {adminNote || t('userDashboard.noAdminNote', 'Keine Notiz hinterlegt.')}
                                    </span>
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
}

UserVacationRequestsList.propTypes = {
    vacationRequests: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.number,
        startDate: PropTypes.string,
        endDate: PropTypes.string,
        approved: PropTypes.bool,
        denied: PropTypes.bool,
        adminNote: PropTypes.string,
        adminComment: PropTypes.string,
        comment: PropTypes.string,
        note: PropTypes.string,
    })),
    t: PropTypes.func.isRequired,
};

UserVacationRequestsList.defaultProps = {
    vacationRequests: [],
};

export default UserVacationRequestsList;
