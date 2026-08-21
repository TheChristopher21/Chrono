import React from "react";
import StatusChip from "./StatusChip.jsx";
import HelpLabel from "./HelpLabel.jsx";

const SupplyChainDataTable = ({ columns, rows, selectedIds, onToggleRow, onSort, sortBy, sortDirection, onOpen, text }) => (
    <div className="sc-table-wrap">
        <table className="sc-table">
            <thead>
                <tr>
                    <th />
                    {columns.map((column) => (
                        <th key={column.key}>
                            <button type="button" className="table-sort" onClick={() => onSort(column.key)}>
                                <HelpLabel label={column.label} help={column.help} className="table-sort-label" />
                                <span className="table-sort-indicator" aria-hidden="true">{sortBy === column.key ? (sortDirection === "asc" ? "↑" : "↓") : ""}</span>
                            </button>
                        </th>
                    ))}
                    <th>{text.rowAction}</th>
                </tr>
            </thead>
            <tbody>
                {rows.length === 0 ? (
                    <tr>
                        <td colSpan={columns.length + 2} className="table-empty">
                            <div className="table-empty-state">
                                <strong>{text.empty}</strong>
                                <span>{text.emptyHint}</span>
                            </div>
                        </td>
                    </tr>
                ) : rows.map((row) => (
                    <tr key={row.id} className={selectedIds.has(row.id) ? "is-selected" : ""}>
                        <td>
                            <input type="checkbox" checked={selectedIds.has(row.id)} onChange={() => onToggleRow(row.id)} />
                        </td>
                        {columns.map((column) => (
                            <td key={`${row.id}-${column.key}`}>
                                {column.key === "status" ? <StatusChip value={row.status} label={row.statusLabel} /> : row[column.key] ?? "-"}
                            </td>
                        ))}
                        <td>
                            <button type="button" className="table-link" onClick={() => onOpen(row)}>
                                {text.openRow}
                            </button>
                        </td>
                    </tr>
                ))}
            </tbody>
        </table>
    </div>
);

export default SupplyChainDataTable;
