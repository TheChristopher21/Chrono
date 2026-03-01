import React from "react";
import { useTranslation } from "../../context/LanguageContext.jsx";

const PipelineOverview = ({
    leadFilter,
    setLeadFilter,
    oppFilter,
    setOppFilter,
    campaignFilter,
    setCampaignFilter,
    leadStatuses,
    opportunityStages,
    campaignStatuses,
    leadStatusCounts,
    opportunityStageCounts,
    campaignChannels,
    upcomingCampaigns,
    formatDateTime,
    leadStatusLabels,
    opportunityStageLabels,
    campaignStatusLabels
}) => {
    const { t } = useTranslation();

    return (
        <section className="card-grid focus-grid">
            <article className="card">
                <div className="section-header">
                    <div>
                        <p className="eyebrow">{t("crm.leadStatus", "Lead-Status")}</p>
                        <h2>{t("crm.leadFlow", "Lead-Fluss")}</h2>
                    </div>
                    <select value={leadFilter} onChange={(e) => setLeadFilter(e.target.value)}>
                        <option value="ALL">{t("common.all", "Alle")}</option>
                        {leadStatuses.map((status) => (
                            <option key={status} value={status}>{leadStatusLabels?.[status] ?? status}</option>
                        ))}
                    </select>
                </div>
                <div className="pill-row">
                    {leadStatusCounts.map(({ status, count }) => (
                        <span key={status} className="pill">
                            {leadStatusLabels?.[status] ?? status} · {count}
                        </span>
                    ))}
                </div>
            </article>
            <article className="card">
                <div className="section-header">
                    <div>
                        <p className="eyebrow">{t("crm.opportunityStages", "Phasen")}</p>
                        <h2>{t("crm.pipeline", "Pipeline")}</h2>
                    </div>
                    <select value={oppFilter} onChange={(e) => setOppFilter(e.target.value)}>
                        <option value="ALL">{t("common.all", "Alle")}</option>
                        {opportunityStages.map((stage) => (
                            <option key={stage} value={stage}>{opportunityStageLabels?.[stage] ?? stage}</option>
                        ))}
                    </select>
                </div>
                <div className="pill-row">
                    {opportunityStageCounts.map(({ stage, count }) => (
                        <span key={stage} className="pill">
                            {opportunityStageLabels?.[stage] ?? stage} · {count}
                        </span>
                    ))}
                </div>
            </article>
            <article className="card full-width">
                <div className="section-header">
                    <div>
                        <p className="eyebrow">{t("crm.marketing", "Marketing")}</p>
                        <h2>{t("crm.marketingOverview", "Kampagnen-Überblick")}</h2>
                    </div>
                    <select value={campaignFilter} onChange={(e) => setCampaignFilter(e.target.value)}>
                        <option value="ALL">{t("common.all", "Alle")}</option>
                        {campaignStatuses.map((status) => (
                            <option key={status} value={status}>{campaignStatusLabels?.[status] ?? status}</option>
                        ))}
                    </select>
                </div>
                <div className="marketing-grid">
                    <div>
                        <p className="eyebrow">{t("crm.channelMix", "Kanäle")}</p>
                        <div className="pill-row">
                            {campaignChannels.map(([channel, amount]) => (
                                <span key={channel} className="pill">
                                    {channel} · {amount}
                                </span>
                            ))}
                            {campaignChannels.length === 0 && (
                                <span className="pill muted">{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</span>
                            )}
                        </div>
                    </div>
                    <div>
                        <p className="eyebrow">{t("crm.upcoming", "Bevorstehend")}</p>
                        <ul className="timeline">
                            {upcomingCampaigns.map((campaign) => (
                                <li key={campaign.id}>
                                    <div className="timeline-title">{campaign.name}</div>
                                    <div className="muted">{campaign.startDate ? formatDateTime(campaign.startDate) : t("crm.noStartDate", "Kein Startdatum")}</div>
                                </li>
                            ))}
                            {upcomingCampaigns.length === 0 && (
                                <li className="muted">{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</li>
                            )}
                        </ul>
                    </div>
                </div>
            </article>
        </section>
    );
};

export default PipelineOverview;
