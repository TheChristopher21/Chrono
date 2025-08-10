import { useState, useEffect, useContext, Fragment } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/AdminPayslipsPageScoped.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

const AdminPayslipsPage = () => {
  const [payslips, setPayslips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ userId: '', start: '', end: '', payoutDate: '' });
  const [filter, setFilter] = useState({ name: '', start: '', end: '' });
  const [logoFile, setLogoFile] = useState(null);
  const { t } = useTranslation();
  const { language } = useContext(LanguageContext);
  const [printLang, setPrintLang] = useState('de');
  const [scheduleVisible, setScheduleVisible] = useState(false);

  const fetchPending = () => {
    api.get('/api/payslips/admin/pending').then(res => {
      setPayslips(res.data);
    });
  };

  const fetchApproved = () => {
    api.get('/api/payslips/admin/approved', { params: filter }).then(res => {
      setApprovedSlips(res.data);
    });
  };

  const approve = (id) => {
    api.post(`/api/payslips/approve/${id}`).then(() => fetchPending());
  };

  const deletePayslip = (id) => {
    if (window.confirm(t('payslips.deleteConfirm', 'Abrechnung wirklich löschen?'))) {
      api.delete(`/api/payslips/${id}`).then(() => fetchPending());
    }
  };

  const editPayoutDate = (id, current) => {
    const val = prompt(t('payslips.enterPayoutDate'), current || '');
    if (val) {
      api.post(`/api/payslips/set-payout/${id}`, null, { params: { payoutDate: val } })
          .then(() => fetchPending());
    }
  };


  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    // Nur fortfahren, wenn der Benutzer einen Kommentar eingegeben hat oder auf OK geklickt hat (comment ist nicht null)
    if (comment !== null) {
      api.post('/api/payslips/approve-all', null, { params: { comment } }).then(() => fetchPending());
    }
  };

  const scheduleAll = () => {
    setScheduleVisible(true);
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
      document.body.removeChild(link);
    });
  };

  const printPdf = (id) => {
    api.get(`/api/payslips/admin/pdf/${id}`, { responseType: 'blob', params: { lang: printLang } })
        .then(res => {
          const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
          const win = window.open(url);
          // Kleiner Timeout, um sicherzustellen, dass der PDF-Viewer geladen ist
          setTimeout(() => {
            win?.print();
          }, 500);
        })
        .catch(() => alert(t('payslips.printError')));
  };

  const reopen = (id) => {
    api.post(`/api/payslips/reopen/${id}`).then(() => {
      fetchApproved();
      fetchPending();
    });
  };

  const createPayslip = () => {
    if (!form.userId || !form.start || !form.end) return;
    api.post('/api/payslips/generate', null, {
      params: { userId: form.userId, start: form.start, end: form.end, payoutDate: form.payoutDate }
    }).then(() => {
      setForm({ userId: '', start: '', end: '', payoutDate: '' });
      fetchPending();
    });
  };

  useEffect(() => {
    fetchPending();
    fetchApproved();
    api.get('/api/admin/users').then(res => setUsers(res.data));
  }, [filter]);

  const backup = () => {
    api.get('/api/payslips/admin/backup');
  };

  const uploadLogo = () => {
    if (!logoFile) return;
    const formData = new FormData();
    formData.append('file', logoFile);
    api.put('/api/admin/company/logo', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(() => {
      alert(t('payslips.logoSaved', 'Logo gespeichert'));
      setLogoFile(null);
    }).catch(() => {
      alert(t('payslips.logoSaveError', 'Fehler beim Speichern'));
    });
  };

  return (
      <div className="admin-payslips-page scoped-dashboard">
        <Navbar />
        <div className="top-sections-grid">
          {/* === Lohnabrechnung erstellen === */}
          <div className="dashboard-card">
            <h2>{t('payslips.generateTitle', 'Lohnabrechnung erstellen')}</h2>
            <p className="section-description">{t('payslips.generateDesc', 'Manuell eine Lohnabrechnung für einen Benutzer erstellen.')}</p>
            <div className="generate-form">
              <div className="form-group">
                <label>{t('payslips.selectUser', 'Benutzer wählen')}</label>
                <select value={form.userId} onChange={e => setForm({ ...form, userId: e.target.value })}>
                  <option value="">{t('payslips.selectUser', 'Benutzer wählen')}</option>
                  {users.map(u => (
                      <option key={u.id} value={u.id}>{u.username}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('payslips.periodStart', 'Startdatum')}</label>
                <input type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} />
              </div>
              <div className="form-group">
                <label>{t('payslips.periodEnd', 'Enddatum')}</label>
                <input type="date" value={form.end} onChange={e => setForm({ ...form, end: e.target.value })} />
              </div>
              <div className="form-group">
                <label>{t('payslips.payoutDate', 'Auszahlungsdatum')}</label>
                <input type="date" value={form.payoutDate} onChange={e => setForm({ ...form, payoutDate: e.target.value })} />
              </div>
              <button onClick={createPayslip} className="primary-btn">{t('payslips.generate', 'Erstellen')}</button>
            </div>
          </div>
          {/* === Logo Upload === */}
          <div className="dashboard-card">
            <h2>{t('payslips.logoTitle', 'Firmenlogo')}</h2>
            <p className="section-description">{t('payslips.logoDesc', 'Logo für die PDF-Abrechnungen hochladen.')}</p>
            <div className="logo-upload-form">
              <input id="file-upload" type="file" accept="image/*" onChange={e => setLogoFile(e.target.files[0])} style={{display: 'none'}}/>
              <label htmlFor="file-upload" className="custom-file-upload">
                {logoFile ? logoFile.name : t('payslips.selectFile', 'Datei wählen...')}
              </label>
              <button onClick={uploadLogo} disabled={!logoFile}>{t('payslips.saveLogo', 'Logo speichern')}</button>
            </div>
          </div>
        </div>


        {/* === Ausstehende Lohnabrechnungen === */}
        <div className="dashboard-card">
          <h2>{t('payslips.pendingTitle', 'Ausstehende Lohnabrechnungen')}</h2>
          <div className="controls-bar">
            <button className="action-btn" onClick={approveAll}>{t('payslips.approveAll', 'Alle freigeben')}</button>
            <button className="action-btn" onClick={scheduleAll}>{t('payslips.scheduleAll', 'Alle planen')}</button>
            <button className="action-btn" onClick={exportCsv}>{t('payslips.exportCsv', 'CSV Export')}</button>
            <button className="action-btn" onClick={backup}>{t('payslips.backup', 'Backup erstellen')}</button>
          </div>
          <ScheduleAllModal
              visible={scheduleVisible}
              onConfirm={confirmScheduleAll}
              onClose={() => setScheduleVisible(false)}
          />
          <div className="table-wrapper">
            <table className="payslip-table">
              <thead>
              <tr>
                <th>{t('payslips.user', 'Benutzer')}</th>
                <th>{t('payslips.period', 'Zeitraum')}</th>
                <th>{t('payslips.gross', 'Brutto')}</th>
                <th>{t('payslips.net', 'Netto')}</th>
                <th>{t('payslips.payoutDate', 'Auszahlungsdatum')}</th>
                <th className="actions-col">{t('payslips.actions', 'Aktionen')}</th>
              </tr>
              </thead>
              <tbody>
              {payslips.map(ps => (
                  <Fragment key={ps.id}>
                    <tr>
                      <td data-label={t('payslips.user', 'Benutzer')}>{ps.firstName} {ps.lastName}</td>
                      <td data-label={t('payslips.period', 'Zeitraum')}>{ps.periodStart} - {ps.periodEnd}</td>
                      <td data-label={t('payslips.gross', 'Brutto')}>{ps.grossSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                      <td data-label={t('payslips.net', 'Netto')}>{ps.netSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                      <td data-label={t('payslips.payoutDate', 'Auszahlungsdatum')}>{ps.payoutDate}</td>
                      <td data-label={t('payslips.actions', 'Aktionen')} className="actions-col">
                        <button onClick={() => editPayoutDate(ps.id, ps.payoutDate)}>{t('payslips.editPayout', 'Datum ändern')}</button>
                        <button onClick={() => approve(ps.id)}>{t('payslips.approve', 'Freigeben')}</button>
                        <button className="danger-btn" onClick={() => deletePayslip(ps.id)}>{t('payslips.delete', 'Löschen')}</button>
                      </td>
                    </tr>
                    {(ps.employerContribList && ps.employerContribList.length > 0) || ps.employerContributions ? (
                      <tr className="employer-details">
                        <td colSpan="6">
                          <strong>{t('payslips.employerContrib', 'Arbeitgeberbeiträge')}:</strong>
                          {ps.employerContribList && ps.employerContribList.length > 0 && (
                            <ul>
                              {ps.employerContribList.map((ec, idx) => (
                                <li key={idx}>{ec.type}: {ec.amount?.toFixed(2)} {ps.currency || 'CHF'}</li>
                              ))}
                            </ul>
                          )}
                          {ps.employerContributions != null && (
                            <div>{t('payslips.employerTotal', 'Summe')}: {ps.employerContributions.toFixed(2)} {ps.currency || 'CHF'}</div>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
              ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* === Genehmigte Lohnabrechnungen === */}
        <div className="dashboard-card">
          <h2>{t('payslips.approvedTitle', 'Genehmigte Lohnabrechnungen')}</h2>
          <div className="controls-bar">
            <div className="form-group">
              <label>{t('payslips.filterName', 'Filter nach Name')}</label>
              <input
                  className="filter-input"
                  placeholder={t('payslips.filterName', 'Name')}
                  value={filter.name}
                  onChange={e => setFilter({ ...filter, name: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label>{t('payslips.periodStart', 'Startdatum')}</label>
              <input type="date" value={filter.start} className="filter-input"
                     onChange={e => setFilter({ ...filter, start: e.target.value })} />
            </div>
            <div className="form-group">
              <label>{t('payslips.periodEnd', 'Enddatum')}</label>
              <input type="date" value={filter.end} className="filter-input"
                     onChange={e => setFilter({ ...filter, end: e.target.value })} />
            </div>
            <div className="form-group">
              <label>{t('navbar.languageLabel', 'Drucksprache')}</label>
              <select value={printLang} onChange={e => setPrintLang(e.target.value)}>
                <option value="de">DE</option>
                <option value="en">EN</option>
              </select>
            </div>
            <button onClick={fetchApproved} className="primary-btn">{t('payslips.filter', 'Filtern')}</button>
          </div>
          <div className="table-wrapper">
            <table className="payslip-table">
              <thead>
              <tr>
                <th>{t('payslips.user', 'Benutzer')}</th>
                <th>{t('payslips.period', 'Zeitraum')}</th>
                <th>{t('payslips.gross', 'Brutto')}</th>
                <th>{t('payslips.net', 'Netto')}</th>
                <th>{t('payslips.payoutDate', 'Auszahlungsdatum')}</th>
                <th className="actions-col">{t('payslips.actions', 'Aktionen')}</th>
              </tr>
              </thead>
              <tbody>
              {approvedSlips.map(ps => (
                  <Fragment key={ps.id}>
                    <tr>
                      <td data-label={t('payslips.user', 'Benutzer')}>{ps.firstName} {ps.lastName}</td>
                      <td data-label={t('payslips.period', 'Zeitraum')}>{ps.periodStart} - {ps.periodEnd}</td>
                      <td data-label={t('payslips.gross', 'Brutto')}>{ps.grossSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                      <td data-label={t('payslips.net', 'Netto')}>{ps.netSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                      <td data-label={t('payslips.payoutDate', 'Auszahlungsdatum')}>{ps.payoutDate}</td>
                      <td data-label={t('payslips.actions', 'Aktionen')} className="actions-col">
                        <button onClick={() => printPdf(ps.id)}>{t('payslips.print', 'Drucken')}</button>
                        <button className="warning-btn" onClick={() => reopen(ps.id)}>{t('payslips.reopen', 'Zurückziehen')}</button>
                      </td>
                    </tr>
                    {(ps.employerContribList && ps.employerContribList.length > 0) || ps.employerContributions ? (
                      <tr className="employer-details">
                        <td colSpan="6">
                          <strong>{t('payslips.employerContrib', 'Arbeitgeberbeiträge')}:</strong>
                          {ps.employerContribList && ps.employerContribList.length > 0 && (
                            <ul>
                              {ps.employerContribList.map((ec, idx) => (
                                <li key={idx}>{ec.type}: {ec.amount?.toFixed(2)} {ps.currency || 'CHF'}</li>
                              ))}
                            </ul>
                          )}
                          {ps.employerContributions != null && (
                            <div>{t('payslips.employerTotal', 'Summe')}: {ps.employerContributions.toFixed(2)} {ps.currency || 'CHF'}</div>
                          )}
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
              ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
  );
};

export default AdminPayslipsPage;