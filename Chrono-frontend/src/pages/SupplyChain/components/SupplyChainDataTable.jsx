import React from "react";
import StatusChip from "./StatusChip.jsx";

const SupplyChainDataTable = ({ columns, rows, selectedIds, onToggleRow, onSort, sortBy, sortDirection, onOpen, text }) => (
    <div className="sc-table-wrap">
        <table className="sc-table">
            <thead>
                <tr>
                    <th />
                    {columns.map((column) => (
                        <th key={column.key}>
                            <button type="button" className="table-sort" onClick={() => onSort(column.key)}>
                                {column.label}
                                {sortBy === column.key ? (sortDirection === "asc" ? " ↑" : " ↓") : ""}
                            </button>
                        </th>
                    ))}
                    <th>{text.rowAction}</th>
                </tr>
            </thead>
            <tbody>
                {rows.map((row) => (
                    <tr key={row.id}>
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
