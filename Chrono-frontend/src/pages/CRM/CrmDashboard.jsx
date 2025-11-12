import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/CrmDashboardScoped.css";


const CrmDashboard = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [leads, setLeads] = useState([]);
    const [opportunities, setOpportunities] = useState([]);
    const [campaigns, setCampaigns] = useState([]);
    const [customers, setCustomers] = useState([]);
    const [selectedCustomer, setSelectedCustomer] = useState(null);
    const [customerContacts, setCustomerContacts] = useState([]);
    const [customerActivities, setCustomerActivities] = useState([]);
    const [customerAddresses, setCustomerAddresses] = useState([]);
    const [customerDocuments, setCustomerDocuments] = useState([]);
    const [leadFilter, setLeadFilter] = useState("QUALIFIED");
    const [oppFilter, setOppFilter] = useState("NEGOTIATION");
    const [campaignFilter, setCampaignFilter] = useState("ACTIVE");
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
                const [leadRes, oppRes, campRes, customerRes] = await Promise.all([
                    api.get("/api/crm/leads", { params: { status: leadFilter } }),
                    api.get("/api/crm/opportunities", { params: { stage: oppFilter } }),
                    api.get("/api/crm/campaigns", { params: { status: campaignFilter } }),
                    api.get("/api/customers")
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
    }, [notify, t, leadFilter, oppFilter, campaignFilter, accessDenied]);

    const loadCustomerDetails = async (customer) => {
        if (!customer) {
            setSelectedCustomer(null);
            setCustomerContacts([]);
            setCustomerActivities([]);
            setCustomerAddresses([]);
            setCustomerDocuments([]);
            return;
        }
        if (accessDenied) {
            return;
        }
        setSelectedCustomer(customer);
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

    const handleLeadStatusChange = async (leadId, status) => {
        try {
            await api.patch(`/api/crm/leads/${leadId}`, { status });
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

    const handleOpportunityStageChange = async (id, stage) => {
        try {
            await api.patch(`/api/crm/opportunities/${id}`, { stage });
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
        if (!selectedCustomer) return;
        try {
            const payload = { type: addressForm.type, street: addressForm.street, postalCode: addressForm.postalCode, city: addressForm.city, country: addressForm.country };
            const endpoint = addressForm.id
                ? `/api/crm/customers/${selectedCustomer.id}/addresses/${addressForm.id}`
                : `/api/crm/customers/${selectedCustomer.id}/addresses`;
            if (addressForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.addressSaved", "Adresse gespeichert."), "success");
            await loadCustomerDetails(selectedCustomer);
            setAddressForm({ id: null, street: "", postalCode: "", city: "", country: "", type: "OFFICE" });
        } catch (error) {
            console.error("Failed to save address", error);
            notify(t("crm.addressSaveFailed", "Adresse konnte nicht gespeichert werden."), "error");
        }
    };

    const handleAddressDelete = async (addressId) => {
        if (!selectedCustomer) return;
        try {
            await api.delete(`/api/crm/customers/${selectedCustomer.id}/addresses/${addressId}`);
            notify(t("crm.addressDeleted", "Adresse gelöscht."), "success");
            await loadCustomerDetails(selectedCustomer);
        } catch (error) {
            console.error("Failed to delete address", error);
            notify(t("crm.addressDeleteFailed", "Adresse konnte nicht gelöscht werden."), "error");
        }
    };

    const handleContactSubmit = async (event) => {
        event.preventDefault();
        if (!selectedCustomer) return;
        try {
            const payload = { firstName: contactForm.firstName, lastName: contactForm.lastName, email: contactForm.email, phone: contactForm.phone, role: contactForm.role };
            const endpoint = contactForm.id
                ? `/api/crm/customers/${selectedCustomer.id}/contacts/${contactForm.id}`
                : `/api/crm/customers/${selectedCustomer.id}/contacts`;
            if (contactForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.contactSaved", "Kontakt gespeichert."), "success");
            await loadCustomerDetails(selectedCustomer);
            setContactForm({ id: null, firstName: "", lastName: "", email: "", phone: "", role: "" });
        } catch (error) {
            console.error("Failed to save contact", error);
            notify(t("crm.contactSaveFailed", "Kontakt konnte nicht gespeichert werden."), "error");
        }
    };

    const handleContactDelete = async (id) => {
        if (!selectedCustomer) return;
        try {
            await api.delete(`/api/crm/customers/${selectedCustomer.id}/contacts/${id}`);
            notify(t("crm.contactDeleted", "Kontakt gelöscht."), "success");
            await loadCustomerDetails(selectedCustomer);
        } catch (error) {
            console.error("Failed to delete contact", error);
            notify(t("crm.contactDeleteFailed", "Kontakt konnte nicht gelöscht werden."), "error");
        }
    };

    const handleActivitySubmit = async (event) => {
        event.preventDefault();
        if (!selectedCustomer) return;
        try {
            const payload = {
                type: activityForm.type,
                notes: activityForm.notes,
                contactId: activityForm.contactId ? Number(activityForm.contactId) : undefined,
                timestamp: activityForm.timestamp ? new Date(activityForm.timestamp).toISOString() : undefined
            };
            const endpoint = activityForm.id
                ? `/api/crm/customers/${selectedCustomer.id}/activities/${activityForm.id}`
                : `/api/crm/customers/${selectedCustomer.id}/activities`;
            if (activityForm.id) {
                await api.put(endpoint, payload);
            } else {
                await api.post(endpoint, payload);
            }
            notify(t("crm.activitySaved", "Aktivität gespeichert."), "success");
            await loadCustomerDetails(selectedCustomer);
            setActivityForm({ id: null, notes: "", timestamp: "", contactId: "", type: "NOTE" });
        } catch (error) {
            console.error("Failed to save activity", error);
            notify(t("crm.activitySaveFailed", "Aktivität konnte nicht gespeichert werden."), "error");
        }
    };

    const handleActivityDelete = async (id) => {
        if (!selectedCustomer) return;
        try {
            await api.delete(`/api/crm/customers/${selectedCustomer.id}/activities/${id}`);
            notify(t("crm.activityDeleted", "Aktivität gelöscht."), "success");
            await loadCustomerDetails(selectedCustomer);
        } catch (error) {
            console.error("Failed to delete activity", error);
            notify(t("crm.activityDeleteFailed", "Aktivität konnte nicht gelöscht werden."), "error");
        }
    };

    const handleDocumentSubmit = async (event) => {
        event.preventDefault();
        if (!selectedCustomer) return;
        try {
            const payload = {
                fileName: documentForm.fileName,
                url: documentForm.url ? documentForm.url.trim() : undefined
            };
            await api.post(`/api/crm/customers/${selectedCustomer.id}/documents`, payload);
            notify(t("crm.documentSaved", "Dokument gespeichert."), "success");
            await loadCustomerDetails(selectedCustomer);
            setDocumentForm({ fileName: "", url: "" });
        } catch (error) {
            console.error("Failed to save document", error);
            notify(t("crm.documentSaveFailed", "Dokument konnte nicht gespeichert werden."), "error");
        }
    };

    const handleDocumentDelete = async (id) => {
        if (!selectedCustomer) return;
        try {
            await api.delete(`/api/crm/customers/${selectedCustomer.id}/documents/${id}`);
            notify(t("crm.documentDeleted", "Dokument gelöscht."), "success");
            await loadCustomerDetails(selectedCustomer);
        } catch (error) {
            console.error("Failed to delete document", error);
            notify(t("crm.documentDeleteFailed", "Dokument konnte nicht gelöscht werden."), "error");
        }
    };

    const leadStatuses = useMemo(() => ["NEW", "QUALIFIED", "CONVERTED", "DISQUALIFIED"], []);
    const opportunityStages = useMemo(() => ["QUALIFICATION", "PROPOSAL", "NEGOTIATION", "WON", "LOST"], []);
    const campaignStatuses = useMemo(() => ["PLANNED", "ACTIVE", "COMPLETED", "ARCHIVED"], []);

    return (
        <div className="admin-page crm-page">

            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <h1>{t("crm.title", "CRM & Marketing")}</h1>
                    <p className="muted">{t("crm.subtitle", "Leads, Aktivitäten und Kampagnen im Überblick")}</p>
                </header>

                {accessDenied ? (
                    <section className="card">
                        <h2>{t("crm.featureDisabledTitle", "CRM nicht freigeschaltet")}</h2>
                        <p className="muted">
                            {t("crm.featureDisabledHint", "Bitte wenden Sie sich an den Chrono-Support, um das CRM-Modul zu aktivieren.")}
                        </p>
                    </section>
                ) : (
                    <>
                        <section className="card-grid">
                            <article className="card">
                                <h2>{t("crm.customerOverview", "Kunden")}</h2>
                                <ul className="list-unstyled">
                                    {customers.map((customer) => (
                                        <li key={customer.id}>
                                            <button
                                                type="button"
                                                className={`link-button${selectedCustomer?.id === customer.id ? " active" : ""}`}
                                                onClick={() => loadCustomerDetails(customer)}
                                            >
                                                {customer.name}
                                            </button>
                                        </li>
                                    ))}
                                    {customers.length === 0 && <li>{t("crm.noCustomers", "Keine Kunden")}</li>}
                                </ul>
                            </article>
                            {selectedCustomer && (
                                <article className="card customer-detail">
                                    <header>
                                        <h2>{selectedCustomer.name}</h2>
                                        <button
                                            type="button"
                                            className="ghost"
                                            onClick={() => {
                                                setSelectedCustomer(null);
                                                setCustomerContacts([]);
                                                setCustomerActivities([]);
                                                setCustomerAddresses([]);
                                                setCustomerDocuments([]);
                                                resetCustomerForms();
                                            }}
                                        >
                                            {t("common.close", "Schließen")}
                                        </button>
                                    </header>
                                    <div className="detail-grid">
                                        <section>
                                            <h3>{t("crm.addresses", "Adressen")}</h3>
                                            <ul className="list-unstyled">
                                                {customerAddresses.map((address) => (
                                                    <li key={address.id}>
                                                        <div>
                                                            <strong>{address.type}</strong>: {address.street}, {address.postalCode} {address.city}
                                                        </div>
                                                        <div className="action-row">
                                                            <button
                                                                type="button"
                                                                className="link-button"
                                                                onClick={() => setAddressForm({ id: address.id, street: address.street ?? "", postalCode: address.postalCode ?? "", city: address.city ?? "", country: address.country ?? "", type: address.type ?? "OFFICE" })}
                                                            >
                                                                {t("common.edit", "Bearbeiten")}
                                                            </button>
                                                            <button type="button" className="link-button danger" onClick={() => handleAddressDelete(address.id)}>{t("common.delete", "Löschen")}</button>
                                                        </div>
                                                    </li>
                                                ))}
                                                {customerAddresses.length === 0 && <li>{t("crm.noAddresses", "Keine Adressen")}</li>}
                                            </ul>
                                            <form className="form-grid" onSubmit={handleAddressSubmit}>
                                                <label>
                                                    {t("crm.street", "Straße")}
                                                    <input type="text" value={addressForm.street} onChange={(e) => setAddressForm({ ...addressForm, street: e.target.value })} required />
                                                </label>
                                                <label>
                                                    {t("crm.postalCode", "PLZ")}
                                                    <input type="text" value={addressForm.postalCode} onChange={(e) => setAddressForm({ ...addressForm, postalCode: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.city", "Ort")}
                                                    <input type="text" value={addressForm.city} onChange={(e) => setAddressForm({ ...addressForm, city: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.country", "Land")}
                                                    <input type="text" value={addressForm.country} onChange={(e) => setAddressForm({ ...addressForm, country: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.addressType", "Typ")}
                                                    <select value={addressForm.type} onChange={(e) => setAddressForm({ ...addressForm, type: e.target.value })}>
                                                        <option value="OFFICE">{t("crm.office", "Büro")}</option>
                                                        <option value="BILLING">{t("crm.billing", "Rechnung")}</option>
                                                        <option value="SHIPPING">{t("crm.shipping", "Versand")}</option>
                                                    </select>
                                                </label>
                                                <div className="form-actions">
                                                    <button type="submit" className="primary">{addressForm.id ? t("common.update", "Aktualisieren") : t("common.add", "Hinzufügen")}</button>
                                                    {addressForm.id && <button type="button" className="ghost" onClick={() => setAddressForm({ id: null, street: "", postalCode: "", city: "", country: "", type: "OFFICE" })}>{t("common.cancel", "Abbrechen")}</button>}
                                                </div>
                                            </form>
                                        </section>
                                        <section>
                                            <h3>{t("crm.contacts", "Kontakte")}</h3>
                                            <ul className="list-unstyled">
                                                {customerContacts.map((contact) => (
                                                    <li key={contact.id}>
                                                        <div>
                                                            <strong>{contact.firstName} {contact.lastName}</strong> – {contact.email}
                                                            {contact.role && <span className="muted"> ({contact.role})</span>}
                                                        </div>
                                                        <div className="action-row">
                                                            <button
                                                                type="button"
                                                                className="link-button"
                                                                onClick={() => setContactForm({ id: contact.id, firstName: contact.firstName ?? "", lastName: contact.lastName ?? "", email: contact.email ?? "", phone: contact.phone ?? "", role: contact.role ?? "" })}
                                                            >
                                                                {t("common.edit", "Bearbeiten")}
                                                            </button>
                                                            <button type="button" className="link-button danger" onClick={() => handleContactDelete(contact.id)}>{t("common.delete", "Löschen")}</button>
                                                        </div>
                                                    </li>
                                                ))}
                                                {customerContacts.length === 0 && <li>{t("crm.noContacts", "Keine Kontakte")}</li>}
                                            </ul>
                                            <form className="form-grid" onSubmit={handleContactSubmit}>
                                                <label>
                                                    {t("crm.firstName", "Vorname")}
                                                    <input type="text" value={contactForm.firstName} onChange={(e) => setContactForm({ ...contactForm, firstName: e.target.value })} required />
                                                </label>
                                                <label>
                                                    {t("crm.lastName", "Nachname")}
                                                    <input type="text" value={contactForm.lastName} onChange={(e) => setContactForm({ ...contactForm, lastName: e.target.value })} required />
                                                </label>
                                                <label>
                                                    Email
                                                    <input type="email" value={contactForm.email} onChange={(e) => setContactForm({ ...contactForm, email: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.phone", "Telefon")}
                                                    <input type="text" value={contactForm.phone} onChange={(e) => setContactForm({ ...contactForm, phone: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.role", "Funktion")}
                                                    <input type="text" value={contactForm.role} onChange={(e) => setContactForm({ ...contactForm, role: e.target.value })} />
                                                </label>
                                                <div className="form-actions">
                                                    <button type="submit" className="primary">{contactForm.id ? t("common.update", "Aktualisieren") : t("common.add", "Hinzufügen")}</button>
                                                    {contactForm.id && <button type="button" className="ghost" onClick={() => setContactForm({ id: null, firstName: "", lastName: "", email: "", phone: "", role: "" })}>{t("common.cancel", "Abbrechen")}</button>}
                                                </div>
                                            </form>
                                        </section>
                                        <section className="activities">
                                            <h3>{t("crm.activities", "Aktivitäten")}</h3>
                                            <ul className="list-unstyled">
                                                {customerActivities.map((activity) => (
                                                    <li key={activity.id}>
                                                        <div>
                                                            <strong>{activity.type}</strong> – {activity.notes}
                                                            {activity.createdAt && <span className="muted"> ({formatDateTime(activity.createdAt)})</span>}
                                                        </div>
                                                        <div className="action-row">
                                                            <button
                                                                type="button"
                                                                className="link-button"
                                                                onClick={() => setActivityForm({ id: activity.id, notes: activity.notes ?? "", timestamp: activity.createdAt ? activity.createdAt.replace("Z", "").slice(0, 16) : "", contactId: activity.contactId ?? "", type: activity.type ?? "NOTE" })}
                                                            >
                                                                {t("common.edit", "Bearbeiten")}
                                                            </button>
                                                            <button type="button" className="link-button danger" onClick={() => handleActivityDelete(activity.id)}>{t("common.delete", "Löschen")}</button>
                                                        </div>
                                                    </li>
                                                ))}
                                                {customerActivities.length === 0 && <li>{t("crm.noActivities", "Keine Aktivitäten")}</li>}
                                            </ul>
                                            <form className="form-grid" onSubmit={handleActivitySubmit}>
                                                <label>
                                                    {t("crm.type", "Typ")}
                                                    <select value={activityForm.type} onChange={(e) => setActivityForm({ ...activityForm, type: e.target.value })}>
                                                        <option value="NOTE">{t("crm.note", "Notiz")}</option>
                                                        <option value="CALL">{t("crm.call", "Anruf")}</option>
                                                        <option value="EMAIL">Email</option>
                                                        <option value="MEETING">{t("crm.meeting", "Meeting")}</option>
                                                    </select>
                                                </label>
                                                <label>
                                                    {t("crm.timestamp", "Zeitpunkt")}
                                                    <input type="datetime-local" value={activityForm.timestamp} onChange={(e) => setActivityForm({ ...activityForm, timestamp: e.target.value })} />
                                                </label>
                                                <label>
                                                    {t("crm.contact", "Kontakt")}
                                                    <select value={activityForm.contactId} onChange={(e) => setActivityForm({ ...activityForm, contactId: e.target.value })}>
                                                        <option value="">{t("crm.optional", "Optional")}</option>
                                                        {customerContacts.map((contact) => (
                                                            <option key={contact.id} value={contact.id}>{contact.firstName} {contact.lastName}</option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label className="full-width">
                                                    {t("crm.notes", "Notizen")}
                                                    <textarea value={activityForm.notes} onChange={(e) => setActivityForm({ ...activityForm, notes: e.target.value })} required />
                                                </label>
                                                <div className="form-actions">
                                                    <button type="submit" className="primary">{activityForm.id ? t("common.update", "Aktualisieren") : t("common.add", "Hinzufügen")}</button>
                                                    {activityForm.id && <button type="button" className="ghost" onClick={() => setActivityForm({ id: null, notes: "", timestamp: "", contactId: "", type: "NOTE" })}>{t("common.cancel", "Abbrechen")}</button>}
                                                </div>
                                            </form>
                                        </section>
                                        <section>
                                            <h3>{t("crm.documents", "Dokumente")}</h3>
                                            <ul className="list-unstyled">
                                                {customerDocuments.map((document) => (
                                                    <li key={document.id}>
                                                        <div>
                                                            <strong>{document.fileName}</strong>
                                                            {document.uploadedBy && <span className="muted"> ({document.uploadedBy})</span>}
                                                        </div>
                                                        <div className="muted">
                                                            {document.uploadedAt ? formatDateTime(document.uploadedAt) : t("crm.noTimestamp", "Kein Zeitstempel")}
                                                        </div>
                                                        <div className="action-row">
                                                            {document.url && (
                                                                <a className="link-button" href={document.url} target="_blank" rel="noreferrer">
                                                                    {t("crm.openDocument", "Öffnen")}
                                                                </a>
                                                            )}
                                                            <button type="button" className="link-button danger" onClick={() => handleDocumentDelete(document.id)}>{t("common.delete", "Löschen")}</button>
                                                        </div>
                                                    </li>
                                                ))}
                                                {customerDocuments.length === 0 && <li>{t("crm.noDocuments", "Keine Dokumente")}</li>}
                                            </ul>
                                            <form className="form-grid" onSubmit={handleDocumentSubmit}>
                                                <label>
                                                    {t("crm.documentName", "Dateiname")}
                                                    <input type="text" value={documentForm.fileName} onChange={(e) => setDocumentForm({ ...documentForm, fileName: e.target.value })} required />
                                                </label>
                                                <label className="full-width">
                                                    {t("crm.documentUrl", "Link (optional)")}
                                                    <input type="url" value={documentForm.url} onChange={(e) => setDocumentForm({ ...documentForm, url: e.target.value })} />
                                                </label>
                                                <div className="form-actions">
                                                    <button type="submit" className="primary">{t("crm.addDocument", "Dokument hinzufügen")}</button>
                                                </div>
                                            </form>
                                        </section>
                                    </div>
                                </article>
                            )}
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <h2>{t("crm.leadList", "Leads")}</h2>
                                <select value={leadFilter} onChange={(e) => setLeadFilter(e.target.value)}>
                                    {leadStatuses.map((status) => (
                                        <option key={status} value={status}>{status}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="two-column">
                                <ul className="list-unstyled">
                                    {leads.map((lead) => (
                                        <li key={lead.id}>
                                            <div>
                                                <strong>{lead.companyName || lead.contactName}</strong> – {lead.email}
                                            </div>
                                            <div className="action-row">
                                                <label>
                                                    {t("crm.status", "Status")}
                                                    <select value={lead.status} onChange={(e) => handleLeadStatusChange(lead.id, e.target.value)}>
                                                        {leadStatuses.map((status) => (
                                                            <option key={status} value={status}>{status}</option>
                                                        ))}
                                                    </select>
                                                </label>
                                            </div>
                                        </li>
                                    ))}
                                    {leads.length === 0 && <li>{t("crm.noLeads", "Keine Leads")}</li>}
                                </ul>
                                <form className="form-grid" onSubmit={handleLeadSubmit}>
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
                                                <option key={status} value={status}>{status}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <button type="submit" className="primary">{t("crm.addLead", "Lead anlegen")}</button>
                                </form>
                            </div>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <h2>{t("crm.opportunityList", "Opportunities")}</h2>
                                <select value={oppFilter} onChange={(e) => setOppFilter(e.target.value)}>
                                    {opportunityStages.map((stage) => (
                                        <option key={stage} value={stage}>{stage}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="two-column">
                                <ul className="list-unstyled">
                                    {opportunities.map((opportunity) => (
                                        <li key={opportunity.id}>
                                            <div>
                                                <strong>{opportunity.title}</strong> – CHF {Number(opportunity.value ?? 0).toFixed(2)}
                                            </div>
                                            <div className="action-row">
                                                <label>
                                                    {t("crm.stage", "Phase")}
                                                    <select value={opportunity.stage} onChange={(e) => handleOpportunityStageChange(opportunity.id, e.target.value)}>
                                                        {opportunityStages.map((stage) => (
                                                            <option key={stage} value={stage}>{stage}</option>
                                                        ))}
                                                    </select>
                                                </label>
                                            </div>
                                        </li>
                                    ))}
                                    {opportunities.length === 0 && <li>{t("crm.noOpportunities", "Keine Opportunities")}</li>}
                                </ul>
                                <form className="form-grid" onSubmit={handleOpportunitySubmit}>
                                    <label>
                                        {t("crm.title", "Titel")}
                                        <input type="text" value={opportunityForm.title} onChange={(e) => setOpportunityForm({ ...opportunityForm, title: e.target.value })} required />
                                    </label>
                                    <label>
                                        {t("crm.value", "Wert")}
                                        <input type="number" step="0.01" value={opportunityForm.value} onChange={(e) => setOpportunityForm({ ...opportunityForm, value: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.probability", "Wahrscheinlichkeit")}
                                        <input type="number" step="0.01" value={opportunityForm.probability} onChange={(e) => setOpportunityForm({ ...opportunityForm, probability: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.stage", "Phase")}
                                        <select value={opportunityForm.stage} onChange={(e) => setOpportunityForm({ ...opportunityForm, stage: e.target.value })}>
                                            {opportunityStages.map((stage) => (
                                                <option key={stage} value={stage}>{stage}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <button type="submit" className="primary">{t("crm.addOpportunity", "Opportunity anlegen")}</button>
                                </form>
                            </div>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <h2>{t("crm.campaignList", "Kampagnen")}</h2>
                                <select value={campaignFilter} onChange={(e) => setCampaignFilter(e.target.value)}>
                                    {campaignStatuses.map((status) => (
                                        <option key={status} value={status}>{status}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="two-column">
                                <ul className="list-unstyled">
                                    {campaigns.map((campaign) => {
                                        const channelLabel = campaign.channel || t("crm.noChannel", "Kein Kanal definiert");
                                        const budgetLabel = typeof campaign.budget === "number"
                                            ? `CHF ${campaign.budget.toLocaleString("de-CH")}`
                                            : t("crm.noBudget", "Kein Budget hinterlegt");
                                        let dateLabel = "";
                                        if (campaign.startDate && campaign.endDate) {
                                            dateLabel = `${campaign.startDate} – ${campaign.endDate}`;
                                        } else if (campaign.startDate) {
                                            dateLabel = `${t("crm.startDate", "Start")}: ${campaign.startDate}`;
                                        } else if (campaign.endDate) {
                                            dateLabel = `${t("crm.endDate", "Ende")}: ${campaign.endDate}`;
                                        }
                                        return (
                                            <li key={campaign.id}>
                                                <div>
                                                    <strong>{campaign.name}</strong> – {channelLabel}
                                                    <span className="muted"> · {budgetLabel}</span>
                                                </div>
                                                {dateLabel && <div className="muted">{dateLabel}</div>}
                                                <div className="action-row">
                                                    <label>
                                                        {t("crm.status", "Status")}
                                                        <select value={campaign.status} onChange={(e) => handleCampaignUpdate(campaign.id, e.target.value)}>
                                                            {campaignStatuses.map((status) => (
                                                                <option key={status} value={status}>{status}</option>
                                                            ))}
                                                        </select>
                                                    </label>
                                                </div>
                                            </li>
                                        );
                                    })}
                                    {campaigns.length === 0 && <li>{t("crm.noCampaigns", "Keine aktiven Kampagnen")}</li>}
                                </ul>
                                <form className="form-grid" onSubmit={handleCampaignSubmit}>
                                    <label>
                                        {t("crm.campaignName", "Name")}
                                        <input type="text" value={campaignForm.name} onChange={(e) => setCampaignForm({ ...campaignForm, name: e.target.value })} required />
                                    </label>
                                    <label>
                                        {t("crm.channel", "Kanal")}
                                        <input type="text" value={campaignForm.channel} onChange={(e) => setCampaignForm({ ...campaignForm, channel: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.startDate", "Start")}
                                        <input type="date" value={campaignForm.startDate} onChange={(e) => setCampaignForm({ ...campaignForm, startDate: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.endDate", "Ende")}
                                        <input type="date" value={campaignForm.endDate} onChange={(e) => setCampaignForm({ ...campaignForm, endDate: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.budget", "Budget")}
                                        <input type="number" min="0" step="1" value={campaignForm.budget} onChange={(e) => setCampaignForm({ ...campaignForm, budget: e.target.value })} />
                                    </label>
                                    <label>
                                        {t("crm.status", "Status")}
                                        <select value={campaignForm.status} onChange={(e) => setCampaignForm({ ...campaignForm, status: e.target.value })}>
                                            {campaignStatuses.map((status) => (
                                                <option key={status} value={status}>{status}</option>
                                            ))}
                                        </select>
                                    </label>
                                    <button type="submit" className="primary">{t("crm.addCampaign", "Kampagne anlegen")}</button>
                                </form>
                            </div>
                        </section>
                    </>
                )}
            </main>
        </div>
    );
};

export default CrmDashboard;
