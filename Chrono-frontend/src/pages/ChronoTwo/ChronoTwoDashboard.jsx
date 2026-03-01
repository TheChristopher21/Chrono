import { useEffect, useMemo, useState } from "react";
import { Canvas } from "@react-three/fiber";
import { Line, OrbitControls } from "@react-three/drei";
import api from "../../utils/api.js";
import "../../styles/ChronoTwoDashboard.css";

const prettifyMetricKey = (metric) =>
    metric
        .replace(/_/g, " ")
        .replace(/\b\w/g, (char) => char.toUpperCase());

const initialForms = {
    product: { name: "", category: "", costPrice: "", weightKg: "" },
    slotting: { productId: "", weightKg: 1, volumeCubicM: 0.1, zonePreference: "A", expectedTurnoverDays: 7 },
    sourcing: { productId: "", quantity: 10 },
    accounting: { purchaseOrderId: "PO-", orderedAmount: "", receivedAmount: "", invoicedAmount: "" },
    inbound: { productId: "", quantity: 10, dockLocationId: "Dock-1" },
    pick: { items: [{ productId: "", quantity: 1 }] },
    nlp: "Wie hoch ist unser Überbestand?",
    sensor: { locationId: "", temperature: 21, humidity: 48, weight: 100 },
};

const defaultPreference = (supplierId) => ({
    supplierId,
    weightPrice: 0.4,
    weightReliability: 0.4,
    weightSustainability: 0.2,
});

const LocationCube = ({ position, occupancyRatio, height, highlight }) => {
    const baseColor = occupancyRatio > 0.85 ? "#ff5f56" : occupancyRatio > 0.5 ? "#ffa500" : "#1abc9c";
    const color = highlight ? "#ff4757" : baseColor;
    return (
        <mesh position={position}>
            <boxGeometry args={[0.9, height, 0.9]} />
            <meshStandardMaterial color={color} opacity={0.8} transparent />
        </mesh>
    );
};

