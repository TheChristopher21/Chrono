import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import '../styles/CompanySettingsPageScoped.css';

const CompanySettingsPage = () => {
  const { t } = useTranslation();
  const [form, setForm] = useState({
    uvgBuRate: '',
    uvgNbuRate: '',
    ktgRateEmployee: '',
    ktgRateEmployer: '',
    fakRate: '',
    midijobFactor: ''
  });
  const [status, setStatus] = useState('');

  useEffect(() => {
    api.get('/api/admin/company/settings').then(res => {
      setForm({
        uvgBuRate: res.data.uvgBuRate ?? '',
        uvgNbuRate: res.data.uvgNbuRate ?? '',
        ktgRateEmployee: res.data.ktgRateEmployee ?? '',
        ktgRateEmployer: res.data.ktgRateEmployer ?? '',
        fakRate: res.data.fakRate ?? '',
        midijobFactor: res.data.midijobFactor ?? ''
      });
    });
  }, []);

  const handleChange = e => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const save = () => {
    const payload = {};
    Object.entries(form).forEach(([k, v]) => {
      payload[k] = v === '' ? null : parseFloat(v);
    });
    api.put('/api/admin/company/settings', payload)
      .then(() => setStatus(t('companySettings.saved', 'Gespeichert')))
      .catch(() => setStatus(t('companySettings.saveError', 'Fehler beim Speichern')));
  };

  return (
    <div className="scoped-dashboard">
      <Navbar />
      <div className="dashboard-card company-settings-card">
        <h2>{t('companySettings.title', 'Firmenparameter')}</h2>
        <div className="company-settings-form">
          <div className="form-group">
            <label>UVG BU</label>
            <input type="number" step="0.0005" name="uvgBuRate" value={form.uvgBuRate} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>UVG NBU</label>
            <input type="number" step="0.0005" name="uvgNbuRate" value={form.uvgNbuRate} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>Krankentaggeld AN</label>
            <input type="number" step="0.0005" name="ktgRateEmployee" value={form.ktgRateEmployee} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>Krankentaggeld AG</label>
            <input type="number" step="0.0005" name="ktgRateEmployer" value={form.ktgRateEmployer} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>FAK</label>
            <input type="number" step="0.0005" name="fakRate" value={form.fakRate} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>Midijob Faktor</label>
            <input type="number" step="0.01" name="midijobFactor" value={form.midijobFactor} onChange={handleChange} />
          </div>
        </div>
        <button className="primary-btn" onClick={save}>{t('companySettings.save', 'Speichern')}</button>
        {status && <p>{status}</p>}
      </div>
    </div>
  );
};

export default CompanySettingsPage;
