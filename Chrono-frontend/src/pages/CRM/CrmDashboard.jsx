import React, { useEffect, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";

const CrmDashboard = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [leads, setLeads] = useState([]);
    const [opportunities, setOpportunities] = useState([]);
    const [campaigns, setCampaigns] = useState([]);

    useEffect(() => {
        const load = async () => {
            try {
                const [leadRes, oppRes, campRes] = await Promise.all([
                    api.get("/api/crm/leads", { params: { status: "QUALIFIED" } }),
                    api.get("/api/crm/opportunities", { params: { stage: "NEGOTIATION" } }),
                    api.get("/api/crm/campaigns", { params: { status: "ACTIVE" } })
                ]);
                setLeads(leadRes.data ?? []);
                setOpportunities(oppRes.data ?? []);
                setCampaigns(campRes.data ?? []);
            } catch (error) {
                console.error("Failed to load CRM data", error);
                notify(t("crm.loadError", "CRM-Daten konnten nicht geladen werden."), "error");
            }
        };
        load();
    }, [notify, t]);

    return (
        <div className="admin-page">
            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <h1>{t("crm.title", "CRM & Marketing")}</h1>
                    <p className="muted">{t("crm.subtitle", "Leads, Aktivitäten und Kampagnen im Überblick")}</p>
                </header>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("crm.leads", "Qualifizierte Leads")}</h2>
                        <p className="metric">{leads.length}</p>
                    </article>
                    <article className="card">
                        <h2>{t("crm.pipeline", "Pipeline (Verhandlungen)")}</h2>
                        <p className="metric">{opportunities.length}</p>
                    </article>
                    <article className="card">
                        <h2>{t("crm.campaigns", "Aktive Kampagnen")}</h2>
                        <p className="metric">{campaigns.length}</p>
                    </article>
                </section>

                <section className="card">
                    <h2>{t("crm.leadList", "Leads")}</h2>
                    <ul className="list-unstyled">
                        {leads.map((lead) => (
                            <li key={lead.id}>
                                <strong>{lead.companyName || lead.contactName}</strong> – {lead.email}
                            </li>
                        ))}
                        {leads.length === 0 && <li>{t("crm.noLeads", "Keine Leads")}</li>}
                    </ul>
                </section>

                <section className="card">
                    <h2>{t("crm.opportunityList", "Opportunities")}</h2>
                    <ul className="list-unstyled">
                        {opportunities.map((opportunity) => (
                            <li key={opportunity.id}>
                                {opportunity.title} – CHF {Number(opportunity.value ?? 0).toFixed(2)}
                            </li>
                        ))}
                        {opportunities.length === 0 && <li>{t("crm.noOpportunities", "Keine Opportunities")}</li>}
                    </ul>
                </section>

                <section className="card">
                    <h2>{t("crm.campaignList", "Kampagnen")}</h2>
                    <ul className="list-unstyled">
                        {campaigns.map((campaign) => (
                            <li key={campaign.id}>
                                {campaign.name} – {campaign.channel}
                            </li>
                        ))}
                        {campaigns.length === 0 && <li>{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</li>}
                    </ul>
                </section>
            </main>
        </div>
    );
};

export default CrmDashboard;
