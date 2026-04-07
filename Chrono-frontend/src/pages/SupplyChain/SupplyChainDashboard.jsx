import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { LanguageContext, useTranslation } from "../../context/LanguageContext.jsx";
import { useNotification } from "../../context/NotificationContext.jsx";
import "../../styles/SupplyChainDashboardScoped.css";
import ProcessWorkspace from "./components/ProcessWorkspace.jsx";
import {
    auditFromMovement,
    buildExceptionRows,
    buildInventoryBucketRows,
    createMovementHistory,
    normalizeDelivery,
    normalizeOrder,
    normalizeProduct,
    normalizeServiceCase,
    normalizeStock,
    normalizeWarehouse,
} from "./models/supplyChainDomain.js";

const roles = ["warehouse", "planner", "quality", "service", "admin"];

const SupplyChainDashboard = () => {
    const { t } = useTranslation();
    const { language } = useContext(LanguageContext);
    const { notify } = useNotification();
    const l = useCallback((de, en) => (language === "de" ? de : en), [language]);

    const [loading, setLoading] = useState(true);
    const [role, setRole] = useState("warehouse");
    const [activeWorkspace, setActiveWorkspace] = useState("inbound");
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [inboundOrders, setInboundOrders] = useState([]);
    const [outboundOrders, setOutboundOrders] = useState([]);
    const [productionOrders, setProductionOrders] = useState([]);
    const [serviceCases, setServiceCases] = useState([]);

    const loadData = useCallback(async () => {
        try {
            const [productRes, warehouseRes, stockRes, inboundRes, outboundRes, productionRes, serviceRes] = await Promise.all([
                api.get("/api/supply-chain/products"),
                api.get("/api/supply-chain/warehouses"),
                api.get("/api/supply-chain/stock"),
                api.get("/api/supply-chain/purchase-orders"),
                api.get("/api/supply-chain/sales-orders"),
                api.get("/api/supply-chain/production-orders"),
                api.get("/api/supply-chain/service-requests"),
            ]);
            const list = (payload) => (Array.isArray(payload) ? payload : payload?.content ?? []);
            setProducts(list(productRes?.data).map(normalizeProduct));
            setWarehouses(list(warehouseRes?.data).map(normalizeWarehouse));
            setStock(list(stockRes?.data).map(normalizeStock));
            setInboundOrders(list(inboundRes?.data).map(normalizeDelivery));
            setOutboundOrders(list(outboundRes?.data).map(normalizeOrder));
            setProductionOrders(list(productionRes?.data).map(normalizeOrder));
            setServiceCases(list(serviceRes?.data).map(normalizeServiceCase));
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Supply-Chain-Daten konnten nicht vollständig geladen werden.", "Could not load all supply-chain data."), "error");
            return false;
        }
    }, [l, notify]);

    useEffect(() => {
        let mounted = true;
        const load = async () => {
            setLoading(true);
            await loadData();
            if (mounted) setLoading(false);
        };

        load();
        return () => {
            mounted = false;
        };
    }, [loadData]);

    useEffect(() => {
        const defaultView = {
            warehouse: "inbound",
            planner: "production",
            quality: "exceptions",
            service: "service",
            admin: "governance",
        };
        setActiveWorkspace(defaultView[role]);
    }, [role]);

    const movementTimeline = useMemo(
        () => createMovementHistory({ stock, productionOrders, serviceRequests: serviceCases, inboundOrders, outboundOrders }),
        [stock, productionOrders, serviceCases, inboundOrders, outboundOrders]
    );
    const auditTrail = useMemo(() => movementTimeline.map((movement) => auditFromMovement(movement)).slice(0, 20), [movementTimeline]);

    const inventoryRows = useMemo(() => stock.map((entry) => {
        const product = products.find((item) => item.id === entry.productId);
        const warehouse = warehouses.find((item) => item.id === entry.warehouseId);
        return {
            id: entry.id,
            sku: product?.sku ?? "-",
            product: product?.name ?? "-",
            warehouse: warehouse?.name ?? "-",
            site: warehouse?.siteName ?? "-",
            zone: warehouse?.zone ?? "-",
            bin: warehouse?.bin ?? "-",
            status: entry.status,
            statusLabel: entry.status,
            quantity: entry.quantity,
            lot: entry.lotNumber,
            partner: "-",
            approvalProgress: entry.status === "BLOCKED" ? 15 : 92,
        };
    }), [products, stock, warehouses]);

    const inboundRows = useMemo(() => inboundOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        asn: entry.asn,
        dock: entry.dock,
        supplier: entry.supplier,
        warehouse: warehouses.find((item) => item.id === entry.warehouseId)?.name ?? "-",
        site: warehouses.find((item) => item.id === entry.warehouseId)?.siteName ?? "-",
        status: entry.status,
        statusLabel: entry.status,
        eta: entry.eta ?? "-",
        partner: entry.supplier,
        approvalProgress: entry.status === "COMPLETED" ? 100 : 64,
    })), [inboundOrders, warehouses]);

    const outboundRows = useMemo(() => outboundOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        customer: entry.customerName,
        priority: entry.priority,
        warehouse: "-",
        site: "-",
        status: entry.status,
        statusLabel: entry.status,
        dueDate: entry.dueDate ?? "-",
        partner: entry.customerName,
        approvalProgress: entry.status === "COMPLETED" ? 100 : 58,
    })), [outboundOrders]);

    const productionRows = useMemo(() => productionOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        product: entry.productName ?? "-",
        quantity: entry.quantity ?? 0,
        priority: entry.priority,
        status: entry.status,
        statusLabel: t(`supplyChain.productionStatus.${String(entry.status).toLowerCase()}`, entry.status),
        warehouse: "Plant-1",
        site: "Main",
        partner: entry.owner ?? "-",
        approvalProgress: entry.status === "IN_PROGRESS" ? 47 : entry.status === "COMPLETED" ? 100 : 25,
    })), [productionOrders, t]);

    const serviceRows = useMemo(() => serviceCases.map((entry) => ({
        id: entry.id,
        case: entry.subject,
        customer: entry.customerName,
        owner: entry.owner,
        warehouse: "Field",
        site: "Service",
        status: entry.status,
        statusLabel: entry.status,
        sla: entry.slaBreached ? "BREACHED" : "OK",
        partner: entry.customerName,
        approvalProgress: entry.status === "CLOSED" ? 100 : 36,
    })), [serviceCases]);

    const returnsRows = useMemo(() => outboundRows.filter((row) => ["BLOCKED", "OPEN"].includes(row.status)).map((row) => ({
        id: `RMA-${row.id}`,
        rma: `RMA-${row.order}`,
        customer: row.customer,
        reason: row.status === "BLOCKED" ? "Damaged" : "Wrong item",
        decision: row.status === "BLOCKED" ? "Quarantine" : "Restock",
        status: row.status,
        statusLabel: row.status,
        warehouse: row.warehouse,
        site: row.site,
        partner: row.customer,
        approvalProgress: 42,
    })), [outboundRows]);

    const exceptionRows = useMemo(() => buildExceptionRows({ inboundOrders, stock, serviceCases }).map((row) => ({
        id: row.id,
        type: row.type,
        severity: row.severity,
        ref: row.ref,
        owner: row.owner,
        status: row.severity === "HIGH" ? "BLOCKED" : "OPEN",
        statusLabel: row.severity,
        warehouse: "-",
        site: "-",
        partner: row.owner,
        approvalProgress: row.severity === "HIGH" ? 20 : 55,
    })), [inboundOrders, stock, serviceCases]);

    const cycleCountRows = useMemo(() => inventoryRows.slice(0, 20).map((row, index) => ({
        id: `CC-${row.id}`,
        plan: `CC-${String(index + 1).padStart(4, "0")}`,
        sku: row.sku,
        warehouse: row.warehouse,
        site: row.site,
        variance: index % 4 === 0 ? 3 : 0,
        status: index % 4 === 0 ? "BLOCKED" : "COMPLETED",
        statusLabel: index % 4 === 0 ? "APPROVAL_REQUIRED" : "OK",
        partner: "count-team",
        approvalProgress: index % 4 === 0 ? 35 : 100,
    })), [inventoryRows]);

    const bucketRows = useMemo(() => buildInventoryBucketRows(stock).map((row) => ({
        id: row.id,
        bucket: row.bucket,
        quantity: row.quantity,
        status: row.quantity > 0 ? "IN_PROGRESS" : "COMPLETED",
        statusLabel: row.bucket,
        warehouse: "Network",
        site: "Global",
        partner: "-",
        approvalProgress: row.quantity > 0 ? 65 : 100,
    })), [stock]);

    const governanceRows = useMemo(() => auditTrail.map((entry) => ({
        id: entry.id,
        entity: `${entry.entityType}:${entry.entityId}`,
        action: entry.action,
        changedBy: entry.changedBy,
        reason: entry.reason,
        status: "COMPLETED",
        statusLabel: "AUDITED",
        warehouse: "-",
        site: "-",
        partner: entry.approvedBy,
        approvalProgress: 100,
    })), [auditTrail]);

    const companyRows = [
        { id: "C1", company: "Chrono DE", currency: "EUR", language: "de", tax: "DE-VAT", status: "COMPLETED", statusLabel: "LIVE", warehouse: "Shared EU", site: "Berlin", partner: "Intercompany", approvalProgress: 100 },
        { id: "C2", company: "Chrono US", currency: "USD", language: "en", tax: "US-SalesTax", status: "IN_PROGRESS", statusLabel: "ROLLING", warehouse: "Shared US", site: "Austin", partner: "Intercompany", approvalProgress: 62 },
    ];

    const kpi = useMemo(() => ({
        otif: outboundRows.length ? Math.round((outboundRows.filter((row) => row.status === "COMPLETED").length / outboundRows.length) * 100) : 0,
        pickPerformance: `${Math.max(80, 100 - exceptionRows.length)}%`,
        inventoryAccuracy: `${Math.max(92, 100 - cycleCountRows.filter((row) => row.variance > 0).length)}%`,
        returnRate: `${outboundRows.length ? Math.round((returnsRows.length / outboundRows.length) * 100) : 0}%`,
    }), [cycleCountRows, exceptionRows.length, outboundRows, returnsRows.length]);

    const text = {
        saveView: l("Ansicht speichern", "Save view"),
        loadView: l("Ansicht laden", "Load view"),
        export: l("Export", "Export"),
        columns: l("Spalten", "Columns"),
        massAction: l("Massenaktion ausführen", "Run mass action"),
        massAssign: l("Verantwortung zuweisen", "Assign owner"),
        massPriority: l("Priorität setzen", "Set priority"),
        massClose: l("Abschließen", "Close"),
        prev: l("Zurück", "Prev"),
        next: l("Weiter", "Next"),
        page: l("Seite", "Page"),
        rowAction: l("Aktion", "Action"),
        openRow: l("Öffnen", "Open"),
        empty: l("Keine Datensätze gefunden", "No records found"),
        drawerTitle: l("Details", "Details"),
        timelineTitle: l("Aktivitäts-Timeline", "Activity timeline"),
        approvalLabel: l("Freigabefortschritt", "Approval progress"),
        filters: {
            search: l("Suche", "Search"), warehouse: l("Lager", "Warehouse"), site: l("Werk", "Site"), status: l("Status", "Status"),
            partner: l("Kunde/Lieferant", "Customer/Supplier"), date: l("Datum", "Date"), sku: "SKU", batch: l("Charge", "Batch"),
            owner: l("Verantwortlich", "Owner"), priority: l("Priorität", "Priority"), reset: l("Reset", "Reset"),
        },
    };

    const workspaceDefinitions = [
        { id: "inbound", title: l("Wareneingänge", "Inbound"), subtitle: l("ASN, Dock, QC, Putaway", "ASN, dock, QC, putaway"), rows: inboundRows, columns: [{ key: "order", label: "Order" }, { key: "asn", label: "ASN" }, { key: "dock", label: "Dock" }, { key: "supplier", label: l("Lieferant", "Supplier") }, { key: "status", label: l("Status", "Status") }] },
        { id: "outbound", title: l("Warenausgänge", "Outbound"), subtitle: l("Wave, Pick, Pack, Ship", "Wave, pick, pack, ship"), rows: outboundRows, columns: [{ key: "order", label: "Order" }, { key: "customer", label: l("Kunde", "Customer") }, { key: "priority", label: l("Priorität", "Priority") }, { key: "dueDate", label: l("Datum", "Date") }, { key: "status", label: l("Status", "Status") }] },
        { id: "production", title: l("Produktionsaufträge", "Production orders"), subtitle: l("Planung bis Abschluss", "Planning to completion"), rows: productionRows, columns: [{ key: "order", label: "Order" }, { key: "product", label: l("Produkt", "Product") }, { key: "quantity", label: l("Menge", "Quantity") }, { key: "priority", label: l("Priorität", "Priority") }, { key: "status", label: l("Status", "Status") }] },
        { id: "service", title: l("Serviceeinsätze", "Service cases"), subtitle: l("SLA- und Eskalationsfokus", "SLA and escalation focused"), rows: serviceRows, columns: [{ key: "case", label: l("Fall", "Case") }, { key: "customer", label: l("Kunde", "Customer") }, { key: "owner", label: l("Verantwortlich", "Owner") }, { key: "sla", label: "SLA" }, { key: "status", label: l("Status", "Status") }] },
        { id: "returns", title: l("Retouren (RMA)", "Returns (RMA)"), subtitle: l("Prüfen, entscheiden, buchen", "Inspect, decide, post"), rows: returnsRows, columns: [{ key: "rma", label: "RMA" }, { key: "customer", label: l("Kunde", "Customer") }, { key: "reason", label: l("Grund", "Reason") }, { key: "decision", label: l("Entscheidung", "Decision") }, { key: "status", label: l("Status", "Status") }] },
        { id: "inventory", title: l("Mehrstufiges Lager", "Multi-level warehouse"), subtitle: l("Site/Warehouse/Zone/Aisle/Rack/Bin", "Site/warehouse/zone/aisle/rack/bin"), rows: inventoryRows, columns: [{ key: "sku", label: "SKU" }, { key: "product", label: l("Produkt", "Product") }, { key: "warehouse", label: l("Lager", "Warehouse") }, { key: "zone", label: "Zone" }, { key: "bin", label: "Bin" }, { key: "status", label: l("Status", "Status") }] },
        { id: "buckets", title: l("Bestandsarten", "Inventory buckets"), subtitle: l("Verfügbar, reserviert, gesperrt, Transit, QC", "Available, reserved, blocked, transit, QC"), rows: bucketRows, columns: [{ key: "bucket", label: l("Art", "Type") }, { key: "quantity", label: l("Menge", "Quantity") }, { key: "status", label: l("Status", "Status") }] },
        { id: "exceptions", title: l("Exception Center", "Exception center"), subtitle: l("Verspätung, QC-Blocker, SLA-Verletzung", "Delays, QC blockers, SLA breaches"), rows: exceptionRows, columns: [{ key: "type", label: l("Typ", "Type") }, { key: "severity", label: l("Schwere", "Severity") }, { key: "ref", label: l("Referenz", "Reference") }, { key: "owner", label: "Owner" }, { key: "status", label: l("Status", "Status") }] },
        { id: "cycle-count", title: l("Cycle Counting", "Cycle counting"), subtitle: l("Regelzählung, Spot Count, 4-Augen-Freigabe", "Planned counts, spot count, 4-eyes approval"), rows: cycleCountRows, columns: [{ key: "plan", label: l("Plan", "Plan") }, { key: "sku", label: "SKU" }, { key: "warehouse", label: l("Lager", "Warehouse") }, { key: "variance", label: l("Differenz", "Variance") }, { key: "status", label: l("Status", "Status") }] },
        { id: "governance", title: l("Audit & Freigaben", "Audit & approvals"), subtitle: l("Wer/Wann/Was/Warum mit Historie", "Who/when/what/why with history"), rows: governanceRows, columns: [{ key: "entity", label: l("Entität", "Entity") }, { key: "action", label: l("Aktion", "Action") }, { key: "changedBy", label: l("Benutzer", "User") }, { key: "reason", label: l("Grund", "Reason") }, { key: "status", label: l("Status", "Status") }] },
        { id: "company", title: l("Multi-Company & Standards", "Multi-company & standards"), subtitle: l("Währung, Sprache, Steuer, Intercompany, Shared WH", "Currency, language, tax, intercompany, shared WH"), rows: companyRows, columns: [{ key: "company", label: l("Gesellschaft", "Company") }, { key: "currency", label: l("Währung", "Currency") }, { key: "language", label: l("Sprache", "Language") }, { key: "tax", label: l("Steuer", "Tax") }, { key: "status", label: l("Status", "Status") }] },
    ];

    const activeDefinition = workspaceDefinitions.find((item) => item.id === activeWorkspace) ?? workspaceDefinitions[0];
    const quickEntryEnabled = ["inbound", "inventory", "buckets"].includes(activeDefinition.id);

    const submitQuickEntry = async (payload) => {
        if (!payload.productId || !payload.warehouseId || !payload.quantityChange) {
            notify(l("Bitte Produkt, Lager und Menge ausfüllen.", "Please fill product, warehouse and quantity."), "warning");
            return false;
        }
        try {
            await api.post("/api/supply-chain/stock/adjust", payload);
            notify(l("Wareneingang erfolgreich erfasst.", "Stock entry saved successfully."), "success");
            await loadData();
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Wareneingang konnte nicht gespeichert werden.", "Could not save stock entry."), "error");
            return false;
        }
    };

    return (
        <div className="admin-page supply-chain-page">
            <Navbar />
            <main className="admin-content">
                <header className="hero-header card compact-hero">
                    <div className="header-text">
                        <h1>{l("Supply Chain Enterprise Control Tower", "Supply Chain Enterprise Control Tower")}</h1>
                        <p className="muted">{l("Betriebsbereit: Smartes, rollenbasiertes Layout mit klaren Arbeitslisten und vollständiger Prozesssicht.", "Operational-ready: smart role-based layout with clear worklists and full process coverage.")}</p>
                    </div>
                    <div className="kpi-strip">
                        <article><span>OTIF</span><strong>{kpi.otif}%</strong></article>
                        <article><span>{l("Pick-Leistung", "Pick performance")}</span><strong>{kpi.pickPerformance}</strong></article>
                        <article><span>{l("Inventurgenauigkeit", "Inventory accuracy")}</span><strong>{kpi.inventoryAccuracy}</strong></article>
                        <article><span>{l("Retourenquote", "Return rate")}</span><strong>{kpi.returnRate}</strong></article>
                    </div>
                </header>

                <section className="card sc-controlbar">
                    <div className="sc-role-switch">
                        <label htmlFor="sc-role">{l("Startseite nach Rolle", "Homepage by role")}</label>
                        <select id="sc-role" value={role} onChange={(event) => setRole(event.target.value)}>
                            {roles.map((roleOption) => <option key={roleOption} value={roleOption}>{roleOption}</option>)}
                        </select>
                    </div>
                    <div className="sc-workspace-nav">
                        {workspaceDefinitions.map((workspace) => (
                            <button key={workspace.id} type="button" className={`sc-pill ${workspace.id === activeWorkspace ? "active" : ""}`} onClick={() => setActiveWorkspace(workspace.id)}>
                                {workspace.title}
                            </button>
                        ))}
                    </div>
                </section>

                {loading ? <p className="card">{l("Lädt…", "Loading…")}</p> : (
                    <ProcessWorkspace
                        id={activeDefinition.id}
                        title={activeDefinition.title}
                        subtitle={activeDefinition.subtitle}
                        rows={activeDefinition.rows}
                        columns={activeDefinition.columns}
                        timeline={movementTimeline}
                        text={text}
                        quickEntry={{
                            enabled: quickEntryEnabled,
                            openLabel: l("+ Wareneingang erfassen", "+ Add stock entry"),
                            closeLabel: l("Schließen", "Close"),
                            title: l("Schnellerfassung Wareneingang", "Quick stock entry"),
                            subtitle: l("Für kleine Teams: Produkt, Lager und Menge direkt buchen.", "For small teams: book product, warehouse and quantity directly."),
                            submitLabel: l("Buchen", "Post entry"),
                            submittingLabel: l("Wird gebucht…", "Posting…"),
                            products,
                            warehouses,
                            labels: {
                                product: l("Produkt", "Product"),
                                warehouse: l("Lager", "Warehouse"),
                                quantity: l("Menge", "Quantity"),
                                type: l("Buchungstyp", "Entry type"),
                                reference: l("Referenz", "Reference"),
                            },
                            placeholders: {
                                product: l("Bitte Produkt wählen", "Select product"),
                                warehouse: l("Bitte Lager wählen", "Select warehouse"),
                                reference: l("z. B. Lieferschein 2026-04-07", "e.g. Delivery note 2026-04-07"),
                            },
                            types: [
                                { value: "RECEIPT", label: l("Wareneingang", "Goods receipt") },
                                { value: "ADJUSTMENT", label: l("Korrektur", "Adjustment") },
                                { value: "RESERVATION", label: l("Reservierung", "Reservation") },
                                { value: "QC_HOLD", label: l("QC-Sperre", "QC hold") },
                                { value: "RELEASE", label: l("Freigabe", "Release") },
                                { value: "TRANSFER", label: l("Umlagerung", "Transfer") },
                            ],
                            onSubmit: submitQuickEntry,
                        }}
                    />
                )}
            </main>
        </div>
    );
};

export default SupplyChainDashboard;
