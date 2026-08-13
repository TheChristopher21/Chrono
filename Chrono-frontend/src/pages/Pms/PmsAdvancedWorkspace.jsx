import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';
import { formatPmsDate, formatPmsDateTime } from './pmsFormatting.js';
import { PmsTranslationBoundary, usePmsLocale } from './pmsI18n.jsx';
import {
    CHANNEL_CONNECTION_STATUS_LABELS,
    CHANNEL_ENVIRONMENT_LABELS,
    COMMUNICATION_CHANNEL_LABELS,
    COMMUNICATION_DIRECTION_LABELS,
    COMMUNICATION_STATUS_LABELS,
    GROUP_BOOKING_STATUS_LABELS,
    GUEST_REGISTRATION_STATUS_LABELS,
    HOTEL_RESOURCE_TYPE_LABELS,
    INVOICE_STATUS_LABELS,
    INVOICE_TYPE_LABELS,
    ORGANIZATION_TYPE_LABELS,
    OUTBOX_STATUS_LABELS,
    RESERVATION_SOURCE_LABELS,
    RESOURCE_BOOKING_STATUS_LABELS,
    getFolioDisplayLabel,
    getPmsEnumLabel,
    getPmsEnumOptions,
} from './pmsTerminology.js';

const eventTypeLabels = Object.freeze({
    'booking.imported': 'Externe Reservierung importiert',
    'channel.connection_created': 'Schnittstelle eingerichtet',
    'channel.inventory_snapshot_ready': 'Bestandsabgleich vorbereitet',
    'communication.queued': 'Nachricht zum Versand eingeplant',
    'communication.received': 'Nachricht empfangen',
    'communication.reply_queued': 'Antwort zum Versand eingeplant',
    'group_booking.created': 'Gruppenreservierung angelegt',
    'guest.registration_completed': 'Meldeschein übermittelt',
    'guest.registration_invited': 'Link zur Gästeanmeldung erstellt',
    'integration.outbox_acknowledged': 'Übertragung bestätigt',
    'integration.outbox_retried': 'Übertragung erneut eingeplant',
    'invoice.credited': 'Rechnung gutgeschrieben',
    'invoice.issued': 'Rechnung ausgestellt',
    'maintenance.created': 'Wartungsauftrag angelegt',
    'maintenance.resolved': 'Wartungsauftrag erledigt',
    'night_audit.closed': 'Tagesabschluss durchgeführt',
    'payment.posted': 'Zahlung verbucht',
    'payment.refunded': 'Zahlung erstattet',
    'payment.voided': 'Zahlung storniert',
    'privacy.guest_anonymized': 'Gastdaten anonymisiert',
    'privacy.guest_exported': 'Gastdaten exportiert',
    'reservation.cancelled': 'Reservierung storniert',
    'reservation.checked_in': 'Gast eingecheckt',
    'reservation.checked_out': 'Gast ausgecheckt',
    'reservation.confirmed': 'Reservierung bestätigt',
    'reservation.created': 'Reservierung angelegt',
    'reservation.hold_expired': 'Optionsfrist abgelaufen',
    'reservation.no_show': 'Gast nicht angereist',
    'reservation.room_moved': 'Zimmerzuweisung geändert',
    'reservation.status_changed': 'Reservierungsstatus geändert',
    'reservation.updated': 'Reservierung aktualisiert',
    'resource.booking_cancelled': 'Ressourcenbuchung storniert',
    'resource.booking_created': 'Ressource gebucht',
});

const aggregateTypeLabels = Object.freeze({
    channel_connection: 'Schnittstelle',
    communication: 'Nachricht',
    guest_communication: 'Gästekommunikation',
    guest: 'Gastprofil',
    group_booking: 'Gruppenreservierung',
    guest_registration: 'Meldeschein',
    integration_outbox: 'Übertragung',
    outbox: 'Übertragung',
    invoice: 'Rechnung',
    maintenance: 'Wartungsauftrag',
    night_audit: 'Tagesabschluss',
    payment: 'Zahlung',
    reservation: 'Reservierung',
    resource_booking: 'Ressourcenbuchung',
});

const eventLabel = (value) => eventTypeLabels[value] ?? 'PMS-Ereignis';
const aggregateLabel = (value) => aggregateTypeLabels[value] ?? 'PMS-Datensatz';
const integrationErrorLabel = (value) => (
    value ? 'Übertragung fehlgeschlagen. Technische Details stehen im Serverprotokoll.' : ''
);

