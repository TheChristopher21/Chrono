import PropTypes from 'prop-types';
import '../styles/TrendChart.css';

function TrendChart({ data }) {
  const max = Math.max(...data.map(d => d.workedMinutes), 1);
  return (
    <div className="trend-chart">
      {data.map(d => (
        <div key={d.date} className="trend-bar-wrapper">
          <div className="trend-bar" style={{height: `${(d.workedMinutes / max) * 100}%`}} />
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
      workedMinutes: PropTypes.number.isRequired
    })
  ).isRequired
};

export default TrendChart;
