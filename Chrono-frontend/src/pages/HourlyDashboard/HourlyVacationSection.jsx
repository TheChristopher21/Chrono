// HourlyVacationSection.jsx
import React from 'react';
import PropTypes from 'prop-types';
import { formatDate } from './hourDashUtils';
import VacationCalendar from '../../components/VacationCalendar';

const HourlyVacationSection = ({
                                   t,
                                   userProfile,
                                   vacationRequests,
                                   vacationForm,
                                   setVacationForm,
                                   handleVacationSubmit
                               }) => {
    return (
        <section className="vacation-section">
            <h3>{t("vacationTitle")}</h3>
            {/* Urlaubsformular */}
            <form onSubmit={handleVacationSubmit} className="form-vacation">
                <div className="form-group">
                    <label>{t("startDate")}:</label>
                    <input
                        type="date"
                        name="startDate"
                        value={vacationForm.startDate}
                        onChange={(e) => setVacationForm({ ...vacationForm, startDate: e.target.value })}
                        required
                    />
                </div>
                <div className="form-group">
                    <label>{t("endDate")}:</label>
                    <input
                        type="date"
                        name="endDate"
                        value={vacationForm.endDate}
                        onChange={(e) => setVacationForm({ ...vacationForm, endDate: e.target.value })}
                        required
                    />
                </div>
                <button type="submit">{t("vacationSubmitButton")}</button>
            </form>

            {/* Urlaubs-Historie */}
            <div className="vacation-history">
                <h4>{t("myVacations")}</h4>
                {vacationRequests.length === 0 ? (
                    <p>{t("noVacations")}</p>
                ) : (
                    <ul>
                        {vacationRequests.map((v) => (
                            <li key={v.id}>
                                {formatDate(v.startDate)} {t("to")} {formatDate(v.endDate)}{' '}
                                {v.approved ? (
                                    <span className="approved">{t("approved")}</span>
                                ) : v.denied ? (
                                    <span className="denied">{t("denied")}</span>
                                ) : (
                                    <span className="pending">{t("pending")}</span>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            {/* Kalender */}
            <div className="calendar-section">
                <h4>{t("vacationCalendarTitle")}</h4>
                <VacationCalendar
                    vacationRequests={vacationRequests.filter(v => v.approved)}
                />
            </div>
        </section>
    );
};

HourlyVacationSection.propTypes = {
    t: PropTypes.func.isRequired,
    userProfile: PropTypes.object,
    vacationRequests: PropTypes.array.isRequired,
    vacationForm: PropTypes.shape({
        startDate: PropTypes.string,
        endDate: PropTypes.string
    }).isRequired,
    setVacationForm: PropTypes.func.isRequired,
    handleVacationSubmit: PropTypes.func.isRequired
};

export default HourlyVacationSection;
