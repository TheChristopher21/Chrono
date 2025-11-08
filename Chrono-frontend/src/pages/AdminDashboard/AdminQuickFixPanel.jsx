import PropTypes from 'prop-types';

const AdminQuickFixPanel = ({ t, items, onSelect }) => {
    const queue = Array.isArray(items) ? items : [];
    const hasItems = queue.length > 0;

    return (
        <section className="inbox-detail-card quick-fix-card" aria-live="polite">
            <div className="quick-fix-header">
                <h4>{t('adminDashboard.quickFix.title', 'Smart Quick Fix')}</h4>
                <p>{t('adminDashboard.quickFix.subtitle', 'Die wichtigsten offenen Probleme auf einen Blick')}</p>
            </div>
            {!hasItems ? (
                <p className="quick-fix-empty">
                    {t('adminDashboard.quickFix.empty', 'Aktuell keine dringenden Probleme – großartig!')}
                </p>
            ) : (
                <ul className="quick-fix-list">
                    {queue.map((item) => (
                        <li key={`${item.username}-${item.filterKey}`}>
                            <button
                                type="button"
                                className="quick-fix-item"
                                onClick={() => onSelect?.(item)}
                            >
                                <span className="quick-fix-icon" aria-hidden="true">{item.icon}</span>
                                <span className="quick-fix-label">
                                    <strong>{item.username}</strong>
                                    <span>
                                        {t(
                                            'adminDashboard.quickFix.countLabel',
                                            '{count}× {label}',
                                            { count: item.count, label: item.label }
                                        )}
                                    </span>
                                </span>
                                <span className="quick-fix-cta">{t('adminDashboard.quickFix.action', 'Springen')}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
};

AdminQuickFixPanel.propTypes = {
    t: PropTypes.func.isRequired,
    items: PropTypes.arrayOf(PropTypes.shape({
        username: PropTypes.string.isRequired,
        filterKey: PropTypes.string,
        icon: PropTypes.string,
        label: PropTypes.string,
        count: PropTypes.number,
        problemType: PropTypes.string,
    })),
    onSelect: PropTypes.func,
};

AdminQuickFixPanel.defaultProps = {
    items: [],
    onSelect: undefined,
};

export default AdminQuickFixPanel;
