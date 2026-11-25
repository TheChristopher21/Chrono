import React, { useMemo, useState } from "react";

const terminalStatuses = new Set(["FULFILLED", "CANCELLED"]);

const OutboundOrderDrawerActions = ({ record, warehouses, text, onReserve, onFulfill }) => {
    const [warehouseId, setWarehouseId] = useState(warehouses[0]?.id ? String(warehouses[0].id) : "");
    const [busyAction, setBusyAction] = useState("");
    const [error, setError] = useState("");
    const status = String(record?.status ?? "DRAFT").toUpperCase();
    const disabled = terminalStatuses.has(status) || !warehouseId || Boolean(busyAction);
    const totals = useMemo(() => (record?.lines ?? []).reduce((summary, line) => ({
        ordered: summary.ordered + Number(line.quantity ?? 0),
        reserved: summary.reserved + Number(line.reservedQuantity ?? 0),
        fulfilled: summary.fulfilled + Number(line.fulfilledQuantity ?? 0),
    }), { ordered: 0, reserved: 0, fulfilled: 0 }), [record?.lines]);

    const run = async (action, callback) => {
        setBusyAction(action);
        setError("");
        try {
            await callback(record.id, Number(warehouseId));
        } catch (requestError) {
            setError(requestError?.response?.data?.message ?? requestError?.message ?? text.failed);
        } finally {
            setBusyAction("");
        }
    };

    return (
        <section className="sc-drawer-process-actions">
            <h3>{text.title}</h3>
            <p className="muted">{text.description}</p>
            <div className="sc-order-progress">
                <span><strong>{totals.ordered}</strong>{text.ordered}</span>
                <span><strong>{totals.reserved}</strong>{text.reserved}</span>
                <span><strong>{totals.fulfilled}</strong>{text.fulfilled}</span>
            </div>
            <label>
                <span>{text.warehouse}</span>
                <select value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)} disabled={terminalStatuses.has(status)}>
                    <option value="">{text.selectWarehouse}</option>
                    {warehouses.map((warehouse) => (
                        <option key={warehouse.id} value={warehouse.id}>{warehouse.code} · {warehouse.name}</option>
                    ))}
                </select>
            </label>
            {error ? <p className="sc-process-error">{error}</p> : null}
            <div className="panel-actions">
                <button type="button" className="secondary" disabled={disabled || status === "RESERVED"} onClick={() => run("reserve", onReserve)}>
                    {busyAction === "reserve" ? text.working : text.reserve}
                </button>
                <button type="button" disabled={disabled} onClick={() => run("fulfill", onFulfill)}>
                    {busyAction === "fulfill" ? text.working : text.fulfill}
                </button>
            </div>
        </section>
    );
};

export default OutboundOrderDrawerActions;
