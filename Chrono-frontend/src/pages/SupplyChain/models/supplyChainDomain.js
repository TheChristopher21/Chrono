export const PROCESS_STATUS = {
    OPEN: "OPEN",
    IN_PROGRESS: "IN_PROGRESS",
    BLOCKED: "BLOCKED",
    COMPLETED: "COMPLETED",
    CLOSED: "CLOSED",
};

export const INVENTORY_BUCKETS = [
    "AVAILABLE",
    "RESERVED",
    "BLOCKED",
    "IN_QC",
    "IN_TRANSIT",
    "DAMAGED",
    "RETURNED",
    "EXPIRING",
];

export const movementFromRecord = ({ sourceType, sourceId, action, user, timestamp, reference, details }) => ({
    id: `${sourceType}-${sourceId}-${timestamp}-${action}`,
    sourceType,
    sourceId,
    action,
    user: user ?? "system",
    timestamp,
    reference: reference ?? "-",
    details: details ?? {},
});

export const auditFromMovement = (movement, reason = "PROCESS_UPDATE", approvedBy = "-") => ({
    id: `AUD-${movement.id}`,
    entityType: movement.sourceType,
    entityId: movement.sourceId,
    action: movement.action,
    changedBy: movement.user,
    changedAt: movement.timestamp,
    before: movement.details?.before ?? null,
    after: movement.details?.after ?? movement.details ?? null,
    reason,
    approvedBy,
});

const toNumber = (value, fallback = 0) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

export const normalizeProduct = (product = {}) => ({
    id: product.id,
    sku: product.sku ?? "-",
    name: product.name ?? "-",
    description: product.description ?? "",
    unitCost: toNumber(product.unitCost),
    unitPrice: toNumber(product.unitPrice),
});

export const normalizeWarehouse = (warehouse = {}) => ({
    id: warehouse.id,
    siteCode: warehouse.siteCode ?? warehouse.locationCode ?? "SITE-1",
    siteName: warehouse.siteName ?? warehouse.location ?? "Main Site",
    code: warehouse.code ?? "-",
    name: warehouse.name ?? "-",
    location: warehouse.location ?? "-",
    zone: warehouse.zone ?? "GENERAL",
    aisle: warehouse.aisle ?? "A1",
    rack: warehouse.rack ?? "R1",
    bin: warehouse.bin ?? "B1",
    pickface: warehouse.pickface ?? "PICK-01",
    reserveLocation: warehouse.reserveLocation ?? "RES-01",
});

export const normalizeStock = (entry = {}) => ({
    id: entry.id,
    productId: entry.productId ?? entry.product?.id,
    warehouseId: entry.warehouseId ?? entry.warehouse?.id,
    quantity: toNumber(entry.quantity),
    lotNumber: entry.lotNumber ?? "-",
    expirationDate: entry.expirationDate ?? null,
    status: entry.status ?? PROCESS_STATUS.OPEN,
    buckets: {
        AVAILABLE: toNumber(entry.availableQty ?? entry.quantity),
        RESERVED: toNumber(entry.reservedQty),
        BLOCKED: toNumber(entry.blockedQty),
        IN_QC: toNumber(entry.inQcQty),
        IN_TRANSIT: toNumber(entry.inTransitQty),
        DAMAGED: toNumber(entry.damagedQty),
        RETURNED: toNumber(entry.returnedQty),
        EXPIRING: toNumber(entry.expiringQty),
    },
    history: Array.isArray(entry.history) ? entry.history : [],
});

export const normalizeOrder = (order = {}) => ({
    id: order.id,
    orderNumber: order.orderNumber ?? String(order.id ?? "-"),
    status: order.status ?? PROCESS_STATUS.OPEN,
    customerName: order.customerName ?? "-",
    vendorName: order.vendorName ?? "-",
    dueDate: order.dueDate ?? order.expectedDate ?? null,
    priority: order.priority ?? "NORMAL",
    lines: Array.isArray(order.lines) ? order.lines : [],
});

export const normalizeDelivery = (delivery = {}) => ({
    id: delivery.id,
    orderNumber: delivery.orderNumber ?? "-",
    warehouseId: delivery.warehouseId,
    status: delivery.status ?? PROCESS_STATUS.OPEN,
    eta: delivery.eta ?? delivery.expectedDate ?? null,
    supplier: delivery.supplier ?? delivery.vendorName ?? "-",
    asn: delivery.asn ?? `ASN-${delivery.id ?? "NEW"}`,
    dock: delivery.dock ?? "D-01",
    lines: Array.isArray(delivery.lines) ? delivery.lines : [],
    quantity: Array.isArray(delivery.lines)
        ? delivery.lines.reduce((sum, line) => sum + toNumber(line?.quantity), 0)
        : toNumber(delivery.quantity),
});

