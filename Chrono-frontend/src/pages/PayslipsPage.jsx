// src/pages/AdminPayslipsPage.jsx
import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

// Importiere die zentralen, einheitlichen Dashboard-Styles
import '../styles/HourlyDashboardScoped.css';
// Importiere die spezifischen Styles für diese Seite
import '../styles/AdminPayslipsPageScoped.css';

const ITEMS_PER_PAGE = 10;

const AdminPayslipsPage = () => {
  const { t } = useTranslation();
  const [payslips, setPayslips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ userId: '', start: '', end: '' });
  const [filter, setFilter] = useState({ name: '', start: '', end: '' });
  const [scheduleVisible, setScheduleVisible] = useState(false);

  // --- NEU: State für Paginierung und Sektions-Toggle ---
  const [pendingPage, setPendingPage] = useState(1);
  const [approvedPage, setApprovedPage] = useState(1);
  const [isPendingExpanded, setIsPendingExpanded] = useState(true);
  const [isApprovedExpanded, setIsApprovedExpanded] = useState(true);

  const fetchPending = () => api.get('/api/payslips/admin/pending').then(res => setPayslips(res.data || []));
  const fetchApproved = () => api.get('/api/payslips/admin/approved', { params: filter }).then(res => setApprovedSlips(res.data || []));

  useEffect(() => {
    fetchPending();
    fetchApproved();
    api.get('/api/admin/users').then(res => setUsers(res.data || []));
  }, [filter]);

  const approve = (id) => api.post(`/api/payslips/approve/${id}`).then(fetchPending);
  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    api.post('/api/payslips/approve-all', { params: { comment } }).then(fetchPending);
  };
  const confirmScheduleAll = (day) => {
    api.post('/api/payslips/schedule-all', null, { params: { day } });
    setScheduleVisible(false);
  };

  const exportCsv = () => {
    api.get('/api/payslips/admin/export', { responseType: 'blob' }).then(res => {
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'payslips.csv');
      document.body.appendChild(link);
      link.click();
      link.remove();
    });
  };

  const printPdf = (id) => {
    api.get(`/api/payslips/admin/pdf/${id}`, { responseType: 'blob', params: { lang: 'de' } })
        .then(res => {
          const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
          window.open(url, '_blank');
        })
        .catch(() => alert(t('payslips.printError')));
  };

  const createPayslip = () => {
    if (!form.userId || !form.start || !form.end) return;
    api.post('/api/payslips/generate', null, { params: form })
        .then(() => {
          setForm({ userId: '', start: '', end: '' });
          fetchPending();
        });
  };

  // --- Paginierungs-Logik ---
  const paginatedPending = payslips.slice((pendingPage - 1) * ITEMS_PER_PAGE, pendingPage * ITEMS_PER_PAGE);
  const totalPendingPages = Math.ceil(payslips.length / ITEMS_PER_PAGE);

  const paginatedApproved = approvedSlips.slice((approvedPage - 1) * ITEMS_PER_PAGE, approvedPage * ITEMS_PER_PAGE);
  const totalApprovedPages = Math.ceil(approvedSlips.length / ITEMS_PER_PAGE);

  const Pagination = ({ currentPage, totalPages, onPageChange }) => {
    if (totalPages <= 1) return null;
    return (
        <div className="pagination-controls">
          <button onClick={() => onPageChange(currentPage - 1)} disabled={currentPage === 1}>
            &laquo; {t('prev', 'Zurück')}
          </button>
          <span>{t('page', 'Seite')} {currentPage} / {totalPages}</span>
          <button onClick={() => onPageChange(currentPage + 1)} disabled={currentPage === totalPages}>
            {t('next', 'Weiter')} &raquo;
          </button>
        </div>
    );
  };


  return (
      <>
        <Navbar />
        <div className="admin-payslips-page scoped-dashboard">
          <header className="dashboard-header">
            <h1>{t('navbar.payslips')}</h1>
          </header>

          <section className="content-section">
            <h3 className="section-title">{t('payslips.generateManual', 'Manuelle Abrechnung erstellen')}</h3>
            <form className="generate-form" onSubmit={e => { e.preventDefault(); createPayslip(); }}>
              <select value={form.userId} onChange={e => setForm({ ...form, userId: e.target.value })} required>
                <option value="">{t('payslips.selectUser', 'Benutzer wählen...')}</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
              </select>
              <input type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} required />
              <input type="date" value={form.end} onChange={e => setForm({ ...form, end: e.target.value })} required />
              <button type="submit" className="button-primary">{t('payslips.generate', 'Erstellen')}</button>
            </form>
          </section>

          <section className="content-section">
            <div className="section-header" onClick={() => setIsPendingExpanded(!isPendingExpanded)}>
              <h3 className="section-title">{t('payslips.pendingTitle')}</h3>
              <span className="toggle-icon">{isPendingExpanded ? '▲' : '▼'}</span>
            </div>
            {isPendingExpanded && (
                <>
                  <div className="controls-bar">
                    <button className="button-primary" onClick={approveAll}>{t('payslips.approveAll')}</button>
                    <button className="button-secondary" onClick={exportCsv}>{t('payslips.exportCsv')}</button>
                    <button className="button-secondary" onClick={() => setScheduleVisible(true)}>{t('payslips.scheduleAll')}</button>
                  </div>
                  <div className="item-list-container">
                    <table className="payslip-table">
                      {/* ... thead ... */}
                      <thead>
                      <tr>
                        <th>{t('payslips.user')}</th>
                        <th>{t('payslips.period')}</th>
                        <th>{t('payslips.gross')}</th>
                        <th>{t('payslips.net')}</th>
                        <th className="actions-col">{t('userManagement.table.actions')}</th>
                      </tr>
                      </thead>
                      <tbody>
                      {paginatedPending.map(ps => (
                          <tr key={ps.id}>
                            <td data-label={t('payslips.user')}>{ps.firstName} {ps.lastName}</td>
                            <td data-label={t('payslips.period')}>{ps.periodStart} - {ps.periodEnd}</td>
                            <td data-label={t('payslips.gross')}>{ps.grossSalary?.toFixed(2)} CHF</td>
                            <td data-label={t('payslips.net')}>{ps.netSalary?.toFixed(2)} CHF</td>
                            <td data-label={t('userManagement.table.actions')} className="actions-col">
                              <button onClick={() => approve(ps.id)} className="button-success">{t('payslips.approve')}</button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination currentPage={pendingPage} totalPages={totalPendingPages} onPageChange={setPendingPage} />
                </>
            )}
          </section>

          <section className="content-section">
            <div className="section-header" onClick={() => setIsApprovedExpanded(!isApprovedExpanded)}>
              <h3 className="section-title">{t('payslips.approvedTitle')}</h3>
              <span className="toggle-icon">{isApprovedExpanded ? '▲' : '▼'}</span>
            </div>
            {isApprovedExpanded && (
                <>
                  <div className="controls-bar">
                    <input className="filter-input" placeholder={t('payslips.filterName')} value={filter.name} onChange={e => setFilter({ ...filter, name: e.target.value })} />
                    <input className="filter-input" type="date" value={filter.start} onChange={e => setFilter({ ...filter, start: e.target.value })} />
                    <input className="filter-input" type="date" value={filter.end} onChange={e => setFilter({ ...filter, end: e.target.value })} />
                  </div>
                  <div className="item-list-container">
                    <table className="payslip-table">
                      {/* ... thead ... */}
                      <thead>
                      <tr>
                        <th>{t('payslips.user')}</th>
                        <th>{t('payslips.period')}</th>
                        <th>{t('payslips.gross')}</th>
                        <th>{t('payslips.net')}</th>
                        <th className="actions-col">{t('userManagement.table.actions')}</th>
                      </tr>
                      </thead>
                      <tbody>
                      {paginatedApproved.map(ps => (
                          <tr key={ps.id}>
                            <td data-label={t('payslips.user')}>{ps.firstName} {ps.lastName}</td>
                            <td data-label={t('payslips.period')}>{ps.periodStart} - {ps.periodEnd}</td>
                            <td data-label={t('payslips.gross')}>{ps.grossSalary?.toFixed(2)} CHF</td>
                            <td data-label={t('payslips.net')}>{ps.netSalary?.toFixed(2)} CHF</td>
                            <td data-label={t('userManagement.table.actions')} className="actions-col">
                              <button onClick={() => printPdf(ps.id)} className="button-primary">{t('payslips.print')}</button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination currentPage={approvedPage} totalPages={totalApprovedPages} onPageChange={setApprovedPage} />
                </>
            )}
          </section>

          <ScheduleAllModal visible={scheduleVisible} onConfirm={confirmScheduleAll} onClose={() => setScheduleVisible(false)} />
        </div>
      </>
  );
};

export default AdminPayslipsPage;