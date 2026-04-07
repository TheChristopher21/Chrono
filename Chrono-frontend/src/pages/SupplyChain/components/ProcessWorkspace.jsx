import React, { useMemo, useState } from "react";
import FilterBar from "./FilterBar.jsx";
import SupplyChainDataTable from "./SupplyChainDataTable.jsx";
import DetailDrawer from "./DetailDrawer.jsx";

const PAGE_SIZE = 10;

const sortRows = (rows, sortBy, sortDirection) => [...rows].sort((a, b) => {
    const left = String(a?.[sortBy] ?? "").toLowerCase();
    const right = String(b?.[sortBy] ?? "").toLowerCase();
    return sortDirection === "asc" ? left.localeCompare(right) : right.localeCompare(left);
});

const initialFilter = { search: "", warehouse: "", site: "", status: "", partner: "", date: "", sku: "", batch: "", owner: "", priority: "" };

const initialQuickEntryForm = { productId: "", warehouseId: "", quantityChange: "1", type: "RECEIPT", reference: "" };
const initialCreateProductForm = { sku: "", name: "" };
const initialCreateWarehouseForm = { code: "", name: "", location: "" };

const ProcessWorkspace = ({ id, title, subtitle, rows, columns, timeline, text, quickEntry }) => {
    const [filters, setFilters] = useState(initialFilter);
    const [selectedIds, setSelectedIds] = useState(new Set());
    const [drawerRecord, setDrawerRecord] = useState(null);
    const [page, setPage] = useState(1);
    const [sortBy, setSortBy] = useState(columns[0]?.key ?? "id");
    const [sortDirection, setSortDirection] = useState("asc");
    const [visibleColumnKeys, setVisibleColumnKeys] = useState(columns.map((column) => column.key));
    const [massAction, setMassAction] = useState("assign");
    const [quickEntryOpen, setQuickEntryOpen] = useState(false);
    const [quickEntrySubmitting, setQuickEntrySubmitting] = useState(false);
    const [quickEntryForm, setQuickEntryForm] = useState(initialQuickEntryForm);
    const [createProductOpen, setCreateProductOpen] = useState(false);
    const [createWarehouseOpen, setCreateWarehouseOpen] = useState(false);
    const [createProductSubmitting, setCreateProductSubmitting] = useState(false);
    const [createWarehouseSubmitting, setCreateWarehouseSubmitting] = useState(false);
    const [createProductForm, setCreateProductForm] = useState(initialCreateProductForm);
    const [createWarehouseForm, setCreateWarehouseForm] = useState(initialCreateWarehouseForm);
    const storageKey = `chrono.supply.savedView.${id}`;

    const filteredRows = useMemo(() => rows.filter((row) => {
        const search = filters.search.toLowerCase();
        const fullText = JSON.stringify(row).toLowerCase();
        if (search && !fullText.includes(search)) return false;
        if (filters.warehouse && !String(row.warehouse ?? "").toLowerCase().includes(filters.warehouse.toLowerCase())) return false;
        if (filters.site && !String(row.site ?? "").toLowerCase().includes(filters.site.toLowerCase())) return false;
        if (filters.status && !String(row.status ?? "").toLowerCase().includes(filters.status.toLowerCase())) return false;
        if (filters.partner && !String(row.partner ?? row.customer ?? row.vendor ?? "").toLowerCase().includes(filters.partner.toLowerCase())) return false;
        if (filters.date && !String(row.date ?? row.dueDate ?? row.eta ?? "").toLowerCase().includes(filters.date.toLowerCase())) return false;
        if (filters.sku && !String(row.sku ?? "").toLowerCase().includes(filters.sku.toLowerCase())) return false;
        if (filters.batch && !String(row.batch ?? row.lot ?? "").toLowerCase().includes(filters.batch.toLowerCase())) return false;
        if (filters.owner && !String(row.owner ?? "").toLowerCase().includes(filters.owner.toLowerCase())) return false;
        if (filters.priority && !String(row.priority ?? "").toLowerCase().includes(filters.priority.toLowerCase())) return false;
        return true;
    }), [rows, filters]);

    const sortedRows = useMemo(() => sortRows(filteredRows, sortBy, sortDirection), [filteredRows, sortBy, sortDirection]);
    const visibleColumns = useMemo(() => columns.filter((column) => visibleColumnKeys.includes(column.key)), [columns, visibleColumnKeys]);

    const pagedRows = useMemo(() => {
        const start = (page - 1) * PAGE_SIZE;
        return sortedRows.slice(start, start + PAGE_SIZE);
    }, [page, sortedRows]);

    const totalPages = Math.max(1, Math.ceil(sortedRows.length / PAGE_SIZE));

    const toggleRow = (idToToggle) => {
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(idToToggle)) next.delete(idToToggle); else next.add(idToToggle);
            return next;
        });
    };

    const changeSort = (columnKey) => {
        if (sortBy === columnKey) {
            setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
            return;
        }
        setSortBy(columnKey);
        setSortDirection("asc");
    };

    const saveView = () => localStorage.setItem(storageKey, JSON.stringify({ filters, sortBy, sortDirection, visibleColumnKeys }));
    const loadView = () => {
        const raw = localStorage.getItem(storageKey);
        if (!raw) return;
        const saved = JSON.parse(raw);
        setFilters(saved.filters ?? filters);
        setSortBy(saved.sortBy ?? sortBy);
        setSortDirection(saved.sortDirection ?? sortDirection);
        setVisibleColumnKeys(saved.visibleColumnKeys ?? visibleColumnKeys);
    };

    const exportCsv = () => {
        const header = visibleColumns.map((column) => column.label).join(",");
        const lines = sortedRows.map((row) => visibleColumns.map((column) => `"${String(row[column.key] ?? "").replaceAll('"', '""')}"`).join(","));
        const csv = [header, ...lines].join("\n");
        const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = `${id}-export.csv`;
        link.click();
        URL.revokeObjectURL(link.href);
    };

    const handleMassAction = () => {
        if (selectedIds.size === 0) return;
        setSelectedIds(new Set());
    };

    const handleQuickEntryChange = (key, value) => {
        setQuickEntryForm((prev) => ({ ...prev, [key]: value }));
    };

    const submitQuickEntry = async (event) => {
        event.preventDefault();
        if (!quickEntry?.onSubmit || quickEntrySubmitting) return;
        const payload = {
            productId: Number(quickEntryForm.productId),
            warehouseId: Number(quickEntryForm.warehouseId),
            quantityChange: Number(quickEntryForm.quantityChange),
            type: quickEntryForm.type,
            reference: quickEntryForm.reference,
        };
        setQuickEntrySubmitting(true);
        try {
            const ok = await quickEntry.onSubmit(payload);
            if (ok !== false) {
                setQuickEntryForm(initialQuickEntryForm);
                setQuickEntryOpen(false);
            }
        } finally {
            setQuickEntrySubmitting(false);
        }
    };

    const submitCreateProduct = async () => {
        if (!quickEntry?.createProduct?.onSubmit || createProductSubmitting) return;
        setCreateProductSubmitting(true);
        try {
            const ok = await quickEntry.createProduct.onSubmit({
                sku: createProductForm.sku.trim(),
                name: createProductForm.name.trim(),
            });
            if (ok !== false) {
                setCreateProductForm(initialCreateProductForm);
                setCreateProductOpen(false);
            }
        } finally {
            setCreateProductSubmitting(false);
        }
    };

    const submitCreateWarehouse = async () => {
        if (!quickEntry?.createWarehouse?.onSubmit || createWarehouseSubmitting) return;
        setCreateWarehouseSubmitting(true);
        try {
            const ok = await quickEntry.createWarehouse.onSubmit({
                code: createWarehouseForm.code.trim(),
                name: createWarehouseForm.name.trim(),
                location: createWarehouseForm.location.trim(),
            });
            if (ok !== false) {
                setCreateWarehouseForm(initialCreateWarehouseForm);
                setCreateWarehouseOpen(false);
            }
        } finally {
            setCreateWarehouseSubmitting(false);
        }
    };

    return (
        <section className="sc-workspace card">
            <header className="sc-workspace-head">
                <div>
                    <h2>{title}</h2>
                    <p className="muted">{subtitle}</p>
                </div>
                <div className="panel-actions">
                    {quickEntry?.enabled && (
                        <button type="button" onClick={() => setQuickEntryOpen((prev) => !prev)}>
                            {quickEntryOpen ? quickEntry.closeLabel : quickEntry.openLabel}
                        </button>
                    )}
                    <button type="button" className="secondary" onClick={saveView}>{text.saveView}</button>
                    <button type="button" className="secondary" onClick={loadView}>{text.loadView}</button>
                    <button type="button" className="secondary" onClick={exportCsv}>{text.export}</button>
                </div>
            </header>

            {quickEntry?.enabled && quickEntryOpen && (
                <form className="sc-quick-entry card" onSubmit={submitQuickEntry}>
                    <div className="sc-quick-entry-head">
                        <h3>{quickEntry.title}</h3>
                        <p className="muted">{quickEntry.subtitle}</p>
                    </div>
                    <div className="sc-quick-entry-grid">
                        <label>
                            {quickEntry.labels.product}
                            <select value={quickEntryForm.productId} onChange={(event) => handleQuickEntryChange("productId", event.target.value)} required>
                                <option value="">{quickEntry.placeholders.product}</option>
                                {quickEntry.products.map((product) => (
                                    <option key={product.id} value={product.id}>
                                        {product.sku} · {product.name}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label>
                            {quickEntry.labels.warehouse}
                            <select value={quickEntryForm.warehouseId} onChange={(event) => handleQuickEntryChange("warehouseId", event.target.value)} required>
                                <option value="">{quickEntry.placeholders.warehouse}</option>
                                {quickEntry.warehouses.map((warehouse) => (
                                    <option key={warehouse.id} value={warehouse.id}>
                                        {warehouse.siteName} · {warehouse.name}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label>
                            {quickEntry.labels.quantity}
                            <input
                                type="number"
                                step="0.01"
                                value={quickEntryForm.quantityChange}
                                onChange={(event) => handleQuickEntryChange("quantityChange", event.target.value)}
                                required
                            />
                        </label>
                        <label>
                            {quickEntry.labels.type}
                            <select value={quickEntryForm.type} onChange={(event) => handleQuickEntryChange("type", event.target.value)}>
                                {quickEntry.types.map((option) => (
                                    <option key={option.value} value={option.value}>{option.label}</option>
                                ))}
                            </select>
                        </label>
                        <label className="sc-quick-entry-span">
                            {quickEntry.labels.reference}
                            <input
                                type="text"
                                value={quickEntryForm.reference}
                                onChange={(event) => handleQuickEntryChange("reference", event.target.value)}
                                placeholder={quickEntry.placeholders.reference}
                            />
                        </label>
                    </div>
                    <div className="sc-quick-entry-inline-actions">
                        {quickEntry.createProduct?.enabled && (
                            <button type="button" className="secondary" onClick={() => setCreateProductOpen((prev) => !prev)}>
                                {createProductOpen ? quickEntry.createProduct.closeLabel : quickEntry.createProduct.openLabel}
                            </button>
                        )}
                        {quickEntry.createWarehouse?.enabled && (
                            <button type="button" className="secondary" onClick={() => setCreateWarehouseOpen((prev) => !prev)}>
                                {createWarehouseOpen ? quickEntry.createWarehouse.closeLabel : quickEntry.createWarehouse.openLabel}
                            </button>
                        )}
                    </div>
                    {quickEntry.createProduct?.enabled && createProductOpen && (
                        <div className="sc-inline-form">
                            <h4>{quickEntry.createProduct.title}</h4>
                            <div className="sc-quick-entry-grid">
                                <label>
                                    {quickEntry.createProduct.labels.sku}
                                    <input
                                        type="text"
                                        value={createProductForm.sku}
                                        onChange={(event) => setCreateProductForm((prev) => ({ ...prev, sku: event.target.value }))}
                                        placeholder={quickEntry.createProduct.placeholders.sku}
                                        required
                                    />
                                </label>
                                <label>
                                    {quickEntry.createProduct.labels.name}
                                    <input
                                        type="text"
                                        value={createProductForm.name}
                                        onChange={(event) => setCreateProductForm((prev) => ({ ...prev, name: event.target.value }))}
                                        placeholder={quickEntry.createProduct.placeholders.name}
                                        required
                                    />
                                </label>
                                <div className="panel-actions sc-quick-entry-span">
                                    <button type="button" onClick={submitCreateProduct} disabled={createProductSubmitting}>
                                        {createProductSubmitting ? quickEntry.createProduct.submittingLabel : quickEntry.createProduct.submitLabel}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                    {quickEntry.createWarehouse?.enabled && createWarehouseOpen && (
                        <div className="sc-inline-form">
                            <h4>{quickEntry.createWarehouse.title}</h4>
                            <div className="sc-quick-entry-grid">
                                <label>
                                    {quickEntry.createWarehouse.labels.code}
                                    <input
                                        type="text"
                                        value={createWarehouseForm.code}
                                        onChange={(event) => setCreateWarehouseForm((prev) => ({ ...prev, code: event.target.value }))}
                                        placeholder={quickEntry.createWarehouse.placeholders.code}
                                        required
                                    />
                                </label>
                                <label>
                                    {quickEntry.createWarehouse.labels.name}
                                    <input
                                        type="text"
                                        value={createWarehouseForm.name}
                                        onChange={(event) => setCreateWarehouseForm((prev) => ({ ...prev, name: event.target.value }))}
                                        placeholder={quickEntry.createWarehouse.placeholders.name}
                                        required
                                    />
                                </label>
                                <label className="sc-quick-entry-span">
                                    {quickEntry.createWarehouse.labels.location}
                                    <input
                                        type="text"
                                        value={createWarehouseForm.location}
                                        onChange={(event) => setCreateWarehouseForm((prev) => ({ ...prev, location: event.target.value }))}
                                        placeholder={quickEntry.createWarehouse.placeholders.location}
                                    />
                                </label>
                                <div className="panel-actions sc-quick-entry-span">
                                    <button type="button" onClick={submitCreateWarehouse} disabled={createWarehouseSubmitting}>
                                        {createWarehouseSubmitting ? quickEntry.createWarehouse.submittingLabel : quickEntry.createWarehouse.submitLabel}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}
                    <div className="panel-actions">
                        <button type="submit" disabled={quickEntrySubmitting}>
                            {quickEntrySubmitting ? quickEntry.submittingLabel : quickEntry.submitLabel}
                        </button>
                        <button type="button" className="secondary" onClick={() => setQuickEntryOpen(false)}>
                            {quickEntry.closeLabel}
                        </button>
                    </div>
                </form>
            )}

            <FilterBar
                filters={filters}
                onChange={(key, value) => { setFilters((prev) => ({ ...prev, [key]: value })); setPage(1); }}
                onReset={() => setFilters(initialFilter)}
                placeholders={text.filters}
            />

            <div className="sc-toolbar">
                <details>
                    <summary>{text.columns}</summary>
                    <div className="sc-column-picker">
                        {columns.map((column) => (
                            <label key={column.key}>
                                <input
                                    type="checkbox"
                                    checked={visibleColumnKeys.includes(column.key)}
                                    onChange={() => setVisibleColumnKeys((prev) => prev.includes(column.key) ? prev.filter((key) => key !== column.key) : [...prev, column.key])}
                                />
                                {column.label}
                            </label>
                        ))}
                    </div>
                </details>
                <div className="sc-mass-actions">
                    <select value={massAction} onChange={(event) => setMassAction(event.target.value)}>
                        <option value="assign">{text.massAssign}</option>
                        <option value="priority">{text.massPriority}</option>
                        <option value="close">{text.massClose}</option>
                    </select>
                    <button type="button" className="secondary" disabled={selectedIds.size === 0} onClick={handleMassAction}>{text.massAction} ({selectedIds.size})</button>
                </div>
            </div>

            <SupplyChainDataTable
                columns={visibleColumns}
                rows={pagedRows}
                selectedIds={selectedIds}
                onToggleRow={toggleRow}
                onSort={changeSort}
                sortBy={sortBy}
                sortDirection={sortDirection}
                onOpen={(row) => setDrawerRecord(row)}
                text={text}
            />

            <footer className="sc-pagination">
                <button type="button" className="secondary" onClick={() => setPage((prev) => Math.max(1, prev - 1))}>{text.prev}</button>
                <span>{text.page} {page}/{totalPages}</span>
                <button type="button" className="secondary" onClick={() => setPage((prev) => Math.min(totalPages, prev + 1))}>{text.next}</button>
            </footer>

            <DetailDrawer
                open={Boolean(drawerRecord)}
                title={text.drawerTitle}
                record={drawerRecord}
                timelineTitle={text.timelineTitle}
                timelineItems={timeline.filter((item) => String(item.sourceId) === String(drawerRecord?.id))}
                approvalLabel={text.approvalLabel}
                onClose={() => setDrawerRecord(null)}
            />
        </section>
    );
};

export default ProcessWorkspace;
