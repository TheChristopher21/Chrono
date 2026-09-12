import { useEffect, useMemo, useRef, useState } from 'react';
import api from '../../utils/api.js';
import PmsGuestProfileDetails, { profileDetails, profilePayload, preferredProfileEmail } from './PmsGuestProfileDetails.jsx';

const RESERVATION_SOURCES = [
    ['DIRECT', 'Direktreservierung'],
    ['PHONE', 'Telefon'],
    ['EMAIL', 'E-Mail'],
    ['WALK_IN', 'Walk-in (ohne Vorreservierung)'],
];

const GUARANTEE_OPTIONS = [
    ['UNGUARANTEED', 'Ohne Garantie'],
    ['CREDIT_CARD', 'Kreditkartengarantie'],
    ['DEPOSIT_REQUIRED', 'Anzahlung erforderlich'],
    ['DEPOSIT_PAID', 'Anzahlung bezahlt'],
    ['COMPANY_GUARANTEE', 'Firmengarantie'],
    ['OTA_GUARANTEE', 'Garantie über Buchungsportal'],
];

const STEP_LABELS = [
    'Aufenthalt & Quelle',
    'Verfügbarkeit & Zimmer',
    'Gast & Meldedaten',
    'Prüfen & abschliessen',
];

const EMPTY_LIST = Object.freeze([]);

const dateKey = (value) => {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
    const date = value instanceof Date ? value : new Date();
    return [
        date.getFullYear(),
        String(date.getMonth() + 1).padStart(2, '0'),
        String(date.getDate()).padStart(2, '0'),
    ].join('-');
};

const addDays = (value, days) => {
    const [year, month, day] = dateKey(value).split('-').map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString().slice(0, 10);
};

const initialStay = (businessDate, walkIn) => {
    // A Walk-in can only be checked in for the hotel's actual current day. The
    // dashboard business date may intentionally point at another planning day.
    const arrivalDate = walkIn ? dateKey(new Date()) : dateKey(businessDate);
    return {
        arrivalDate,
        departureDate: addDays(arrivalDate, 1),
        adults: 1,
        children: 0,
        childAges: [],
        source: walkIn ? 'WALK_IN' : 'DIRECT',
    };
};

const emptyGuest = () => ({
    ...profileDetails(),
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    dateOfBirth: '',
    nationalityCode: 'CH',
    languageCode: 'de',
    addressLine1: '',
    postalCode: '',
    city: '',
    countryCode: 'CH',
    vehiclePlate: '',
    roomPreferences: '',
    organizationId: '',
    notes: '',
    vip: false,
});

