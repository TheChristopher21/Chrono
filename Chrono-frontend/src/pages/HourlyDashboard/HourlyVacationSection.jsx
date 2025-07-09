import React from 'react';
import PropTypes from 'prop-types';
import VacationCalendar from '../../components/VacationCalendar'; // Stellt die Kalenderlogik bereit

const HourlyVacationSection = ({
                                   t,
                                   userProfile,
                                   vacationRequests,
                                   onRefreshVacations
                               }) => {
    return (
        <section className="vacation-section content-section"> {/* .content-section für einheitliches Styling */}
            <h3 className="section-title">{t('vacationTitle', 'Urlaub & Abwesenheiten')}</h3> {/* .section-title für einheitliches Styling */}
            <div className="vacation-info-header">
                <p>
                    <strong>{t('vacationCalendarTitle', 'Jahresurlaub')}:</strong>{" "}
                    {userProfile?.annualVacationDays ?? 0} {t('daysLabel', 'Tage')}
                </p>
                {/* Weitere Infos wie verbleibende Tage könnten hier aus userProfile oder berechnet angezeigt werden, falls im UserDashboard Logik dafür existiert */}
            </div>

            <VacationCalendar
                vacationRequests={vacationRequests}
                userProfile={userProfile}
                onRefreshVacations={onRefreshVacations} // Wichtig, damit der Kalender sich aktualisieren kann
                // `cssScope` könnte hier übergeben werden, wenn VacationCalendar es unterstützt,
                // um spezifische Kalender-Styles innerhalb des HourlyDashboards zu ermöglichen, falls nötig.
                // z.B. cssScope="hourly-calendar"
            />
        </section>
    );
};

HourlyVacationSection.propTypes = {
    t: PropTypes.func.isRequired,
    userProfile: PropTypes.shape({
        username: PropTypes.string,
        annualVacationDays: PropTypes.number,
        // Weitere relevante Felder aus dem User-Profil
    }),
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            id: PropTypes.number.isRequired,
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            // Weitere Felder des VacationRequest-Objekts
        })
    ).isRequired,
    onRefreshVacations: PropTypes.func.isRequired
};

export default HourlyVacationSection;