import React from "react";

const FilterBar = ({ filters, onChange, onReset, placeholders }) => (
    <div className="sc-filter-bar">
        <input
            type="search"
            value={filters.search}
            onChange={(event) => onChange("search", event.target.value)}
            placeholder={placeholders.search}
        />
        <input type="text" value={filters.warehouse} onChange={(event) => onChange("warehouse", event.target.value)} placeholder={placeholders.warehouse} />
        <input type="text" value={filters.site} onChange={(event) => onChange("site", event.target.value)} placeholder={placeholders.site} />
        <input type="text" value={filters.status} onChange={(event) => onChange("status", event.target.value)} placeholder={placeholders.status} />
        <input type="text" value={filters.partner} onChange={(event) => onChange("partner", event.target.value)} placeholder={placeholders.partner} />
        <button type="button" className="secondary" onClick={onReset}>{placeholders.reset}</button>
    </div>
);

export default FilterBar;
