import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import api from '../../utils/api.js';
import '../../styles/PmsBookingPage.css';

const dateKey = (offset = 0) => {
    const value = new Date();
    value.setDate(value.getDate() + offset);
    return value.toISOString().slice(0, 10);
};

const errorMessage = (error) => error?.response?.data?.detail
    || error?.response?.data?.message || error?.message || 'Die Buchung konnte nicht abgeschlossen werden.';

const PmsBookingPage = () => {
    const { propertyCode } = useParams();
    const location = useLocation();
    const [config, setConfig] = useState(null);
    const [availability, setAvailability] = useState(null);
    const [selectedRateId, setSelectedRateId] = useState('');
    const [stay, setStay] = useState({ arrival: dateKey(1), departure: dateKey(2), adults: 1, children: 0 });
    const [guest, setGuest] = useState({
        firstName: '', lastName: '', email: '', phone: '', termsAccepted: false, privacyAccepted: false,
    });
    const [confirmation, setConfirmation] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const bookingKeyRef = useRef(null);
    const verificationToken = useMemo(() => new URLSearchParams(location.search)
        .get('verificationToken'), [location.search]);

    useEffect(() => {
        setBusy(true);
        api.get(`/api/public/pms/booking/${propertyCode}`)
            .then((response) => setConfig(response.data))
            .catch((loadError) => setError(errorMessage(loadError)))
            .finally(() => setBusy(false));
    }, [propertyCode]);

    useEffect(() => {
        if (!verificationToken) return;
        setBusy(true);
        setError('');
        api.post(`/api/public/pms/booking/${propertyCode}/verify`, { token: verificationToken })
            .then((response) => setConfirmation(response.data))
            .catch((verifyError) => setError(errorMessage(verifyError)))
            .finally(() => setBusy(false));
    }, [propertyCode, verificationToken]);

    useEffect(() => {
        bookingKeyRef.current = null;
    }, [stay, guest, selectedRateId]);

    const selectedRate = useMemo(() => availability?.roomTypes
        ?.flatMap((roomType) => roomType.rates.map((rate) => ({ ...rate, roomTypeName: roomType.name })))
        .find((rate) => String(rate.ratePlanId) === String(selectedRateId)), [availability, selectedRateId]);

    const search = async (event) => {
        event?.preventDefault();
        setBusy(true);
        setError('');
        setConfirmation(null);
        try {
            const response = await api.get(`/api/public/pms/booking/${propertyCode}/availability`, {
                params: { arrival: stay.arrival, departure: stay.departure },
            });
            setAvailability(response.data);
            setSelectedRateId('');
        } catch (searchError) {
            setError(errorMessage(searchError));
        } finally { setBusy(false); }
    };

    const book = async (event) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            if (!bookingKeyRef.current) {
                bookingKeyRef.current = globalThis.crypto?.randomUUID?.()
                    || `booking-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            }
            const response = await api.post(`/api/public/pms/booking/${propertyCode}/reservations`, {
                arrivalDate: stay.arrival,
                departureDate: stay.departure,
                ratePlanId: Number(selectedRateId),
                adults: Number(stay.adults),
                children: Number(stay.children),
                ...guest,
            }, {
                headers: { 'Idempotency-Key': bookingKeyRef.current },
            });
            setConfirmation(response.data);
            setAvailability(null);
            bookingKeyRef.current = null;
        } catch (bookingError) {
            setError(errorMessage(bookingError));
        } finally { setBusy(false); }
    };

    return (
        <main className="pms-booking-page">
            <header className="pms-booking-hero">
                <span>Direkt beim Hotel buchen</span>
                <h1>{config?.hotelName || 'Onlinebuchung'}</h1>
                {config?.city && <p>{config.city}</p>}
            </header>

            {error && <div className="pms-booking-message is-error" role="alert">{error}</div>}
            {confirmation ? (
                <section className="pms-booking-card pms-booking-confirmation">
                    <span>{confirmation.verificationRequired
                        ? 'E-Mail-Bestätigung erforderlich'
                        : confirmation.status === 'CONFIRMED'
                            ? 'Reservierung bestätigt'
                            : 'E-Mail bestätigt – Garantie ausstehend'}</span>
                    <h2>{confirmation.confirmationCode}</h2>
                    <p>{confirmation.roomTypeName} · {confirmation.rateName}</p>
                    <strong>{new Intl.NumberFormat('de-CH', { style: 'currency', currency: confirmation.currencyCode }).format(confirmation.totalAmount)}</strong>
                    {confirmation.verificationRequired && <p>Wir haben dir einen Bestätigungslink per E-Mail gesendet. Bis zur Bestätigung bleibt das Zimmer kurzzeitig vorgemerkt.</p>}
                    {!confirmation.verificationRequired && confirmation.status === 'TENTATIVE' && <p>Deine E-Mail ist bestätigt. Das Hotel bestätigt die Reservierung nach Eingang der erforderlichen Garantie oder Anzahlung.</p>}
                    {confirmation.holdUntil && confirmation.status === 'TENTATIVE' && <p>Vorgemerkt bis {new Intl.DateTimeFormat('de-CH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(confirmation.holdUntil))}.</p>}
                    {confirmation.confirmationMessage && <p>{confirmation.confirmationMessage}</p>}
                    <button type="button" onClick={() => { setConfirmation(null); setGuest({ firstName: '', lastName: '', email: '', phone: '', termsAccepted: false, privacyAccepted: false }); }}>Weitere Buchung</button>
                </section>
            ) : (
                <>
                    <section className="pms-booking-card">
                        <h2>Aufenthalt suchen</h2>
                        <form className="pms-booking-grid" onSubmit={search}>
                            <label>Anreise<input type="date" min={dateKey()} value={stay.arrival} onChange={(e) => setStay({ ...stay, arrival: e.target.value })} required /></label>
                            <label>Abreise<input type="date" min={stay.arrival} value={stay.departure} onChange={(e) => setStay({ ...stay, departure: e.target.value })} required /></label>
                            <label>Erwachsene<input type="number" min="1" max="20" value={stay.adults} onChange={(e) => setStay({ ...stay, adults: e.target.value })} /></label>
                            <label>Kinder<input type="number" min="0" max="20" value={stay.children} onChange={(e) => setStay({ ...stay, children: e.target.value })} /></label>
                            <button className="is-primary" disabled={busy}>{busy ? 'Suche…' : 'Verfügbarkeit prüfen'}</button>
                        </form>
                    </section>

                    {availability && (
                        <section className="pms-booking-card">
                            <h2>Zimmer und Rate wählen</h2>
                            <div className="pms-booking-rates">
                                {availability.roomTypes.flatMap((roomType) => roomType.rates.map((rate) => (
                                    <label className={`pms-booking-rate ${!rate.available ? 'is-disabled' : ''}`} key={rate.ratePlanId}>
                                        <input type="radio" name="rate" disabled={!rate.available} checked={String(selectedRateId) === String(rate.ratePlanId)} onChange={() => setSelectedRateId(rate.ratePlanId)} />
                                        <span><strong>{roomType.name}</strong><small>{rate.name}{rate.restriction ? ` · ${rate.restriction}` : ''}</small></span>
                                        <b>{new Intl.NumberFormat('de-CH', { style: 'currency', currency: rate.currencyCode }).format(rate.totalAmount)}</b>
                                    </label>
                                )))}
                            </div>
                        </section>
                    )}

                    {selectedRate && (
                        <section className="pms-booking-card">
                            <h2>Kontaktdaten</h2>
                            <form className="pms-booking-grid" onSubmit={book}>
                                <label>Vorname<input value={guest.firstName} onChange={(e) => setGuest({ ...guest, firstName: e.target.value })} required /></label>
                                <label>Nachname<input value={guest.lastName} onChange={(e) => setGuest({ ...guest, lastName: e.target.value })} required /></label>
                                <label>E-Mail<input type="email" value={guest.email} onChange={(e) => setGuest({ ...guest, email: e.target.value })} required /></label>
                                <label>Telefon<input value={guest.phone} onChange={(e) => setGuest({ ...guest, phone: e.target.value })} /></label>
                                <label className="pms-booking-consent"><input type="checkbox" checked={guest.termsAccepted} onChange={(e) => setGuest({ ...guest, termsAccepted: e.target.checked })} required /> Ich akzeptiere {config?.termsUrl ? <a href={config.termsUrl} target="_blank" rel="noreferrer">die AGB</a> : 'die Buchungsbedingungen'}.</label>
                                <label className="pms-booking-consent"><input type="checkbox" checked={guest.privacyAccepted} onChange={(e) => setGuest({ ...guest, privacyAccepted: e.target.checked })} required /> Ich akzeptiere {config?.privacyUrl ? <a href={config.privacyUrl} target="_blank" rel="noreferrer">die Datenschutzhinweise</a> : 'die Datenschutzhinweise'}.</label>
                                {config?.requireGuarantee && <p className="pms-booking-hint">Das Hotel wird sich zur Garantie oder Anzahlung mit dir in Verbindung setzen.</p>}
                                <button className="is-primary" disabled={busy}>Kostenpflichtig buchen · {new Intl.NumberFormat('de-CH', { style: 'currency', currency: selectedRate.currencyCode }).format(selectedRate.totalAmount)}</button>
                            </form>
                        </section>
                    )}
                </>
            )}
        </main>
    );
};

export default PmsBookingPage;
