export const PROCESS_STATUS = {
    OPEN: "OPEN",
    IN_PROGRESS: "IN_PROGRESS",
    BLOCKED: "BLOCKED",
    COMPLETED: "COMPLETED",
};

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
    code: warehouse.code ?? "-",
    name: warehouse.name ?? "-",
    location: warehouse.location ?? "-",
});

export const normalizeStock = (entry = {}) => ({
    id: entry.id,
    productId: entry.productId ?? entry.product?.id,
    warehouseId: entry.warehouseId ?? entry.warehouse?.id,
    quantity: toNumber(entry.quantity),
    lotNumber: entry.lotNumber ?? "-",
    expirationDate: entry.expirationDate ?? null,
    status: entry.status ?? PROCESS_STATUS.OPEN,
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
});

export const normalizeQualityCase = (qualityCase = {}) => ({
    id: qualityCase.id,
    reference: qualityCase.reference ?? "-",
    status: qualityCase.status ?? PROCESS_STATUS.OPEN,
    severity: qualityCase.severity ?? "MEDIUM",
    owner: qualityCase.owner ?? "-",
    createdAt: qualityCase.createdAt ?? null,
});

export const normalizeReturn = (record = {}) => ({
    id: record.id,
    reference: record.reference ?? record.rma ?? "-",
    status: record.status ?? PROCESS_STATUS.OPEN,
    reason: record.reason ?? "-",
    customer: record.customer ?? "-",
});

export const normalizeServiceCase = (serviceCase = {}) => ({
    id: serviceCase.id,
    customerName: serviceCase.customerName ?? "-",
    subject: serviceCase.subject ?? "-",
    status: serviceCase.status ?? PROCESS_STATUS.OPEN,
    owner: serviceCase.owner ?? "-",
    updatedAt: serviceCase.updatedAt ?? serviceCase.createdAt ?? null,
});

export const createMovementHistory = ({ stock = [], productionOrders = [], serviceRequests = [] }) => {
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

    return [...stockMovements, ...productionMovements, ...serviceMovements].sort(
        (a, b) => Date.parse(b.timestamp ?? 0) - Date.parse(a.timestamp ?? 0)
    );
};
