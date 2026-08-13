import { useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';
import PmsAdvancedWorkspace from './PmsAdvancedWorkspace.jsx';
import PmsExtensionsWorkspace from './PmsExtensionsWorkspace.jsx';
import { formatPmsDate } from './pmsFormatting.js';
import { PMS_SECTIONS } from './pmsNavigation.js';
import { PmsTranslationBoundary, usePmsLocale } from './pmsI18n.jsx';
import {
    FOLIO_ITEM_TYPE_LABELS,
    FOLIO_STATUS_LABELS,
    HOUSEKEEPING_STATUS_LABELS,
    HOUSEKEEPING_TASK_TYPE_LABELS,
    MAINTENANCE_PRIORITY_LABELS,
    MAINTENANCE_STATUS_LABELS,
    PAYMENT_KIND_LABELS,
    PAYMENT_METHOD_LABELS,
    PAYMENT_STATUS_LABELS,
    RESERVATION_GUARANTEE_STATUS_LABELS,
    RESERVATION_SOURCE_LABELS,
    RESERVATION_STATUS_LABELS,
    ROOM_BLOCK_TYPE_LABELS,
    ROOM_OPERATIONAL_STATUS_LABELS,
    getFolioDisplayLabel,
    getPmsEnumLabel,
    getPmsEnumOptions,
} from './pmsTerminology.js';

const sections = PMS_SECTIONS
    .filter((section) => section.key !== 'overview')
    .map((section) => [section.key, section.label]);

const WorkspaceFrame = ({ embedded, onClose, children }) => {
    if (embedded) {
        return children;
    }
    return (
        <div className="pms-workspace-backdrop" role="presentation" onMouseDown={onClose}>
            {children}
        </div>
    );
};

const housekeepingPriorityLabel = (priority) => {
    const value = Number(priority ?? 0);
    if (value >= 80) return 'Dringend';
    if (value >= 60) return 'Hoch';
    if (value >= 40) return 'Normal';
    return 'Niedrig';
};

const todayKey = () => new Date().toISOString().slice(0, 10);
const tomorrowKey = () => {
    const next = new Date();
    next.setDate(next.getDate() + 1);
    return next.toISOString().slice(0, 10);
};

const errorMessage = (error) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || 'Die Aktion konnte nicht abgeschlossen werden.'
);

const getRoomSalesState = (room, activeBlock) => {
    if (room.operationalStatus === 'INACTIVE') {
        return {
            label: getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus),
            availabilityLabel: 'Nicht belegbar und nicht zuweisbar',
            assignable: false,
            tone: 'is-room-unavailable',
        };
    }
    if (room.operationalStatus === 'OUT_OF_ORDER') {
        return {
            label: getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus),
            availabilityLabel: 'Nicht belegbar und nicht zuweisbar',
            assignable: false,
            tone: 'is-room-unavailable',
        };
    }
    if (room.operationalStatus !== 'IN_SERVICE') {
        return {
            label: getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus),
            availabilityLabel: 'Betriebsstatus unbekannt – nicht zuweisbar',
            assignable: false,
            tone: 'is-room-unavailable',
        };
    }
    if (activeBlock?.type === 'OUT_OF_ORDER' || activeBlock?.type === 'OWNER_USE') {
        return {
            label: getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, activeBlock.type),
            availabilityLabel: activeBlock.type === 'OWNER_USE'
                ? 'Eigennutzung – nicht zuweisbar'
                : 'Nicht belegbar und nicht zuweisbar',
            assignable: false,
            tone: 'is-room-unavailable',
        };
    }
    if (activeBlock?.type === 'OUT_OF_SERVICE') {
        return {
            label: getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, activeBlock.type),
            availabilityLabel: 'Frei · eingeschränkt, aber zuweisbar',
            assignable: true,
            tone: 'is-room-limited',
        };
    }
    return {
        label: getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus),
        availabilityLabel: 'Frei und zuweisbar',
        assignable: true,
        tone: '',
    };
};

