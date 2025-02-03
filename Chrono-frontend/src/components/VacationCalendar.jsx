// src/components/VacationCalendar.jsx
import React, { useState } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendar.css';

const VacationCalendar = ({ vacationRequests }) => {
    const [date, setDate] = useState(new Date());

    const tileClassName = ({ date, view }) => {
        if (view === 'month') {
            const isVacationDay = vacationRequests.some(vac => {
                const start = new Date(vac.startDate);
                const end = new Date(vac.endDate);
                start.setHours(0, 0, 0, 0);
                end.setHours(0, 0, 0, 0);
                const current = new Date(date);
                current.setHours(0, 0, 0, 0);
                return current >= start && current <= end;
            });
            return isVacationDay ? 'vacation-day' : null;
        }
        return null;
    };

    return (
        <div className="vacation-calendar">
            <Calendar
                onChange={setDate}
                value={date}
                tileClassName={tileClassName}
            />
        </div>
    );
};

VacationCalendar.propTypes = {
    vacationRequests: PropTypes.arrayOf(
        PropTypes.shape({
            startDate: PropTypes.string.isRequired,
            endDate: PropTypes.string.isRequired,
            approved: PropTypes.bool,
            denied: PropTypes.bool,
        })
    ).isRequired,
};

export default VacationCalendar;
