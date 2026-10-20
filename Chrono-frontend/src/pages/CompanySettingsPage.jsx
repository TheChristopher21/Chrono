import PayrollConfigurationPanel from '../components/PayrollConfigurationPanel';
import React, { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import '../styles/CompanySettingsPageScoped.css';

const buildHolidayPreferenceMap = (preferences = []) => {
    return preferences.reduce((acc, pref) => {
        if (pref?.holidayCode) {
            acc[pref.holidayCode] = {
                selected: true,
                halfDay: Boolean(pref.halfDay)
            };
        }
        return acc;
    }, {});
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
    const [loaded, setLoaded] = useState(false);
    const [saving, setSaving] = useState(false);
    const [holidayCatalog, setHolidayCatalog] = useState([]);
    const [holidayPreferences, setHolidayPreferences] = useState({});
    const [customHolidaySelectionEnabled, setCustomHolidaySelectionEnabled] = useState(true);
    const [holidayCountryFilter, setHolidayCountryFilter] = useState('ALL');
    const [holidaySearch, setHolidaySearch] = useState('');

    useEffect(() => {
        Promise.all([
            api.get('/api/admin/company/settings'),
            api.get('/api/admin/company/holiday-catalog')
        ]).then(([res, catalogRes]) => {
            setForm({
                uvgBuRate: res.data.uvgBuRate ?? '',
                uvgNbuRate: res.data.uvgNbuRate ?? '',
                ktgRateEmployee: res.data.ktgRateEmployee ?? '',
                ktgRateEmployer: res.data.ktgRateEmployer ?? '',
                fakRate: res.data.fakRate ?? '',
                midijobFactor: res.data.midijobFactor ?? ''
            });
            setCustomHolidaySelectionEnabled(Boolean(res.data.customHolidaySelectionEnabled));
            setHolidayPreferences(buildHolidayPreferenceMap(res.data.holidayPreferences || []));
            setHolidayCatalog(catalogRes.data || []);
            setLoaded(true);
        }).catch(() => setStatus('Firmeneinstellungen konnten nicht geladen werden. Bitte die Seite erneut laden.'));
    }, []);

    const validateField = (name, value) => {
        // Leere Altwerte bleiben unbekannt; Vertragswerte werden separat versioniert.
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

    const setHolidaySelected = (code, selected) => {
        setHolidayPreferences((prev) => ({
            ...prev,
            [code]: {
                selected,
                halfDay: selected ? Boolean(prev[code]?.halfDay) : false
            }
        }));
    };

    const setHolidayHalfDay = (code, halfDay) => {
        setHolidayPreferences((prev) => ({
            ...prev,
            [code]: {
                selected: Boolean(prev[code]?.selected),
                halfDay
            }
        }));
    };

    const filteredHolidayCatalog = holidayCatalog.filter((item) => {
        const matchesCountry = holidayCountryFilter === 'ALL' || item.country === holidayCountryFilter;
        const term = holidaySearch.trim().toLowerCase();
        const matchesSearch = !term ||
            item.name?.toLowerCase().includes(term) ||
            item.regionHint?.toLowerCase().includes(term);
        return matchesCountry && matchesSearch;
    });

    const save = () => {
        if (!loaded || saving) return;
        if (!validateAll()) {
            setStatus('Bitte Eingaben korrigieren.');
            return;
        }
        const payload = {};
        Object.entries(form).forEach(([k, v]) => {
            payload[k] = v === '' ? null : parseFloat(v);
        });
        payload.customHolidaySelectionEnabled = customHolidaySelectionEnabled;
        payload.holidayPreferences = Object.entries(holidayPreferences)
            .filter(([, pref]) => pref.selected)
            .map(([holidayCode, pref]) => ({
                holidayCode,
                halfDay: Boolean(pref.halfDay)
            }));
        setSaving(true);
        api.put('/api/admin/company/settings', payload)
            .then(() => setStatus(t('companySettings.saved', 'Einstellungen gespeichert')))
            .catch(() => setStatus(t('companySettings.saveError', 'Fehler beim Speichern')))
            .finally(() => setSaving(false));
    };

    return (
        <div className="company-settings-page">
            <Navbar />
            <div className="dashboard-card company-settings-card">
                <h2>{t('companySettings.title', 'Firmenparameter')}</h2>

                <PayrollConfigurationPanel />
                <div className="company-settings-form">
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

                <section className="holiday-settings-panel">
                    <div className="holiday-settings-header">
                        <h3>{t('companySettings.holidaysTitle', 'Feiertage')}</h3>
                        <label className="holiday-profile-toggle">
                            <input
                                type="checkbox"
                                checked={customHolidaySelectionEnabled}
                                onChange={(e) => setCustomHolidaySelectionEnabled(e.target.checked)}
                            />
                            {t('companySettings.customHolidaySelection', 'Firmenauswahl verwenden')}
                        </label>
                    </div>

                    <div className="holiday-controls">
                        <select
                            value={holidayCountryFilter}
                            onChange={(e) => setHolidayCountryFilter(e.target.value)}
                        >
                            <option value="ALL">{t('companySettings.allCountries', 'Alle Länder')}</option>
                            <option value="CH">Schweiz</option>
                            <option value="DE">Deutschland</option>
                        </select>
                        <input
                            type="search"
                            value={holidaySearch}
                            onChange={(e) => setHolidaySearch(e.target.value)}
                            placeholder={t('companySettings.searchHoliday', 'Feiertag suchen')}
                        />
                    </div>

                    <div className="holiday-list">
                        {filteredHolidayCatalog.map((item) => {
                            const pref = holidayPreferences[item.code] || { selected: false, halfDay: false };
                            return (
                                <div className={`holiday-row ${pref.selected ? 'selected' : ''}`} key={item.code}>
                                    <label className="holiday-main-check">
                                        <input
                                            type="checkbox"
                                            disabled={!customHolidaySelectionEnabled}
                                            checked={Boolean(pref.selected)}
                                            onChange={(e) => setHolidaySelected(item.code, e.target.checked)}
                                        />
                                        <span className="holiday-name">{item.name}</span>
                                        <span className="holiday-country">{item.country}</span>
                                        <span className="holiday-region">{item.regionHint}</span>
                                    </label>
                                    <label className="holiday-halfday-check">
                                        <input
                                            type="checkbox"
                                            disabled={!customHolidaySelectionEnabled || !pref.selected}
                                            checked={Boolean(pref.halfDay)}
                                            onChange={(e) => setHolidayHalfDay(item.code, e.target.checked)}
                                        />
                                        {t('companySettings.halfDay', 'Halbtags')}
                                    </label>
                                </div>
                            );
                        })}
                    </div>
                </section>

                <button className="primary-btn" onClick={save} disabled={!loaded || saving}>
                    {t('companySettings.save', 'Speichern')}
                </button>
                {status && <p>{status}</p>}

                <div className="info-box subtle">
                    <strong>Tipp:</strong> Der Midijob-Faktor ist ein deutscher Jahresparameter. Schweizer Vertragswerte werden oben separat gespeichert.
                </div>
            </div>
        </div>
    );
};

export default CompanySettingsPage;
