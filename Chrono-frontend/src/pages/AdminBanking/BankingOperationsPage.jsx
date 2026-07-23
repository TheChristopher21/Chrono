import React, { useEffect, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/BankingOperationsPageScoped.css";

const initialAccount = {
    name: "",
    iban: "",
    bic: "",
    clearingNumber: "",
};

const initialInstructionDraft = {
    creditorName: "",
    creditorIban: "",
    creditorBic: "",
    amount: "",
    currency: "CHF",
    reference: "",
};

const initialSignatureForm = {
    documentType: "",
    documentPath: "",
    email: "",
};

const initialMessageForm = {
    recipient: "",
    subject: "",
    body: "",
    transport: "EBICS",
};

const initialTransmitState = {
    open: false,
    batchId: null,
    reference: "",
};

const tabConfig = [
    { id: "overview", label: "Übersicht" },
    { id: "payments", label: "Zahlungen" },
    { id: "signatures", label: "Signaturen" },
    { id: "messages", label: "Nachrichten" },
    { id: "accounts", label: "Konten" },
];

const batchFilterConfig = [
    { id: "ALL", label: "Alle" },
    { id: "PENDING_APPROVAL", label: "Freigaben" },
    { id: "APPROVED", label: "Bereit" },
    { id: "SENT", label: "Gesendet" },
    { id: "FAILED", label: "Fehler" },
];

const dateFormatter = new Intl.DateTimeFormat("de-CH", {
    dateStyle: "medium",
    timeStyle: "short",
});

const shortDateFormatter = new Intl.DateTimeFormat("de-CH", {
    dateStyle: "medium",
});

const currencyFormatter = new Intl.NumberFormat("de-CH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

const createInstructionId = () =>
    `instruction-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

const readPagedContent = (payload) => payload?.content ?? payload ?? [];

const toNumeric = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
};

const sumBy = (items, selector) =>
    items.reduce((total, item) => total + toNumeric(selector(item)), 0);

const statusTone = {
    DRAFT: "muted",
    PENDING_APPROVAL: "warning",
    APPROVED: "info",
    SENT: "success",
    FAILED: "danger",
    PENDING: "warning",
    IN_PROGRESS: "info",
    COMPLETED: "success",
};

const statusLabel = (status, t) => {
    switch (status) {
        case "DRAFT":
            return t("banking.statusDraft", "Entwurf");
        case "PENDING_APPROVAL":
            return t("banking.statusPendingApproval", "Freigabe ausstehend");
        case "APPROVED":
            return t("banking.statusApproved", "Freigegeben");
        case "SENT":
            return t("banking.statusSent", "Übermittelt");
        case "FAILED":
            return t("banking.statusFailed", "Fehlgeschlagen");
        case "PENDING":
            return t("banking.signaturePending", "Ausstehend");
        case "IN_PROGRESS":
            return t("banking.signatureInProgress", "In Arbeit");
        case "COMPLETED":
            return t("banking.signatureCompleted", "Abgeschlossen");
        default:
            return status ?? "–";
    }
};

const transportLabel = (transport) => {
    if (!transport) {
        return "–";
    }
    return transport.toUpperCase();
};

const formatDateTime = (value) => {
    if (!value) {
        return "–";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
};

const formatDate = (value) => {
    if (!value) {
        return "–";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : shortDateFormatter.format(date);
};

const formatMoney = (amount, currency = "CHF") => {
    if (amount === null || amount === undefined || amount === "") {
        return `0.00 ${currency}`;
    }
    return `${currencyFormatter.format(toNumeric(amount))} ${currency}`;
};

const mapAccountToForm = (account) => ({
    name: account?.name ?? "",
    iban: account?.iban ?? "",
    bic: account?.bic ?? "",
    clearingNumber: account?.clearingNumber ?? "",
});

const mapPayableToInstruction = (invoice) => ({
    localId: createInstructionId(),
    source: "vendor_invoice",
    vendorInvoiceId: invoice.id,
    customerInvoiceId: null,
    creditorName: invoice.vendorName ?? "",
    creditorIban: "",
    creditorBic: "",
    amount: `${invoice.openAmount ?? invoice.amount ?? ""}`,
    currency: invoice.currency ?? "CHF",
    reference: invoice.invoiceNumber ?? "",
});

const triggerXmlDownload = (content, filename) => {
    const blob = new Blob([content], { type: "application/xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
};

const BankingOperationsPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [loading, setLoading] = useState(true);
    const [refreshTick, setRefreshTick] = useState(0);
    const [activeTab, setActiveTab] = useState("overview");

    const [accounts, setAccounts] = useState([]);
    const [batches, setBatches] = useState([]);
    const [payables, setPayables] = useState([]);
    const [receivables, setReceivables] = useState([]);
    const [signatures, setSignatures] = useState([]);
    const [messages, setMessages] = useState([]);

    const [accountForm, setAccountForm] = useState(initialAccount);
    const [editingAccountId, setEditingAccountId] = useState(null);

    const [batchForm, setBatchForm] = useState({ bankAccountId: "", instructions: [] });
    const [instructionDraft, setInstructionDraft] = useState(initialInstructionDraft);
    const [batchStatusFilter, setBatchStatusFilter] = useState("ALL");
    const [selectedBatchId, setSelectedBatchId] = useState(null);
    const [transmitState, setTransmitState] = useState(initialTransmitState);

    const [signatureForm, setSignatureForm] = useState(initialSignatureForm);
    const [signatureUpload, setSignatureUpload] = useState({ uploading: false, fileName: "", size: 0 });
    const [selectedSignatureId, setSelectedSignatureId] = useState(null);

    const [messageForm, setMessageForm] = useState(initialMessageForm);
    const [selectedMessageId, setSelectedMessageId] = useState(null);

    useEffect(() => {
        let ignore = false;

        const loadWorkspace = async () => {
            setLoading(true);
            try {
                const [
                    accountRes,
                    batchRes,
                    payableRes,
                    receivableRes,
                    signatureRes,
                    messageRes,
                ] = await Promise.all([
                    api.get("/api/banking/accounts"),
                    api.get("/api/banking/batches"),
                    api.get("/api/accounting/payables/open", { params: { page: 0, size: 100 } }),
                    api.get("/api/accounting/receivables/open", { params: { page: 0, size: 100 } }),
                    api.get("/api/banking/signatures"),
                    api.get("/api/banking/messages"),
                ]);

                if (ignore) {
                    return;
                }

                const nextAccounts = accountRes.data ?? [];
                const nextBatches = batchRes.data ?? [];
                const nextPayables = readPagedContent(payableRes.data);
                const nextReceivables = readPagedContent(receivableRes.data);
                const nextSignatures = signatureRes.data ?? [];
                const nextMessages = messageRes.data ?? [];

                setAccounts(nextAccounts);
                setBatches(nextBatches);
                setPayables(nextPayables);
                setReceivables(nextReceivables);
                setSignatures(nextSignatures);
                setMessages(nextMessages);

                setSelectedBatchId((current) =>
                    nextBatches.some((batch) => batch.id === current) ? current : nextBatches[0]?.id ?? null
                );
                setSelectedSignatureId((current) =>
                    nextSignatures.some((signature) => signature.id === current) ? current : nextSignatures[0]?.id ?? null
                );
                setSelectedMessageId((current) =>
                    nextMessages.some((message) => message.id === current) ? current : nextMessages[0]?.id ?? null
                );
            } catch (error) {
                console.error("Failed to load banking workspace", error);
                notify(t("banking.loadError", "Bankdaten konnten nicht geladen werden."), "error");
            } finally {
                if (!ignore) {
                    setLoading(false);
                }
            }
        };

        loadWorkspace();

        return () => {
            ignore = true;
        };
    }, [notify, refreshTick, t]);

    const refreshWorkspace = () => setRefreshTick((value) => value + 1);

    const filteredBatches = batches.filter(
        (batch) => batchStatusFilter === "ALL" || batch.status === batchStatusFilter
    );

    const selectedBatch =
        batches.find((batch) => batch.id === selectedBatchId) ?? filteredBatches[0] ?? null;

    const selectedSignature =
        signatures.find((signature) => signature.id === selectedSignatureId) ?? signatures[0] ?? null;

    const selectedMessage =
        messages.find((message) => message.id === selectedMessageId) ?? messages[0] ?? null;

    const pendingBatches = batches.filter((batch) => batch.status === "PENDING_APPROVAL");
    const approvedBatches = batches.filter((batch) => batch.status === "APPROVED");
    const signatureInProgress = signatures.filter((signature) =>
        ["PENDING", "IN_PROGRESS"].includes(signature.status)
    );
    const deliveredMessages = messages.filter((message) => message.delivered);
    const openPayableAmount = sumBy(payables, (invoice) => invoice.openAmount ?? invoice.amount);
    const openReceivableAmount = sumBy(receivables, (invoice) => invoice.openAmount ?? invoice.amount);
    const draftTotalAmount = sumBy(batchForm.instructions, (instruction) => instruction.amount);
    const hasAccounts = accounts.length > 0;

    const resetAccountForm = () => {
        setAccountForm(initialAccount);
        setEditingAccountId(null);
    };

    const handleAccountSubmit = async (event) => {
        event.preventDefault();
        try {
            if (editingAccountId) {
                await api.put(`/api/banking/accounts/${editingAccountId}`, accountForm);
                notify(t("banking.accountUpdated", "Bankkonto aktualisiert."), "success");
            } else {
                await api.post("/api/banking/accounts", accountForm);
                notify(t("banking.accountSaved", "Bankkonto gespeichert."), "success");
            }
            resetAccountForm();
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to save account", error);
            notify(t("banking.accountSaveFailed", "Bankkonto konnte nicht gespeichert werden."), "error");
        }
    };

    const handleEditAccount = (account) => {
        setActiveTab("accounts");
        setEditingAccountId(account.id);
        setAccountForm(mapAccountToForm(account));
    };

    const handleDeleteAccount = async (account) => {
        if (!window.confirm(t("banking.deleteAccountConfirm", `Bankkonto ${account.name} wirklich löschen?`))) {
            return;
        }
        try {
            await api.delete(`/api/banking/accounts/${account.id}`);
            notify(t("banking.accountDeleted", "Bankkonto gelöscht."), "success");
            if (editingAccountId === account.id) {
                resetAccountForm();
            }
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to delete account", error);
            notify(t("banking.accountDeleteFailed", "Bankkonto konnte nicht gelöscht werden."), "error");
        }
    };

    const addManualInstruction = () => {
        if (!instructionDraft.creditorName || !instructionDraft.creditorIban || !instructionDraft.amount) {
            notify(
                t("banking.instructionIncomplete", "Bitte Empfänger, IBAN und Betrag angeben."),
                "warning"
            );
            return;
        }
        setBatchForm((current) => ({
            ...current,
            instructions: [
                ...current.instructions,
                {
                    localId: createInstructionId(),
                    source: "manual",
                    vendorInvoiceId: null,
                    customerInvoiceId: null,
                    ...instructionDraft,
                },
            ],
        }));
        setInstructionDraft(initialInstructionDraft);
    };

    const addPayableInstruction = (invoice) => {
        setBatchForm((current) => {
            const alreadyAdded = current.instructions.some(
                (instruction) => instruction.vendorInvoiceId === invoice.id
            );
            if (alreadyAdded) {
                notify(
                    t("banking.invoiceAlreadyInBatch", "Diese Kreditorenrechnung liegt bereits im Entwurf."),
                    "info"
                );
                return current;
            }
            return {
                ...current,
                instructions: [...current.instructions, mapPayableToInstruction(invoice)],
            };
        });
    };

    const updateInstruction = (localId, field, value) => {
        setBatchForm((current) => ({
            ...current,
            instructions: current.instructions.map((instruction) =>
                instruction.localId === localId
                    ? { ...instruction, [field]: value }
                    : instruction
            ),
        }));
    };

    const removeInstruction = (localId) => {
        setBatchForm((current) => ({
            ...current,
            instructions: current.instructions.filter((instruction) => instruction.localId !== localId),
        }));
    };

    const handleBatchSubmit = async (event) => {
        event.preventDefault();

        if (!batchForm.bankAccountId) {
            notify(t("banking.chooseAccountWarning", "Bitte zuerst ein Belastungskonto wählen."), "warning");
            return;
        }

        if (batchForm.instructions.length === 0) {
            notify(t("banking.noInstructions", "Bitte mindestens eine Zahlungsanweisung hinzufügen."), "warning");
            return;
        }

        const hasInvalidInstruction = batchForm.instructions.some(
            (instruction) =>
                !instruction.creditorName ||
                !instruction.creditorIban ||
                toNumeric(instruction.amount) <= 0
        );

        if (hasInvalidInstruction) {
            notify(
                t("banking.fixInstructionLines", "Bitte alle Zahlungszeilen mit Empfänger, IBAN und Betrag vervollständigen."),
                "warning"
            );
            return;
        }

        try {
            const payload = {
                bankAccountId: Number(batchForm.bankAccountId),
                instructions: batchForm.instructions.map((instruction) => ({
                    vendorInvoiceId: instruction.vendorInvoiceId ?? undefined,
                    customerInvoiceId: instruction.customerInvoiceId ?? undefined,
                    creditorName: instruction.creditorName,
                    creditorIban: instruction.creditorIban,
                    creditorBic: instruction.creditorBic || undefined,
                    amount: Number(instruction.amount),
                    currency: instruction.currency || "CHF",
                    reference: instruction.reference || undefined,
                })),
            };

            await api.post("/api/banking/batches", payload);
            notify(t("banking.batchCreated", "Zahlungsauftrag erstellt."), "success");
            setBatchForm({ bankAccountId: "", instructions: [] });
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to create batch", error);
            notify(t("banking.batchCreateFailed", "Zahlungsauftrag konnte nicht erstellt werden."), "error");
        }
    };

    const handleApprove = async (batchId) => {
        try {
            await api.post(`/api/banking/batches/${batchId}/approve`);
            notify(t("banking.batchApproved", "Auftrag freigegeben."), "success");
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to approve batch", error);
            notify(t("banking.batchApproveFailed", "Freigabe fehlgeschlagen."), "error");
        }
    };

    const openTransmitDialog = (batch) => {
        setTransmitState({
            open: true,
            batchId: batch.id,
            reference:
                batch.transmissionReference ||
                `BATCH-${batch.id}-${new Date().toISOString().slice(0, 10)}`,
        });
    };

    const closeTransmitDialog = () => setTransmitState(initialTransmitState);

    const handleTransmit = async (event) => {
        event.preventDefault();
        if (!transmitState.batchId || !transmitState.reference.trim()) {
            notify(t("banking.referenceRequired", "Bitte eine Referenz für die Übermittlung angeben."), "warning");
            return;
        }
        try {
            await api.post(`/api/banking/batches/${transmitState.batchId}/transmit`, {
                reference: transmitState.reference.trim(),
            });
            notify(t("banking.batchTransmitted", "Auftrag übermittelt."), "success");
            closeTransmitDialog();
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to transmit batch", error);
            notify(t("banking.batchTransmitFailed", "Übermittlung fehlgeschlagen."), "error");
        }
    };

    const handleDownloadPain001 = async (batchId) => {
        try {
            const response = await api.get(`/api/banking/pain001/${batchId}`, {
                responseType: "text",
            });
            triggerXmlDownload(response.data, `pain001-batch-${batchId}.xml`);
            notify(t("banking.painDownloaded", "pain.001 exportiert."), "success");
        } catch (error) {
            console.error("Failed to download pain.001", error);
            notify(t("banking.painDownloadFailed", "pain.001 konnte nicht geladen werden."), "error");
        }
    };

    const handleSignatureUpload = async (file) => {
        if (!file) {
            return;
        }
        setSignatureUpload({ uploading: true, fileName: file.name, size: file.size });
        try {
            const formData = new FormData();
            formData.append("file", file);
            const response = await api.post("/api/banking/signatures/upload", formData, {
                headers: { "Content-Type": "multipart/form-data" },
            });
            setSignatureForm((current) => ({
                ...current,
                documentPath: response.data?.documentPath ?? current.documentPath,
            }));
            notify(t("banking.documentUploaded", "Dokument hochgeladen."), "success");
        } catch (error) {
            console.error("Failed to upload signature document", error);
            notify(t("banking.documentUploadFailed", "Dokument konnte nicht hochgeladen werden."), "error");
        } finally {
            setSignatureUpload((current) => ({ ...current, uploading: false }));
        }
    };

    const handleSignatureSubmit = async (event) => {
        event.preventDefault();
        if (!signatureForm.documentPath) {
            notify(t("banking.signatureNeedsDocument", "Bitte zuerst ein Dokumentpfad oder einen Upload angeben."), "warning");
            return;
        }
        try {
            await api.post("/api/banking/signatures", signatureForm);
            notify(t("banking.signatureRequested", "Signatur angefordert."), "success");
            setSignatureForm(initialSignatureForm);
            setSignatureUpload({ uploading: false, fileName: "", size: 0 });
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to request signature", error);
            notify(t("banking.signatureFailed", "Signaturanforderung fehlgeschlagen."), "error");
        }
    };

    const refreshSignature = async (signatureId) => {
        try {
            await api.post(`/api/banking/signatures/${signatureId}/refresh`);
            notify(t("banking.signatureRefreshed", "Signaturstatus aktualisiert."), "success");
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to refresh signature", error);
            notify(t("banking.signatureRefreshFailed", "Signaturstatus konnte nicht aktualisiert werden."), "error");
        }
    };

    const completeSignature = async (signatureId) => {
        try {
            await api.post(`/api/banking/signatures/${signatureId}/complete`);
            notify(t("banking.signatureCompletedToast", "Signatur abgeschlossen."), "success");
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to complete signature", error);
            notify(t("banking.signatureCompleteFailed", "Signatur konnte nicht abgeschlossen werden."), "error");
        }
    };

    const handleMessageSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/banking/messages", messageForm);
            notify(t("banking.messageSent", "Nachricht gesendet."), "success");
            setMessageForm(initialMessageForm);
            refreshWorkspace();
        } catch (error) {
            console.error("Failed to send message", error);
            notify(t("banking.messageFailed", "Nachricht konnte nicht gesendet werden."), "error");
        }
    };

    return (
        <div className="admin-page banking-page">
            <Navbar />

            <main className="admin-content">
                <header className="admin-header banking-hero">
                    <div>
                        <span className="eyebrow">{t("banking.eyebrow", "Finanzworkspace")}</span>
                        <h1>{t("banking.title", "Zahlungsverkehr & Signaturen")}</h1>
                        <p className="muted">
                            {t(
                                "banking.subtitle",
                                "Konten verwalten, Zahlungsläufe steuern, Signaturen nachhalten und sichere Nachrichten dokumentieren."
                            )}
                        </p>
                    </div>

                    <div className="hero-actions">
                        <button type="button" className="secondary" onClick={refreshWorkspace}>
                            {t("common.refresh", "Aktualisieren")}
                        </button>
                        <button
                            type="button"
                            className={hasAccounts ? "ghost" : "primary"}
                            onClick={() => setActiveTab("accounts")}
                        >
                            {t("banking.createOwnAccount", "Eigenes Firmenkonto anlegen")}
                        </button>
                        <button
                            type="button"
                            className="ghost"
                            onClick={() => setActiveTab(hasAccounts ? "payments" : "accounts")}
                        >
                            {t("banking.newPaymentRun", "Neuer Zahlungsauftrag")}
                        </button>
                    </div>
                </header>

                <section className="overview-grid">
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricAccounts", "Bankkonten")}</span>
                        <strong className="metric-value">{accounts.length}</strong>
                        <span className="metric-hint">{t("banking.metricAccountsHint", "Hinterlegte Belastungskonten")}</span>
                    </article>
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricPending", "Offene Freigaben")}</span>
                        <strong className="metric-value">{pendingBatches.length}</strong>
                        <span className="metric-hint">{t("banking.metricPendingHint", "Warten auf Freigabe")}</span>
                    </article>
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricApproved", "Bereit zur Übermittlung")}</span>
                        <strong className="metric-value">{approvedBatches.length}</strong>
                        <span className="metric-hint">{t("banking.metricApprovedHint", "Freigegebene Zahlungsläufe")}</span>
                    </article>
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricSignatures", "Signaturen aktiv")}</span>
                        <strong className="metric-value">{signatureInProgress.length}</strong>
                        <span className="metric-hint">{t("banking.metricSignaturesHint", "PENDING oder IN_PROGRESS")}</span>
                    </article>
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricPayables", "Offene Kreditoren")}</span>
                        <strong className="metric-value">{formatMoney(openPayableAmount)}</strong>
                        <span className="metric-hint">{t("banking.metricPayablesHint", "Ausstehende Auszahlungen")}</span>
                    </article>
                    <article className="metric-card">
                        <span className="metric-label">{t("banking.metricReceivables", "Offene Debitoren")}</span>
                        <strong className="metric-value">{formatMoney(openReceivableAmount)}</strong>
                        <span className="metric-hint">{t("banking.metricReceivablesHint", "Erwartete Eingänge")}</span>
                    </article>
                </section>

                <nav className="tab-bar" aria-label={t("banking.tabs", "Banking Navigation")}>
                    {tabConfig.map((tab) => (
                        <button
                            key={tab.id}
                            type="button"
                            className={`tab ${activeTab === tab.id ? "active" : ""}`}
                            onClick={() => setActiveTab(tab.id)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </nav>

                {loading && (
                    <section className="card">
                        <p className="muted">{t("banking.loading", "Banking-Workspace wird geladen...")}</p>
                    </section>
                )}

                {!loading && activeTab === "overview" && (
                    <div className="workspace-grid">
                        {!hasAccounts && (
                            <section className="card setup-card">
                                <div className="section-header">
                                    <div>
                                        <h2>{t("banking.firstAccountTitle", "Erstes Firmenkonto anlegen")}</h2>
                                        <p className="muted">
                                            {t(
                                                "banking.firstAccountHint",
                                                "Lege zuerst euer eigenes Belastungskonto an. Erst danach koennen Zahlungslaeufe, API-Versand und EBICS-Bankexport sauber genutzt werden."
                                            )}
                                        </p>
                                    </div>
                                </div>
                                <div className="hero-actions">
                                    <button type="button" className="primary" onClick={() => setActiveTab("accounts")}>
                                        {t("banking.createOwnAccount", "Eigenes Firmenkonto anlegen")}
                                    </button>
                                    <button type="button" className="secondary" onClick={() => setActiveTab("payments")}>
                                        {t("banking.openPaymentsWorkspace", "Zahlungsflow ansehen")}
                                    </button>
                                </div>
                            </section>
                        )}

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.cashflowRadar", "Cashflow Radar")}</h2>
                                    <p className="muted">{t("banking.cashflowHint", "Fällige Rechnungen und erwartete Eingänge auf einen Blick")}</p>
                                </div>
                            </div>
                            <div className="card-grid">
                                <article className="subcard">
                                    <div className="subcard-header">
                                        <h3>{t("banking.openPayables", "Offene Kreditoren")}</h3>
                                        <span className="badge neutral">{payables.length}</span>
                                    </div>
                                    <ul className="detail-list">
                                        {payables.slice(0, 5).map((invoice) => (
                                            <li key={invoice.id}>
                                                <div>
                                                    <strong>{invoice.invoiceNumber}</strong>
                                                    <span>{invoice.vendorName}</span>
                                                </div>
                                                <div className="align-right">
                                                    <strong>{formatMoney(invoice.openAmount ?? invoice.amount, invoice.currency)}</strong>
                                                    <span>{formatDate(invoice.dueDate)}</span>
                                                </div>
                                            </li>
                                        ))}
                                        {payables.length === 0 && (
                                            <li>{t("banking.noPayables", "Keine offenen Kreditoren vorhanden.")}</li>
                                        )}
                                    </ul>
                                </article>
                                <article className="subcard">
                                    <div className="subcard-header">
                                        <h3>{t("banking.openReceivables", "Offene Debitoren")}</h3>
                                        <span className="badge neutral">{receivables.length}</span>
                                    </div>
                                    <ul className="detail-list">
                                        {receivables.slice(0, 5).map((invoice) => (
                                            <li key={invoice.id}>
                                                <div>
                                                    <strong>{invoice.invoiceNumber}</strong>
                                                    <span>{invoice.customerName}</span>
                                                </div>
                                                <div className="align-right">
                                                    <strong>{formatMoney(invoice.openAmount ?? invoice.amount, invoice.currency)}</strong>
                                                    <span>{formatDate(invoice.dueDate)}</span>
                                                </div>
                                            </li>
                                        ))}
                                        {receivables.length === 0 && (
                                            <li>{t("banking.noReceivables", "Keine offenen Debitoren vorhanden.")}</li>
                                        )}
                                    </ul>
                                </article>
                            </div>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.latestRuns", "Neueste Zahlungsläufe")}</h2>
                                    <p className="muted">{t("banking.latestRunsHint", "Status, Referenz und Gesamtvolumen der letzten Batches")}</p>
                                </div>
                            </div>
                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t("banking.batch", "Batch")}</th>
                                            <th>{t("banking.account", "Konto")}</th>
                                            <th>{t("banking.status", "Status")}</th>
                                            <th>{t("banking.created", "Erstellt")}</th>
                                            <th className="numeric">{t("banking.amount", "Volumen")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {batches.slice(0, 6).map((batch) => (
                                            <tr key={batch.id}>
                                                <td>#{batch.id}</td>
                                                <td>{batch.bankAccountName || batch.bankAccountIban || "–"}</td>
                                                <td>
                                                    <span className={`status-pill ${statusTone[batch.status] || "muted"}`}>
                                                        {statusLabel(batch.status, t)}
                                                    </span>
                                                </td>
                                                <td>{formatDateTime(batch.createdAt)}</td>
                                                <td className="numeric">{formatMoney(batch.totalAmount)}</td>
                                            </tr>
                                        ))}
                                        {batches.length === 0 && (
                                            <tr>
                                                <td colSpan="5">{t("banking.noBatches", "Keine Zahlungsaufträge vorhanden.")}</td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.signatureMonitor", "Signaturmonitor")}</h2>
                                    <p className="muted">{t("banking.signatureMonitorHint", "Aktive und zuletzt abgeschlossene Signaturanfragen")}</p>
                                </div>
                            </div>
                            <ul className="detail-list">
                                {signatures.slice(0, 5).map((signature) => (
                                    <li key={signature.id}>
                                        <div>
                                            <strong>{signature.documentType || t("banking.unknownDocument", "Unbekanntes Dokument")}</strong>
                                            <span>{signature.signerEmail}</span>
                                        </div>
                                        <div className="align-right">
                                            <span className={`status-pill ${statusTone[signature.status] || "muted"}`}>
                                                {statusLabel(signature.status, t)}
                                            </span>
                                            <span>{formatDateTime(signature.requestedAt)}</span>
                                        </div>
                                    </li>
                                ))}
                                {signatures.length === 0 && (
                                    <li>{t("banking.noSignatures", "Noch keine Signaturanfragen vorhanden.")}</li>
                                )}
                            </ul>
                        </section>
                    </div>
                )}

                {!loading && activeTab === "payments" && (
                    <div className="workspace-grid">
                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.createBatch", "Zahlungsauftrag erstellen")}</h2>
                                    <p className="muted">{t("banking.createBatchHint", "Manuelle Zeilen oder offene Kreditoren sammeln und als Batch speichern")}</p>
                                </div>
                                <span className="badge neutral">{formatMoney(draftTotalAmount)}</span>
                            </div>

                            {!hasAccounts && (
                                <div className="callout setup-callout">
                                    <strong>{t("banking.accountRequired", "Eigenes Firmenkonto fehlt noch.")}</strong>
                                    <p className="muted">
                                        {t(
                                            "banking.accountRequiredHint",
                                            "Bevor ein Batch ueber API oder EBICS exportiert werden kann, braucht ihr zuerst euer eigenes Belastungskonto."
                                        )}
                                    </p>
                                    <div className="inline-actions">
                                        <button type="button" className="primary" onClick={() => setActiveTab("accounts")}>
                                            {t("banking.createOwnAccount", "Eigenes Firmenkonto anlegen")}
                                        </button>
                                    </div>
                                </div>
                            )}

                            <form className="form-grid" onSubmit={handleBatchSubmit}>
                                <label>
                                    {t("banking.account", "Belastungskonto")}
                                    <select
                                        value={batchForm.bankAccountId}
                                        onChange={(event) =>
                                            setBatchForm((current) => ({
                                                ...current,
                                                bankAccountId: event.target.value,
                                            }))
                                        }
                                        required
                                    >
                                        <option value="">{t("banking.chooseAccount", "Konto wählen")}</option>
                                        {accounts.map((account) => (
                                            <option key={account.id} value={account.id}>
                                                {account.name} • {account.iban}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                            </form>

                            <div className="builder-grid">
                                <article className="subcard">
                                    <div className="subcard-header">
                                        <h3>{t("banking.manualInstruction", "Manuelle Zahlungszeile")}</h3>
                                    </div>
                                    <div className="instruction-form">
                                        <input type="text" placeholder={t("banking.creditorName", "Empfängername")} value={instructionDraft.creditorName} onChange={(event) => setInstructionDraft((current) => ({ ...current, creditorName: event.target.value }))} />
                                        <input type="text" placeholder="IBAN" value={instructionDraft.creditorIban} onChange={(event) => setInstructionDraft((current) => ({ ...current, creditorIban: event.target.value }))} />
                                        <input type="text" placeholder="BIC" value={instructionDraft.creditorBic} onChange={(event) => setInstructionDraft((current) => ({ ...current, creditorBic: event.target.value }))} />
                                        <input type="number" step="0.01" placeholder={t("banking.amount", "Betrag")} value={instructionDraft.amount} onChange={(event) => setInstructionDraft((current) => ({ ...current, amount: event.target.value }))} />
                                        <input type="text" placeholder={t("banking.currency", "Währung")} value={instructionDraft.currency} onChange={(event) => setInstructionDraft((current) => ({ ...current, currency: event.target.value }))} />
                                        <input type="text" placeholder={t("banking.reference", "Verwendungszweck")} value={instructionDraft.reference} onChange={(event) => setInstructionDraft((current) => ({ ...current, reference: event.target.value }))} />
                                        <button type="button" className="secondary" onClick={addManualInstruction}>
                                            {t("banking.addInstruction", "Zeile hinzufügen")}
                                        </button>
                                    </div>
                                </article>

                                <article className="subcard">
                                    <div className="subcard-header">
                                        <h3>{t("banking.payablesSource", "Offene Kreditoren importieren")}</h3>
                                        <span className="badge neutral">{payables.length}</span>
                                    </div>
                                    <ul className="detail-list compact">
                                        {payables.slice(0, 8).map((invoice) => (
                                            <li key={invoice.id}>
                                                <div>
                                                    <strong>{invoice.invoiceNumber}</strong>
                                                    <span>{invoice.vendorName}</span>
                                                </div>
                                                <div className="inline-actions">
                                                    <span>{formatMoney(invoice.openAmount ?? invoice.amount, invoice.currency)}</span>
                                                    <button type="button" className="ghost" onClick={() => addPayableInstruction(invoice)}>
                                                        {t("banking.addToBatch", "In Batch")}
                                                    </button>
                                                </div>
                                            </li>
                                        ))}
                                        {payables.length === 0 && (
                                            <li>{t("banking.noPayablesImport", "Keine offenen Kreditoren zum Importieren.")}</li>
                                        )}
                                    </ul>
                                </article>
                            </div>

                            <div className="section-header top-gap">
                                <div>
                                    <h3>{t("banking.batchDraft", "Batch-Entwurf")}</h3>
                                    <p className="muted">{t("banking.batchDraftHint", "Jede Zeile bleibt vor dem Speichern editierbar.")}</p>
                                </div>
                            </div>

                            <div className="instruction-draft-list">
                                {batchForm.instructions.map((instruction) => (
                                    <article key={instruction.localId} className="instruction-draft">
                                        <div className="instruction-draft-header">
                                            <span className={`status-pill ${instruction.source === "vendor_invoice" ? "info" : "muted"}`}>
                                                {instruction.source === "vendor_invoice"
                                                    ? t("banking.sourcePayable", "Kreditor")
                                                    : t("banking.sourceManual", "Manuell")}
                                            </span>
                                            <button type="button" className="link-button danger" onClick={() => removeInstruction(instruction.localId)}>
                                                {t("common.delete", "Löschen")}
                                            </button>
                                        </div>
                                        <div className="instruction-grid">
                                            <label>
                                                {t("banking.creditorName", "Empfänger")}
                                                <input type="text" value={instruction.creditorName} onChange={(event) => updateInstruction(instruction.localId, "creditorName", event.target.value)} />
                                            </label>
                                            <label>
                                                IBAN
                                                <input type="text" value={instruction.creditorIban} onChange={(event) => updateInstruction(instruction.localId, "creditorIban", event.target.value)} />
                                            </label>
                                            <label>
                                                BIC
                                                <input type="text" value={instruction.creditorBic} onChange={(event) => updateInstruction(instruction.localId, "creditorBic", event.target.value)} />
                                            </label>
                                            <label>
                                                {t("banking.amount", "Betrag")}
                                                <input type="number" step="0.01" value={instruction.amount} onChange={(event) => updateInstruction(instruction.localId, "amount", event.target.value)} />
                                            </label>
                                            <label>
                                                {t("banking.currency", "Währung")}
                                                <input type="text" value={instruction.currency} onChange={(event) => updateInstruction(instruction.localId, "currency", event.target.value)} />
                                            </label>
                                            <label>
                                                {t("banking.reference", "Referenz")}
                                                <input type="text" value={instruction.reference} onChange={(event) => updateInstruction(instruction.localId, "reference", event.target.value)} />
                                            </label>
                                        </div>
                                    </article>
                                ))}
                                {batchForm.instructions.length === 0 && (
                                    <div className="empty-state">
                                        {t("banking.emptyDraft", "Noch keine Zahlungszeilen im Entwurf.")}
                                    </div>
                                )}
                            </div>

                            <div className="toolbar-end">
                                <button type="submit" className="primary" onClick={handleBatchSubmit}>
                                    {t("banking.saveBatch", "Auftrag anlegen")}
                                </button>
                            </div>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.batchHistory", "Batch-Historie")}</h2>
                                    <p className="muted">{t("banking.batchHistoryHint", "Status, Provider-Feedback und Details je Zahlungsauftrag")}</p>
                                </div>
                                <span className="badge neutral">{filteredBatches.length}</span>
                            </div>

                            <div className="filter-bar">
                                {batchFilterConfig.map((filter) => (
                                    <button key={filter.id} type="button" className={`filter-chip ${batchStatusFilter === filter.id ? "active" : ""}`} onClick={() => setBatchStatusFilter(filter.id)}>
                                        {filter.label}
                                    </button>
                                ))}
                            </div>

                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t("banking.batchId", "Batch")}</th>
                                            <th>{t("banking.account", "Konto")}</th>
                                            <th>{t("banking.status", "Status")}</th>
                                            <th>{t("banking.created", "Erstellt")}</th>
                                            <th className="numeric">{t("banking.amount", "Volumen")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredBatches.map((batch) => (
                                            <tr key={batch.id} className={selectedBatch?.id === batch.id ? "selected-row" : ""} onClick={() => setSelectedBatchId(batch.id)}>
                                                <td>#{batch.id}</td>
                                                <td>{batch.bankAccountName || batch.bankAccountIban || "–"}</td>
                                                <td>
                                                    <span className={`status-pill ${statusTone[batch.status] || "muted"}`}>
                                                        {statusLabel(batch.status, t)}
                                                    </span>
                                                </td>
                                                <td>{formatDateTime(batch.createdAt)}</td>
                                                <td className="numeric">{formatMoney(batch.totalAmount)}</td>
                                            </tr>
                                        ))}
                                        {filteredBatches.length === 0 && (
                                            <tr>
                                                <td colSpan="5">{t("banking.noBatchesInFilter", "Keine Zahlungsaufträge für diesen Filter.")}</td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>

                            {selectedBatch && (
                                <div className="detail-drawer">
                                    <div className="detail-header">
                                        <div>
                                            <h3>{t("banking.batchDetails", "Batch-Details")} #{selectedBatch.id}</h3>
                                            <p className="muted">{selectedBatch.bankAccountName || selectedBatch.bankAccountIban || "–"} • {formatDateTime(selectedBatch.createdAt)}</p>
                                        </div>
                                        <div className="inline-actions">
                                            {selectedBatch.status === "PENDING_APPROVAL" && (
                                                <button type="button" className="secondary" onClick={() => handleApprove(selectedBatch.id)}>
                                                    {t("banking.approve", "Freigeben")}
                                                </button>
                                            )}
                                            {selectedBatch.status === "APPROVED" && (
                                                <button type="button" className="primary" onClick={() => openTransmitDialog(selectedBatch)}>
                                                    {t("banking.transmit", "Übermitteln")}
                                                </button>
                                            )}
                                            <button type="button" className="ghost" onClick={() => handleDownloadPain001(selectedBatch.id)}>
                                                {t("banking.exportPain", "pain.001")}
                                            </button>
                                        </div>
                                    </div>

                                    <div className="detail-metrics">
                                        <div><span className="metric-label">{t("banking.totalAmount", "Gesamtvolumen")}</span><strong>{formatMoney(selectedBatch.totalAmount)}</strong></div>
                                        <div><span className="metric-label">{t("banking.instructions", "Anweisungen")}</span><strong>{selectedBatch.instructions?.length ?? 0}</strong></div>
                                        <div><span className="metric-label">{t("banking.providerStatus", "Providerstatus")}</span><strong>{selectedBatch.providerStatus || "–"}</strong></div>
                                        <div><span className="metric-label">{t("banking.reference", "Referenz")}</span><strong>{selectedBatch.transmissionReference || "–"}</strong></div>
                                    </div>

                                    {selectedBatch.providerMessage && (
                                        <p className="callout"><strong>{t("banking.providerMessage", "Provider-Meldung")}:</strong> {selectedBatch.providerMessage}</p>
                                    )}

                                    <p className="callout">
                                        <strong>{t("banking.deliveryChannel", "Versandkanal")}:</strong> {selectedBatch.deliveryChannel || "-"}
                                    </p>

                                    {selectedBatch.providerArtifactName && (
                                        <p className="callout">
                                            <strong>{t("banking.exportArtifact", "Exportdatei")}:</strong> {selectedBatch.providerArtifactName}
                                            {selectedBatch.providerArtifactPath ? ` | ${selectedBatch.providerArtifactPath}` : ""}
                                        </p>
                                    )}

                                    <ul className="instruction-summary">
                                        {(selectedBatch.instructions ?? []).map((instruction) => (
                                            <li key={instruction.id}>
                                                <div>
                                                    <strong>{instruction.creditorName || "–"}</strong>
                                                    <span>{instruction.creditorIban || "Keine IBAN"}</span>
                                                </div>
                                                <div className="align-right">
                                                    <strong>{formatMoney(instruction.amount, instruction.currency)}</strong>
                                                    <span>{instruction.vendorInvoiceNumber || instruction.customerInvoiceNumber || instruction.reference || "–"}</span>
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}
                        </section>
                    </div>
                )}

                {!loading && activeTab === "signatures" && (
                    <div className="workspace-grid">
                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.newSignatureRequest", "Neue Signaturanfrage")}</h2>
                                    <p className="muted">{t("banking.newSignatureHint", "Dokument hochladen oder Pfad hinterlegen und zur Signatur versenden")}</p>
                                </div>
                            </div>

                            <form className="form-grid" onSubmit={handleSignatureSubmit}>
                                <label>
                                    {t("banking.documentType", "Dokumenttyp")}
                                    <input type="text" value={signatureForm.documentType} onChange={(event) => setSignatureForm((current) => ({ ...current, documentType: event.target.value }))} required />
                                </label>

                                <label className="full-width">
                                    {t("banking.documentUpload", "Dokument hochladen")}
                                    <input type="file" accept=".pdf,.doc,.docx,.png,.jpg,.jpeg" onChange={(event) => handleSignatureUpload(event.target.files?.[0])} />
                                    <span className="muted">
                                        {signatureUpload.uploading
                                            ? t("banking.uploading", "Upload läuft...")
                                            : signatureUpload.fileName
                                                ? `${signatureUpload.fileName} • ${Math.round(signatureUpload.size / 1024)} KB`
                                                : t("banking.uploadHint", "Optional: Datei hochladen und Pfad automatisch setzen.")}
                                    </span>
                                </label>

                                <label className="full-width">
                                    {t("banking.documentPath", "Dokumentpfad")}
                                    <input type="text" value={signatureForm.documentPath} onChange={(event) => setSignatureForm((current) => ({ ...current, documentPath: event.target.value }))} required />
                                </label>

                                <label>
                                    {t("banking.signerEmail", "Signer Email")}
                                    <input type="email" value={signatureForm.email} onChange={(event) => setSignatureForm((current) => ({ ...current, email: event.target.value }))} required />
                                </label>

                                <div className="toolbar-end full-width">
                                    <button type="submit" className="primary">{t("banking.requestSignature", "Signatur anfordern")}</button>
                                </div>
                            </form>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.signatureHistory", "Signaturhistorie")}</h2>
                                    <p className="muted">{t("banking.signatureHistoryHint", "Status, Signing-URL und Provider-Rückmeldungen")}</p>
                                </div>
                                <span className="badge neutral">{signatures.length}</span>
                            </div>

                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t("banking.documentType", "Dokument")}</th>
                                            <th>{t("banking.signer", "Signer")}</th>
                                            <th>{t("banking.status", "Status")}</th>
                                            <th>{t("banking.created", "Angefragt")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {signatures.map((signature) => (
                                            <tr key={signature.id} className={selectedSignature?.id === signature.id ? "selected-row" : ""} onClick={() => setSelectedSignatureId(signature.id)}>
                                                <td>{signature.documentType || "–"}</td>
                                                <td>{signature.signerEmail || "–"}</td>
                                                <td><span className={`status-pill ${statusTone[signature.status] || "muted"}`}>{statusLabel(signature.status, t)}</span></td>
                                                <td>{formatDateTime(signature.requestedAt)}</td>
                                            </tr>
                                        ))}
                                        {signatures.length === 0 && (
                                            <tr>
                                                <td colSpan="4">{t("banking.noSignatures", "Noch keine Signaturanfragen vorhanden.")}</td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>

                            {selectedSignature && (
                                <div className="detail-drawer">
                                    <div className="detail-header">
                                        <div>
                                            <h3>{selectedSignature.documentType || t("banking.signatureDetail", "Signaturdetail")}</h3>
                                            <p className="muted">{selectedSignature.signerEmail || "–"}</p>
                                        </div>
                                        <div className="inline-actions">
                                            <button type="button" className="secondary" onClick={() => refreshSignature(selectedSignature.id)}>
                                                {t("banking.refreshStatus", "Status aktualisieren")}
                                            </button>
                                            {selectedSignature.signingUrl && (
                                                <a className="ghost button-link" href={selectedSignature.signingUrl} target="_blank" rel="noreferrer">
                                                    {t("banking.openSigning", "Signing öffnen")}
                                                </a>
                                            )}
                                            {selectedSignature.status !== "COMPLETED" && (
                                                <button type="button" className="primary" onClick={() => completeSignature(selectedSignature.id)}>
                                                    {t("banking.completeSignature", "Abschließen")}
                                                </button>
                                            )}
                                        </div>
                                    </div>

                                    <div className="detail-metrics">
                                        <div><span className="metric-label">{t("banking.requestedAt", "Angefragt")}</span><strong>{formatDateTime(selectedSignature.requestedAt)}</strong></div>
                                        <div><span className="metric-label">{t("banking.completedAt", "Abgeschlossen")}</span><strong>{formatDateTime(selectedSignature.completedAt)}</strong></div>
                                        <div><span className="metric-label">{t("banking.lastStatusCheck", "Letzter Check")}</span><strong>{formatDateTime(selectedSignature.lastStatusCheck)}</strong></div>
                                        <div><span className="metric-label">{t("banking.providerReference", "Provider-Referenz")}</span><strong>{selectedSignature.providerReference || "–"}</strong></div>
                                    </div>

                                    <p className="callout"><strong>{t("banking.documentPath", "Dokumentpfad")}:</strong> {selectedSignature.documentPath || "–"}</p>
                                    {selectedSignature.providerStatusMessage && (
                                        <p className="callout"><strong>{t("banking.providerMessage", "Provider-Meldung")}:</strong> {selectedSignature.providerStatusMessage}</p>
                                    )}
                                </div>
                            )}
                        </section>
                    </div>
                )}

                {!loading && activeTab === "messages" && (
                    <div className="workspace-grid">
                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.secureMessage", "Sichere Nachricht verfassen")}</h2>
                                    <p className="muted">{t("banking.secureMessageHint", "EBICS, SWIFT oder Email dokumentiert auslösen")}</p>
                                </div>
                            </div>

                            <form className="form-grid" onSubmit={handleMessageSubmit}>
                                <label>
                                    {t("banking.recipient", "Empfänger")}
                                    <input type="text" value={messageForm.recipient} onChange={(event) => setMessageForm((current) => ({ ...current, recipient: event.target.value }))} required />
                                </label>
                                <label>
                                    {t("banking.subject", "Betreff")}
                                    <input type="text" value={messageForm.subject} onChange={(event) => setMessageForm((current) => ({ ...current, subject: event.target.value }))} required />
                                </label>
                                <label>
                                    {t("banking.transport", "Kanal")}
                                    <select value={messageForm.transport} onChange={(event) => setMessageForm((current) => ({ ...current, transport: event.target.value }))}>
                                        <option value="EBICS">EBICS</option>
                                        <option value="SWIFT">SWIFT</option>
                                        <option value="EMAIL">EMAIL</option>
                                    </select>
                                </label>
                                <label className="full-width">
                                    {t("banking.message", "Nachricht")}
                                    <textarea value={messageForm.body} onChange={(event) => setMessageForm((current) => ({ ...current, body: event.target.value }))} required />
                                </label>
                                <div className="toolbar-end full-width">
                                    <button type="submit" className="primary">{t("banking.send", "Senden")}</button>
                                </div>
                            </form>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.messageHistory", "Nachrichtenverlauf")}</h2>
                                    <p className="muted">{t("banking.messageHistoryHint", "Zustellung, Providerstatus und Inhalt der letzten Nachrichten")}</p>
                                </div>
                                <span className="badge neutral">{deliveredMessages.length}/{messages.length}</span>
                            </div>

                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t("banking.recipient", "Empfänger")}</th>
                                            <th>{t("banking.subject", "Betreff")}</th>
                                            <th>{t("banking.transport", "Kanal")}</th>
                                            <th>{t("banking.status", "Status")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {messages.map((message) => (
                                            <tr key={message.id} className={selectedMessage?.id === message.id ? "selected-row" : ""} onClick={() => setSelectedMessageId(message.id)}>
                                                <td>{message.recipient}</td>
                                                <td>{message.subject}</td>
                                                <td>{transportLabel(message.transport)}</td>
                                                <td><span className={`status-pill ${message.delivered ? "success" : "warning"}`}>{message.delivered ? t("banking.delivered", "Zugestellt") : t("banking.pendingDelivery", "Ausstehend")}</span></td>
                                            </tr>
                                        ))}
                                        {messages.length === 0 && (
                                            <tr>
                                                <td colSpan="4">{t("banking.noMessages", "Noch keine Nachrichten vorhanden.")}</td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>

                            {selectedMessage && (
                                <div className="detail-drawer">
                                    <div className="detail-header">
                                        <div>
                                            <h3>{selectedMessage.subject || t("banking.messageDetail", "Nachrichtendetail")}</h3>
                                            <p className="muted">{selectedMessage.recipient} • {transportLabel(selectedMessage.transport)} • {formatDateTime(selectedMessage.sentAt)}</p>
                                        </div>
                                        <span className={`status-pill ${selectedMessage.delivered ? "success" : "warning"}`}>{selectedMessage.delivered ? t("banking.delivered", "Zugestellt") : t("banking.pendingDelivery", "Ausstehend")}</span>
                                    </div>

                                    <p className="message-body">{selectedMessage.body || "–"}</p>

                                    <div className="detail-metrics">
                                        <div><span className="metric-label">{t("banking.providerReference", "Provider-Referenz")}</span><strong>{selectedMessage.providerReference || "–"}</strong></div>
                                        <div><span className="metric-label">{t("banking.providerStatus", "Providerstatus")}</span><strong>{selectedMessage.providerStatus || "–"}</strong></div>
                                    </div>

                                    {selectedMessage.providerMessage && (
                                        <p className="callout"><strong>{t("banking.providerMessage", "Provider-Meldung")}:</strong> {selectedMessage.providerMessage}</p>
                                    )}
                                </div>
                            )}
                        </section>
                    </div>
                )}

                {!loading && activeTab === "accounts" && (
                    <div className="workspace-grid">
                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{editingAccountId ? t("banking.editAccount", "Firmenkonto bearbeiten") : t("banking.newAccount", "Eigenes Firmenkonto anlegen")}</h2>
                                    <p className="muted">{t("banking.accountHint", "Belastungskonten für Zahlungsläufe und Referenzen verwalten")}</p>
                                </div>
                                {editingAccountId && (
                                    <button type="button" className="ghost" onClick={resetAccountForm}>
                                        {t("common.cancel", "Abbrechen")}
                                    </button>
                                )}
                            </div>

                            <p className="callout">
                                <strong>{t("banking.ownAccountInfo", "Eigenes Firmenkonto")}:</strong> {t("banking.ownAccountInfoHint", "Dieses Konto wird fuer euer Debitor-/Kreditor-Belastungskonto, API-Versand und pain.001/EBICS-Bankexport genutzt.")}
                            </p>

                            <form className="form-grid" onSubmit={handleAccountSubmit}>
                                <label>
                                    {t("banking.accountName", "Kontobezeichnung")}
                                    <input type="text" value={accountForm.name} onChange={(event) => setAccountForm((current) => ({ ...current, name: event.target.value }))} required />
                                </label>
                                <label>
                                    IBAN
                                    <input type="text" value={accountForm.iban} onChange={(event) => setAccountForm((current) => ({ ...current, iban: event.target.value }))} required />
                                </label>
                                <label>
                                    BIC
                                    <input type="text" value={accountForm.bic} onChange={(event) => setAccountForm((current) => ({ ...current, bic: event.target.value }))} />
                                </label>
                                <label>
                                    {t("banking.clearing", "Clearing-Nr.")}
                                    <input type="text" value={accountForm.clearingNumber} onChange={(event) => setAccountForm((current) => ({ ...current, clearingNumber: event.target.value }))} />
                                </label>
                                <div className="toolbar-end full-width">
                                    <button type="submit" className="primary">{editingAccountId ? t("banking.updateAccount", "Änderungen speichern") : t("banking.saveAccount", "Speichern")}</button>
                                </div>
                            </form>
                        </section>

                        <section className="card">
                            <div className="section-header">
                                <div>
                                    <h2>{t("banking.accounts", "Hinterlegte Firmenkonten")}</h2>
                                    <p className="muted">{t("banking.accountsHint", "Konten für Zahlungsläufe, pain.001 und Referenzen")}</p>
                                </div>
                                <span className="badge neutral">{accounts.length}</span>
                            </div>

                            <div className="accounts-list">
                                {accounts.map((account) => (
                                    <article key={account.id} className="account-card">
                                        <div>
                                            <h3>{account.name}</h3>
                                            <p>{account.iban}</p>
                                            <p className="muted">{account.bic || "BIC –"} • {account.clearingNumber || t("banking.noClearing", "keine Clearing-Nr.")}</p>
                                        </div>
                                        <div className="inline-actions">
                                            <button type="button" className="secondary" onClick={() => handleEditAccount(account)}>{t("common.edit", "Bearbeiten")}</button>
                                            <button type="button" className="ghost danger" onClick={() => handleDeleteAccount(account)}>{t("common.delete", "Löschen")}</button>
                                        </div>
                                    </article>
                                ))}
                                {accounts.length === 0 && (
                                    <div className="empty-state">
                                        {t("banking.noAccounts", "Noch kein eigenes Firmenkonto hinterlegt.")}
                                    </div>
                                )}
                            </div>
                        </section>
                    </div>
                )}
            </main>

            {transmitState.open && (
                <div className="modal-backdrop" role="presentation">
                    <div className="modal">
                        <div className="detail-header">
                            <div>
                                <h3>{t("banking.transmitBatch", "Zahlungsauftrag übermitteln")}</h3>
                                <p className="muted">{t("banking.transmitBatchHint", "Referenz für Gateway oder Idempotency-Key erfassen")}</p>
                            </div>
                            <button type="button" className="ghost" onClick={closeTransmitDialog}>
                                {t("common.close", "Schließen")}
                            </button>
                        </div>

                        <form className="form-grid" onSubmit={handleTransmit}>
                            <label className="full-width">
                                {t("banking.reference", "Referenz")}
                                <input type="text" value={transmitState.reference} onChange={(event) => setTransmitState((current) => ({ ...current, reference: event.target.value }))} required />
                            </label>

                            <div className="toolbar-end full-width">
                                <button type="button" className="ghost" onClick={closeTransmitDialog}>{t("common.cancel", "Abbrechen")}</button>
                                <button type="submit" className="primary">{t("banking.transmit", "Übermitteln")}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default BankingOperationsPage;

