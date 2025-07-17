import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/AdminPayslipsPageScoped.css';
import { useTranslation } from '../context/LanguageContext';

const AdminPayslipsPage = () => {
  const [payslips, setPayslips] = useState([]);
  const { t } = useTranslation();

  const fetchPending = () => {
    api.get('/api/payslips/admin/pending').then(res => {
      setPayslips(res.data);
    });
  };

  const approve = (id) => {
    const comment = prompt(t('payslips.approve'));
    api.post(`/api/payslips/approve/${id}`, null, { params: { comment } }).then(() => fetchPending());
  };

  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll'));
    api.post('/api/payslips/approve-all', null, { params: { comment } }).then(() => fetchPending());
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

  useEffect(() => {
    fetchPending();
  }, []);

  const backup = () => {
    api.get('/api/payslips/admin/backup');
  };

  return (
    <div className="admin-payslips-page scoped-dashboard">
      <Navbar />
      <h2>{t('payslips.pendingTitle')}</h2>
      <button className="approve-all" onClick={approveAll}>{t('payslips.approveAll')}</button>
      <button className="approve-all" onClick={exportCsv}>{t('payslips.exportCsv')}</button>
      <button className="approve-all" onClick={backup}>{t('payslips.backup')}</button>
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
    </div>
  );
};

export default AdminPayslipsPage;
