import { useEffect, useMemo, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import { HOUSEKEEPING_STATUS_LABELS, ROOM_OPERATIONAL_STATUS_LABELS, RESERVATION_STATUS_LABELS, ROOM_BLOCK_TYPE_LABELS, getPmsEnumLabel } from './pmsTerminology.js';
import { hotelToday, addPlanDays, planDayLabel, layoutRoomEvents } from './pmsRoomPlan.js';
import '../../styles/PmsRoomPlan.css';

const emptyFilters = { search: '', roomTypeId: '', floor: '', bedType: '', housekeepingSection: '', housekeepingStatus: '', operationalStatus: '', guests: '', features: [], onlyAvailable: false, includeInactive: false };
const statusLabel = (value) => getPmsEnumLabel(RESERVATION_STATUS_LABELS, value);
const message = (error) => error?.response?.data?.detail || error?.response?.data?.message || 'Der Zimmerplan konnte nicht geladen werden.';
const optionList = (items = []) => items.map((value) => <option key={value} value={value}>{value}</option>);

export default function PmsRoomPlan({ property, canManage = false, businessDate, refreshKey, onSelectReservation, onOperationsChange }) {
    const [today, setToday] = useState(() => hotelToday(property?.timezone));
    const [from, setFrom] = useState('');
    const [days, setDays] = useState(30);
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(50);
    const [filters, setFilters] = useState(emptyFilters);
    const [search, setSearch] = useState('');
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [revision, setRevision] = useState(0);
    const [moving, setMoving] = useState(false);
    const [selected, setSelected] = useState(null);
    const viewport = useRef(null);
    const start = from || today;

    useEffect(() => {
        const update = () => setToday(hotelToday(property?.timezone));
        update();
        const timer = setInterval(update, 30_000);
        return () => clearInterval(timer);
    }, [property?.timezone]);

    useEffect(() => {
        const timer = setTimeout(() => {
            setFilters((current) => current.search === search ? current : { ...current, search });
            setPage(0);
        }, 250);
        return () => clearTimeout(timer);
    }, [search]);

    useEffect(() => {
        setFrom(''); setPage(0); setFilters(emptyFilters); setSearch(''); setSelected(null); setData(null);
    }, [property?.id]);

    useEffect(() => {
        if (!property?.id) return undefined;
        let current = true;
        setLoading(true); setError('');
        const params = { from: start, days, page, size, ...filters };
        Object.keys(params).forEach((key) => { if (params[key] === '' || (Array.isArray(params[key]) && !params[key].length)) delete params[key]; });
        // Repeated unbracketed keys match Spring's List<String> request binding.
        api.get(`/api/pms/properties/${property.id}/room-plan`, { params, paramsSerializer: { indexes: null } })
            .then((response) => {
                if (!current) return;
                if (!Array.isArray(response?.data?.rooms)) throw new Error('Ungültige Zimmerplan-Antwort');
                setData(response.data);
                if (viewport.current) viewport.current.scrollTop = 0;
            })
            .catch((failure) => { if (current) { setError(message(failure)); setData(null); } })
            .finally(() => { if (current) setLoading(false); });
        return () => { current = false; };
    }, [property?.id, start, days, page, size, filters, refreshKey, revision]);

    const visibleDays = useMemo(() => Array.from({ length: days }, (_, i) => addPlanDays(start, i)), [start, days]);
    const eventsByRoom = useMemo(() => {
        const reservations = new Map(); const blocks = new Map();
        for (const entry of data?.reservations || []) reservations.set(entry.roomId, [...(reservations.get(entry.roomId) || []), entry]);
        for (const entry of data?.blocks || []) blocks.set(entry.roomId, [...(blocks.get(entry.roomId) || []), entry]);
        return new Map((data?.rooms || []).map((room) => [room.id, layoutRoomEvents(reservations.get(room.id) || [], blocks.get(room.id) || [], start, days)]));
    }, [data, start, days]);

    const changeFilter = (key, value) => { setFilters((current) => ({ ...current, [key]: value })); setPage(0); setSelected(null); };
    const changeStart = (value) => { setFrom(value); setPage(0); setSelected(null); if (viewport.current) viewport.current.scrollLeft = 0; };
    const toggleFeature = (feature) => changeFilter('features', filters.features.includes(feature) ? filters.features.filter((value) => value !== feature) : [...filters.features, feature]);
    const reset = () => { setFilters(emptyFilters); setSearch(''); setPage(0); setSelected(null); };
    const move = async (reservationId, target) => {
        if (!canManage || loading || moving) return;
        const reservation = data?.reservations.find((entry) => String(entry.id) === reservationId);
        if (!reservation || reservation.roomId === target.id) return;
        if (target.operationalStatus !== 'IN_SERVICE' || !target.active) { setError('Dieses Zimmer ist nicht zuweisbar.'); return; }
        if (reservation.roomTypeId !== target.roomTypeId) { setError('Verschieben ist nur innerhalb desselben Zimmertyps möglich.'); return; }
        if (data.blocks.some((block) => block.roomId === target.id && ['OUT_OF_ORDER', 'OWNER_USE'].includes(block.type)
            && block.startDate < reservation.departureDate && block.endDate > reservation.arrivalDate)) {
            setError(`Zimmer ${target.number} ist im Aufenthalt gesperrt und nicht zuweisbar.`); return;
        }
        setMoving(true); setError(''); setNotice('');
        try {
            const response = await api.post(`/api/pms/reservations/${reservation.id}/move-room`, { roomId: target.id, reason: 'Verschoben im fortlaufenden Zimmerplan' }, { params: { businessDate } });
            onOperationsChange?.(response.data);
            setNotice(`Reservierung ${reservation.confirmationCode} auf Zimmer ${target.number} verschoben.`);
            setSelected(null); setRevision((value) => value + 1);
        } catch (failure) { setError(message(failure)); }
        finally { setMoving(false); }
    };
    const facets = data?.filters || {};
    const currentPage = data?.page ?? page;
    const total = data?.totalRooms || 0;
    const pageCount = Math.max(1, Math.ceil(total / size));
    const gridStyle = { gridTemplateColumns: `224px repeat(${days}, 94px)` };

    return <section className="pms-work-card pms-timeline" aria-label="Fortlaufender Zimmerplan">
        <div className="pms-timeline-heading">
            <div><span className="pms-eyebrow">Belegung & Zimmerauswahl</span><h3>Zimmerplan</h3><p>{property?.name} · {formatPmsDate(start)} – {formatPmsDate(addPlanDays(start, days - 1))}</p></div>
            <button type="button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>Aktualisieren</button>
        </div>
        <div className="pms-timeline-toolbar">
            <div className="pms-timeline-date-navigation">
                <button type="button" aria-label="Vorheriger Zeitraum" onClick={() => changeStart(addPlanDays(start, -days))}>←</button>
                <button type="button" onClick={() => changeStart('')} aria-pressed={!from}>Heute</button>
                <button type="button" aria-label="Nächster Zeitraum" onClick={() => changeStart(addPlanDays(start, days))}>→</button>
            </div>
            <label>Startdatum<input type="date" value={start} onChange={(event) => changeStart(event.target.value)} /></label>
            <label>Zeitraum<select value={days} onChange={(event) => { setDays(Number(event.target.value)); setPage(0); }}>
                {[7, 10, 14, 30, 60, 90].map((value) => <option key={value} value={value}>{value} Tage</option>)}
            </select></label>
            <label className="pms-timeline-search">Zimmer suchen<input type="search" maxLength="200" value={search} placeholder="Nummer, Name, Kategorie, Ausstattung" onChange={(event) => setSearch(event.target.value)} /></label>
            <label>Zimmer je Seite<select value={size} onChange={(event) => { setSize(Number(event.target.value)); setPage(0); }}>{[25, 50, 100].map((value) => <option key={value}>{value}</option>)}</select></label>
        </div>
        <div className="pms-timeline-filters">
            <label>Zimmertyp<select value={filters.roomTypeId} onChange={(event) => changeFilter('roomTypeId', event.target.value)}><option value="">Alle Typen</option>{(facets.roomTypes || []).map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
            <label>Etage<select value={filters.floor} onChange={(event) => changeFilter('floor', event.target.value)}><option value="">Alle Etagen</option>{optionList(facets.floors)}</select></label>
            <label>Bettenart<select value={filters.bedType} onChange={(event) => changeFilter('bedType', event.target.value)}><option value="">Alle Bettenarten</option>{optionList(facets.bedTypes)}</select></label>
            <label>Mind. Personen<input type="number" min="1" max="100" value={filters.guests} onChange={(event) => changeFilter('guests', event.target.value)} placeholder="Beliebig" /></label>
            <label>Housekeeping<select value={filters.housekeepingStatus} onChange={(event) => changeFilter('housekeepingStatus', event.target.value)}><option value="">Alle Zustände</option>{Object.entries(HOUSEKEEPING_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label>Betriebsstatus<select value={filters.operationalStatus} onChange={(event) => changeFilter('operationalStatus', event.target.value)}><option value="">Alle Betriebsstatus</option>{Object.entries(ROOM_OPERATIONAL_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label>Reinigungsbereich<select value={filters.housekeepingSection} onChange={(event) => changeFilter('housekeepingSection', event.target.value)}><option value="">Alle Bereiche</option>{optionList(facets.housekeepingSections)}</select></label>
        </div>
        <details className="pms-timeline-features" open={filters.features.length > 0 || undefined}>
            <summary>Ausstattung · {filters.features.length ? `${filters.features.length} gewählt` : 'Badewanne, ruhige Lage und weitere Merkmale'}</summary>
            <p>Alle gewählten Merkmale müssen gemeinsam zutreffen. Merkmale werden in den Hotel-Einstellungen beim Zimmer gepflegt; die Bettenart beim Zimmertyp.</p>
            <div>{(facets.features || []).map((feature) => <label key={feature}><input type="checkbox" checked={filters.features.includes(feature)} onChange={() => toggleFeature(feature)} />{feature}</label>)}</div>
            {!facets.features?.length && <p>Noch keine Ausstattungsmerkmale für dieses Hotel hinterlegt.</p>}
        </details>
        <div className="pms-timeline-switches">
            <label><input type="checkbox" checked={filters.onlyAvailable} onChange={(event) => changeFilter('onlyAvailable', event.target.checked)} />Im gesamten Zeitraum frei und zuweisbar</label>
            <label><input type="checkbox" checked={filters.includeInactive} onChange={(event) => changeFilter('includeInactive', event.target.checked)} />Inaktive Zimmer einblenden</label>
            <button type="button" onClick={reset}>Filter zurücksetzen</button>
        </div>
        <div className="pms-timeline-legend" aria-label="Farblegende">
            <span className="pms-timeline-key is-free">Frei</span><span className="pms-timeline-key is-confirmed">Bestätigt</span><span className="pms-timeline-key is-checked_in">Im Haus</span><span className="pms-timeline-key is-tentative">Option</span><span className="pms-timeline-key is-checked_out">Abgereist</span><span className="pms-timeline-key is-block">Gesperrt / Eigennutzung</span><span className="pms-timeline-key is-limited">Eingeschränkt</span>
        </div>
        <p className="pms-timeline-help">Datum = Übernachtung; Abreise gibt das Zimmer frei. Die linke Zimmerfarbe kennzeichnet den Zimmertyp. Heute folgt automatisch dem Hoteldatum{property?.timezone ? ` (${property.timezone})` : ''}.{canManage ? ' Reservierungen zum Verschieben auf eine andere Zimmerzeile ziehen.' : ''}</p>
        {error && <div className="pms-timeline-error" role="alert">{error}<button type="button" onClick={() => setRevision((value) => value + 1)}>Erneut laden</button></div>}
        {notice && <p role="status">{notice}</p>}
        {loading && <p role="status">Zimmerplan wird geladen …</p>}
        {!error && data && <>
            <div className="pms-timeline-scroll" ref={viewport} tabIndex={0} aria-label="Zimmer und Tage, horizontal und vertikal scrollbar" aria-busy={loading || moving}>
                <div role="table" aria-label="Belegung nach Zimmer und Tag" aria-rowcount={total + 1} aria-colcount={days + 1} className={`pms-timeline-sheet${loading ? ' is-loading' : ''}`} style={{ width: 224 + days * 94 }}>
                    <div role="row" className="pms-timeline-header" style={gridStyle}>
                        <div role="columnheader" className="pms-timeline-corner"><span>A</span>Zimmer / Kategorie</div>
                        {visibleDays.map((date) => <div role="columnheader" key={date} className={date === today ? 'is-today' : ''} aria-current={date === today ? 'date' : undefined}>{planDayLabel(date)}{date === today && <small>Heute</small>}</div>)}
                    </div>
                    {(data.rooms || []).map((room, rowIndex) => {
                        const events = eventsByRoom.get(room.id) || [];
                        const height = Math.max(76, 16 + (Math.max(0, ...events.map((event) => event.lane)) + 1) * 33);
                        const unavailable = !room.active || room.operationalStatus !== 'IN_SERVICE';
                        return <div role="row" aria-rowindex={currentPage * size + rowIndex + 2} key={room.id} className={`pms-timeline-row${unavailable ? ' is-unavailable' : ''}`} style={{ ...gridStyle, minHeight: height }}
                            onDragOver={(event) => { if (canManage && !unavailable && !loading) event.preventDefault(); }}
                            onDrop={(event) => { event.preventDefault(); if (!unavailable) move(event.dataTransfer.getData('text/reservation-id'), room); }}>
                            <div role="rowheader" className={`pms-timeline-room is-type-${Number(room.roomTypeId) % 6}`} title={[room.name, room.features, room.bedType, `${room.maxOccupancy} Personen`, room.housekeepingSection].filter(Boolean).join(' · ')}>
                                <strong>{room.number} <small>{room.floor ? `Et. ${room.floor}` : ''}</small></strong><span>{room.roomTypeName}</span><small>{getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, room.housekeepingStatus)}{unavailable ? ` · ${getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus)}` : ''}</small>
                            </div>
                            {visibleDays.map((date, index) => <div role="cell" key={date} className={`pms-timeline-cell${date === today ? ' is-today' : ''}${[0, 6].includes(new Date(`${date}T12:00:00Z`).getUTCDay()) ? ' is-weekend' : ''}`} style={{ gridColumn: index + 2 }} aria-label={`Zimmer ${room.number}, ${planDayLabel(date)}${unavailable ? ', nicht zuweisbar' : ''}`} />)}
                            {events.map((event) => {
                                const reservation = event.kind === 'reservation';
                                const label = reservation ? `${event.entry.guestName} · ${event.entry.confirmationCode} · ${statusLabel(event.entry.status)}` : `${getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, event.entry.type)} · ${event.entry.reason}`;
                                const tone = reservation ? event.entry.status.toLowerCase() : event.entry.type === 'OUT_OF_SERVICE' ? 'limited' : 'block';
                                return <button type="button" key={`${event.kind}-${event.entry.id}`} className={`pms-timeline-event is-${tone}`} disabled={loading || moving}
                                    style={{ gridColumn: `${event.start + 2} / span ${event.end - event.start}`, marginTop: 8 + event.lane * 33 }}
                                    title={`${label}\n${formatPmsDate(event.startDate)} – ${formatPmsDate(event.endDate)}`}
                                    draggable={canManage && reservation && ['TENTATIVE', 'CONFIRMED', 'CHECKED_IN'].includes(event.entry.status)}
                                    onDragStart={(e) => e.dataTransfer.setData('text/reservation-id', String(event.entry.id))}
                                    onClick={() => setSelected({ ...event, room })}>
                                    {event.startDate < start ? '‹ ' : ''}{reservation ? event.entry.guestName : getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, event.entry.type)}{event.endDate > addPlanDays(start, days) ? ' ›' : ''}
                                </button>;
                            })}
                        </div>;
                    })}
                </div>
            </div>
            {!data.rooms.length && !loading && <p>Keine Zimmer passen zu diesen Filtern.</p>}
            <div className="pms-timeline-pagination">
                <span>{total ? `${currentPage * size + 1}–${Math.min((currentPage + 1) * size, total)} von ${total} Zimmern` : '0 Zimmer'} · Seite {currentPage + 1} / {pageCount}</span>
                <button type="button" disabled={loading || currentPage === 0} onClick={() => setPage(currentPage - 1)}>Vorherige Zimmer</button>
                <button type="button" disabled={loading || currentPage + 1 >= pageCount} onClick={() => setPage(currentPage + 1)}>Weitere Zimmer</button>
            </div>
        </>}
        {selected && <aside className="pms-timeline-selection" aria-label="Ausgewählter Aufenthalt">
            <div><strong>Zimmer {selected.room.number} · {selected.kind === 'reservation' ? selected.entry.guestName : getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, selected.entry.type)}</strong><p>{formatPmsDate(selected.startDate)} – {formatPmsDate(selected.endDate)} · {selected.kind === 'reservation' ? `${selected.entry.confirmationCode} · ${statusLabel(selected.entry.status)}` : selected.entry.reason}</p>{selected.entry.guestPreferenceSnapshot && <p>Zimmerwünsche: {selected.entry.guestPreferenceSnapshot}</p>}{selected.room.features && <p>Ausstattung: {selected.room.features}</p>}</div>
            {selected.kind === 'reservation' && onSelectReservation && <button type="button" onClick={() => onSelectReservation(selected.entry)}>Reservierung öffnen</button>}
            <button type="button" onClick={() => setSelected(null)}>Schließen</button>
        </aside>}
    </section>;
}

PmsRoomPlan.propTypes = {
    property: PropTypes.object, canManage: PropTypes.bool, businessDate: PropTypes.string,
    refreshKey: PropTypes.any, onSelectReservation: PropTypes.func, onOperationsChange: PropTypes.func,
};