const ChronoTwoDashboard = () => {
    const [products, setProducts] = useState([]);
    const [locations, setLocations] = useState([]);
    const [inventory, setInventory] = useState([]);
    const [kpis, setKpis] = useState({ kpis: {}, trends: {} });
    const [returns, setReturns] = useState([]);
    const [forecast, setForecast] = useState(null);
    const [slotting, setSlotting] = useState(null);
    const [sourcing, setSourcing] = useState(null);
    const [accounting, setAccounting] = useState(null);
    const [inbound, setInbound] = useState(null);
    const [pickRoute, setPickRoute] = useState(null);
    const [nlpResponse, setNlpResponse] = useState(null);
    const [movementLedger, setMovementLedger] = useState([]);
    const [sensorForm, setSensorForm] = useState({ ...initialForms.sensor });
    const [sensorData, setSensorData] = useState({});
    const [formState, setFormState] = useState(initialForms);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        const load = async () => {
            try {
                setLoading(true);
                const [prodRes, locRes, invRes, kpiRes, returnsRes, ledgerRes] = await Promise.all([
                    api.get("/chrono2/products"),
                    api.get("/chrono2/locations"),
                    api.get("/chrono2/inventory"),
                    api.get("/chrono2/analytics/kpis"),
                    api.get("/chrono2/outbound/returns"),
                    api.get("/chrono2/blockchain/movement"),
                ]);
                setProducts(prodRes.data);
                setLocations(locRes.data);
                setInventory(invRes.data);
                setKpis(kpiRes.data);
                setReturns(returnsRes.data);
                setMovementLedger(ledgerRes.data);
                if (prodRes.data.length > 0) {
                    fetchForecast(prodRes.data[0].id);
                }
            } catch (err) {
                setError(err.message ?? "Fehler beim Laden der Chrono 2.0 Daten");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, []);

    const occupancyByLocation = useMemo(() => {
        const map = new Map();
        locations.forEach((loc) => {
            map.set(loc.id, { capacity: loc.capacity, occupied: loc.occupied ?? 0 });
        });
        inventory.forEach((item) => {
            const entry = map.get(item.locationId);
            if (entry) {
                entry.occupied += item.quantity;
            }
        });
        return map;
    }, [locations, inventory]);

    const locationScale = 0.5;

    const routeLocationIds = useMemo(() => new Set(pickRoute?.waypoints?.map((wp) => wp.locationId) ?? []), [pickRoute]);

    const routePoints = useMemo(() => {
        if (!pickRoute?.waypoints?.length) {
            return [];
        }
        const points = [[0, 0, 0]];
        pickRoute.waypoints.forEach((wp) => {
            points.push([wp.x * locationScale, wp.z * locationScale, wp.y * locationScale]);
        });
        return points;
    }, [pickRoute, locationScale]);

    const fetchForecast = async (productId) => {
        try {
            const response = await api.get(`/chrono2/inventory/${productId}/forecast`);
            setForecast(response.data);
        } catch (err) {
            setError(err.message ?? "Vorhersage konnte nicht geladen werden");
        }
    };

    const handleFormChange = (key, field, value) => {
        setFormState((prev) => ({
            ...prev,
            [key]: { ...prev[key], [field]: value },
        }));
    };

    const submitProduct = async (evt) => {
        evt.preventDefault();
        try {
            const payload = {
                ...formState.product,
                costPrice: formState.product.costPrice ? Number(formState.product.costPrice) : undefined,
                weightKg: formState.product.weightKg ? Number(formState.product.weightKg) : undefined,
            };
            const response = await api.post("/chrono2/products", payload);
            setProducts((prev) => [response.data, ...prev.filter((p) => p.id !== response.data.id)]);
            setFormState((prev) => ({ ...prev, product: initialForms.product }));
        } catch (err) {
            setError(err.message ?? "Produkt konnte nicht gespeichert werden");
        }
    };

    const submitSlotting = async (evt) => {
        evt.preventDefault();
        try {
            const payload = {
                ...formState.slotting,
                weightKg: Number(formState.slotting.weightKg),
                volumeCubicM: Number(formState.slotting.volumeCubicM),
                expectedTurnoverDays: Number(formState.slotting.expectedTurnoverDays),
            };
            const response = await api.post("/chrono2/slotting", payload);
            setSlotting(response.data);
        } catch (err) {
            setError(err.message ?? "Smart Slotting fehlgeschlagen");
        }
    };

    const submitSensor = async (evt) => {
        evt.preventDefault();
        try {
            const payload = {
                ...sensorForm,
                temperature: Number(sensorForm.temperature),
                humidity: Number(sensorForm.humidity),
                weight: Number(sensorForm.weight),
            };
            await api.post("/chrono2/iot", payload);
            const refreshed = await api.get(`/chrono2/iot/${payload.locationId}`);
            setSensorData((prev) => ({ ...prev, [payload.locationId]: refreshed.data }));
            setSensorForm({ ...initialForms.sensor });
            setError(null);
        } catch (err) {
            setError(err.message ?? "IoT-Übermittlung fehlgeschlagen");
        }
    };

    const submitSourcing = async (evt) => {
        evt.preventDefault();
        try {
            const preferences = [defaultPreference("SUP-001"), defaultPreference("SUP-002"), defaultPreference("SUP-003")];
            const response = await api.post("/chrono2/procurement/sourcing", {
                ...formState.sourcing,
                quantity: Number(formState.sourcing.quantity),
                preferences,
            });
            setSourcing(response.data);
        } catch (err) {
            setError(err.message ?? "Smart Sourcing fehlgeschlagen");
        }
    };

    const submitAccounting = async (evt) => {
        evt.preventDefault();
        try {
            const response = await api.post("/chrono2/procurement/accounting", {
                ...formState.accounting,
                orderedAmount: Number(formState.accounting.orderedAmount),
                receivedAmount: Number(formState.accounting.receivedAmount),
                invoicedAmount: Number(formState.accounting.invoicedAmount),
            });
            setAccounting(response.data);
        } catch (err) {
            setError(err.message ?? "Autonomous Accounting fehlgeschlagen");
        }
    };

    const submitInbound = async (evt) => {
        evt.preventDefault();
        try {
            const response = await api.post("/chrono2/procurement/mobile-inbound", {
                ...formState.inbound,
                quantity: Number(formState.inbound.quantity),
            });
            setInbound(response.data);
        } catch (err) {
            setError(err.message ?? "Inbound Guidance fehlgeschlagen");
        }
    };

    const submitPick = async (evt) => {
        evt.preventDefault();
        try {
            const payload = {
                items: formState.pick.items.map((item) => ({
                    productId: item.productId,
                    quantity: Number(item.quantity),
                })),
            };
            const response = await api.post("/chrono2/outbound/pick-route", payload);
            setPickRoute(response.data);
        } catch (err) {
            setError(err.message ?? "Pick-by-Vision Planung fehlgeschlagen");
        }
    };

    const submitNlp = async (evt) => {
        evt.preventDefault();
        try {
            const response = await api.post("/chrono2/analytics/nlp", { query: formState.nlp });
            setNlpResponse(response.data);
        } catch (err) {
            setError(err.message ?? "NLP-Anfrage konnte nicht beantwortet werden");
        }
    };

    const pickItems = formState.pick.items;

    const updatePickItem = (index, field, value) => {
        setFormState((prev) => {
            const items = [...prev.pick.items];
            items[index] = { ...items[index], [field]: value };
            return { ...prev, pick: { items } };
        });
    };

    const addPickItem = () => {
        setFormState((prev) => ({
            ...prev,
            pick: { items: [...prev.pick.items, { productId: "", quantity: 1 }] },
        }));
    };

    const removePickItem = (index) => {
        setFormState((prev) => ({
            ...prev,
            pick: { items: prev.pick.items.filter((_, i) => i !== index) },
        }));
    };

    const humanReadableKpis = Object.entries(kpis.kpis ?? {});
    const kpiTrends = kpis.trends ?? {};

    return (
        <div className="chrono-two admin-page">
            <header className="chrono-two__header">
                <div className="chrono-two__title">
                    <span className="chrono-two__pill">Release Candidate</span>
                    <h1>Chrono 2.0 – Intelligente Lagersteuerung</h1>
                    <p className="muted">KI, AR und Automatisierung nahtlos in einer Plattform.</p>
                </div>
                <div className="kpi-strip">
                    {humanReadableKpis.map(([key, value]) => {
                        const trend = typeof kpiTrends[key] === "number" ? kpiTrends[key] : undefined;
                        const trendClass = trend > 0 ? "kpi-trend kpi-trend--up" : trend < 0 ? "kpi-trend kpi-trend--down" : "kpi-trend";
                        const trendSymbol = trend > 0 ? "▲" : trend < 0 ? "▼" : "■";
                        return (
                            <div className="kpi" key={key}>
                                <span className="kpi-label">{prettifyMetricKey(key)}</span>
                                <span className="kpi-value">{value}</span>
                                {trend !== undefined && (
                                    <span className={trendClass}>
                                        {trendSymbol} {Math.abs(trend).toFixed(1)}%
                                    </span>
                                )}
                            </div>
                        );
                    })}
                </div>
            </header>

            {error && <div className="alert alert-error">{error}</div>}
            {loading && <div className="loading">Chrono 2.0 Daten werden geladen …</div>}

            <section className="grid grid-2">
                <article className="card">
                    <h2>3D-Lager-Zwilling</h2>
                    <p className="muted">WebGL-Ansicht des Lagers mit Echtzeit-Auslastung.</p>
                    <div className="three-wrapper">
                        <Canvas camera={{ position: [5, 5, 8] }}>
                            <ambientLight intensity={0.6} />
                            <pointLight position={[10, 10, 10]} />
                            {locations.map((loc) => {
                                const occupancy = occupancyByLocation.get(loc.id);
                                const ratio = occupancy ? Math.min(1, occupancy.occupied / Math.max(1, occupancy.capacity)) : 0;
                                const height = Math.max(0.4, ratio * 1.6 + 0.2);
                                return (
                                    <LocationCube
                                        key={loc.id}
                                        position={[loc.x * locationScale, loc.z * locationScale + height / 2, loc.y * locationScale]}
                                        occupancyRatio={ratio}
                                        height={height}
                                        highlight={routeLocationIds.has(loc.id)}
                                    />
                                );
                            })}
                            {routePoints.length > 1 && (
                                <Line points={routePoints} color="#ff4757" lineWidth={2} dashed={false} />
                            )}
                            {pickRoute?.waypoints?.map((wp) => (
                                <mesh key={`${wp.locationId}-${wp.productId}`} position={[wp.x * locationScale, wp.z * locationScale + 0.15, wp.y * locationScale]}>
                                    <sphereGeometry args={[0.2, 16, 16]} />
                                    <meshStandardMaterial color="#ff4757" />
                                </mesh>
                            ))}
                            <OrbitControls enablePan enableRotate enableZoom />
                        </Canvas>
                    </div>
                </article>

                <article className="card">
                    <h2>Stammdaten & KI-Datenanreicherung</h2>
                    <form className="form-grid" onSubmit={submitProduct}>
                        <label>
                            Produktname
                            <input
                                type="text"
                                value={formState.product.name}
                                onChange={(e) => handleFormChange("product", "name", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Kategorie
                            <input
                                type="text"
                                value={formState.product.category}
                                onChange={(e) => handleFormChange("product", "category", e.target.value)}
                            />
                        </label>
                        <label>
                            Einstandspreis (CHF)
                            <input
                                type="number"
                                step="0.01"
                                value={formState.product.costPrice}
                                onChange={(e) => handleFormChange("product", "costPrice", e.target.value)}
                            />
                        </label>
                        <label>
                            Gewicht (kg)
                            <input
                                type="number"
                                step="0.1"
                                value={formState.product.weightKg}
                                onChange={(e) => handleFormChange("product", "weightKg", e.target.value)}
                            />
                        </label>
                        <button className="btn primary" type="submit">Produkt anreichern</button>
                    </form>
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>Produkt</th>
                                    <th>Kategorie</th>
                                    <th>Gewicht</th>
                                    <th>VK-Preis</th>
                                    <th>Segment</th>
                                </tr>
                            </thead>
                            <tbody>
                                {products.map((product) => (
                                    <tr key={product.id} onClick={() => fetchForecast(product.id)}>
                                        <td>{product.name}</td>
                                        <td>{product.category}</td>
                                        <td>{product.weightKg?.toFixed?.(2)} kg</td>
                                        <td>{product.salesPrice} CHF</td>
                                        <td>{product.demandSegment}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </article>
            </section>

            <section className="grid grid-3">
                <article className="card">
                    <h3>Smart Slotting</h3>
                    <form className="form-grid" onSubmit={submitSlotting}>
                        <label>
                            Produkt-ID
                            <input
                                type="text"
                                value={formState.slotting.productId}
                                onChange={(e) => handleFormChange("slotting", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Gewicht (kg)
                            <input
                                type="number"
                                value={formState.slotting.weightKg}
                                onChange={(e) => handleFormChange("slotting", "weightKg", e.target.value)}
                            />
                        </label>
                        <label>
                            Volumen (m³)
                            <input
                                type="number"
                                value={formState.slotting.volumeCubicM}
                                onChange={(e) => handleFormChange("slotting", "volumeCubicM", e.target.value)}
                            />
                        </label>
                        <label>
                            Umschlagszeit (Tage)
                            <input
                                type="number"
                                value={formState.slotting.expectedTurnoverDays}
                                onChange={(e) => handleFormChange("slotting", "expectedTurnoverDays", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">Optimale Position berechnen</button>
                    </form>
                    {slotting && (
                        <div className="result">
                            <strong>Lagerplatz:</strong> {slotting.locationId}<br />
                            <strong>Confidence:</strong> {(slotting.confidence * 100).toFixed(1)} %<br />
                            <strong>Grund:</strong> {slotting.reason}
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>Predictive Inventory</h3>
                    {forecast ? (
                        <ul className="forecast-list">
                            {Object.entries(forecast.forecast).map(([date, value]) => (
                                <li key={date}>
                                    <span>{date}</span>
                                    <span>{value} Stk.</span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className="muted">Wähle ein Produkt, um die 6-Wochen-Vorhersage zu sehen.</p>
                    )}
                    {forecast && (
                        <div className="alerts">
                            {forecast.stockOutRisk && <span className="badge warning">Stock-Out Risiko</span>}
                            {forecast.overstockRisk && <span className="badge info">Überbestand möglich</span>}
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>IoT Monitoring</h3>
                    <form className="form-grid" onSubmit={submitSensor}>
                        <label>
                            Lagerplatz
                            <input
                                type="text"
                                value={sensorForm.locationId}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, locationId: e.target.value }))}
                                required
                            />
                        </label>
                        <label>
                            Temperatur (°C)
                            <input
                                type="number"
                                value={sensorForm.temperature}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, temperature: e.target.value }))}
                            />
                        </label>
                        <label>
                            Luftfeuchte (%)
                            <input
                                type="number"
                                value={sensorForm.humidity}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, humidity: e.target.value }))}
                            />
                        </label>
                        <label>
                            Gewicht (kg)
                            <input
                                type="number"
                                value={sensorForm.weight}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, weight: e.target.value }))}
                            />
                        </label>
                        <button className="btn" type="submit">Messwert senden</button>
                    </form>
                    <p className="muted">Werte werden revisionssicher dokumentiert.</p>
                    {Object.entries(sensorData).length > 0 && (
                        <ul className="ledger">
                            {Object.entries(sensorData).map(([loc, readings]) => {
                                const last = readings[readings.length - 1];
                                if (!last) return null;
                                return (
                                    <li key={loc}>
                                        {loc}: {last.temperature.toFixed?.(1)}°C · {last.humidity.toFixed?.(1)}% · {last.weight.toFixed?.(1)} kg
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </article>
            </section>

            <section className="grid grid-3">
                <article className="card">
                    <h3>Smart Sourcing</h3>
                    <form className="form-grid" onSubmit={submitSourcing}>
                        <label>
                            Produkt-ID
                            <input
                                type="text"
                                value={formState.sourcing.productId}
                                onChange={(e) => handleFormChange("sourcing", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Menge
                            <input
                                type="number"
                                value={formState.sourcing.quantity}
                                onChange={(e) => handleFormChange("sourcing", "quantity", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">Lieferanten bewerten</button>
                    </form>
                    {sourcing && (
                        <div className="result">
                            <strong>Empfehlung:</strong> {sourcing.recommended?.supplierName}<br />
                            <strong>Score:</strong> {sourcing.recommended?.score.toFixed?.(2)}
                            <ul className="muted">
                                {sourcing.rankedSuppliers?.map((supplier) => (
                                    <li key={supplier.supplierId}>
                                        {supplier.supplierName} · Score {supplier.score.toFixed(2)} · {supplier.leadTimeDays} Tage
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>Autonomous Accounting</h3>
                    <form className="form-grid" onSubmit={submitAccounting}>
                        <label>
                            Bestellnummer
                            <input
                                type="text"
                                value={formState.accounting.purchaseOrderId}
                                onChange={(e) => handleFormChange("accounting", "purchaseOrderId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Bestellt (CHF)
                            <input
                                type="number"
                                value={formState.accounting.orderedAmount}
                                onChange={(e) => handleFormChange("accounting", "orderedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Wareneingang (CHF)
                            <input
                                type="number"
                                value={formState.accounting.receivedAmount}
                                onChange={(e) => handleFormChange("accounting", "receivedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Rechnung (CHF)
                            <input
                                type="number"
                                value={formState.accounting.invoicedAmount}
                                onChange={(e) => handleFormChange("accounting", "invoicedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <button className="btn" type="submit">3-Wege-Abgleich prüfen</button>
                    </form>
                    {accounting && (
                        <div className="result">
                            <strong>Status:</strong> {accounting.autoApproved ? "Auto-Freigabe" : "Manuelle Prüfung"}<br />
                            <strong>Abweichung:</strong> {accounting.deviation} CHF
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>Mobile Inbound</h3>
                    <form className="form-grid" onSubmit={submitInbound}>
                        <label>
                            Produkt-ID
                            <input
                                type="text"
                                value={formState.inbound.productId}
                                onChange={(e) => handleFormChange("inbound", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            Menge
                            <input
                                type="number"
                                value={formState.inbound.quantity}
                                onChange={(e) => handleFormChange("inbound", "quantity", e.target.value)}
                            />
                        </label>
                        <label>
                            Rampe
                            <input
                                type="text"
                                value={formState.inbound.dockLocationId}
                                onChange={(e) => handleFormChange("inbound", "dockLocationId", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">Route anzeigen</button>
                    </form>
                    {inbound && (
                        <ul className="muted">
                            <li>Slot: {inbound.assignedLocationId}</li>
                            {inbound.navigation?.map((step, idx) => (
                                <li key={`${step.from}-${idx}`}>
                                    {step.from} ➜ {step.to} ({step.distanceMeters.toFixed(1)} m)
                                </li>
                            ))}
                        </ul>
                    )}
                </article>
            </section>

            <section className="grid grid-2">
                <article className="card">
                    <h3>Pick-by-Vision Routen</h3>
                    <form className="form-grid" onSubmit={submitPick}>
                        {pickItems.map((item, index) => (
                            <div key={`pick-${index}`} className="pick-item">
                                <label>
                                    Produkt-ID
                                    <input
                                        type="text"
                                        value={item.productId}
                                        onChange={(e) => updatePickItem(index, "productId", e.target.value)}
                                        required
                                    />
                                </label>
                                <label>
                                    Menge
                                    <input
                                        type="number"
                                        value={item.quantity}
                                        onChange={(e) => updatePickItem(index, "quantity", Number(e.target.value))}
                                        min={1}
                                    />
                                </label>
                                {pickItems.length > 1 && (
                                    <button type="button" className="btn ghost" onClick={() => removePickItem(index)}>
                                        Entfernen
                                    </button>
                                )}
                            </div>
                        ))}
                        <div className="pick-actions">
                            <button type="button" className="btn ghost" onClick={addPickItem}>Position hinzufügen</button>
                            <button type="submit" className="btn">Route planen</button>
                        </div>
                    </form>
                    {pickRoute && (
                        <div className="result">
                            <strong>Gesamtzeit:</strong> {pickRoute.estimatedDurationSeconds.toFixed?.(1)} s<br />
                            <strong>Distanz:</strong> {pickRoute.totalDistance.toFixed?.(1)} m
                            <ul>
                                {pickRoute.waypoints?.map((wp, idx) => (
                                    <li key={`${wp.locationId}-${idx}`}>
                                        {wp.productId} · {wp.locationId} · ETA {wp.etaSeconds.toFixed(1)} s
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>NLP Dashboard</h3>
                    <form className="form-inline" onSubmit={submitNlp}>
                        <input
                            type="text"
                            value={formState.nlp}
                            onChange={(e) => setFormState((prev) => ({ ...prev, nlp: e.target.value }))}
                            placeholder="Frag die Chrono-KI …"
                        />
                        <button className="btn" type="submit">Analysieren</button>
                    </form>
                    {nlpResponse && (
                        <div className="result">
                            <strong>{nlpResponse.interpretedMetric}</strong>
                            <pre>{JSON.stringify(nlpResponse.data, null, 2)}</pre>
                            <span className="muted">{nlpResponse.explanation}</span>
                        </div>
                    )}
                </article>
            </section>

            <section className="grid grid-2">
                <article className="card">
                    <h3>Zirkuläre Logistik</h3>
                    <ul>
                        {returns.map((item) => (
                            <li key={item.caseId}>
                                <strong>{item.productId}</strong> – {item.reason} · Status {item.status}
                            </li>
                        ))}
                    </ul>
                </article>

                <article className="card">
                    <h3>Blockchain-Bewegungen</h3>
                    <ul className="ledger">
                        {movementLedger.map((entry) => (
                            <li key={entry.id}>
                                {entry.productId}: {entry.fromLocation ?? "Anlieferung"} ➜ {entry.toLocation} · {entry.quantity} Stk.
                            </li>
                        ))}
                    </ul>
                </article>
            </section>
        </div>
    );
};

export default ChronoTwoDashboard;
