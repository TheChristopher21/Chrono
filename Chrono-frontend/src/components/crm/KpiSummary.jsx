import React from "react";
import { useTranslation } from "../../context/LanguageContext.jsx";

const KpiSummary = ({ customers, leads, pipelineValue, activeCampaigns, averageDealValue, winRate, dateRange, ownerFilter }) => {
    const { t } = useTranslation();

    const formatCurrency = (value) => {
        return `CHF ${Number(value || 0).toLocaleString("de-CH", { minimumFractionDigits: 0 })}`;
    };

    return (
        <section className="card-grid summary-grid">
            <article className="card stat-card">
                <div className="stat-label">{t("crm.customerOverview", "Kunden")}</div>
                <div className="metric">{customers}</div>
                <p className="muted">{t("crm.customerCount", "Aktive Accounts im Blick")}</p>
                <div className="muted tiny">{t("crm.filteredBy", "Gefiltert nach")}: {dateRange} · {ownerFilter}</div>
            </article>
            <article className="card stat-card">
                <div className="stat-label">{t("crm.leads", "Leads")}</div>
                <div className="metric">{leads}</div>
                <p className="muted">{t("crm.newLeads", "Neu hinzugefügt und qualifiziert")}</p>
                <div className="muted tiny">{t("crm.filteredBy", "Gefiltert nach")}: {dateRange} · {ownerFilter}</div>
            </article>
            <article className="card stat-card">
                <div className="stat-label">{t("crm.opportunities", "Opportunities")}</div>
                <div className="metric">{formatCurrency(pipelineValue)}</div>
                <p className="muted">{t("crm.pipelineValue", "Gesamter Pipeline-Wert")}</p>
                <div className="muted tiny">{t("crm.avgDeal", "Ø Deal")}: {formatCurrency(averageDealValue)}</div>
            </article>
            <article className="card stat-card">
                <div className="stat-label">{t("crm.campaigns", "Kampagnen")}</div>
                <div className="metric">{activeCampaigns}</div>
                <p className="muted">{t("crm.activeCampaigns", "Derzeit aktive Marketinginitiativen")}</p>
                <div className="muted tiny">{t("crm.winRate", "Win-Rate")}: {winRate.toFixed(1)}%</div>
            </article>
        </section>
    );
};

export default KpiSummary;
