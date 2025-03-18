import  { useState } from 'react';
import PropTypes from 'prop-types';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import '../styles/VacationCalendarAdmin.css';

function getContrastYIQ(hexcolor) {
    hexcolor = hexcolor.replace("#", "");
    const r = parseInt(hexcolor.substr(0, 2), 16);
    const g = parseInt(hexcolor.substr(2, 2), 16);
    const b = parseInt(hexcolor.substr(4, 2), 16);
    const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
    return (yiq >= 128) ? '#000' : '#fff';
}

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
                return (
                    <div className="vacation-markers">
                        {vacationsForDay.map((vac, index) => {
                            const bgColor = vac.color || '#ccc';
                            const textColor = getContrastYIQ(bgColor);
                            return (
                                <div
                                    key={index}
                                    className="vacation-marker"
                                    style={{ backgroundColor: bgColor, color: textColor }}
                                    title={vac.username}
                                >
                                    {vac.username}
                                </div>
                            );
                        })}
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
