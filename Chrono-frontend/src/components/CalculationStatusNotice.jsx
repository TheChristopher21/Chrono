import PropTypes from 'prop-types';
import './CalculationStatusNotice.css';

export const CALCULATION_STATUS = Object.freeze({
    IDLE: 'idle',
    LOADING: 'loading',
    READY: 'ready',
    ERROR: 'error',
});

export function formatCalculatedMinutes(value, status, formatter) {
    if (Number.isFinite(value)) return formatter(value);
    if (status === CALCULATION_STATUS.ERROR) return 'Nicht verfuegbar';
    return '...';
}

const CalculationStatusNotice = ({ status }) => {
    if (status === CALCULATION_STATUS.READY || status === CALCULATION_STATUS.IDLE) {
        return null;
    }

    const isError = status === CALCULATION_STATUS.ERROR;
    return (
        <div
            className={`calculation-status-notice ${isError ? 'is-error' : 'is-loading'}`}
            role={isError ? 'alert' : 'status'}
        >
            {isError
                ? 'Die Zeitberechnung ist momentan nicht verfuegbar. Es werden keine Nullwerte als Ergebnis angenommen.'
                : 'Zeitberechnung wird geladen ...'}
        </div>
    );
};

CalculationStatusNotice.propTypes = {
    status: PropTypes.oneOf(Object.values(CALCULATION_STATUS)).isRequired,
};

export default CalculationStatusNotice;
