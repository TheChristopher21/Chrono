import { useState, useEffect, useContext } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/AdminPayslipsPageScoped.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

const AdminPayslipsPage = () => {
  const [payslips, setPayslips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ userId: '', start: '', end: '' });
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


  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    api.post('/api/payslips/approve-all', null, { params: { comment } }).then(() => fetchPending());
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
    });
  };

  const printPdf = (id) => {
    api.get(`/api/payslips/admin/pdf/${id}`, { responseType: 'blob', params: { lang: printLang } })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
        const win = window.open(url);
        win?.print();
      })
      .catch(() => alert(t('payslips.printError')));

  };

  const createPayslip = () => {
    if (!form.userId || !form.start || !form.end) return;
    api.post('/api/payslips/generate', null, {
      params: { userId: form.userId, start: form.start, end: form.end }
    }).then(() => {
      setForm({ userId: '', start: '', end: '' });
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
        <div className="logo-upload">
          <input type="file" accept="image/*" onChange={e => setLogoFile(e.target.files[0])} />
          <button onClick={uploadLogo}>{t('payslips.saveLogo', 'Logo speichern')}</button>
        </div>
        <h2>{t('payslips.pendingTitle')}</h2>
        {/* NEU: Formular zum Erstellen */}
        <div className="generate-form">
          <select value={form.userId} onChange={e => setForm({ ...form, userId: e.target.value })}>
            <option value="">{t('payslips.selectUser', 'Benutzer wählen')}</option>
            {users.map(u => (
                <option key={u.id} value={u.id}>{u.username}</option>
            ))}
          </select>
          <input type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} />
          <input type="date" value={form.end} onChange={e => setForm({ ...form, end: e.target.value })} />
          <button onClick={createPayslip}>{t('payslips.generate', 'Erstellen')}</button>
        </div>
        {/* --- */}
        <button className="approve-all" onClick={approveAll}>{t('payslips.approveAll')}</button>
        <button className="approve-all" onClick={exportCsv}>{t('payslips.exportCsv')}</button>
        <button className="approve-all" onClick={backup}>{t('payslips.backup')}</button>
        <button className="approve-all" onClick={scheduleAll}>{t('payslips.scheduleAll')}</button>
        <ScheduleAllModal
          visible={scheduleVisible}
          onConfirm={confirmScheduleAll}
          onClose={() => setScheduleVisible(false)}
        />

        <table className="payslip-table">
          <thead>
          <tr>
            <th>{t('payslips.user')}</th>
            <th>{t('payslips.period', 'Zeitraum')}</th>
            <th>{t('payslips.gross')}</th>
            <th>{t('payslips.net')}</th>
            <th></th>
          </tr>
          </thead>
          <tbody>
          {payslips.map(ps => (
              <tr key={ps.id}>
                <td>{ps.userId}</td>
                <td>{ps.periodStart} - {ps.periodEnd}</td>
                <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                <td>{ps.netSalary?.toFixed(2)} CHF</td>
                <td><button onClick={() => approve(ps.id)}>{t('payslips.approve')}</button></td>
              </tr>
          ))}
          </tbody>
        </table>

        <h2>{t('payslips.approvedTitle')}</h2>
        <div className="print-lang-select">
          <label>{t('navbar.languageLabel', 'Sprache')}:</label>
          <select value={printLang} onChange={e => setPrintLang(e.target.value)}>
            <option value="de">DE</option>
            <option value="en">EN</option>
          </select>
        </div>
        <div className="filter-form">
          <input
            placeholder={t('payslips.filterName', 'Name')}
            value={filter.name}
            onChange={e => setFilter({ ...filter, name: e.target.value })}
          />
          <input type="date" value={filter.start}
            onChange={e => setFilter({ ...filter, start: e.target.value })} />
          <input type="date" value={filter.end}
            onChange={e => setFilter({ ...filter, end: e.target.value })} />
          <button onClick={fetchApproved}>{t('payslips.filter', 'Filtern')}</button>
        </div>
        <table className="payslip-table">
          <thead>
          <tr>
            <th>{t('payslips.user')}</th>
            <th>{t('payslips.period', 'Zeitraum')}</th>
            <th>{t('payslips.gross')}</th>
            <th>{t('payslips.net')}</th>
            <th></th>
          </tr>
          </thead>
          <tbody>
          {approvedSlips.map(ps => (
              <tr key={ps.id}>
                <td>{ps.userId}</td>
                <td>{ps.periodStart} - {ps.periodEnd}</td>
                <td>{ps.grossSalary?.toFixed(2)} CHF</td>
                <td>{ps.netSalary?.toFixed(2)} CHF</td>
                <td><button onClick={() => printPdf(ps.id)}>{t('payslips.print')}</button></td>
              </tr>
          ))}
          </tbody>
        </table>
      </div>
  );
};

export default AdminPayslipsPage;
