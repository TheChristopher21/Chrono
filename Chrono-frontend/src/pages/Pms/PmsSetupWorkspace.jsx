import { useEffect, useMemo, useState } from 'react';
import api from '../../utils/api.js';
import {
    ROOM_OPERATIONAL_STATUS_LABELS,
    getPmsEnumLabel,
    getPmsEnumOptions,
} from './pmsTerminology.js';

const propertyDefaults = {
    code: '',
    name: '',
    legalName: '',
    countryCode: 'CH',
    currencyCode: 'CHF',
    timezone: 'Europe/Zurich',
    addressLine1: '',
    postalCode: '',
    city: '',
    phone: '',
    email: '',
    checkInTime: '15:00',
    checkOutTime: '11:00',
    active: true,
};

const roomTypeDefaults = {
    code: '',
    name: '',
    description: '',
    baseOccupancy: 1,
    maxOccupancy: 2,
    bedCount: 1,
    bedType: '',
    sortOrder: 0,
    active: true,
};

const roomDefaults = {
    roomTypeId: '',
    number: '',
    name: '',
    floor: '',
    housekeepingSection: '',
    operationalStatus: 'IN_SERVICE',
    active: true,
};

const cleanPropertyForForm = (property) => ({
    code: property.code ?? '',
    name: property.name ?? '',
    legalName: property.legalName ?? '',
    countryCode: property.countryCode ?? 'CH',
    currencyCode: property.currencyCode ?? 'CHF',
    timezone: property.timezone ?? 'Europe/Zurich',
    addressLine1: property.addressLine1 ?? '',
    postalCode: property.postalCode ?? '',
    city: property.city ?? '',
    phone: property.phone ?? '',
    email: property.email ?? '',
    checkInTime: (property.checkInTime ?? '15:00').slice(0, 5),
    checkOutTime: (property.checkOutTime ?? '11:00').slice(0, 5),
    active: property.active ?? true,
});

const errorMessage = (error) => error?.response?.data?.detail
    || error?.response?.data?.message
    || 'Die PMS-Daten konnten nicht gespeichert werden.';

