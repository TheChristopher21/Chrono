import { useEffect, useMemo, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import api from '../../utils/api.js';
import { formatPmsDate } from './pmsFormatting.js';
import { HOUSEKEEPING_STATUS_LABELS, ROOM_OPERATIONAL_STATUS_LABELS, RESERVATION_STATUS_LABELS, ROOM_BLOCK_TYPE_LABELS, getPmsEnumLabel } from './pmsTerminology.js';
import { hotelToday, addPlanDays, planDayLabel, layoutRoomEvents } from './pmsRoomPlan.js';
import '../../styles/PmsRoomPlan.css';
import usePmsLiveRefresh from './usePmsLiveRefresh.js';
import usePmsRoomWindow from './usePmsRoomWindow.js';

const emptyFilters = { search: '', roomTypeId: '', floor: '', bedType: '', housekeepingSection: '', housekeepingStatus: '', operationalStatus: '', guests: '', features: [], onlyAvailable: false, includeInactive: false };
const statusLabel = (value) => getPmsEnumLabel(RESERVATION_STATUS_LABELS, value);
const message = (error) => error?.response?.data?.detail || error?.response?.data?.message || 'Der Zimmerplan konnte nicht geladen werden.';
const optionList = (items = []) => items.map((value) => <option key={value} value={value}>{value}</option>);

export default function PmsRoomPlan({ property, canManage = false, canManageHousekeeping = false, businessDate, refreshKey, onSelectReservation, onOperationsChange }) {
    const [today, setToday] = useState(() => hotelToday(property?.timezone));
    const [from, setFrom] = useState('');
    const [days, setDays] = useState(30);
    const [scroll, setScroll] = useState({ top: 0, height: 650 });
    const [range, setRange] = useState(null);
    const [jumpRoom, setJumpRoom] = useState('');
    const [batch, setBatch] = useState(null);
    const [filters, setFilters] = useState(emptyFilters);
    const [search, setSearch] = useState('');
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [revision, setRevision] = useState(0);
    const [moving, setMoving] = useState(false);
    const [selected, setSelected] = useState(null);
    const viewport = useRef(null);
    const heights = useRef(new Map());
    const start = from || today;
    usePmsLiveRefresh(() => setRevision((value) => value + 1), { enabled: !moving, propertyId: property?.id });
    const [windowRows, setWindowRows] = useState({ first: 0, last: 18 });
    const { data, loading, error: loadError, filters: roomFacets } = usePmsRoomWindow(property?.id, { from: start, days, ...filters }, windowRows.first, windowRows.last, `${refreshKey}:${revision}`);
    const resetPosition = () => {
        heights.current.clear(); setRange(null); setSelected(null); setBatch(null);
        setScroll((value) => ({ ...value, top: 0 })); setWindowRows({ first: 0, last: 18 });
        if (viewport.current) viewport.current.scrollTop = 0;
    };

    useEffect(() => {
        const update = () => setToday(hotelToday(property?.timezone));
        update();
        const timer = setInterval(update, 30_000);
        return () => clearInterval(timer);
    }, [property?.timezone]);

    useEffect(() => {
        if (search === filters.search) return undefined;
        const timer = setTimeout(() => {
            setFilters((current) => current.search === search ? current : { ...current, search });
            resetPosition();
        }, 250);
        return () => clearTimeout(timer);
    }, [search]);

    useEffect(() => {
        setFrom(''); setFilters(emptyFilters); setSearch(''); resetPosition();
    }, [property?.id]);

    const visibleDays = useMemo(() => Array.from({ length: days }, (_, i) => addPlanDays(start, i)), [start, days]);
    const eventsByRoom = useMemo(() => {
        const reservations = new Map(); const blocks = new Map();
        for (const entry of data?.reservations || []) reservations.set(entry.roomId, [...(reservations.get(entry.roomId) || []), entry]);
        for (const entry of data?.blocks || []) blocks.set(entry.roomId, [...(blocks.get(entry.roomId) || []), entry]);
        return new Map((data?.rooms || []).map((room) => [room.id, layoutRoomEvents(reservations.get(room.id) || [], blocks.get(room.id) || [], start, days)]));
    }, [data, start, days]);

    const changeFilter = (key, value) => { setFilters((current) => ({ ...current, [key]: value })); resetPosition(); };
    const changeStart = (value) => { setFrom(value); resetPosition(); if (viewport.current) viewport.current.scrollLeft = 0; };
    const toggleFeature = (feature) => changeFilter('features', filters.features.includes(feature) ? filters.features.filter((value) => value !== feature) : [...filters.features, feature]);
    const reset = () => { setFilters(emptyFilters); setSearch(''); resetPosition(); };
    const move = async (reservationId, target, segmentId) => {
        if (!canManage || loading || moving || loadError) return;
        const reservation = data?.reservations.find((entry) => String(entry.id) === reservationId && (!segmentId || String(entry.segmentId || '') === segmentId));
        if (!reservation || reservation.roomId === target.id) return;
        if (target.operationalStatus !== 'IN_SERVICE' || !target.active) { setError('Dieses Zimmer ist nicht zuweisbar.'); return; }
        if (reservation.roomTypeId !== target.roomTypeId) { setError('Verschieben ist nur innerhalb desselben Zimmertyps möglich.'); return; }
        const effectiveDate = [today, reservation.arrivalDate, reservation.segmentStartDate || reservation.arrivalDate].sort().at(-1);
        if (effectiveDate >= reservation.departureDate) { setError('Ein bereits beendeter Aufenthalt kann nicht verschoben werden.'); return; }
        if (data.blocks.some((block) => block.roomId === target.id && ['OUT_OF_ORDER', 'OWNER_USE'].includes(block.type)
            && block.startDate < reservation.departureDate && block.endDate > effectiveDate)) {
            setError(`Zimmer ${target.number} ist im Aufenthalt gesperrt und nicht zuweisbar.`); return;
        }
        setMoving(true); setError(''); setNotice('');
        try {
            const response = await api.post(`/api/pms/reservations/${reservation.id}/move-room`, { roomId: target.id, effectiveDate, reason: 'Verschoben im fortlaufenden Zimmerplan' }, { params: { businessDate } });
            onOperationsChange?.(response.data);
            setNotice(`Reservierung ${reservation.confirmationCode} auf Zimmer ${target.number} verschoben.`);
            setSelected(null); setRevision((value) => value + 1);
        } catch (failure) { setError(message(failure)); }
        finally { setMoving(false); }
    };
    const facets = data?.filters || roomFacets || {};
    const total = data?.totalRooms || 0;
    const gridStyle = { gridTemplateColumns: `224px repeat(${days}, 94px)` };
    const roomsByIndex = useMemo(() => new Map((data?.rooms || []).map((room) => [room.planIndex, room])), [data]);
    const offsets = useMemo(() => {
        for (const room of data?.rooms || []) {
            const events = eventsByRoom.get(room.id) || [];
            heights.current.set(room.planIndex, Math.max(76, 16 + (Math.max(0, ...events.map((event) => event.lane)) + 1) * 33));
        }
        const result = [0];
        for (let index = 0; index < total; index += 1) result.push(result[index] + (heights.current.get(index) || 76));
        return result;
    }, [total, data, eventsByRoom]);
    const rowAt = (position) => {
        let low = 0; let high = Math.max(0, total - 1);
        while (low < high) { const middle = Math.ceil((low + high) / 2); if (offsets[middle] <= position) low = middle; else high = middle - 1; }
        return low;
    };
    const visibleFirst = Math.max(0, rowAt(Math.max(0, scroll.top - 56)) - 5);
    const visibleLast = Math.min(Math.max(0, total - 1), rowAt(scroll.top + scroll.height) + 5);
    useEffect(() => { setWindowRows((old) => old.first === visibleFirst && old.last === visibleLast ? old : { first: visibleFirst, last: visibleLast }); }, [visibleFirst, visibleLast]);
    const scrollToRow = (index) => {
        const bounded = Math.max(0, Math.min(total - 1, index));
        if (viewport.current) viewport.current.scrollTop = offsets[bounded] || 0;
        setScroll((old) => ({ ...old, top: offsets[bounded] || 0 }));
    };
    const pickCell = (row, column, extend = false) => {
        setRange((old) => ({ anchor: extend && old ? old.anchor : { row, column }, focus: { row, column } }));
        viewport.current?.focus({ preventScroll: true });
    };
    const bounds = range ? { top: Math.min(range.anchor.row, range.focus.row), bottom: Math.max(range.anchor.row, range.focus.row), left: Math.min(range.anchor.column, range.focus.column), right: Math.max(range.anchor.column, range.focus.column) } : null;
    const selectionText = () => {
        if (!bounds) return '';
        const lines = [['Zimmer', ...visibleDays.slice(bounds.left, bounds.right + 1).map(planDayLabel)]];
        for (let row = bounds.top; row <= bounds.bottom; row += 1) {
            const room = roomsByIndex.get(row);
            if (!room) throw new Error('Bitte den ausgewählten Bereich zuerst laden oder die Auswahl verkleinern.');
            lines.push([room.number, ...visibleDays.slice(bounds.left, bounds.right + 1).map((date) => (eventsByRoom.get(room.id) || [])
                .filter((event) => event.startDate <= date && date < event.endDate)
                .map((event) => event.kind === 'reservation' ? `${event.entry.guestName} (${event.entry.confirmationCode})` : getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, event.entry.type)).join(' / ') || (room.active && room.operationalStatus === 'IN_SERVICE' ? 'Frei' : 'Nicht zuweisbar'))]);
        }
        // Prevent Excel formulas from profile data and preserve the TSV shape.
        return lines.map((line) => line.map((value) => String(value).replace(/[\t\r\n]/g, ' ').replace(/^([=+@-])/, "'$1")).join('\t')).join('\n');
    };
    const copySelection = async () => {
        try { const text = selectionText(); if (!text) return; await navigator.clipboard.writeText(text); setNotice('Auswahl als Tabelle kopiert.'); }
        catch (failure) { setError(failure.message || 'Kopieren nicht möglich. Bitte Strg+C verwenden.'); }
    };
    const submitBatch = async (event) => {
        event.preventDefault(); if (!bounds || !canManageHousekeeping || moving || loadError) return;
        setError('');
        const rooms = Array.from({ length: bounds.bottom - bounds.top + 1 }, (_, index) => roomsByIndex.get(bounds.top + index));
        if (rooms.some((room) => !room)) { setError('Bitte alle ausgewählten Zimmer zuerst laden oder die Auswahl verkleinern.'); return; }
        setMoving(true);
        try {
            const { data: result } = await api.post(`/api/pms/properties/${property.id}/housekeeping/work-orders/batch`, {
                roomIds: rooms.map((room) => room.id), from: visibleDays[bounds.left], toExclusive: addPlanDays(visibleDays[bounds.right], 1),
                workType: batch.workType, assignedTo: batch.assignedTo || null, notes: batch.notes || null, priority: 50, estimatedMinutes: 30,
            });
            setNotice(`${result.created} Aufgaben angelegt; ${result.alreadyExisting} bereits vorhandene Aufgaben beibehalten.`);
            setBatch(null); setRange(null); setRevision((value) => value + 1);
        } catch (failure) { setError(message(failure)); }
        finally { setMoving(false); }
    };
    const keyDown = (event) => {
        if (event.target !== viewport.current) return;
        if (event.key === 'Escape') { setRange(null); setSelected(null); return; }
        if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown'].includes(event.key)) return;
        event.preventDefault();
        let { row, column } = range?.focus || { row: visibleFirst, column: 0 };
        if (event.key === 'ArrowUp') row -= 1;
        if (event.key === 'ArrowDown') row += 1;
        if (event.key === 'ArrowLeft') column -= 1;
        if (event.key === 'ArrowRight') column += 1;
        if (event.key === 'PageUp') row -= Math.max(1, Math.floor(scroll.height / 76));
        if (event.key === 'PageDown') row += Math.max(1, Math.floor(scroll.height / 76));
        if (event.key === 'Home') { column = 0; if (event.ctrlKey || event.metaKey) row = 0; }
        if (event.key === 'End') { column = days - 1; if (event.ctrlKey || event.metaKey) row = total - 1; }
        row = Math.max(0, Math.min(total - 1, row)); column = Math.max(0, Math.min(days - 1, column));
        pickCell(row, column, event.shiftKey);
        if (offsets[row] < scroll.top || offsets[row + 1] > scroll.top + scroll.height - 56) scrollToRow(row);
        if (viewport.current) {
            const left = column * 94;
            if (left < viewport.current.scrollLeft) viewport.current.scrollLeft = left;
            else if (left + 318 > viewport.current.scrollLeft + viewport.current.clientWidth) viewport.current.scrollLeft = left + 318 - viewport.current.clientWidth;
        }
    };

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
            <label>Zeitraum<select value={days} onChange={(event) => { setDays(Number(event.target.value)); resetPosition(); }}>
                {[7, 10, 14, 30, 60, 90].map((value) => <option key={value} value={value}>{value} Tage</option>)}
            </select></label>
            <label className="pms-timeline-search">Zimmer suchen<input type="search" maxLength="200" value={search} placeholder="Nummer, Name, Kategorie, Ausstattung" onChange={(event) => setSearch(event.target.value)} /></label>
            <label>Zu Zeile springen<input type="number" min="1" max={total || 1} value={jumpRoom} placeholder={`1–${total}`} onChange={(event) => setJumpRoom(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && jumpRoom) scrollToRow(Number(jumpRoom) - 1); }} /></label>
            <button type="button" disabled={!jumpRoom || !total} onClick={() => scrollToRow(Number(jumpRoom) - 1)}>Gehe zu</button>
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
        <p className="pms-timeline-help">Durchgehend scrollen · Pfeiltasten zum Navigieren · Umschalt + Klick oder Pfeiltasten zum Markieren · Strg+C zum Kopieren nach Excel.</p>
        {bounds && <div className="pms-timeline-range-bar" role="region" aria-label="Tabellenauswahl"><strong>{bounds.bottom - bounds.top + 1} Zimmer × {bounds.right - bounds.left + 1} Tage</strong><span>{planDayLabel(visibleDays[bounds.left])} – {planDayLabel(visibleDays[bounds.right])}</span><button type="button" onClick={copySelection}>Auswahl kopieren</button>{canManageHousekeeping && <button type="button" disabled={moving || (bounds.bottom - bounds.top + 1) * (bounds.right - bounds.left + 1) > 500 || bounds.bottom - bounds.top >= 100 || bounds.right - bounds.left >= 31} onClick={() => setBatch({ workType: 'CLEAN', assignedTo: '', notes: '' })}>Sammelauftrag planen</button>}<button type="button" disabled={moving} onClick={() => { setRange(null); setBatch(null); }}>Auswahl aufheben</button></div>}
        {bounds && batch && <form className="pms-timeline-batch" onSubmit={submitBatch}><fieldset disabled={moving}><legend>Sammelauftrag für die markierten Zimmer und Tage</legend><div className="pms-timeline-filters"><label>Arbeitsart<select value={batch.workType} onChange={(event) => setBatch({ ...batch, workType: event.target.value })}><option value="CLEAN">Reinigung</option><option value="INSPECTION">Kontrolle</option><option value="TURNDOWN">Abendservice</option></select></label><label>Zuweisung<input maxLength={120} value={batch.assignedTo} onChange={(event) => setBatch({ ...batch, assignedTo: event.target.value })} /></label><label>Arbeitsnotiz<input maxLength={1000} value={batch.notes} onChange={(event) => setBatch({ ...batch, notes: event.target.value })} /></label></div><p>Je Zimmer, Tag und Arbeitsart wird höchstens ein Auftrag angelegt. Vorhandene Aufträge behalten ihre Angaben.</p><button type="submit">{moving ? 'Wird angelegt …' : 'Aufträge anlegen'}</button><button type="button" onClick={() => setBatch(null)}>Abbrechen</button></fieldset></form>}
        {(error || loadError) && <div className="pms-timeline-error" role="alert">{error || message(loadError)}<button type="button" onClick={() => { setError(''); setRevision((value) => value + 1); }}>Erneut laden</button></div>}
        {notice && <p role="status">{notice}</p>}
        {loading && <p role="status">Zimmerplan wird geladen …</p>}
        {data && <>
            <div className="pms-timeline-scroll" ref={viewport} tabIndex={0} aria-label="Zimmer und Tage, horizontal und vertikal scrollbar" aria-busy={loading || moving}
                onKeyDown={keyDown} onScroll={(event) => setScroll({ top: event.currentTarget.scrollTop, height: event.currentTarget.clientHeight || 650 })}
                onCopy={(event) => { if (!range) return; try { event.clipboardData.setData('text/plain', selectionText()); event.preventDefault(); } catch (failure) { setError(failure.message); } }}>
                <div role="table" aria-label="Belegung nach Zimmer und Tag" aria-rowcount={total + 1} aria-colcount={days + 1} className="pms-timeline-sheet" style={{ width: 224 + days * 94 }}>
                    <div role="row" className="pms-timeline-header" style={gridStyle}>
                        <div role="columnheader" className="pms-timeline-corner"><span>A</span>Zimmer / Kategorie</div>
                        {visibleDays.map((date) => <div role="columnheader" key={date} className={date === today ? 'is-today' : ''} aria-current={date === today ? 'date' : undefined}>{planDayLabel(date)}{date === today && <small>Heute</small>}</div>)}
                    </div>
                    {visibleFirst > 0 && <div aria-hidden="true" style={{ height: offsets[visibleFirst] }} />}
                    {Array.from({ length: total ? visibleLast - visibleFirst + 1 : 0 }, (_, index) => visibleFirst + index).map((rowIndex) => {
                        const room = roomsByIndex.get(rowIndex);
                        if (!room) return <div key={`loading-${rowIndex}`} className="pms-timeline-row pms-timeline-placeholder" aria-hidden="true" style={{ ...gridStyle, height: heights.current.get(rowIndex) || 76 }}><span className="pms-timeline-room">Zeile {rowIndex + 1} · wird geladen …</span></div>;
                        const events = eventsByRoom.get(room.id) || [];
                        const height = Math.max(76, 16 + (Math.max(0, ...events.map((event) => event.lane)) + 1) * 33);
                        const unavailable = !room.active || room.operationalStatus !== 'IN_SERVICE';
                        return <div role="row" aria-rowindex={rowIndex + 2} key={room.id} className={`pms-timeline-row${unavailable ? ' is-unavailable' : ''}`} style={{ ...gridStyle, height }}
                            onDragOver={(event) => { if (canManage && !unavailable && !loading) event.preventDefault(); }}
                            onDrop={(event) => { event.preventDefault(); if (!unavailable) move(event.dataTransfer.getData('text/reservation-id'), room, event.dataTransfer.getData('text/reservation-segment-id')); }}>
                            <div role="rowheader" className={`pms-timeline-room is-type-${Number(room.roomTypeId) % 6}`} onClick={(event) => { pickCell(rowIndex, 0, event.shiftKey); setRange((old) => ({ ...old, focus: { row: rowIndex, column: days - 1 } })); }} title={[room.name, room.features, room.bedType, `${room.maxOccupancy} Personen`, room.housekeepingSection].filter(Boolean).join(' · ')}>
                                <strong>{room.number} <small>{room.floor ? `Et. ${room.floor}` : ''}</small></strong><span>{room.roomTypeName}</span><small>{getPmsEnumLabel(HOUSEKEEPING_STATUS_LABELS, room.housekeepingStatus)}{unavailable ? ` · ${getPmsEnumLabel(ROOM_OPERATIONAL_STATUS_LABELS, room.operationalStatus)}` : ''}</small>
                            </div>
                            {visibleDays.map((date, index) => <div role="cell" key={date} data-selected={bounds && rowIndex >= bounds.top && rowIndex <= bounds.bottom && index >= bounds.left && index <= bounds.right ? 'true' : undefined} data-focus={range?.focus.row === rowIndex && range?.focus.column === index ? 'true' : undefined} onClick={(event) => pickCell(rowIndex, index, event.shiftKey)} className={`pms-timeline-cell${date === today ? ' is-today' : ''}${[0, 6].includes(new Date(`${date}T12:00:00Z`).getUTCDay()) ? ' is-weekend' : ''}`} style={{ gridColumn: index + 2 }} aria-label={`Zimmer ${room.number}, ${planDayLabel(date)}${unavailable ? ', nicht zuweisbar' : ''}`} />)}
                            {events.map((event) => {
                                const reservation = event.kind === 'reservation';
                                const label = reservation ? `${event.entry.guestName} · ${event.entry.confirmationCode} · ${statusLabel(event.entry.status)}` : `${getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, event.entry.type)} · ${event.entry.reason}`;
                                const tone = reservation ? event.entry.status.toLowerCase() : event.entry.type === 'OUT_OF_SERVICE' ? 'limited' : 'block';
                                return <button type="button" key={`${event.kind}-${event.entry.id}-${event.entry.segmentId || 'stay'}`} className={`pms-timeline-event is-${tone}`} disabled={loading || moving}
                                    style={{ gridColumn: `${event.start + 2} / span ${event.end - event.start}`, marginTop: 8 + event.lane * 33 }}
                                    title={`${label}\n${formatPmsDate(event.startDate)} – ${formatPmsDate(event.endDate)}`}
                                    draggable={canManage && reservation && ['TENTATIVE', 'CONFIRMED', 'CHECKED_IN'].includes(event.entry.status)}
                                    onDragStart={(e) => { e.dataTransfer.setData('text/reservation-id', String(event.entry.id)); e.dataTransfer.setData('text/reservation-segment-id', String(event.entry.segmentId || '')); }}
                                    onClick={() => setSelected({ ...event, room })}>
                                    {event.startDate < start ? '‹ ' : ''}{reservation ? event.entry.guestName : getPmsEnumLabel(ROOM_BLOCK_TYPE_LABELS, event.entry.type)}{event.endDate > addPlanDays(start, days) ? ' ›' : ''}
                                </button>;
                            })}
                        </div>;
                    })}
                    {visibleLast + 1 < total && <div aria-hidden="true" style={{ height: offsets[total] - offsets[visibleLast + 1] }} />}
                </div>
            </div>
            {!data.rooms.length && !loading && <p>Keine Zimmer passen zu diesen Filtern.</p>}
            <div className="pms-timeline-pagination">
                <span>{total} Zimmer · fortlaufende Tabelle · sichtbare Zeilen {total ? rowAt(scroll.top) + 1 : 0}–{Math.min(total, rowAt(scroll.top + scroll.height) + 1)}</span>
                <button type="button" disabled={!total || visibleFirst === 0} onClick={() => scrollToRow(0)}>Zum ersten Zimmer</button>
                <button type="button" disabled={!total || visibleLast + 1 >= total} onClick={() => scrollToRow(Math.min(total - 1, visibleLast + 1))}>Weitere Zimmer</button>
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
    property: PropTypes.object, canManage: PropTypes.bool, canManageHousekeeping: PropTypes.bool, businessDate: PropTypes.string,
    refreshKey: PropTypes.any, onSelectReservation: PropTypes.func, onOperationsChange: PropTypes.func,
};
