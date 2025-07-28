// PayslipsPage.jsx
import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

// Styles
import '../styles/AdminPayslipsPageScoped.css'; // Einheitliche Basis-Styles :contentReference[oaicite:4]{index=4}

const PayslipsPage = () => {
  const { t } = useTranslation();
  const [payslips, setPayslips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ userId: '', start: '', end: '' });
  const [filter, setFilter] = useState({ name: '', start: '', end: '' });
  const [logoFile, setLogoFile] = useState(null);
  const [scheduleVisible, setScheduleVisible] = useState(false);
  const [isPendingExpanded, setIsPendingExpanded] = useState(true);
  const [isApprovedExpanded, setIsApprovedExpanded] = useState(false);

  useEffect(() => {
    api.get('/api/payslips/admin/pending').then(res => setPayslips(res.data || []));
    api.get('/api/payslips/admin/approved', { params: filter }).then(res => setApprovedSlips(res.data || []));
    api.get('/api/admin/users').then(res => setUsers(res.data || []));
  }, [filter]);

  const approve = id =>
      api.post(`/api/payslips/approve/${id}`)
          .then(() => api.get('/api/payslips/admin/pending').then(res => setPayslips(res.data || [])));

  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    if (comment !== null) {
      api.post('/api/payslips/approve-all', null, { params: { comment } })
          .then(() => api.get('/api/payslips/admin/pending').then(res => setPayslips(res.data || [])));
    }
  };

  const confirmScheduleAll = day => {
    api.post('/api/payslips/schedule-all', null, { params: { day } });
    setScheduleVisible(false);
  };

  const uploadLogo = () => {
    if (!logoFile) return;
    const formData = new FormData();
    formData.append('file', logoFile);
    api.put('/api/admin/company/logo', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(() => alert(t('payslips.logoSaved')))
        .catch(() => alert(t('payslips.logoSaveError')));
  };

  return (
      <>
        <Navbar />
        <div className="admin-payslips-page scoped-dashboard">
          <header className="dashboard-header">
            <h1>{t('navbar.payslips')}</h1>
          </header>

          <div className="top-sections-grid">
            <section className="content-section">
              <h3 className="section-title">{t('payslips.generateManual')}</h3>
              <form
                  className="generate-form"
                  onSubmit={e => {
                    e.preventDefault();
                    // TODO: api.post zum Erstellen der manuellen Abrechnung
                  }}
              >
                <select
                    value={form.userId}
                    onChange={e => setForm({ ...form, userId: e.target.value })}
                    required
                >
                  <option value="">{t('payslips.selectUser')}</option>
                  {users.map(u => (
                      <option key={u.id} value={u.id}>
                        {u.username}
                      </option>
                  ))}
                </select>
                <input
                    type="date"
                    value={form.start}
                    onChange={e => setForm({ ...form, start: e.target.value })}
                    required
                />
                <input
                    type="date"
                    value={form.end}
                    onChange={e => setForm({ ...form, end: e.target.value })}
                    required
                />
                <button type="submit" className="button-primary">
                  {t('payslips.generate')}
                </button>
              </form>
            </section>

            <section className="content-section">
              <h3 className="section-title">{t('payslips.logoUploadTitle')}</h3>
              <div className="logo-upload-form">
                <label htmlFor="logo-upload-input" className="custom-file-upload">
                  {logoFile ? logoFile.name : t('payslips.selectFile')}
                </label>
                <input
                    id="logo-upload-input"
                    type="file"
                    accept="image/*"
                    onChange={e => setLogoFile(e.target.files[0])}
                />
                <button type="button" onClick={uploadLogo} className="button-secondary">
                  {t('payslips.saveLogo')}
                </button>
              </div>
            </section>
          </div>

          {/* Pending Section */}
          <section className="content-section">
            <div
                className="section-header"
                role="button"
                tabIndex={0}
                aria-expanded={isPendingExpanded}
                onClick={() => setIsPendingExpanded(!isPendingExpanded)}
                onKeyPress={e => e.key === 'Enter' && setIsPendingExpanded(!isPendingExpanded)}
            >
              <h3 className="section-title">{t('payslips.pendingTitle')}</h3>
              <span className="toggle-icon">{isPendingExpanded ? '▲' : '▼'}</span>
            </div>
            {isPendingExpanded && (
                <>
                  <div className="controls-bar">
                    <button type="button" className="button-primary" onClick={approveAll}>
                      {t('payslips.approveAll')}
                    </button>
                  </div>
                  <div className="table-wrapper">
                    <table className="payslip-table">
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
                      {payslips.map(ps => (
                          <tr key={ps.id}>
                            <td>{ps.firstName} {ps.lastName}</td>
                            <td>{ps.periodStart} – {ps.periodEnd}</td>
                            <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                            <td>{ps.netSalary?.toFixed(2)} CHF</td>
                            <td className="actions-col">
                              <button
                                  type="button"
                                  className="button-success"
                                  onClick={() => approve(ps.id)}
                              >
                                {t('payslips.approve')}
                              </button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                </>
            )}
          </section>

          {/* Approved Section */}
          <section className="content-section">
            <div
                className="section-header"
                role="button"
                tabIndex={0}
                aria-expanded={isApprovedExpanded}
                onClick={() => setIsApprovedExpanded(!isApprovedExpanded)}
                onKeyPress={e => e.key === 'Enter' && setIsApprovedExpanded(!isApprovedExpanded)}
            >
              <h3 className="section-title">{t('payslips.approvedTitle')}</h3>
              <span className="toggle-icon">{isApprovedExpanded ? '▲' : '▼'}</span>
            </div>
            {isApprovedExpanded && (
                <>
                  <div className="controls-bar">
                    <input
                        type="text"
                        className="filter-input"
                        placeholder={t('payslips.filterName')}
                        value={filter.name}
                        onChange={e => setFilter({ ...filter, name: e.target.value })}
                    />
                    <input
                        type="date"
                        className="filter-input"
                        value={filter.start}
                        onChange={e => setFilter({ ...filter, start: e.target.value })}
                    />
                    <input
                        type="date"
                        className="filter-input"
                        value={filter.end}
                        onChange={e => setFilter({ ...filter, end: e.target.value })}
                    />
                  </div>
                  <div className="table-wrapper">
                    <table className="payslip-table">
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
                      {approvedSlips.map(ps => (
                          <tr key={ps.id}>
                            <td>{ps.firstName} {ps.lastName}</td>
                            <td>{ps.periodStart} – {ps.periodEnd}</td>
                            <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                            <td>{ps.netSalary?.toFixed(2)} CHF</td>
                            <td className="actions-col">
                              <button type="button" className="button-primary">
                                {t('payslips.print')}
                              </button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                </>
            )}
          </section>

          <ScheduleAllModal
              visible={scheduleVisible}
              onConfirm={confirmScheduleAll}
              onClose={() => setScheduleVisible(false)}
          />
        </div>
      </>
  );
};

export default PayslipsPage;
