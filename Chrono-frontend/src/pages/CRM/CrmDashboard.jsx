import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/CrmDashboardScoped.css";
import KpiSummary from "../../components/crm/KpiSummary.jsx";
import PipelineOverview from "../../components/crm/PipelineOverview.jsx";
import SalesBoard from "../../components/crm/SalesBoard.jsx";
import MarketingBoard from "../../components/crm/MarketingBoard.jsx";
import EntitySlideOver from "../../components/crm/EntitySlideOver.jsx";
import TeamPerformance from "../../components/crm/TeamPerformance.jsx";

const DATE_RANGE_PRESETS = ["LAST_30_DAYS", "THIS_QUARTER", "THIS_YEAR", "CUSTOM"];
const OWNER_FILTERS = ["ALL", "ME"];

const CrmDashboard = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [leads, setLeads] = useState([]);
    const [opportunities, setOpportunities] = useState([]);
    const [campaigns, setCampaigns] = useState([]);
    const [customers, setCustomers] = useState([]);
    const [selectedEntity, setSelectedEntity] = useState(null);
    const [selectedEntityType, setSelectedEntityType] = useState(null);
    const [customerContacts, setCustomerContacts] = useState([]);
    const [customerActivities, setCustomerActivities] = useState([]);
    const [customerAddresses, setCustomerAddresses] = useState([]);
    const [customerDocuments, setCustomerDocuments] = useState([]);
    const [leadFilter, setLeadFilter] = useState("ALL");
    const [oppFilter, setOppFilter] = useState("ALL");
    const [campaignFilter, setCampaignFilter] = useState("ALL");
    const [dateRange, setDateRange] = useState("LAST_30_DAYS");
    const [ownerFilter, setOwnerFilter] = useState("ALL");
    const [leadForm, setLeadForm] = useState({ companyName: "", contactName: "", email: "", status: "NEW" });
    const [opportunityForm, setOpportunityForm] = useState({ title: "", value: "", probability: "", stage: "QUALIFICATION" });
    const [campaignForm, setCampaignForm] = useState({ name: "", status: "PLANNED", channel: "", startDate: "", endDate: "", budget: "" });
    const [addressForm, setAddressForm] = useState({ id: null, street: "", postalCode: "", city: "", country: "", type: "OFFICE" });
    const [contactForm, setContactForm] = useState({ id: null, firstName: "", lastName: "", email: "", phone: "", role: "" });
    const [activityForm, setActivityForm] = useState({ id: null, notes: "", timestamp: "", contactId: "", type: "NOTE" });
    const [documentForm, setDocumentForm] = useState({ fileName: "", url: "" });
    const [accessDenied, setAccessDenied] = useState(false);

    const formatDateTime = (value) => {
        if (!value) {
            return "";
        }
        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) {
            return value;
        }
        return parsed.toLocaleString();
    };

    useEffect(() => {
        if (accessDenied) {
            return;
        }
        const load = async () => {
            try {
                const ownerParam = ownerFilter === "ME" ? "me" : undefined;
                const leadParams = {
                    ...(leadFilter === "ALL" ? {} : { status: leadFilter }),
                    ...(dateRange ? { dateRange } : {}),
                    ...(ownerParam ? { owner: ownerParam } : {})
                };
                const opportunityParams = {
                    ...(oppFilter === "ALL" ? {} : { stage: oppFilter }),
                    ...(dateRange ? { dateRange } : {}),
                    ...(ownerParam ? { owner: ownerParam } : {})
                };
                const campaignParams = {
                    ...(campaignFilter === "ALL" ? {} : { status: campaignFilter }),
                    ...(dateRange ? { dateRange } : {}),
                    ...(ownerParam ? { owner: ownerParam } : {})
                };
                const customerParams = {
                    ...(dateRange ? { dateRange } : {}),
                    ...(ownerParam ? { owner: ownerParam } : {})
                };

                const [leadRes, oppRes, campRes, customerRes] = await Promise.all([
                    api.get("/api/crm/leads", { params: leadParams }),
                    api.get("/api/crm/opportunities", { params: opportunityParams }),
                    api.get("/api/crm/campaigns", { params: campaignParams }),
                    api.get("/api/customers", { params: customerParams })
                ]);
                setAccessDenied(false);
                setLeads(leadRes.data ?? []);
                setOpportunities(oppRes.data ?? []);
                setCampaigns(campRes.data ?? []);
                setCustomers(customerRes.data ?? []);
            } catch (error) {
                console.error("Failed to load CRM data", error);
                if (error?.response?.status === 403) {
                    if (!accessDenied) {
                        notify(t("crm.featureDisabled", "Das CRM-Modul ist für Ihre Firma nicht freigeschaltet."), "warning");
                    }
                    setAccessDenied(true);
                    setLeads([]);
                    setOpportunities([]);
                    setCampaigns([]);
                    setCustomers([]);
                } else {
                    notify(t("crm.loadError", "CRM-Daten konnten nicht geladen werden."), "error");
                }
            }
        };
        load();
    }, [notify, t, leadFilter, oppFilter, campaignFilter, accessDenied, dateRange, ownerFilter]);

    const loadCustomerDetails = async (customer) => {
        if (!customer) {
            setSelectedEntity(null);
            setSelectedEntityType(null);
            setCustomerContacts([]);
            setCustomerActivities([]);
            setCustomerAddresses([]);
            setCustomerDocuments([]);
            return;
        }
        if (accessDenied) {
            return;
        }
        setSelectedEntity(customer);
        setSelectedEntityType("customer");
        try {
            const [contactRes, activityRes, addressRes, documentRes] = await Promise.all([
                api.get(`/api/crm/customers/${customer.id}/contacts`),
                api.get(`/api/crm/customers/${customer.id}/activities`),
                api.get(`/api/crm/customers/${customer.id}/addresses`),
                api.get(`/api/crm/customers/${customer.id}/documents`)
            ]);
            setCustomerContacts(contactRes.data ?? []);
            setCustomerActivities(activityRes.data ?? []);
            setCustomerAddresses(addressRes.data ?? []);
            setCustomerDocuments(documentRes.data ?? []);
        } catch (error) {
            console.error("Failed to load customer details", error);
            notify(t("crm.customerLoadFailed", "Kundendetails konnten nicht geladen werden."), "error");
        }
    };

    const resetCustomerForms = () => {
        setAddressForm({ id: null, street: "", postalCode: "", city: "", country: "", type: "OFFICE" });
        setContactForm({ id: null, firstName: "", lastName: "", email: "", phone: "", role: "" });
        setActivityForm({ id: null, notes: "", timestamp: "", contactId: "", type: "NOTE" });
        setDocumentForm({ fileName: "", url: "" });
    };

    const handleLeadSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/crm/leads", leadForm);
            notify(t("crm.leadCreated", "Lead erstellt."), "success");
            setLeadForm({ companyName: "", contactName: "", email: "", status: "NEW" });
            setLeadFilter("NEW");
        } catch (error) {
            console.error("Failed to create lead", error);
            notify(t("crm.leadCreateFailed", "Lead konnte nicht erstellt werden."), "error");
        }
    };

    const handleLeadStatusChange = async (leadId, status, ownerId) => {
        try {
            const payload = { status };
            if (ownerId) {
                payload.ownerId = ownerId;
            }
            await api.patch(`/api/crm/leads/${leadId}`, payload);
            notify(t("crm.leadUpdated", "Lead aktualisiert."), "success");
            setLeadFilter(status);
        } catch (error) {
            console.error("Failed to update lead", error);
            notify(t("crm.leadUpdateFailed", "Lead konnte nicht aktualisiert werden."), "error");
        }
    };

    const handleOpportunitySubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                title: opportunityForm.title,
                value: opportunityForm.value ? Number(opportunityForm.value) : undefined,
                probability: opportunityForm.probability ? Number(opportunityForm.probability) : undefined,
                stage: opportunityForm.stage
            };
            await api.post("/api/crm/opportunities", payload);
            notify(t("crm.opportunityCreated", "Opportunity erstellt."), "success");
            setOpportunityForm({ title: "", value: "", probability: "", stage: "QUALIFICATION" });
            setOppFilter("QUALIFICATION");
        } catch (error) {
            console.error("Failed to create opportunity", error);
            notify(t("crm.opportunityCreateFailed", "Opportunity konnte nicht erstellt werden."), "error");
        }
    };

    const handleOpportunityStageChange = async (id, stage, ownerId) => {
        try {
            const payload = { stage };
            if (ownerId) {
                payload.ownerId = ownerId;
            }
            await api.patch(`/api/crm/opportunities/${id}`, payload);
            notify(t("crm.opportunityUpdated", "Opportunity aktualisiert."), "success");
            setOppFilter(stage);
        } catch (error) {
            console.error("Failed to update opportunity", error);
            notify(t("crm.opportunityUpdateFailed", "Opportunity konnte nicht aktualisiert werden."), "error");
        }
    };

    const handleCampaignSubmit = async (event) => {
        event.preventDefault();
        try {
            const parsedBudget = campaignForm.budget === "" ? undefined : Number(campaignForm.budget);
            const payload = {
                name: campaignForm.name,
                status: campaignForm.status,
                channel: campaignForm.channel || undefined,
                startDate: campaignForm.startDate || undefined,
                endDate: campaignForm.endDate || undefined,
                budget: parsedBudget !== undefined && !Number.isNaN(parsedBudget) ? parsedBudget : undefined
            };
            await api.post("/api/crm/campaigns", payload);
            notify(t("crm.campaignCreated", "Kampagne erstellt."), "success");
            setCampaignForm({ name: "", status: "PLANNED", channel: "", startDate: "", endDate: "", budget: "" });
            setCampaignFilter("PLANNED");
        } catch (error) {
            console.error("Failed to create campaign", error);
            notify(t("crm.campaignCreateFailed", "Kampagne konnte nicht erstellt werden."), "error");
        }
    };

    const handleCampaignUpdate = async (id, status) => {
        try {
            await api.patch(`/api/crm/campaigns/${id}`, { status });
            notify(t("crm.campaignUpdated", "Kampagne aktualisiert."), "success");
            setCampaignFilter(status);
        } catch (error) {
            console.error("Failed to update campaign", error);
            notify(t("crm.campaignUpdateFailed", "Kampagne konnte nicht aktualisiert werden."), "error");
        }
    };

    const handleAddressSubmit = async (event) => {
        event.preventDefault();
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            const payload = { type: addressForm.type, street: addressForm.street, postalCode: addressForm.postalCode, city: addressForm.city, country: addressForm.country };
            const endpoint = addressForm.id
                ? `/api/crm/customers/${selectedEntity.id}/addresses/${addressForm.id}`
                : `/api/crm/customers/${selectedEntity.id}/addresses`;
            if (addressForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.addressSaved", "Adresse gespeichert."), "success");
            await loadCustomerDetails(selectedEntity);
            setAddressForm({ id: null, street: "", postalCode: "", city: "", country: "", type: "OFFICE" });
        } catch (error) {
            console.error("Failed to save address", error);
            notify(t("crm.addressSaveFailed", "Adresse konnte nicht gespeichert werden."), "error");
        }
    };

    const handleAddressDelete = async (addressId) => {
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            await api.delete(`/api/crm/customers/${selectedEntity.id}/addresses/${addressId}`);
            notify(t("crm.addressDeleted", "Adresse gelöscht."), "success");
            await loadCustomerDetails(selectedEntity);
        } catch (error) {
            console.error("Failed to delete address", error);
            notify(t("crm.addressDeleteFailed", "Adresse konnte nicht gelöscht werden."), "error");
        }
    };

    const handleContactSubmit = async (event) => {
        event.preventDefault();
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            const payload = { firstName: contactForm.firstName, lastName: contactForm.lastName, email: contactForm.email, phone: contactForm.phone, role: contactForm.role };
            const endpoint = contactForm.id
                ? `/api/crm/customers/${selectedEntity.id}/contacts/${contactForm.id}`
                : `/api/crm/customers/${selectedEntity.id}/contacts`;
            if (contactForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.contactSaved", "Kontakt gespeichert."), "success");
            await loadCustomerDetails(selectedEntity);
            setContactForm({ id: null, firstName: "", lastName: "", email: "", phone: "", role: "" });
        } catch (error) {
            console.error("Failed to save contact", error);
            notify(t("crm.contactSaveFailed", "Kontakt konnte nicht gespeichert werden."), "error");
        }
    };

    const handleContactDelete = async (id) => {
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            await api.delete(`/api/crm/customers/${selectedEntity.id}/contacts/${id}`);
            notify(t("crm.contactDeleted", "Kontakt gelöscht."), "success");
            await loadCustomerDetails(selectedEntity);
        } catch (error) {
            console.error("Failed to delete contact", error);
            notify(t("crm.contactDeleteFailed", "Kontakt konnte nicht gelöscht werden."), "error");
        }
    };

    const handleActivitySubmit = async (event) => {
        event.preventDefault();
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            const payload = {
                type: activityForm.type,
                notes: activityForm.notes,
                contactId: activityForm.contactId ? Number(activityForm.contactId) : undefined,
                timestamp: activityForm.timestamp ? new Date(activityForm.timestamp).toISOString() : undefined
            };
            const endpoint = activityForm.id
                ? `/api/crm/customers/${selectedEntity.id}/activities/${activityForm.id}`
                : `/api/crm/customers/${selectedEntity.id}/activities`;
            if (activityForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.activitySaved", "Aktivität gespeichert."), "success");
            await loadCustomerDetails(selectedEntity);
            setActivityForm({ id: null, notes: "", timestamp: "", contactId: "", type: "NOTE" });
        } catch (error) {
            console.error("Failed to save activity", error);
            notify(t("crm.activitySaveFailed", "Aktivität konnte nicht gespeichert werden."), "error");
        }
    };

    const handleActivityDelete = async (id) => {
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            await api.delete(`/api/crm/customers/${selectedEntity.id}/activities/${id}`);
            notify(t("crm.activityDeleted", "Aktivität gelöscht."), "success");
            await loadCustomerDetails(selectedEntity);
        } catch (error) {
            console.error("Failed to delete activity", error);
            notify(t("crm.activityDeleteFailed", "Aktivität konnte nicht gelöscht werden."), "error");
        }
    };

    const handleDocumentSubmit = async (event) => {
        event.preventDefault();
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            const payload = {
                fileName: documentForm.fileName,
                url: documentForm.url ? documentForm.url.trim() : undefined
            };
            await api.post(`/api/crm/customers/${selectedEntity.id}/documents`, payload);
            notify(t("crm.documentSaved", "Dokument gespeichert."), "success");
            await loadCustomerDetails(selectedEntity);
            setDocumentForm({ fileName: "", url: "" });
        } catch (error) {
            console.error("Failed to save document", error);
            notify(t("crm.documentSaveFailed", "Dokument konnte nicht gespeichert werden."), "error");
        }
    };

    const handleDocumentDelete = async (id) => {
        if (!selectedEntity || selectedEntityType !== "customer") return;
        try {
            await api.delete(`/api/crm/customers/${selectedEntity.id}/documents/${id}`);
            notify(t("crm.documentDeleted", "Dokument gelöscht."), "success");
            await loadCustomerDetails(selectedEntity);
        } catch (error) {
            console.error("Failed to delete document", error);
            notify(t("crm.documentDeleteFailed", "Dokument konnte nicht gelöscht werden."), "error");
        }
    };

    const leadStatuses = useMemo(() => ["NEW", "QUALIFIED", "CONVERTED", "DISQUALIFIED"], []);
    const opportunityStages = useMemo(() => ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "WON", "LOST"], []);
    const campaignStatuses = useMemo(() => ["PLANNED", "ACTIVE", "COMPLETED", "ARCHIVED"], []);

    const leadStatusCounts = useMemo(() => {
        return leadStatuses.map((status) => ({
            status,
            count: leads.filter((lead) => lead.status === status).length
        }));
    }, [leadStatuses, leads]);

    const opportunityStageCounts = useMemo(() => {
        return opportunityStages.map((stage) => ({
            stage,
            count: opportunities.filter((opp) => opp.stage === stage).length
        }));
    }, [opportunityStages, opportunities]);

    const totalPipelineValue = useMemo(() => {
        return opportunities.reduce((sum, opp) => (typeof opp.value === "number" ? sum + opp.value : sum), 0);
    }, [opportunities]);

    const activeCampaigns = useMemo(() => campaigns.filter((campaign) => campaign.status === "ACTIVE"), [campaigns]);

    const upcomingCampaigns = useMemo(() => {
        return campaigns
            .filter((campaign) => campaign.startDate)
            .sort((a, b) => new Date(a.startDate) - new Date(b.startDate))
            .slice(0, 3);
    }, [campaigns]);

    const campaignChannels = useMemo(() => {
        const counts = {};
        campaigns.forEach((campaign) => {
            const channel = campaign.channel || t("crm.noChannel", "Kein Kanal definiert");
            counts[channel] = (counts[channel] || 0) + 1;
        });
        return Object.entries(counts).sort((a, b) => b[1] - a[1]);
    }, [campaigns, t]);

    const averageDealValue = useMemo(() => {
        const opportunityCount = opportunities.length;
        if (opportunityCount === 0) return 0;
        return totalPipelineValue / opportunityCount;
    }, [totalPipelineValue, opportunities]);

    const winRate = useMemo(() => {
        const won = opportunities.filter((opp) => opp.stage === "WON").length;
        const lost = opportunities.filter((opp) => opp.stage === "LOST").length;
        if (won + lost === 0) return 0;
        return (won / (won + lost)) * 100;
    }, [opportunities]);

    const ownerOptions = useMemo(() => {
        const owners = new Set();
        [...leads, ...opportunities, ...customers].forEach((entity) => {
            if (entity.owner) {
                owners.add(entity.owner);
            }
            if (entity.ownerName) {
                owners.add(entity.ownerName);
            }
        });
        return Array.from(owners);
    }, [leads, opportunities, customers]);

    const handleEntityOpen = (type, entity) => {
        setSelectedEntityType(type);
        if (type === "customer") {
            loadCustomerDetails(entity);
        } else {
            setSelectedEntity(entity);
        }
    };

    const closeSlideOver = () => {
        setSelectedEntity(null);
        setSelectedEntityType(null);
        resetCustomerForms();
    };

    return (
        <div className="admin-page crm-page">
            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <div>
                        <h1>{t("crm.title", "CRM & Marketing")}</h1>
                        <p className="muted">{t("crm.subtitle", "Leads, Aktivitäten und Kampagnen im Überblick")}</p>
                    </div>
                    <div className="crm-filter-bar">
                        <label>
                            {t("crm.dateRange", "Zeitraum")}
                            <select value={dateRange} onChange={(e) => setDateRange(e.target.value)}>
                                {DATE_RANGE_PRESETS.map((preset) => (
                                    <option key={preset} value={preset}>{preset}</option>
                                ))}
                            </select>
                        </label>
                        <label>
                            {t("crm.owner", "Owner")}
                            <select value={ownerFilter} onChange={(e) => setOwnerFilter(e.target.value)}>
                                {OWNER_FILTERS.map((filter) => (
                                    <option key={filter} value={filter}>{filter}</option>
                                ))}
                            </select>
                        </label>
                    </div>
                </header>

                <KpiSummary
                    customers={customers.length}
                    leads={leads.length}
                    pipelineValue={totalPipelineValue}
                    activeCampaigns={activeCampaigns.length}
                    averageDealValue={averageDealValue}
                    winRate={winRate}
                    dateRange={dateRange}
                    ownerFilter={ownerFilter}
                />

                {accessDenied ? (
                    <section className="card">
                        <h2>{t("crm.featureDisabledTitle", "CRM nicht freigeschaltet")}</h2>
                        <p className="muted">
                            {t("crm.featureDisabledHint", "Bitte wenden Sie sich an den Chrono-Support, um das CRM-Modul zu aktivieren.")}
                        </p>
                    </section>
                ) : (
                    <>
                        <PipelineOverview
                            leadFilter={leadFilter}
                            setLeadFilter={setLeadFilter}
                            oppFilter={oppFilter}
                            setOppFilter={setOppFilter}
                            campaignFilter={campaignFilter}
                            setCampaignFilter={setCampaignFilter}
                            leadStatuses={leadStatuses}
                            opportunityStages={opportunityStages}
                            campaignStatuses={campaignStatuses}
                            leadStatusCounts={leadStatusCounts}
                            opportunityStageCounts={opportunityStageCounts}
                            campaignChannels={campaignChannels}
                            upcomingCampaigns={upcomingCampaigns}
                            formatDateTime={formatDateTime}
                        />

                        <SalesBoard
                            t={t}
                            leads={leads}
                            opportunities={opportunities}
                            customers={customers}
                            leadStatuses={leadStatuses}
                            opportunityStages={opportunityStages}
                            ownerOptions={ownerOptions}
                            onLeadUpdate={handleLeadStatusChange}
                            onOpportunityUpdate={handleOpportunityStageChange}
                            onLeadCreate={handleLeadSubmit}
                            onOpportunityCreate={handleOpportunitySubmit}
                            leadForm={leadForm}
                            setLeadForm={setLeadForm}
                            opportunityForm={opportunityForm}
                            setOpportunityForm={setOpportunityForm}
                            onEntitySelect={handleEntityOpen}
                            dateRange={dateRange}
                            ownerFilter={ownerFilter}
                        />

                        <MarketingBoard
                            t={t}
                            campaigns={campaigns}
                            campaignStatuses={campaignStatuses}
                            campaignFilter={campaignFilter}
                            setCampaignFilter={setCampaignFilter}
                            campaignForm={campaignForm}
                            setCampaignForm={setCampaignForm}
                            onCampaignCreate={handleCampaignSubmit}
                            onCampaignUpdate={handleCampaignUpdate}
                            upcomingCampaigns={upcomingCampaigns}
                            formatDateTime={formatDateTime}
                            dateRange={dateRange}
                            ownerFilter={ownerFilter}
                        />

                        <TeamPerformance
                            t={t}
                            leads={leads}
                            opportunities={opportunities}
                            activities={customerActivities}
                            ownerOptions={ownerOptions}
                            dateRange={dateRange}
                            ownerFilter={ownerFilter}
                        />
                    </>
                )}
            </main>

            <EntitySlideOver
                t={t}
                isOpen={!!selectedEntity}
                entityType={selectedEntityType}
                entity={selectedEntity}
                onClose={closeSlideOver}
                contacts={customerContacts}
                activities={customerActivities}
                addresses={customerAddresses}
                documents={customerDocuments}
                contactForm={contactForm}
                setContactForm={setContactForm}
                activityForm={activityForm}
                setActivityForm={setActivityForm}
                addressForm={addressForm}
                setAddressForm={setAddressForm}
                documentForm={documentForm}
                setDocumentForm={setDocumentForm}
                onContactSubmit={handleContactSubmit}
                onContactDelete={handleContactDelete}
                onActivitySubmit={handleActivitySubmit}
                onActivityDelete={handleActivityDelete}
                onAddressSubmit={handleAddressSubmit}
                onAddressDelete={handleAddressDelete}
                onDocumentSubmit={handleDocumentSubmit}
                onDocumentDelete={handleDocumentDelete}
                formatDateTime={formatDateTime}
                resetForms={resetCustomerForms}
            />
        </div>
    );
};

export default CrmDashboard;
