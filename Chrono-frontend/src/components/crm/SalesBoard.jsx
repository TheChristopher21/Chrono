import React, { useMemo, useState } from "react";
import { useTranslation } from "../../context/LanguageContext.jsx";

const SalesBoard = ({
    t,
    leads,
    opportunities,
    customers,
    leadStatuses,
    opportunityStages,
    ownerOptions,
    onLeadUpdate,
    onOpportunityUpdate,
    onLeadCreate,
    onOpportunityCreate,
    leadForm,
    setLeadForm,
    opportunityForm,
    setOpportunityForm,
    onEntitySelect,
    dateRangeLabel,
    ownerFilterLabel,
    leadStatusLabels,
    opportunityStageLabels
}) => {
    const { t: translate } = useTranslation();
    const [activeTab, setActiveTab] = useState("leads");
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [ownerSelection, setOwnerSelection] = useState("ALL");

    const formatCurrency = (value) => {
        return `CHF (Schweizer Franken) ${Number(value || 0).toLocaleString("de-CH")}`;
    };

    const resolveStatusLabel = (status) => {
        return leadStatusLabels?.[status] ?? opportunityStageLabels?.[status] ?? status;
    };

    const filteredLeads = useMemo(() => {
        return leads.filter((lead) => {
            const matchesSearch = search
                ? [lead.companyName, lead.contactName, lead.email].some((field) =>
                      (field || "").toLowerCase().includes(search.toLowerCase())
                  )
                : true;
            const matchesStatus = statusFilter === "ALL" ? true : lead.status === statusFilter;
            const matchesOwner = ownerSelection === "ALL" ? true : lead.owner === ownerSelection || lead.ownerName === ownerSelection;
            return matchesSearch && matchesStatus && matchesOwner;
        });
    }, [leads, search, statusFilter, ownerSelection]);

    const filteredOpportunities = useMemo(() => {
        return opportunities.filter((opp) => {
            const matchesSearch = search ? (opp.title || "").toLowerCase().includes(search.toLowerCase()) : true;
            const matchesStage = statusFilter === "ALL" ? true : opp.stage === statusFilter;
            const matchesOwner = ownerSelection === "ALL" ? true : opp.owner === ownerSelection || opp.ownerName === ownerSelection;
            return matchesSearch && matchesStage && matchesOwner;
        });
    }, [opportunities, search, statusFilter, ownerSelection]);

    const filteredCustomers = useMemo(() => {
        return customers.filter((customer) => {
            const matchesSearch = search ? (customer.name || customer.companyName || "").toLowerCase().includes(search.toLowerCase()) : true;
            const matchesOwner = ownerSelection === "ALL" ? true : customer.owner === ownerSelection || customer.ownerName === ownerSelection;
            return matchesSearch && matchesOwner;
        });
    }, [customers, search, ownerSelection]);

    const renderLeadTable = () => (
        <div className="table-wrapper">
            <table className="crm-table">
                <thead>
                    <tr>
                        <th><input type="checkbox" aria-label={t("common.selectAll", "Alle auswählen")} /></th>
                        <th>{t("crm.company", "Firma")}</th>
                        <th>{t("crm.contactName", "Ansprechpartner")}</th>
                        <th>Email</th>
                        <th>{t("crm.status", "Status")}</th>
                        <th>{t("crm.owner", "Owner (Verantwortlich)")}</th>
                    </tr>
                </thead>
                <tbody>
                    {filteredLeads.map((lead) => (
                        <tr key={lead.id} onClick={() => onEntitySelect("lead", lead)}>
                            <td><input type="checkbox" aria-label={t("common.select", "Auswählen")} /></td>
                            <td>{lead.companyName || translate("crm.unknownCompany", "Unbekannte Firma")}</td>
                            <td>{lead.contactName || translate("crm.noContact", "Kein Kontakt")}</td>
                            <td>{lead.email}</td>
                            <td>
                                <select value={lead.status} onChange={(e) => onLeadUpdate(lead.id, e.target.value, lead.ownerId)}>
                                    {leadStatuses.map((status) => (
                                        <option key={status} value={status}>{resolveStatusLabel(status)}</option>
                                    ))}
                                </select>
                            </td>
                            <td>
                                <select
                                    value={lead.ownerId || lead.owner || ""}
                                    onChange={(e) => onLeadUpdate(lead.id, lead.status, e.target.value || undefined)}
                                >
                                    <option value="">{translate("crm.noOwner", "Kein Owner (Verantwortlicher)")}</option>
                                    {ownerOptions.map((owner) => (
                                        <option key={owner} value={owner}>{owner}</option>
                                    ))}
                                </select>
                            </td>
                        </tr>
                    ))}
                    {filteredLeads.length === 0 && (
                        <tr>
                            <td colSpan={6} className="muted">{t("crm.noLeads", "Keine Leads")}</td>
                        </tr>
                    )}
                </tbody>
            </table>
            <form className="form-grid" onSubmit={onLeadCreate}>
                <label>
                    {t("crm.company", "Firma")}
                    <input type="text" value={leadForm.companyName} onChange={(e) => setLeadForm({ ...leadForm, companyName: e.target.value })} />
                </label>
                <label>
                    {t("crm.contactName", "Ansprechpartner")}
                    <input type="text" value={leadForm.contactName} onChange={(e) => setLeadForm({ ...leadForm, contactName: e.target.value })} />
                </label>
                <label>
                    Email
                    <input type="email" value={leadForm.email} onChange={(e) => setLeadForm({ ...leadForm, email: e.target.value })} required />
                </label>
                <label>
                    {t("crm.status", "Status")}
                    <select value={leadForm.status} onChange={(e) => setLeadForm({ ...leadForm, status: e.target.value })}>
                        {leadStatuses.map((status) => (
                            <option key={status} value={status}>{resolveStatusLabel(status)}</option>
                        ))}
                    </select>
                </label>
                <div className="form-actions">
                    <button type="submit" className="primary">{t("crm.addLead", "Lead hinzufügen")}</button>
                </div>
            </form>
        </div>
    );

    const renderOpportunityTable = () => (
        <div className="table-wrapper">
            <table className="crm-table">
                <thead>
                    <tr>
                        <th><input type="checkbox" aria-label={t("common.selectAll", "Alle auswählen")} /></th>
                        <th>{t("crm.opportunity", "Opportunity")}</th>
                        <th>{t("crm.value", "Wert")}</th>
                        <th>{t("crm.stage", "Phase")}</th>
                        <th>{t("crm.owner", "Owner (Verantwortlich)")}</th>
                    </tr>
                </thead>
                <tbody>
                    {filteredOpportunities.map((opp) => (
                        <tr key={opp.id} onClick={() => onEntitySelect("opportunity", opp)}>
                            <td><input type="checkbox" aria-label={t("common.select", "Auswählen")} /></td>
                            <td>{opp.title}</td>
                            <td>{typeof opp.value === "number" ? formatCurrency(opp.value) : "-"}</td>
                            <td>
                                <select value={opp.stage} onChange={(e) => onOpportunityUpdate(opp.id, e.target.value, opp.ownerId)}>
                                    {opportunityStages.map((stage) => (
                                        <option key={stage} value={stage}>{resolveStatusLabel(stage)}</option>
                                    ))}
                                </select>
                            </td>
                            <td>
                                <select
                                    value={opp.ownerId || opp.owner || ""}
                                    onChange={(e) => onOpportunityUpdate(opp.id, opp.stage, e.target.value || undefined)}
                                >
                                    <option value="">{translate("crm.noOwner", "Kein Owner (Verantwortlicher)")}</option>
                                    {ownerOptions.map((owner) => (
                                        <option key={owner} value={owner}>{owner}</option>
                                    ))}
                                </select>
                            </td>
                        </tr>
                    ))}
                    {filteredOpportunities.length === 0 && (
                        <tr>
                            <td colSpan={5} className="muted">{t("crm.noOpportunities", "Keine Opportunities")}</td>
                        </tr>
                    )}
                </tbody>
            </table>
            <form className="form-grid" onSubmit={onOpportunityCreate}>
                <label>
                    {t("crm.opportunity", "Opportunity")}
                    <input type="text" value={opportunityForm.title} onChange={(e) => setOpportunityForm({ ...opportunityForm, title: e.target.value })} required />
                </label>
                <label>
                    {t("crm.value", "Wert")}
                    <input type="number" value={opportunityForm.value} onChange={(e) => setOpportunityForm({ ...opportunityForm, value: e.target.value })} />
                </label>
                <label>
                    {t("crm.stage", "Phase")}
                    <select value={opportunityForm.stage} onChange={(e) => setOpportunityForm({ ...opportunityForm, stage: e.target.value })}>
                        {opportunityStages.map((stage) => (
                            <option key={stage} value={stage}>{resolveStatusLabel(stage)}</option>
                        ))}
                    </select>
                </label>
                <div className="form-actions">
                    <button type="submit" className="primary">{t("crm.addOpportunity", "Opportunity hinzufügen")}</button>
                </div>
            </form>
        </div>
    );

    const renderCustomerTable = () => (
        <div className="table-wrapper">
            <table className="crm-table">
                <thead>
                    <tr>
                        <th><input type="checkbox" aria-label={t("common.selectAll", "Alle auswählen")} /></th>
                        <th>{t("crm.customer", "Kunde")}</th>
                        <th>{t("crm.owner", "Owner (Verantwortlich)")}</th>
                        <th>{t("crm.region", "Region")}</th>
                    </tr>
                </thead>
                <tbody>
                    {filteredCustomers.map((customer) => (
                        <tr key={customer.id} onClick={() => onEntitySelect("customer", customer)}>
                            <td><input type="checkbox" aria-label={t("common.select", "Auswählen")} /></td>
                            <td>{customer.name || customer.companyName}</td>
                            <td>{customer.ownerName || customer.owner || translate("crm.noOwner", "Kein Owner (Verantwortlicher)")}</td>
                            <td>{customer.region || translate("crm.noRegion", "Keine Region")}</td>
                        </tr>
                    ))}
                    {filteredCustomers.length === 0 && (
                        <tr>
                            <td colSpan={4} className="muted">{t("crm.noCustomers", "Keine Kunden")}</td>
                        </tr>
                    )}
                </tbody>
            </table>
        </div>
    );

    const renderTab = () => {
        if (activeTab === "leads") return renderLeadTable();
        if (activeTab === "opportunities") return renderOpportunityTable();
        return renderCustomerTable();
    };

    return (
        <section className="card">
            <div className="section-header">
                <div>
                    <p className="eyebrow">{t("crm.salesBoard", "Sales Board (Vertriebsübersicht)")}</p>
                    <h2>{t("crm.revenueEngine", "Umsatz-Engine")}</h2>
                </div>
                <div className="muted tiny">{dateRangeLabel} · {ownerFilterLabel}</div>
            </div>
            <div className="crm-tabs">
                <button className={activeTab === "leads" ? "active" : ""} onClick={() => setActiveTab("leads")}>{t("crm.leads", "Leads")}</button>
                <button className={activeTab === "opportunities" ? "active" : ""} onClick={() => setActiveTab("opportunities")}>{t("crm.opportunities", "Opportunities")}</button>
                <button className={activeTab === "accounts" ? "active" : ""} onClick={() => setActiveTab("accounts")}>{t("crm.accounts", "Accounts")}</button>
            </div>

            <div className="filter-row">
                <input
                    type="search"
                    placeholder={t("crm.searchPlaceholder", "Suchen nach Name, Firma oder E-Mail")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                {activeTab !== "accounts" && (
                    <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                        <option value="ALL">{t("common.all", "Alle")}</option>
                        {(activeTab === "leads" ? leadStatuses : opportunityStages).map((status) => (
                            <option key={status} value={status}>{resolveStatusLabel(status)}</option>
                        ))}
                    </select>
                )}
                <select value={ownerSelection} onChange={(e) => setOwnerSelection(e.target.value)}>
                    <option value="ALL">{t("crm.allOwners", "Alle Owner (Verantwortliche)")}</option>
                    {ownerOptions.map((owner) => (
                        <option key={owner} value={owner}>{owner}</option>
                    ))}
                </select>
            </div>

            {renderTab()}
        </section>
    );
};

export default SalesBoard;
