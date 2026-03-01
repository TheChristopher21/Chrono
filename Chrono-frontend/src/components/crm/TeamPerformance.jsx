import React, { useMemo } from "react";

const TeamPerformance = ({ t, leads, opportunities, activities, dateRangeLabel, ownerFilterLabel }) => {
    const leaderboard = useMemo(() => {
        const stats = {};
        const ensureOwner = (ownerKey) => {
            if (!stats[ownerKey]) {
                stats[ownerKey] = { owner: ownerKey, leads: 0, opportunities: 0, pipeline: 0, won: 0, activities: 0 };
            }
        };

        leads.forEach((lead) => {
            const ownerKey = lead.ownerName || lead.owner || t("crm.unassigned", "Unassigned");
            ensureOwner(ownerKey);
            stats[ownerKey].leads += 1;
        });

        opportunities.forEach((opp) => {
            const ownerKey = opp.ownerName || opp.owner || t("crm.unassigned", "Unassigned");
            ensureOwner(ownerKey);
            stats[ownerKey].opportunities += 1;
            if (typeof opp.value === "number") {
                stats[ownerKey].pipeline += opp.value;
            }
            if (opp.stage === "WON") {
                stats[ownerKey].won += 1;
            }
        });

        activities.forEach((activity) => {
            const ownerKey = activity.ownerName || activity.owner || t("crm.unassigned", "Unassigned");
            ensureOwner(ownerKey);
            stats[ownerKey].activities += 1;
        });

        return Object.values(stats).sort((a, b) => b.pipeline - a.pipeline);
    }, [activities, leads, opportunities, t]);

    const maxPipeline = leaderboard[0]?.pipeline || 1;

    const formatCurrency = (value) => {
        return `CHF (Schweizer Franken) ${Number(value || 0).toLocaleString("de-CH")}`;
    };

    return (
        <section className="card">
            <div className="section-header">
                <div>
                    <p className="eyebrow">{t("crm.teamPerformance", "Team Performance (Teamleistung)")}</p>
                    <h2>{t("crm.leaderboard", "Leaderboard (Rangliste)")}</h2>
                </div>
                <div className="muted tiny">{dateRangeLabel} · {ownerFilterLabel}</div>
            </div>
            <div className="table-wrapper">
                <table className="crm-table">
                    <thead>
                        <tr>
                            <th>{t("crm.owner", "Owner (Verantwortlich)")}</th>
                            <th>{t("crm.leads", "Leads")}</th>
                            <th>{t("crm.opportunities", "Opportunities")}</th>
                            <th>{t("crm.pipeline", "Pipeline")}</th>
                            <th>{t("crm.wonDeals", "Gewonnen")}</th>
                            <th>{t("crm.activities", "Aktivitäten")}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {leaderboard.map((row) => (
                            <tr key={row.owner}>
                                <td>
                                    <div className="bar-label">{row.owner}</div>
                                    <div className="bar-track">
                                        <div className="bar-fill" style={{ width: `${Math.min(100, (row.pipeline / maxPipeline) * 100)}%` }} />
                                    </div>
                                </td>
                                <td>{row.leads}</td>
                                <td>{row.opportunities}</td>
                                <td>{row.pipeline ? formatCurrency(row.pipeline) : "-"}</td>
                                <td>{row.won}</td>
                                <td>{row.activities}</td>
                            </tr>
                        ))}
                        {leaderboard.length === 0 && (
                            <tr>
                                <td colSpan={6} className="muted">{t("crm.noTeamData", "Keine Team-Daten")}</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </section>
    );
};

export default TeamPerformance;