const PmsSetupWorkspace = ({
    setup,
    activePropertyId,
    canManage,
    onSetupChange,
    onPropertyChange,
    onClose,
}) => {
    const properties = setup?.properties ?? [];
    const selectedProperty = useMemo(
        () => properties.find((property) => property.id === activePropertyId) ?? properties[0] ?? null,
        [activePropertyId, properties],
    );
    const [step, setStep] = useState(() => {
        if (properties.length === 0) return 'property';
        if ((properties[0]?.roomTypes?.length ?? 0) === 0) return 'room-types';
        return 'rooms';
    });
    const [newProperty, setNewProperty] = useState(properties.length === 0);
    const [propertyForm, setPropertyForm] = useState(() =>
        selectedProperty ? cleanPropertyForForm(selectedProperty) : propertyDefaults
    );
    const [roomTypeForm, setRoomTypeForm] = useState(roomTypeDefaults);
    const [roomForm, setRoomForm] = useState(roomDefaults);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        if (!newProperty) {
            setPropertyForm(selectedProperty ? cleanPropertyForForm(selectedProperty) : propertyDefaults);
        }
        setRoomForm((current) => ({
            ...current,
            roomTypeId: selectedProperty?.roomTypes?.some((roomType) => String(roomType.id) === String(current.roomTypeId))
                ? current.roomTypeId
                : selectedProperty?.roomTypes?.[0]?.id ?? '',
        }));
    }, [newProperty, selectedProperty]);

    const updateForm = (setter) => (event) => {
        const { name, type, checked, value } = event.target;
        setter((current) => ({
            ...current,
            [name]: type === 'checkbox' ? checked : value,
        }));
    };

    const submit = async (request) => {
        setSaving(true);
        setError('');
        setMessage('');
        try {
            const response = await request();
            onSetupChange(response.data);
            return response.data;
        } catch (requestError) {
            setError(errorMessage(requestError));
            return null;
        } finally {
            setSaving(false);
        }
    };

    const saveProperty = async (event) => {
        event.preventDefault();
        if (!canManage) return;
        const response = await submit(() => newProperty
            ? api.post('/api/pms/properties', propertyForm)
            : api.put(`/api/pms/properties/${selectedProperty.id}`, propertyForm));
        if (!response) return;

        const savedProperty = response.properties.find(
            (property) => property.code === propertyForm.code.trim().toUpperCase().replace(/\s+/g, '-')
        ) ?? response.properties[0];
        if (savedProperty) onPropertyChange(savedProperty.id);
        setNewProperty(false);
        setMessage('Hotel- und Betriebsdaten wurden gespeichert.');
        setStep('room-types');
    };

    const saveRoomType = async (event) => {
        event.preventDefault();
        if (!canManage || !selectedProperty) return;
        const payload = {
            ...roomTypeForm,
            baseOccupancy: Number(roomTypeForm.baseOccupancy),
            maxOccupancy: Number(roomTypeForm.maxOccupancy),
            bedCount: Number(roomTypeForm.bedCount),
            sortOrder: Number(roomTypeForm.sortOrder),
        };
        const response = await submit(() =>
            api.post(`/api/pms/properties/${selectedProperty.id}/room-types`, payload)
        );
        if (!response) return;

        const refreshedProperty = response.properties.find((property) => property.id === selectedProperty.id);
        setRoomTypeForm(roomTypeDefaults);
        setRoomForm((current) => ({
            ...current,
            roomTypeId: refreshedProperty?.roomTypes?.at(-1)?.id ?? '',
        }));
        setMessage('Der Zimmertyp wurde angelegt.');
        setStep('rooms');
    };

    const saveRoom = async (event) => {
        event.preventDefault();
        if (!canManage || !selectedProperty) return;
        const payload = {
            ...roomForm,
            roomTypeId: Number(roomForm.roomTypeId),
        };
        const response = await submit(() =>
            api.post(`/api/pms/properties/${selectedProperty.id}/rooms`, payload)
        );
        if (!response) return;

        setRoomForm((current) => ({
            ...roomDefaults,
            roomTypeId: current.roomTypeId,
            floor: current.floor,
            housekeepingSection: current.housekeepingSection,
        }));
        setMessage('Das Zimmer wurde angelegt und steht dem PMS jetzt als Bestand zur Verfügung.');
    };

    const chooseProperty = (event) => {
        const id = Number(event.target.value);
        setNewProperty(false);
        onPropertyChange(id);
        setMessage('');
        setError('');
    };

    return (
        <div className="pms-setup-backdrop" role="presentation" onMouseDown={onClose}>
            <section
                className="pms-setup-workspace"
                role="dialog"
                aria-modal="true"
                aria-labelledby="pms-setup-title"
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className="pms-setup-workspace-header">
                    <div>
                        <span className="pms-eyebrow">Hoteleinrichtung</span>
                        <h2 id="pms-setup-title">Hoteleinrichtung</h2>
                        <p>Grunddaten für Hotel, Zimmertypen und Zimmer.</p>
                    </div>
                    <div className="pms-setup-header-actions">
                        {properties.length > 0 && (
                            <select
                                aria-label="Aktives Hotel"
                                value={selectedProperty?.id ?? ''}
                                onChange={chooseProperty}
                            >
                                {properties.map((property) => (
                                    <option key={property.id} value={property.id}>{property.name}</option>
                                ))}
                            </select>
                        )}
                        {canManage && (!newProperty || properties.length > 0) && (
                            <button
                                type="button"
                                className="is-secondary"
                                onClick={() => {
                                    setNewProperty(true);
                                    setPropertyForm(propertyDefaults);
                                    setStep('property');
                                    setMessage('');
                                    setError('');
                                }}
                            >
                                Weiteres Hotel anlegen
                            </button>
                        )}
                        <button type="button" className="pms-setup-close" onClick={onClose} aria-label="Einrichtung schliessen">
                            ×
                        </button>
                    </div>
                </header>

                <nav className="pms-setup-tabs" aria-label="Einrichtungsschritte">
                    <button
                        type="button"
                        className={step === 'property' ? 'is-active' : ''}
                        onClick={() => setStep('property')}
                    >
                        <span>1</span> Hotel
                    </button>
                    <button
                        type="button"
                        className={step === 'room-types' ? 'is-active' : ''}
                        disabled={!selectedProperty || newProperty}
                        onClick={() => setStep('room-types')}
                    >
                        <span>2</span> Zimmertypen
                    </button>
                    <button
                        type="button"
                        className={step === 'rooms' ? 'is-active' : ''}
                        disabled={!selectedProperty || newProperty}
                        onClick={() => setStep('rooms')}
                    >
                        <span>3</span> Zimmer
                    </button>
                </nav>

                {!canManage && (
                    <div className="pms-setup-readonly">Du hast Lesezugriff. Änderungen erfordern die Berechtigung „Verwalten“.</div>
                )}
                {error && <div className="pms-setup-feedback is-error" role="alert">{error}</div>}
                {message && <div className="pms-setup-feedback is-success" role="status">{message}</div>}

                <div className="pms-setup-workspace-body">
                    {step === 'property' && (
                        <form className="pms-setup-form" onSubmit={saveProperty}>
                            <div className="pms-setup-form-heading">
                                <div>
                                    <h3>{newProperty ? 'Hotel anlegen' : 'Hotel bearbeiten'}</h3>
                                    <p>Betriebliche Identität, Lokalisierung und Standardzeiten.</p>
                                </div>
                                <span>{newProperty ? 'Neu' : selectedProperty?.code}</span>
                            </div>
                            <div className="pms-form-grid">
                                <label>
                                    Hotelcode
                                    <input name="code" required maxLength="32" value={propertyForm.code} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="is-wide">
                                    Hotelname
                                    <input name="name" required maxLength="160" value={propertyForm.name} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="is-wide">
                                    Firmenname / rechtlicher Betreiber
                                    <input name="legalName" maxLength="180" value={propertyForm.legalName} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Land (ISO-Code)
                                    <input name="countryCode" required maxLength="2" value={propertyForm.countryCode} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Währung
                                    <input name="currencyCode" required maxLength="3" value={propertyForm.currencyCode} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="is-wide">
                                    Zeitzone
                                    <input name="timezone" required value={propertyForm.timezone} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="is-wide">
                                    Adresse
                                    <input name="addressLine1" maxLength="180" value={propertyForm.addressLine1} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    PLZ
                                    <input name="postalCode" maxLength="20" value={propertyForm.postalCode} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Ort
                                    <input name="city" maxLength="120" value={propertyForm.city} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Check-in ab
                                    <input type="time" name="checkInTime" required value={propertyForm.checkInTime} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Check-out bis
                                    <input type="time" name="checkOutTime" required value={propertyForm.checkOutTime} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="is-wide">
                                    E-Mail
                                    <input type="email" name="email" maxLength="190" value={propertyForm.email} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label>
                                    Telefon
                                    <input name="phone" maxLength="60" value={propertyForm.phone} onChange={updateForm(setPropertyForm)} />
                                </label>
                                <label className="pms-checkbox-field">
                                    <input type="checkbox" name="active" checked={propertyForm.active} onChange={updateForm(setPropertyForm)} />
                                    Betrieb aktiv
                                </label>
                            </div>
                            <div className="pms-form-actions">
                                <button type="submit" disabled={!canManage || saving}>
                                    {saving ? 'Speichert…' : 'Hotel speichern'}
                                </button>
                            </div>
                        </form>
                    )}

                    {step === 'room-types' && selectedProperty && (
                        <div className="pms-setup-split">
                            <form className="pms-setup-form" onSubmit={saveRoomType}>
                                <div className="pms-setup-form-heading">
                                    <div>
                                        <h3>Zimmertyp anlegen</h3>
                                        <p>Verkaufs- und Belegungsstruktur für {selectedProperty.name}.</p>
                                    </div>
                                </div>
                                <div className="pms-form-grid">
                                    <label>
                                        Code
                                        <input name="code" required maxLength="32" value={roomTypeForm.code} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label className="is-wide">
                                        Bezeichnung
                                        <input name="name" required maxLength="120" value={roomTypeForm.name} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label className="is-full">
                                        Beschreibung
                                        <textarea name="description" maxLength="1000" value={roomTypeForm.description} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label>
                                        Standardbelegung
                                        <input type="number" name="baseOccupancy" min="1" max="20" value={roomTypeForm.baseOccupancy} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label>
                                        Maximalbelegung
                                        <input type="number" name="maxOccupancy" min="1" max="20" value={roomTypeForm.maxOccupancy} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label>
                                        Betten
                                        <input type="number" name="bedCount" min="1" max="20" value={roomTypeForm.bedCount} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                    <label className="is-wide">
                                        Bettentyp
                                        <input name="bedType" maxLength="60" placeholder="z. B. Doppelbett" value={roomTypeForm.bedType} onChange={updateForm(setRoomTypeForm)} />
                                    </label>
                                </div>
                                <div className="pms-form-actions">
                                    <button type="submit" disabled={!canManage || saving}>
                                        {saving ? 'Speichert…' : 'Zimmertyp anlegen'}
                                    </button>
                                </div>
                            </form>

                            <section className="pms-setup-inventory">
                                <div>
                                    <span className="pms-eyebrow">Vorhanden</span>
                                    <h3>{selectedProperty.roomTypes.length} Zimmertypen</h3>
                                </div>
                                {selectedProperty.roomTypes.length === 0 ? (
                                    <p className="pms-setup-empty">Noch keine Zimmertypen angelegt.</p>
                                ) : (
                                    <ul>
                                        {selectedProperty.roomTypes.map((roomType) => (
                                            <li key={roomType.id}>
                                                <span>{roomType.code}</span>
                                                <div>
                                                    <strong>{roomType.name}</strong>
                                                    <small>{roomType.baseOccupancy}–{roomType.maxOccupancy} Gäste · {roomType.roomCount} Zimmer</small>
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </section>
                        </div>
                    )}

                    {step === 'rooms' && selectedProperty && (
                        <div className="pms-setup-split">
                            <form className="pms-setup-form" onSubmit={saveRoom}>
                                <div className="pms-setup-form-heading">
                                    <div>
                                        <h3>Zimmer anlegen</h3>
                                        <p>Konkretes Hotelzimmer für Belegung, Reinigung und Wartung.</p>
                                    </div>
                                </div>
                                {selectedProperty.roomTypes.length === 0 ? (
                                    <div className="pms-setup-empty">
                                        Lege zuerst mindestens einen Zimmertyp an.
                                        <button type="button" onClick={() => setStep('room-types')}>Zu den Zimmertypen</button>
                                    </div>
                                ) : (
                                    <>
                                        <div className="pms-form-grid">
                                            <label className="is-wide">
                                                Zimmertyp
                                                <select name="roomTypeId" required value={roomForm.roomTypeId} onChange={updateForm(setRoomForm)}>
                                                    <option value="">Bitte wählen</option>
                                                    {selectedProperty.roomTypes.map((roomType) => (
                                                        <option key={roomType.id} value={roomType.id}>{roomType.code} · {roomType.name}</option>
                                                    ))}
                                                </select>
                                            </label>
                                            <label>
                                                Zimmernummer
                                                <input name="number" required maxLength="40" value={roomForm.number} onChange={updateForm(setRoomForm)} />
                                            </label>
                                            <label className="is-wide">
                                                Zimmername (optional)
                                                <input name="name" maxLength="120" placeholder="optional" value={roomForm.name} onChange={updateForm(setRoomForm)} />
                                            </label>
                                            <label>
                                                Etage
                                                <input name="floor" maxLength="40" value={roomForm.floor} onChange={updateForm(setRoomForm)} />
                                            </label>
                                            <label className="is-wide">
                                                Reinigungsbereich
                                                <input name="housekeepingSection" maxLength="80" value={roomForm.housekeepingSection} onChange={updateForm(setRoomForm)} />
                                            </label>
                                            <label className="is-wide">
                                                Betriebs-/Verkaufsstatus
                                                <select name="operationalStatus" value={roomForm.operationalStatus} onChange={updateForm(setRoomForm)}>
                                                    {getPmsEnumOptions(ROOM_OPERATIONAL_STATUS_LABELS).map(({ value, label }) => (
                                                        <option key={value} value={value}>{label}</option>
                                                    ))}
                                                </select>
                                            </label>
                                        </div>
                                        <div className="pms-form-actions">
                                            <button type="submit" disabled={!canManage || saving}>
                                                {saving ? 'Speichert…' : 'Zimmer anlegen'}
                                            </button>
                                        </div>
                                    </>
                                )}
                            </form>

                            <section className="pms-setup-inventory">
                                <div>
                                    <span className="pms-eyebrow">Zimmerbestand</span>
                                    <h3>{selectedProperty.rooms.length} Zimmer</h3>
                                </div>
                                {selectedProperty.rooms.length === 0 ? (
                                    <p className="pms-setup-empty">Noch keine Zimmer angelegt.</p>
                                ) : (
                                    <ul>
                                        {selectedProperty.rooms.map((room) => (
                                            <li key={room.id}>
                                                <span>{room.number}</span>
                                                <div>
                                                    <strong>{room.name || room.roomTypeName}</strong>
                                                    <small>{room.roomTypeCode}{room.floor ? ` · Etage ${room.floor}` : ''}</small>
                                                </div>
                                                <i className={`is-${room.operationalStatus.toLowerCase().replaceAll('_', '-')}`}>
                                                    {getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus)}
                                                </i>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </section>
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
};

export default PmsSetupWorkspace;