const errorMessage = (error) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || 'Die Aktion konnte nicht abgeschlossen werden.'
);

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
    const locale = usePmsLocale();
    const money = (value, currency = 'CHF') => new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
    }).format(Number(value ?? 0));
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
        toDate: businessDate,
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
                    toDateExclusive: addDays(reportPeriod.toDate, 1),
                },
            });
            setPerformanceReport(response.data);
        } catch (loadError) {
            setError(errorMessage(loadError));
        } finally {
            setBusy(false);
        }
    }, [property?.id, reportPeriod.fromDate, reportPeriod.toDate, section]);

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
        { reason: 'Korrektur durch Rezeption' },
        `Gutschrift zu ${invoice.invoiceNumber} erstellt.`,
    );

    const closeNightAudit = () => mutateAdvanced(
        'post',
        `/api/pms/properties/${property.id}/night-audits`,
        { businessDate, markPendingArrivalsAsNoShow: true },
        `Betriebstag ${formatPmsDate(businessDate)} abgeschlossen.`,
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
            'Antwort zum Versand eingeplant.',
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
            channelForm.environment === 'LIVE' ? 'Produktive Schnittstelle eingerichtet.' : 'Testschnittstelle eingerichtet.',
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
            setNotice('Sicherer Link zur Gästeanmeldung erstellt.');
            await loadAdvanced();
        } catch (inviteError) {
            setError(errorMessage(inviteError));
        } finally {
            setBusy(false);
        }
    };

    if (busy && !advanced) return <section className="pms-work-card"><p>PMS-Daten werden geladen…</p></section>;

    return (
        <PmsTranslationBoundary>
        <>
            {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
            {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}

            {section === 'portfolio' && portfolio && (
                <div className="pms-operations-stack">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Hotelportfolio</span><h3>Betriebsvergleich am {formatPmsDate(portfolio.businessDate)}</h3></div><button type="button" onClick={loadPortfolio} disabled={busy}>Aktualisieren</button></div>
                        <div className="pms-metrics">
                            <article><span>Hotels</span><strong>{portfolio.properties}</strong><small>Zentral sichtbar</small></article>
                            <article><span>Verkaufskapazität am Betriebstag</span><strong>{portfolio.availableRooms}</strong><small>{portfolio.operationalRooms} Zimmer grundsätzlich im Verkauf</small></article>
                            <article><span>Portfolio-Auslastung</span><strong>{Number(portfolio.occupancyPercent).toFixed(2)} %</strong><small>{portfolio.soldRooms} verkauft · {Math.max(0, portfolio.availableRooms - portfolio.soldRooms)} noch frei</small></article>
                            <article><span>Anreisen</span><strong>{portfolio.arrivals}</strong><small>{portfolio.departures} Abreisen</small></article>
                        </div>
                        <p><small>Finanzwerte bleiben absichtlich pro Hotel und Währung getrennt. Die Portfolio-Auslastung wird aus verkauften Zimmern und der Verkaufskapazität des Betriebstags berechnet.</small></p>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Hotelvergleich</span><h3>Betriebe</h3></div></div>
                        <div className="pms-table-wrap is-portfolio-table">
                            <table>
                                <thead><tr><th>Hotel</th><th>Ort</th><th>Währung</th><th>Zimmer im Betrieb</th><th>Verkaufskapazität</th><th>Verkauft</th><th>Noch frei</th><th>Auslastung</th><th>An-/Abreisen</th></tr></thead>
                                <tbody>{portfolio.hotels.map((hotel) => <tr key={hotel.propertyId}><td><strong>{hotel.name}</strong><small>{hotel.code}</small></td><td>{hotel.city}</td><td>{hotel.currencyCode}</td><td>{hotel.operationalRooms}</td><td>{hotel.availableRooms}</td><td>{hotel.soldRooms}</td><td>{Math.max(0, hotel.availableRooms - hotel.soldRooms)}</td><td>{Number(hotel.occupancyPercent).toFixed(2)} %</td><td>{hotel.arrivals} / {hotel.departures}</td></tr>)}</tbody>
                            </table>
                        </div>
                    </section>
                </div>
            )}

            {section === 'events' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Hotelressourcen</span><h3>Ressource anlegen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={createHotelResource}>
                            <label>Typ<select aria-label="Ressourcentyp" value={resourceForm.type} onChange={(event) => setResourceForm({ ...resourceForm, type: event.target.value })}>{getPmsEnumOptions(HOTEL_RESOURCE_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                            <label>Code<input aria-label="Ressourcencode" value={resourceForm.code} onChange={(event) => setResourceForm({ ...resourceForm, code: event.target.value })} required /></label>
                            <label>Name<input aria-label="Ressourcenname" value={resourceForm.name} onChange={(event) => setResourceForm({ ...resourceForm, name: event.target.value })} required /></label>
                            <label>Lage / Raum<input value={resourceForm.location} onChange={(event) => setResourceForm({ ...resourceForm, location: event.target.value })} /></label>
                            <label>Kapazität<input type="number" min="1" value={resourceForm.capacity} onChange={(event) => setResourceForm({ ...resourceForm, capacity: event.target.value })} required /></label>
                            <label>Preis pro Stunde (falls berechnet)<input type="number" min="0" step="0.01" value={resourceForm.hourlyRate} onChange={(event) => setResourceForm({ ...resourceForm, hourlyRate: event.target.value })} required /></label>
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
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Ressourcenbelegungen</span><h3>{advanced?.resourceBookings?.length ?? 0} Buchungen</h3></div></div>
                        <div className="pms-record-list">{advanced?.resourceBookings?.map((booking) => <article className="pms-record" key={booking.id}><div><span>{getPmsEnumLabel(RESOURCE_BOOKING_STATUS_LABELS, booking.status)} · {formatPmsDateTime(booking.startAt)} bis {formatPmsDateTime(booking.endAt)}</span><strong>{booking.title}</strong><small>{booking.resourceName} · {booking.attendees} Personen · {money(booking.totalAmount)}</small></div>{booking.status !== 'CANCELLED' && <button type="button" onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/resource-bookings/${booking.id}/cancel`, undefined, 'Ressourcenbuchung storniert.')} disabled={!canManage || busy}>Stornieren</button>}</article>)}</div>
                    </section>
                </div>
            )}

            {section === 'groups' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gruppenreservierungen</span><h3>Gruppenreservierung anlegen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitGroup}>
                            <label>Gruppencode<input value={groupForm.groupCode} onChange={(event) => setGroupForm({ ...groupForm, groupCode: event.target.value })} required /></label>
                            <label>Gruppenname<input value={groupForm.name} onChange={(event) => setGroupForm({ ...groupForm, name: event.target.value })} required /></label>
                            <label>Hauptansprechperson<select value={groupForm.contactGuestId} onChange={(event) => setGroupForm({ ...groupForm, contactGuestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Firma / Reisebüro<select value={groupForm.organizationId} onChange={(event) => setGroupForm({ ...groupForm, organizationId: event.target.value })}><option value="">Privat / keine</option>{organizations.filter((entry) => entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select></label>
                            <label>Anreise<input type="date" value={groupForm.arrivalDate} onChange={(event) => setGroupForm({ ...groupForm, arrivalDate: event.target.value })} required /></label>
                            <label>Abreise<input type="date" value={groupForm.departureDate} onChange={(event) => setGroupForm({ ...groupForm, departureDate: event.target.value })} required /></label>
                            <label>Status<select value={groupForm.status} onChange={(event) => setGroupForm({ ...groupForm, status: event.target.value })}><option value="CONFIRMED">Bestätigt</option><option value="OPTION">Option</option></select></label>
                            <label className="is-wide">Notizen<textarea value={groupForm.notes} onChange={(event) => setGroupForm({ ...groupForm, notes: event.target.value })} /></label>
                            <div className="is-wide pms-rooming-list">
                                <div className="pms-work-card-heading">
                                    <h4>Zimmerliste (Rooming List)</h4>
                                    <button type="button" onClick={() => setGroupForm((current) => ({
                                        ...current,
                                        rooms: [...current.rooms, { guestId: '', roomTypeId: property.roomTypes?.[0]?.id ?? '', roomId: '', ratePlanId: '', adults: 1, children: 0 }],
                                    }))}>Zimmer hinzufügen</button>
                                </div>
                                {groupForm.rooms.map((entry, index) => (
                                    <div className="pms-rooming-row" key={`rooming-${index}`}>
                                        <select aria-label={`Gast Zimmer ${index + 1}`} value={entry.guestId} onChange={(event) => updateGroupRoom(index, { guestId: event.target.value })} required><option value="">Gast</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select>
                                        <select aria-label={`Zimmertyp Zimmer ${index + 1}`} value={entry.roomTypeId} onChange={(event) => updateGroupRoom(index, { roomTypeId: event.target.value, roomId: '', ratePlanId: '' })} required>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select>
                                        <select aria-label={`Ratenplan Zimmer ${index + 1}`} value={entry.ratePlanId} onChange={(event) => updateGroupRoom(index, { ratePlanId: event.target.value })} required><option value="">Ratenplan</option>{groupRoomChoices[index]?.rates.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select>
                                        <select aria-label={`Zimmernummer ${index + 1}`} value={entry.roomId} onChange={(event) => updateGroupRoom(index, { roomId: event.target.value })}><option value="">Automatisch</option>{groupRoomChoices[index]?.rooms.map((room) => <option key={room.id} value={room.id}>{room.number}</option>)}</select>
                                        {groupForm.rooms.length > 1 && <button type="button" aria-label={`Zimmer ${index + 1} entfernen`} onClick={() => setGroupForm((current) => ({ ...current, rooms: current.rooms.filter((_, roomIndex) => roomIndex !== index) }))}>×</button>}
                                    </div>
                                ))}
                            </div>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Gruppenreservierung anlegen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gruppen</span><h3>{advanced?.groups?.length ?? 0} Buchungen</h3></div></div>
                        <div className="pms-record-list">
                            {advanced?.groups?.map((group) => <article className="pms-record is-stacked" key={group.id}><div><span>{group.groupCode} · {getPmsEnumLabel(GROUP_BOOKING_STATUS_LABELS, group.status)}</span><strong>{group.name}</strong><small>{formatPmsDate(group.arrivalDate)} bis {formatPmsDate(group.departureDate)} · {group.rooms.length} Zimmer · {group.organizationName || 'Privat'}</small></div><ul>{group.rooms.map((entry) => <li key={entry.reservationId}>{entry.guestName} · {entry.roomNumber ? `Zimmer ${entry.roomNumber}` : entry.roomTypeName} · {entry.confirmationCode}</li>)}</ul></article>)}
                        </div>
                    </section>
                </div>
            )}

            {section === 'organizations' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Firmen, Reisebüros & Veranstalter</span><h3>Geschäftspartner anlegen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitOrganization}>
                            <label>Typ<select value={organizationForm.type} onChange={(event) => setOrganizationForm({ ...organizationForm, type: event.target.value })}>{getPmsEnumOptions(ORGANIZATION_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                            <label>Name<input value={organizationForm.name} onChange={(event) => setOrganizationForm({ ...organizationForm, name: event.target.value })} required /></label>
                            <label>UID / MWST-Nr.<input value={organizationForm.vatNumber} onChange={(event) => setOrganizationForm({ ...organizationForm, vatNumber: event.target.value })} /></label>
                            <label>Zahlungsziel (Tage)<input type="number" min="0" value={organizationForm.paymentTermsDays} onChange={(event) => setOrganizationForm({ ...organizationForm, paymentTermsDays: event.target.value })} /></label>
                            <label className="is-wide">Adresse<input value={organizationForm.addressLine1} onChange={(event) => setOrganizationForm({ ...organizationForm, addressLine1: event.target.value })} /></label>
                            <label>PLZ<input value={organizationForm.postalCode} onChange={(event) => setOrganizationForm({ ...organizationForm, postalCode: event.target.value })} /></label>
                            <label>Ort<input value={organizationForm.city} onChange={(event) => setOrganizationForm({ ...organizationForm, city: event.target.value })} /></label>
                            <label>E-Mail<input type="email" value={organizationForm.email} onChange={(event) => setOrganizationForm({ ...organizationForm, email: event.target.value })} /></label>
                            <label>Rechnungs-E-Mail<input type="email" value={organizationForm.billingEmail} onChange={(event) => setOrganizationForm({ ...organizationForm, billingEmail: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Geschäftspartner speichern</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Geschäftspartner</span><h3>{organizations.length} Profile</h3></div></div>
                        <div className="pms-record-list">{organizations.map((entry) => <article className="pms-record" key={entry.id}><div><span>{getPmsEnumLabel(ORGANIZATION_TYPE_LABELS, entry.type)}</span><strong>{entry.name}</strong><small>{entry.billingEmail || entry.email || 'Keine E-Mail'} · {entry.paymentTermsDays} Tage Zahlungsziel</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'invoices' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Rechnungswesen</span><h3>Rechnung erstellen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitInvoice}>
                            <label className="is-wide">Gastkonto (Folio)<select value={invoiceForm.folioId} onChange={(event) => { const folio = folios.find((entry) => String(entry.id) === event.target.value); setInvoiceForm({ ...invoiceForm, folioId: event.target.value, recipientName: folio?.organizationName || folio?.guestName || '' }); }} required><option value="">Gastkonto wählen</option>{folios.filter((folio) => Number(folio.charges) > 0).map((folio) => <option key={folio.id} value={folio.id}>{getFolioDisplayLabel(folio.label)} · {folio.confirmationCode} · {folio.guestName}</option>)}</select></label>
                            <label>Empfänger<input value={invoiceForm.recipientName} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientName: event.target.value })} required /></label>
                            <label>Fällig am<input type="date" value={invoiceForm.dueDate} onChange={(event) => setInvoiceForm({ ...invoiceForm, dueDate: event.target.value })} required /></label>
                            <label>MWST-Satz (%)<input type="number" min="0" max="100" step="0.01" value={invoiceForm.vatRate} onChange={(event) => setInvoiceForm({ ...invoiceForm, vatRate: event.target.value })} required /></label>
                            <label>IBAN<input value={invoiceForm.creditorIban} onChange={(event) => setInvoiceForm({ ...invoiceForm, creditorIban: event.target.value })} placeholder="optional für Schweizer QR-Rechnung" /></label>
                            <label className="is-wide">Adresse<input value={invoiceForm.recipientAddress} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientAddress: event.target.value })} /></label>
                            <label>PLZ<input value={invoiceForm.recipientPostalCode} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientPostalCode: event.target.value })} /></label>
                            <label>Ort<input value={invoiceForm.recipientCity} onChange={(event) => setInvoiceForm({ ...invoiceForm, recipientCity: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Rechnung ausstellen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Rechnungsjournal</span><h3>{advanced?.invoices?.length ?? 0} Rechnungen</h3></div></div>
                        <div className="pms-record-list">{advanced?.invoices?.map((invoice) => <article className="pms-record" key={invoice.id}><div><span>{invoice.invoiceNumber} · {getPmsEnumLabel(INVOICE_TYPE_LABELS, invoice.type)} · {getPmsEnumLabel(INVOICE_STATUS_LABELS, invoice.status)}</span><strong>{invoice.recipientName}</strong><small>Netto {money(invoice.netAmount, invoice.currencyCode)} · MWST {money(invoice.vatAmount, invoice.currencyCode)} · Total {money(invoice.grossAmount, invoice.currencyCode)}</small>{invoice.originalInvoiceNumber && <small>Korrektur zu {invoice.originalInvoiceNumber} · {invoice.correctionReason}</small>}</div><div className="pms-record-actions"><button type="button" onClick={() => downloadInvoice(invoice)}>PDF</button>{invoice.type === 'INVOICE' && invoice.status === 'ISSUED' && <button type="button" className="is-danger" disabled={!canManage || busy} onClick={() => correctInvoice(invoice)}>Gutschrift</button>}</div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'audit' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Tagesabschluss (Night Audit)</span><h3>Betriebstag {formatPmsDate(businessDate)}</h3></div></div>
                        <p>Der Abschluss speichert Anreisen, Abreisen, Gäste im Haus, Nichtanreisen und den offenen Saldo als unveränderliche Tagesabschluss-Zusammenfassung. Offene bestätigte Anreisen werden als nicht angereist markiert.</p>
                        <button type="button" className="is-primary" onClick={closeNightAudit} disabled={!canManage || busy || advanced?.nightAudits?.some((entry) => entry.businessDate === businessDate)}>Betriebstag abschliessen</button>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Abschlussjournal</span><h3>{advanced?.nightAudits?.length ?? 0} Abschlüsse</h3></div></div>
                        <div className="pms-record-list">{advanced?.nightAudits?.map((audit) => <article className="pms-record" key={audit.id}><div><span>{formatPmsDate(audit.businessDate)} · {audit.closedBy}</span><strong>{audit.inHouseCount} im Haus · {audit.noShowCount} nicht angereist</strong><small>{audit.arrivalsCount} Anreisen · {audit.departuresCount} Abreisen · Offen {money(audit.openBalance)}</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'communications' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gästekommunikation</span><h3>Nachrichtenvorlage anlegen</h3></div></div>
                        <form className="pms-form-grid" onSubmit={submitTemplate}>
                            <label>Code<input value={templateForm.code} onChange={(event) => setTemplateForm({ ...templateForm, code: event.target.value })} required /></label>
                            <label>Name<input value={templateForm.name} onChange={(event) => setTemplateForm({ ...templateForm, name: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input value={templateForm.subject} onChange={(event) => setTemplateForm({ ...templateForm, subject: event.target.value })} required /></label>
                            <label className="is-wide">Text<textarea value={templateForm.body} onChange={(event) => setTemplateForm({ ...templateForm, body: event.target.value })} placeholder="{{guestName}}, {{hotelName}}, {{arrivalDate}}, {{departureDate}}, {{confirmationCode}}" required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Vorlage speichern</button></div>
                        </form>
                        <hr />
                        <h4>Versand vorbereiten</h4>
                        <form className="pms-form-grid" onSubmit={queueCommunication}>
                            <label>Gast<select value={communicationForm.guestId} onChange={(event) => setCommunicationForm({ ...communicationForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select value={communicationForm.reservationId} onChange={(event) => setCommunicationForm({ ...communicationForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(communicationForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Vorlage<select value={communicationForm.templateId} onChange={(event) => setCommunicationForm({ ...communicationForm, templateId: event.target.value })} required><option value="">Vorlage wählen</option>{advanced?.communicationTemplates?.filter((entry) => entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select></label>
                            <label>E-Mail (optional)<input type="email" value={communicationForm.recipient} onChange={(event) => setCommunicationForm({ ...communicationForm, recipient: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Versand einplanen</button></div>
                        </form>
                        <hr />
                        <h4>Testnachricht erfassen</h4>
                        <p>Diese Testfunktion simuliert eingehende E-Mail-, SMS-, WhatsApp- und OTA-Nachrichten. Sie versendet keine echte Nachricht. Produktive Anbieterzugänge verwenden dieselbe Schnittstelle.</p>
                        <form className="pms-form-grid" onSubmit={recordInboundMessage}>
                            <label>Gast<select aria-label="Posteingang Gast" value={inboxForm.guestId} onChange={(event) => setInboxForm({ ...inboxForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select aria-label="Posteingang Reservierung" value={inboxForm.reservationId} onChange={(event) => setInboxForm({ ...inboxForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(inboxForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Kanal<select aria-label="Posteingang Kanal" value={inboxForm.channel} onChange={(event) => setInboxForm({ ...inboxForm, channel: event.target.value })}>{getPmsEnumOptions(COMMUNICATION_CHANNEL_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                            <label>Absender<input aria-label="Posteingang Absender" value={inboxForm.sender} onChange={(event) => setInboxForm({ ...inboxForm, sender: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input aria-label="Posteingang Betreff" value={inboxForm.subject} onChange={(event) => setInboxForm({ ...inboxForm, subject: event.target.value })} /></label>
                            <label className="is-wide">Nachricht<textarea aria-label="Posteingang Nachricht" value={inboxForm.body} onChange={(event) => setInboxForm({ ...inboxForm, body: event.target.value })} required /></label>
                            <label className="is-wide">Externe Vorgangs-ID (optional)<input aria-label="Posteingang Vorgangs-ID" value={inboxForm.externalThreadId} onChange={(event) => setInboxForm({ ...inboxForm, externalThreadId: event.target.value })} /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Eingang erfassen</button></div>
                        </form>
                    </section>
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gemeinsamer Posteingang</span><h3>{advanced?.communications?.length ?? 0} Nachrichten</h3></div></div>
                        <div className="pms-record-list">
                            {advanced?.communications?.map((entry) => (
                                <article className="pms-record" key={entry.id}>
                                    <div>
                                        <span>
                                            {getPmsEnumLabel(COMMUNICATION_DIRECTION_LABELS, entry.direction)}
                                            {' · '}{getPmsEnumLabel(COMMUNICATION_CHANNEL_LABELS, entry.channel ?? 'EMAIL')}
                                            {' · '}{getPmsEnumLabel(COMMUNICATION_STATUS_LABELS, entry.status)}
                                        </span>
                                        <strong>{entry.subject}</strong>
                                        <small>{entry.guestName} · {entry.direction === 'INBOUND' ? entry.sender : entry.recipient}</small>
                                        <p>{entry.body}</p>
                                    </div>
                                    <div className="pms-record-actions">
                                        {entry.direction === 'INBOUND' && !entry.readAt && <button type="button" onClick={() => markMessageRead(entry.id)} disabled={!canManage || busy}>Als gelesen markieren</button>}
                                        {entry.direction === 'INBOUND' && <button type="button" onClick={() => prepareReply(entry)}>Antworten</button>}
                                    </div>
                                </article>
                            ))}
                        </div>
                        <hr />
                        <h4>Individuelle Antwort</h4>
                        <form className="pms-form-grid" onSubmit={queueInboxReply}>
                            <label>Gast<select aria-label="Antwort Gast" value={replyForm.guestId} onChange={(event) => setReplyForm({ ...replyForm, guestId: event.target.value })} required><option value="">Gast wählen</option>{guests.map((guest) => <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>)}</select></label>
                            <label>Reservierung<select aria-label="Antwort Reservierung" value={replyForm.reservationId} onChange={(event) => setReplyForm({ ...replyForm, reservationId: event.target.value })}><option value="">Ohne Reservierung</option>{reservations.filter((entry) => String(entry.guestId) === String(replyForm.guestId)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode}</option>)}</select></label>
                            <label>Kanal<select aria-label="Antwort Kanal" value={replyForm.channel} onChange={(event) => setReplyForm({ ...replyForm, channel: event.target.value })}>{getPmsEnumOptions(COMMUNICATION_CHANNEL_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                            <label>Empfänger<input aria-label="Antwort Empfänger" value={replyForm.recipient} onChange={(event) => setReplyForm({ ...replyForm, recipient: event.target.value })} required /></label>
                            <label className="is-wide">Betreff<input aria-label="Antwort Betreff" value={replyForm.subject} onChange={(event) => setReplyForm({ ...replyForm, subject: event.target.value })} /></label>
                            <label className="is-wide">Antwort<textarea aria-label="Antwort Nachricht" value={replyForm.body} onChange={(event) => setReplyForm({ ...replyForm, body: event.target.value })} required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Antwort zum Versand einplanen</button></div>
                        </form>
                    </section>
                </div>
            )}

            {section === 'digital-check-in' && (
                <div className="pms-operations-layout">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Digitale Gästeanmeldung</span><h3>Digitalen Meldeschein senden</h3></div></div>
                        <p>Der Link ist sieben Tage gültig. Die vollständige Dokumentnummer wird ausschliesslich gehasht gespeichert. Der Check-in an der Rezeption erfolgt separat.</p>
                        <form className="pms-form-grid" onSubmit={issueRegistrationInvite}>
                            <label className="is-wide">Reservierung<select value={registrationReservationId} onChange={(event) => { setRegistrationReservationId(event.target.value); setRegistrationInvite(null); }} required><option value="">Reservierung wählen</option>{reservations.filter((entry) => !['CANCELLED', 'NO_SHOW', 'CHECKED_OUT'].includes(entry.status)).map((entry) => <option key={entry.id} value={entry.id}>{entry.confirmationCode} · {entry.guestName} · {formatPmsDate(entry.arrivalDate)}</option>)}</select></label>
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
                        <div className="pms-record-list">{advanced?.guestRegistrations?.map((registration) => <article className="pms-record" key={registration.id}><div><span>{getPmsEnumLabel(GUEST_REGISTRATION_STATUS_LABELS, registration.status)} · {registration.confirmationCode}</span><strong>{registration.guestName}</strong><small>{registration.status === 'COMPLETED' ? `${registration.city}, ${registration.countryCode} · Dokument ••••${registration.documentLastFour}` : 'Noch nicht ausgefüllt'}</small></div></article>)}</div>
                    </section>
                </div>
            )}

            {section === 'reports' && (
                <div className="pms-operations-stack">
                    <section className="pms-work-card">
                        <div className="pms-work-card-heading">
                            <div><span className="pms-eyebrow">Umsatz & Auslastung</span><h3>Zimmerauslastung, ADR und RevPAR</h3></div>
                        </div>
                        <form className="pms-form-grid" onSubmit={submitReportPeriod}>
                            <label>Von<input aria-label="Bericht von" type="date" value={reportPeriod.fromDate} onChange={(event) => setReportPeriod({ ...reportPeriod, fromDate: event.target.value })} required /></label>
                            <label>Bis (einschliesslich)<input aria-label="Bericht bis" type="date" value={reportPeriod.toDate} onChange={(event) => setReportPeriod({ ...reportPeriod, toDate: event.target.value })} required /></label>
                            <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={busy}>Bericht aktualisieren</button></div>
                        </form>
                        {performanceReport && (
                            <>
                                <div className="pms-metrics">
                                    <article><span>Zimmerauslastung</span><strong>{Number(performanceReport.occupancyPercent).toFixed(2)} %</strong><small>{performanceReport.soldRoomNights} von {performanceReport.availableRoomNights} Zimmernächten</small></article>
                                    <article><span>Zimmerumsatz</span><strong>{money(performanceReport.roomRevenue, performanceReport.currencyCode)}</strong><small>Auf Aufenthaltsnächte verteilt</small></article>
                                    <article><span>ADR</span><strong>{money(performanceReport.adr, performanceReport.currencyCode)}</strong><small>Umsatz je verkaufter Zimmernacht</small></article>
                                    <article><span>RevPAR</span><strong>{money(performanceReport.revPar, performanceReport.currencyCode)}</strong><small>Umsatz je verfügbarer Zimmernacht</small></article>
                                    <article><span>Anreisen</span><strong>{performanceReport.arrivals}</strong><small>{performanceReport.cancellations} Stornos · {performanceReport.noShows} nicht angereist</small></article>
                                </div>
                                <p><small>{performanceReport.methodology}</small></p>
                            </>
                        )}
                    </section>
                    {performanceReport && (
                        <div className="pms-operations-layout pms-report-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Tageswerte</span><h3>{performanceReport.daily.length} Betriebstage</h3></div></div>
                                <div className="pms-table-wrap is-daily-table">
                                    <table>
                                        <thead><tr><th>Datum</th><th>Verkaufskapazität</th><th>Verkauft</th><th>Noch frei</th><th>Zimmerauslastung</th><th>Umsatz</th><th>ADR</th><th>RevPAR</th></tr></thead>
                                        <tbody>{performanceReport.daily.map((day) => <tr key={day.date}><td>{formatPmsDate(day.date)}</td><td>{day.availableRooms}</td><td>{day.soldRooms}</td><td>{Math.max(0, day.availableRooms - day.soldRooms)}</td><td>{Number(day.occupancyPercent).toFixed(2)} %</td><td>{money(day.roomRevenue, performanceReport.currencyCode)}</td><td>{money(day.adr, performanceReport.currencyCode)}</td><td>{money(day.revPar, performanceReport.currencyCode)}</td></tr>)}</tbody>
                                    </table>
                                </div>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Quellenmix</span><h3>Buchungskanäle</h3></div></div>
                                <div className="pms-record-list">
                                    {performanceReport.sources.length
                                        ? performanceReport.sources.map((source) => <article className="pms-record" key={source.source}><div><span>{Number(source.sharePercent).toFixed(2)} %</span><strong>{getPmsEnumLabel(RESERVATION_SOURCE_LABELS, source.source)}</strong><small>{source.soldRoomNights} Zimmernächte · {money(source.roomRevenue, performanceReport.currencyCode)}</small></div></article>)
                                        : <p className="pms-workspace-placeholder">Im gewählten Zeitraum wurden noch keine Zimmernächte verkauft.</p>}
                                </div>
                            </section>
                        </div>
                    )}
                </div>
            )}

            {section === 'integrations' && (
                <section className="pms-work-card">
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Schnittstellen & Integrationen</span><h3>Übertragungsstatus externer Systeme</h3></div><button type="button" onClick={loadAdvanced} disabled={busy}>Aktualisieren</button></div>
                    <p>Hier werden Verbindungen zu Buchungsportalen und anderen externen Systemen eingerichtet und überwacht. Fehlgeschlagene Übertragungen bleiben sichtbar und können kontrolliert erneut gestartet werden.</p>
                    <form className="pms-form-grid" onSubmit={createChannelConnection}>
                        <label>Anbietercode<input value={channelForm.providerCode} onChange={(event) => setChannelForm({ ...channelForm, providerCode: event.target.value })} required /></label>
                        <label>Name<input value={channelForm.displayName} onChange={(event) => setChannelForm({ ...channelForm, displayName: event.target.value })} required /></label>
                        <label>Betriebsart<select value={channelForm.environment} onChange={(event) => setChannelForm({ ...channelForm, environment: event.target.value })}>{getPmsEnumOptions(CHANNEL_ENVIRONMENT_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                        {channelForm.environment === 'LIVE' && <label>Zugangsdaten-Referenz (Server-Variable)<input aria-label="Zugangsdaten-Referenz" value={channelForm.secretReference} onChange={(event) => setChannelForm({ ...channelForm, secretReference: event.target.value })} placeholder="env:CHANNEL_PROVIDER_SECRET" pattern="env:[A-Z][A-Z0-9_]{2,100}" required /></label>}
                        <label>Zimmertyp<select value={channelForm.roomTypeId} onChange={(event) => setChannelForm({ ...channelForm, roomTypeId: event.target.value, ratePlanId: '' })}>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
                        <label>Ratenplan<select value={channelForm.ratePlanId} onChange={(event) => setChannelForm({ ...channelForm, ratePlanId: event.target.value })} required><option value="">Ratenplan wählen</option>{rates.filter((rate) => String(rate.roomTypeId) === String(channelForm.roomTypeId)).map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
                        <label>Externer Zimmercode<input value={channelForm.externalRoomCode} onChange={(event) => setChannelForm({ ...channelForm, externalRoomCode: event.target.value })} required /></label>
                        <label>Externer Ratencode<input value={channelForm.externalRateCode} onChange={(event) => setChannelForm({ ...channelForm, externalRateCode: event.target.value })} required /></label>
                        <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>{channelForm.environment === 'LIVE' ? 'Produktive Verbindung anlegen' : 'Testverbindung anlegen'}</button></div>
                    </form>
                    <div className="pms-record-list">{advanced?.channelConnections?.map((connection) => <article className="pms-record" key={connection.id}><div><span>{getPmsEnumLabel(CHANNEL_ENVIRONMENT_LABELS, connection.environment)} · {getPmsEnumLabel(CHANNEL_CONNECTION_STATUS_LABELS, connection.status)}</span><strong>{connection.displayName}</strong><small>{connection.mappings.length} Zuordnungen · {connection.lastSyncMessage || 'Noch kein Abgleich'}</small></div><button type="button" onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/channel-connections/${connection.id}/sync`, undefined, connection.environment === 'LIVE' ? 'Produktiver Abgleich eingeplant.' : 'Testabgleich erstellt.')} disabled={!canManage || busy}>{connection.environment === 'LIVE' ? 'Produktiv synchronisieren' : 'Synchronisierung testen'}</button></article>)}</div>
                    <div className="pms-record-list">{advanced?.integrationOutbox?.map((event) => <article className="pms-record" key={event.id}><div><span>{getPmsEnumLabel(OUTBOX_STATUS_LABELS, event.status)} · {aggregateLabel(event.aggregateType)} #{event.aggregateId}</span><strong>{eventLabel(event.eventType)}</strong><small>{formatPmsDateTime(event.createdAt)} · Übertragungsversuch {event.attemptCount}{event.lastError ? ` · ${integrationErrorLabel(event.lastError)}` : ''}</small></div><div className="pms-inline-actions">{event.status === 'PENDING' && <button type="button" disabled={!canManage || busy} onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/integration-outbox/${event.id}/acknowledge`, undefined, 'Übertragung als zugestellt bestätigt.')}>Bestätigen</button>}{['FAILED', 'DEAD_LETTER'].includes(event.status) && <button type="button" disabled={!canManage || busy} onClick={() => mutateAdvanced('post', `/api/pms/properties/${property.id}/integration-outbox/${event.id}/retry`, undefined, 'Übertragung erneut eingeplant.')}>Erneut versuchen</button>}</div></article>)}</div>
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Nachvollziehbarkeit</span><h3>Unveränderliches Änderungsprotokoll</h3></div></div>
                    <div className="pms-record-list">{advanced?.auditEvents?.map((event) => <article className="pms-record" key={event.id}><div><span>{formatPmsDateTime(event.createdAt)} · {event.actor}</span><strong>{eventLabel(event.eventType)}</strong><small>{aggregateLabel(event.aggregateType)} #{event.aggregateId} · Prüfsumme {event.integrityHash?.slice(0, 12)}…</small></div></article>)}</div>
                </section>
            )}
        </>
        </PmsTranslationBoundary>
    );
};

export default PmsAdvancedWorkspace;
