import { useState, useEffect, useContext } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/PayslipsPageScoped.css';
import { useAuth } from '../context/AuthContext';
import { useTranslation, LanguageContext } from '../context/LanguageContext';
import jsPDF from 'jspdf';

const PayslipsPage = () => {
  const { currentUser } = useAuth();
  const { t } = useTranslation();
  const { language, setLanguage } = useContext(LanguageContext);
  const [printLang, setPrintLang] = useState('de');
  const [payslips, setPayslips] = useState([]);
  const [form, setForm] = useState({ start: '', end: '' });
  const [scheduleDay, setScheduleDay] = useState(1);

  const createPayslip = () => {
    if (!form.start || !form.end || !currentUser) return;
    api
      .post('/api/payslips/generate', null, {
        params: { userId: currentUser.id, start: form.start, end: form.end }
      })
      .then(res => {
        setPayslips(prev => [...prev, res.data]);
        setForm({ start: '', end: '' });
      });
  };

  const saveSchedule = () => {
    if (!currentUser) return;
    api.post('/api/payslips/schedule', null, {
      params: { userId: currentUser.id, day: scheduleDay }
    });
  };

  const handlePrint = async (ps) => {
    const prev = language;
    if (printLang !== language) {
      setLanguage(printLang);
      await new Promise((r) => setTimeout(r, 0));
    }
    const doc = new jsPDF();
    doc.setFontSize(16);
    doc.text(t('payslips.title'), 105, 20, { align: 'center' });
    doc.setFontSize(12);
    doc.text(`${t('payslips.period')}: ${ps.periodStart} - ${ps.periodEnd}`, 20, 40);
    doc.text(`${t('payslips.gross')}: ${ps.grossSalary?.toFixed(2)} CHF`, 20, 50);
    doc.text(`${t('payslips.net')}: ${ps.netSalary?.toFixed(2)} CHF`, 20, 60);
    doc.save(`Payslip_${ps.periodStart}_${ps.periodEnd}.pdf`);
    if (printLang !== prev) {
      setLanguage(prev);
    }
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
      <div className="print-lang-select">
        <label>{t('navbar.languageLabel', 'Sprache')}:</label>
        <select value={printLang} onChange={e => setPrintLang(e.target.value)}>
          <option value="de">DE</option>
          <option value="en">EN</option>
        </select>
      </div>
      <div className="generate-form">
        <input
          type="date"
          value={form.start}
          onChange={e => setForm({ ...form, start: e.target.value })}
        />
        <input
          type="date"
          value={form.end}
          onChange={e => setForm({ ...form, end: e.target.value })}
        />
        <button onClick={createPayslip}>{t('payslips.generate', 'Erstellen')}</button>
      </div>
      <div className="schedule-form">
        <label>{t('payslips.scheduleDay')}:</label>
        <input
          type="number"
          min="1"
          max="28"
          value={scheduleDay}
          onChange={e => setScheduleDay(e.target.value)}
        />
        <button onClick={saveSchedule}>{t('payslips.scheduleButton')}</button>
      </div>
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
