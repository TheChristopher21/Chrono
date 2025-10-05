import React, { useCallback, useEffect, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/BankingOperationsPageScoped.css";


const initialAccount = {
    name: "",
    iban: "",
    bic: "",
    clearingNumber: ""
};

const BankingOperationsPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [accounts, setAccounts] = useState([]);
    const [batches, setBatches] = useState([]);
    const [batchMeta, setBatchMeta] = useState({ total: 0 });

    const [accountForm, setAccountForm] = useState(initialAccount);
    const [batchForm, setBatchForm] = useState({ bankAccountId: "", instructions: [] });
    const [instructionDraft, setInstructionDraft] = useState({ creditorName: "", creditorIban: "", amount: "", currency: "CHF", reference: "" });
    const [signatureForm, setSignatureForm] = useState({ documentType: "", documentPath: "", email: "" });
    const [messageForm, setMessageForm] = useState({ recipient: "", subject: "", body: "", transport: "EBICS" });

    const load = useCallback(async () => {
        try {
            const [accountRes, batchRes] = await Promise.all([
                api.get("/api/banking/accounts"),
                api.get("/api/banking/batches/open")
            ]);
            const normalize = (payload) => {
                if (!payload) {
                    return [];
                }
                if (Array.isArray(payload)) {
                    return payload;
                }
                if (Array.isArray(payload.content)) {
                    return payload.content;
                }
                return [];
            };
            const normalizedAccounts = normalize(accountRes.data);
            const normalizedBatches = normalize(batchRes.data);
            setAccounts(normalizedAccounts);
            setBatches(normalizedBatches);
            const totalBatches = typeof batchRes.data?.totalElements === "number"
                ? batchRes.data.totalElements
                : normalizedBatches.length;
            setBatchMeta({
                total: totalBatches,
            });
        } catch (error) {
            console.error("Failed to load banking data", error);
            notify(t("banking.loadError", "Bankdaten konnten nicht geladen werden."), "error");
        }
    }, [notify, t]);

    useEffect(() => {
        load();
    }, [load]);

    const handleAccountSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/banking/accounts", accountForm);
            notify(t("banking.accountSaved", "Bankkonto gespeichert."), "success");
            setAccountForm(initialAccount);
            await load();
        } catch (error) {
            console.error("Failed to save account", error);
            notify(t("banking.accountSaveFailed", "Bankkonto konnte nicht angelegt werden."), "error");
        }
    };

    const addInstruction = () => {
        if (!instructionDraft.creditorName || !instructionDraft.amount) {
            notify(t("banking.instructionIncomplete", "Bitte Empfänger und Betrag angeben."), "warning");
            return;
        }
        setBatchForm((prev) => ({
            ...prev,
            instructions: [...prev.instructions, { ...instructionDraft }]
        }));
        setInstructionDraft({ creditorName: "", creditorIban: "", amount: "", currency: "CHF", reference: "" });
    };

    const removeInstruction = (index) => {
        setBatchForm((prev) => ({
            ...prev,
            instructions: prev.instructions.filter((_, idx) => idx !== index)
        }));
    };

    const handleBatchSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                bankAccountId: Number(batchForm.bankAccountId),
                instructions: batchForm.instructions.map((instruction) => ({
                    creditorName: instruction.creditorName,
                    creditorIban: instruction.creditorIban,
                    amount: Number(instruction.amount || 0),
                    currency: instruction.currency || "CHF",
                    reference: instruction.reference || undefined
                }))
            };
            if (!payload.bankAccountId || payload.instructions.length === 0) {
                notify(t("banking.noInstructions", "Bitte ein Konto und mindestens eine Anweisung auswählen."), "warning");
                return;
            }
            await api.post("/api/banking/batches", payload);
            notify(t("banking.batchCreated", "Zahlungsauftrag erstellt."), "success");
            setBatchForm({ bankAccountId: "", instructions: [] });
            await load();
        } catch (error) {
            console.error("Failed to create payment batch", error);
            notify(t("banking.batchCreateFailed", "Zahlungsauftrag konnte nicht erstellt werden."), "error");
        }
    };

    const handleApprove = async (batchId) => {
        try {
            await api.post(`/api/banking/batches/${batchId}/approve`);
            notify(t("banking.batchApproved", "Auftrag freigegeben."), "success");
            await load();
        } catch (error) {
            console.error("Failed to approve batch", error);
            notify(t("banking.batchApproveFailed", "Freigabe fehlgeschlagen."), "error");
        }
    };

    const handleTransmit = async (batchId) => {
        const reference = prompt(t("banking.transmitReference", "Referenz für die Übermittlung"));
        if (reference === null) {
            return;
        }
        try {
            await api.post(`/api/banking/batches/${batchId}/transmit`, { reference });
            notify(t("banking.batchTransmitted", "Auftrag übermittelt."), "success");
            await load();
        } catch (error) {
            console.error("Failed to transmit batch", error);
            notify(t("banking.batchTransmitFailed", "Übermittlung fehlgeschlagen."), "error");
        }
    };

    const handleSignatureSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/banking/signatures", signatureForm);
            notify(t("banking.signatureRequested", "Signatur angefordert."), "success");
            setSignatureForm({ documentType: "", documentPath: "", email: "" });
        } catch (error) {
            console.error("Failed to request signature", error);
            notify(t("banking.signatureFailed", "Signaturanforderung fehlgeschlagen."), "error");
        }
    };

    const handleMessageSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/banking/messages", messageForm);
            notify(t("banking.messageSent", "Nachricht gesendet."), "success");
            setMessageForm({ recipient: "", subject: "", body: "", transport: "EBICS" });
        } catch (error) {
            console.error("Failed to send secure message", error);
            notify(t("banking.messageFailed", "Nachricht konnte nicht gesendet werden."), "error");
        }
    };

    return (
        <div className="admin-page banking-page">

            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <h1>{t("banking.title", "Zahlungsverkehr & Signaturen")}</h1>
                    <p className="muted">{t("banking.subtitle", "Bankkonten verwalten und Zahlungsfreigaben prüfen")}</p>
                </header>

                <section className="card">
                    <h2>{t("banking.newAccount", "Neues Bankkonto")}</h2>
                    <form className="form-grid" onSubmit={handleAccountSubmit}>
                        <label>
                            {t("banking.accountName", "Bezeichnung")}
                            <input
                                type="text"
                                value={accountForm.name}
                                onChange={(e) => setAccountForm({ ...accountForm, name: e.target.value })}
                                required
                            />
                        </label>
                        <label>
                            IBAN
                            <input
                                type="text"
                                value={accountForm.iban}
                                onChange={(e) => setAccountForm({ ...accountForm, iban: e.target.value })}
                                required
                            />
                        </label>
                        <label>
                            BIC
                            <input
                                type="text"
                                value={accountForm.bic}
                                onChange={(e) => setAccountForm({ ...accountForm, bic: e.target.value })}
                            />
                        </label>
                        <label>
                            {t("banking.clearing", "Clearing-Nr.")}
                            <input
                                type="text"
                                value={accountForm.clearingNumber}
                                onChange={(e) => setAccountForm({ ...accountForm, clearingNumber: e.target.value })}
                            />
                        </label>
                        <button type="submit" className="primary">
                            {t("banking.saveAccount", "Speichern")}
                        </button>
                    </form>
                </section>

                <section className="card">
                    <h2>{t("banking.accounts", "Bankkonten")}</h2>
                    <ul className="list-unstyled">
                        {accounts.map((account) => (
                            <li key={account.id}>
                                <strong>{account.name}</strong> – {account.iban}
                            </li>
                        ))}
                        {accounts.length === 0 && <li>{t("banking.noAccounts", "Keine Konten erfasst")}</li>}
                    </ul>
                </section>

                <section className="card">
                    <h2>{t("banking.createBatch", "Neuer Zahlungsauftrag")}</h2>
                    <form className="form-grid" onSubmit={handleBatchSubmit}>
                        <label>
                            {t("banking.account", "Bankkonto")}
                            <select value={batchForm.bankAccountId} onChange={(e) => setBatchForm({ ...batchForm, bankAccountId: e.target.value })} required>
                                <option value="">{t("banking.chooseAccount", "Konto wählen")}</option>
                                {accounts.map((account) => (
                                    <option key={account.id} value={account.id}>{account.name} – {account.iban}</option>
                                ))}
                            </select>
                        </label>
                        <div className="instruction-builder">
                            <h3>{t("banking.instructions", "Zahlungsanweisungen")}</h3>
                            <div className="instruction-form">
                                <input
                                    type="text"
                                    placeholder={t("banking.creditorName", "Empfängername")}
                                    value={instructionDraft.creditorName}
                                    onChange={(e) => setInstructionDraft({ ...instructionDraft, creditorName: e.target.value })}
                                />
                                <input
                                    type="text"
                                    placeholder="IBAN"
                                    value={instructionDraft.creditorIban}
                                    onChange={(e) => setInstructionDraft({ ...instructionDraft, creditorIban: e.target.value })}
                                />
                                <input
                                    type="number"
                                    step="0.01"
                                    placeholder={t("banking.amount", "Betrag")}
                                    value={instructionDraft.amount}
                                    onChange={(e) => setInstructionDraft({ ...instructionDraft, amount: e.target.value })}
                                />
                                <input
                                    type="text"
                                    placeholder={t("banking.currency", "Währung")}
                                    value={instructionDraft.currency}
                                    onChange={(e) => setInstructionDraft({ ...instructionDraft, currency: e.target.value })}
                                />
                                <input
                                    type="text"
                                    placeholder={t("banking.reference", "Verwendungszweck")}
                                    value={instructionDraft.reference}
                                    onChange={(e) => setInstructionDraft({ ...instructionDraft, reference: e.target.value })}
                                />
                                <button type="button" className="secondary" onClick={addInstruction}>{t("banking.addInstruction", "Hinzufügen")}</button>
                            </div>
                            <ul className="list-unstyled">
                                {batchForm.instructions.map((instruction, index) => (
                                    <li key={`${instruction.creditorName}-${index}`} className="instruction-item">
                                        <span>{instruction.creditorName} – {Number(instruction.amount || 0).toFixed(2)} {instruction.currency}</span>
                                        <button type="button" className="link-button danger" onClick={() => removeInstruction(index)}>{t("common.delete", "Löschen")}</button>
                                    </li>
                                ))}
                                {batchForm.instructions.length === 0 && <li>{t("banking.noInstructions", "Keine Anweisungen hinzugefügt")}</li>}
                            </ul>
                        </div>
                        <button type="submit" className="primary">{t("banking.saveBatch", "Auftrag anlegen")}</button>
                    </form>
                </section>

                <section className="card">
                    <div className="section-header">
                        <div>
                            <h2>{t("banking.pendingBatches", "Offene Zahlungsaufträge")}</h2>
                            <p className="muted">{t("banking.pendingBatchesHint", "Noch nicht übermittelte Zahlungen aus den Freigaben")}</p>
                        </div>
                        <span className="badge">{batchMeta.total}</span>
                    </div>

                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t("banking.batchId", "ID")}</th>
                                    <th>{t("banking.status", "Status")}</th>
                                    <th>{t("banking.created", "Erstellt")}</th>
                                    <th>{t("banking.instructions", "Anweisungen")}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {batches.map((batch) => (
                                    <tr key={batch.id}>
                                        <td>{batch.id}</td>
                                        <td>{batch.status}</td>
                                        <td>{batch.createdAt}</td>
                                        <td>
                                            {batch.instructions?.length ?? 0}
                                            <div className="action-row">
                                                <button type="button" className="secondary" onClick={() => handleApprove(batch.id)}>
                                                    {t("banking.approve", "Freigeben")}
                                                </button>
                                                <button type="button" className="ghost" onClick={() => handleTransmit(batch.id)}>
                                                    {t("banking.transmit", "Übermitteln")}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                                {batches.length === 0 && (
                                    <tr>
                                        <td colSpan="4">{t("banking.noBatches", "Keine offenen Zahlungsaufträge")}</td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("banking.signature", "Digitale Signatur")}</h2>
                        <form className="form-grid" onSubmit={handleSignatureSubmit}>
                            <label>
                                {t("banking.documentType", "Dokumenttyp")}
                                <input type="text" value={signatureForm.documentType} onChange={(e) => setSignatureForm({ ...signatureForm, documentType: e.target.value })} required />
                            </label>
                            <label>
                                {t("banking.documentPath", "Dokumentpfad")}
                                <input type="text" value={signatureForm.documentPath} onChange={(e) => setSignatureForm({ ...signatureForm, documentPath: e.target.value })} required />
                            </label>
                            <label>
                                Email
                                <input type="email" value={signatureForm.email} onChange={(e) => setSignatureForm({ ...signatureForm, email: e.target.value })} required />
                            </label>
                            <button type="submit" className="primary">{t("banking.requestSignature", "Signatur anfordern")}</button>
                        </form>
                    </article>
                    <article className="card">
                        <h2>{t("banking.secureMessage", "Sichere Nachricht")}</h2>
                        <form className="form-grid" onSubmit={handleMessageSubmit}>
                            <label>
                                {t("banking.recipient", "Empfänger")}
                                <input type="text" value={messageForm.recipient} onChange={(e) => setMessageForm({ ...messageForm, recipient: e.target.value })} required />
                            </label>
                            <label>
                                {t("banking.subject", "Betreff")}
                                <input type="text" value={messageForm.subject} onChange={(e) => setMessageForm({ ...messageForm, subject: e.target.value })} required />
                            </label>
                            <label className="full-width">
                                {t("banking.message", "Nachricht")}
                                <textarea value={messageForm.body} onChange={(e) => setMessageForm({ ...messageForm, body: e.target.value })} required />
                            </label>
                            <label>
                                {t("banking.transport", "Kanal")}
                                <select value={messageForm.transport} onChange={(e) => setMessageForm({ ...messageForm, transport: e.target.value })}>
                                    <option value="EBICS">EBICS</option>
                                    <option value="SWIFT">SWIFT</option>
                                    <option value="EMAIL">Email</option>
                                </select>
                            </label>
                            <button type="submit" className="primary">{t("banking.send", "Senden")}</button>
                        </form>
                    </article>
                </section>
            </main>
        </div>
    );
};

export default BankingOperationsPage;