const PmsOperationsWorkspace = ({
    section = 'reservations',
    setup,
    operations,
    property,
    businessDate,
    canManage,
    canManageGuestPrivacy = false,
    initialAction,
    onOperationsChange,
    onClose,
    embedded = false,
    onSectionChange,
}) => {
    const locale = usePmsLocale();
    const money = (value, currency = 'CHF') => new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
    }).format(Number(value ?? 0));
    const [activeSection, setActiveSection] = useState(section);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [availability, setAvailability] = useState(null);
    const [editingReservationId, setEditingReservationId] = useState(null);
    const [editingGuestId, setEditingGuestId] = useState(null);
    const [editingRateId, setEditingRateId] = useState(null);
    const [privacyGuestId, setPrivacyGuestId] = useState(null);
    const [privacyReason, setPrivacyReason] = useState('');
    const [privacyConfirmationOpen, setPrivacyConfirmationOpen] = useState(false);
    const [guestSearch, setGuestSearch] = useState('');
    const [guestMatches, setGuestMatches] = useState([]);

    const emptyGuest = {
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        nationalityCode: 'CH',
        languageCode: 'de',
        notes: '',
        vip: false,
    };
    const emptyRate = {
        roomTypeId: property?.roomTypes?.[0]?.id ?? '',
        code: '',
        name: '',
        nightlyRate: '',
        minStay: 1,
        breakfastIncluded: false,
        refundable: true,
        active: true,
    };
    const emptyOverride = {
        ratePlanId: '',
        stayDate: businessDate,
        price: '',
        minStay: 1,
        closed: false,
        closedArrival: false,
        closedDeparture: false,
    };
    const emptyReservation = {
        propertyId: property?.id ?? '',
        guestId: '',
        roomTypeId: property?.roomTypes?.[0]?.id ?? '',
        roomId: '',
        ratePlanId: '',
        arrivalDate: businessDate || todayKey(),
        departureDate: tomorrowKey(),
        adults: 1,
        children: 0,
        status: 'CONFIRMED',
        source: initialAction === 'walk-in' ? 'WALK_IN' : 'DIRECT',
        notes: '',
        guaranteeStatus: 'UNGUARANTEED',
        holdUntil: '',
    };
    const [guestForm, setGuestForm] = useState(emptyGuest);
    const [rateForm, setRateForm] = useState(emptyRate);
    const [overrideForm, setOverrideForm] = useState(emptyOverride);
    const [reservationForm, setReservationForm] = useState(emptyReservation);
    const [paymentForm, setPaymentForm] = useState({ folioId: '', amount: '', method: 'CARD', reference: '' });
    const [cashShiftForm, setCashShiftForm] = useState({ openingFloat: '200.00', actualCash: '', notes: '' });
    const [chargeForm, setChargeForm] = useState({
        folioId: '',
        serviceDate: businessDate,
        type: 'SERVICE',
        description: '',
        quantity: 1,
        unitPrice: '',
    });
    const [housekeepingForm, setHousekeepingForm] = useState({
        roomId: '',
        serviceDate: businessDate,
        type: 'STAYOVER',
        priority: 50,
        estimatedMinutes: 30,
        assignedTo: '',
        notes: '',
    });
    const [maintenanceForm, setMaintenanceForm] = useState({
        roomId: '',
        title: '',
        description: '',
        priority: 'NORMAL',
        assignedTo: '',
        dueDate: businessDate,
        blockRoom: true,
        blockType: 'OUT_OF_ORDER',
        blockStartDate: businessDate,
        blockEndDate: tomorrowKey(),
    });
    const [splitFolioForm, setSplitFolioForm] = useState({ reservationId: '', label: 'Zusatzkonto' });
    const [moveItemForm, setMoveItemForm] = useState({ sourceFolioId: '', targetFolioId: '', itemId: '' });

    useEffect(() => setActiveSection(section), [section]);

    useEffect(() => {
        if (!property) return;
        setReservationForm((current) => ({
            ...current,
            propertyId: property.id,
            roomTypeId: current.roomTypeId || property.roomTypes?.[0]?.id || '',
        }));
        setRateForm((current) => ({
            ...current,
            roomTypeId: current.roomTypeId || property.roomTypes?.[0]?.id || '',
        }));
    }, [property]);

    useEffect(() => {
        if (!property?.id || guestSearch.trim().length < 2) {
            setGuestMatches([]);
            return undefined;
        }
        const timer = window.setTimeout(() => {
            api.get(`/api/pms/properties/${property.id}/guests/search`, {
                params: { q: guestSearch.trim(), limit: 50 },
            }).then((response) => setGuestMatches(response.data ?? []))
                .catch((searchError) => setError(errorMessage(searchError)));
        }, 250);
        return () => window.clearTimeout(timer);
    }, [guestSearch, property?.id]);

    const guests = operations?.guests ?? [];
    const guestOptions = useMemo(() => {
        const byId = new Map([...guests, ...guestMatches].map((guest) => [guest.id, guest]));
        return [...byId.values()].sort((left, right) => (
            `${left.lastName} ${left.firstName}`.localeCompare(`${right.lastName} ${right.firstName}`, 'de')
        ));
    }, [guestMatches, guests]);
    const reservations = operations?.reservations ?? [];
    const ratePlans = operations?.ratePlans ?? [];
    const rooms = operations?.rooms ?? [];
    const folios = operations?.folios ?? [];
    const tasks = operations?.housekeepingTasks ?? [];
    const currency = operations?.currencyCode ?? property?.currencyCode ?? 'CHF';
    const activeRoomBlocksByRoomId = useMemo(() => {
        const dateKey = String(businessDate ?? '').slice(0, 10);
        const blocksByRoomId = new Map();
        (operations?.roomBlocks ?? [])
            .filter((block) => block.status === 'ACTIVE')
            .filter((block) => block.startDate <= dateKey && dateKey < block.endDate)
            .forEach((block) => {
                const existingBlock = blocksByRoomId.get(block.roomId);
                const blocksInventory = ['OUT_OF_ORDER', 'OWNER_USE'].includes(block.type);
                const existingBlocksInventory = ['OUT_OF_ORDER', 'OWNER_USE'].includes(existingBlock?.type);
                if (!existingBlock || (blocksInventory && !existingBlocksInventory)) {
                    blocksByRoomId.set(block.roomId, block);
                }
            });
        return blocksByRoomId;
    }, [businessDate, operations?.roomBlocks]);

    const filteredRates = useMemo(
        () => ratePlans.filter((rate) => String(rate.roomTypeId) === String(reservationForm.roomTypeId) && rate.active),
        [ratePlans, reservationForm.roomTypeId],
    );
    const filteredRooms = useMemo(
        () => rooms.filter((room) => String(room.roomTypeId) === String(reservationForm.roomTypeId)),
        [rooms, reservationForm.roomTypeId],
    );

    useEffect(() => {
        if (filteredRates.length && !filteredRates.some((rate) => String(rate.id) === String(reservationForm.ratePlanId))) {
            setReservationForm((current) => ({ ...current, ratePlanId: filteredRates[0].id }));
        }
    }, [filteredRates, reservationForm.ratePlanId]);

    const runMutation = async (method, url, payload, successMessage) => {
        if (!canManage) return null;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const separator = url.includes('?') ? '&' : '?';
            const datedUrl = `${url}${separator}businessDate=${businessDate}`;
            const response = payload === undefined
                ? await api[method](datedUrl)
                : await api[method](datedUrl, payload);
            onOperationsChange(response.data);
            setNotice(successMessage);
            return response.data;
        } catch (mutationError) {
            setError(errorMessage(mutationError));
            return null;
        } finally {
            setBusy(false);
        }
    };

    const loadAvailability = async () => {
        if (!property?.id || !reservationForm.arrivalDate || !reservationForm.departureDate) return;
        setBusy(true);
        setError('');
        try {
            const response = await api.get(`/api/pms/properties/${property.id}/availability`, {
                params: {
                    arrival: reservationForm.arrivalDate,
                    departure: reservationForm.departureDate,
                },
            });
            setAvailability(response.data);
        } catch (availabilityError) {
            setError(errorMessage(availabilityError));
        } finally {
            setBusy(false);
        }
    };

    const submitGuest = async (event) => {
        event.preventDefault();
        const url = editingGuestId
            ? `/api/pms/properties/${property.id}/guests/${editingGuestId}`
            : `/api/pms/properties/${property.id}/guests`;
        const result = await runMutation(editingGuestId ? 'put' : 'post', url, guestForm, 'Gastprofil gespeichert.');
        if (result) {
            setGuestForm(emptyGuest);
            setEditingGuestId(null);
        }
    };

    const exportGuestData = async (guest) => {
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const response = await api.get(`/api/pms/privacy/guests/${guest.id}/export`);
            const blob = new Blob(
                [JSON.stringify(response.data, null, 2)],
                { type: 'application/json;charset=utf-8' },
            );
            const objectUrl = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = objectUrl;
            link.download = `pms-gast-${guest.id}-datenexport.json`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(objectUrl);
            setNotice(`Datenexport für ${guest.firstName} ${guest.lastName} erstellt.`);
        } catch (privacyError) {
            setError(errorMessage(privacyError));
        } finally {
            setBusy(false);
        }
    };

    const requestGuestAnonymization = (event) => {
        event.preventDefault();
        if (!privacyGuestId || !privacyReason.trim()) return;
        setPrivacyConfirmationOpen(true);
    };

    const anonymizeGuest = async () => {
        if (!privacyGuestId || !privacyReason.trim()) return;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            await api.post(
                `/api/pms/privacy/guests/${privacyGuestId}/anonymize`,
                { reason: privacyReason.trim() },
            );
            const anonymizedName = `Anonymisiert GAST-${privacyGuestId}`;
            onOperationsChange({
                ...operations,
                guests: guests.map((guest) => (
                    guest.id === privacyGuestId
                        ? {
                            ...guest,
                            firstName: 'Anonymisiert',
                            lastName: `GAST-${privacyGuestId}`,
                            email: null,
                            phone: null,
                            notes: null,
                            vip: false,
                        }
                        : guest
                )),
                reservations: reservations.map((reservation) => (
                    reservation.guestId === privacyGuestId
                        ? { ...reservation, guestName: anonymizedName, notes: null }
                        : reservation
                )),
                folios: folios.map((folio) => (
                    folio.guestId === privacyGuestId
                        ? { ...folio, guestName: anonymizedName }
                        : folio
                )),
            });
            setEditingGuestId(null);
            setGuestForm(emptyGuest);
            setPrivacyGuestId(null);
            setPrivacyReason('');
            setPrivacyConfirmationOpen(false);
            setNotice('Gastprofil wurde anonymisiert; gesetzliche Finanzbelege bleiben erhalten.');
        } catch (privacyError) {
            setError(errorMessage(privacyError));
        } finally {
            setBusy(false);
        }
    };

    const submitRate = async (event) => {
        event.preventDefault();
        const payload = {
            ...rateForm,
            roomTypeId: Number(rateForm.roomTypeId),
            nightlyRate: Number(rateForm.nightlyRate),
            minStay: Number(rateForm.minStay),
        };
        const url = editingRateId
            ? `/api/pms/properties/${property.id}/rate-plans/${editingRateId}`
            : `/api/pms/properties/${property.id}/rate-plans`;
        const result = await runMutation(editingRateId ? 'put' : 'post', url, payload, 'Ratenplan gespeichert.');
        if (result) {
            setRateForm(emptyRate);
            setEditingRateId(null);
        }
    };

    const submitOverride = async (event) => {
        event.preventDefault();
        const payload = {
            ...overrideForm,
            price: Number(overrideForm.price),
            minStay: Number(overrideForm.minStay),
        };
        const result = await runMutation(
            'put',
            `/api/pms/properties/${property.id}/rate-plans/${overrideForm.ratePlanId}/override`,
            payload,
            'Tagesrate gespeichert.',
        );
        if (result) setOverrideForm(emptyOverride);
    };

    const submitReservation = async (event) => {
        event.preventDefault();
        const payload = {
            ...reservationForm,
            propertyId: Number(property.id),
            guestId: Number(reservationForm.guestId),
            roomTypeId: Number(reservationForm.roomTypeId),
            roomId: reservationForm.roomId ? Number(reservationForm.roomId) : null,
            ratePlanId: Number(reservationForm.ratePlanId),
            adults: Number(reservationForm.adults),
            children: Number(reservationForm.children),
        };
        const url = editingReservationId
            ? `/api/pms/reservations/${editingReservationId}`
            : '/api/pms/reservations';
        const result = await runMutation(
            editingReservationId ? 'put' : 'post',
            url,
            payload,
            editingReservationId ? 'Reservierung aktualisiert.' : 'Reservierung angelegt.',
        );
        if (result) {
            setReservationForm(emptyReservation);
            setEditingReservationId(null);
            setAvailability(null);
        }
    };

    const editReservation = (reservation) => {
        setReservationForm({
            propertyId: property.id,
            guestId: reservation.guestId,
            roomTypeId: reservation.roomTypeId,
            roomId: reservation.roomId ?? '',
            ratePlanId: reservation.ratePlanId,
            arrivalDate: reservation.arrivalDate,
            departureDate: reservation.departureDate,
            adults: reservation.adults,
            children: reservation.children,
            status: ['OFFERED', 'TENTATIVE', 'WAITLISTED', 'CONFIRMED'].includes(reservation.status)
                ? reservation.status
                : 'CONFIRMED',
            source: reservation.source,
            notes: reservation.notes ?? '',
            guaranteeStatus: reservation.guaranteeStatus ?? 'UNGUARANTEED',
            holdUntil: reservation.holdUntil?.slice(0, 16) ?? '',
        });
        setEditingReservationId(reservation.id);
        setActiveSection('reservations');
    };

    const performReservationAction = (reservationId, action, message) => (
        runMutation('post', `/api/pms/reservations/${reservationId}/${action}`, undefined, message)
    );

    const confirmReservation = (reservation) => runMutation(
        'post',
        `/api/pms/reservations/${reservation.id}/confirm`,
        { guaranteeStatus: reservation.guaranteeStatus ?? 'UNGUARANTEED' },
        'Reservierung bestätigt.',
    );

    const submitPayment = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/folios/${paymentForm.folioId}/payments`,
            {
                amount: Number(paymentForm.amount),
                method: paymentForm.method,
                reference: paymentForm.reference,
            },
            'Zahlung verbucht.',
        );
        if (result) setPaymentForm({ folioId: '', amount: '', method: 'CARD', reference: '' });
    };

    const openCashShift = async (event) => {
        event.preventDefault();
        await runMutation(
            'post',
            `/api/pms/properties/${property.id}/cash-shifts/open`,
            { openingFloat: Number(cashShiftForm.openingFloat), notes: cashShiftForm.notes },
            'Kassenschicht geöffnet.',
        );
    };

    const closeCashShift = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/cash-shifts/close`,
            { actualCash: Number(cashShiftForm.actualCash), notes: cashShiftForm.notes },
            'Kassenschicht abgeschlossen.',
        );
        if (result) setCashShiftForm({ openingFloat: '200.00', actualCash: '', notes: '' });
    };

    const refundPayment = (payment) => runMutation(
        'post',
        `/api/pms/properties/${property.id}/payments/${payment.id}/refund`,
        { amount: Math.abs(Number(payment.amount)), reason: 'Rückerstattung Rezeption' },
        'Zahlung rückerstattet.',
    );

    const voidPayment = (payment) => runMutation(
        'post',
        `/api/pms/properties/${property.id}/payments/${payment.id}/void`,
        { reason: 'Fehlbuchung Rezeption' },
        'Zahlung storniert.',
    );

    const submitCharge = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/folios/${chargeForm.folioId}/items`,
            {
                ...chargeForm,
                quantity: Number(chargeForm.quantity),
                unitPrice: Number(chargeForm.unitPrice),
            },
            'Gastkonto-Position verbucht.',
        );
        if (result) {
            setChargeForm({
                folioId: '',
                serviceDate: businessDate,
                type: 'SERVICE',
                description: '',
                quantity: 1,
                unitPrice: '',
            });
        }
    };

    const updateTask = (task, status) => runMutation(
        'put',
        `/api/pms/properties/${property.id}/housekeeping/${task.id}`,
        {
            type: task.type,
            status,
            priority: task.priority,
            estimatedMinutes: task.estimatedMinutes,
            notes: task.notes,
            assignedTo: task.assignedTo,
        },
        `Zimmer ${task.roomNumber} ist jetzt „${getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, status)}“.`,
    );

    const submitHousekeepingTask = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/housekeeping`,
            {
                ...housekeepingForm,
                roomId: Number(housekeepingForm.roomId),
                priority: Number(housekeepingForm.priority),
                estimatedMinutes: Number(housekeepingForm.estimatedMinutes),
            },
            'Housekeeping-Aufgabe eingeplant.',
        );
        if (result) setHousekeepingForm({
            roomId: '',
            serviceDate: businessDate,
            type: 'STAYOVER',
            priority: 50,
            estimatedMinutes: 30,
            assignedTo: '',
            notes: '',
        });
    };

    const submitMaintenance = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/maintenance`,
            { ...maintenanceForm, roomId: Number(maintenanceForm.roomId) },
            'Wartungsauftrag erstellt.',
        );
        if (result) setMaintenanceForm((current) => ({
            ...current, roomId: '', title: '', description: '', assignedTo: '',
        }));
    };

    const resolveMaintenance = (workOrder) => runMutation(
        'post',
        `/api/pms/properties/${property.id}/maintenance/${workOrder.id}/resolve`,
        { resolutionNotes: 'Arbeit abgeschlossen und Zimmer freigegeben' },
        `Wartungsauftrag für Zimmer ${workOrder.roomNumber} abgeschlossen.`,
    );

    const submitSplitFolio = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/folios`,
            {
                reservationId: Number(splitFolioForm.reservationId),
                label: splitFolioForm.label,
                organizationId: null,
            },
            'Zusätzliches Gastkonto angelegt.',
        );
        if (result) setSplitFolioForm({ reservationId: '', label: 'Zusatzkonto' });
    };

    const submitMoveFolioItem = async (event) => {
        event.preventDefault();
        const result = await runMutation(
            'post',
            `/api/pms/properties/${property.id}/folios/${moveItemForm.sourceFolioId}/move-items`,
            {
                targetFolioId: Number(moveItemForm.targetFolioId),
                itemIds: [Number(moveItemForm.itemId)],
            },
            'Gastkonto-Position verschoben.',
        );
        if (result) setMoveItemForm({ sourceFolioId: '', targetFolioId: '', itemId: '' });
    };

    const moveReservationToRoom = async (reservationId, room) => {
        const reservation = reservations.find((entry) => String(entry.id) === String(reservationId));
        if (!reservation || String(reservation.roomId) === String(room.id)) return;
        const roomSalesState = getRoomSalesState(room, activeRoomBlocksByRoomId.get(room.id));
        if (!roomSalesState.assignable) {
            setError(`Zimmer ${room.number} ist am gewählten Betriebstag nicht zuweisbar.`);
            return;
        }
        if (String(reservation.roomTypeId) !== String(room.roomTypeId)) {
            setError('Die Reservierung kann nur auf ein Zimmer desselben Zimmertyps verschoben werden.');
            return;
        }
        await runMutation(
            'post',
            `/api/pms/reservations/${reservation.id}/move-room`,
            { roomId: Number(room.id), reason: 'Verschoben im Zimmerplan' },
            `Reservierung auf Zimmer ${room.number} verschoben.`,
        );
    };

    if (!property) {
        return (
            <PmsTranslationBoundary>
            <WorkspaceFrame embedded={embedded} onClose={onClose}>
                <section
                    className={`pms-operations-workspace${embedded ? ' is-embedded' : ''}`}
                    role={embedded ? undefined : 'dialog'}
                    aria-modal={embedded ? undefined : 'true'}
                >
                    {!embedded && <button type="button" className="pms-workspace-close" onClick={onClose}>×</button>}
                    <div className="pms-workspace-empty">
                        <h2>Zuerst ein Hotel einrichten</h2>
                        <p>Reservierungen benötigen mindestens ein Hotel, einen Zimmertyp und ein Zimmer.</p>
                    </div>
                </section>
            </WorkspaceFrame>
            </PmsTranslationBoundary>
        );
    }

    const activeSectionLabel = sections.find(([key]) => key === activeSection)?.[1] ?? 'Hotelbetrieb';

    return (
        <PmsTranslationBoundary>
        <WorkspaceFrame embedded={embedded} onClose={onClose}>
            <section
                className={`pms-operations-workspace${embedded ? ' is-embedded' : ''}`}
                role={embedded ? undefined : 'dialog'}
                aria-modal={embedded ? undefined : 'true'}
                aria-labelledby="pms-operations-title"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className="pms-workspace-header">
                    <div>
                        <span className="pms-eyebrow">Chrono Hotel-PMS · {property.name}</span>
                        <h2 id="pms-operations-title">{activeSectionLabel}</h2>
                        <p>Betriebstag {formatPmsDate(businessDate)} · Alle Änderungen werden sofort im Hotel-PMS gespeichert.</p>
                    </div>
                    {!embedded && <button type="button" className="pms-workspace-close" onClick={onClose} aria-label="Arbeitsbereich schliessen">×</button>}
                </header>

                {!embedded && <nav className="pms-workspace-tabs" aria-label="PMS-Arbeitsbereiche">
                    {sections.map(([key, label]) => (
                        <button
                            type="button"
                            key={key}
                            className={activeSection === key ? 'is-active' : ''}
                            onClick={() => {
                                setActiveSection(key);
                                onSectionChange?.(key);
                                setError('');
                                setNotice('');
                            }}
                        >
                            {label}
                        </button>
                    ))}
                </nav>}

                {!canManage && (
                    <div className="pms-inline-message">Du hast Lesezugriff. Änderungen sind deaktiviert.</div>
                )}
                {error && <div className="pms-inline-message is-error" role="alert">{error}</div>}
                {notice && <div className="pms-inline-message is-success" role="status">{notice}</div>}

                <div className="pms-workspace-body">
                    {activeSection === 'commerce' && (
                        <PmsExtensionsWorkspace
                            property={property}
                            operations={operations}
                            businessDate={businessDate}
                            canManage={canManage}
                            onOperationsChange={onOperationsChange}
                        />
                    )}
                    {['portfolio', 'groups', 'events', 'organizations', 'invoices', 'audit', 'digital-check-in', 'communications', 'reports', 'integrations'].includes(activeSection) && (
                        <PmsAdvancedWorkspace
                            section={activeSection}
                            property={property}
                            operations={operations}
                            businessDate={businessDate}
                            canManage={canManage}
                            onOperationsChange={onOperationsChange}
                        />
                    )}
                    {activeSection === 'reservations' && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading">
                                    <div>
                                        <span className="pms-eyebrow">Rezeption</span>
                                        <h3>{editingReservationId ? 'Reservierung bearbeiten' : 'Neue Reservierung'}</h3>
                                    </div>
                                    {editingReservationId && (
                                        <button type="button" onClick={() => {
                                            setEditingReservationId(null);
                                            setReservationForm(emptyReservation);
                                        }}>Abbrechen</button>
                                    )}
                                </div>
                                <form className="pms-form-grid" onSubmit={submitReservation}>
                                    <label>
                                        Gast suchen
                                        <input
                                            value={guestSearch}
                                            onChange={(event) => setGuestSearch(event.target.value)}
                                            placeholder="Name, E-Mail oder Telefon"
                                        />
                                    </label>
                                    <label>
                                        Gast
                                        <select
                                            aria-label="Gast"
                                            value={reservationForm.guestId}
                                            onChange={(event) => setReservationForm({ ...reservationForm, guestId: event.target.value })}
                                            required
                                        >
                                            <option value="">Gast wählen</option>
                                            {guestOptions.map((guest) => (
                                                <option key={guest.id} value={guest.id}>{guest.firstName} {guest.lastName}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        Zimmertyp
                                        <select
                                            value={reservationForm.roomTypeId}
                                            onChange={(event) => setReservationForm({
                                                ...reservationForm,
                                                roomTypeId: event.target.value,
                                                roomId: '',
                                                ratePlanId: '',
                                            })}
                                            required
                                        >
                                            {property.roomTypes?.filter((type) => type.active).map((type) => (
                                                <option key={type.id} value={type.id}>{type.name}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        Anreise
                                        <input type="date" value={reservationForm.arrivalDate} onChange={(event) => setReservationForm({ ...reservationForm, arrivalDate: event.target.value })} required />
                                    </label>
                                    <label>
                                        Abreise
                                        <input type="date" value={reservationForm.departureDate} onChange={(event) => setReservationForm({ ...reservationForm, departureDate: event.target.value })} required />
                                    </label>
                                    <label>
                                        Ratenplan
                                        <select value={reservationForm.ratePlanId} onChange={(event) => setReservationForm({ ...reservationForm, ratePlanId: event.target.value })} required>
                                            <option value="">Ratenplan wählen</option>
                                            {filteredRates.map((rate) => (
                                                <option key={rate.id} value={rate.id}>{rate.name} · {money(rate.nightlyRate, rate.currencyCode)}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        Zimmer (optional)
                                        <select value={reservationForm.roomId} onChange={(event) => setReservationForm({ ...reservationForm, roomId: event.target.value })}>
                                            <option value="">Später zuweisen</option>
                                            {filteredRooms.map((room) => (
                                                <option key={room.id} value={room.id}>Zimmer {room.number} · {getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, room.housekeepingStatus)}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        Erwachsene
                                        <input type="number" min="1" value={reservationForm.adults} onChange={(event) => setReservationForm({ ...reservationForm, adults: event.target.value })} required />
                                    </label>
                                    <label>
                                        Kinder
                                        <input type="number" min="0" value={reservationForm.children} onChange={(event) => setReservationForm({ ...reservationForm, children: event.target.value })} required />
                                    </label>
                                    <label>
                                        Quelle
                                        <select value={reservationForm.source} onChange={(event) => setReservationForm({ ...reservationForm, source: event.target.value })}>
                                            {Object.entries(RESERVATION_SOURCE_LABELS).map(([value, label]) => (
                                                <option key={value} value={value}>{label}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <label>
                                        Status
                                        <select value={reservationForm.status} onChange={(event) => setReservationForm({ ...reservationForm, status: event.target.value })}>
                                            <option value="CONFIRMED">Bestätigt</option>
                                            <option value="OFFERED">Angebot (kein Zimmerblock)</option>
                                            <option value="TENTATIVE">Option</option>
                                            <option value="WAITLISTED">Warteliste (kein Zimmerblock)</option>
                                        </select>
                                    </label>
                                    <label>
                                        Garantieart
                                        <select value={reservationForm.guaranteeStatus} onChange={(event) => setReservationForm({ ...reservationForm, guaranteeStatus: event.target.value })}>
                                            {Object.entries(RESERVATION_GUARANTEE_STATUS_LABELS).map(([value, label]) => (
                                                <option key={value} value={value}>{label}</option>
                                            ))}
                                        </select>
                                    </label>
                                    {['OFFERED', 'TENTATIVE'].includes(reservationForm.status) && (
                                        <label>
                                            Optionsfrist
                                            <input
                                                type="datetime-local"
                                                value={reservationForm.holdUntil}
                                                onChange={(event) => setReservationForm({ ...reservationForm, holdUntil: event.target.value })}
                                            />
                                        </label>
                                    )}
                                    <label className="is-wide">
                                        Notizen
                                        <textarea value={reservationForm.notes} onChange={(event) => setReservationForm({ ...reservationForm, notes: event.target.value })} />
                                    </label>
                                    <div className="pms-form-actions is-wide">
                                        <button type="button" onClick={loadAvailability} disabled={busy}>Verfügbarkeit prüfen</button>
                                        <button type="submit" className="is-primary" disabled={!canManage || busy || !guestOptions.length || !filteredRates.length}>
                                            {editingReservationId ? 'Änderungen speichern' : 'Reservierung anlegen'}
                                        </button>
                                    </div>
                                </form>
                                {availability && (
                                    <div className="pms-availability-result">
                                        {availability.roomTypes.map((type) => (
                                            <div key={type.roomTypeId}>
                                                <strong>{type.name}</strong>
                                                <span>{type.availableRooms} von {type.totalRooms} verfügbar</span>
                                                {type.rates.map((rate) => (
                                                    <small key={rate.ratePlanId}>
                                                        {rate.name}: {rate.available ? money(rate.totalAmount, rate.currencyCode) : rate.restriction}
                                                    </small>
                                                ))}
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section className="pms-work-card">
                                <div className="pms-work-card-heading">
                                    <div>
                                        <span className="pms-eyebrow">Reservierungsbuch</span>
                                        <h3>{reservations.length} Reservierungen</h3>
                                    </div>
                                </div>
                                <div className="pms-record-list">
                                    {reservations.length ? reservations.map((reservation) => (
                                        <article key={reservation.id} className={`pms-record is-${reservation.status.toLowerCase()}`}>
                                            <div>
                                                <span>{reservation.confirmationCode}</span>
                                                <strong>{reservation.guestName}</strong>
                                                <small>
                                                    {formatPmsDate(reservation.arrivalDate)} → {formatPmsDate(reservation.departureDate)} · {reservation.roomNumber ? `Zimmer ${reservation.roomNumber}` : reservation.roomTypeName}
                                                </small>
                                                <small>{money(reservation.totalAmount, reservation.currencyCode)} · {getPmsEnumLabel(RESERVATION_STATUS_LABELS, reservation.status)}</small>
                                                <small>
                                                    {getPmsEnumLabel(RESERVATION_GUARANTEE_STATUS_LABELS, reservation.guaranteeStatus)}
                                                    {reservation.holdUntil ? ` · Frist ${new Date(reservation.holdUntil).toLocaleString('de-CH')}` : ''}
                                                </small>
                                                {!!reservation.history?.length && (
                                                    <details>
                                                        <summary>Verlauf ({reservation.history.length})</summary>
                                                        {reservation.history.slice(0, 5).map((entry) => (
                                                            <small key={entry.id}>
                                                                {new Date(entry.changedAt).toLocaleString('de-CH')} · {getPmsEnumLabel(RESERVATION_STATUS_LABELS, entry.toStatus)} · {entry.changedBy}
                                                                {entry.reason ? ` · ${entry.reason}` : ''}
                                                            </small>
                                                        ))}
                                                    </details>
                                                )}
                                            </div>
                                            <div className="pms-record-actions">
                                                {['OFFERED', 'TENTATIVE', 'WAITLISTED', 'CONFIRMED'].includes(reservation.status) && (
                                                    <button type="button" onClick={() => editReservation(reservation)}>Bearbeiten</button>
                                                )}
                                                {['OFFERED', 'TENTATIVE', 'WAITLISTED'].includes(reservation.status) && (
                                                    <button type="button" onClick={() => confirmReservation(reservation)} disabled={!canManage || busy}>Bestätigen</button>
                                                )}
                                                {reservation.status === 'CONFIRMED' && (
                                                    <button type="button" onClick={() => performReservationAction(reservation.id, 'waitlist', 'Auf Warteliste gesetzt.')} disabled={!canManage || busy}>Warteliste</button>
                                                )}
                                                {reservation.status === 'CONFIRMED' && (
                                                    <button type="button" onClick={() => performReservationAction(reservation.id, 'check-in', 'Check-in abgeschlossen.')} disabled={!canManage || busy}>Check-in</button>
                                                )}
                                                {reservation.status === 'CHECKED_IN' && (
                                                    <button type="button" onClick={() => performReservationAction(reservation.id, 'check-out', 'Check-out abgeschlossen.')} disabled={!canManage || busy}>Check-out</button>
                                                )}
                                                {['OFFERED', 'TENTATIVE', 'WAITLISTED', 'CONFIRMED'].includes(reservation.status) && (
                                                    <button type="button" className="is-danger" onClick={() => performReservationAction(reservation.id, 'cancel', 'Reservierung storniert.')} disabled={!canManage || busy}>Stornieren</button>
                                                )}
                                                {reservation.status === 'CONFIRMED' && (
                                                    <button type="button" onClick={() => performReservationAction(reservation.id, 'no-show', 'Reservierung als nicht angereist markiert.')} disabled={!canManage || busy}>Als nicht angereist (No-Show) markieren</button>
                                                )}
                                            </div>
                                        </article>
                                    )) : <p className="pms-workspace-placeholder">Noch keine Reservierungen vorhanden.</p>}
                                </div>
                            </section>
                        </div>
                    )}

                    {activeSection === 'room-plan' && (
                        <section className="pms-work-card">
                            <div className="pms-work-card-heading">
                                <div>
                                    <span className="pms-eyebrow">Aktueller Zimmerstatus</span>
                                    <h3>Zimmerplan für {formatPmsDate(businessDate)}</h3>
                                </div>
                            </div>
                            <div className="pms-room-plan-grid">
                                {rooms.map((room) => {
                                    const activeBlock = activeRoomBlocksByRoomId.get(room.id);
                                    const roomSalesState = getRoomSalesState(room, activeBlock);
                                    return (
                                        <article
                                            key={room.id}
                                            className={`is-${room.housekeepingStatus.toLowerCase()} ${roomSalesState.tone}`.trim()}
                                            onDragOver={(event) => {
                                                if (roomSalesState.assignable) event.preventDefault();
                                            }}
                                            onDrop={(event) => {
                                                if (!roomSalesState.assignable) return;
                                                event.preventDefault();
                                                moveReservationToRoom(event.dataTransfer.getData('text/reservation-id'), room);
                                            }}
                                        >
                                            <span>Zimmer</span>
                                            <strong>{room.number}</strong>
                                            <small>{room.roomTypeName} · Etage {room.floor || '–'}</small>
                                            <div className="pms-room-plan-statuses">
                                                <p>
                                                    <span>Housekeeping</span>
                                                    <strong>{getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, room.housekeepingStatus)}</strong>
                                                </p>
                                                <p>
                                                    <span>Betriebs-/Verkaufsstatus</span>
                                                    <strong>{roomSalesState.label}</strong>
                                                </p>
                                            </div>
                                            {room.currentReservation ? (
                                                <button
                                                    type="button"
                                                    draggable={canManage && ['TENTATIVE', 'CONFIRMED', 'CHECKED_IN'].includes(room.currentReservation.status)}
                                                    onDragStart={(event) => event.dataTransfer.setData('text/reservation-id', String(room.currentReservation.id))}
                                                    onClick={() => editReservation(room.currentReservation)}
                                                    title="Zum Verschieben auf ein anderes passendes Zimmer ziehen"
                                                >
                                                    {room.currentReservation.guestName}
                                                    <small>{getPmsEnumLabel(RESERVATION_STATUS_LABELS, room.currentReservation.status)}</small>
                                                </button>
                                            ) : (
                                                <em className={roomSalesState.assignable ? '' : 'is-unavailable'}>
                                                    {roomSalesState.availabilityLabel}
                                                </em>
                                            )}
                                        </article>
                                    );
                                })}
                            </div>
                        </section>
                    )}

                    {activeSection === 'guests' && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading">
                                    <div><span className="pms-eyebrow">Gästekartei</span><h3>{editingGuestId ? 'Gast bearbeiten' : 'Gastprofil anlegen'}</h3></div>
                                </div>
                                <form className="pms-form-grid" onSubmit={submitGuest}>
                                    <label>Vorname<input value={guestForm.firstName} onChange={(event) => setGuestForm({ ...guestForm, firstName: event.target.value })} required /></label>
                                    <label>Nachname<input value={guestForm.lastName} onChange={(event) => setGuestForm({ ...guestForm, lastName: event.target.value })} required /></label>
                                    <label>E-Mail<input type="email" value={guestForm.email} onChange={(event) => setGuestForm({ ...guestForm, email: event.target.value })} /></label>
                                    <label>Telefon<input value={guestForm.phone} onChange={(event) => setGuestForm({ ...guestForm, phone: event.target.value })} /></label>
                                    <label>Nationalität (ISO-Ländercode, z. B. CH)<input maxLength="2" pattern="[A-Za-z]{2}" title="Zweistelliger ISO-Ländercode, zum Beispiel CH" value={guestForm.nationalityCode} onChange={(event) => setGuestForm({ ...guestForm, nationalityCode: event.target.value.toUpperCase() })} /></label>
                                    <label>Sprache (Sprachcode, z. B. de)<input maxLength="8" value={guestForm.languageCode} onChange={(event) => setGuestForm({ ...guestForm, languageCode: event.target.value })} /></label>
                                    <label className="is-wide">Notizen<textarea value={guestForm.notes} onChange={(event) => setGuestForm({ ...guestForm, notes: event.target.value })} /></label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={guestForm.vip} onChange={(event) => setGuestForm({ ...guestForm, vip: event.target.checked })} /> VIP-Gast</label>
                                    <div className="pms-form-actions is-wide">
                                        {editingGuestId && <button type="button" onClick={() => { setEditingGuestId(null); setGuestForm(emptyGuest); }}>Abbrechen</button>}
                                        <button type="submit" className="is-primary" disabled={!canManage || busy}>Gast speichern</button>
                                    </div>
                                </form>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gästekartei</span><h3>{guests.length} zuletzt geänderte Gäste</h3></div></div>
                                <div className="pms-record-list">
                                    {guests.map((guest) => (
                                        <article className="pms-record" key={guest.id}>
                                            <div>
                                                <span>{guest.vip ? 'VIP' : 'Gast'}</span>
                                                <strong>{guest.firstName} {guest.lastName}</strong>
                                                <small>{guest.email || 'Keine E-Mail'} · {guest.phone || 'Kein Telefon'}</small>
                                            </div>
                                            <div className="pms-record-actions">
                                                <button type="button" onClick={() => {
                                                    setEditingGuestId(guest.id);
                                                    setGuestForm({
                                                        firstName: guest.firstName,
                                                        lastName: guest.lastName,
                                                        email: guest.email ?? '',
                                                        phone: guest.phone ?? '',
                                                        nationalityCode: guest.nationalityCode ?? '',
                                                        languageCode: guest.languageCode ?? 'de',
                                                        notes: guest.notes ?? '',
                                                        vip: guest.vip,
                                                    });
                                                }}>Bearbeiten</button>
                                                {canManageGuestPrivacy && (
                                                    <>
                                                        <button
                                                            type="button"
                                                            disabled={busy}
                                                            onClick={() => exportGuestData(guest)}
                                                        >
                                                            Datenexport
                                                        </button>
                                                        <button
                                                            type="button"
                                                            className="is-danger"
                                                            disabled={busy}
                                                            onClick={() => {
                                                                setPrivacyGuestId(guest.id);
                                                                setPrivacyReason('');
                                                            }}
                                                        >
                                                            Anonymisieren
                                                        </button>
                                                    </>
                                                )}
                                            </div>
                                        </article>
                                    ))}
                                </div>
                                {canManageGuestPrivacy && privacyGuestId && (
                                    <form className="pms-form-grid" onSubmit={requestGuestAnonymization}>
                                        <div className="is-wide">
                                            <span className="pms-eyebrow">Datenschutz</span>
                                            <h3>Gast #{privacyGuestId} anonymisieren</h3>
                                            <p>
                                                Nur abgeschlossene Aufenthalte ohne offene Gastkonten können
                                                anonymisiert werden. Rechnungen und Buchungsreferenzen
                                                bleiben wegen gesetzlicher Pflichten erhalten.
                                            </p>
                                        </div>
                                        <label className="is-wide">
                                            Begründung
                                            <textarea
                                                value={privacyReason}
                                                onChange={(event) => setPrivacyReason(event.target.value)}
                                                maxLength="500"
                                                required
                                            />
                                        </label>
                                        <div className="pms-form-actions is-wide">
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setPrivacyGuestId(null);
                                                    setPrivacyReason('');
                                                }}
                                            >
                                                Abbrechen
                                            </button>
                                            <button
                                                type="submit"
                                                className="is-danger"
                                                disabled={busy || !privacyReason.trim()}
                                            >
                                                Endgültig anonymisieren
                                            </button>
                                        </div>
                                    </form>
                                )}
                            </section>
                        </div>
                    )}

                    {privacyConfirmationOpen && (
                        <div
                            className="pms-confirm-backdrop"
                            role="presentation"
                            onMouseDown={() => setPrivacyConfirmationOpen(false)}
                            onKeyDown={(event) => {
                                if (event.key === 'Escape') {
                                    event.stopPropagation();
                                    setPrivacyConfirmationOpen(false);
                                }
                            }}
                        >
                            <section
                                className="pms-confirm-dialog"
                                role="dialog"
                                aria-modal="true"
                                aria-labelledby="pms-privacy-confirm-title"
                                aria-describedby="pms-privacy-confirm-description"
                                onMouseDown={(event) => event.stopPropagation()}
                            >
                                <span className="pms-confirm-icon is-danger" aria-hidden="true">!</span>
                                <span className="pms-eyebrow">Sicherheitsabfrage</span>
                                <h3 id="pms-privacy-confirm-title">Gastprofil anonymisieren?</h3>
                                <p id="pms-privacy-confirm-description">
                                    Persönliche Gastdaten werden endgültig entfernt. Gesetzlich
                                    aufzubewahrende Finanzbelege und Buchungsreferenzen bleiben erhalten.
                                </p>
                                <strong>Diese Aktion kann nicht rückgängig gemacht werden.</strong>
                                <div className="pms-confirm-actions">
                                    <button
                                        type="button"
                                        onClick={() => setPrivacyConfirmationOpen(false)}
                                        disabled={busy}
                                    >
                                        Abbrechen
                                    </button>
                                    <button
                                        type="button"
                                        className="is-danger"
                                        onClick={anonymizeGuest}
                                        disabled={busy}
                                        autoFocus
                                    >
                                        {busy ? 'Wird anonymisiert …' : 'Anonymisierung bestätigen'}
                                    </button>
                                </div>
                            </section>
                        </div>
                    )}

                    {activeSection === 'rates' && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Ratenverwaltung</span><h3>{editingRateId ? 'Ratenplan bearbeiten' : 'Ratenplan anlegen'}</h3></div></div>
                                <form className="pms-form-grid" onSubmit={submitRate}>
                                    <label>Zimmertyp<select value={rateForm.roomTypeId} onChange={(event) => setRateForm({ ...rateForm, roomTypeId: event.target.value })}>{property.roomTypes?.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
                                    <label>Ratencode<input value={rateForm.code} onChange={(event) => setRateForm({ ...rateForm, code: event.target.value })} required /></label>
                                    <label className="is-wide">Name<input value={rateForm.name} onChange={(event) => setRateForm({ ...rateForm, name: event.target.value })} required /></label>
                                    <label>Standardpreis pro Zimmer/Nacht<input type="number" min="0" step="0.01" value={rateForm.nightlyRate} onChange={(event) => setRateForm({ ...rateForm, nightlyRate: event.target.value })} required /></label>
                                    <label>Mindestaufenthalt (Nächte)<input type="number" min="1" value={rateForm.minStay} onChange={(event) => setRateForm({ ...rateForm, minStay: event.target.value })} required /></label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={rateForm.refundable} onChange={(event) => setRateForm({ ...rateForm, refundable: event.target.checked })} /> Stornierbar</label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={rateForm.breakfastIncluded} onChange={(event) => setRateForm({ ...rateForm, breakfastIncluded: event.target.checked })} /> Frühstück inklusive</label>
                                    <div className="pms-form-actions is-wide">
                                        {editingRateId && <button type="button" onClick={() => { setEditingRateId(null); setRateForm(emptyRate); }}>Abbrechen</button>}
                                        <button type="submit" className="is-primary" disabled={!canManage || busy}>Ratenplan speichern</button>
                                    </div>
                                </form>
                                <hr />
                                <h4>Tagespreis und Restriktionen</h4>
                                <form className="pms-form-grid" onSubmit={submitOverride}>
                                    <label>Ratenplan<select value={overrideForm.ratePlanId} onChange={(event) => setOverrideForm({ ...overrideForm, ratePlanId: event.target.value })} required><option value="">Ratenplan wählen</option>{ratePlans.map((rate) => <option key={rate.id} value={rate.id}>{rate.name}</option>)}</select></label>
                                    <label>Datum<input type="date" value={overrideForm.stayDate} onChange={(event) => setOverrideForm({ ...overrideForm, stayDate: event.target.value })} required /></label>
                                    <label>Tagespreis<input type="number" min="0" step="0.01" value={overrideForm.price} onChange={(event) => setOverrideForm({ ...overrideForm, price: event.target.value })} required /></label>
                                    <label>Mindestaufenthalt (Nächte)<input type="number" min="1" value={overrideForm.minStay} onChange={(event) => setOverrideForm({ ...overrideForm, minStay: event.target.value })} required /></label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={overrideForm.closed} onChange={(event) => setOverrideForm({ ...overrideForm, closed: event.target.checked })} /> Verkauf geschlossen (Stop Sell)</label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={overrideForm.closedArrival} onChange={(event) => setOverrideForm({ ...overrideForm, closedArrival: event.target.checked })} /> Anreise gesperrt (CTA)</label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={overrideForm.closedDeparture} onChange={(event) => setOverrideForm({ ...overrideForm, closedDeparture: event.target.checked })} /> Abreise gesperrt (CTD)</label>
                                    <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Tagesrate speichern</button></div>
                                </form>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Verkauf</span><h3>{ratePlans.length} Ratenpläne</h3></div></div>
                                <div className="pms-record-list">
                                    {ratePlans.map((rate) => (
                                        <article className="pms-record" key={rate.id}>
                                            <div>
                                                <span>{rate.code} · {rate.roomTypeName}</span>
                                                <strong>{rate.name}</strong>
                                                <small>{money(rate.nightlyRate, rate.currencyCode)} · mindestens {rate.minStay} {rate.minStay === 1 ? 'Nacht' : 'Nächte'}</small>
                                            </div>
                                            <button type="button" onClick={() => {
                                                setEditingRateId(rate.id);
                                                setRateForm({
                                                    roomTypeId: rate.roomTypeId,
                                                    code: rate.code,
                                                    name: rate.name,
                                                    nightlyRate: rate.nightlyRate,
                                                    minStay: rate.minStay,
                                                    breakfastIncluded: rate.breakfastIncluded,
                                                    refundable: rate.refundable,
                                                    active: rate.active,
                                                });
                                            }}>Bearbeiten</button>
                                        </article>
                                    ))}
                                </div>
                            </section>
                        </div>
                    )}

                    {activeSection === 'housekeeping' && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Housekeeping</span><h3>Aufgaben am {formatPmsDate(businessDate)}</h3></div></div>
                                <div className="pms-housekeeping-board">
                                    {tasks.length ? tasks.map((task) => (
                                        <article key={task.id}>
                                            <div>
                                                <span>Zimmer {task.roomNumber}</span>
                                                <strong>{getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, task.status)}</strong>
                                                <small>
                                                    {getPmsEnumLabel(HOUSEKEEPING_TASK_TYPE_LABELS, task.type)}
                                                    {' · '}{task.estimatedMinutes} Min.
                                                    {' · '}{housekeepingPriorityLabel(task.priority)} ({task.priority}/100)
                                                    {task.assignedTo ? ` · ${task.assignedTo}` : ''}
                                                </small>
                                            </div>
                                            <div className="pms-record-actions">
                                                <button type="button" onClick={() => updateTask(task, 'IN_PROGRESS')} disabled={!canManage || busy}>Start</button>
                                                <button type="button" onClick={() => updateTask(task, 'INSPECTION')} disabled={!canManage || busy}>Kontrolle</button>
                                                <button type="button" className="is-primary" onClick={() => updateTask(task, 'CLEAN')} disabled={!canManage || busy}>Sauber</button>
                                            </div>
                                        </article>
                                    )) : <p className="pms-workspace-placeholder">Für diesen Betriebstag sind keine Housekeeping-Aufgaben offen.</p>}
                                </div>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Planung</span><h3>Aufgabe einplanen</h3></div></div>
                                <form className="pms-form-grid" onSubmit={submitHousekeepingTask}>
                                    <label>Zimmer<select value={housekeepingForm.roomId} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, roomId: event.target.value })} required><option value="">Zimmer wählen</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.number} · {room.roomTypeName}</option>)}</select></label>
                                    <label>Datum<input type="date" value={housekeepingForm.serviceDate} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, serviceDate: event.target.value })} required /></label>
                                    <label>Auftragsart<select value={housekeepingForm.type} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, type: event.target.value })}>{getPmsEnumOptions(HOUSEKEEPING_TASK_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                                    <label>Zuständig<input value={housekeepingForm.assignedTo} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, assignedTo: event.target.value })} /></label>
                                    <label>Dringlichkeit (0–100)<input type="number" min="0" max="100" value={housekeepingForm.priority} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, priority: event.target.value })} /></label>
                                    <label>Minuten<input type="number" min="1" max="1440" value={housekeepingForm.estimatedMinutes} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, estimatedMinutes: event.target.value })} /></label>
                                    <label className="is-wide">Notizen<textarea value={housekeepingForm.notes} onChange={(event) => setHousekeepingForm({ ...housekeepingForm, notes: event.target.value })} /></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Aufgabe speichern</button></div>
                                </form>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Technik</span><h3>Wartung & Zimmerverfügbarkeit</h3></div></div>
                                <form className="pms-form-grid" onSubmit={submitMaintenance}>
                                    <label>Zimmer<select value={maintenanceForm.roomId} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, roomId: event.target.value })} required><option value="">Zimmer wählen</option>{rooms.map((room) => <option key={room.id} value={room.id}>{room.number} · {room.roomTypeName}</option>)}</select></label>
                                    <label>Priorität<select value={maintenanceForm.priority} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, priority: event.target.value })}>{getPmsEnumOptions(MAINTENANCE_PRIORITY_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                                    <label className="is-wide">Titel<input value={maintenanceForm.title} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, title: event.target.value })} required /></label>
                                    <label>Zuständig<input value={maintenanceForm.assignedTo} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, assignedTo: event.target.value })} /></label>
                                    <label>Fällig<input type="date" value={maintenanceForm.dueDate} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, dueDate: event.target.value })} /></label>
                                    <label className="pms-checkbox"><input type="checkbox" checked={maintenanceForm.blockRoom} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, blockRoom: event.target.checked })} /> Auswirkung auf die Zimmerverfügbarkeit erfassen</label>
                                    {maintenanceForm.blockRoom && (
                                        <>
                                            <label>Auswirkung auf Verfügbarkeit<select value={maintenanceForm.blockType} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, blockType: event.target.value })}>{getPmsEnumOptions(ROOM_BLOCK_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select><small>OOO und Eigennutzung nehmen das Zimmer aus dem Verkauf. Bei OOS bleibt es im Bestand und kann weiterhin zugewiesen werden.</small></label>
                                            <label>Von<input type="date" value={maintenanceForm.blockStartDate} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, blockStartDate: event.target.value })} required /></label>
                                            <label>Bis<input type="date" value={maintenanceForm.blockEndDate} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, blockEndDate: event.target.value })} required /></label>
                                        </>
                                    )}
                                    <label className="is-wide">Beschreibung<textarea value={maintenanceForm.description} onChange={(event) => setMaintenanceForm({ ...maintenanceForm, description: event.target.value })} /></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Wartungsauftrag erstellen</button></div>
                                </form>
                                <div className="pms-record-list">
                                    {operations?.maintenanceWorkOrders?.map((workOrder) => (
                                        <article className="pms-record" key={workOrder.id}>
                                            <div>
                                                <span>
                                                    Zimmer {workOrder.roomNumber}
                                                    {' · '}{getPmsEnumLabel(MAINTENANCE_PRIORITY_LABELS, workOrder.priority)}
                                                    {' · '}{getPmsEnumLabel(MAINTENANCE_STATUS_LABELS, workOrder.status)}
                                                </span>
                                                <strong>{workOrder.title}</strong>
                                                <small>{workOrder.assignedTo || 'Nicht zugeteilt'}{workOrder.roomBlockId ? ' · Verfügbarkeitszeitraum erfasst' : ''}</small>
                                            </div>
                                            {!['RESOLVED', 'CANCELLED'].includes(workOrder.status) && <button type="button" onClick={() => resolveMaintenance(workOrder)} disabled={!canManage || busy}>Erledigt & freigeben</button>}
                                        </article>
                                    ))}
                                </div>
                            </section>
                        </div>
                    )}

                    {activeSection === 'folios' && (
                        <div className="pms-operations-layout">
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Kasse</span><h3>{operations?.cashShift ? 'Schicht geöffnet' : 'Schicht öffnen'}</h3></div></div>
                                {operations?.cashShift ? (
                                    <form className="pms-form-grid" onSubmit={closeCashShift}>
                                        <p className="is-wide">
                                            Geöffnet von {operations.cashShift.openedBy} · Anfang {money(operations.cashShift.openingFloat, currency)}
                                            {' · '}Barbewegungen {money(operations.cashShift.cashMovements, currency)}
                                            {' · '}Soll {money(operations.cashShift.expectedCash, currency)}
                                        </p>
                                        <label>Ist-Bargeld<input type="number" min="0" step="0.01" value={cashShiftForm.actualCash} onChange={(event) => setCashShiftForm({ ...cashShiftForm, actualCash: event.target.value })} required /></label>
                                        <label>Notiz<input value={cashShiftForm.notes} onChange={(event) => setCashShiftForm({ ...cashShiftForm, notes: event.target.value })} /></label>
                                        <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Kassenschicht abschliessen</button></div>
                                    </form>
                                ) : (
                                    <form className="pms-form-grid" onSubmit={openCashShift}>
                                        <label>Anfangsbestand<input type="number" min="0" step="0.01" value={cashShiftForm.openingFloat} onChange={(event) => setCashShiftForm({ ...cashShiftForm, openingFloat: event.target.value })} required /></label>
                                        <label>Notiz<input value={cashShiftForm.notes} onChange={(event) => setCashShiftForm({ ...cashShiftForm, notes: event.target.value })} /></label>
                                        <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Kasse öffnen</button></div>
                                    </form>
                                )}
                                <hr />
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Abrechnung</span><h3>Zahlung erfassen</h3></div></div>
                                <form className="pms-form-grid" onSubmit={submitPayment}>
                                    <label className="is-wide">Gastkonto (Folio)<select value={paymentForm.folioId} onChange={(event) => {
                                        const folio = folios.find((entry) => String(entry.id) === event.target.value);
                                        setPaymentForm({ ...paymentForm, folioId: event.target.value, amount: folio?.balance ?? '' });
                                    }} required><option value="">Gastkonto wählen</option>{folios.filter((folio) => folio.status === 'OPEN' && Number(folio.balance) > 0).map((folio) => <option key={folio.id} value={folio.id}>{folio.confirmationCode} · {folio.guestName} · {money(folio.balance, folio.currencyCode)}</option>)}</select></label>
                                    <label>Betrag<input type="number" min="0.01" step="0.01" value={paymentForm.amount} onChange={(event) => setPaymentForm({ ...paymentForm, amount: event.target.value })} required /></label>
                                    <label>Zahlungsart<select value={paymentForm.method} onChange={(event) => setPaymentForm({ ...paymentForm, method: event.target.value })}>{getPmsEnumOptions(PAYMENT_METHOD_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                                    <label className="is-wide">Referenz<input value={paymentForm.reference} onChange={(event) => setPaymentForm({ ...paymentForm, reference: event.target.value })} /></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Zahlung verbuchen</button></div>
                                </form>
                                <hr />
                                <h4>Zusatzleistung verbuchen</h4>
                                <form className="pms-form-grid" onSubmit={submitCharge}>
                                    <label className="is-wide">Gastkonto<select value={chargeForm.folioId} onChange={(event) => setChargeForm({ ...chargeForm, folioId: event.target.value })} required><option value="">Gastkonto wählen</option>{folios.filter((folio) => folio.status === 'OPEN').map((folio) => <option key={folio.id} value={folio.id}>{folio.confirmationCode} · {folio.guestName}</option>)}</select></label>
                                    <label>Datum<input type="date" value={chargeForm.serviceDate} onChange={(event) => setChargeForm({ ...chargeForm, serviceDate: event.target.value })} required /></label>
                                    <label>Art<select value={chargeForm.type} onChange={(event) => setChargeForm({ ...chargeForm, type: event.target.value })}>{getPmsEnumOptions(FOLIO_ITEM_TYPE_LABELS).filter(({ value }) => value !== 'ROOM').map(({ value, label }) => <option key={value} value={value}>{label}</option>)}</select></label>
                                    <label className="is-wide">Beschreibung<input value={chargeForm.description} onChange={(event) => setChargeForm({ ...chargeForm, description: event.target.value })} required /></label>
                                    <label>Menge<input type="number" min="0.01" step="0.01" value={chargeForm.quantity} onChange={(event) => setChargeForm({ ...chargeForm, quantity: event.target.value })} required /></label>
                                    <label>Einzelpreis<input type="number" step="0.01" value={chargeForm.unitPrice} onChange={(event) => setChargeForm({ ...chargeForm, unitPrice: event.target.value })} required /></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" className="is-primary" disabled={!canManage || busy}>Position verbuchen</button></div>
                                </form>
                                <hr />
                                <h4>Gastkonto aufteilen</h4>
                                <form className="pms-form-grid" onSubmit={submitSplitFolio}>
                                    <label>Reservierung<select value={splitFolioForm.reservationId} onChange={(event) => setSplitFolioForm({ ...splitFolioForm, reservationId: event.target.value })} required><option value="">Reservierung wählen</option>{reservations.map((reservation) => <option key={reservation.id} value={reservation.id}>{reservation.confirmationCode} · {reservation.guestName}</option>)}</select></label>
                                    <label>Bezeichnung<input value={splitFolioForm.label} onChange={(event) => setSplitFolioForm({ ...splitFolioForm, label: event.target.value })} required /></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Zusatzkonto anlegen</button></div>
                                </form>
                                <form className="pms-form-grid" onSubmit={submitMoveFolioItem}>
                                    <label>Ausgangskonto<select value={moveItemForm.sourceFolioId} onChange={(event) => setMoveItemForm({ sourceFolioId: event.target.value, targetFolioId: '', itemId: '' })} required><option value="">Ausgangskonto wählen</option>{folios.filter((folio) => folio.status === 'OPEN' && folio.items.length > 0).map((folio) => <option key={folio.id} value={folio.id}>{getFolioDisplayLabel(folio.label)} · {folio.confirmationCode}</option>)}</select></label>
                                    <label>Position<select value={moveItemForm.itemId} onChange={(event) => setMoveItemForm({ ...moveItemForm, itemId: event.target.value })} required><option value="">Position wählen</option>{folios.find((folio) => String(folio.id) === String(moveItemForm.sourceFolioId))?.items.map((item) => <option key={item.id} value={item.id}>{item.description} · {money(item.totalAmount, currency)}</option>)}</select></label>
                                    <label className="is-wide">Zielkonto<select value={moveItemForm.targetFolioId} onChange={(event) => setMoveItemForm({ ...moveItemForm, targetFolioId: event.target.value })} required><option value="">Zielkonto wählen</option>{folios.filter((folio) => {
                                        const source = folios.find((entry) => String(entry.id) === String(moveItemForm.sourceFolioId));
                                        return source && folio.status === 'OPEN' && folio.id !== source.id && folio.reservationId === source.reservationId;
                                    }).map((folio) => <option key={folio.id} value={folio.id}>{getFolioDisplayLabel(folio.label)}</option>)}</select></label>
                                    <div className="pms-form-actions is-wide"><button type="submit" disabled={!canManage || busy}>Position verschieben</button></div>
                                </form>
                            </section>
                            <section className="pms-work-card">
                                <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Gastkonten</span><h3>{folios.length} Konten</h3></div></div>
                                <div className="pms-record-list">
                                    {folios.map((folio) => (
                                        <article className="pms-record" key={folio.id}>
                                            <div>
                                                <span>{folio.confirmationCode} · {getPmsEnumLabel(FOLIO_STATUS_LABELS, folio.status)}</span>
                                                <strong>{getFolioDisplayLabel(folio.label)} · {folio.guestName}</strong>
                                                <small>Leistungen {money(folio.charges, folio.currencyCode)} · Zahlungen {money(folio.payments, folio.currencyCode)}</small>
                                                {folio.paymentEntries?.map((payment) => (
                                                    <small key={payment.id}>
                                                        {getPmsEnumLabel(PAYMENT_KIND_LABELS, payment.kind)}
                                                        {' · '}{getPmsEnumLabel(PAYMENT_METHOD_LABELS, payment.method)}
                                                        {' · '}{money(payment.amount, folio.currencyCode)}
                                                        {' · '}{getPmsEnumLabel(PAYMENT_STATUS_LABELS, payment.status)}
                                                        {payment.status === 'POSTED' && (
                                                            <span className="pms-record-actions">
                                                                {payment.kind === 'PAYMENT' && <button type="button" onClick={() => refundPayment(payment)} disabled={!canManage || busy}>Erstatten</button>}
                                                                <button type="button" onClick={() => voidPayment(payment)} disabled={!canManage || busy}>Stornieren</button>
                                                            </span>
                                                        )}
                                                    </small>
                                                ))}
                                            </div>
                                            <strong className={Number(folio.balance) > 0 ? 'is-balance-open' : ''}>{money(folio.balance, folio.currencyCode)}</strong>
                                        </article>
                                    ))}
                                </div>
                            </section>
                        </div>
                    )}

                </div>
            </section>
        </WorkspaceFrame>
        </PmsTranslationBoundary>
    );
};

export default PmsOperationsWorkspace;
