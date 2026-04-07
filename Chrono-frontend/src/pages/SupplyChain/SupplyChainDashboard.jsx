import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { LanguageContext, useTranslation } from "../../context/LanguageContext.jsx";
import { useNotification } from "../../context/NotificationContext.jsx";
import "../../styles/SupplyChainDashboardScoped.css";
import ProcessWorkspace from "./components/ProcessWorkspace.jsx";
import {
    createMovementHistory,
    normalizeDelivery,
    normalizeOrder,
    normalizeProduct,
    normalizeServiceCase,
    normalizeStock,
    normalizeWarehouse,
} from "./models/supplyChainDomain.js";

const SupplyChainDashboard = () => {
    const { t } = useTranslation();
    const { language } = useContext(LanguageContext);
    const { notify } = useNotification();
    const [loading, setLoading] = useState(true);
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [inboundOrders, setInboundOrders] = useState([]);
    const [outboundOrders, setOutboundOrders] = useState([]);
    const [productionOrders, setProductionOrders] = useState([]);
    const [serviceCases, setServiceCases] = useState([]);

    const l = useCallback((de, en) => (language === "de" ? de : en), [language]);

    useEffect(() => {
        let mounted = true;
        const load = async () => {
            setLoading(true);
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

                if (!mounted) return;
                const list = (payload) => (Array.isArray(payload) ? payload : payload?.content ?? []);
                setProducts(list(productRes?.data).map(normalizeProduct));
                setWarehouses(list(warehouseRes?.data).map(normalizeWarehouse));
                setStock(list(stockRes?.data).map(normalizeStock));
                setInboundOrders(list(inboundRes?.data).map(normalizeDelivery));
                setOutboundOrders(list(outboundRes?.data).map(normalizeOrder));
                setProductionOrders(list(productionRes?.data).map(normalizeOrder));
                setServiceCases(list(serviceRes?.data).map(normalizeServiceCase));
            } catch (error) {
                console.error(error);
                notify(l("Supply-Chain-Daten konnten nicht vollständig geladen werden.", "Could not load all supply-chain data."), "error");
            } finally {
                if (mounted) setLoading(false);
            }
        };

        load();
        return () => {
            mounted = false;
        };
    }, [l, notify]);

    const movementTimeline = useMemo(
        () => createMovementHistory({ stock, productionOrders, serviceRequests: serviceCases }),
        [stock, productionOrders, serviceCases]
    );

    const inventoryRows = useMemo(() => stock.map((entry) => {
        const product = products.find((item) => item.id === entry.productId);
        const warehouse = warehouses.find((item) => item.id === entry.warehouseId);
        return {
            id: entry.id,
            sku: product?.sku ?? "-",
            product: product?.name ?? "-",
            warehouse: warehouse?.name ?? "-",
            site: warehouse?.location ?? "-",
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
        supplier: entry.supplier,
        warehouse: warehouses.find((item) => item.id === entry.warehouseId)?.name ?? "-",
        site: warehouses.find((item) => item.id === entry.warehouseId)?.location ?? "-",
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
        warehouse: "Field",
        site: "Service",
        status: entry.status,
        statusLabel: entry.status,
        owner: entry.owner,
        partner: entry.customerName,
        approvalProgress: entry.status === "CLOSED" ? 100 : 36,
    })), [serviceCases]);

    const text = {
        saveView: l("Ansicht speichern", "Save view"),
        loadView: l("Ansicht laden", "Load view"),
        massAction: l("Massenaktion", "Mass action"),
        prev: l("Zurück", "Prev"),
        next: l("Weiter", "Next"),
        page: l("Seite", "Page"),
        rowAction: l("Aktion", "Action"),
        openRow: l("Öffnen", "Open"),
        drawerTitle: l("Details", "Details"),
        timelineTitle: l("Aktivitäts-Timeline", "Activity timeline"),
        approvalLabel: l("Freigabefortschritt", "Approval progress"),
        filters: {
            search: l("Suche", "Search"),
            warehouse: l("Lager", "Warehouse"),
            site: l("Werk", "Site"),
            status: l("Status", "Status"),
            partner: l("Kunde/Lieferant", "Customer/Supplier"),
            reset: l("Reset", "Reset"),
        },
    };

    return (
        <div className="admin-page supply-chain-page">
            <Navbar />
            <main className="admin-content">
                <header className="hero-header card compact-hero">
                    <div className="header-text">
                        <h1>{l("Supply Chain Operations Center", "Supply Chain Operations Center")}</h1>
                        <p className="muted">
                            {l(
                                "Phase 0/1 umgesetzt: modulare Prozesse, einheitliche Tabellen/Filter/Statuschips/Drawer/Timeline/Freigabe plus Arbeitslisten mit Saved Views, Pagination und Massenaktionen.",
                                "Phase 0/1 delivered: modular processes, shared table/filter/status-chip/drawer/timeline/approval framework plus worklists with saved views, pagination and mass actions."
                            )}
                        </p>
                    </div>
                    <div className="kpi-strip">
                        <article><span>{l("Produkte", "Products")}</span><strong>{products.length}</strong></article>
                        <article><span>{l("Lager", "Warehouses")}</span><strong>{warehouses.length}</strong></article>
                        <article><span>{l("Bestand", "Stock")}</span><strong>{stock.length}</strong></article>
                        <article><span>{l("Offene Services", "Open service")}</span><strong>{serviceRows.filter((item) => item.status !== "CLOSED").length}</strong></article>
                    </div>
                </header>

                {loading ? <p className="card">{l("Lädt…", "Loading…")}</p> : (
                    <div className="sc-process-grid">
                        <ProcessWorkspace
                            id="inbound"
                            title={l("Wareneingänge", "Inbound")}
                            subtitle={l("ASN/Ankünfte/QC/Putaway als Arbeitsliste.", "ASN/arrivals/QC/putaway as worklist.")}
                            rows={inboundRows}
                            columns={[
                                { key: "order", label: "Order" },
                                { key: "supplier", label: l("Lieferant", "Supplier") },
                                { key: "warehouse", label: l("Lager", "Warehouse") },
                                { key: "eta", label: "ETA" },
                                { key: "status", label: l("Status", "Status") },
                            ]}
                            timeline={movementTimeline}
                            text={text}
                        />
                        <ProcessWorkspace
                            id="outbound"
                            title={l("Warenausgänge", "Outbound")}
                            subtitle={l("Pick/Pack/Ship mit Ausnahmen.", "Pick/pack/ship with exceptions.")}
                            rows={outboundRows}
                            columns={[
                                { key: "order", label: "Order" },
                                { key: "customer", label: l("Kunde", "Customer") },
                                { key: "dueDate", label: l("Datum", "Date") },
                                { key: "status", label: l("Status", "Status") },
                            ]}
                            timeline={movementTimeline}
                            text={text}
                        />
                        <ProcessWorkspace
                            id="production"
                            title={l("Produktionsaufträge", "Production orders")}
                            subtitle={l("Status, Priorität und Verantwortliche direkt in der Liste.", "Status, priority and owners directly in list.")}
                            rows={productionRows}
                            columns={[
                                { key: "order", label: "Order" },
                                { key: "product", label: l("Produkt", "Product") },
                                { key: "quantity", label: l("Menge", "Quantity") },
                                { key: "status", label: l("Status", "Status") },
                            ]}
                            timeline={movementTimeline}
                            text={text}
                        />
                        <ProcessWorkspace
                            id="service"
                            title={l("Serviceeinsätze", "Service cases")}
                            subtitle={l("SLA-/Status-fokussierte Einsatzliste.", "SLA and status focused service worklist.")}
                            rows={serviceRows}
                            columns={[
                                { key: "case", label: l("Fall", "Case") },
                                { key: "customer", label: l("Kunde", "Customer") },
                                { key: "owner", label: l("Verantwortlich", "Owner") },
                                { key: "status", label: l("Status", "Status") },
                            ]}
                            timeline={movementTimeline}
                            text={text}
                        />
                        <ProcessWorkspace
                            id="inventory"
                            title={l("Bestand & Lagerplätze", "Inventory & locations")}
                            subtitle={l("Mehrstufiges Lagermodell vorbereitet (Site/Warehouse/Stock-Status).", "Prepared for multi-level model (site/warehouse/stock status).")}
                            rows={inventoryRows}
                            columns={[
                                { key: "sku", label: "SKU" },
                                { key: "product", label: l("Produkt", "Product") },
                                { key: "warehouse", label: l("Lager", "Warehouse") },
                                { key: "quantity", label: l("Menge", "Quantity") },
                                { key: "status", label: l("Status", "Status") },
                            ]}
                            timeline={movementTimeline}
                            text={text}
                        />
                    </div>
                )}
            </main>
        </div>
    );
};

export default SupplyChainDashboard;
