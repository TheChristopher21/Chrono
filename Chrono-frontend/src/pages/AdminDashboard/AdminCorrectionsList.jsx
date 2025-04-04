// AdminCorrectionsList.jsx
import 'react';
import { formatDate } from './adminDashboardUtils';
import PropTypes from "prop-types";

const AdminCorrectionsList = ({
                                  t,
                                  allCorrections,
                                  handleApproveCorrection,
                                  handleDenyCorrection
                              }) => {
    return (
        <section className="correction-section">
            <h3>{t('adminDashboard.correctionRequestsTitle')}</h3>
            {allCorrections.length === 0 ? (
                <p>{t('adminDashboard.noEntriesThisWeek')}</p>
            ) : (
                <ul className="correction-list">
                    {allCorrections.map(corr => {
                        const dateStrRaw = corr.desiredStartTime || corr.desiredStart;
                        let correctionDate = "-";
                        if (dateStrRaw) {
                            const parsed = new Date(dateStrRaw);
                            if (!isNaN(parsed.getTime())) {
                                correctionDate = formatDate(parsed);
                            }
                        }
                        return (
                            <li key={corr.id} className="correction-item">
                                <h4>Korrektur vom {correctionDate}</h4>
                                <div className="correction-info">
                                    <p><strong>User:</strong> {corr.username}</p>
                                    <p><strong>Work Start:</strong> {corr.workStart || "-"}</p>
                                    <p><strong>Break Start:</strong> {corr.breakStart || "-"}</p>
                                    <p><strong>Break End:</strong> {corr.breakEnd || "-"}</p>
                                    <p><strong>Work End:</strong> {corr.workEnd || "-"}</p>
                                    <p><strong>Reason:</strong> {corr.reason}</p>
                                    <p>
                                        <strong>Status:</strong>{" "}
                                        {corr.approved
                                            ? t("adminDashboard.approved")
                                            : corr.denied
                                                ? t("adminDashboard.denied")
                                                : t("adminDashboard.pending")}
                                    </p>
                                </div>
                                <div className="correction-buttons">
                                    {!corr.approved && !corr.denied && (
                                        <>
                                            <button onClick={() => handleApproveCorrection(corr.id)}>
                                                {t('adminDashboard.acceptButton')}
                                            </button>
                                            <button onClick={() => handleDenyCorrection(corr.id)}>
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
        </section>
    );
};
AdminCorrectionsList.propTypes = {
    t: PropTypes.func.isRequired,
    allCorrections: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.number,
        username: PropTypes.string,
        desiredStartTime: PropTypes.string,
        desiredStart: PropTypes.string,
        workStart: PropTypes.string,
        breakStart: PropTypes.string,
        breakEnd: PropTypes.string,
        workEnd: PropTypes.string,
        reason: PropTypes.string,
        approved: PropTypes.bool,
        denied: PropTypes.bool
        // ... weitere Felder, falls nötig
    })).isRequired,
    handleApproveCorrection: PropTypes.func.isRequired,
    handleDenyCorrection: PropTypes.func.isRequired
};

export default AdminCorrectionsList;
