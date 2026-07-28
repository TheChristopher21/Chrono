import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';

const errorMessage = (error) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || 'Die Aktion konnte nicht abgeschlossen werden.'
);

const money = (value, currency = 'CHF') => new Intl.NumberFormat('de-CH', {
    style: 'currency',
    currency,
}).format(Number(value ?? 0));

const addDays = (date, days) => {
    const value = new Date(`${date}T12:00:00`);
    value.setDate(value.getDate() + days);
    return value.toISOString().slice(0, 10);
};

const PmsAdvancedWorkspace = ({
    section,
    property,
    operations,
    businessDate,
    canManage,
    onOperationsChange,
}) => {
    const [advanced, setAdvanced] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [organizationForm, setOrganizationForm] = useState({
        type: 'COMPANY', name: '', vatNumber: '', addressLine1: '', postalCode: '', city: '',
        countryCode: 'CH', email: '', phone: '', billingEmail: '', paymentTermsDays: 10,
        notes: '', active: true,
    });
    const [groupForm, setGroupForm] = useState({
        groupCode: '', name: '', contactGuestId: '', organizationId: '',
        arrivalDate: addDays(businessDate, 1), departureDate: addDays(businessDate, 2),
        status: 'CONFIRMED', notes: '',
        rooms: [{ guestId: '', roomTypeId: property?.roomTypes?.[0]?.id ?? '', roomId: '', ratePlanId: '', adults: 1, children: 0 }],
    });
    const [invoiceForm, setInvoiceForm] = useState({
        folioId: '', dueDate: addDays(businessDate, 10), vatRate: '8.10',
        recipientName: '', recipientAddress: '', recipientPostalCode: '', recipientCity: '',
        recipientCountryCode: 'CH', creditorIban: '', qrReference: '',
    });
    const [templateForm, setTemplateForm] = useState({
        code: '', name: '', subject: '', body: '', languageCode: 'de', active: true,
    });
    const [communicationForm, setCommunicationForm] = useState({
        guestId: '', reservationId: '', templateId: '', recipient: '',
    });
    const [inboxForm, setInboxForm] = useState({
        guestId: '', reservationId: '', channel: 'EMAIL', sender: '',
        subject: '', body: '', externalThreadId: '',
    });
    const [replyForm, setReplyForm] = useState({
        guestId: '', reservationId: '', channel: 'EMAIL', recipient: '',
        subject: '', body: '', externalThreadId: '',
    });
    const [channelForm, setChannelForm] = useState({
        providerCode: '',
        displayName: '',
        environment: 'SANDBOX',
        secretReference: '',
        roomTypeId: property?.roomTypes?.[0]?.id ?? '',
        ratePlanId: '',
        externalRoomCode: '',
        externalRateCode: '',
    });
    const [registrationReservationId, setRegistrationReservationId] = useState('');
    const [registrationInvite, setRegistrationInvite] = useState(null);
    const [reportPeriod, setReportPeriod] = useState({
        fromDate: `${businessDate.slice(0, 7)}-01`,
        toDateExclusive: addDays(businessDate, 1),
    });
    const [performanceReport, setPerformanceReport] = useState(null);
    const [portfolio, setPortfolio] = useState(null);
    const [resourceForm, setResourceForm] = useState({
        type: 'CONFERENCE_ROOM', code: '', name: '', location: '',
        capacity: 20, hourlyRate: 0, currencyCode: property?.currencyCode ?? 'CHF', active: true,
    });
    const [resourceBookingForm, setResourceBookingForm] = useState({
        resourceId: '', groupBookingId: '', title: '', organizerName: '',
        startAt: `${businessDate}T09:00`, endAt: `${businessDate}T12:00`,
        attendees: 1, status: 'CONFIRMED', totalAmount: 0, notes: '',
    });

    const loadAdvanced = useCallback(async () => {
        if (!property?.id) return;
        setBusy(true);
        setError('');
        try {
            const response = await api.get('/api/pms/advanced', {
                params: { propertyId: property.id, businessDate },
            });
            setAdvanced(response.data);
        } catch (loadError) {
            setError(errorMessage(loadError));
        } finally {
            setBusy(false);
        }
    }, [businessDate, property?.id]);

    useEffect(() => {
        loadAdvanced();
    }, [loadAdvanced]);

    const loadPerformanceReport = useCallback(async () => {
        if (!property?.id || section !== 'reports') return;
        setBusy(true);
        setError('');
        try {
            const response = await api.get('/api/pms/reports/performance', {
                params: {
                    propertyId: property.id,
                    fromDate: reportPeriod.fromDate,
                    toDateExclusive: reportPeriod.toDateExclusive,
                },
            });
            setPerformanceReport(response.data);
        } catch (loadError) {
            setError(errorMessage(loadError));
        } finally {
            setBusy(false);
        }
    }, [property?.id, reportPeriod.fromDate, reportPeriod.toDateExclusive, section]);

    useEffect(() => {
        loadPerformanceReport();
    }, [loadPerformanceReport]);

    const loadPortfolio = useCallback(async () => {
        if (section !== 'portfolio') return;
        setBusy(true);
        setError('');
        try {
            const response = await api.get('/api/pms/reports/portfolio', {
                params: { businessDate },
            });
            setPortfolio(response.data);
        } catch (loadError) {
            setError(errorMessage(loadError));
        } finally {
            setBusy(false);
        }
    }, [businessDate, section]);

    useEffect(() => {
        loadPortfolio();
    }, [loadPortfolio]);

    const refreshOperations = async () => {
        const response = await api.get('/api/pms/operations', {
            params: { propertyId: property.id, businessDate },
        });
        onOperationsChange(response.data);
    };

    const mutateAdvanced = async (method, url, payload, message, refreshCore = false) => {
        if (!canManage) return false;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const separator = url.includes('?') ? '&' : '?';
            const response = await api[method](`${url}${separator}businessDate=${businessDate}`, payload);
            setAdvanced(response.data);
            if (refreshCore) await refreshOperations();
            setNotice(message);
            return true;
        } catch (mutationError) {
            setError(errorMessage(mutationError));
            return false;
        } finally {
            setBusy(false);
        }
    };

    const guests = operations?.guests ?? [];
    const reservations = operations?.reservations ?? [];
    const rates = operations?.ratePlans ?? [];
    const rooms = operations?.rooms ?? [];
    const folios = operations?.folios ?? [];
    const organizations = advanced?.organizations ?? [];

    const groupRoomChoices = useMemo(() => groupForm.rooms.map((entry) => ({
        rates: rates.filter((rate) => String(rate.roomTypeId) === String(entry.roomTypeId) && rate.active),
        rooms: rooms.filter((room) => String(room.roomTypeId) === String(entry.roomTypeId)),
    })), [groupForm.rooms, rates, rooms]);

    const updateGroupRoom = (index, patch) => {
        setGroupForm((current) => ({
            ...current,
            rooms: current.rooms.map((room, roomIndex) => (
                roomIndex === index ? { ...room, ...patch } : room
            )),
        }));
    };

    const submitOrganization = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/organizations`,
            { ...organizationForm, paymentTermsDays: Number(organizationForm.paymentTermsDays) },
            'Firmenprofil gespeichert.',
        );
        if (saved) setOrganizationForm({
            type: 'COMPANY', name: '', vatNumber: '', addressLine1: '', postalCode: '', city: '',
            countryCode: 'CH', email: '', phone: '', billingEmail: '', paymentTermsDays: 10,
            notes: '', active: true,
        });
    };

    const submitGroup = async (event) => {
        event.preventDefault();
        const payload = {
            ...groupForm,
            propertyId: Number(property.id),
            contactGuestId: Number(groupForm.contactGuestId),
            organizationId: groupForm.organizationId ? Number(groupForm.organizationId) : null,
            rooms: groupForm.rooms.map((entry) => ({
                ...entry,
                guestId: Number(entry.guestId),
                roomTypeId: Number(entry.roomTypeId),
                roomId: entry.roomId ? Number(entry.roomId) : null,
                ratePlanId: Number(entry.ratePlanId),
                adults: Number(entry.adults),
                children: Number(entry.children),
                source: 'DIRECT',
            })),
        };
        const saved = await mutateAdvanced('post', '/api/pms/groups', payload, 'Gruppenbuchung angelegt.', true);
        if (saved) setGroupForm((current) => ({ ...current, groupCode: '', name: '', notes: '' }));
    };

    const submitInvoice = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/invoices`,
            {
                ...invoiceForm,
                folioId: Number(invoiceForm.folioId),
                vatRate: Number(invoiceForm.vatRate),
                creditorIban: invoiceForm.creditorIban || null,
                qrReference: invoiceForm.qrReference || null,
            },
            'Rechnung unveränderlich erstellt.',
        );
        if (saved) setInvoiceForm((current) => ({ ...current, folioId: '', recipientName: '' }));
    };

    const downloadInvoice = async (invoice) => {
        setBusy(true);
        setError('');
        try {
            const response = await api.get(`/api/pms/invoices/${invoice.id}/pdf`, { responseType: 'blob' });
            const url = URL.createObjectURL(response.data);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = `${invoice.invoiceNumber}.pdf`;
            anchor.click();
            URL.revokeObjectURL(url);
        } catch (downloadError) {
            setError(errorMessage(downloadError));
        } finally {
            setBusy(false);
        }
    };

    const correctInvoice = (invoice) => mutateAdvanced(
        'post',
        `/api/pms/properties/${property.id}/invoices/${invoice.id}/correct`,
        { reason: 'Korrektur durch Front Office' },
        `Gutschrift zu ${invoice.invoiceNumber} erstellt.`,
    );

    const closeNightAudit = () => mutateAdvanced(
        'post',
        `/api/pms/properties/${property.id}/night-audits`,
        { businessDate, markPendingArrivalsAsNoShow: true },
        `Betriebstag ${businessDate} abgeschlossen.`,
        true,
    );

    const submitTemplate = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/communication-templates`,
            templateForm,
            'Kommunikationsvorlage gespeichert.',
        );
        if (saved) setTemplateForm({
            code: '', name: '', subject: '', body: '', languageCode: 'de', active: true,
        });
    };

    const queueCommunication = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/communications`,
            {
                guestId: Number(communicationForm.guestId),
                reservationId: communicationForm.reservationId
                    ? Number(communicationForm.reservationId)
                    : null,
                templateId: Number(communicationForm.templateId),
                recipient: communicationForm.recipient || null,
            },
            'Nachricht zur Zustellung vorgemerkt.',
        );
        if (saved) setCommunicationForm({
            guestId: '', reservationId: '', templateId: '', recipient: '',
        });
    };

    const recordInboundMessage = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/inbox/messages`,
            {
                ...inboxForm,
                guestId: Number(inboxForm.guestId),
                reservationId: inboxForm.reservationId ? Number(inboxForm.reservationId) : null,
                externalThreadId: inboxForm.externalThreadId || null,
            },
            'Eingehende Nachricht erfasst.',
        );
        if (saved) setInboxForm({
            guestId: '', reservationId: '', channel: 'EMAIL', sender: '',
            subject: '', body: '', externalThreadId: '',
        });
    };

    const queueInboxReply = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/inbox/replies`,
            {
                ...replyForm,
                guestId: Number(replyForm.guestId),
                reservationId: replyForm.reservationId ? Number(replyForm.reservationId) : null,
                externalThreadId: replyForm.externalThreadId || null,
            },
            'Antwort in die lokale Zustellqueue gelegt.',
        );
        if (saved) setReplyForm({
            guestId: '', reservationId: '', channel: 'EMAIL', recipient: '',
            subject: '', body: '', externalThreadId: '',
        });
    };

    const markMessageRead = (messageId) => mutateAdvanced(
        'post',
        `/api/pms/properties/${property.id}/inbox/messages/${messageId}/read`,
        undefined,
        'Nachricht als gelesen markiert.',
    );

    const submitReportPeriod = (event) => {
        event.preventDefault();
        loadPerformanceReport();
    };

    const createHotelResource = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/resources`,
            {
                ...resourceForm,
                capacity: Number(resourceForm.capacity),
                hourlyRate: Number(resourceForm.hourlyRate),
            },
            'Hotelressource angelegt.',
        );
        if (saved) setResourceForm({
            type: 'CONFERENCE_ROOM', code: '', name: '', location: '',
            capacity: 20, hourlyRate: 0, currencyCode: property?.currencyCode ?? 'CHF', active: true,
        });
    };

    const createResourceBooking = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/resource-bookings`,
            {
                ...resourceBookingForm,
                resourceId: Number(resourceBookingForm.resourceId),
                groupBookingId: resourceBookingForm.groupBookingId
                    ? Number(resourceBookingForm.groupBookingId)
                    : null,
                attendees: Number(resourceBookingForm.attendees),
                totalAmount: Number(resourceBookingForm.totalAmount),
                notes: resourceBookingForm.notes || null,
            },
            'Ressource konfliktfrei gebucht.',
        );
        if (saved) setResourceBookingForm({
            resourceId: '', groupBookingId: '', title: '', organizerName: '',
            startAt: `${businessDate}T09:00`, endAt: `${businessDate}T12:00`,
            attendees: 1, status: 'CONFIRMED', totalAmount: 0, notes: '',
        });
    };

    const prepareReply = (entry) => {
        setReplyForm({
            guestId: String(entry.guestId),
            reservationId: entry.reservationId ? String(entry.reservationId) : '',
            channel: entry.channel,
            recipient: entry.sender || '',
            subject: entry.subject?.startsWith('Re:') ? entry.subject : `Re: ${entry.subject || 'Nachricht'}`,
            body: '',
            externalThreadId: entry.externalThreadId || '',
        });
    };

    const createChannelConnection = async (event) => {
        event.preventDefault();
        const saved = await mutateAdvanced(
            'post',
            `/api/pms/properties/${property.id}/channel-connections`,
            {
                providerCode: channelForm.providerCode,
                displayName: channelForm.displayName,
                environment: channelForm.environment,
                secretReference: channelForm.environment === 'LIVE' ? channelForm.secretReference : null,
                mappings: [{
                    roomTypeId: Number(channelForm.roomTypeId),
                    ratePlanId: Number(channelForm.ratePlanId),
                    externalRoomCode: channelForm.externalRoomCode,
                    externalRateCode: channelForm.externalRateCode,
                }],
            },
            channelForm.environment === 'LIVE' ? 'Live-Channel eingerichtet.' : 'Sandbox-Channel eingerichtet.',
        );
        if (saved) setChannelForm((current) => ({
            ...current, providerCode: '', displayName: '', secretReference: '', externalRoomCode: '', externalRateCode: '',
        }));
    };

    const issueRegistrationInvite = async (event) => {
        event.preventDefault();
        if (!canManage) return;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const response = await api.post(
                `/api/pms/properties/${property.id}/reservations/${registrationReservationId}/guest-registration/invite`,
            );
            setRegistrationInvite(response.data);
            setNotice('Sicherer Check-in-Link erstellt.');
            await loadAdvanced();
        } catch (inviteError) {
            setError(errorMessage(inviteError));
        } finally {
            setBusy(false);
        }
    };

    if (busy && !advanced) return <section className="pms-work-card"><p>PMS-Daten werden geladen…</p></section>;

    return (
        <>
            {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
            {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}

            {section === 'portfolio' && portfolio && (
                <div className="pms-operations-stack">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Multi-Property Control</span><h3>Portfolio am {portfolio.businessDate}</h3></div><button type="button" onClick={loadPortfolio} disabled={busy}>Aktualisieren</button></div>
                        <div className="pms-metrics">
                            <article><span>Hotelbetriebe</span><strong>{portfolio.properties}</strong><small>Zentral sichtbar</small></article>
                            <article><span>Operative Zimmer</span><strong>{portfolio.operationalRooms}</strong><small>{portfolio.availableRooms} heute verfügbar</small></article>
                            <article><span>Portfolio-Belegung</span><strong>{Number(portfolio.occupancyPercent).toFixed(2)} %</strong><small>{portfolio.soldRooms} verkaufte Zimmer</small></article>
                            <article><span>Anreisen</span><strong>{portfolio.arrivals}</strong><small>{portfolio.departures} Abreisen</small></article>
                        </div>
                        <p><small>Finanzwerte bleiben absichtlich pro Hotel und Währung getrennt. Die Portfolio-Belegung wird nur aus Zimmermengen berechnet.</small></p>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Hotelvergleich</span><h3>Betriebe</h3></div></div>
                        <div className="pms-table-wrap">
                            <table>
                                <thead><tr><th>Hotel</th><th>Ort</th><th>Währung</th><th>Zimmer</th><th>Verfügbar</th><th>Verkauft</th><th>Belegung</th><th>An-/Abreisen</th></tr></thead>
                                <tbody>{portfolio.hotels.map((hotel) => <tr key={hotel.propertyId}><td><strong>{hotel.name}</strong><small>{hotel.code}</small></td><td>{hotel.city}</td><td>{hotel.currencyCode}</td><td>{hotel.operationalRooms}</td><td>{hotel.availableRooms}</td><td>{hotel.soldRooms}</td><td>{Number(hotel.occupancyPercent).toFixed(2)} %</td><td>{hotel.arrivals} / {hotel.departures}</td></tr>)}</tbody>
                            </table>
                        </div>
                    </section>
                </div>
            )}

            {section === 'events' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">MICE Inventory</span><h3>Hotelressource</h3></div></div>
                        <form className="pms-form-grid" onSubmit={createHotelResource}>
                            <label>Typ<select aria-label="Ressourcentyp" value={resourceForm.type} onChange={(event) => setResourceForm({ ...resourceForm, type: event.target.value })}>{['CONFERENCE_ROOM', 'PARKING', 'SPA', 'RESTAURANT', 'BAR', 'EQUIPMENT', 'OTHER'].map((type) => <option key={type}>{type}</option>)}</select></label>
                            <label>Code<input aria-label="Ressourcencode" value={resourceForm.code} onChange={(event) => setResourceForm({ ...resourceForm, code: event.target.value })} required /></label>
                            <label>Name<input aria-label="Ressourcenname" value={resourceForm.name} onChange={(event) => setResourceForm({ ...resourceForm, name: event.target.value })} required /></label>
                            <label>Ort<input value={resourceForm.location} onChange={(event) => setResourceForm({ ...resourceForm, location: event.target.value })} /></label>
                            <label>Kapazität<input type="number" min="1" value={resourceForm.capacity} onChange={(event) => setResourceForm({ ...resourceForm, capacity: event.target.value })} required /></label>
                            <label>Stundensatz<input type="number" min="0" step="0.01" value={resourceForm.hourlyRate} onChange={(event) => setResourceForm({ ...resourceForm, hourlyRate: event.target.value })} required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Ressource anlegen</button></div>
                        </form>
                        <hr />
                        <h4>Ressource buchen</h4>
                        <form className="pms-form-grid" onSubmit={createResourceBooking}>
                            <label>Ressource<select aria-label="Buchungsressource" value={resourceBookingForm.resourceId} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, resourceId: event.target.value })} required><option value="">Ressource wählen</option>{advanced?.hotelResources?.filter((resource) => resource.active).map((resource) => <option key={resource.id} value={resource.id}>{resource.name} · max. {resource.capacity}</option>)}</select></label>
                            <label>Gruppe (optional)<select value={resourceBookingForm.groupBookingId} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, groupBookingId: event.target.value })}><option value="">Ohne Gruppe</option>{advanced?.groups?.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}</select></label>
                            <label>Titel<input aria-label="Eventtitel" value={resourceBookingForm.title} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, title: event.target.value })} required /></label>
                            <label>Veranstalter<input aria-label="Veranstalter" value={resourceBookingForm.organizerName} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, organizerName: event.target.value })} required /></label>
                            <label>Beginn<input aria-label="Eventbeginn" type="datetime-local" value={resourceBookingForm.startAt} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, startAt: event.target.value })} required /></label>
                            <label>Ende<input aria-label="Eventende" type="datetime-local" value={resourceBookingForm.endAt} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, endAt: event.target.value })} required /></label>
                            <label>Teilnehmende<input type="number" min="1" value={resourceBookingForm.attendees} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, attendees: event.target.value })} required /></label>
                            <label>Gesamtbetrag<input type="number" min="0" step="0.01" value={resourceBookingForm.totalAmount} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, totalAmount: event.target.value })} required /></label>
                            <label className="is-wide">Notizen<textarea value={resourceBookingForm.notes} onChange={(event) => setResourceBookingForm({ ...resourceBookingForm, notes: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Konfliktfrei buchen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Eventplan</span><h3>{advanced?.resourceBookings?.length ?? 0} Buchungen</h3></div></div>
                        <div className="pms-record-list">{advanced?.resourceBookings?.map((booking) => <article className="pms-record" key={booking.id}><div><span>{booking.status} · {booking.startAt} bis {booking.endAt}</span><strong>{booking.title}</strong><small>{booking.resourceName} · {booking.attendees} Personen · {money(booking.totalAmount)}</small></div>{booking.status !== 'CANCELLED' && <button type="button" onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/resource-bookings/${booking.id}/cancel`, undefined, 'Ressourcenbuchung storniert.')} disabled={!canManage || busy}>Stornieren</button>}</article>)}</div>
                    </section>
                </div>
            )}

            {section === 'groups' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Sales</span><h3>Gruppenbuchung</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitGroup}>
                            <label>Gruppencode<input value={groupForm.groupCode} onChange={(event) => setGroupForm({ ...groupForm, groupCode: event.target.value })} required /></label>
                            <label>Name<input value={groupForm.name} onChange={(event) => setGroupForm({ ...groupForm, name: event.target.value })} required /></label>
                            <label>Kontakt<select value={groupForm.contactGuestId} onChange={(event) => setGroupForm({ ...groupForm, contactGuestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Firma / Reisebüro<select value={groupForm.organizationId} onChange={(event) => setGroupForm({ ...groupForm, organizationId: event.target.value })}><option value="">Privat / keine</option>{organizations.filter((entry) => entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select></label>
                            <label>Anreise<input type="date" value={groupForm.arrivalDate} onChange={(event) => setGroupForm({ ...groupForm, arrivalDate: event.target.value })} required /></label>
                            <label>Abreise<input type="date" value={groupForm.departureDate} onChange={(event) => setGroupForm({ ...groupForm, departureDate: event.target.value })} required /></label>
                            <label>Status<select value={groupForm.status} onChange={(event) => setGroupForm({ ...groupForm, status: event.target.value })}><option value="CONFIRMED">Bestätigt</option><option value="OPTION">Option</option></select></label>
                            <label className="is-wide">Notizen<textarea value={groupForm.notes} onChange={(event) => setGroupForm({ ...groupForm, notes: event.target.value })} /></label>
                            <div className="is-wide pms-rooming-list">
                                <div className="pms-work-card-heading">
                                    <h4>Rooming List</h4>
                                    <button type="button" onClick={() => setGroupForm((current) => ({
                                        ...current,
                                        rooms: [...current.rooms, { guestId: '', roomTypeId: property.roomTypes?.[0]?.id ?? '', roomId: '', ratePlanId: '', adults: 1, children: 0 }],
                                    }))}>Zimmer hinzufügen</button>
                                </div>
                                {groupForm.rooms.map((entry, index) => (
                                    <div className="pms-rooming-row" key={`rooming-${index}`}>
                                        <select aria-label={`Gast Zimmer ${index + 1}`} value={entry.guestId} onChange={(event) => updateGroupRoom(index, { guestId: event.target.value })} required><option value="">Gast</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select>
                                        <select aria-label={`Zimmertyp Zimmer ${index + 1}`} value={entry.roomTypeId} onChange={(event) => updateGroupRoom(index, { roomTypeId: event.target.value, roomId: '', ratePlanId: '' })} required>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select>
                                        <select aria-label={`Rate Zimmer ${index + 1}`} value={entry.ratePlanId} onChange={(event) => updateGroupRoom(index, { ratePlanId: event.target.value })} required><option value="">Rate</option>{groupRoomChoices[index]?.rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select>
                                        <select aria-label={`Zimmernummer ${index + 1}`} value={entry.roomId} onChange={(event) => updateGroupRoom(index, { roomId: event.target.value })}><option value="">Automatisch</option>{groupRoomChoices[index]?.rooms.map((room) => <option key={room.id} value={room.id}>{room.number}</option>)}</select>
                                        {groupForm.rooms.length > 1 && <button type="button" aria-label={`Zimmer ${index + 1} entfernen`} onClick={() => setGroupForm((current) => ({ ...current, rooms: current.rooms.filter((_, roomIndex) => roomIndex !== index) }))}>×</button>}
                                    </div>
                                ))}
                            </div>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Gruppe verbindlich anlegen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gruppen</span><h3>{advanced?.groups?.length ?? 0} Buchungen</h3></div></div>
                        <div className="pms-record-list">
                            {advanced?.groups?.map((group) => <article className="pms-record is-stacked" key={group.id}><div><span>{group.groupCode} · {group.status}</span><strong>{group.name}</strong><small>{group.arrivalDate} bis {group.departureDate} · {group.rooms.length} Zimmer · {group.organizationName || 'Privat'}</small></div><ul>{group.rooms.map((entry) => <li key={entry.reservationId}>{entry.guestName} · {entry.roomNumber ? `Zimmer ${entry.roomNumber}` : entry.roomTypeName} · {entry.confirmationCode}</li>)}</ul></article>)}
                        </div>
                    </section>
                </div>
            )}

            {section === 'organizations' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">CRM</span><h3>Firma oder Reisebüro</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitOrganization}>
                            <label>Typ<select value={organizationForm.type} onChange={(event) => setOrganizationForm({ ...organizationForm, type: event.target.value })}><option value="COMPANY">Firma</option><option value="TRAVEL_AGENCY">Reisebüro</option><option value="EVENT_ORGANIZER">Veranstalter</option></select></label>
                            <label>Name<input value={organizationForm.name} onChange={(event) => setOrganizationForm({ ...organizationForm, name: event.target.value })} required /></label>
                            <label>UID / MWST<input value={organizationForm.vatNumber} onChange={(event) => setOrganizationForm({ ...organizationForm, vatNumber: event.target.value })} /></label>
                            <label>Zahlungsziel<input type="number" min="0" value={organizationForm.paymentTermsDays} onChange={(event) => setOrganizationForm({ ...organizationForm, paymentTermsDays: event.target.value })} /></label>
                            <label className="is-wide">Adresse<input value={organizationForm.addressLine1} onChange={(event) => setOrganizationForm({ ...organizationForm, addressLine1: event.target.value })} /></label>
                            <label>PLZ<input value={organizationForm.postalCode} onChange={(event) => setOrganizationForm({ ...organizationForm, postalCode: event.target.value })} /></label>
                            <label>Ort<input value={organizationForm.city} onChange={(event) => setOrganizationForm({ ...organizationForm, city: event.target.value })} /></label>
                            <label>E-Mail<input type="email" value={organizationForm.email} onChange={(event) => setOrganizationForm({ ...organizationForm, email: event.target.value })} /></label>
                            <label>Rechnungs-E-Mail<input type="email" value={organizationForm.billingEmail} onChange={(event) => setOrganizationForm({ ...organizationForm, billingEmail: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Profil speichern</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Geschäftspartner</span><h3>{organizations.length} Profile</h3></div></div>
                        <div className="pms-record-list">{organizations.map((entry) => <article className="pms-record" key={entry.id}><div><span>{entry.type}</span><strong>{entry.name}</strong><small>{entry.billingEmail || entry.email || 'Keine E-Mail'} · {entry.paymentTermsDays} Tage Zahlungsziel</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'invoices' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Debitoren</span><h3>Rechnung erstellen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitInvoice}>
                            <label className="is-wide">Folio<select value={invoiceForm.folioId} onChange={(event) => { const folio = folios.find((entry) => String(entry.id) === event.target.value); setInvoiceForm({ ...invoiceForm, folioId: event.target.value, recipientName: folio?.organizationName || folio?.guestName || '' }); }} required><option value="">Folio wählen</option>{folios.filter((folio) => Number(folio.charges) > 0).map((folio) => <option key={folio.id} value={folio.id}>{folio.label} · {folio.confirmationCode} · {folio.guestName}</option>)}</select></label>
                            <label>Empfänger<input value={invoiceForm.recipientName} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientName: event.target.value })} required /></label>
                            <label>Fällig<input type="date" value={invoiceForm.dueDate} onChange={(event) => setInvoiceForm({ ...invoiceForm, dueDate: event.target.value })} required /></label>
                            <label>MWST %<input type="number" min="0" max="100" step="0.01" value={invoiceForm.vatRate} onChange={(event) => setInvoiceForm({ ...invoiceForm, vatRate: event.target.value })} required /></label>
                            <label>IBAN<input value={invoiceForm.creditorIban} onChange={(event) => setInvoiceForm({ ...invoiceForm, creditorIban: event.target.value })} placeholder="optional für Swiss QR" /></label>
                            <label className="is-wide">Adresse<input value={invoiceForm.recipientAddress} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientAddress: event.target.value })} /></label>
                            <label>PLZ<input value={invoiceForm.recipientPostalCode} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientPostalCode: event.target.value })} /></label>
                            <label>Ort<input value={invoiceForm.recipientCity} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientCity: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Rechnung ausstellen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Rechnungsjournal</span><h3>{advanced?.invoices?.length ?? 0} Rechnungen</h3></div></div>
                        <div className="pms-record-list">{advanced?.invoices?.map((invoice) => <article className="pms-record" key={invoice.id}><div><span>{invoice.invoiceNumber} · {invoice.type === 'CREDIT_NOTE' ? 'Gutschrift' : 'Rechnung'} · {invoice.status}</span><strong>{invoice.recipientName}</strong><small>Netto {money(invoice.netAmount, invoice.currencyCode)} · MWST {money(invoice.vatAmount, invoice.currencyCode)} · Total {money(invoice.grossAmount, invoice.currencyCode)}</small>{invoice.originalInvoiceNumber && <small>Korrektur zu {invoice.originalInvoiceNumber} · {invoice.correctionReason}</small>}</div><div className="pms-record-actions"><button type="button" onClick={() => downloadInvoice(invoice)}>PDF</button>{invoice.type === 'INVOICE' && invoice.status === 'ISSUED' && <button type="button" className="is-danger" disabled={!canManage || busy} onClick={() => correctInvoice(invoice)}>Gutschrift</button>}</div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'audit' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Night Audit</span><h3>Betriebstag {businessDate}</h3></div></div>
                        <p>Der Abschluss speichert Anreisen, Abreisen, In-House, No-shows und den offenen Saldo als unveränderlichen Tages-Snapshot. Offene bestätigte Anreisen werden als No-show abgeschlossen.</p>
                        <button type="button" className="is-primary" onClick={closeNightAudit} disabled={!canManage || busy || advanced?.nightAudits?.some((entry) => entry.businessDate === businessDate)}>Betriebstag abschließen</button>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Abschlussjournal</span><h3>{advanced?.nightAudits?.length ?? 0} Abschlüsse</h3></div></div>
                        <div className="pms-record-list">{advanced?.nightAudits?.map((audit) => <article className="pms-record" key={audit.id}><div><span>{audit.businessDate} · {audit.closedBy}</span><strong>{audit.inHouseCount} In-House · {audit.noShowCount} No-shows</strong><small>{audit.arrivalsCount} Anreisen · {audit.departuresCount} Abreisen · Offen {money(audit.openBalance)}</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'communications' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Guest Journey</span><h3>Vorlage</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitTemplate}>
                            <label>Code<input value={templateForm.code} onChange={(event) => setTemplateForm({ ...templateForm, code: event.target.value })} required /></label>
                            <label>Name<input value={templateForm.name} onChange={(event) => setTemplateForm({ ...templateForm, name: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input value={templateForm.subject} onChange={(event) => setTemplateForm({ ...templateForm, subject: event.target.value })} required /></label>
                            <label className="is-wide">Text<textarea value={templateForm.body} onChange={(event) => setTemplateForm({ ...templateForm, body: event.target.value })} placeholder="{{guestName}}, {{hotelName}}, {{arrivalDate}}, {{departureDate}}, {{confirmationCode}}" required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Vorlage speichern</button></div>
                        </form>
                        <hr />
                        <h4>Nachricht vormerken</h4>
                        <form className="pms-form-grid" onSubmit={queueCommunication}>
                            <label>Gast<select value={communicationForm.guestId} onChange={(event) => setCommunicationForm({ ...communicationForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select value={communicationForm.reservationId} onChange={(event) => setCommunicationForm({ ...communicationForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(communicationForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Vorlage<select value={communicationForm.templateId} onChange={(event) => setCommunicationForm({ ...communicationForm, templateId: event.target.value })} required><option value="">Vorlage wählen</option>{advanced?.communicationTemplates?.filter((entry) => entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select></label>
                            <label>E-Mail (optional)<input type="email" value={communicationForm.recipient} onChange={(event) => setCommunicationForm({ ...communicationForm, recipient: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>In Versandqueue legen</button></div>
                        </form>
                        <hr />
                        <h4>Eingang über lokalen Adapter erfassen</h4>
                        <p>Dieser Adapter bildet eingehende E-Mail-, SMS-, WhatsApp- und OTA-Nachrichten lokal ab. Echte Provider-Zugänge werden später an dieselbe Schnittstelle angeschlossen.</p>
                        <form className="pms-form-grid" onSubmit={recordInboundMessage}>
                            <label>Gast<select aria-label="Posteingang Gast" value={inboxForm.guestId} onChange={(event) => setInboxForm({ ...inboxForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select aria-label="Posteingang Reservierung" value={inboxForm.reservationId} onChange={(event) => setInboxForm({ ...inboxForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(inboxForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Kanal<select aria-label="Posteingang Kanal" value={inboxForm.channel} onChange={(event) => setInboxForm({ ...inboxForm, channel: event.target.value })}>{['EMAIL', 'SMS', 'WHATSAPP', 'OTA', 'PORTAL'].map((channel) => <option key={channel}>{channel}</option>)}</select></label>
                            <label>Absender<input aria-label="Posteingang Absender" value={inboxForm.sender} onChange={(event) => setInboxForm({ ...inboxForm, sender: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input aria-label="Posteingang Betreff" value={inboxForm.subject} onChange={(event) => setInboxForm({ ...inboxForm, subject: event.target.value })} /></label>
                            <label className="is-wide">Nachricht<textarea aria-label="Posteingang Nachricht" value={inboxForm.body} onChange={(event) => setInboxForm({ ...inboxForm, body: event.target.value })} required /></label>
                            <label className="is-wide">Externe Thread-ID (optional)<input aria-label="Posteingang Thread-ID" value={inboxForm.externalThreadId} onChange={(event) => setInboxForm({ ...inboxForm, externalThreadId: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Eingang erfassen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Unified Inbox</span><h3>{advanced?.communications?.length ?? 0} Nachrichten</h3></div></div>
                        <div className="pms-record-list">
                            {advanced?.communications?.map((entry) => (
                                <article className="pms-record" key={entry.id}>
                                    <div>
                                        <span>{entry.direction === 'INBOUND' ? 'Eingang' : 'Ausgang'} · {entry.channel ?? 'EMAIL'} · {entry.status}</span>
                                        <strong>{entry.subject}</strong>
                                        <small>{entry.guestName} · {entry.direction === 'INBOUND' ? entry.sender : entry.recipient}</small>
                                        <p>{entry.body}</p>
                                    </div>
                                    <div className="pms-record-actions">
                                        {entry.direction === 'INBOUND' && !entry.readAt && <button type="button" onClick={() => markMessageRead(entry.id)} disabled={!canManage || busy}>Gelesen</button>}
                                        {entry.direction === 'INBOUND' && <button type="button" onClick={() => prepareReply(entry)}>Antworten</button>}
                                    </div>
                                </article>
                            ))}
                        </div>
                        <hr />
                        <h4>Freie Antwort</h4>
                        <form className="pms-form-grid" onSubmit={queueInboxReply}>
                            <label>Gast<select aria-label="Antwort Gast" value={replyForm.guestId} onChange={(event) => setReplyForm({ ...replyForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select aria-label="Antwort Reservierung" value={replyForm.reservationId} onChange={(event) => setReplyForm({ ...replyForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(replyForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Kanal<select aria-label="Antwort Kanal" value={replyForm.channel} onChange={(event) => setReplyForm({ ...replyForm, channel: event.target.value })}>{['EMAIL', 'SMS', 'WHATSAPP', 'OTA', 'PORTAL'].map((channel) => <option key={channel}>{channel}</option>)}</select></label>
                            <label>Empfänger<input aria-label="Antwort Empfänger" value={replyForm.recipient} onChange={(event) => setReplyForm({ ...replyForm, recipient: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input aria-label="Antwort Betreff" value={replyForm.subject} onChange={(event) => setReplyForm({ ...replyForm, subject: event.target.value })} /></label>
                            <label className="is-wide">Antwort<textarea aria-label="Antwort Nachricht" value={replyForm.body} onChange={(event) => setReplyForm({ ...replyForm, body: event.target.value })} required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Antwort vormerken</button></div>
                        </form>
                    </section>
                </div>
            )}

            {section === 'digital-check-in' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Guest Journey</span><h3>Digitalen Meldeschein senden</h3></div></div>
                        <p>Der Link ist sieben Tage gültig. Die vollständige Dokumentnummer wird ausschließlich gehasht gespeichert.</p>
                        <form className="pms-form-grid" onSubmit={issueRegistrationInvite}>
                            <label className="is-wide">Reservierung<select value={registrationReservationId} onChange={(event) => { setRegistrationReservationId(event.target.value); setRegistrationInvite(null); }} required><option value="">Reservierung wählen</option>{reservations.filter((entry) => !['CANCELLED', 'NO_SHOW', 'CHECKED_OUT'].includes(entry.status)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode} · {entry.guestName} · {entry.arrivalDate}</option>)}</select></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Sicheren Link erstellen</button></div>
                        </form>
                        {registrationInvite && (
                            <div className="pms-inline-message is-success">
                                <strong>Link bis {new Date(registrationInvite.expiresAt).toLocaleString('de-CH')}</strong>
                                <p>{`${window.location.origin}${registrationInvite.portalPath}`}</p>
                                <button type="button" onClick={() => navigator.clipboard?.writeText(`${window.location.origin}${registrationInvite.portalPath}`)}>Link kopieren</button>
                            </div>
                        )}
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Meldescheine</span><h3>{advanced?.guestRegistrations?.length ?? 0} Vorgänge</h3></div></div>
                        <div className="pms-record-list">{advanced?.guestRegistrations?.map((registration) => <article className="pms-record" key={registration.id}><div><span>{registration.status} · {registration.confirmationCode}</span><strong>{registration.guestName}</strong><small>{registration.status === 'COMPLETED' ? `${registration.city}, ${registration.countryCode} · Dokument ••••${registration.documentLastFour}` : 'Link ausstehend'}</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'reports' && (
                <div className="pms-operations-stack">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading">
                            <div><span className="pms-eyebrow">Revenue & Performance</span><h3>Belegung, ADR und RevPAR</h3></div>
                        </div>
                        <form className="pms-form-grid" onSubmit={submitReportPeriod}>
                            <label>Von (inklusive)<input aria-label="Bericht von" type="date" value={reportPeriod.fromDate} onChange={(event) => setReportPeriod({ ...reportPeriod, fromDate: event.target.value })} required /></label>
                            <label>Bis (exklusive)<input aria-label="Bericht bis" type="date" value={reportPeriod.toDateExclusive} onChange={(event) => setReportPeriod({ ...reportPeriod, toDateExclusive: event.target.value })} required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Bericht aktualisieren</button></div>
                        </form>
                        {performanceReport && (
                            <>
                                <div className="pms-metrics">
                                    <article><span>Belegung</span><strong>{Number(performanceReport.occupancyPercent).toFixed(2)} %</strong><small>{performanceReport.soldRoomNights} von {performanceReport.availableRoomNights} Zimmernächten</small></article>
                                    <article><span>Zimmerumsatz</span><strong>{money(performanceReport.roomRevenue, performanceReport.currencyCode)}</strong><small>Auf Aufenthaltsnächte verteilt</small></article>
                                    <article><span>ADR</span><strong>{money(performanceReport.adr, performanceReport.currencyCode)}</strong><small>Umsatz je verkaufter Zimmernacht</small></article>
                                    <article><span>RevPAR</span><strong>{money(performanceReport.revPar, performanceReport.currencyCode)}</strong><small>Umsatz je verfügbarer Zimmernacht</small></article>
                                    <article><span>Anreisen</span><strong>{performanceReport.arrivals}</strong><small>{performanceReport.cancellations} Stornos · {performanceReport.noShows} No-shows</small></article>
                                </div>
                                <p><small>{performanceReport.methodology}</small></p>
                            </>
                        )}
                    </section>
                    {performanceReport && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Tageswerte</span><h3>{performanceReport.daily.length} Betriebstage</h3></div></div>
                                <div className="pms-table-wrap">
                                    <table>
                                        <thead><tr><th>Datum</th><th>Verfügbar</th><th>Verkauft</th><th>Belegung</th><th>Umsatz</th><th>ADR</th><th>RevPAR</th></tr></thead>
                                        <tbody>{performanceReport.daily.map((day) => <tr key={day.date}><td>{day.date}</td><td>{day.availableRooms}</td><td>{day.soldRooms}</td><td>{Number(day.occupancyPercent).toFixed(2)} %</td><td>{money(day.roomRevenue, performanceReport.currencyCode)}</td><td>{money(day.adr, performanceReport.currencyCode)}</td><td>{money(day.revPar, performanceReport.currencyCode)}</td></tr>)}</tbody>
                                    </table>
                                </div>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Quellenmix</span><h3>Buchungskanäle</h3></div></div>
                                <div className="pms-record-list">{performanceReport.sources.map((source) => <article className="pms-record" key={source.source}><div><span>{Number(source.sharePercent).toFixed(2)} %</span><strong>{source.source}</strong><small>{source.soldRoomNights} Zimmernächte · {money(source.roomRevenue, performanceReport.currencyCode)}</small></div></article>)}</div>
                            </section>
                        </div>
                    )}
                </div>
            )}

            {section === 'integrations' && (
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Integration Control Center</span><h3>Provider-neutrale Ereignisqueue</h3></div><button type="button" onClick={loadAdvanced} disabled={busy}>Aktualisieren</button></div>
                    <p>Chrono schreibt Reservierungs-, Rechnungs-, Kommunikations- und Tagesabschlussereignisse in eine transaktionale Outbox. Sandbox-Verbindungen bleiben lokal; Live-Verbindungen werden signiert und mit stabilen Idempotenzschlüsseln an das konfigurierte Provider-Gateway zugestellt.</p>
                    <form className="pms-form-grid" onSubmit={createChannelConnection}>
                        <label>Provider-Code<input value={channelForm.providerCode} onChange={(event) => setChannelForm({ ...channelForm, providerCode: event.target.value })} required /></label>
                        <label>Name<input value={channelForm.displayName} onChange={(event) => setChannelForm({ ...channelForm, displayName: event.target.value })} required /></label>
                        <label>Umgebung<select value={channelForm.environment} onChange={(event) => setChannelForm({ ...channelForm, environment: event.target.value })}><option value="SANDBOX">Sandbox</option><option value="LIVE">Live</option></select></label>
                        {channelForm.environment === 'LIVE' && <label>Secret-Referenz<input value={channelForm.secretReference} onChange={(event) => setChannelForm({ ...channelForm, secretReference: event.target.value })} placeholder="env:CHANNEL_PROVIDER_SECRET" pattern="env:[A-Z][A-Z0-9_]{2,100}" required /></label>}
                        <label>Zimmertyp<select value={channelForm.roomTypeId} onChange={(event) => setChannelForm({ ...channelForm, roomTypeId: event.target.value, ratePlanId: '' })}>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
                        <label>Rate<select value={channelForm.ratePlanId} onChange={(event) => setChannelForm({ ...channelForm, ratePlanId: event.target.value })} required><option value="">Rate wählen</option>{rates.filter((rate) => String(rate.roomTypeId) === String(channelForm.roomTypeId)).map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
                        <label>Externer Zimmercode<input value={channelForm.externalRoomCode} onChange={(event) => setChannelForm({ ...channelForm, externalRoomCode: event.target.value })} required /></label>
                        <label>Externer Ratencode<input value={channelForm.externalRateCode} onChange={(event) => setChannelForm({ ...channelForm, externalRateCode: event.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>{channelForm.environment === 'LIVE' ? 'Live-Verbindung anlegen' : 'Sandbox-Verbindung anlegen'}</button></div>
                    </form>
                    <div className="pms-record-list">{advanced?.channelConnections?.map((connection) => <article className="pms-record" key={connection.id}><div><span>{connection.environment} · {connection.status}</span><strong>{connection.displayName}</strong><small>{connection.mappings.length} Mapping(s) · {connection.lastSyncMessage || 'Noch kein Snapshot'}</small></div><button type="button" onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/channel-connections/${connection.id}/sync`, undefined, connection.environment === 'LIVE' ? 'Live-Snapshot eingeplant.' : 'Sandbox-Snapshot erstellt.')} disabled={!canManage || busy}>{connection.environment === 'LIVE' ? 'Live synchronisieren' : 'Sync testen'}</button></article>)}</div>
                    <div className="pms-record-list">{advanced?.integrationOutbox?.map((event) => <article className="pms-record" key={event.id}><div><span>{event.status} · {event.aggregateType} #{event.aggregateId}</span><strong>{event.eventType}</strong><small>{event.createdAt} · Versuch {event.attemptCount}{event.lastError ? ` · ${event.lastError}` : ''}</small></div><div className="pms-inline-actions">{event.status === 'PENDING' && <button type="button" disabled={!canManage || busy} onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/integration-outbox/${event.id}/acknowledge`, undefined, 'Ereignis als zugestellt bestätigt.')}>Bestätigen</button>}{['FAILED', 'DEAD_LETTER'].includes(event.status) && <button type="button" disabled={!canManage || busy} onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/integration-outbox/${event.id}/retry`, undefined, 'Ereignis erneut eingeplant.')}>Erneut versuchen</button>}</div></article>)}</div>
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Revisionsspur</span><h3>Unveränderliches PMS-Audit</h3></div></div>
                    <div className="pms-record-list">{advanced?.auditEvents?.map((event) => <article className="pms-record" key={event.id}><div><span>{event.createdAt} · {event.actor}</span><strong>{event.eventType}</strong><small>{event.aggregateType} #{event.aggregateId} · Hash {event.integrityHash?.slice(0, 12)}…</small></div></article>)}</div>
                </section>
            )}
        </>
    );
};

export default PmsAdvancedWorkspace;
