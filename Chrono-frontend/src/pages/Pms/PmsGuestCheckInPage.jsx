import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import api from '../../utils/api.js';
import '../../styles/PmsDashboardScoped.css';

const errorMessage = (error) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || 'Der digitale Check-in konnte nicht geladen werden.'
);

const PmsGuestCheckInPage = () => {
    const { token } = useParams();
    const [registration, setRegistration] = useState(null);
    const [busy, setBusy] = useState(true);
    const [error, setError] = useState('');
    const [form, setForm] = useState({
        addressLine: '',
        postalCode: '',
        city: '',
        countryCode: 'CH',
        nationalityCode: 'CH',
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
            const response = await api.post(`/api/public/pms/guest-registration/${token}`, form);
            setRegistration(response.data);
        } catch (submitError) {
            setError(errorMessage(submitError));
        } finally {
            setBusy(false);
        }
    };

    if (busy && !registration) return <main className="pms-dashboard"><section className="pms-work-card"><p>Check-in wird geladen…</p></section></main>;

    return (
        <main className="pms-dashboard">
            <section className="pms-work-card" style={{ maxWidth: 760, margin: '2rem auto' }}>
                {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
                {registration?.status === 'COMPLETED' ? (
                    <div className="pms-workspace-empty">
                        <span className="pms-eyebrow">{registration.hotelName}</span>
                        <h1>Check-in vollständig</h1>
                        <p>Danke, {registration.guestName}. Dein Meldeschein wurde sicher gespeichert.</p>
                    </div>
                ) : registration && (
                    <>
                        <div className="pms-work-card-heading">
                            <div>
                                <span className="pms-eyebrow">{registration.hotelName}</span>
                                <h1>Digitaler Check-in</h1>
                                <p>{registration.guestName} · {registration.arrivalDate} bis {registration.departureDate}</p>
                            </div>
                        </div>
                        <form className="pms-form-grid" onSubmit={submit}>
                            <label className="is-wide">Adresse<input value={form.addressLine} onChange={(event) => setForm({ ...form, addressLine: event.target.value })} required /></label>
                            <label>PLZ<input value={form.postalCode} onChange={(event) => setForm({ ...form, postalCode: event.target.value })} required /></label>
                            <label>Ort<input value={form.city} onChange={(event) => setForm({ ...form, city: event.target.value })} required /></label>
                            <label>Wohnsitzland<input maxLength="2" value={form.countryCode} onChange={(event) => setForm({ ...form, countryCode: event.target.value.toUpperCase() })} required /></label>
                            <label>Nationalität<input maxLength="2" value={form.nationalityCode} onChange={(event) => setForm({ ...form, nationalityCode: event.target.value.toUpperCase() })} required /></label>
                            <label className="is-wide">Ausweis- oder Passnummer<input autoComplete="off" value={form.documentNumber} onChange={(event) => setForm({ ...form, documentNumber: event.target.value })} required /></label>
                            <label>Kennzeichen (optional)<input value={form.vehiclePlate} onChange={(event) => setForm({ ...form, vehiclePlate: event.target.value })} /></label>
                            <label>Unterschrift (Name)<input value={form.signatureName} onChange={(event) => setForm({ ...form, signatureName: event.target.value })} required /></label>
                            <label className="pms-checkbox is-wide"><input type="checkbox" checked={form.privacyConsent} onChange={(event) => setForm({ ...form, privacyConsent: event.target.checked })} required /> Ich bestätige die Richtigkeit und stimme der Verarbeitung für den gesetzlichen Meldeschein zu.</label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Check-in abschließen</button></div>
                        </form>
                    </>
                )}
            </section>
        </main>
    );
};

export default PmsGuestCheckInPage;
