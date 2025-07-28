// src/pages/PayslipsPage.jsx
import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext'; // Wichtig für die Rollenprüfung
import ScheduleAllModal from '../components/ScheduleAllModal';

// Styles
import '../styles/AdminPayslipsPageScoped.css';

const PayslipsPage = () => {
  const { t } = useTranslation();
  const { currentUser } = useAuth();
  const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN');

  // States für beide Ansichten
  const [pendingSlips, setPendingSlips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ userId: '', start: '', end: '' });
  const [filter, setFilter] = useState({ name: '', start: '', end: '' });
  const [logoFile, setLogoFile] = useState(null);
  const [scheduleVisible, setScheduleVisible] = useState(false);
  const [isPendingExpanded, setIsPendingExpanded] = useState(true);
  const [isApprovedExpanded, setIsApprovedExpanded] = useState(false);

  useEffect(() => {
    if (!currentUser) return;

    if (isAdmin) {
      // Admin: Lädt alle relevanten Daten
      api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || []));
      api.get('/api/payslips/admin/approved', { params: filter }).then(res => setApprovedSlips(res.data || []));
      api.get('/api/admin/users').then(res => setUsers(res.data || []));
    } else {
      // User: Lädt nur eigene, genehmigte Abrechnungen
      api.get('/api/payslips/my', { params: filter }).then(res => setApprovedSlips(res.data || []));
    }
  }, [currentUser, filter, isAdmin]);

  // Admin-Funktionen
  const approve = id => {
    api.post(`/api/payslips/approve/${id}`).then(() => {
      api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || []));
    });
  };

  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    if (comment !== null) {
      api.post('/api/payslips/approve-all', null, { params: { comment } })
          .then(() => api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || [])));
    }
  };

  const uploadLogo = () => {
    if (!logoFile) return;
    const formData = new FormData();
    formData.append('file', logoFile);
    api.put('/api/admin/company/logo', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(() => alert(t('payslips.logoSaved')))
        .catch(() => alert(t('payslips.logoSaveError')));
  };

  // Gemeinsame Funktion
  const printPdf = (id) => {
    api.get(`/api/payslips/pdf/${id}`, { responseType: 'blob', params: { lang: 'de' } })
        .then(res => {
          const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
          window.open(url, '_blank')?.print();
        })
        .catch(() => alert(t('payslips.printError')));
  };

  // Rendert die Admin-spezifischen Sektionen
  const renderAdminSections = () => (
      <>
        <div className="top-sections-grid">
          {/* Manuelle Erstellung */}
          <section className="content-section">
            <h3 className="section-title">{t('payslips.generateManual')}</h3>
            <form className="generate-form" onSubmit={e => e.preventDefault()}>
              <select value={form.userId} onChange={e => setForm({ ...form, userId: e.target.value })} required>
                <option value="">{t('payslips.selectUser')}</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
              </select>
              <input type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} required />
              <input type="date" value={form.end} onChange={e => setForm({ ...form, end: e.target.value })} required />
              <button type="submit" className="button-primary">{t('payslips.generate')}</button>
            </form>
          </section>
          {/* Logo Upload */}
          <section className="content-section">
            <h3 className="section-title">{t('payslips.logoUploadTitle')}</h3>
            <div className="logo-upload-form">
              <label htmlFor="logo-upload-input" className="custom-file-upload">
                {logoFile ? logoFile.name : t('payslips.selectFile')}
              </label>
              <input id="logo-upload-input" type="file" accept="image/*" onChange={e => setLogoFile(e.target.files[0])} />
              <button type="button" onClick={uploadLogo} className="button-secondary">{t('payslips.saveLogo')}</button>
            </div>
          </section>
        </div>

        {/* Offene Abrechnungen (Pending) */}
        <section className="content-section">
          <div className="section-header" role="button" onClick={() => setIsPendingExpanded(!isPendingExpanded)}>
            <h3 className="section-title">{t('payslips.pendingTitle')}</h3>
            <span className="toggle-icon">{isPendingExpanded ? '▲' : '▼'}</span>
          </div>
          {isPendingExpanded && (
              <>
                <div className="controls-bar"><button type="button" className="button-primary" onClick={approveAll}>{t('payslips.approveAll')}</button></div>
                <div className="table-wrapper">
                  <table className="payslip-table">
                    {/* Tabellenkopf für Admins */}
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
                    {pendingSlips.map(ps => (
                        <tr key={ps.id}>
                          <td>{ps.firstName} {ps.lastName}</td>
                          <td>{ps.periodStart} – {ps.periodEnd}</td>
                          <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                          <td>{ps.netSalary?.toFixed(2)} CHF</td>
                          <td className="actions-col">
                            <button type="button" className="button-success" onClick={() => approve(ps.id)}>{t('payslips.approve')}</button>
                          </td>
                        </tr>
                    ))}
                    </tbody>
                  </table>
                </div>
              </>
          )}
        </section>
      </>
  );

  return (
      <>
        <Navbar />
        <div className="admin-payslips-page scoped-dashboard">
          <header className="dashboard-header">
            <h1>{isAdmin ? t('navbar.payslips') : t('payslips.myPayslips', 'Meine Lohnabrechnungen')}</h1>
          </header>

          {isAdmin && renderAdminSections()}

          {/* Genehmigte Abrechnungen (für beide Rollen sichtbar, aber mit unterschiedlichen Daten) */}
          <section className="content-section">
            <div className="section-header" role="button" onClick={() => setIsApprovedExpanded(!isApprovedExpanded)}>
              <h3 className="section-title">{t('payslips.approvedTitle')}</h3>
              <span className="toggle-icon">{isApprovedExpanded ? '▲' : '▼'}</span>
            </div>
            {isApprovedExpanded && (
                <>
                  <div className="controls-bar">
                    {isAdmin && <input type="text" className="filter-input" placeholder={t('payslips.filterName')} value={filter.name} onChange={e => setFilter({ ...filter, name: e.target.value })} />}
                    <input type="date" className="filter-input" value={filter.start} onChange={e => setFilter({ ...filter, start: e.target.value })} />
                    <input type="date" className="filter-input" value={filter.end} onChange={e => setFilter({ ...filter, end: e.target.value })} />
                  </div>
                  <div className="table-wrapper">
                    <table className="payslip-table">
                      <thead>
                      <tr>
                        {isAdmin && <th>{t('payslips.user')}</th>}
                        <th>{t('payslips.period')}</th>
                        <th>{t('payslips.gross')}</th>
                        <th>{t('payslips.net')}</th>
                        <th className="actions-col">{t('userManagement.table.actions')}</th>
                      </tr>
                      </thead>
                      <tbody>
                      {approvedSlips.map(ps => (
                          <tr key={ps.id}>
                            {isAdmin && <td>{ps.firstName} {ps.lastName}</td>}
                            <td>{ps.periodStart} – {ps.periodEnd}</td>
                            <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                            <td>{ps.netSalary?.toFixed(2)} CHF</td>
                            <td className="actions-col">
                              <button type="button" className="button-primary" onClick={() => printPdf(ps.id)}>{t('payslips.print')}</button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                </>
            )}
          </section>

          {isAdmin && <ScheduleAllModal visible={scheduleVisible} onConfirm={() => {}} onClose={() => setScheduleVisible(false)} />}
        </div>
      </>
  );
};

export default PayslipsPage;