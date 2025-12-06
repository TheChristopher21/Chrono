import React, { useMemo, useState } from "react";

const MarketingBoard = ({
    t,
    campaigns,
    campaignStatuses,
    campaignFilter,
    setCampaignFilter,
    campaignForm,
    setCampaignForm,
    onCampaignCreate,
    onCampaignUpdate,
    upcomingCampaigns,
    formatDateTime,
    dateRange,
    ownerFilter
}) => {
    const [channelFilter, setChannelFilter] = useState("ALL");
    const [search, setSearch] = useState("");

    const channels = useMemo(() => {
        const unique = new Set();
        campaigns.forEach((campaign) => {
            if (campaign.channel) {
                unique.add(campaign.channel);
            }
        });
        return Array.from(unique);
    }, [campaigns]);

    const filteredCampaigns = useMemo(() => {
        return campaigns.filter((campaign) => {
            const matchesStatus = campaignFilter === "ALL" ? true : campaign.status === campaignFilter;
            const matchesChannel = channelFilter === "ALL" ? true : campaign.channel === channelFilter;
            const matchesSearch = search ? (campaign.name || "").toLowerCase().includes(search.toLowerCase()) : true;
            return matchesStatus && matchesChannel && matchesSearch;
        });
    }, [campaigns, campaignFilter, channelFilter, search]);

    return (
        <section className="card">
            <div className="section-header">
                <div>
                    <p className="eyebrow">{t("crm.marketing", "Marketing")}</p>
                    <h2>{t("crm.marketingBoard", "Marketing Board")}</h2>
                </div>
                <div className="muted tiny">{dateRange} · {ownerFilter}</div>
            </div>

            <div className="filter-row">
                <input
                    type="search"
                    placeholder={t("crm.searchCampaigns", "Kampagnen suchen")}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                <select value={campaignFilter} onChange={(e) => setCampaignFilter(e.target.value)}>
                    <option value="ALL">{t("common.all", "Alle")}</option>
                    {campaignStatuses.map((status) => (
                        <option key={status} value={status}>{status}</option>
                    ))}
                </select>
                <select value={channelFilter} onChange={(e) => setChannelFilter(e.target.value)}>
                    <option value="ALL">{t("crm.allChannels", "Alle Kanäle")}</option>
                    {channels.map((channel) => (
                        <option key={channel} value={channel}>{channel}</option>
                    ))}
                </select>
            </div>

            <div className="marketing-board-grid">
                <div className="table-wrapper">
                    <table className="crm-table">
                        <thead>
                            <tr>
                                <th>{t("crm.campaign", "Kampagne")}</th>
                                <th>{t("crm.channel", "Kanal")}</th>
                                <th>{t("crm.status", "Status")}</th>
                                <th>{t("crm.start", "Start")}</th>
                                <th>{t("crm.end", "Ende")}</th>
                                <th>{t("crm.budget", "Budget")}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredCampaigns.map((campaign) => (
                                <tr key={campaign.id}>
                                    <td>{campaign.name}</td>
                                    <td>{campaign.channel || t("crm.noChannel", "Kein Kanal")}</td>
                                    <td>
                                        <select value={campaign.status} onChange={(e) => onCampaignUpdate(campaign.id, e.target.value)}>
                                            {campaignStatuses.map((status) => (
                                                <option key={status} value={status}>{status}</option>
                                            ))}
                                        </select>
                                    </td>
                                    <td>{campaign.startDate ? formatDateTime(campaign.startDate) : "-"}</td>
                                    <td>{campaign.endDate ? formatDateTime(campaign.endDate) : "-"}</td>
                                    <td>{campaign.budget ? `CHF ${campaign.budget.toLocaleString("de-CH")}` : t("crm.noBudget", "Kein Budget")}</td>
                                </tr>
                            ))}
                            {filteredCampaigns.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="muted">{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</td>
                                </tr>
                            )}
                        </tbody>
                    </table>

                    <form className="form-grid" onSubmit={onCampaignCreate}>
                        <label>
                            {t("crm.campaignName", "Name")}
                            <input type="text" value={campaignForm.name} onChange={(e) => setCampaignForm({ ...campaignForm, name: e.target.value })} required />
                        </label>
                        <label>
                            {t("crm.channel", "Kanal")}
                            <input type="text" value={campaignForm.channel} onChange={(e) => setCampaignForm({ ...campaignForm, channel: e.target.value })} />
                        </label>
                        <label>
                            {t("crm.status", "Status")}
                            <select value={campaignForm.status} onChange={(e) => setCampaignForm({ ...campaignForm, status: e.target.value })}>
                                {campaignStatuses.map((status) => (
                                    <option key={status} value={status}>{status}</option>
                                ))}
                            </select>
                        </label>
                        <label>
                            {t("crm.start", "Start")}
                            <input type="date" value={campaignForm.startDate} onChange={(e) => setCampaignForm({ ...campaignForm, startDate: e.target.value })} />
                        </label>
                        <label>
                            {t("crm.end", "Ende")}
                            <input type="date" value={campaignForm.endDate} onChange={(e) => setCampaignForm({ ...campaignForm, endDate: e.target.value })} />
                        </label>
                        <label>
                            {t("crm.budget", "Budget")}
                            <input type="number" value={campaignForm.budget} onChange={(e) => setCampaignForm({ ...campaignForm, budget: e.target.value })} />
                        </label>
                        <div className="form-actions">
                            <button type="submit" className="primary">{t("crm.addCampaign", "Kampagne hinzufügen")}</button>
                        </div>
                    </form>
                </div>

                <div className="upcoming-widget">
                    <p className="eyebrow">{t("crm.upcoming", "Bevorstehend")}</p>
                    <ul className="timeline">
                        {upcomingCampaigns.map((campaign) => (
                            <li key={campaign.id}>
                                <div className="timeline-title">{campaign.name}</div>
                                <div className="muted">{campaign.startDate ? formatDateTime(campaign.startDate) : t("crm.noStartDate", "Kein Startdatum")}</div>
                            </li>
                        ))}
                        {upcomingCampaigns.length === 0 && <li className="muted">{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</li>}
                    </ul>
                </div>
            </div>
        </section>
    );
};

export default MarketingBoard;
