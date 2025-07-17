import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/PayslipsPageScoped.css';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../context/LanguageContext';
import jsPDF from 'jspdf';

const PayslipsPage = () => {
  const { currentUser } = useAuth();
  const { t } = useTranslation();
  const [payslips, setPayslips] = useState([]);

  const handlePrint = (ps) => {
    const doc = new jsPDF();
    doc.setFontSize(16);
    doc.text(t('payslips.title'), 105, 20, { align: 'center' });
    doc.setFontSize(12);
    doc.text(`${t('payslips.period')}: ${ps.periodStart} - ${ps.periodEnd}`, 20, 40);
    doc.text(`${t('payslips.gross')}: ${ps.grossSalary?.toFixed(2)} CHF`, 20, 50);
    doc.text(`${t('payslips.net')}: ${ps.netSalary?.toFixed(2)} CHF`, 20, 60);
    doc.save(`Payslip_${ps.periodStart}_${ps.periodEnd}.pdf`);
  };

  useEffect(() => {
    if (!currentUser) return;
    api.get(`/api/payslips/user/${currentUser.id}`).then(res => {
      setPayslips(res.data);
    });
  }, [currentUser]);

  return (
    <div className="payslips-page scoped-dashboard">
      <Navbar />
      <h2>{t('payslips.title')}</h2>
      <table className="payslip-table">
        <thead>
          <tr>
            <th>{t('payslips.period', 'Zeitraum')}</th>
            <th>{t('payslips.gross')}</th>
            <th>{t('payslips.net')}</th>
          </tr>
        </thead>
        <tbody>
          {payslips.map(ps => (
            <tr key={ps.id}>
              <td>{ps.periodStart} - {ps.periodEnd}</td>
              <td>{ps.grossSalary?.toFixed(2)} CHF</td>
              <td>{ps.netSalary?.toFixed(2)} CHF</td>
              <td>
                <button onClick={() => handlePrint(ps)}>{t('payslips.print')}</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default PayslipsPage;
