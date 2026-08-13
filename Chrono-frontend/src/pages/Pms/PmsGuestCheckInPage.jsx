import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import { PmsLanguageSwitch, PmsTranslationBoundary } from './pmsI18n.jsx';
import '../../styles/PmsDashboardScoped.css';

const errorMessage = (error) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || 'Die Gästeanmeldung konnte nicht geladen werden.'
);

const ALL_REGISTRATION_FIELDS = [
    'addressLine',
    'postalCode',
    'city',
    'countryCode',
    'nationalityCode',
    'documentNumber',
    'signatureName',
    'privacyConsent',
];

const registrationRulePresentation = (ruleCode) => {
    if (ruleCode === 'CH-MELDESCHEIN') {
        return {
            title: 'Digitaler Meldeschein',
            completedTitle: 'Meldeschein übermittelt',
            buttonLabel: 'Meldeschein übermitteln',
            ruleLabel: 'Meldeschein Schweiz',
            purpose: 'für den Meldeschein Schweiz',
        };
    }
    if (ruleCode === 'DE-MELDESCHEIN') {
        return {
            title: 'Digitaler Meldeschein',
            completedTitle: 'Meldeschein übermittelt',
            buttonLabel: 'Meldeschein übermitteln',
            ruleLabel: 'Meldeschein Deutschland',
            purpose: 'für den Meldeschein Deutschland',
        };
    }
    return {
        title: 'Digitale Gästeanmeldung',
        completedTitle: 'Gästeanmeldung übermittelt',
        buttonLabel: 'Gästeanmeldung übermitteln',
        ruleLabel: 'Gästeanmeldung',
        purpose: 'für die Gästeanmeldung',
    };
};

const PmsGuestCheckInPage = () => {
    const { token } = useParams();
    const [registration, setRegistration] = useState(null);
    const [busy, setBusy] = useState(true);
    const [error, setError] = useState('');
    const [form, setForm] = useState({
        addressLine: '',
        postalCode: '',
        city: '',
        countryCode: '',
        nationalityCode: '',
        documentNumber: '',
        vehiclePlate: '',
        signatureName: '',
        privacyConsent: false,
    });

    useEffect(() => {
        let active = true;
        api.get(`/api/public/pms/guest-registration/${token}`)
            .then((response) => {
                if (active) {
                    setRegistration(response.data);
                    setForm((current) => ({ ...current, signatureName: response.data.guestName }));
                }
            })
            .catch((loadError) => active && setError(errorMessage(loadError)))
            .finally(() => active && setBusy(false));
        return () => { active = false; };
    }, [token]);

    const submit = async (event) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            const response = await api.post(`/api/public/pms/guest-registration/${token}`, {
                ...form,
                acknowledgedRuleCode: registration.ruleCode,
                acknowledgedRuleVersion: registration.ruleVersion,
            });
            setRegistration(response.data);
        } catch (submitError) {
            setError(errorMessage(submitError));
        } finally {
            setBusy(false);
        }
    };

    if (busy && !registration) return <main className="pms-dashboard"><section className="pms-work-card"><p>Meldeschein wird geladen…</p></section></main>;

    const rule = registrationRulePresentation(registration?.ruleCode);
    const requiredFields = new Set(
        Array.isArray(registration?.requiredFields)
            ? registration.requiredFields
            : ALL_REGISTRATION_FIELDS,
    );
    const isRequired = (fieldName) => requiredFields.has(fieldName);
    const labelFor = (label, fieldName) => (
        isRequired(fieldName) ? label : `${label} (optional)`
    );

    return (
        <PmsTranslationBoundary>
        <main className="pms-dashboard">
            <PmsLanguageSwitch />
            <section className="pms-work-card" style={{ maxWidth: 760, margin: '2rem auto' }}>
                {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
                {registration?.status === 'COMPLETED' ? (
                    <div className="pms-workspace-empty">
                        <span className="pms-eyebrow">{registration.hotelName}</span>
                        <h1>{rule.completedTitle}</h1>
                        <p>Danke, {registration.guestName}. Deine Angaben wurden sicher gespeichert.</p>
                        <p>Der Check-in und die Zimmerübergabe erfolgen separat durch das Hotel.</p>
                    </div>
                ) : registration && (
                    <>
                        <div className="pms-work-card-heading">
                            <div>
                                <span className="pms-eyebrow">{registration.hotelName}</span>
                                <h1>{rule.title}</h1>
                                <p>{registration.guestName} · {formatPmsDate(registration.arrivalDate)} bis {formatPmsDate(registration.departureDate)}</p>
                                <p>Diese Anmeldung ersetzt nicht den Check-in und die Zimmerübergabe im Hotel.</p>
                                <p>Verfahren: {rule.ruleLabel} · Version {registration.ruleVersion}</p>
                            </div>
                        </div>
                        <form className="pms-form-grid" onSubmit={submit}>
                            <label className="is-wide">{labelFor('Adresse', 'addressLine')}<input value={form.addressLine} onChange={(event) => setForm({ ...form, addressLine: event.target.value })} required={isRequired('addressLine')} /></label>
                            <label>{labelFor('PLZ', 'postalCode')}<input value={form.postalCode} onChange={(event) => setForm({ ...form, postalCode: event.target.value })} required={isRequired('postalCode')} /></label>
                            <label>{labelFor('Ort', 'city')}<input value={form.city} onChange={(event) => setForm({ ...form, city: event.target.value })} required={isRequired('city')} /></label>
                            <label>{labelFor('Wohnsitzland (ISO-Ländercode, z. B. CH)', 'countryCode')}<input maxLength="2" pattern="[A-Za-z]{2}" title="Zweistelliger ISO-Ländercode, zum Beispiel CH" value={form.countryCode} onChange={(event) => setForm({ ...form, countryCode: event.target.value.toUpperCase() })} required={isRequired('countryCode')} /></label>
                            <label>{labelFor('Nationalität (ISO-Ländercode, z. B. CH)', 'nationalityCode')}<input maxLength="2" pattern="[A-Za-z]{2}" title="Zweistelliger ISO-Ländercode, zum Beispiel CH" value={form.nationalityCode} onChange={(event) => setForm({ ...form, nationalityCode: event.target.value.toUpperCase() })} required={isRequired('nationalityCode')} /></label>
                            <label className="is-wide">{labelFor('Ausweis- oder Passnummer', 'documentNumber')}<input autoComplete="off" value={form.documentNumber} onChange={(event) => setForm({ ...form, documentNumber: event.target.value })} required={isRequired('documentNumber')} /></label>
                            <label>Kennzeichen (optional)<input value={form.vehiclePlate} onChange={(event) => setForm({ ...form, vehiclePlate: event.target.value })} /></label>
                            <label>{labelFor('Bestätigung durch Gast (vollständiger Name)', 'signatureName')}<input value={form.signatureName} onChange={(event) => setForm({ ...form, signatureName: event.target.value })} required={isRequired('signatureName')} /></label>
                            <label className="pms-checkbox is-wide"><input type="checkbox" checked={form.privacyConsent} onChange={(event) => setForm({ ...form, privacyConsent: event.target.checked })} required={isRequired('privacyConsent')} /> Ich bestätige, dass meine Angaben vollständig und richtig sind. Das Hotel verarbeitet diese Angaben {rule.purpose}.</label>
                            <p className="is-wide"><a href="/datenschutz" target="_blank" rel="noreferrer">Datenschutzhinweise öffnen</a></p>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>{rule.buttonLabel}</button></div>
                        </form>
                    </>
                )}
            </section>
        </main>
        </PmsTranslationBoundary>
    );
};

export default PmsGuestCheckInPage;
