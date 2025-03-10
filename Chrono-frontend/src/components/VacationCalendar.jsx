import React, { useState } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendar.css';

const VacationCalendarAdmin = ({ vacationRequests }) => {
    const [date, setDate] = useState(new Date());

    const tileContent = ({ date, view }) => {
        if (view === 'month') {
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
                const color = vacationsForDay[0].color || '#ccc';
                return (
                    <div style={{
                        backgroundColor: color,
                        borderRadius: '50%',
                        width: '16px',
                        height: '16px',
                        margin: '0 auto'
                    }} />
                );
            }
        }
        return null;
    };

    return (
        <div className="vacation-calendar">
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
            color: PropTypes.string
        })
    ).isRequired
};

export default VacationCalendarAdmin;
