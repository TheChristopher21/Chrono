import React from 'react';
import PropTypes from 'prop-types';
import VacationCalendar from '../../components/VacationCalendar';
import '../../styles/PercentageDashboardScoped.css';

const PercentageVacationSection = ({
                                       t,
                                       userProfile,
                                       vacationRequests,
                                       onRefreshVacations
                                   }) => {
    return (

        <section className="vacation-section">
            <h3>{t("vacationTitle")}</h3>


            {/* Kalender mit integriertem Antrag */}
            <div className="calendar-section">
                <VacationCalendar
                    vacationRequests={vacationRequests}
                    userProfile={userProfile}
                    onRefreshVacations={onRefreshVacations}
                />
            </div>
        </section>
    );
};

PercentageVacationSection.propTypes = {
    t: PropTypes.func.isRequired,
    userProfile: PropTypes.object.isRequired,
    vacationRequests: PropTypes.array.isRequired,
    onRefreshVacations: PropTypes.func
};

export default PercentageVacationSection;
