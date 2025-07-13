import PropTypes from 'prop-types';
import '../styles/TrendChart.css';

function TrendChart({ data }) {
  const max = Math.max(...data.map(d => Math.max(d.workedMinutes, d.expectedMinutes || 0)), 1);
  return (
    <div className="trend-chart">
      {data.map(d => (
        <div key={d.date} className="trend-bar-wrapper">
          <div className="trend-bars">
            {typeof d.expectedMinutes === 'number' && (
              <div className="trend-bar expected-bar" style={{height: `${(d.expectedMinutes / max) * 100}%`}} />
            )}
            <div className="trend-bar actual-bar" style={{height: `${(d.workedMinutes / max) * 100}%`}} />
          </div>
          <span className="trend-label">{new Date(d.date).toLocaleDateString()}</span>
        </div>
      ))}
    </div>
  );
}

TrendChart.propTypes = {
  data: PropTypes.arrayOf(
    PropTypes.shape({
      date: PropTypes.string.isRequired,
      workedMinutes: PropTypes.number.isRequired,
      expectedMinutes: PropTypes.number
    })
  ).isRequired
};

export default TrendChart;
