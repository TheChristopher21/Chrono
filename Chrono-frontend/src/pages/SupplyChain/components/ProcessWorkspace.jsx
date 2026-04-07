import React, { useMemo, useState } from "react";
import FilterBar from "./FilterBar.jsx";
import SupplyChainDataTable from "./SupplyChainDataTable.jsx";
import DetailDrawer from "./DetailDrawer.jsx";

const PAGE_SIZE = 8;

const sortRows = (rows, sortBy, sortDirection) => [...rows].sort((a, b) => {
    const left = String(a?.[sortBy] ?? "").toLowerCase();
    const right = String(b?.[sortBy] ?? "").toLowerCase();
    return sortDirection === "asc" ? left.localeCompare(right) : right.localeCompare(left);
});

const ProcessWorkspace = ({ id, title, subtitle, rows, columns, timeline, text }) => {
    const [filters, setFilters] = useState({ search: "", warehouse: "", site: "", status: "", partner: "", date: "", sku: "", batch: "", owner: "", priority: "" });
    const [selectedIds, setSelectedIds] = useState(new Set());
    const [drawerRecord, setDrawerRecord] = useState(null);
    const [page, setPage] = useState(1);
    const [sortBy, setSortBy] = useState(columns[0]?.key ?? "id");
    const [sortDirection, setSortDirection] = useState("asc");
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

    const saveView = () => localStorage.setItem(storageKey, JSON.stringify({ filters, sortBy, sortDirection }));
    const loadView = () => {
        const raw = localStorage.getItem(storageKey);
        if (!raw) return;
        const saved = JSON.parse(raw);
        setFilters(saved.filters ?? filters);
        setSortBy(saved.sortBy ?? sortBy);
        setSortDirection(saved.sortDirection ?? sortDirection);
    };

    return (
        <section className="sc-workspace card">
            <header className="sc-workspace-head">
                <div>
                    <h2>{title}</h2>
                    <p className="muted">{subtitle}</p>
                </div>
                <div className="panel-actions">
                    <button type="button" className="secondary" onClick={saveView}>{text.saveView}</button>
                    <button type="button" className="secondary" onClick={loadView}>{text.loadView}</button>
                    <button type="button" className="secondary" disabled={selectedIds.size === 0}>{text.massAction} ({selectedIds.size})</button>
                </div>
            </header>

            <FilterBar
                filters={filters}
                onChange={(key, value) => { setFilters((prev) => ({ ...prev, [key]: value })); setPage(1); }}
                onReset={() => setFilters({ search: "", warehouse: "", site: "", status: "", partner: "", date: "", sku: "", batch: "", owner: "", priority: "" })}
                placeholders={text.filters}
            />

            <SupplyChainDataTable
                columns={columns}
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
