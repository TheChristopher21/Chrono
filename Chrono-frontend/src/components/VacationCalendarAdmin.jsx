import React, { useState } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdmin.css';

const VacationCalendarAdmin = ({ vacationRequests }) => {
    const [date, setDate] = useState(new Date());

    const tileContent = ({ date, view }) => {
        if (view === 'month') {
            // Finde alle Urlaubseinträge für diesen Tag
            const vacationsForDay = vacationRequests.filter(vac => {
                const start = new Date(vac.startDate);
                const end = new Date(vac.endDate);
                start.setHours(0, 0, 0, 0);
                end.setHours(0, 0, 0, 0);
                const current = new Date(date);
                current.setHours(0, 0, 0, 0);
                return current >= start && current <= end;
            });
            if (vacationsForDay.length > 0) {
                return (
                    <div className="vacation-markers">
                        {vacationsForDay.map((vac, index) => (
                            <div
                                key={index}
                                className="vacation-marker"
                                style={{ backgroundColor: vac.color || '#ccc' }}
                                title={vac.username}
                            >
                                {vac.username}
                            </div>
                        ))}
                    </div>
                );
            }
        }
        return null;
    };

    return (
        <div className="vacation-calendar-admin">
            <Calendar
                onChange={setDate}
                value={date}
                tileContent={tileContent}
            />
        </div>
    );
};

VacationCalendarAdmin.propTypes = {
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
            color: PropTypes.string,
            username: PropTypes.string.isRequired
        })
    ).isRequired
};

export default VacationCalendarAdmin;
