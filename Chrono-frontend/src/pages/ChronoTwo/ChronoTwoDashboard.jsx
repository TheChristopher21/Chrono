import { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { Canvas } from "@react-three/fiber";
import { Line, OrbitControls } from "@react-three/drei";
import api from "../../utils/api.js";
import { LanguageContext } from "../../context/LanguageContext.jsx";
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
    nlp: "",
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
    const { language } = useContext(LanguageContext);
    const l = useCallback((de, en) => (language === "en" ? en : de), [language]);
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
                setError(err.message ?? l("Fehler beim Laden der Chrono 2.0 Daten", "Failed to load Chrono 2.0 data"));
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [l]);

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
            setError(err.message ?? l("Vorhersage konnte nicht geladen werden", "Forecast could not be loaded"));
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
            setError(err.message ?? l("Produkt konnte nicht gespeichert werden", "Product could not be saved"));
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
            setError(err.message ?? l("Smart Slotting fehlgeschlagen", "Smart slotting failed"));
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
            setError(err.message ?? l("IoT-Übermittlung fehlgeschlagen", "IoT transmission failed"));
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
            setError(err.message ?? l("Smart Sourcing fehlgeschlagen", "Smart sourcing failed"));
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
            setError(err.message ?? l("Autonomous Accounting fehlgeschlagen", "Autonomous accounting failed"));
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
            setError(err.message ?? l("Inbound Guidance fehlgeschlagen", "Inbound guidance failed"));
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
            setError(err.message ?? l("Pick-by-Vision Planung fehlgeschlagen", "Pick-by-vision planning failed"));
        }
    };

    const submitNlp = async (evt) => {
        evt.preventDefault();
        try {
            const response = await api.post("/chrono2/analytics/nlp", { query: formState.nlp });
            setNlpResponse(response.data);
        } catch (err) {
            setError(err.message ?? l("NLP-Anfrage konnte nicht beantwortet werden", "The NLP request could not be answered"));
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
                    <h1>{l("Chrono 2.0 - Regelbasierte Lagersteuerung", "Chrono 2.0 - Rule-Based Warehouse Control")}</h1>
                    <p className="muted">{l("Heuristische Analysen, AR und Automatisierung nahtlos in einer Plattform.", "Heuristic analytics, AR and automation in one seamless platform.")}</p>
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
            {loading && <div className="loading">{l("Chrono 2.0 Daten werden geladen...", "Loading Chrono 2.0 data...")}</div>}

            <section className="grid grid-2">
                <article className="card">
                    <h2>{l("3D-Lager-Zwilling", "3D Warehouse Twin")}</h2>
                    <p className="muted">{l("WebGL-Ansicht des Lagers mit Echtzeit-Auslastung.", "WebGL warehouse view with real-time utilization.")}</p>
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
                    <h2>{l("Stammdaten & regelbasierte Datenanreicherung", "Master Data & Rule-Based Enrichment")}</h2>
                    <form className="form-grid" onSubmit={submitProduct}>
                        <label>
                            {l("Produktname", "Product name")}
                            <input
                                type="text"
                                value={formState.product.name}
                                onChange={(e) => handleFormChange("product", "name", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Kategorie", "Category")}
                            <input
                                type="text"
                                value={formState.product.category}
                                onChange={(e) => handleFormChange("product", "category", e.target.value)}
                            />
                        </label>
                        <label>
                            {l("Einstandspreis (CHF)", "Cost price (CHF)")}
                            <input
                                type="number"
                                step="0.01"
                                value={formState.product.costPrice}
                                onChange={(e) => handleFormChange("product", "costPrice", e.target.value)}
                            />
                        </label>
                        <label>
                            {l("Gewicht (kg)", "Weight (kg)")}
                            <input
                                type="number"
                                step="0.1"
                                value={formState.product.weightKg}
                                onChange={(e) => handleFormChange("product", "weightKg", e.target.value)}
                            />
                        </label>
                        <button className="btn primary" type="submit">{l("Produkt anreichern", "Enrich product")}</button>
                    </form>
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>{l("Produkt", "Product")}</th>
                                    <th>{l("Kategorie", "Category")}</th>
                                    <th>{l("Gewicht", "Weight")}</th>
                                    <th>{l("VK-Preis", "Sales price")}</th>
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
                            {l("Produkt-ID", "Product ID")}
                            <input
                                type="text"
                                value={formState.slotting.productId}
                                onChange={(e) => handleFormChange("slotting", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Gewicht (kg)", "Weight (kg)")}
                            <input
                                type="number"
                                value={formState.slotting.weightKg}
                                onChange={(e) => handleFormChange("slotting", "weightKg", e.target.value)}
                            />
                        </label>
                        <label>
                            {l("Volumen (m³)", "Volume (m³)")}
                            <input
                                type="number"
                                value={formState.slotting.volumeCubicM}
                                onChange={(e) => handleFormChange("slotting", "volumeCubicM", e.target.value)}
                            />
                        </label>
                        <label>
                            {l("Umschlagszeit (Tage)", "Turnover time (days)")}
                            <input
                                type="number"
                                value={formState.slotting.expectedTurnoverDays}
                                onChange={(e) => handleFormChange("slotting", "expectedTurnoverDays", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">{l("Optimale Position berechnen", "Calculate optimal position")}</button>
                    </form>
                    {slotting && (
                        <div className="result">
                            <strong>{l("Lagerplatz", "Storage location")}:</strong> {slotting.locationId}<br />
                            <strong>Confidence:</strong> {(slotting.confidence * 100).toFixed(1)} %<br />
                            <strong>{l("Grund", "Reason")}:</strong> {slotting.reason}
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
                                    <span>{value} {l("Stk.", "pcs.")}</span>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p className="muted">{l("Wähle ein Produkt, um die 6-Wochen-Vorhersage zu sehen.", "Select a product to see the 6-week forecast.")}</p>
                    )}
                    {forecast && (
                        <div className="alerts">
                            {forecast.stockOutRisk && <span className="badge warning">{l("Stock-Out Risiko", "Stock-out risk")}</span>}
                            {forecast.overstockRisk && <span className="badge info">{l("Überbestand möglich", "Overstock possible")}</span>}
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>IoT Monitoring</h3>
                    <form className="form-grid" onSubmit={submitSensor}>
                        <label>
                            {l("Lagerplatz", "Storage location")}
                            <input
                                type="text"
                                value={sensorForm.locationId}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, locationId: e.target.value }))}
                                required
                            />
                        </label>
                        <label>
                            {l("Temperatur (°C)", "Temperature (°C)")}
                            <input
                                type="number"
                                value={sensorForm.temperature}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, temperature: e.target.value }))}
                            />
                        </label>
                        <label>
                            {l("Luftfeuchte (%)", "Humidity (%)")}
                            <input
                                type="number"
                                value={sensorForm.humidity}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, humidity: e.target.value }))}
                            />
                        </label>
                        <label>
                            {l("Gewicht (kg)", "Weight (kg)")}
                            <input
                                type="number"
                                value={sensorForm.weight}
                                onChange={(e) => setSensorForm((prev) => ({ ...prev, weight: e.target.value }))}
                            />
                        </label>
                        <button className="btn" type="submit">{l("Messwert senden", "Send reading")}</button>
                    </form>
                    <p className="muted">{l("Werte werden revisionssicher dokumentiert.", "Readings are documented with an audit trail.")}</p>
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
                            {l("Produkt-ID", "Product ID")}
                            <input
                                type="text"
                                value={formState.sourcing.productId}
                                onChange={(e) => handleFormChange("sourcing", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Menge", "Quantity")}
                            <input
                                type="number"
                                value={formState.sourcing.quantity}
                                onChange={(e) => handleFormChange("sourcing", "quantity", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">{l("Lieferanten bewerten", "Evaluate suppliers")}</button>
                    </form>
                    {sourcing && (
                        <div className="result">
                            <strong>{l("Empfehlung", "Recommendation")}:</strong> {sourcing.recommended?.supplierName}<br />
                            <strong>Score:</strong> {sourcing.recommended?.score.toFixed?.(2)}
                            <ul className="muted">
                                {sourcing.rankedSuppliers?.map((supplier) => (
                                    <li key={supplier.supplierId}>
                                        {supplier.supplierName} · Score {supplier.score.toFixed(2)} · {supplier.leadTimeDays} {l("Tage", "days")}
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
                            {l("Bestellnummer", "Purchase order number")}
                            <input
                                type="text"
                                value={formState.accounting.purchaseOrderId}
                                onChange={(e) => handleFormChange("accounting", "purchaseOrderId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Bestellt (CHF)", "Ordered (CHF)")}
                            <input
                                type="number"
                                value={formState.accounting.orderedAmount}
                                onChange={(e) => handleFormChange("accounting", "orderedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Wareneingang (CHF)", "Goods receipt (CHF)")}
                            <input
                                type="number"
                                value={formState.accounting.receivedAmount}
                                onChange={(e) => handleFormChange("accounting", "receivedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Rechnung (CHF)", "Invoice (CHF)")}
                            <input
                                type="number"
                                value={formState.accounting.invoicedAmount}
                                onChange={(e) => handleFormChange("accounting", "invoicedAmount", e.target.value)}
                                required
                            />
                        </label>
                        <button className="btn" type="submit">{l("3-Wege-Abgleich prüfen", "Check three-way match")}</button>
                    </form>
                    {accounting && (
                        <div className="result">
                            <strong>Status:</strong> {accounting.autoApproved ? l("Auto-Freigabe", "Auto-approved") : l("Manuelle Prüfung", "Manual review")}<br />
                            <strong>{l("Abweichung", "Deviation")}:</strong> {accounting.deviation} CHF
                        </div>
                    )}
                </article>

                <article className="card">
                    <h3>Mobile Inbound</h3>
                    <form className="form-grid" onSubmit={submitInbound}>
                        <label>
                            {l("Produkt-ID", "Product ID")}
                            <input
                                type="text"
                                value={formState.inbound.productId}
                                onChange={(e) => handleFormChange("inbound", "productId", e.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {l("Menge", "Quantity")}
                            <input
                                type="number"
                                value={formState.inbound.quantity}
                                onChange={(e) => handleFormChange("inbound", "quantity", e.target.value)}
                            />
                        </label>
                        <label>
                            {l("Rampe", "Dock")}
                            <input
                                type="text"
                                value={formState.inbound.dockLocationId}
                                onChange={(e) => handleFormChange("inbound", "dockLocationId", e.target.value)}
                            />
                        </label>
                        <button className="btn" type="submit">{l("Route anzeigen", "Show route")}</button>
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
                    <h3>{l("Pick-by-Vision Routen", "Pick-by-Vision Routes")}</h3>
                    <form className="form-grid" onSubmit={submitPick}>
                        {pickItems.map((item, index) => (
                            <div key={`pick-${index}`} className="pick-item">
                                <label>
                                    {l("Produkt-ID", "Product ID")}
                                    <input
                                        type="text"
                                        value={item.productId}
                                        onChange={(e) => updatePickItem(index, "productId", e.target.value)}
                                        required
                                    />
                                </label>
                                <label>
                                    {l("Menge", "Quantity")}
                                    <input
                                        type="number"
                                        value={item.quantity}
                                        onChange={(e) => updatePickItem(index, "quantity", Number(e.target.value))}
                                        min={1}
                                    />
                                </label>
                                {pickItems.length > 1 && (
                                    <button type="button" className="btn ghost" onClick={() => removePickItem(index)}>
                                        {l("Entfernen", "Remove")}
                                    </button>
                                )}
                            </div>
                        ))}
                        <div className="pick-actions">
                            <button type="button" className="btn ghost" onClick={addPickItem}>{l("Position hinzufügen", "Add item")}</button>
                            <button type="submit" className="btn">{l("Route planen", "Plan route")}</button>
                        </div>
                    </form>
                    {pickRoute && (
                        <div className="result">
                            <strong>{l("Gesamtzeit", "Total time")}:</strong> {pickRoute.estimatedDurationSeconds.toFixed?.(1)} s<br />
                            <strong>{l("Distanz", "Distance")}:</strong> {pickRoute.totalDistance.toFixed?.(1)} m
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
                    <h3>{l("Analyse-Dashboard", "Analytics Dashboard")}</h3>
                    <form className="form-inline" onSubmit={submitNlp}>
                        <input
                            type="text"
                            value={formState.nlp}
                            onChange={(e) => setFormState((prev) => ({ ...prev, nlp: e.target.value }))}
                            placeholder={l("Frage die Lageranalyse...", "Ask warehouse analytics...")}
                        />
                        <button className="btn" type="submit">{l("Analysieren", "Analyze")}</button>
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
                    <h3>{l("Zirkuläre Logistik", "Circular Logistics")}</h3>
                    <ul>
                        {returns.map((item) => (
                            <li key={item.caseId}>
                                <strong>{item.productId}</strong> – {item.reason} · Status {item.status}
                            </li>
                        ))}
                    </ul>
                </article>

                <article className="card">
                    <h3>{l("Blockchain-Bewegungen", "Blockchain Movements")}</h3>
                    <ul className="ledger">
                        {movementLedger.map((entry) => (
                            <li key={entry.id}>
                                {entry.productId}: {entry.fromLocation ?? l("Anlieferung", "Inbound")} ➜ {entry.toLocation} · {entry.quantity} {l("Stk.", "pcs.")}
                            </li>
                        ))}
                    </ul>
                </article>
            </section>
        </div>
    );
};

export default ChronoTwoDashboard;
