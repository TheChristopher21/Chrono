import React from "react";

const FilterBar = ({ filters, onChange, onReset, placeholders }) => (
    <div className="sc-filter-bar">
        <div className="sc-filter-primary">
            <label className="sc-filter-field sc-filter-search">
                <span>{placeholders.searchLabel ?? placeholders.search}</span>
                <input type="search" value={filters.search} onChange={(event) => onChange("search", event.target.value)} placeholder={placeholders.search} />
            </label>
            <button type="button" className="secondary" onClick={onReset}>{placeholders.reset}</button>
        </div>

        <div className="sc-filter-groups">
            <section className="sc-filter-group">
                <p>{placeholders.groups?.location}</p>
                <div className="sc-filter-group-grid">
                    <label className="sc-filter-field">
                        <span>{placeholders.warehouse}</span>
                        <input type="text" value={filters.warehouse} onChange={(event) => onChange("warehouse", event.target.value)} placeholder={placeholders.warehouse} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.site}</span>
                        <input type="text" value={filters.site} onChange={(event) => onChange("site", event.target.value)} placeholder={placeholders.site} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.date}</span>
                        <input type="text" value={filters.date} onChange={(event) => onChange("date", event.target.value)} placeholder={placeholders.date} />
                    </label>
                </div>
            </section>

            <section className="sc-filter-group">
                <p>{placeholders.groups?.status}</p>
                <div className="sc-filter-group-grid">
                    <label className="sc-filter-field">
                        <span>{placeholders.status}</span>
                        <input type="text" value={filters.status} onChange={(event) => onChange("status", event.target.value)} placeholder={placeholders.status} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.owner}</span>
                        <input type="text" value={filters.owner} onChange={(event) => onChange("owner", event.target.value)} placeholder={placeholders.owner} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.priority}</span>
                        <input type="text" value={filters.priority} onChange={(event) => onChange("priority", event.target.value)} placeholder={placeholders.priority} />
                    </label>
                </div>
            </section>

            <section className="sc-filter-group">
                <p>{placeholders.groups?.reference}</p>
                <div className="sc-filter-group-grid">
                    <label className="sc-filter-field">
                        <span>{placeholders.partner}</span>
                        <input type="text" value={filters.partner} onChange={(event) => onChange("partner", event.target.value)} placeholder={placeholders.partner} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.sku}</span>
                        <input type="text" value={filters.sku} onChange={(event) => onChange("sku", event.target.value)} placeholder={placeholders.sku} />
                    </label>
                    <label className="sc-filter-field">
                        <span>{placeholders.batch}</span>
                        <input type="text" value={filters.batch} onChange={(event) => onChange("batch", event.target.value)} placeholder={placeholders.batch} />
                    </label>
                </div>
            </section>
        </div>
    </div>
);

export default FilterBar;