export const normalizeServiceCase = (serviceCase = {}) => ({
    id: serviceCase.id,
    customerName: serviceCase.customerName ?? "-",
    subject: serviceCase.subject ?? "-",
    status: serviceCase.status ?? PROCESS_STATUS.OPEN,
    owner: serviceCase.owner ?? "-",
    updatedAt: serviceCase.updatedAt ?? serviceCase.createdAt ?? null,
    slaBreached: Boolean(serviceCase.slaBreached),
});

export const createMovementHistory = ({ stock = [], productionOrders = [], serviceRequests = [], inboundOrders = [], outboundOrders = [] }) => {
    const stockMovements = stock.flatMap((item) => {
        const fallbackTimestamp = item.updatedAt ?? item.createdAt ?? new Date().toISOString();
        const canonical = [
            movementFromRecord({
                sourceType: "STOCK",
                sourceId: item.id,
                action: "STOCK_UPDATED",
                user: item.updatedBy ?? "warehouse.bot",
                timestamp: fallbackTimestamp,
                reference: item.lotNumber ?? item.serialNumber ?? "-",
                details: { quantity: item.quantity, warehouseId: item.warehouseId },
            }),
        ];
        const history = Array.isArray(item.history)
            ? item.history.map((movement) =>
                movementFromRecord({
                    sourceType: "STOCK",
                    sourceId: item.id,
                    action: movement.action ?? "STOCK_HISTORY_ENTRY",
                    user: movement.user,
                    timestamp: movement.timestamp ?? fallbackTimestamp,
                    reference: movement.reference,
                    details: movement.details,
                })
            )
            : [];
        return [...history, ...canonical];
    });

    const orderMovements = [...inboundOrders, ...outboundOrders].map((order) =>
        movementFromRecord({
            sourceType: "ORDER",
            sourceId: order.id,
            action: `ORDER_${order.status ?? "OPEN"}`,
            user: order.updatedBy ?? "planner",
            timestamp: order.updatedAt ?? order.dueDate ?? new Date().toISOString(),
            reference: order.orderNumber,
            details: { priority: order.priority },
        })
    );

    const productionMovements = productionOrders.map((order) =>
        movementFromRecord({
            sourceType: "PRODUCTION_ORDER",
            sourceId: order.id,
            action: `PRODUCTION_${order.status ?? "OPEN"}`,
            user: order.updatedBy ?? "production.planner",
            timestamp: order.updatedAt ?? order.startDate ?? new Date().toISOString(),
            reference: order.orderNumber,
        })
    );

    const serviceMovements = serviceRequests.map((request) =>
        movementFromRecord({
            sourceType: "SERVICE_CASE",
            sourceId: request.id,
            action: `SERVICE_${request.status ?? "OPEN"}`,
            user: request.updatedBy ?? "service.dispatch",
            timestamp: request.updatedAt ?? request.createdAt ?? new Date().toISOString(),
            reference: request.subject,
        })
    );

    return [...stockMovements, ...orderMovements, ...productionMovements, ...serviceMovements].sort(
        (a, b) => Date.parse(b.timestamp ?? 0) - Date.parse(a.timestamp ?? 0)
    );
};

export const buildExceptionRows = ({ inboundOrders = [], stock = [], serviceCases = [] }) => {
    const delayed = inboundOrders.filter((row) => row.eta && Date.parse(row.eta) < Date.now() && row.status !== "COMPLETED")
        .map((row) => ({ id: `delay-${row.id}`, type: "LATE_DELIVERY", severity: "HIGH", ref: row.orderNumber, owner: row.supplier }));
    const blockedQc = stock.filter((row) => row.buckets.IN_QC > 0 || row.status === "BLOCKED")
        .map((row) => ({ id: `qc-${row.id}`, type: "QC_BLOCKED", severity: "MEDIUM", ref: row.lotNumber, owner: "quality" }));
    const slaBreaches = serviceCases.filter((row) => row.slaBreached)
        .map((row) => ({ id: `sla-${row.id}`, type: "SLA_BREACH", severity: "HIGH", ref: row.subject, owner: row.owner }));
    return [...delayed, ...blockedQc, ...slaBreaches];
};

export const buildInventoryBucketRows = (stock = []) => INVENTORY_BUCKETS.map((bucket) => ({
    id: bucket,
    bucket,
    quantity: stock.reduce((sum, row) => sum + toNumber(row.buckets?.[bucket]), 0),
}));
