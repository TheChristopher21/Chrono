import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import { useAuth } from "../../context/AuthContext.jsx";
import api from "../../utils/api.js";
import { LanguageContext } from "../../context/LanguageContext.jsx";
import { useNotification } from "../../context/NotificationContext.jsx";
import "../../styles/SupplyChainDashboardScoped.css";
import ProcessWorkspace from "./components/ProcessWorkspace.jsx";
import CycleCountDrawerActions from "./components/CycleCountDrawerActions.jsx";
import { ACCESS_MANAGE, hasPageAccess } from "../../utils/pageAccess.js";
import {
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
const negativeStockMovementTypes = new Set(["ISSUE", "WRITE_OFF"]);
const doneStatuses = new Set(["COMPLETED", "CLOSED", "RESOLVED"]);
const attentionStatuses = new Set(["OPEN", "BLOCKED", "APPROVAL_REQUIRED"]);
const inventoryStatusProgressMap = {
    AVAILABLE: 100,
    RESERVED: 88,
    IN_TRANSIT: 64,
    IN_QC: 48,
    RETURNED: 72,
    DAMAGED: 18,
    BLOCKED: 15,
    COMPLETED: 100,
};

const workspaceIconMap = {
    overview: "HQ",
    inbound: "IN",
    outbound: "OUT",
    production: "PRD",
    service: "SRV",
    returns: "RMA",
    inventory: "INV",
    buckets: "BKT",
    exceptions: "EXC",
    "cycle-count": "CC",
    governance: "AUD",
    company: "CO",
};

const CODE_LABELS = {
    OPEN: { de: "Offen", en: "Open" },
    PLANNED: { de: "Geplant", en: "Planned" },
    PENDING: { de: "Ausstehend", en: "Pending" },
    IN_PROGRESS: { de: "In Bearbeitung", en: "In progress" },
    BLOCKED: { de: "Gesperrt", en: "Blocked" },
    APPROVED: { de: "Freigegeben", en: "Approved" },
    REJECTED: { de: "Abgelehnt", en: "Rejected" },
    COMPLETED: { de: "Abgeschlossen", en: "Completed" },
    CLOSED: { de: "Geschlossen", en: "Closed" },
    RESOLVED: { de: "Gelöst", en: "Resolved" },
    CANCELLED: { de: "Storniert", en: "Cancelled" },
    AVAILABLE: { de: "Verfügbar", en: "Available" },
    RESERVED: { de: "Reserviert", en: "Reserved" },
    IN_QC: { de: "In QS", en: "In QC" },
    IN_TRANSIT: { de: "Im Transit", en: "In transit" },
    RECEIVED: { de: "Empfangen", en: "Received" },
    PICKED: { de: "Kommissioniert", en: "Picked" },
    PACKED: { de: "Verpackt", en: "Packed" },
    SHIPPED: { de: "Versendet", en: "Shipped" },
    DELIVERED: { de: "Zugestellt", en: "Delivered" },
    RELEASED: { de: "Freigegeben", en: "Released" },
    DAMAGED: { de: "Beschädigt", en: "Damaged" },
    RETURNED: { de: "Retourniert", en: "Returned" },
    EXPIRING: { de: "Läuft bald ab", en: "Expiring" },
    BREACHED: { de: "Verletzt", en: "Breached" },
    OK: { de: "In Ordnung", en: "OK" },
    HIGH: { de: "Hoch", en: "High" },
    MEDIUM: { de: "Mittel", en: "Medium" },
    LOW: { de: "Niedrig", en: "Low" },
    NORMAL: { de: "Normal", en: "Normal" },
    AUDITED: { de: "Geprüft", en: "Audited" },
    LIVE: { de: "Aktiv", en: "Live" },
    ROLLING: { de: "Im Rollout", en: "Rolling" },
    APPROVAL_REQUIRED: { de: "Freigabe erforderlich", en: "Approval required" },
    LATE_DELIVERY: { de: "Verspätete Lieferung", en: "Late delivery" },
    QC_BLOCKED: { de: "QS-Sperre", en: "QC blocked" },
    SLA_BREACH: { de: "SLA-Verstoss", en: "SLA breach" },
    PROCESS_UPDATE: { de: "Prozessaktualisierung", en: "Process update" },
    STOCK_UPDATED: { de: "Bestand aktualisiert", en: "Stock updated" },
    STOCK_HISTORY_ENTRY: { de: "Bestandshistorie", en: "Stock history" },
    INFO: { de: "Info", en: "Info" },
    WARNING: { de: "Warnung", en: "Warning" },
    CRITICAL: { de: "Kritisch", en: "Critical" },
};

const ENTITY_LABELS = {
    STOCK: { de: "Bestand", en: "Stock" },
    ORDER: { de: "Auftrag", en: "Order" },
    PRODUCTION_ORDER: { de: "Produktionsauftrag", en: "Production order" },
    SERVICE_CASE: { de: "Servicefall", en: "Service case" },
    COMPANY: { de: "Gesellschaft", en: "Company" },
    USER: { de: "Benutzer", en: "User" },
};

const ACTOR_LABELS = {
    system: { de: "System", en: "System" },
    "warehouse.bot": { de: "System", en: "System" },
    "production.planner": { de: "Produktionsplanung", en: "Production planning" },
    "service.dispatch": { de: "Service-Disposition", en: "Service dispatch" },
    quality: { de: "Qualität", en: "Quality" },
    planner: { de: "Planung", en: "Planning" },
    "count-team": { de: "Zählteam", en: "Count team" },
};

const CONTEXT_LABELS = {
    "Field": { de: "Aussendienst", en: "Field" },
    "Service": { de: "Service", en: "Service" },
    "Network": { de: "Netzwerk", en: "Network" },
    "Global": { de: "Global", en: "Global" },
    "Main Site": { de: "Hauptstandort", en: "Main Site" },
    "Plant-1": { de: "Werk 1", en: "Plant 1" },
    "Main": { de: "Hauptwerk", en: "Main" },
    "GENERAL": { de: "Allgemein", en: "General" },
    "Shared EU": { de: "Gemeinsames EU-Lager", en: "Shared EU warehouse" },
    "Shared US": { de: "Gemeinsames US-Lager", en: "Shared US warehouse" },
    "Intercompany": { de: "Konzernintern", en: "Intercompany" },
    "de": { de: "Deutsch", en: "German" },
    "en": { de: "Englisch", en: "English" },
    "DE-VAT": { de: "Deutsche Umsatzsteuer", en: "German VAT" },
    "US-SalesTax": { de: "US-Umsatzsteuer", en: "US sales tax" },
};

const countAttentionItems = (rows = []) => rows.filter((row) => (
    attentionStatuses.has(String(row.status))
    || String(row.sla ?? "").toUpperCase() === "BREACHED"
    || (Number(row.variance ?? 0) > 0 && !doneStatuses.has(String(row.status)))
)).length;

const getWorkspaceTone = (rows = []) => {
    const attention = countAttentionItems(rows);
    if (!rows.length) return "neutral";
    if (attention === 0) return "safe";
    if (attention >= Math.max(2, Math.ceil(rows.length * 0.35))) return "danger";
    return "warn";
};

const SupplyChainDashboard = () => {
    const { currentUser } = useAuth();
    const { language } = useContext(LanguageContext);
    const { notify } = useNotification();
    const workspaceSectionRef = React.useRef(null);
    const l = useCallback((de, en) => (language === "de" ? de : en), [language]);
    const locale = language === "de" ? "de" : "en";
    const canManageSupplyChain = hasPageAccess(currentUser, "supplyChain", ACCESS_MANAGE);
    const notifyViewOnlySupplyChain = useCallback(() => {
        notify(
            l(
                "Nur Ansicht: Dieser Benutzer darf Supply Chain sehen, aber keine Änderungen vornehmen.",
                "View only: this user can see Supply Chain, but cannot change anything."
            ),
            "warning"
        );
    }, [l, notify]);
    const helpContent = useMemo(() => ({
        otif: {
            title: "OTIF",
            description: l(
                "OTIF bedeutet On Time In Full. Die Kennzahl zeigt, wie viele Aufträge pünktlich und vollständig ausgeliefert wurden.",
                "OTIF means On Time In Full. It shows how many orders were delivered on time and in full."
            ),
        },
        pickPerformance: {
            title: l("Pick-Leistung", "Pick performance"),
            description: l(
                "Zeigt, wie schnell und sauber die Kommissionierung läuft.",
                "Shows how quickly and cleanly picking is being completed."
            ),
        },
        inventoryAccuracy: {
            title: l("Inventurgenauigkeit", "Inventory accuracy"),
            description: l(
                "Vergleicht Systembestand und gezählten echten Lagerbestand.",
                "Compares system stock with the physically counted stock."
            ),
        },
        returnRate: {
            title: l("Retourenquote", "Return rate"),
            description: l(
                "Anteil der Auslieferungen, die als Retoure zurückkommen.",
                "Share of deliveries that come back as returns."
            ),
        },
        asn: {
            title: "ASN",
            description: l(
                "ASN bedeutet Advance Shipping Notice. Das ist die digitale Vorankündigung einer Lieferung vor dem Eintreffen.",
                "ASN means Advance Shipping Notice. It is the digital pre-alert of a delivery before it arrives."
            ),
        },
        qc: {
            title: l("QS / QC", "QC"),
            description: l(
                "QS oder QC steht für Qualitätssicherung. Hier wird geprüft, ob Ware oder Arbeit in Ordnung ist.",
                "QC stands for quality control. It is where goods or work are checked for quality."
            ),
        },
        sla: {
            title: "SLA",
            description: l(
                "SLA bedeutet Service Level Agreement. Gemeint ist die vereinbarte Zeit für Reaktion oder Lösung.",
                "SLA means Service Level Agreement. It is the agreed response or resolution time."
            ),
        },
        rma: {
            title: "RMA",
            description: l(
                "RMA bedeutet Return Merchandise Authorization. Das ist die Vorgangsnummer für eine genehmigte Retoure.",
                "RMA means Return Merchandise Authorization. It is the case number for an approved return."
            ),
        },
        sku: {
            title: "SKU",
            description: l(
                "SKU bedeutet Stock Keeping Unit. Das ist die interne Artikelnummer eines Produkts.",
                "SKU means Stock Keeping Unit. It is the internal item number of a product."
            ),
        },
        eta: {
            title: "ETA",
            description: l(
                "ETA bedeutet Estimated Time of Arrival. Das ist der erwartete Ankunftszeitpunkt.",
                "ETA means Estimated Time of Arrival. It is the expected arrival time."
            ),
        },
        putaway: {
            title: l("Einlagerung", "Putaway"),
            description: l(
                "Einlagerung bedeutet: eingetroffene Ware auf den richtigen Lagerplatz bringen.",
                "Putaway means moving received goods into the correct storage location."
            ),
        },
        wave: {
            title: l("Welle", "Wave"),
            description: l(
                "Eine Welle ist eine Gruppe von Aufträgen, die gemeinsam bearbeitet oder kommissioniert wird.",
                "A wave is a group of orders processed or picked together."
            ),
        },
        picking: {
            title: l("Kommissionierung", "Picking"),
            description: l(
                "Kommissionierung bedeutet: die benötigten Artikel für einen Auftrag aus dem Lager entnehmen.",
                "Picking means collecting the required items from the warehouse for an order."
            ),
        },
        escalation: {
            title: l("Eskalation", "Escalation"),
            description: l(
                "Eine Eskalation bedeutet, dass ein Fall schneller oder auf höherer Ebene bearbeitet werden muss.",
                "An escalation means a case needs faster handling or attention at a higher level."
            ),
        },
        multiLevelWarehouse: {
            title: l("Mehrstufiges Lager", "Multi-level warehouse"),
            description: l(
                "Der Bestand wird nach mehreren Ebenen organisiert, zum Beispiel Standort, Lager, Zone, Gang, Regal und Fach.",
                "Stock is organized across multiple levels such as site, warehouse, zone, aisle, rack, and bin."
            ),
        },
        inventoryBucket: {
            title: l("Bestandsarten", "Inventory buckets"),
            description: l(
                "Bestandsarten teilen Lagerbestand nach Zustand ein, zum Beispiel verfügbar, reserviert oder gesperrt.",
                "Inventory buckets split stock by state, such as available, reserved, or blocked."
            ),
        },
        exceptionCenter: {
            title: l("Ausnahmezentrum", "Exception center"),
            description: l(
                "Hier landen Fälle, die von der normalen Abwicklung abweichen und Aufmerksamkeit brauchen.",
                "This is where cases that deviate from normal processing and need attention are collected."
            ),
        },
        cycleCount: {
            title: l("Zykluszählung", "Cycle counting"),
            description: l(
                "Eine Zykluszählung ist eine regelmäßige Teilinventur einzelner Lagerplätze statt einer kompletten Gesamtinventur.",
                "Cycle counting is a recurring count of selected storage locations instead of one full stock count."
            ),
        },
        fourEyes: {
            title: l("4-Augen-Freigabe", "4-eyes approval"),
            description: l(
                "Ein zweiter Mensch prüft und bestätigt den Vorgang, bevor er endgültig gilt.",
                "A second person checks and confirms the action before it becomes final."
            ),
        },
        governance: {
            title: l("Governance", "Governance"),
            description: l(
                "Governance meint Regeln, Freigaben und Nachvollziehbarkeit in den Prozessen.",
                "Governance means rules, approvals, and traceability across processes."
            ),
        },
        intercompany: {
            title: "Intercompany",
            description: l(
                "Intercompany bedeutet Geschäfte oder Bewegungen zwischen Gesellschaften derselben Unternehmensgruppe.",
                "Intercompany means transactions or movements between companies of the same group."
            ),
        },
        approvalProgress: {
            title: l("Freigabefortschritt", "Approval progress"),
            description: l(
                "Zeigt, wie weit ein Vorgang im Prüf- oder Freigabeprozess ist.",
                "Shows how far a record has moved through review or approval."
            ),
        },
        variance: {
            title: l("Differenz", "Variance"),
            description: l(
                "Die Differenz zeigt die Abweichung zwischen gezähltem Bestand und Systembestand.",
                "Variance shows the difference between counted stock and system stock."
            ),
        },
        lot: {
            title: l("Charge", "Lot"),
            description: l(
                "Eine Charge ist eine Gruppe gleicher Ware zur Rückverfolgung.",
                "A lot is a batch of similar goods used for traceability."
            ),
        },
        entryType: {
            title: l("Buchungstyp", "Entry type"),
            description: l(
                "Legt fest, ob Bestand zu- oder abgebucht wird, zum Beispiel Wareneingang, Entnahme oder Abschreibung.",
                "Defines whether stock is increased or decreased, for example receipt, issue, or write-off."
            ),
        },
        reference: {
            title: l("Referenz", "Reference"),
            description: l(
                "Freies Kennzeichen wie Lieferschein, Auftragsnummer oder Notiz, damit man die Buchung später leichter findet.",
                "Free text like delivery note, order number, or note so the posting can be found later."
            ),
        },
        warehouseCode: {
            title: l("Lagercode", "Warehouse code"),
            description: l(
                "Kurzer interner Code für ein Lager oder einen Standort.",
                "Short internal code for a warehouse or location."
            ),
        },
    }), [l]);

    const translateCode = useCallback((value) => {
        const key = String(value ?? "").trim().toUpperCase();
        return CODE_LABELS[key]?.[locale] ?? String(value ?? "-");
    }, [locale]);

    const translateActor = useCallback((value) => {
        const key = String(value ?? "").trim();
        return ACTOR_LABELS[key]?.[locale] ?? String(value ?? "-");
    }, [locale]);

    const translateContext = useCallback((value) => {
        const key = String(value ?? "").trim();
        return CONTEXT_LABELS[key]?.[locale] ?? String(value ?? "-");
    }, [locale]);

    const translateEntity = useCallback((entityType, entityId) => {
        const label = ENTITY_LABELS[String(entityType ?? "").trim()]?.[locale] ?? String(entityType ?? "-");
        return `${label} #${entityId ?? "-"}`;
    }, [locale]);

    const translateAction = useCallback((value) => {
        const key = String(value ?? "").trim().toUpperCase();

        if (CODE_LABELS[key]?.[locale]) {
            return CODE_LABELS[key][locale];
        }

        if (key.startsWith("ORDER_")) {
            return `${l("Auftrag", "Order")} ${translateCode(key.slice(6))}`;
        }

        if (key.startsWith("PRODUCTION_")) {
            return `${l("Produktion", "Production")} ${translateCode(key.slice(11))}`;
        }

        if (key.startsWith("SERVICE_")) {
            return `${l("Servicefall", "Service case")} ${translateCode(key.slice(8))}`;
        }

        return String(value ?? "-");
    }, [l, locale, translateCode]);

    const deriveInventoryStatus = useCallback((entry) => {
        const explicitStatus = String(entry?.status ?? "").trim().toUpperCase();
        const availableQty = Number(entry?.buckets?.AVAILABLE ?? entry?.quantity ?? 0);
        const reservedQty = Number(entry?.buckets?.RESERVED ?? 0);
        const blockedQty = Number(entry?.buckets?.BLOCKED ?? 0);
        const qcQty = Number(entry?.buckets?.IN_QC ?? 0);
        const transitQty = Number(entry?.buckets?.IN_TRANSIT ?? 0);
        const damagedQty = Number(entry?.buckets?.DAMAGED ?? 0);
        const returnedQty = Number(entry?.buckets?.RETURNED ?? 0);

        let code = explicitStatus && explicitStatus !== "OPEN" ? explicitStatus : "";

        if (!code) {
            if (blockedQty > 0) code = "BLOCKED";
            else if (qcQty > 0) code = "IN_QC";
            else if (transitQty > 0) code = "IN_TRANSIT";
            else if (damagedQty > 0) code = "DAMAGED";
            else if (returnedQty > 0) code = "RETURNED";
            else if (reservedQty > 0 && availableQty <= 0) code = "RESERVED";
            else if (availableQty > 0) code = "AVAILABLE";
            else code = "COMPLETED";
        }

        return {
            code,
            approvalProgress: inventoryStatusProgressMap[code] ?? 100,
        };
    }, []);

    const [loading, setLoading] = useState(true);
    const [role, setRole] = useState("warehouse");
    const [activeWorkspace, setActiveWorkspace] = useState("overview");
    const [globalSearch, setGlobalSearch] = useState("");
    const [products, setProducts] = useState([]);
    const [warehouses, setWarehouses] = useState([]);
    const [stock, setStock] = useState([]);
    const [stockMovements, setStockMovements] = useState([]);
    const [inboundOrders, setInboundOrders] = useState([]);
    const [outboundOrders, setOutboundOrders] = useState([]);
    const [productionOrders, setProductionOrders] = useState([]);
    const [serviceCases, setServiceCases] = useState([]);
    const [cycleCounts, setCycleCounts] = useState([]);
    const [auditLogs, setAuditLogs] = useState([]);

    const loadData = useCallback(async () => {
        const list = (payload) => (Array.isArray(payload) ? payload : payload?.content ?? []);
        const [
            productRes,
            warehouseRes,
            stockRes,
            movementRes,
            inboundRes,
            outboundRes,
            productionRes,
            serviceRes,
            cycleCountRes,
            auditRes,
        ] = await Promise.allSettled([
            api.get("/api/supply-chain/products"),
            api.get("/api/supply-chain/warehouses"),
            api.get("/api/supply-chain/stock"),
            api.get("/api/supply-chain/stock-movements?size=200"),
            api.get("/api/supply-chain/purchase-orders"),
            api.get("/api/supply-chain/sales-orders"),
            api.get("/api/supply-chain/production-orders"),
            api.get("/api/supply-chain/service-requests"),
            api.get("/api/supply-chain/cycle-counts?size=200"),
            api.get("/api/audit?limit=25"),
        ]);

        const pickList = (result) => (result.status === "fulfilled" ? list(result.value?.data) : []);

        setProducts(pickList(productRes).map(normalizeProduct));
        setWarehouses(pickList(warehouseRes).map(normalizeWarehouse));
        setStock(pickList(stockRes).map(normalizeStock));
        setStockMovements(pickList(movementRes));
        setInboundOrders(pickList(inboundRes).map(normalizeDelivery));
        setOutboundOrders(pickList(outboundRes).map(normalizeOrder));
        setProductionOrders(pickList(productionRes).map(normalizeOrder));
        setServiceCases(pickList(serviceRes).map(normalizeServiceCase));
        setCycleCounts(pickList(cycleCountRes));
        setAuditLogs(auditRes.status === "fulfilled" && Array.isArray(auditRes.value?.data) ? auditRes.value.data : []);

        const requiredFailures = [
            productRes,
            warehouseRes,
            stockRes,
            movementRes,
            inboundRes,
            outboundRes,
            productionRes,
            serviceRes,
            cycleCountRes,
        ].some((result) => result.status === "rejected");

        if (requiredFailures) {
            notify(l("Supply-Chain-Daten konnten nur teilweise geladen werden.", "Supply-chain data could only be loaded partially."), "warning");
        }

        return !requiredFailures;
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

    const movementTimeline = useMemo(
        () => createMovementHistory({ stock, productionOrders, serviceRequests: serviceCases, inboundOrders, outboundOrders }),
        [stock, productionOrders, serviceCases, inboundOrders, outboundOrders]
    );
    const localizedMovementTimeline = useMemo(() => movementTimeline.map((entry) => ({
        ...entry,
        action: translateAction(entry.action),
        user: translateActor(entry.user),
    })), [movementTimeline, translateAction, translateActor]);
    const mapCycleCountToRow = useCallback((entry) => {
        const status = String(entry?.status ?? "PLANNED").toUpperCase();
        return {
            id: entry.id,
            plan: entry.planNumber ?? "-",
            sku: entry.productSku ?? "-",
            product: entry.productName ?? "-",
            warehouse: entry.warehouseName ?? "-",
            site: "-",
            expectedQuantity: Number(entry.expectedQuantity ?? 0),
            countedQuantity: entry.countedQuantity == null ? null : Number(entry.countedQuantity),
            variance: Number(entry.variance ?? 0),
            status,
            statusLabel: translateCode(status),
            partner: entry.countedBy ?? entry.requestedBy ?? "-",
            owner: entry.countedBy ?? entry.requestedBy ?? l("Zählteam", "Count team"),
            requestedBy: entry.requestedBy ?? "-",
            countedBy: entry.countedBy ?? "-",
            approvedBy: entry.approvedBy ?? "-",
            createdAt: entry.createdAt ?? "-",
            countedAt: entry.countedAt ?? "-",
            approvedAt: entry.approvedAt ?? "-",
            approvalProgress: status === "PLANNED" ? 18 : status === "APPROVAL_REQUIRED" ? 58 : 100,
        };
    }, [l, translateCode]);

    const inventoryRows = useMemo(() => stock.map((entry) => {
        const product = products.find((item) => item.id === entry.productId);
        const warehouse = warehouses.find((item) => item.id === entry.warehouseId);
        const inventoryStatus = deriveInventoryStatus(entry);
        return {
            id: entry.id,
            sku: product?.sku ?? "-",
            product: product?.name ?? "-",
            warehouse: warehouse?.name ?? "-",
            site: translateContext(warehouse?.siteName ?? "-"),
            zone: translateContext(warehouse?.zone ?? "-"),
            bin: warehouse?.bin ?? "-",
            status: inventoryStatus.code,
            statusLabel: translateCode(inventoryStatus.code),
            quantity: entry.quantity,
            lot: entry.lotNumber,
            partner: "-",
            owner: l("Bestandsteam", "Inventory team"),
            approvalProgress: inventoryStatus.approvalProgress,
        };
    }), [deriveInventoryStatus, l, products, stock, translateCode, translateContext, warehouses]);

    const summarizeInboundProducts = useCallback((lines = []) => {
        if (!Array.isArray(lines) || lines.length === 0) {
            return "-";
        }

        const labels = [...new Set(lines.map((line) => {
            const product = products.find((item) => item.id === line?.productId);
            return product?.name ?? product?.sku ?? null;
        }).filter(Boolean))];

        if (labels.length === 0) {
            return "-";
        }

        if (labels.length <= 2) {
            return labels.join(", ");
        }

        return `${labels.slice(0, 2).join(", ")} +${labels.length - 2}`;
    }, [products]);

    const inboundRows = useMemo(() => {
        const orderRows = inboundOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        asn: entry.asn,
        dock: entry.dock,
        supplier: entry.supplier,
        product: summarizeInboundProducts(entry.lines),
        quantity: entry.quantity ?? 0,
        warehouse: warehouses.find((item) => item.id === entry.warehouseId)?.name ?? "-",
        site: translateContext(warehouses.find((item) => item.id === entry.warehouseId)?.siteName ?? "-"),
        status: entry.status,
        statusLabel: translateCode(entry.status),
        eta: entry.eta ?? "-",
        partner: entry.supplier,
        owner: entry.status === "COMPLETED" ? l("System", "System") : l("Lager Team", "Warehouse team"),
        approvalProgress: entry.status === "COMPLETED" ? 100 : 64,
        }));

        const manualRows = stockMovements
            .filter((movement) => movement?.type === "RECEIPT")
            .map((movement) => {
                const warehouse = warehouses.find((item) => item.id === movement.warehouseId);
                const product = products.find((item) => item.id === movement.productId);
                const reference = String(movement.reference ?? "").trim();
                return {
                    id: `movement-${movement.id}`,
                    order: `${l("Buchung", "Entry")}-${movement.id}`,
                    asn: reference || "-",
                    dock: l("Manuell", "Manual"),
                    supplier: l("Nicht hinterlegt", "Not recorded"),
                    product: product?.name ?? "-",
                    quantity: Math.abs(Number(movement.quantityChange ?? 0)),
                    warehouse: warehouse?.name ?? "-",
                    site: translateContext(warehouse?.siteName ?? "-"),
                    status: "COMPLETED",
                    statusLabel: l("Gebucht", "Posted"),
                    eta: movement.movementDate ?? "-",
                    partner: "-",
                    owner: l("Lager Team", "Warehouse team"),
                    approvalProgress: 100,
                };
            });

        return [...manualRows, ...orderRows];
    }, [inboundOrders, l, products, stockMovements, summarizeInboundProducts, translateCode, translateContext, warehouses]);

    const outboundRows = useMemo(() => outboundOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        customer: entry.customerName,
        priority: translateCode(entry.priority),
        warehouse: "-",
        site: "-",
        status: entry.status,
        statusLabel: translateCode(entry.status),
        dueDate: entry.dueDate ?? "-",
        partner: entry.customerName,
        owner: l("Kommissionierung", "Picking team"),
        approvalProgress: entry.status === "COMPLETED" ? 100 : 58,
    })), [l, outboundOrders, translateCode]);

    const productionRows = useMemo(() => productionOrders.map((entry) => ({
        id: entry.id,
        order: entry.orderNumber,
        product: entry.productName ?? "-",
        quantity: entry.quantity ?? 0,
        priority: translateCode(entry.priority),
        status: entry.status,
        statusLabel: translateCode(entry.status),
        warehouse: translateContext("Plant-1"),
        site: translateContext("Main"),
        partner: translateActor(entry.owner ?? "-"),
        owner: translateActor(entry.owner ?? "production.planner"),
        approvalProgress: entry.status === "IN_PROGRESS" ? 47 : entry.status === "COMPLETED" ? 100 : 25,
    })), [productionOrders, translateActor, translateCode, translateContext]);

    const serviceRows = useMemo(() => serviceCases.map((entry) => ({
        id: entry.id,
        case: entry.subject,
        customer: entry.customerName,
        owner: translateActor(entry.owner),
        warehouse: translateContext("Field"),
        site: translateContext("Service"),
        status: entry.status,
        statusLabel: translateCode(entry.status),
        sla: translateCode(entry.slaBreached ? "BREACHED" : "OK"),
        partner: entry.customerName,
        approvalProgress: entry.status === "CLOSED" ? 100 : 36,
    })), [serviceCases, translateActor, translateCode, translateContext]);

    const exceptionRows = useMemo(() => buildExceptionRows({ inboundOrders, stock, serviceCases }).map((row) => ({
        id: row.id,
        type: translateCode(row.type),
        severity: translateCode(row.severity),
        ref: row.ref,
        owner: translateActor(row.owner),
        status: row.severity === "HIGH" ? "BLOCKED" : "OPEN",
        statusLabel: translateCode(row.severity),
        warehouse: "-",
        site: "-",
        partner: translateActor(row.owner),
        approvalProgress: row.severity === "HIGH" ? 20 : 55,
    })), [inboundOrders, serviceCases, stock, translateActor, translateCode]);

    const cycleCountRows = useMemo(() => cycleCounts.map(mapCycleCountToRow), [cycleCounts, mapCycleCountToRow]);

    const bucketRows = useMemo(() => buildInventoryBucketRows(stock).map((row) => ({
        id: row.id,
        bucket: translateCode(row.bucket),
        quantity: row.quantity,
        status: row.quantity > 0 ? "IN_PROGRESS" : "COMPLETED",
        statusLabel: translateCode(row.quantity > 0 ? "IN_PROGRESS" : "COMPLETED"),
        warehouse: translateContext("Network"),
        site: translateContext("Global"),
        partner: "-",
        owner: l("Stammdaten", "Master data"),
        approvalProgress: row.quantity > 0 ? 65 : 100,
    })), [l, stock, translateCode, translateContext]);

    const governanceRows = useMemo(() => auditLogs.map((entry) => {
        const severity = String(entry?.severity ?? "INFO").toUpperCase();
        const status = severity === "CRITICAL" ? "BLOCKED" : severity === "WARNING" ? "IN_PROGRESS" : "COMPLETED";
        return {
            id: entry.id,
            entity: translateEntity(entry.targetType, entry.targetId),
            action: translateAction(entry.action),
            changedBy: entry.username ?? "-",
            changedAt: entry.createdAt ?? "-",
            reason: entry.details ?? "-",
            status,
            statusLabel: translateCode(severity),
            warehouse: "-",
            site: "-",
            partner: "-",
            owner: entry.username ?? l("Audit", "Audit"),
            approvalProgress: severity === "CRITICAL" ? 28 : severity === "WARNING" ? 72 : 100,
        };
    }), [auditLogs, l, translateAction, translateCode, translateEntity]);

    const overviewRowsRaw = useMemo(() => {
        const tasks = [];
        const pushTask = (task) => tasks.push(task);

        exceptionRows.forEach((row) => pushTask({
            id: `overview-exception-${row.id}`,
            type: l("Ausnahme", "Exception"),
            ref: row.ref,
            area: l("Ausnahmen", "Exceptions"),
            status: row.status,
            statusLabel: row.statusLabel,
            priority: row.status === "BLOCKED" ? l("Kritisch", "Critical") : l("Hoch", "High"),
            owner: row.owner,
            dueDate: l("Sofort", "Now"),
            warehouse: row.warehouse,
            site: row.site,
            partner: row.partner,
            approvalProgress: row.approvalProgress,
            sortWeight: row.status === "BLOCKED" ? 100 : 82,
        }));

        inboundRows
            .filter((row) => !doneStatuses.has(String(row.status)))
            .forEach((row) => pushTask({
                id: `overview-inbound-${row.id}`,
                type: l("Wareneingang", "Inbound"),
                ref: row.asn && row.asn !== "-" ? row.asn : row.order,
                area: l("Wareneingang", "Inbound"),
                status: row.status,
                statusLabel: row.statusLabel,
                priority: attentionStatuses.has(String(row.status)) ? l("Hoch", "High") : l("Normal", "Normal"),
                owner: row.owner,
                dueDate: row.eta,
                warehouse: row.warehouse,
                site: row.site,
                partner: row.partner,
                approvalProgress: row.approvalProgress,
                sortWeight: attentionStatuses.has(String(row.status)) ? 74 : 58,
            }));

        outboundRows
            .filter((row) => !doneStatuses.has(String(row.status)))
            .forEach((row) => pushTask({
                id: `overview-outbound-${row.id}`,
                type: l("Pickauftrag", "Pick order"),
                ref: row.order,
                area: l("Warenausgang", "Outbound"),
                status: row.status,
                statusLabel: row.statusLabel,
                priority: row.priority ?? l("Normal", "Normal"),
                owner: row.owner,
                dueDate: row.dueDate,
                warehouse: row.warehouse,
                site: row.site,
                partner: row.partner,
                approvalProgress: row.approvalProgress,
                sortWeight: attentionStatuses.has(String(row.status)) ? 70 : 52,
            }));

        cycleCountRows
            .filter((row) => !doneStatuses.has(String(row.status)))
            .forEach((row) => pushTask({
                id: `overview-cycle-${row.id}`,
                type: l("Zählung", "Count"),
                ref: row.plan,
                area: l("Inventur & Zählung", "Inventory counts"),
                status: row.status,
                statusLabel: row.statusLabel,
                priority: row.status === "APPROVAL_REQUIRED" ? l("Hoch", "High") : l("Normal", "Normal"),
                owner: row.owner,
                dueDate: row.countedAt && row.countedAt !== "-" ? row.countedAt : row.createdAt,
                warehouse: row.warehouse,
                site: row.site,
                partner: row.partner,
                approvalProgress: row.approvalProgress,
                sortWeight: row.status === "APPROVAL_REQUIRED" ? 76 : 44,
            }));

        inventoryRows
            .filter((row) => ["BLOCKED", "IN_QC", "DAMAGED"].includes(String(row.status)) || Number(row.quantity ?? 0) <= 0)
            .forEach((row) => pushTask({
                id: `overview-inventory-${row.id}`,
                type: l("Bestand", "Stock"),
                ref: row.sku,
                area: l("Bestand", "Inventory"),
                status: row.status,
                statusLabel: row.statusLabel,
                priority: ["BLOCKED", "DAMAGED"].includes(String(row.status)) ? l("Hoch", "High") : l("Normal", "Normal"),
                owner: row.owner,
                dueDate: l("Heute", "Today"),
                warehouse: row.warehouse,
                site: row.site,
                partner: row.product,
                approvalProgress: row.approvalProgress,
                sortWeight: ["BLOCKED", "DAMAGED"].includes(String(row.status)) ? 78 : 48,
            }));

        if (!tasks.length) {
            pushTask({
                id: "overview-next-setup",
                type: l("Nächster Schritt", "Next step"),
                ref: products.length && warehouses.length ? l("Ersten Vorgang starten", "Start first workflow") : l("Setup abschließen", "Finish setup"),
                area: products.length && warehouses.length ? l("Wareneingang", "Inbound") : l("Stammdaten", "Master data"),
                status: "OPEN",
                statusLabel: l("Bereit", "Ready"),
                priority: l("Normal", "Normal"),
                owner: products.length && warehouses.length ? l("Lager Team", "Warehouse team") : l("Admin", "Admin"),
                dueDate: l("Heute", "Today"),
                warehouse: warehouses[0]?.name ?? l("Hauptlager", "Main warehouse"),
                site: translateContext(warehouses[0]?.siteName ?? l("Alle Standorte", "All sites")),
                partner: "-",
                approvalProgress: 20,
                sortWeight: 10,
            });
        }

        return tasks
            .sort((left, right) => right.sortWeight - left.sortWeight)
            .map(({ sortWeight, ...task }) => task);
    }, [cycleCountRows, exceptionRows, inboundRows, inventoryRows, l, outboundRows, products.length, translateContext, warehouses]);

    const overviewRows = useMemo(() => {
        const query = globalSearch.trim().toLowerCase();
        if (!query) return overviewRowsRaw;
        return overviewRowsRaw.filter((row) => JSON.stringify(row).toLowerCase().includes(query));
    }, [globalSearch, overviewRowsRaw]);

    const roleDefinitions = useMemo(() => ([
        { id: "warehouse", label: l("Lager", "Warehouse"), description: l("Fokus auf offene Arbeit, Wareneingang und Bestand.", "Focus on open work, inbound and inventory."), defaultWorkspace: "overview" },
        { id: "planner", label: l("Planung", "Planner"), description: l("Plant Produktionsaufträge, Materialfluss und Termine.", "Plans production orders, material flow and due dates."), defaultWorkspace: "production" },
        { id: "quality", label: l("Qualität", "Quality"), description: l("Sieht Blocker, Abweichungen und Freigaben auf einen Blick.", "Sees blockers, deviations and approvals at a glance."), defaultWorkspace: "exceptions" },
        { id: "service", label: l("Service", "Service"), description: l("Steuert Reaktionszeiten (SLA) und Kundenfälle.", "Steers response times (SLA) and customer cases."), defaultWorkspace: "service" },
        { id: "admin", label: l("Admin", "Admin"), description: l("Überblick über Regeln, Freigaben und Prüfhistorie.", "Overview of rules, approvals, and audit history."), defaultWorkspace: "governance" },
    ]), [l]);

    const scrollToWorkspaceSection = useCallback(() => {
        if (typeof window === "undefined") return;
        window.requestAnimationFrame(() => {
            workspaceSectionRef.current?.scrollIntoView({
                behavior: "smooth",
                block: "start",
            });
        });
    }, []);

    const activateWorkspace = useCallback((workspaceId, options = {}) => {
        const { scroll = false } = options;
        setActiveWorkspace(workspaceId);
        if (scroll) {
            scrollToWorkspaceSection();
        }
    }, [scrollToWorkspaceSection]);

    const activateRole = useCallback((roleId, options = {}) => {
        const { scroll = false } = options;
        const nextRole = roleDefinitions.find((item) => item.id === roleId) ?? roleDefinitions[0];
        setRole(nextRole.id);
        setActiveWorkspace(nextRole.defaultWorkspace);
        if (scroll) {
            scrollToWorkspaceSection();
        }
    }, [roleDefinitions, scrollToWorkspaceSection]);

    const text = {
        saveView: l("Ansicht speichern", "Save view"),
        loadView: l("Ansicht laden", "Load view"),
        export: l("Export", "Export"),
        columns: l("Spalten", "Columns"),
        massAction: l("Massenaktion ausführen", "Run mass action"),
        massAssign: l("Verantwortung zuweisen", "Assign owner"),
        massPriority: l("Priorität setzen", "Set priority"),
        massClose: l("Abschließen", "Close"),
        moreActions: l("Mehr", "More"),
        prev: l("Zurück", "Prev"),
        next: l("Weiter", "Next"),
        page: l("Seite", "Page"),
        rowAction: l("Aktion", "Action"),
        openRow: l("Öffnen", "Open"),
        empty: l("Keine offenen Vorgänge gefunden", "No open work items found"),
        emptyHint: l("Es gibt aktuell keine Vorgänge für diese Filter. Filter zurücksetzen oder einen neuen Vorgang starten.", "There are no work items for these filters. Reset filters or start a new workflow."),
        drawerTitle: l("Details", "Details"),
        timelineTitle: l("Aktivitäts-Timeline", "Activity timeline"),
        approvalLabel: l("Freigabefortschritt", "Approval progress"),
        locale,
        fieldLabels: {
            id: "ID",
            sku: "SKU",
            product: l("Produkt", "Product"),
            warehouse: l("Lager", "Warehouse"),
            site: l("Standort", "Site"),
            zone: l("Zone", "Zone"),
            bin: l("Fach", "Bin"),
            status: l("Status", "Status"),
            quantity: l("Menge", "Quantity"),
            lot: l("Charge", "Lot"),
            partner: l("Partner", "Partner"),
            order: l("Auftrag", "Order"),
            asn: "ASN",
            dock: l("Tor", "Dock"),
            supplier: l("Lieferant", "Supplier"),
            eta: "ETA",
            customer: l("Kunde", "Customer"),
            priority: l("Priorität", "Priority"),
            dueDate: l("Fällig am", "Due date"),
            owner: l("Verantwortlich", "Owner"),
            case: l("Fall", "Case"),
            sla: "SLA",
            rma: "RMA",
            reason: l("Grund", "Reason"),
            decision: l("Entscheidung", "Decision"),
            type: l("Typ", "Type"),
            severity: l("Schwere", "Severity"),
            ref: l("Referenz", "Reference"),
            plan: l("Plan", "Plan"),
            variance: l("Differenz", "Variance"),
            expectedQuantity: l("Systembestand", "System stock"),
            countedQuantity: l("Gezählt", "Counted"),
            bucket: l("Bestandsart", "Bucket"),
            entity: l("Entität", "Entity"),
            action: l("Aktion", "Action"),
            changedBy: l("Benutzer", "User"),
            changedAt: l("Zeitpunkt", "Timestamp"),
            requestedBy: l("Angelegt von", "Requested by"),
            approvedBy: l("Freigegeben von", "Approved by"),
            createdAt: l("Angelegt am", "Created at"),
            countedAt: l("Gezählt am", "Counted at"),
            approvedAt: l("Freigegeben am", "Approved at"),
            approvalProgress: l("Freigabefortschritt", "Approval progress"),
        },
        fieldHelp: {
            sku: helpContent.sku,
            lot: helpContent.lot,
            asn: helpContent.asn,
            eta: helpContent.eta,
            sla: helpContent.sla,
            rma: helpContent.rma,
            variance: helpContent.variance,
            bucket: helpContent.inventoryBucket,
            approvalProgress: helpContent.approvalProgress,
        },
        filters: {
            searchLabel: l("Schnellsuche", "Quick search"),
            search: l("Suche", "Search"), warehouse: l("Lager", "Warehouse"), site: l("Werk", "Site"), status: l("Status", "Status"),
            partner: l("Kunde/Lieferant", "Customer/Supplier"), date: l("Datum", "Date"), sku: "SKU", batch: l("Charge", "Batch"),
            owner: l("Verantwortlich", "Owner"), priority: l("Priorität", "Priority"), reset: l("Reset", "Reset"),
            more: l("Mehr Filter", "More filters"),
            groups: {
                location: l("Ort & Zeit", "Location & time"),
                status: l("Status & Verantwortung", "Status & ownership"),
                reference: l("Bezug & Artikel", "Reference & item"),
            },
        },
        workspaceMetaLabels: {
            records: l("Datensätze", "Records"),
            attention: l("Handlungsbedarf", "Attention"),
            attentionActive: l("offen", "open"),
            attentionClear: l("Keine offenen Punkte", "No open issues"),
            focus: l("Nächster Schritt", "Next step"),
        },
        filterPanel: {
            kicker: l("Filter", "Filters"),
            title: l("Filter", "Filters"),
            hint: l("Kompakt suchen, erweiterte Filter nur bei Bedarf öffnen.", "Search compactly and open advanced filters only when needed."),
            activeLabel: l("Filter aktiv", "filters active"),
        },
        tablePanel: {
            kicker: l("Arbeitsliste", "Work list"),
            title: l("Offene Vorgänge", "Open work items"),
            hint: l("Priorisierte Liste mit Status, Verantwortung und nächstem Schritt.", "Prioritized list with status, owner, and next step."),
            resultsLabel: l("Treffer", "results"),
            selectedLabel: l("ausgewählt", "selected"),
        },
    };

    const workspaceDefinitions = [
        { id: "overview", title: l("Übersicht", "Overview"), subtitle: l("Heute, offene Arbeit und Blocker", "Today, open work, and blockers"), rows: overviewRows, columns: [{ key: "priority", label: l("Priorität", "Priority") }, { key: "type", label: l("Typ", "Type") }, { key: "ref", label: l("Referenz", "Reference") }, { key: "area", label: l("Bereich", "Area") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }, { key: "dueDate", label: l("Fälligkeit", "Due") }] },
        { id: "inbound", title: l("Wareneingang", "Inbound"), subtitle: l("ASN, Ankunft, QS und Einlagerung", "ASN, arrival, QC, and putaway"), rows: inboundRows, columns: [{ key: "asn", label: "ASN" }, { key: "supplier", label: l("Lieferant", "Supplier") }, { key: "eta", label: "ETA" }, { key: "status", label: l("Status", "Status") }, { key: "product", label: l("Artikel", "Item") }, { key: "quantity", label: l("Menge", "Quantity") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "inventory", title: l("Bestand", "Inventory"), subtitle: l("Lagerbestand, Plätze, Status und Bestandsqualität", "Stock, locations, status, and quality"), rows: inventoryRows, columns: [{ key: "sku", label: "SKU" }, { key: "product", label: l("Artikel", "Item") }, { key: "warehouse", label: l("Lager", "Warehouse") }, { key: "bin", label: l("Lagerplatz", "Bin") }, { key: "quantity", label: l("Bestand", "Stock") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "outbound", title: l("Warenausgang", "Outbound"), subtitle: l("Picklisten, Packen, Versand und Fälligkeit", "Pick lists, packing, shipping, and due dates"), rows: outboundRows, columns: [{ key: "order", label: l("Auftrag", "Order") }, { key: "customer", label: l("Kunde", "Customer") }, { key: "priority", label: l("Priorität", "Priority") }, { key: "dueDate", label: l("Fällig am", "Due date") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "cycle-count", title: l("Zählungen", "Counts"), subtitle: l("Zykluszählungen, Differenzen und Freigabe", "Cycle counts, variances, and approval"), rows: cycleCountRows, columns: [{ key: "plan", label: l("Plan", "Plan") }, { key: "sku", label: "SKU" }, { key: "warehouse", label: l("Lager", "Warehouse") }, { key: "variance", label: l("Differenz", "Variance") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "exceptions", title: l("Ausnahmen", "Exceptions"), subtitle: l("Blocker, QS-Fälle und Eskalationen", "Blockers, QC cases, and escalations"), rows: exceptionRows, columns: [{ key: "severity", label: l("Priorität", "Priority") }, { key: "type", label: l("Problem", "Problem") }, { key: "ref", label: l("Referenz", "Reference") }, { key: "owner", label: l("Verantwortlich", "Owner") }, { key: "status", label: l("Status", "Status") }] },
        { id: "governance", title: l("Freigaben & Audit", "Approvals & audit"), subtitle: l("Prüfung, Historie und Nachvollziehbarkeit", "Review, history, and traceability"), rows: governanceRows, columns: [{ key: "entity", label: l("Entität", "Entity") }, { key: "action", label: l("Aktion", "Action") }, { key: "changedBy", label: l("Benutzer", "User") }, { key: "reason", label: l("Grund", "Reason") }, { key: "status", label: l("Status", "Status") }] },
        { id: "buckets", title: l("Stammdaten", "Master data"), subtitle: l("Produkte, Lager, Zonen und Bestandsarten", "Products, warehouses, zones, and inventory buckets"), rows: bucketRows, columns: [{ key: "bucket", label: l("Bestandsart", "Bucket") }, { key: "quantity", label: l("Menge", "Quantity") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "production", title: l("Produktion", "Production"), subtitle: l("Produktionsaufträge und Materialfluss", "Production orders and material flow"), rows: productionRows, columns: [{ key: "order", label: l("Auftrag", "Order") }, { key: "product", label: l("Produkt", "Product") }, { key: "quantity", label: l("Menge", "Quantity") }, { key: "priority", label: l("Priorität", "Priority") }, { key: "status", label: l("Status", "Status") }, { key: "owner", label: l("Verantwortlich", "Owner") }] },
        { id: "service", title: l("Service", "Service"), subtitle: l("Servicefälle und SLA-Eskalationen", "Service cases and SLA escalations"), rows: serviceRows, columns: [{ key: "case", label: l("Fall", "Case") }, { key: "customer", label: l("Kunde", "Customer") }, { key: "owner", label: l("Verantwortlich", "Owner") }, { key: "sla", label: "SLA" }, { key: "status", label: l("Status", "Status") }] },
    ];

    const workspaceHelpConfig = useMemo(() => ({
        inbound: {
            subtitleParts: [
                { label: "ASN", help: helpContent.asn },
                { label: l("Tor", "Dock") },
                { label: l("QS", "QC"), help: helpContent.qc },
                { label: l("Einlagerung", "Putaway"), help: helpContent.putaway },
            ],
            columnHelp: {
                asn: helpContent.asn,
            },
        },
        outbound: {
            subtitleParts: [
                { label: l("Welle", "Wave"), help: helpContent.wave },
                { label: l("Kommissionierung", "Picking"), help: helpContent.picking },
                { label: l("Packen", "Pack") },
                { label: l("Versand", "Ship") },
            ],
        },
        service: {
            subtitleParts: [
                { label: "SLA", help: helpContent.sla },
                { label: l("Eskalationsfokus", "Escalation focus"), help: helpContent.escalation },
            ],
            columnHelp: {
                sla: helpContent.sla,
            },
        },
        inventory: {
            titleHelp: helpContent.multiLevelWarehouse,
            columnHelp: {
                sku: helpContent.sku,
            },
        },
        buckets: {
            titleHelp: helpContent.inventoryBucket,
            subtitleParts: [
                { label: l("Verfügbar", "Available") },
                { label: l("Reserviert", "Reserved") },
                { label: l("Gesperrt", "Blocked") },
                { label: l("Transit", "Transit") },
                { label: "QC", help: helpContent.qc },
            ],
            columnHelp: {
                bucket: helpContent.inventoryBucket,
            },
        },
        exceptions: {
            titleHelp: helpContent.exceptionCenter,
            subtitleParts: [
                { label: l("Verspätung", "Delays") },
                { label: l("QS-Blocker", "QC blockers"), help: helpContent.qc },
                { label: l("SLA-Verletzung", "SLA breach"), help: helpContent.sla },
            ],
        },
        "cycle-count": {
            titleHelp: helpContent.cycleCount,
            subtitleParts: [
                { label: l("Regelzählung", "Planned counts") },
                { label: l("Stichprobenzählung", "Spot count") },
                { label: l("4-Augen-Freigabe", "4-eyes approval"), help: helpContent.fourEyes },
            ],
            columnHelp: {
                sku: helpContent.sku,
                variance: helpContent.variance,
            },
        },
        governance: {
            titleHelp: helpContent.governance,
        },
    }), [helpContent, l]);

    const workspaceDefinitionsWithHelp = useMemo(() => workspaceDefinitions.map((workspace) => {
        const config = workspaceHelpConfig[workspace.id] ?? {};
        return {
            ...workspace,
            titleHelp: config.titleHelp,
            subtitleParts: config.subtitleParts,
            columns: workspace.columns.map((column) => ({
                ...column,
                help: config.columnHelp?.[column.key],
            })),
        };
    }), [workspaceDefinitions, workspaceHelpConfig]);

    const toneLabels = useMemo(() => ({
        safe: l("stabil", "stable"),
        warn: l("beobachten", "watch"),
        danger: l("kritisch", "critical"),
        neutral: l("leer", "empty"),
    }), [l]);

    const workspaceCards = useMemo(() => workspaceDefinitionsWithHelp.map((workspace) => ({
        ...workspace,
        icon: workspaceIconMap[workspace.id] ?? "SC",
        total: workspace.rows.length,
        attention: countAttentionItems(workspace.rows),
        tone: getWorkspaceTone(workspace.rows),
    })).map((workspace) => ({
        ...workspace,
        toneLabel: toneLabels[workspace.tone],
    })), [toneLabels, workspaceDefinitionsWithHelp]);

    const activeDefinition = workspaceCards.find((item) => item.id === activeWorkspace) ?? workspaceCards[0];
    const activeRole = roleDefinitions.find((item) => item.id === role) ?? roleDefinitions[0];
    const quickEntryEnabled = canManageSupplyChain && ["inbound", "inventory", "cycle-count"].includes(activeDefinition.id);
    const receivingAssistantEnabled = canManageSupplyChain && activeDefinition.id === "inbound";
    const totalAttention = useMemo(() => workspaceCards.reduce((sum, workspace) => sum + workspace.attention, 0), [workspaceCards]);

    const priorityWorkspaces = useMemo(() => {
        const toneRank = { danger: 3, warn: 2, safe: 1, neutral: 0 };
        const ranked = [...workspaceCards].sort((left, right) => (
            (toneRank[right.tone] - toneRank[left.tone])
            || (right.attention - left.attention)
            || (right.total - left.total)
        ));
        const actionable = ranked.filter((workspace) => workspace.attention > 0 || toneRank[workspace.tone] >= 2);
        const fallback = ranked.filter((workspace) => workspace.total > 0);
        const targets = (actionable.length ? actionable : fallback).slice(0, 4);

        return targets.map((workspace, index) => ({
            ...workspace,
            actionLabel: index === 0
                ? l("Als Fokus wählen", "Set as focus")
                : workspace.attention
                    ? l("Als Nächstes", "Next up")
                    : l("Bereit", "Ready"),
            actionText: workspace.attention
                ? l(`${workspace.attention} offene Punkte warten dort auf dich.`, `${workspace.attention} open items are waiting there.`)
                : workspace.total
                    ? l(`${workspace.total} Einträge sind dort bereit.`, `${workspace.total} records are ready there.`)
                    : l("Der Bereich ist aktuell leer.", "This area is currently empty."),
        }));
    }, [l, workspaceCards]);

    const nextWorkspace = useMemo(() => {
        const urgentWorkspace = priorityWorkspaces.find((workspace) => workspace.attention > 0);
        if (urgentWorkspace) return urgentWorkspace;

        const roleDefaultWorkspace = workspaceCards.find((workspace) => workspace.id === activeRole.defaultWorkspace);
        if (roleDefaultWorkspace?.total) return roleDefaultWorkspace;

        return priorityWorkspaces[0] ?? activeDefinition;
    }, [activeDefinition, activeRole.defaultWorkspace, priorityWorkspaces, workspaceCards]);

    const workspaceById = useMemo(() => new Map(workspaceCards.map((workspace) => [workspace.id, workspace])), [workspaceCards]);
    const sidebarGroups = useMemo(() => {
        const item = (id, labelOverride, descriptionOverride) => {
            const workspace = workspaceById.get(id);
            if (!workspace) return null;
            return {
                ...workspace,
                navLabel: labelOverride ?? workspace.title,
                navDescription: descriptionOverride ?? workspace.subtitle,
            };
        };

        return [
            {
                id: "daily",
                label: l("Tagesgeschäft", "Daily work"),
                items: [
                    item("overview", l("Übersicht", "Overview"), l("Heute, Aufgaben, Blocker", "Today, tasks, blockers")),
                    item("inbound"),
                    item("inventory"),
                    item("outbound"),
                    item("cycle-count"),
                    item("exceptions"),
                ].filter(Boolean),
            },
            {
                id: "control",
                label: l("Kontrolle", "Control"),
                items: [
                    item("governance"),
                    item("production"),
                    item("service"),
                ].filter(Boolean),
            },
            {
                id: "setup",
                label: l("Einrichtung", "Setup"),
                items: [
                    item("buckets", l("Stammdaten", "Master data"), l("Produkte, Lager, Zonen, Regeln", "Products, warehouses, zones, rules")),
                ].filter(Boolean),
            },
        ];
    }, [l, workspaceById]);

    const dashboardKpis = useMemo(() => {
        const openInbound = inboundRows.filter((row) => !doneStatuses.has(String(row.status))).length;
        const openOutbound = outboundRows.filter((row) => !doneStatuses.has(String(row.status))).length;
        const stockIssues = inventoryRows.filter((row) => ["BLOCKED", "IN_QC", "DAMAGED"].includes(String(row.status)) || Number(row.quantity ?? 0) <= 0).length;
        const dueCounts = cycleCountRows.filter((row) => !doneStatuses.has(String(row.status))).length;

        return [
            {
                key: "tasks",
                label: l("Offene Aufgaben", "Open tasks"),
                value: overviewRowsRaw.length,
                note: l("Priorisierte Vorgänge über alle Bereiche", "Prioritized work across all areas"),
                tone: totalAttention ? "warn" : "safe",
                target: "overview",
            },
            {
                key: "inbound",
                label: l("Wareneingänge", "Inbound"),
                value: openInbound,
                note: l("Erwartet, angekommen oder in Prüfung", "Expected, arrived, or in review"),
                tone: openInbound ? "info" : "neutral",
                target: "inbound",
            },
            {
                key: "exceptions",
                label: l("Ausnahmen", "Exceptions"),
                value: exceptionRows.length,
                note: l("Blocker und QS-Themen zuerst", "Blockers and QC topics first"),
                tone: exceptionRows.length ? "danger" : "safe",
                target: "exceptions",
            },
            {
                key: "stock",
                label: l("Bestand", "Inventory"),
                value: stockIssues,
                note: l("Gesperrt, QS oder ohne Bestand", "Blocked, QC, or no stock"),
                tone: stockIssues ? "warn" : "safe",
                target: "inventory",
            },
            {
                key: "counts",
                label: l("Zählungen fällig", "Counts due"),
                value: dueCounts,
                note: l("Inventur und Differenzen", "Inventory counts and variances"),
                tone: dueCounts ? "info" : "neutral",
                target: "cycle-count",
            },
        ];
    }, [cycleCountRows, exceptionRows.length, inboundRows, inventoryRows, l, outboundRows, overviewRowsRaw.length, totalAttention]);

    const todaySignals = useMemo(() => {
        const openInbound = inboundRows.filter((row) => !doneStatuses.has(String(row.status))).length;
        const openOutbound = outboundRows.filter((row) => !doneStatuses.has(String(row.status))).length;
        const dueCounts = cycleCountRows.filter((row) => !doneStatuses.has(String(row.status))).length;
        const stockIssues = inventoryRows.filter((row) => ["BLOCKED", "IN_QC", "DAMAGED"].includes(String(row.status)) || Number(row.quantity ?? 0) <= 0).length;

        return [
            l(`${openInbound} Wareneingänge erwartet oder offen`, `${openInbound} inbound items expected or open`),
            l(`${openOutbound} Warenausgänge zu bearbeiten`, `${openOutbound} outbound items to process`),
            l(`${exceptionRows.length} Ausnahmen offen`, `${exceptionRows.length} exceptions open`),
            l(`${stockIssues} Bestände brauchen Prüfung`, `${stockIssues} stock positions need review`),
            l(`${dueCounts} Zählungen fällig`, `${dueCounts} counts due`),
        ];
    }, [cycleCountRows, exceptionRows.length, inboundRows, inventoryRows, l, outboundRows]);

    const topQueueRows = useMemo(() => overviewRowsRaw.slice(0, 4), [overviewRowsRaw]);
    const companyLabel = currentUser?.companyName ?? currentUser?.company?.name ?? l("Chrono Demo AG", "Chrono Demo Ltd.");
    const selectedWarehouse = warehouses[0];
    const currentSiteLabel = selectedWarehouse?.siteName ? translateContext(selectedWarehouse.siteName) : l("Alle Standorte", "All sites");
    const currentWarehouseLabel = selectedWarehouse?.name ?? l("Hauptlager", "Main warehouse");
    const nextStepText = totalAttention
        ? l(`${nextWorkspace.title}: ${nextWorkspace.attention || nextWorkspace.total} offene Punkte zuerst bearbeiten.`, `${nextWorkspace.title}: handle ${nextWorkspace.attention || nextWorkspace.total} open items first.`)
        : products.length && warehouses.length
            ? l("Setup ist bereit. Starte den ersten Wareneingang oder prüfe Lagerplätze.", "Setup is ready. Start the first inbound workflow or review bins.")
            : l("Lege zuerst Produkte, Lager und Lagerplätze an.", "Create products, warehouses, and bins first.");

    const submitQuickEntry = async (payload) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return false;
        }
        const rawQuantity = Number(payload.quantityChange);
        if (!payload.productId || !payload.warehouseId || !Number.isFinite(rawQuantity) || rawQuantity === 0) {
            notify(l("Bitte Produkt, Lager und Menge ausfüllen.", "Please fill product, warehouse and quantity."), "warning");
            return false;
        }
        try {
            const type = String(payload.type ?? "RECEIPT").toUpperCase();
            const quantityChange = Math.abs(rawQuantity) * (negativeStockMovementTypes.has(type) ? -1 : 1);
            await api.post("/api/supply-chain/stock/adjust", {
                ...payload,
                type,
                quantityChange,
            });
            notify(l("Bestandsbuchung erfolgreich erfasst.", "Stock entry saved successfully."), "success");
            await loadData();
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Bestandsbuchung konnte nicht gespeichert werden.", "Could not save stock entry."), "error");
            return false;
        }
    };

    const submitCreateCycleCountPlan = async (payload) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return false;
        }
        if (!payload.productId || !payload.warehouseId) {
            notify(l("Bitte Produkt und Lager für die Zählung wählen.", "Please choose a product and warehouse for the count."), "warning");
            return false;
        }
        try {
            await api.post("/api/supply-chain/cycle-counts", payload);
            notify(l("Zählplan erfolgreich angelegt.", "Cycle count plan created successfully."), "success");
            await loadData();
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Zählplan konnte nicht angelegt werden.", "Could not create cycle count plan."), "error");
            return false;
        }
    };

    const submitCycleCountResult = useCallback(async (cycleCountId, countedQuantity) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return null;
        }
        const quantity = Number(countedQuantity);
        if (!Number.isFinite(quantity) || quantity < 0) {
            notify(l("Bitte eine gültige gezählte Menge eingeben.", "Please enter a valid counted quantity."), "warning");
            return null;
        }
        try {
            const response = await api.post(`/api/supply-chain/cycle-counts/${cycleCountId}/submit`, {
                countedQuantity: quantity,
            });
            await loadData();
            notify(l("Zählergebnis gespeichert.", "Count result saved."), "success");
            return mapCycleCountToRow(response.data);
        } catch (error) {
            console.error(error);
            notify(l("Zählergebnis konnte nicht gespeichert werden.", "Could not save count result."), "error");
            return null;
        }
    }, [canManageSupplyChain, l, loadData, mapCycleCountToRow, notify, notifyViewOnlySupplyChain]);

    const approveCycleCountResult = useCallback(async (cycleCountId) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return null;
        }
        try {
            const response = await api.post(`/api/supply-chain/cycle-counts/${cycleCountId}/approve`);
            await loadData();
            notify(l("Zykluszählung freigegeben und Bestand angepasst.", "Cycle count approved and stock adjusted."), "success");
            return mapCycleCountToRow(response.data);
        } catch (error) {
            console.error(error);
            notify(l("Zykluszählung konnte nicht freigegeben werden.", "Could not approve cycle count."), "error");
            return null;
        }
    }, [canManageSupplyChain, l, loadData, mapCycleCountToRow, notify, notifyViewOnlySupplyChain]);

    const renderDrawerActions = useCallback(({ record, setRecord }) => {
        if (!canManageSupplyChain || activeDefinition?.id !== "cycle-count" || !record) {
            return null;
        }

        return (
            <CycleCountDrawerActions
                record={record}
                text={{
                    countTitle: l("Zählergebnis erfassen", "Submit count result"),
                    countText: l("Gezählten Bestand erfassen und bei Differenzen zur Freigabe weitergeben.", "Enter the counted stock and route differences to approval."),
                    countedLabel: l("Gezählte Menge", "Counted quantity"),
                    submitLabel: l("Ergebnis speichern", "Save result"),
                    submittingLabel: l("Speichert…", "Saving…"),
                    approvalTitle: l("Freigabe ausstehend", "Approval pending"),
                    approvalText: l("Die Differenz ist erkannt. Nach der Freigabe wird der Systembestand angepasst.", "The variance was detected. After approval, the system stock will be adjusted."),
                    expectedLabel: l("Systembestand", "System stock"),
                    varianceLabel: l("Differenz", "Variance"),
                    approveLabel: l("Freigeben", "Approve"),
                    approvingLabel: l("Gibt frei…", "Approving…"),
                    completedTitle: l("Zählung abgeschlossen", "Count completed"),
                    completedText: l("Für diesen Datensatz ist keine weitere Aktion nötig.", "No further action is needed for this record."),
                }}
                onSubmitCount={async (countedQuantity) => {
                    const updated = await submitCycleCountResult(record.id, countedQuantity);
                    if (updated) setRecord(updated);
                }}
                onApprove={async () => {
                    const updated = await approveCycleCountResult(record.id);
                    if (updated) setRecord(updated);
                }}
            />
        );
    }, [activeDefinition?.id, approveCycleCountResult, canManageSupplyChain, l, submitCycleCountResult]);

    const submitCreateProduct = async (payload) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return false;
        }
        if (!payload.sku || !payload.name) {
            notify(l("Bitte SKU und Produktname ausfüllen.", "Please fill SKU and product name."), "warning");
            return false;
        }
        try {
            await api.post("/api/supply-chain/products", {
                sku: payload.sku,
                name: payload.name,
                unitOfMeasure: "pcs",
                active: true,
            });
            notify(l("Produkt erfolgreich angelegt.", "Product created successfully."), "success");
            await loadData();
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Produkt konnte nicht angelegt werden.", "Could not create product."), "error");
            return false;
        }
    };

    const submitCreateWarehouse = async (payload) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            return false;
        }
        if (!payload.code || !payload.name) {
            notify(l("Bitte Lagercode und Lagername ausfüllen.", "Please fill warehouse code and name."), "warning");
            return false;
        }
        try {
            await api.post("/api/supply-chain/warehouses", {
                code: payload.code,
                name: payload.name,
                location: payload.location || payload.name,
            });
            notify(l("Lager erfolgreich angelegt.", "Warehouse created successfully."), "success");
            await loadData();
            return true;
        } catch (error) {
            console.error(error);
            notify(l("Lager konnte nicht angelegt werden.", "Could not create warehouse."), "error");
            return false;
        }
    };

    const submitReceivingPreview = async (payload) => {
        try {
            const response = await api.post("/api/supply-chain/receiving/preview", payload);
            return response.data;
        } catch (error) {
            console.error(error);
            notify(l("Scan oder Dokument konnte nicht analysiert werden.", "Could not analyze scan or document."), "error");
            throw error;
        }
    };

    const submitReceivingDocumentPreview = async (file) => {
        try {
            const formData = new FormData();
            formData.append("file", file);
            const response = await api.post("/api/supply-chain/receiving/document-preview", formData, {
                headers: { "Content-Type": "multipart/form-data" },
            });
            return response.data;
        } catch (error) {
            console.error(error);
            notify(l("Dokument konnte nicht gelesen werden.", "Document could not be read."), "error");
            throw error;
        }
    };

    const submitReceivingApply = async (payload) => {
        if (!canManageSupplyChain) {
            notifyViewOnlySupplyChain();
            throw new Error("supply_chain_view_only");
        }
        try {
            const response = await api.post("/api/supply-chain/receiving/apply", payload);
            notify(l("Wareneingang erfolgreich übernommen.", "Goods receipt posted successfully."), "success");
            await loadData();
            return response.data;
        } catch (error) {
            console.error(error);
            notify(l("Wareneingang konnte nicht übernommen werden.", "Could not post goods receipt."), "error");
            throw error;
        }
    };

    return (
        <div className="admin-page supply-chain-page">
            <Navbar />
            <main className="admin-content">
                <header className="card sc-control-header">
                    <div className="sc-control-header-title">
                        <span className="sc-eyebrow">{l("Warehouse Control Center", "Warehouse Control Center")}</span>
                        <h1>{l("Lager & Supply Chain", "Warehouse & Supply Chain")}</h1>
                        <div className="sc-context-line">
                            <span>{l("Firma", "Company")}: {companyLabel}</span>
                            <span>{l("Standort", "Site")}: {currentSiteLabel}</span>
                            <span>{l("Lager", "Warehouse")}: {currentWarehouseLabel}</span>
                            <span>{l("Rolle", "Role")}: {activeRole.label}</span>
                            <span>{l("Aktiver Bereich", "Active area")}: {activeDefinition.title}</span>
                        </div>
                    </div>
                    <div className="sc-header-tools">
                        <label className="sc-global-search">
                            <span>{l("Globale Suche", "Global search")}</span>
                            <input
                                type="search"
                                value={globalSearch}
                                onFocus={() => activateWorkspace("overview")}
                                onChange={(event) => {
                                    setGlobalSearch(event.target.value);
                                    setActiveWorkspace("overview");
                                }}
                                placeholder={l("ASN, Artikel, Charge, Lagerplatz, Lieferant...", "ASN, item, lot, bin, supplier...")}
                            />
                        </label>
                        <div className="sc-header-actions">
                            <button type="button" className="sc-primary-action" onClick={() => activateWorkspace("inbound", { scroll: true })}>
                                {l("+ Neue Buchung", "+ New entry")}
                            </button>
                            <button type="button" onClick={() => activateWorkspace("inbound", { scroll: true })}>
                                {l("Scannen", "Scan")}
                            </button>
                            <button type="button" className="secondary" onClick={() => activateWorkspace("buckets", { scroll: true })}>
                                {l("Mehr", "More")}
                            </button>
                        </div>
                    </div>
                    {!canManageSupplyChain && (
                        <p className="sc-readonly-banner">
                            {l(
                                "Nur Ansicht: Dieser Zugang darf Supply Chain sehen, aber keine Buchungen, Freigaben oder Stammdatenänderungen ausführen.",
                                "View only: this account can see Supply Chain, but cannot post entries, approve counts, or change master data."
                            )}
                        </p>
                    )}
                </header>

                <section className="sc-enterprise-shell">
                    <aside className="card sc-enterprise-nav" aria-label={l("Supply Chain Navigation", "Supply Chain navigation")}>
                        <div className="sc-nav-context">
                            <span className={`sc-health-dot ${totalAttention ? "warn" : "safe"}`} />
                            <div>
                                <strong>{totalAttention ? l("Handlungsbedarf", "Attention needed") : l("Stabil", "Stable")}</strong>
                                <span>{totalAttention ? `${totalAttention} ${l("offene Punkte", "open items")}` : l("Keine akuten Blocker", "No urgent blockers")}</span>
                            </div>
                        </div>
                        <div className="sc-role-switch">
                            <label htmlFor="sc-role">{l("Rolle", "Role")}</label>
                            <select id="sc-role" value={role} onChange={(event) => activateRole(event.target.value)}>
                                {roles.map((roleOption) => (
                                    <option key={roleOption} value={roleOption}>
                                        {roleOption === "warehouse" && l("Lager", "warehouse")}
                                        {roleOption === "planner" && l("Planung", "planner")}
                                        {roleOption === "quality" && l("Qualität", "quality")}
                                        {roleOption === "service" && l("Service", "service")}
                                        {roleOption === "admin" && l("Admin", "admin")}
                                    </option>
                                ))}
                            </select>
                        </div>
                        {sidebarGroups.map((group) => (
                            <nav key={group.id} className="sc-nav-group" aria-label={group.label}>
                                <p>{group.label}</p>
                                {group.items.map((workspace) => (
                                    <button
                                        key={workspace.id}
                                        type="button"
                                        className={`sc-nav-item tone-${workspace.tone} ${workspace.id === activeWorkspace ? "active" : ""}`}
                                        onClick={() => activateWorkspace(workspace.id, { scroll: true })}
                                    >
                                        <span className="sc-nav-item-main">
                                            <strong>{workspace.navLabel}</strong>
                                            <span>{workspace.navDescription}</span>
                                        </span>
                                        <span className="sc-nav-count">
                                            {workspace.attention || workspace.total || 0}
                                        </span>
                                    </button>
                                ))}
                            </nav>
                        ))}
                    </aside>

                    <div ref={workspaceSectionRef} className="sc-enterprise-main sc-workspace-anchor">
                        {activeDefinition.id === "overview" && !loading && (
                            <section className="sc-overview-board" aria-label={l("Supply Chain Übersicht", "Supply Chain overview")}>
                                <div className="sc-overview-kpis">
                                    {dashboardKpis.map((card) => (
                                        <button
                                            key={card.key}
                                            type="button"
                                            className={`sc-overview-card tone-${card.tone}`}
                                            onClick={() => activateWorkspace(card.target, { scroll: true })}
                                        >
                                            <span>{card.label}</span>
                                            <strong>{card.value}</strong>
                                            <p>{card.note}</p>
                                        </button>
                                    ))}
                                </div>
                                <div className="sc-overview-lower">
                                    <section className="sc-next-step-panel">
                                        <p className="sc-panel-kicker">{l("Nächster Schritt", "Next step")}</p>
                                        <h2>{nextStepText}</h2>
                                        <div className="sc-next-step-actions">
                                            <button type="button" className="sc-primary-action" onClick={() => activateWorkspace(nextWorkspace.id, { scroll: true })}>
                                                {l("Arbeitsliste öffnen", "Open work list")}
                                            </button>
                                            <button type="button" className="secondary" onClick={() => activateWorkspace("exceptions", { scroll: true })}>
                                                {l("Blocker prüfen", "Review blockers")}
                                            </button>
                                        </div>
                                    </section>
                                    <section className="sc-today-panel">
                                        <p className="sc-panel-kicker">{l("Heute im Lager", "Today in warehouse")}</p>
                                        <ul>
                                            {todaySignals.map((signal) => (
                                                <li key={signal}>{signal}</li>
                                            ))}
                                        </ul>
                                    </section>
                                    <section className="sc-queue-panel">
                                        <p className="sc-panel-kicker">{l("Top-Prioritäten", "Top priorities")}</p>
                                        <div className="sc-mini-queue">
                                            {topQueueRows.map((row) => (
                                                <button key={row.id} type="button" onClick={() => activateWorkspace("overview", { scroll: true })}>
                                                    <strong>{row.priority}</strong>
                                                    <span>{row.type} · {row.ref}</span>
                                                    <em>{row.owner}</em>
                                                </button>
                                            ))}
                                        </div>
                                    </section>
                                </div>
                            </section>
                        )}

                        {activeDefinition.id !== "overview" && !loading && (
                            <section className="sc-process-context">
                                <div>
                                    <p className="sc-panel-kicker">{l("Arbeitsbereich", "Workspace")}</p>
                                    <h2>{activeDefinition.title}</h2>
                                    <p>{activeDefinition.subtitle}</p>
                                </div>
                                <div className="sc-process-context-stats">
                                    <span>{activeDefinition.total} {l("Vorgänge", "records")}</span>
                                    <span>{activeDefinition.attention ? `${activeDefinition.attention} ${l("offen", "open")}` : l("Keine offenen Blocker", "No open blockers")}</span>
                                </div>
                            </section>
                        )}

                    {loading ? <p className="card">{l("Lädt…", "Loading…")}</p> : (
                        <ProcessWorkspace
                            key={activeDefinition.id}
                            id={activeDefinition.id}
                            title={activeDefinition.title}
                            titleHelp={activeDefinition.titleHelp}
                            subtitle={activeDefinition.subtitle}
                            subtitleParts={activeDefinition.subtitleParts}
                            rows={activeDefinition.rows}
                            columns={activeDefinition.columns}
                            timeline={localizedMovementTimeline}
                            text={text}
                            workspaceMeta={{
                                total: activeDefinition.total,
                                attention: activeDefinition.attention,
                                tone: activeDefinition.tone,
                                nextAction: activeDefinition.attention
                                    ? l("Offene Vorgänge zuerst prüfen und priorisieren.", "Review and prioritize open records first.")
                                    : l("Bereich ist bereit für neue Buchung, Prüfung oder Suche.", "Area is ready for new posting, review, or search."),
                            }}
                            quickEntry={{
                                enabled: quickEntryEnabled,
                                kind: activeDefinition.id === "cycle-count" ? "cycle-count" : "stock-entry",
                                openLabel: activeDefinition.id === "cycle-count"
                                    ? l("+ Zählplan anlegen", "+ Create count plan")
                                    : activeDefinition.id === "inbound"
                                        ? l("+ Wareneingang", "+ Inbound receipt")
                                        : l("+ Bestand buchen", "+ Post stock entry"),
                                closeLabel: l("Schließen", "Close"),
                                title: activeDefinition.id === "cycle-count"
                                    ? l("Zählplan anlegen", "Create count plan")
                                    : activeDefinition.id === "inbound"
                                        ? l("Wareneingang buchen", "Post inbound receipt")
                                        : l("Schnellbuchung Bestand", "Quick stock entry"),
                                subtitle: activeDefinition.id === "cycle-count"
                                    ? l("Produkt und Lager auswählen, damit ein echter Zählauftrag entsteht.", "Choose a product and warehouse to create a real count task.")
                                    : activeDefinition.id === "inbound"
                                        ? l("Produkt, Ziellager und Beleg erfassen, danach wird der Bestand gebucht.", "Enter product, target warehouse, and reference, then post stock.")
                                        : l("Für kleine Teams: Produkt, Lager und Buchung direkt erfassen.", "For small teams: post product, warehouse, and movement directly."),
                                submitLabel: activeDefinition.id === "cycle-count" ? l("Zählplan speichern", "Save count plan") : l("Buchen", "Post entry"),
                                submittingLabel: activeDefinition.id === "cycle-count" ? l("Wird angelegt…", "Creating…") : l("Wird gebucht…", "Posting…"),
                                products,
                                warehouses,
                                labels: {
                                    product: l("Produkt", "Product"),
                                    warehouse: l("Lager", "Warehouse"),
                                    quantity: l("Menge", "Quantity"),
                                    type: l("Buchungstyp", "Entry type"),
                                    reference: l("Referenz", "Reference"),
                                },
                                help: {
                                    type: helpContent.entryType,
                                    reference: helpContent.reference,
                                },
                                placeholders: {
                                    product: l("Bitte Produkt wählen", "Select product"),
                                    warehouse: l("Bitte Lager wählen", "Select warehouse"),
                                    reference: l("z. B. Buchungsgrund oder Beleg", "e.g. booking reason or reference"),
                                },
                                types: [
                                    { value: "RECEIPT", label: l("Wareneingang", "Goods receipt") },
                                    { value: "ISSUE", label: l("Entnahme", "Issue") },
                                    { value: "WRITE_OFF", label: l("Abschreibung", "Write-off") },
                                    { value: "GAIN", label: l("Bestandsgewinn", "Stock gain") },
                                ],
                                createProduct: {
                                    enabled: activeDefinition.id !== "cycle-count",
                                    openLabel: l("+ Produkt anlegen", "+ Add product"),
                                    closeLabel: l("Produkt-Form schließen", "Close product form"),
                                    title: l("Neues Produkt", "New product"),
                                    titleHelp: helpContent.sku,
                                    submitLabel: l("Produkt speichern", "Save product"),
                                    submittingLabel: l("Speichert…", "Saving…"),
                                    labels: {
                                        sku: "SKU",
                                        name: l("Produktname", "Product name"),
                                    },
                                    help: {
                                        sku: helpContent.sku,
                                    },
                                    placeholders: {
                                        sku: l("z. B. SCHRAUBE-M8", "e.g. SCREW-M8"),
                                        name: l("z. B. Schraube M8 x 20", "e.g. Screw M8 x 20"),
                                    },
                                    onSubmit: submitCreateProduct,
                                },
                                createWarehouse: {
                                    enabled: activeDefinition.id !== "cycle-count",
                                    openLabel: l("+ Lager anlegen", "+ Add warehouse"),
                                    closeLabel: l("Lager-Form schließen", "Close warehouse form"),
                                    title: l("Neues Lager", "New warehouse"),
                                    titleHelp: helpContent.warehouseCode,
                                    submitLabel: l("Lager speichern", "Save warehouse"),
                                    submittingLabel: l("Speichert…", "Saving…"),
                                    labels: {
                                        code: l("Lagercode", "Warehouse code"),
                                        name: l("Lagername", "Warehouse name"),
                                        location: l("Ort", "Location"),
                                    },
                                    help: {
                                        code: helpContent.warehouseCode,
                                    },
                                    placeholders: {
                                        code: l("z. B. BER-01", "e.g. BER-01"),
                                        name: l("z. B. Hauptlager Berlin", "e.g. Berlin main warehouse"),
                                        location: l("z. B. Berlin", "e.g. Berlin"),
                                    },
                                    onSubmit: submitCreateWarehouse,
                                },
                                onSubmit: activeDefinition.id === "cycle-count" ? submitCreateCycleCountPlan : submitQuickEntry,
                            }}
                            renderDrawerActions={renderDrawerActions}
                            receivingAssistant={{
                                enabled: receivingAssistantEnabled,
                                openLabel: l("Scannen", "Scan"),
                                closeLabel: l("Scan schließen", "Close scan"),
                                warehouses,
                                onPreview: submitReceivingPreview,
                                onPreviewDocument: submitReceivingDocumentPreview,
                                onApply: submitReceivingApply,
                                text: {
                                    title: l("Wareneingang per Scan oder Dokument", "Receive by scan or document"),
                                    subtitle: l(
                                        "Scanner-Gerät, Kamera oder Lieferscheinbild nutzen und danach sicher prüfen, bevor gebucht wird.",
                                        "Use a scanner device, camera, or delivery-note document and review the result before posting."
                                    ),
                                    labels: {
                                        warehouse: l("Ziellager", "Target warehouse"),
                                        reference: l("Referenz", "Reference"),
                                        mode: l("Erkannt", "Detected"),
                                        purchaseOrder: l("Bestellung", "Purchase order"),
                                        vendor: l("Lieferant", "Vendor"),
                                        documentDate: l("Dokumentdatum", "Document date"),
                                        codes: l("Codes", "Codes"),
                                    },
                                    placeholders: {
                                        warehouse: l("Bitte Lager wählen", "Select warehouse"),
                                        reference: l("z. B. Lieferschein, ASN oder Bestellnummer", "e.g. delivery note, ASN, or purchase order"),
                                    },
                                    actions: {
                                        scan: l("Scan auswerten", "Analyze scan"),
                                        startCamera: l("Kamera starten", "Start camera"),
                                        stopCamera: l("Kamera stoppen", "Stop camera"),
                                        apply: l("Wareneingang übernehmen", "Post goods receipt"),
                                        reset: l("Zurücksetzen", "Reset"),
                                        working: l("Wird verarbeitet…", "Working…"),
                                    },
                                    device: {
                                        title: l("Scanner / Barcode", "Scanner / barcode"),
                                        subtitle: l(
                                            "USB-Handscanner, Funkscanner oder Kamera können Bestellnummer, ASN oder Lieferschein-Code lesen.",
                                            "USB scanner, wireless scanner, or camera can read a purchase order number, ASN, or delivery-note code."
                                        ),
                                        inputLabel: l("Scanwert", "Scan value"),
                                        placeholder: l("Code scannen oder hier einfügen", "Scan or paste a code here"),
                                        barcodeReady: l("Barcode bereit", "Barcode ready"),
                                        barcodeMissing: l("Kein Barcode-Scan", "No barcode scan"),
                                        cameraHint: l("Kamera sucht laufend nach Barcode oder QR-Code.", "Camera continuously looks for a barcode or QR code."),
                                        cameraUnsupported: l("Kamera-Scan ist auf diesem Gerät oder Browser nicht verfügbar.", "Camera scanning is not available on this device or browser."),
                                        cameraError: l("Kamera konnte nicht gestartet oder gelesen werden.", "Camera could not be started or read."),
                                    },
                                document: {
                                    title: l("Dokument hochladen", "Upload document"),
                                    subtitle: l(
                                        "Bild, Scan oder PDF analysieren. Bilder nutzen Barcode und OCR des Geräts, PDFs den enthaltenen Text.",
                                        "Analyze an image, scan, or PDF. Images use the device barcode/OCR features, PDFs use embedded document text."
                                    ),
                                    choose: l("Lieferschein auswählen", "Choose delivery note"),
                                    hint: l(
                                        "Bilder und Fotos werden direkt im Browser geprüft. PDFs werden als Dokumenttext gelesen, wenn Text enthalten ist.",
                                        "Images and photos are analyzed in the browser. PDFs are read from embedded document text when available."
                                    ),
                                    processing: l("Dokument wird analysiert…", "Analyzing document…"),
                                    success: l("Dokument analysiert.", "Document analyzed."),
                                    noSignal: l("Kein lesbarer Code oder Text gefunden. Bitte Dokument prüfen oder manuell nacharbeiten.", "No readable code or text found. Please review the document or adjust manually."),
                                    imageFallback: l("OCR war auf diesem Gerät nicht verfügbar. Es wurde nur nach Barcodes gesucht.", "OCR was not available on this device. Only barcodes were searched."),
                                    error: l("Dokument konnte nicht analysiert werden.", "Document could not be analyzed."),
                                    ocrReady: l("OCR bereit", "OCR ready"),
                                    ocrMissing: l("OCR begrenzt", "OCR limited"),
                                },
                                preview: {
                                    title: l("Erkannter Vorschlag", "Detected proposal"),
                                    empty: l("Noch keine Analyse vorhanden.", "No analysis yet."),
                                    modeLabels: {
                                        PURCHASE_ORDER: l("Passende Bestellung", "Matched purchase order"),
                                        DIRECT_RECEIPT: l("Direkte Lagerbuchung", "Direct stock receipt"),
                                        NONE: l("Kein sicherer Treffer", "No reliable match"),
                                    },
                                    modeDescriptions: {
                                        PURCHASE_ORDER: l(
                                            "Es wurde eine offene Bestellung erkannt. Lager prüfen und Wareneingang übernehmen.",
                                            "An open purchase order was detected. Review the warehouse and post the receipt."
                                        ),
                                        DIRECT_RECEIPT: l(
                                            "Artikel wurden aus Scan oder Dokument erkannt. Mengen prüfen und dann buchen.",
                                            "Items were detected from the scan or document. Review quantities and then post."
                                        ),
                                        NONE: l(
                                            "Noch kein sicherer Treffer. Noch einmal scannen oder ein klareres Dokument hochladen.",
                                            "No reliable match yet. Scan again or upload a clearer document."
                                        ),
                                    },
                                    messageTranslations: {
                                        "Open purchase order matched and ready for receiving review.": l(
                                            "Eine offene Bestellung wurde erkannt und ist zur Prüfung bereit.",
                                            "Open purchase order matched and ready for receiving review."
                                        ),
                                        "Products detected from scan or document. Review quantities before posting.": l(
                                            "Artikel wurden aus Scan oder Dokument erkannt. Bitte Mengen vor dem Buchen prüfen.",
                                            "Products detected from scan or document. Review quantities before posting."
                                        ),
                                        "No reliable match found yet. Scan an order number, ASN, or upload a clearer delivery note.": l(
                                            "Noch kein sicherer Treffer. Bitte Bestellnummer, ASN oder einen klareren Lieferschein scannen bzw. hochladen.",
                                            "No reliable match found yet. Scan an order number, ASN, or upload a clearer delivery note."
                                        ),
                                    },
                                    warningTranslations: {
                                        "This booking is posted as direct goods receipt and does not close a purchase order automatically.": l(
                                            "Diese Buchung wird als direkter Wareneingang erfasst und schließt keine Bestellung automatisch ab.",
                                            "This booking is posted as direct goods receipt and does not close a purchase order automatically."
                                        ),
                                        "No purchase order or SKU could be matched from the current scan/document.": l(
                                            "Aus dem aktuellen Scan oder Dokument konnte keine Bestellung und keine SKU sicher zugeordnet werden.",
                                            "No purchase order or SKU could be matched from the current scan/document."
                                        ),
                                        "No readable document text was available. Matching is based on detected codes and file name only.": l(
                                            "Es war kein lesbarer Dokumenttext verfügbar. Die Zuordnung basiert nur auf erkannten Codes und dem Dateinamen.",
                                            "No readable document text was available. Matching is based on detected codes and file name only."
                                        ),
                                    },
                                    completePurchaseOrder: l("Bestellung als vollständig empfangen markieren", "Mark purchase order as fully received"),
                                    completePurchaseOrderHint: l(
                                        "Aktiv: Bestellung wird abgeschlossen. Aus: Es wird nur der Wareneingang gebucht und die Bestellung bleibt offen.",
                                        "On: the purchase order will be completed. Off: only stock receipt is posted and the order stays open."
                                    ),
                                    warnings: l("Hinweise", "Notes"),
                                    extractedText: l("Gelesener Dokumenttext", "Read document text"),
                                },
                                columns: {
                                    sku: "SKU",
                                    name: l("Produkt", "Product"),
                                    quantity: l("Menge", "Quantity"),
                                    unit: l("Einheit", "Unit"),
                                },
                            },
                        }}
                    />
                )}
                    </div>
                </section>
            </main>
        </div>
    );
};

export default SupplyChainDashboard;

