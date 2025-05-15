import React from 'react';
import PropTypes from 'prop-types';
import VacationCalendar from '../../components/VacationCalendar';

const HourlyVacationSection = ({
                                   t,
                                   userProfile,
                                   vacationRequests,
                                   onRefreshVacations
                               }) => {
    return (
        <section className="vacation-section">
            <h3>{t('vacationTitle')}</h3>
            <p>
                <strong>{t('vacationCalendarTitle')}:</strong>{" "}
                {userProfile?.annualVacationDays ?? 0} {t('daysLabel')}
            </p>

            <VacationCalendar
                vacationRequests={vacationRequests}
                userProfile={userProfile}
                onRefreshVacations={onRefreshVacations}
            />
        </section>
    );
};

HourlyVacationSection.propTypes = {
    t: PropTypes.func.isRequired,
    userProfile: PropTypes.object,
    vacationRequests: PropTypes.array.isRequired,
    onRefreshVacations: PropTypes.func.isRequired
};

export default HourlyVacationSection;
