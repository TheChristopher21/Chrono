import React, { useMemo, useState } from "react";

const EntitySlideOver = ({
    t,
    isOpen,
    entityType,
    entity,
    onClose,
    contacts,
    activities,
    addresses,
    documents,
    contactForm,
    setContactForm,
    activityForm,
    setActivityForm,
    addressForm,
    setAddressForm,
    documentForm,
    setDocumentForm,
    onContactSubmit,
    onContactDelete,
    onActivitySubmit,
    onActivityDelete,
    onAddressSubmit,
    onAddressDelete,
    onDocumentSubmit,
    onDocumentDelete,
    formatDateTime,
    resetForms
}) => {
    const [activeTab, setActiveTab] = useState("overview");

    const title = useMemo(() => {
        if (!entity) return "";
        if (entityType === "lead") return entity.companyName || entity.contactName || t("crm.lead", "Lead");
        if (entityType === "opportunity") return entity.title || t("crm.opportunity", "Opportunity");
        return entity.name || entity.companyName || t("crm.customer", "Kunde");
    }, [entity, entityType, t]);

    if (!isOpen || !entity) return null;

    return (
        <>
            <div className="slide-over-backdrop" onClick={onClose} />
            <aside className="slide-over" aria-live="polite">
                <header className="slide-over-header">
                    <div>
                        <p className="eyebrow">{entityType}</p>
                        <h3>{title}</h3>
                    </div>
                    <button className="ghost" onClick={onClose}>{t("common.close", "Schließen")}</button>
                </header>

                <div className="crm-tabs">
                    <button className={activeTab === "overview" ? "active" : ""} onClick={() => setActiveTab("overview")}>{t("crm.overview", "Überblick")}</button>
                    <button className={activeTab === "contacts" ? "active" : ""} onClick={() => setActiveTab("contacts")}>{t("crm.contacts", "Kontakte")}</button>
                    <button className={activeTab === "activities" ? "active" : ""} onClick={() => setActiveTab("activities")}>{t("crm.activities", "Aktivitäten")}</button>
                    <button className={activeTab === "documents" ? "active" : ""} onClick={() => setActiveTab("documents")}>{t("crm.documents", "Dokumente")}</button>
                    <button className={activeTab === "addresses" ? "active" : ""} onClick={() => setActiveTab("addresses")}>{t("crm.addresses", "Adressen")}</button>
                </div>

                <div className="slide-over-content">
                    {activeTab === "overview" && (
                        <div className="stack">
                            <div className="muted tiny">ID: {entity.id}</div>
                            {entityType === "opportunity" && (
                                <>
                                    <div>{t("crm.stage", "Phase")}: {entity.stage}</div>
                                    <div>{t("crm.value", "Wert")}: {entity.value ? `CHF ${entity.value.toLocaleString("de-CH")}` : "-"}</div>
                                </>
                            )}
                            {entityType === "lead" && (
                                <>
                                    <div>{t("crm.status", "Status")}: {entity.status}</div>
                                    <div>{t("crm.email", "E-Mail")}: {entity.email}</div>
                                </>
                            )}
                            {entityType === "customer" && (
                                <>
                                    <div>{t("crm.owner", "Owner")}: {entity.ownerName || entity.owner || t("crm.noOwner", "Kein Owner")}</div>
                                    <div>{t("crm.segment", "Segment")}: {entity.segment || "-"}</div>
                                </>
                            )}
                        </div>
                    )}

                    {entityType === "customer" && activeTab === "contacts" && (
                        <div className="stack">
                            <ul className="list-unstyled">
                                {contacts.map((contact) => (
                                    <li key={contact.id}>
                                        <div><strong>{contact.firstName} {contact.lastName}</strong></div>
                                        <div className="muted">{contact.email}</div>
                                        <div className="action-row">
                                            <button type="button" className="link-button danger" onClick={() => onContactDelete(contact.id)}>{t("common.delete", "Löschen")}</button>
                                        </div>
                                    </li>
                                ))}
                                {contacts.length === 0 && <li className="muted">{t("crm.noContacts", "Keine Kontakte")}</li>}
                            </ul>
                            <form className="form-grid" onSubmit={onContactSubmit}>
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
                                    <input type="email" value={contactForm.email} onChange={(e) => setContactForm({ ...contactForm, email: e.target.value })} required />
                                </label>
                                <label>
                                    {t("crm.phone", "Telefon")}
                                    <input type="text" value={contactForm.phone} onChange={(e) => setContactForm({ ...contactForm, phone: e.target.value })} />
                                </label>
                                <div className="form-actions">
                                    <button type="submit" className="primary">{t("crm.saveContact", "Kontakt speichern")}</button>
                                    <button type="button" className="ghost" onClick={resetForms}>{t("common.cancel", "Abbrechen")}</button>
                                </div>
                            </form>
                        </div>
                    )}

                    {entityType === "customer" && activeTab === "activities" && (
                        <div className="stack">
                            <ul className="list-unstyled">
                                {activities.map((activity) => (
                                    <li key={activity.id}>
                                        <div><strong>{activity.type}</strong></div>
                                        <div className="muted">{activity.timestamp ? formatDateTime(activity.timestamp) : t("crm.noTimestamp", "Kein Zeitstempel")}</div>
                                        <p>{activity.notes}</p>
                                        <button type="button" className="link-button danger" onClick={() => onActivityDelete(activity.id)}>{t("common.delete", "Löschen")}</button>
                                    </li>
                                ))}
                                {activities.length === 0 && <li className="muted">{t("crm.noActivities", "Keine Aktivitäten")}</li>}
                            </ul>
                            <form className="form-grid" onSubmit={onActivitySubmit}>
                                <label>
                                    {t("crm.type", "Typ")}
                                    <select value={activityForm.type} onChange={(e) => setActivityForm({ ...activityForm, type: e.target.value })}>
                                        <option value="NOTE">{t("crm.note", "Notiz")}</option>
                                        <option value="CALL">{t("crm.call", "Anruf")}</option>
                                        <option value="MEETING">{t("crm.meeting", "Meeting")}</option>
                                    </select>
                                </label>
                                <label>
                                    {t("crm.timestamp", "Zeitstempel")}
                                    <input type="datetime-local" value={activityForm.timestamp} onChange={(e) => setActivityForm({ ...activityForm, timestamp: e.target.value })} />
                                </label>
                                <label>
                                    {t("crm.notes", "Notizen")}
                                    <textarea value={activityForm.notes} onChange={(e) => setActivityForm({ ...activityForm, notes: e.target.value })} required />
                                </label>
                                <div className="form-actions">
                                    <button type="submit" className="primary">{t("crm.saveActivity", "Aktivität speichern")}</button>
                                    <button type="button" className="ghost" onClick={resetForms}>{t("common.cancel", "Abbrechen")}</button>
                                </div>
                            </form>
                        </div>
                    )}

                    {entityType === "customer" && activeTab === "documents" && (
                        <div className="stack">
                            <ul className="list-unstyled">
                                {documents.map((document) => (
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
                                            <button type="button" className="link-button danger" onClick={() => onDocumentDelete(document.id)}>{t("common.delete", "Löschen")}</button>
                                        </div>
                                    </li>
                                ))}
                                {documents.length === 0 && <li className="muted">{t("crm.noDocuments", "Keine Dokumente")}</li>}
                            </ul>
                            <form className="form-grid" onSubmit={onDocumentSubmit}>
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
                        </div>
                    )}

                    {entityType === "customer" && activeTab === "addresses" && (
                        <div className="stack">
                            <ul className="list-unstyled">
                                {addresses.map((address) => (
                                    <li key={address.id}>
                                        <div><strong>{address.type}</strong></div>
                                        <div className="muted">{address.street}, {address.postalCode} {address.city}</div>
                                        <div className="action-row">
                                            <button type="button" className="link-button danger" onClick={() => onAddressDelete(address.id)}>{t("common.delete", "Löschen")}</button>
                                        </div>
                                    </li>
                                ))}
                                {addresses.length === 0 && <li className="muted">{t("crm.noAddresses", "Keine Adressen")}</li>}
                            </ul>
                            <form className="form-grid" onSubmit={onAddressSubmit}>
                                <label>
                                    {t("crm.type", "Typ")}
                                    <select value={addressForm.type} onChange={(e) => setAddressForm({ ...addressForm, type: e.target.value })}>
                                        <option value="OFFICE">{t("crm.office", "Office")}</option>
                                        <option value="BILLING">{t("crm.billing", "Rechnung")}</option>
                                        <option value="SHIPPING">{t("crm.shipping", "Versand")}</option>
                                    </select>
                                </label>
                                <label>
                                    {t("crm.street", "Straße")}
                                    <input type="text" value={addressForm.street} onChange={(e) => setAddressForm({ ...addressForm, street: e.target.value })} required />
                                </label>
                                <label>
                                    {t("crm.postalCode", "PLZ")}
                                    <input type="text" value={addressForm.postalCode} onChange={(e) => setAddressForm({ ...addressForm, postalCode: e.target.value })} required />
                                </label>
                                <label>
                                    {t("crm.city", "Stadt")}
                                    <input type="text" value={addressForm.city} onChange={(e) => setAddressForm({ ...addressForm, city: e.target.value })} required />
                                </label>
                                <label>
                                    {t("crm.country", "Land")}
                                    <input type="text" value={addressForm.country} onChange={(e) => setAddressForm({ ...addressForm, country: e.target.value })} required />
                                </label>
                                <div className="form-actions">
                                    <button type="submit" className="primary">{t("crm.saveAddress", "Adresse speichern")}</button>
                                    <button type="button" className="ghost" onClick={resetForms}>{t("common.cancel", "Abbrechen")}</button>
                                </div>
                            </form>
                        </div>
                    )}
                </div>
            </aside>
        </>
    );
};

export default EntitySlideOver;