const emptyRegistration = () => ({
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

const clean = (value) => String(value ?? '').trim();
const optional = (value) => clean(value) || null;
const upperCountry = (value) => clean(value).toUpperCase();
const isValidEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean(value));

const errorMessage = (error, fallback) => (
    error?.response?.data?.detail
    || error?.response?.data?.message
    || error?.message
    || fallback
);

const createIdempotencyKey = () => (
    globalThis.crypto?.randomUUID?.()
    || `frontdesk-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`
);

const isBlockingRoomBlock = (block, roomId, arrivalDate, departureDate) => (
    String(block.roomId) === String(roomId)
    && block.status === 'ACTIVE'
    && ['OUT_OF_ORDER', 'OWNER_USE'].includes(block.type)
    && block.startDate < departureDate
    && block.endDate > arrivalDate
);

const rateChoicesFromAvailability = (availability) => (
    (availability?.roomTypes ?? []).flatMap((roomType) => (
        (roomType.rates ?? []).map((rate) => ({
            ...rate,
            roomTypeId: roomType.roomTypeId ?? roomType.id,
            roomTypeName: roomType.name ?? roomType.roomTypeName,
            availableRooms: Number(roomType.availableRooms ?? 0),
            available: rate.available !== false && Number(roomType.availableRooms ?? 0) > 0,
        }))
    ))
);

const freeRoomsFromAvailability = (availability) => {
    const normalizeRoom = (room, defaults = {}) => ({
        ...defaults,
        ...room,
        id: room.id ?? room.roomId,
        number: room.number ?? room.roomNumber,
    });
    const topLevelRooms = Array.isArray(availability?.freeRooms)
        ? availability.freeRooms.map((room) => normalizeRoom(room))
        : [];
    const nestedRooms = (availability?.roomTypes ?? []).flatMap((roomType) => (
        (roomType.freeRooms ?? []).map((room) => normalizeRoom(room, {
            roomTypeId: room.roomTypeId ?? roomType.roomTypeId ?? roomType.id,
            roomTypeName: room.roomTypeName ?? roomType.name,
        }))
    ));
    return {
        provided: Array.isArray(availability?.freeRooms)
            || (availability?.roomTypes ?? []).some((roomType) => Array.isArray(roomType.freeRooms)),
        rooms: [...topLevelRooms, ...nestedRooms],
    };
};

const formatMoney = (value, currencyCode = 'CHF') => {
    try {
        return new Intl.NumberFormat('de-CH', {
            style: 'currency',
            currency: currencyCode || 'CHF',
        }).format(Number(value ?? 0));
    } catch {
        return `${currencyCode || 'CHF'} ${Number(value ?? 0).toFixed(2)}`;
    }
};

const reservationStatusLabel = (status) => ({
    CONFIRMED: 'Bestätigt',
    CHECKED_IN: 'Eingecheckt',
    CHECKED_OUT: 'Ausgecheckt',
}[status] ?? status ?? 'Abgeschlossen');

const PmsReceptionBookingFlow = ({
    property,
    operations,
    businessDate,
    canManage,
    mode = 'reservation',
    onOperationsChange,
    onComplete,
}) => {
    const walkIn = mode === 'walk-in';
    const [step, setStep] = useState(1);
    const [stay, setStay] = useState(() => initialStay(businessDate, walkIn));
    const [availability, setAvailability] = useState(null);
    const [availabilityLoading, setAvailabilityLoading] = useState(false);
    const [availabilityError, setAvailabilityError] = useState('');
    const [availabilityRefresh, setAvailabilityRefresh] = useState(0);
    const [roomTypeId, setRoomTypeId] = useState('');
    const [ratePlanId, setRatePlanId] = useState('');
    const [roomId, setRoomId] = useState('');
    const [guestMode, setGuestMode] = useState('existing');
    const [existingGuestId, setExistingGuestId] = useState('');
    const [guestQuery, setGuestQuery] = useState('');
    const [guestMatches, setGuestMatches] = useState(() => operations?.guests ?? []);
    const [guestSearchLoading, setGuestSearchLoading] = useState(false);
    const [guestSearchError, setGuestSearchError] = useState('');
    const [newGuest, setNewGuest] = useState(emptyGuest);
    const [collectRegistration, setCollectRegistration] = useState(walkIn);
    const [registration, setRegistration] = useState(emptyRegistration);
    const [guaranteeStatus, setGuaranteeStatus] = useState('UNGUARANTEED');
    const [reservationNotes, setReservationNotes] = useState('');
    const [flowError, setFlowError] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [success, setSuccess] = useState(null);
    const availabilityRequestRef = useRef(0);
    const submittingRef = useRef(false);
    const submissionAttemptRef = useRef({ fingerprint: null, key: null });
    const stepHeadingRef = useRef(null);
    const successHeadingRef = useRef(null);
    const flowErrorRef = useRef(null);
    const didMountRef = useRef(false);

    const registrationEnabled = walkIn || collectRegistration;
    const checkInNow = walkIn;
    const operationGuests = operations?.guests ?? EMPTY_LIST;
    const organizations = operations?.organizations ?? EMPTY_LIST;

    useEffect(() => {
        if (!didMountRef.current) {
            didMountRef.current = true;
            return;
        }
        stepHeadingRef.current?.focus();
    }, [step]);

    useEffect(() => {
        if (success) successHeadingRef.current?.focus();
    }, [success]);

    useEffect(() => {
        if (flowError) flowErrorRef.current?.focus();
    }, [flowError]);
    const rateChoices = useMemo(
        () => rateChoicesFromAvailability(availability),
        [availability],
    );
    const selectedRate = useMemo(
        () => rateChoices.find((rate) => (
            String(rate.ratePlanId) === String(ratePlanId)
            && String(rate.roomTypeId) === String(roomTypeId)
        )) ?? null,
        [rateChoices, ratePlanId, roomTypeId],
    );

    useEffect(() => {
        if (!property?.id || !stay.arrivalDate || !stay.departureDate
            || stay.arrivalDate >= stay.departureDate) {
            setAvailability(null);
            return undefined;
        }

        const requestId = availabilityRequestRef.current + 1;
        availabilityRequestRef.current = requestId;
        const controller = new AbortController();
        setAvailabilityLoading(true);
        setAvailabilityError('');

        api.get(`/api/pms/properties/${property.id}/availability`, {
            params: {
                arrival: stay.arrivalDate,
                departure: stay.departureDate,
                adults: Number(stay.adults),
                children: Number(stay.children),
                guestId: guestMode === 'existing' ? existingGuestId || undefined : undefined,
                organizationId: guestMode === 'new' ? newGuest.organizationId || undefined : undefined,
            },
            signal: controller.signal,
        }).then((response) => {
            if (requestId !== availabilityRequestRef.current || controller.signal.aborted) return;
            setAvailability(response.data ?? { roomTypes: [] });
        }).catch((error) => {
            if (requestId !== availabilityRequestRef.current || controller.signal.aborted) return;
            setAvailability(null);
            setAvailabilityError(errorMessage(error, 'Die Verfügbarkeit konnte nicht geprüft werden.'));
        }).finally(() => {
            if (requestId === availabilityRequestRef.current && !controller.signal.aborted) {
                setAvailabilityLoading(false);
            }
        });

        return () => controller.abort();
    }, [availabilityRefresh, property?.id, stay.arrivalDate, stay.departureDate, stay.adults, stay.children, guestMode, existingGuestId, newGuest.organizationId]);

    useEffect(() => {
        const stillAvailable = rateChoices.some((rate) => (
            rate.available
            && String(rate.ratePlanId) === String(ratePlanId)
            && String(rate.roomTypeId) === String(roomTypeId)
        ));
        if (ratePlanId && !stillAvailable) {
            setRatePlanId('');
            setRoomTypeId('');
            setRoomId('');
        }
    }, [rateChoices, ratePlanId, roomTypeId]);

    useEffect(() => {
        if (guestMode !== 'existing' || !property?.id) return undefined;
        const query = guestQuery.trim();
        if (query.length < 2) {
            setGuestMatches(operationGuests.slice(0, 50));
            setGuestSearchError('');
            setGuestSearchLoading(false);
            return undefined;
        }

        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            setGuestSearchLoading(true);
            setGuestSearchError('');
            api.get(`/api/pms/properties/${property.id}/guests/search`, {
                params: { q: query, limit: 50 },
                signal: controller.signal,
            }).then((response) => {
                if (!controller.signal.aborted) setGuestMatches(response.data ?? []);
            }).catch((error) => {
                if (!controller.signal.aborted) {
                    setGuestSearchError(errorMessage(error, 'Die Gastsuche ist fehlgeschlagen.'));
                }
            }).finally(() => {
                if (!controller.signal.aborted) setGuestSearchLoading(false);
            });
        }, 250);

        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [guestMode, guestQuery, operationGuests, property?.id]);

    const suitableRooms = useMemo(() => {
        if (!roomTypeId) return [];
        const backendRoomAvailability = freeRoomsFromAvailability(availability);
        const hasBackendFreeRooms = backendRoomAvailability.provided;
        const operationRoomsById = new Map(
            (operations?.rooms ?? []).map((room) => [String(room.id), room]),
        );
        const sourceRooms = hasBackendFreeRooms
            ? backendRoomAvailability.rooms
            : (operations?.rooms ?? []);
        const seen = new Set();

        return sourceRooms
            .map((room) => ({ ...operationRoomsById.get(String(room.id)), ...room }))
            .filter((room) => {
                if (room.id == null || seen.has(String(room.id))) return false;
                seen.add(String(room.id));
                if (String(room.roomTypeId) !== String(roomTypeId)) return false;
                if (room.active === false || room.available === false || room.assignable === false) return false;
                if (room.operationalStatus && room.operationalStatus !== 'IN_SERVICE') return false;
                if ((!hasBackendFreeRooms || checkInNow) && room.currentReservation) return false;
                if (checkInNow && room.checkInReady === false) return false;
                if (checkInNow && room.housekeepingStatus !== 'CLEAN') return false;
                return !(operations?.roomBlocks ?? []).some((block) => (
                    isBlockingRoomBlock(block, room.id, stay.arrivalDate, stay.departureDate)
                ));
            })
            .sort((left, right) => String(left.number ?? '').localeCompare(
                String(right.number ?? ''),
                'de',
                { numeric: true },
            ));
    }, [availability, checkInNow, operations?.roomBlocks, operations?.rooms,
        roomTypeId, stay.arrivalDate, stay.departureDate]);

    useEffect(() => {
        if (roomId && !suitableRooms.some((room) => String(room.id) === String(roomId))) {
            setRoomId('');
        }
    }, [roomId, suitableRooms]);

    const selectedGuest = useMemo(() => (
        [...operationGuests, ...guestMatches].find((guest) => (
            String(guest.id) === String(existingGuestId)
        )) ?? null
    ), [existingGuestId, guestMatches, operationGuests]);

    const selectExistingGuest = (guest) => {
        setExistingGuestId(String(guest.id));
        setRegistration((current) => ({
            ...current,
            addressLine: current.addressLine || guest.addressLine1 || '',
            postalCode: current.postalCode || guest.postalCode || '',
            city: current.city || guest.city || '',
            countryCode: upperCountry(guest.countryCode) || current.countryCode || 'CH',
            vehiclePlate: current.vehiclePlate || guest.vehiclePlate || '',
            nationalityCode: upperCountry(guest.nationalityCode) || current.nationalityCode || 'CH',
            signatureName: current.signatureName
                || [guest.firstName, guest.lastName].filter(Boolean).join(' '),
        }));
    };

    const selectGuestMode = (nextMode) => {
        setGuestMode(nextMode);
        if (nextMode === 'new') {
            setRegistration((current) => ({
                ...current,
                nationalityCode: upperCountry(newGuest.nationalityCode) || 'CH',
            }));
        }
    };

    const updateNewGuestNationality = (value) => {
        const nationalityCode = upperCountry(value).slice(0, 2);
        setNewGuest((current) => ({ ...current, nationalityCode }));
        setRegistration((current) => ({ ...current, nationalityCode }));
    };

    const validateStep = (targetStep = step) => {
        if (!property?.id) return 'Es ist kein Hotel ausgewählt.';
        if (targetStep === 1) {
            if (!stay.arrivalDate || !stay.departureDate || stay.arrivalDate >= stay.departureDate) {
                return 'Die Abreise muss nach der Anreise liegen.';
            }
            if (Number(stay.adults) < 1 || Number(stay.adults) > 20
                || Number(stay.children) < 0 || Number(stay.children) > 20) {
                return 'Bitte prüfe die Anzahl der Erwachsenen und Kinder.';
            }
            if (Number(stay.children) > 0 && (stay.childAges?.length !== Number(stay.children)
                || stay.childAges.some((age) => age === '' || Number(age) < 0 || Number(age) > 17))) {
                return 'Bitte erfasse für jedes Kind ein Alter zwischen 0 und 17 Jahren.';
            }
            const allowedSources = walkIn
                ? ['WALK_IN']
                : ['DIRECT', 'PHONE', 'EMAIL'];
            if (!allowedSources.includes(stay.source)) return 'Bitte wähle eine gültige Buchungsquelle.';
        }
        if (targetStep === 2) {
            if (availabilityLoading) return 'Die Verfügbarkeit wird noch geprüft.';
            if (availabilityError) return 'Die Verfügbarkeit muss zuerst erfolgreich geprüft werden.';
            if (!selectedRate?.available) return 'Bitte wähle einen verfügbaren Zimmertyp mit Rate.';
            if (checkInNow && !roomId) return 'Für einen direkten Check-in muss ein sauberes, freies Zimmer gewählt werden.';
            if (roomId && !suitableRooms.some((room) => String(room.id) === String(roomId))) {
                return 'Das gewählte Zimmer ist für diesen Aufenthalt nicht geeignet.';
            }
        }
        if (targetStep === 3) {
            if (guestMode === 'existing' && !selectedGuest) return 'Bitte wähle einen vorhandenen Gast.';
            if (guestMode === 'new' && (!clean(newGuest.firstName) || !clean(newGuest.lastName))) {
                return 'Vorname und Nachname des Hauptgasts sind erforderlich.';
            }
            if (walkIn && guestMode === 'new' && !clean(newGuest.dateOfBirth)) {
                return 'Das Geburtsdatum des Walk-in-Hauptgasts ist erforderlich.';
            }
            if (walkIn && guestMode === 'existing' && !clean(selectedGuest?.dateOfBirth)) {
                return 'Im gewählten Gastprofil fehlt das Geburtsdatum. Bitte ergänze das Profil oder erfasse den Gast neu.';
            }
            if (guestMode === 'new' && !preferredProfileEmail(newGuest) && !clean(newGuest.phone)) {
                return 'Für einen neuen Hauptgast ist mindestens E-Mail oder Telefon erforderlich.';
            }
            if (guestMode === 'new' && preferredProfileEmail(newGuest) && !isValidEmail(preferredProfileEmail(newGuest))) {
                return 'Bitte gib eine gültige E-Mail-Adresse ein.';
            }
            if (newGuest.nationalityCode && upperCountry(newGuest.nationalityCode).length !== 2) {
                return 'Die Nationalität muss als zweistelliger Ländercode erfasst werden.';
            }
            if (registrationEnabled) {
                if (!clean(registration.addressLine) || !clean(registration.postalCode)
                    || !clean(registration.city) || !clean(registration.signatureName)) {
                    return 'Bitte fülle alle erforderlichen Meldedaten aus.';
                }
                if (upperCountry(registration.countryCode).length !== 2
                    || upperCountry(registration.nationalityCode).length !== 2) {
                    return 'Wohnsitzland und Nationalität benötigen einen zweistelligen Ländercode.';
                }
                if (clean(registration.documentNumber).length < 4) {
                    return 'Die Ausweis- oder Passnummer muss mindestens vier Zeichen enthalten.';
                }
                if (!registration.privacyConsent) {
                    return 'Die Richtigkeit der Meldedaten muss bestätigt werden.';
                }
            }
        }
        return '';
    };

    const nextStep = () => {
        const validationError = validateStep(step);
        if (validationError) {
            setFlowError(validationError);
            return;
        }
        setFlowError('');
        setStep((current) => Math.min(4, current + 1));
    };

    const previousStep = () => {
        setFlowError('');
        setStep((current) => Math.max(1, current - 1));
    };

    const buildPayload = () => ({
        existingGuestId: guestMode === 'existing' ? Number(existingGuestId) : null,
        newGuest: guestMode === 'new' ? {
            ...profilePayload(newGuest),
            firstName: clean(newGuest.firstName),
            lastName: clean(newGuest.lastName),
            email: optional(preferredProfileEmail(newGuest)),
            phone: optional(newGuest.phone),
            dateOfBirth: optional(newGuest.dateOfBirth),
            nationalityCode: optional(upperCountry(newGuest.nationalityCode)),
            languageCode: optional(newGuest.languageCode),
            addressLine1: optional(newGuest.addressLine1 || (registrationEnabled ? registration.addressLine : '')),
            postalCode: optional(newGuest.postalCode || (registrationEnabled ? registration.postalCode : '')),
            city: optional(newGuest.city || (registrationEnabled ? registration.city : '')),
            countryCode: optional(upperCountry(newGuest.countryCode || (registrationEnabled ? registration.countryCode : ''))),
            vehiclePlate: optional(newGuest.vehiclePlate || (registrationEnabled ? registration.vehiclePlate : '')),
            roomPreferences: optional(newGuest.roomPreferences),
            organizationId: newGuest.organizationId ? Number(newGuest.organizationId) : null,
            notes: optional(newGuest.notes),
            vip: Boolean(newGuest.vip),
        } : null,
        roomTypeId: Number(roomTypeId),
        roomId: roomId ? Number(roomId) : null,
        ratePlanId: Number(ratePlanId),
        arrivalDate: stay.arrivalDate,
        departureDate: stay.departureDate,
        adults: Number(stay.adults),
        children: Number(stay.children),
        childAges: (stay.childAges ?? []).map(Number),
        source: walkIn ? 'WALK_IN' : stay.source,
        guaranteeStatus,
        notes: optional(reservationNotes),
        registration: registrationEnabled ? {
            addressLine: clean(registration.addressLine),
            postalCode: clean(registration.postalCode),
            city: clean(registration.city),
            countryCode: upperCountry(registration.countryCode),
            nationalityCode: upperCountry(registration.nationalityCode),
            documentNumber: clean(registration.documentNumber),
            vehiclePlate: optional(registration.vehiclePlate),
            signatureName: clean(registration.signatureName),
            privacyConsent: true,
        } : null,
        checkInNow,
    });

    const submitBooking = async (event) => {
        event.preventDefault();
        if (submittingRef.current || !canManage) return;
        const stepOneError = validateStep(1);
        const stepTwoError = validateStep(2);
        const stepThreeError = validateStep(3);
        const validationError = stepOneError || stepTwoError || stepThreeError;
        if (validationError) {
            setFlowError(validationError);
            return;
        }

        const payload = buildPayload();
        const fingerprint = JSON.stringify(payload);
        if (!submissionAttemptRef.current.key
            || submissionAttemptRef.current.fingerprint !== fingerprint) {
            submissionAttemptRef.current = {
                fingerprint,
                key: createIdempotencyKey(),
            };
        }

        submittingRef.current = true;
        setSubmitting(true);
        setFlowError('');
        try {
            const operationsDate = checkInNow ? stay.arrivalDate : businessDate;
            const query = operationsDate
                ? `?businessDate=${encodeURIComponent(dateKey(operationsDate))}`
                : '';
            const response = await api.post(
                `/api/pms/properties/${property.id}/front-desk-bookings${query}`,
                payload,
                { headers: { 'Idempotency-Key': submissionAttemptRef.current.key } },
            );
            const result = response.data ?? {};
            if (result.operations) onOperationsChange?.(result.operations);
            setSuccess(result);
            // The document number is required only for the request and is never persisted client-side.
            setRegistration((current) => ({ ...current, documentNumber: '' }));
        } catch (error) {
            setFlowError(errorMessage(error, 'Die Rezeptionsbuchung konnte nicht abgeschlossen werden.'));
        } finally {
            submittingRef.current = false;
            setSubmitting(false);
        }
    };

    const resetFlow = () => {
        const nextStay = initialStay(businessDate, walkIn);
        setStep(1);
        setStay(nextStay);
        setRoomTypeId('');
        setRatePlanId('');
        setRoomId('');
        setGuestMode('existing');
        setExistingGuestId('');
        setGuestQuery('');
        setGuestMatches(operations?.guests ?? []);
        setNewGuest(emptyGuest());
        setCollectRegistration(walkIn);
        setRegistration(emptyRegistration());
        setGuaranteeStatus('UNGUARANTEED');
        setReservationNotes('');
        setFlowError('');
        setSuccess(null);
        submissionAttemptRef.current = { fingerprint: null, key: null };
        setAvailabilityRefresh((current) => current + 1);
    };

    const successView = useMemo(() => {
        if (!success) return null;
        const resultOperations = success.operations ?? operations ?? {};
        const reservation = (resultOperations.reservations ?? []).find((entry) => (
            String(entry.id) === String(success.reservationId)
        ));
        const folio = (resultOperations.folios ?? []).find((entry) => (
            String(entry.id) === String(success.folioId)
            || String(entry.reservationId) === String(success.reservationId)
        ));
        const fallbackGuestName = guestMode === 'existing'
            ? [selectedGuest?.firstName, selectedGuest?.lastName].filter(Boolean).join(' ')
            : [newGuest.firstName, newGuest.lastName].filter(Boolean).join(' ');
        const selectedRoom = suitableRooms.find((room) => String(room.id) === String(roomId));
        return {
            reservation,
            folio,
            guestName: reservation?.guestName || fallbackGuestName || `Gast #${success.guestId}`,
            roomLabel: reservation?.roomNumber
                ? `Zimmer ${reservation.roomNumber}`
                : selectedRoom?.number
                    ? `Zimmer ${selectedRoom.number}`
                    : 'Zimmer wird später zugewiesen',
            rateName: reservation?.ratePlanName || selectedRate?.name || 'Gewählte Rate',
            currencyCode: reservation?.currencyCode || property?.currencyCode || 'CHF',
        };
    }, [guestMode, newGuest.firstName, newGuest.lastName, operations, property?.currencyCode,
        roomId, selectedGuest, selectedRate, success, suitableRooms]);

    if (success && successView) {
        return (
            <section
                className="pms-work-card pms-reception-booking-flow"
                aria-labelledby="pms-reception-success-title"
                aria-live="polite"
                role="status"
            >
                <div className="pms-workspace-empty">
                    <span className="pms-eyebrow">Rezeption · Vorgang abgeschlossen</span>
                    <h2 id="pms-reception-success-title" ref={successHeadingRef} tabIndex={-1}>
                        {walkIn ? 'Walk-in erfolgreich aufgenommen' : 'Reservierung erfolgreich angelegt'}
                    </h2>
                    <p>{successView.guestName} wurde unter dem Buchungscode <strong>{success.confirmationCode}</strong> erfasst.</p>
                </div>
                <div className="pms-record-list" aria-label="Buchungszusammenfassung">
                    <article className="pms-record">
                        <div>
                            <span>{reservationStatusLabel(success.reservationStatus)}</span>
                            <strong>{success.confirmationCode} · {successView.guestName}</strong>
                            <small>{successView.roomLabel} · {successView.rateName}</small>
                            <small>Meldedaten: {success.registrationStatus === 'COMPLETED' ? 'Vollständig' : 'Noch offen'}</small>
                            {success.checkInReady === false && Boolean(success.checkInBlockers?.length) && (
                                <small>Check-in noch nicht bereit: {success.checkInBlockers.join(' · ')}</small>
                            )}
                        </div>
                        <div>
                            <strong>{formatMoney(success.totalAmount, successView.currencyCode)}</strong>
                            <small>Offener Saldo {formatMoney(success.balance, successView.currencyCode)}</small>
                        </div>
                    </article>
                </div>
                <div className="pms-form-actions">
                    <button type="button" onClick={resetFlow}>Neue Buchung</button>
                    <button
                        type="button"
                        className="is-primary"
                        onClick={() => onComplete?.({
                            action: 'folio',
                            ...success,
                            guestName: successView.guestName,
                            roomNumber: successView.reservation?.roomNumber ?? null,
                        })}
                    >
                        Gastkonto öffnen
                    </button>
                </div>
            </section>
        );
    }

    return (
        <section className="pms-work-card pms-reception-booking-flow" aria-labelledby="pms-reception-flow-title">
            <div className="pms-work-card-heading">
                <div>
                    <span className="pms-eyebrow">Rezeption · Geführter Ablauf</span>
                    <h2 id="pms-reception-flow-title">
                        {walkIn ? 'Walk-in vollständig aufnehmen' : 'Reservierung anlegen'}
                    </h2>
                    <p>
                        {walkIn
                            ? 'Gastdaten, Meldeschein, Zimmer und Check-in werden in einem Vorgang erfasst.'
                            : 'Aufenthalt, Verfügbarkeit und Gastdaten werden vor dem Speichern gemeinsam geprüft.'}
                    </p>
                </div>
            </div>

            <ol className="pms-setup-list" aria-label="Fortschritt der Rezeptionsbuchung">
                {STEP_LABELS.map((label, index) => {
                    const number = index + 1;
                    return (
                        <li
                            key={label}
                            className={number < step ? 'is-done' : number === step ? 'is-next' : ''}
                            aria-current={number === step ? 'step' : undefined}
                        >
                            <span>{number}</span>
                            <strong>{label}</strong>
                            <small>{number < step ? 'Erledigt' : number === step ? 'Aktuell' : 'Als Nächstes'}</small>
                        </li>
                    );
                })}
            </ol>

            {!canManage && (
                <div className="pms-inline-message">Du hast Lesezugriff. Eine Rezeptionsbuchung kann nicht gespeichert werden.</div>
            )}
            {flowError && (
                <div
                    className="pms-inline-message is-error"
                    ref={flowErrorRef}
                    role="alert"
                    tabIndex={-1}
                >
                    {flowError}
                </div>
            )}

            {canManage && step === 1 && (
                <form className="pms-operations-stack" onSubmit={(event) => { event.preventDefault(); nextStep(); }}>
                    <div className="pms-work-card-heading">
                        <div><span className="pms-eyebrow">Schritt 1</span><h3 ref={stepHeadingRef} tabIndex={-1}>Aufenthalt und Buchungsquelle</h3></div>
                    </div>
                    <div className="pms-form-grid">
                        <label>
                            Anreise
                            <input
                                type="date"
                                value={stay.arrivalDate}
                                readOnly={walkIn}
                                required
                                onChange={(event) => setStay((current) => ({
                                    ...current,
                                    arrivalDate: event.target.value,
                                    departureDate: current.departureDate > event.target.value
                                        ? current.departureDate
                                        : addDays(event.target.value, 1),
                                }))}
                            />
                        </label>
                        <label>
                            Abreise
                            <input
                                type="date"
                                min={addDays(stay.arrivalDate, 1)}
                                value={stay.departureDate}
                                required
                                onChange={(event) => setStay((current) => ({ ...current, departureDate: event.target.value }))}
                            />
                        </label>
                        <label>
                            Erwachsene
                            <input type="number" min="1" max="20" value={stay.adults} onChange={(event) => setStay((current) => ({ ...current, adults: event.target.value }))} required />
                        </label>
                        <label>
                            Kinder
                            <input type="number" min="0" max="20" value={stay.children} onChange={(event) => {
                                const children = Number(event.target.value);
                                setStay((current) => ({
                                    ...current,
                                    children,
                                    childAges: Array.from({ length: Math.max(0, children) }, (_, index) => current.childAges?.[index] ?? ''),
                                }));
                            }} required />
                        </label>
                        {(stay.childAges ?? []).map((age, index) => (
                            <label key={`child-age-${index}`}>
                                Alter Kind {index + 1}
                                <input type="number" min="0" max="17" value={age} onChange={(event) => setStay((current) => ({
                                    ...current,
                                    childAges: current.childAges.map((value, ageIndex) => ageIndex === index ? event.target.value : value),
                                }))} required />
                            </label>
                        ))}
                        <label className="is-wide">
                            Buchungsquelle
                            <select
                                value={walkIn ? 'WALK_IN' : stay.source}
                                disabled={walkIn}
                                required
                                onChange={(event) => setStay((current) => ({ ...current, source: event.target.value }))}
                            >
                                {RESERVATION_SOURCES
                                    .filter(([value]) => walkIn ? value === 'WALK_IN' : value !== 'WALK_IN')
                                    .map(([value, label]) => <option value={value} key={value}>{label}</option>)}
                            </select>
                        </label>
                        {walkIn && (
                            <p className="is-wide">Der Walk-in beginnt heute, erhält ein bereites Zimmer und wird nach der Prüfung direkt eingecheckt.</p>
                        )}
                    </div>
                    <div className="pms-form-actions"><button type="submit" className="is-primary">Weiter zur Verfügbarkeit</button></div>
                </form>
            )}

            {canManage && step === 2 && (
                <form className="pms-operations-stack" onSubmit={(event) => { event.preventDefault(); nextStep(); }}>
                    <div className="pms-work-card-heading">
                        <div><span className="pms-eyebrow">Schritt 2</span><h3 ref={stepHeadingRef} tabIndex={-1}>Verfügbarkeit, Rate und Zimmer</h3></div>
                        <button type="button" onClick={() => setAvailabilityRefresh((current) => current + 1)} disabled={availabilityLoading}>Neu prüfen</button>
                    </div>
                    {availabilityLoading && <div className="pms-inline-message" role="status">Verfügbarkeit wird automatisch geprüft…</div>}
                    {availabilityError && <div className="pms-inline-message is-error" role="alert">{availabilityError}</div>}
                    {!availabilityLoading && !availabilityError && (
                        <>
                            <fieldset className="pms-work-card">
                                <legend>Zimmertyp und Rate</legend>
                                <div className="pms-record-list">
                                    {rateChoices.length ? rateChoices.map((rate) => (
                                        <label className="pms-record" key={`${rate.roomTypeId}-${rate.ratePlanId}`}>
                                            <input
                                                type="radio"
                                                name="reception-rate"
                                                value={rate.ratePlanId}
                                                disabled={!rate.available}
                                                checked={String(ratePlanId) === String(rate.ratePlanId)
                                                    && String(roomTypeId) === String(rate.roomTypeId)}
                                                onChange={() => {
                                                    setRoomTypeId(String(rate.roomTypeId));
                                                    setRatePlanId(String(rate.ratePlanId));
                                                    setRoomId('');
                                                }}
                                            />
                                            <span>
                                                <strong>{rate.roomTypeName} · {rate.name}</strong>
                                                <small>{rate.available
                                                    ? `${rate.availableRooms} Zimmer verfügbar`
                                                    : rate.restriction || 'Nicht verfügbar'}</small>
                                            </span>
                                            <b>{formatMoney(rate.totalAmount, rate.currencyCode || property?.currencyCode)}</b>
                                        </label>
                                    )) : <p className="pms-workspace-placeholder">Für diesen Zeitraum wurden keine buchbaren Raten gefunden.</p>}
                                </div>
                            </fieldset>

                            {selectedRate && (
                                <fieldset className="pms-work-card">
                                    <legend>{checkInNow ? 'Bereites Zimmer für direkten Check-in' : 'Zimmerzuweisung'}</legend>
                                    <div className="pms-record-list">
                                        {!checkInNow && (
                                            <label className="pms-record">
                                                <input type="radio" name="reception-room" value="" checked={!roomId} onChange={() => setRoomId('')} />
                                                <span><strong>Später zuweisen</strong><small>Der Zimmertyp wird jetzt reserviert.</small></span>
                                            </label>
                                        )}
                                        {suitableRooms.map((room) => (
                                            <label className="pms-record" key={room.id}>
                                                <input
                                                    type="radio"
                                                    name="reception-room"
                                                    value={room.id}
                                                    checked={String(roomId) === String(room.id)}
                                                    onChange={() => setRoomId(String(room.id))}
                                                />
                                                <span>
                                                    <strong>Zimmer {room.number}</strong>
                                                    <small>{room.roomTypeName || selectedRate.roomTypeName}
                                                        {room.housekeepingStatus ? ` · ${room.housekeepingStatus === 'CLEAN' ? 'Sauber' : room.housekeepingStatus}` : ''}</small>
                                                    {room.features && <small>Ausstattung: {room.features}</small>}
                                                </span>
                                            </label>
                                        ))}
                                        {!suitableRooms.length && (
                                            <p className="pms-workspace-placeholder">
                                                {checkInNow
                                                    ? 'Kein sauberes, freies und betriebsbereites Zimmer verfügbar.'
                                                    : 'Kein sicher vorgefiltertes Zimmer verfügbar. Die Zuweisung kann später erfolgen.'}
                                            </p>
                                        )}
                                    </div>
                                </fieldset>
                            )}
                        </>
                    )}
                    <div className="pms-form-actions"><button type="button" onClick={previousStep}>Zurück</button><button type="button" onClick={() => setStep(3)}>Gast oder Firma zuerst auswählen</button><button type="submit" className="is-primary">Weiter zum Gast</button></div>
                </form>
            )}

            {canManage && step === 3 && (
                <form className="pms-operations-stack" onSubmit={(event) => { event.preventDefault(); nextStep(); }}>
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Schritt 3</span><h3 ref={stepHeadingRef} tabIndex={-1}>Hauptgast und Meldedaten</h3></div></div>
                    <div className="pms-inline-actions" role="group" aria-label="Art des Gastprofils">
                        <button type="button" aria-pressed={guestMode === 'existing'} className={guestMode === 'existing' ? 'is-primary' : ''} onClick={() => selectGuestMode('existing')}>Vorhandenen Gast wählen</button>
                        <button type="button" aria-pressed={guestMode === 'new'} className={guestMode === 'new' ? 'is-primary' : ''} onClick={() => selectGuestMode('new')}>Neuen Gast erfassen</button>
                    </div>

                    {guestMode === 'existing' ? (
                        <fieldset className="pms-work-card">
                            <legend>Vorhandenes Gastprofil</legend>
                            <div className="pms-form-grid">
                                <label className="is-wide">Gast suchen<input value={guestQuery} onChange={(event) => setGuestQuery(event.target.value)} placeholder="Name, E-Mail oder Telefon" /></label>
                            </div>
                            {guestSearchLoading && <p role="status">Gäste werden gesucht…</p>}
                            {guestSearchError && <div className="pms-inline-message is-error" role="alert">{guestSearchError}</div>}
                            <div className="pms-record-list">
                                {guestMatches.map((guest) => (
                                    <label className="pms-record" key={guest.id}>
                                        <input
                                            type="radio"
                                            name="reception-guest"
                                            value={guest.id}
                                            checked={String(existingGuestId) === String(guest.id)}
                                            onChange={() => selectExistingGuest(guest)}
                                        />
                                        <span>
                                            <strong>{guest.firstName} {guest.lastName}</strong>
                                            <small>{guest.referenceCode ? `${guest.referenceCode} · ` : ''}{guest.email || 'Keine E-Mail'} · {guest.phone || 'Kein Telefon'}
                                                {guest.dateOfBirth ? ` · Geboren ${guest.dateOfBirth}` : ''}</small>
                                            {guest.organizationName && <small>Firma: {guest.organizationName}</small>}
                                            {guest.roomPreferences && <small>Zimmerwunsch: {guest.roomPreferences}</small>}
                                        </span>
                                    </label>
                                ))}
                                {!guestSearchLoading && !guestMatches.length && <p className="pms-workspace-placeholder">Kein Gast gefunden. Lege ein neues Profil an.</p>}
                            </div>
                        </fieldset>
                    ) : (
                        <fieldset className="pms-work-card">
                            <legend>Neues Gastprofil</legend>
                            <div className="pms-form-grid">
                                <label>Vorname<input autoComplete="given-name" value={newGuest.firstName} onChange={(event) => setNewGuest((current) => ({ ...current, firstName: event.target.value }))} required /></label>
                                <label>Nachname<input autoComplete="family-name" value={newGuest.lastName} onChange={(event) => setNewGuest((current) => ({ ...current, lastName: event.target.value }))} required /></label>
                                <label>Geburtsdatum<input type="date" autoComplete="bday" value={newGuest.dateOfBirth} onChange={(event) => setNewGuest((current) => ({ ...current, dateOfBirth: event.target.value }))} required={walkIn} /></label>
                                <label>Nationalität (ISO-Code)<input autoCapitalize="characters" maxLength="2" value={newGuest.nationalityCode} onChange={(event) => updateNewGuestNationality(event.target.value)} required /></label>
                                <label>E-Mail<input type="email" autoComplete="email" value={newGuest.email} onChange={(event) => setNewGuest((current) => ({ ...current, email: event.target.value }))} /></label>
                                <label>Telefon<input type="tel" inputMode="tel" autoComplete="tel" value={newGuest.phone} onChange={(event) => setNewGuest((current) => ({ ...current, phone: event.target.value }))} /></label>
                                <label>Sprache<input maxLength="8" value={newGuest.languageCode} onChange={(event) => setNewGuest((current) => ({ ...current, languageCode: event.target.value }))} /></label>
                                <label>Firma<select value={newGuest.organizationId} onChange={(event) => setNewGuest((current) => ({ ...current, organizationId: event.target.value }))}><option value="">Privat</option>{organizations.filter((entry) => entry.active).map((entry) => <option key={entry.id} value={entry.id}>{entry.referenceCode ? `${entry.referenceCode} · ` : ''}{entry.name}</option>)}</select></label>
                                <label className="is-wide">Privatadresse<input autoComplete="street-address" value={newGuest.addressLine1} onChange={(event) => setNewGuest((current) => ({ ...current, addressLine1: event.target.value }))} /></label>
                                <label>PLZ<input autoComplete="postal-code" value={newGuest.postalCode} onChange={(event) => setNewGuest((current) => ({ ...current, postalCode: event.target.value }))} /></label>
                                <label>Ort<input autoComplete="address-level2" value={newGuest.city} onChange={(event) => setNewGuest((current) => ({ ...current, city: event.target.value }))} /></label>
                                <label>Wohnsitzland<input maxLength="2" value={newGuest.countryCode} onChange={(event) => setNewGuest((current) => ({ ...current, countryCode: event.target.value.toUpperCase() }))} /></label>
                                <label>Kennzeichen<input value={newGuest.vehiclePlate} onChange={(event) => setNewGuest((current) => ({ ...current, vehiclePlate: event.target.value.toUpperCase() }))} /></label>
                                <label className="is-wide">Zimmerwünsche<textarea placeholder="z. B. ruhig, hohe Etage, Badewanne, Parkett, King-Bett" value={newGuest.roomPreferences} onChange={(event) => setNewGuest((current) => ({ ...current, roomPreferences: event.target.value }))} /></label>
                                <label className="pms-checkbox"><input type="checkbox" checked={newGuest.vip} onChange={(event) => setNewGuest((current) => ({ ...current, vip: event.target.checked }))} /> VIP-Gast</label>
                                <PmsGuestProfileDetails value={newGuest} onChange={setNewGuest} organizations={organizations} />
                                <label className="is-wide">Gastnotizen<textarea value={newGuest.notes} onChange={(event) => setNewGuest((current) => ({ ...current, notes: event.target.value }))} /></label>
                            </div>
                        </fieldset>
                    )}

                    <div className="pms-work-card">
                        <p>Nach der Gast- oder Firmenauswahl stehen passende Vertragsraten in der Verfügbarkeit bereit.</p>
                        <button type="button" onClick={() => { setStep(2); setAvailabilityRefresh((current) => current + 1); }}>Passende Firmenraten prüfen</button>
                    </div>

                    {!walkIn && (
                        <label className="pms-checkbox">
                            <input type="checkbox" checked={collectRegistration} onChange={(event) => setCollectRegistration(event.target.checked)} />
                            Meldedaten jetzt an der Rezeption vollständig erfassen
                        </label>
                    )}

                    {registrationEnabled && (
                        <fieldset className="pms-work-card">
                            <legend>{walkIn ? 'Pflicht-Meldedaten für den Walk-in' : 'Meldedaten'}</legend>
                            <p>Die vollständige Ausweisnummer wird nur an den Server übertragen und nicht im Browser gespeichert.</p>
                            <div className="pms-form-grid">
                                <label className="is-wide">Adresse<input autoComplete="street-address" value={registration.addressLine} onChange={(event) => setRegistration((current) => ({ ...current, addressLine: event.target.value }))} required /></label>
                                <label>PLZ<input autoComplete="postal-code" value={registration.postalCode} onChange={(event) => setRegistration((current) => ({ ...current, postalCode: event.target.value }))} required /></label>
                                <label>Ort<input autoComplete="address-level2" value={registration.city} onChange={(event) => setRegistration((current) => ({ ...current, city: event.target.value }))} required /></label>
                                <label>Wohnsitzland (ISO-Code)<input autoCapitalize="characters" maxLength="2" value={registration.countryCode} onChange={(event) => setRegistration((current) => ({ ...current, countryCode: event.target.value.toUpperCase() }))} required /></label>
                                <label>Nationalität (ISO-Code)<input autoCapitalize="characters" maxLength="2" value={registration.nationalityCode} onChange={(event) => setRegistration((current) => ({ ...current, nationalityCode: event.target.value.toUpperCase() }))} required /></label>
                                <label className="is-wide">Ausweis- oder Passnummer<input autoComplete="off" value={registration.documentNumber} onChange={(event) => setRegistration((current) => ({ ...current, documentNumber: event.target.value }))} minLength="4" required /></label>
                                <label>Kennzeichen (optional)<input value={registration.vehiclePlate} onChange={(event) => setRegistration((current) => ({ ...current, vehiclePlate: event.target.value }))} /></label>
                                <label>Unterschrift / vollständiger Name<input value={registration.signatureName} onChange={(event) => setRegistration((current) => ({ ...current, signatureName: event.target.value }))} required /></label>
                                <label className="pms-checkbox is-wide"><input type="checkbox" checked={registration.privacyConsent} onChange={(event) => setRegistration((current) => ({ ...current, privacyConsent: event.target.checked }))} required /> Der Gast bestätigt die Vollständigkeit und Richtigkeit der Angaben.</label>
                            </div>
                        </fieldset>
                    )}
                    <div className="pms-form-actions"><button type="button" onClick={previousStep}>Zurück</button><button type="submit" className="is-primary">Weiter zur Prüfung</button></div>
                </form>
            )}

            {step === 4 && (
                <form className="pms-operations-stack" onSubmit={submitBooking}>
                    <div className="pms-work-card-heading"><div><span className="pms-eyebrow">Schritt 4</span><h3 ref={stepHeadingRef} tabIndex={-1}>Angaben prüfen und atomar abschliessen</h3></div></div>
                    <div className="pms-record-list" aria-label="Prüfübersicht">
                        <article className="pms-record"><div><span>Aufenthalt</span><strong>{stay.arrivalDate} bis {stay.departureDate}</strong><small>{stay.adults} Erwachsene · {stay.children} Kinder{stay.childAges?.length ? ` (${stay.childAges.join(', ')} Jahre)` : ''} · {RESERVATION_SOURCES.find(([value]) => value === (walkIn ? 'WALK_IN' : stay.source))?.[1]}</small></div></article>
                        <article className="pms-record"><div><span>Zimmer & Rate</span><strong>{selectedRate?.roomTypeName} · {selectedRate?.name}</strong><small>{roomId ? `Zimmer ${suitableRooms.find((room) => String(room.id) === String(roomId))?.number}` : 'Zimmer wird später zugewiesen'} · {formatMoney(selectedRate?.totalAmount, selectedRate?.currencyCode || property?.currencyCode)}</small></div></article>
                        <article className="pms-record"><div><span>Hauptgast</span><strong>{guestMode === 'existing' ? `${selectedGuest?.firstName ?? ''} ${selectedGuest?.lastName ?? ''}` : `${newGuest.firstName} ${newGuest.lastName}`}</strong><small>{registrationEnabled ? 'Meldedaten vollständig erfasst' : 'Meldedaten werden später erfasst'}{checkInNow ? ' · Direkter Check-in' : ''}</small>{(guestMode === 'existing' ? selectedGuest?.roomPreferences : newGuest.roomPreferences) && <small>Zimmerwunsch beachten: {guestMode === 'existing' ? selectedGuest.roomPreferences : newGuest.roomPreferences}</small>}</div></article>
                    </div>
                    <div className="pms-form-grid">
                        <label>Garantieart<select value={guaranteeStatus} onChange={(event) => setGuaranteeStatus(event.target.value)}>{GUARANTEE_OPTIONS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
                        <label className="is-wide">Reservierungsnotizen<textarea value={reservationNotes} onChange={(event) => setReservationNotes(event.target.value)} /></label>
                    </div>
                    <div className="pms-form-actions">
                        <button type="button" onClick={previousStep} disabled={submitting}>Zurück</button>
                        <button type="submit" className="is-primary" disabled={!canManage || submitting}>
                            {submitting
                                ? 'Wird sicher gespeichert…'
                                : walkIn
                                    ? 'Walk-in anlegen und einchecken'
                                    : 'Reservierung verbindlich anlegen'}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
};

export default PmsReceptionBookingFlow;
