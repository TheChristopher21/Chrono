import React from "react";

const FilterBar = ({ filters, onChange, onReset, placeholders }) => (
    <div className="sc-filter-bar">
        <input type="search" value={filters.search} onChange={(event) => onChange("search", event.target.value)} placeholder={placeholders.search} />
        <input type="text" value={filters.warehouse} onChange={(event) => onChange("warehouse", event.target.value)} placeholder={placeholders.warehouse} />
        <input type="text" value={filters.site} onChange={(event) => onChange("site", event.target.value)} placeholder={placeholders.site} />
        <input type="text" value={filters.status} onChange={(event) => onChange("status", event.target.value)} placeholder={placeholders.status} />
        <input type="text" value={filters.partner} onChange={(event) => onChange("partner", event.target.value)} placeholder={placeholders.partner} />
        <input type="text" value={filters.date} onChange={(event) => onChange("date", event.target.value)} placeholder={placeholders.date} />
        <input type="text" value={filters.sku} onChange={(event) => onChange("sku", event.target.value)} placeholder={placeholders.sku} />
        <input type="text" value={filters.batch} onChange={(event) => onChange("batch", event.target.value)} placeholder={placeholders.batch} />
        <input type="text" value={filters.owner} onChange={(event) => onChange("owner", event.target.value)} placeholder={placeholders.owner} />
        <input type="text" value={filters.priority} onChange={(event) => onChange("priority", event.target.value)} placeholder={placeholders.priority} />
        <button type="button" className="secondary" onClick={onReset}>{placeholders.reset}</button>
    </div>
);

export default FilterBar;
