import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import '../styles/CompanySettingsPageScoped.css';

const rateToPercent = (v) => {
    const n = Number(v);
    if (!isFinite(n)) return null;
    return (n * 100).toFixed(2);
};

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
    const [errors, setErrors] = useState({});
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

    const validateField = (name, value) => {
        // Leer = okay (wird als "nicht verwendet" interpretiert)
        if (value === '' || value === null || value === undefined) return '';

        const num = Number(value);
        if (!isFinite(num)) return 'Bitte eine Zahl eingeben.';

        // Standard-Rates: 0–0.25 (0–25 %)
        const inRateRange = (x) => x >= 0 && x <= 0.25;
        // Midijob-Faktor: 0–1
        const inFactorRange = (x) => x >= 0 && x <= 1;

        if (name === 'midijobFactor') {
            if (!inFactorRange(num)) return 'Erlaubt: 0 bis 1 (z. B. 0.69).';
        } else {
            if (!inRateRange(num)) return 'Erlaubt: 0 bis 0.25 (0–25 %).';
        }
        return '';
    };

    const validateAll = (draft = form) => {
        const nextErrors = Object.fromEntries(
            Object.entries(draft).map(([k, v]) => [k, validateField(k, v)])
        );
        setErrors(nextErrors);
        // gültig, wenn keine Fehlermeldungen
        return Object.values(nextErrors).every((e) => !e);
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        const next = { ...form, [name]: value };
        setForm(next);
        // Live-Validierung pro Feld
        setErrors((prev) => ({ ...prev, [name]: validateField(name, value) }));
    };

    const save = () => {
        if (!validateAll()) {
            setStatus('Bitte Eingaben korrigieren.');
            return;
        }
        const payload = {};
        Object.entries(form).forEach(([k, v]) => {
            payload[k] = v === '' ? null : parseFloat(v);
        });
        api.put('/api/admin/company/settings', payload)
            .then(() => setStatus(t('companySettings.saved', 'Einstellungen gespeichert')))
            .catch(() => setStatus(t('companySettings.saveError', 'Fehler beim Speichern')));
    };

    return (
        <div className="scoped-dashboard">
            <Navbar />
            <div className="dashboard-card company-settings-card">
                <h2>{t('companySettings.title', 'Firmenparameter')}</h2>

                <div className="info-box">
                    <strong>Hinweis:</strong> Alle Beitragssätze bitte als <em>Dezimalzahl</em> mit Punkt eingeben.
                    Beispiel: <code>0.0125</code> entspricht <code>1,25&nbsp;%</code>. Leere Felder = nicht verwendet.
                </div>

                <div className="company-settings-form">
                    {/* CH – Arbeitgeber */}
                    <div className="form-group">
                        <label>
                            UVG BU <span className="badge">CH</span>
                        </label>
                        <small className="hint">
                            Berufsunfallversicherung (AG-Anteil) auf AHV-pflichtigen Lohn.
                            Beispiel: <code>0.012</code> = 1,2&nbsp;%.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="0.25"
                            step="0.0005"
                            placeholder="z. B. 0.012 (1,2 %)"
                            name="uvgBuRate"
                            value={form.uvgBuRate}
                            onChange={handleChange}
                        />
                        {form.uvgBuRate !== '' && <div className="preview">= {rateToPercent(form.uvgBuRate)} %</div>}
                        {errors.uvgBuRate && <div className="error">{errors.uvgBuRate}</div>}
                    </div>

                    {/* CH – Arbeitnehmer */}
                    <div className="form-group">
                        <label>
                            UVG NBU <span className="badge">CH</span>
                        </label>
                        <small className="hint">
                            Nichtberufsunfallversicherung (AN-Abzug).
                            Beispiel: <code>0.014</code> = 1,4&nbsp;%.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="0.25"
                            step="0.0005"
                            placeholder="z. B. 0.014 (1,4 %)"
                            name="uvgNbuRate"
                            value={form.uvgNbuRate}
                            onChange={handleChange}
                        />
                        {form.uvgNbuRate !== '' && <div className="preview">= {rateToPercent(form.uvgNbuRate)} %</div>}
                        {errors.uvgNbuRate && <div className="error">{errors.uvgNbuRate}</div>}
                    </div>

                    {/* CH – KTG */}
                    <div className="form-group">
                        <label>
                            Krankentaggeld AN <span className="badge">CH</span>
                        </label>
                        <small className="hint">
                            KTG-Versicherung (AN-Abzug), falls vorhanden. Sonst leer lassen.
                            Beispiel: <code>0.004</code> = 0,4&nbsp;%.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="0.25"
                            step="0.0005"
                            placeholder="z. B. 0.004 (0,4 %)"
                            name="ktgRateEmployee"
                            value={form.ktgRateEmployee}
                            onChange={handleChange}
                        />
                        {form.ktgRateEmployee !== '' && <div className="preview">= {rateToPercent(form.ktgRateEmployee)} %</div>}
                        {errors.ktgRateEmployee && <div className="error">{errors.ktgRateEmployee}</div>}
                    </div>

                    <div className="form-group">
                        <label>
                            Krankentaggeld AG <span className="badge">CH</span>
                        </label>
                        <small className="hint">
                            KTG-Versicherung (AG-Anteil/Arbeitgeberkosten), falls vorhanden. Sonst leer lassen.
                            Beispiel: <code>0.004</code> = 0,4&nbsp;%.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="0.25"
                            step="0.0005"
                            placeholder="z. B. 0.004 (0,4 %)"
                            name="ktgRateEmployer"
                            value={form.ktgRateEmployer}
                            onChange={handleChange}
                        />
                        {form.ktgRateEmployer !== '' && <div className="preview">= {rateToPercent(form.ktgRateEmployer)} %</div>}
                        {errors.ktgRateEmployer && <div className="error">{errors.ktgRateEmployer}</div>}
                    </div>

                    {/* CH – FAK */}
                    <div className="form-group">
                        <label>
                            FAK <span className="badge">CH</span>
                        </label>
                        <small className="hint">
                            Familienausgleichskasse (AG-Beitrag, kantonal unterschiedlich).
                            Beispiel: <code>0.02</code> = 2&nbsp;%.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="0.25"
                            step="0.0005"
                            placeholder="z. B. 0.02 (2 %)"
                            name="fakRate"
                            value={form.fakRate}
                            onChange={handleChange}
                        />
                        {form.fakRate !== '' && <div className="preview">= {rateToPercent(form.fakRate)} %</div>}
                        {errors.fakRate && <div className="error">{errors.fakRate}</div>}
                    </div>

                    {/* DE – Midijob */}
                    <div className="form-group">
                        <label>
                            Midijob Faktor <span className="badge">DE</span>
                        </label>
                        <small className="hint">
                            Faktor F für den Übergangsbereich (Midijob). Jahresparameter in Deutschland.
                            Wert zwischen <code>0</code> und <code>1</code>, z. B. <code>0.69</code>.
                        </small>
                        <input
                            type="number"
                            min="0"
                            max="1"
                            step="0.01"
                            placeholder="z. B. 0.69"
                            name="midijobFactor"
                            value={form.midijobFactor}
                            onChange={handleChange}
                        />
                        {errors.midijobFactor && <div className="error">{errors.midijobFactor}</div>}
                    </div>
                </div>

                <button className="primary-btn" onClick={save}>
                    {t('companySettings.save', 'Speichern')}
                </button>
                {status && <p>{status}</p>}

                <div className="info-box subtle">
                    <strong>Tipp:</strong> Schweiz-Firma ⇒ DE-Feld leer lassen. Deutschland-Firma ⇒ CH-Felder leer lassen.
                </div>
            </div>
        </div>
    );
};

export default CompanySettingsPage;
