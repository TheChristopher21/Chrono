import React, { useEffect, useMemo, useRef, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/AdminAccountingPageScoped.css";

const initialAccount = {
    code: "",
    name: "",
    type: "ASSET",
    active: true
};

const AccountTypeOptions = [
    "ASSET",
    "LIABILITY",
    "EQUITY",
    "REVENUE",
    "EXPENSE",
    "CONTRA_ASSET",
    "OFF_BALANCE"
];

const TABS = [
    { id: "overview", label: "Overview" },
    { id: "accounts", label: "Kontenplan" },
    { id: "journal", label: "Journal" },
    { id: "receivables", label: "Debitoren" },
    { id: "payables", label: "Kreditoren" },
    { id: "assets", label: "Anlagen" }
];

const PAGE_SIZE_OPTIONS = [10, 20, 50];

const AdminAccountingPage = () => {
    const { notify } = useNotification();
    const { t } = useTranslation();
    const [accounts, setAccounts] = useState([]);
    const [journalEntries, setJournalEntries] = useState([]);
    const [receivables, setReceivables] = useState([]);
    const [payables, setPayables] = useState([]);
    const [assets, setAssets] = useState([]);
    const [loading, setLoading] = useState(true);
    const [accountForm, setAccountForm] = useState(initialAccount);
    const [refreshFlag, setRefreshFlag] = useState(0);
    const [showJournalForm, setShowJournalForm] = useState(false);
    const [activeTab, setActiveTab] = useState("overview");
    const [filters, setFilters] = useState({
        from: "",
        to: "",
        period: "",
        status: "all",
        query: ""
    });
    const [journalForm, setJournalForm] = useState({
        entryDate: "",
        description: "",
        documentReference: "",
        lines: [{ accountId: "", debit: "", credit: "", memo: "" }]
    });
    const [journalAttachment, setJournalAttachment] = useState(null);
    const [assetForm, setAssetForm] = useState({
        assetName: "",
        acquisitionDate: "",
        acquisitionCost: "",
        usefulLifeMonths: "",
        residualValue: ""
    });
    const [paymentModal, setPaymentModal] = useState({
        open: false,
        type: "receivable",
        invoice: null,
        amount: "",
        paymentDate: "",
        memo: ""
    });
    const [paymentError, setPaymentError] = useState("");
    const [paymentLoading, setPaymentLoading] = useState(false);
    const modalRef = useRef(null);
    const modalFirstInputRef = useRef(null);

    const [journalPage, setJournalPage] = useState(0);
    const [journalPageSize, setJournalPageSize] = useState(20);
    const [journalMeta, setJournalMeta] = useState({ totalPages: 1, totalElements: 0 });
    const [receivablePage, setReceivablePage] = useState(0);
    const [receivablePageSize, setReceivablePageSize] = useState(20);
    const [receivableMeta, setReceivableMeta] = useState({ totalPages: 1, totalElements: 0 });
    const [payablePage, setPayablePage] = useState(0);
    const [payablePageSize, setPayablePageSize] = useState(20);
    const [payableMeta, setPayableMeta] = useState({ totalPages: 1, totalElements: 0 });

    const [selectedJournalEntry, setSelectedJournalEntry] = useState(null);
    const [selectedReceivable, setSelectedReceivable] = useState(null);
    const [selectedPayable, setSelectedPayable] = useState(null);
    const [selectedAsset, setSelectedAsset] = useState(null);

    const currencyFormatter = useMemo(
        () => new Intl.NumberFormat("de-CH", { style: "currency", currency: "CHF" }),
        []
    );
    const dateFormatter = useMemo(() => new Intl.DateTimeFormat("de-CH"), []);

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [accRes, journalRes, recvRes, payRes, assetRes] = await Promise.all([
                    api.get("/api/accounting/accounts"),
                    api.get("/api/accounting/journal", { params: { page: journalPage, size: journalPageSize } }),
                    api.get("/api/accounting/receivables/open", { params: { page: receivablePage, size: receivablePageSize } }),
                    api.get("/api/accounting/payables/open", { params: { page: payablePage, size: payablePageSize } }),
                    api.get("/api/accounting/assets")
                ]);
                setAccounts(accRes.data ?? []);
                const journal = journalRes.data?.content ?? journalRes.data ?? [];
                setJournalEntries(journal);
                setJournalMeta({
                    totalPages: journalRes.data?.totalPages ?? 1,
                    totalElements: journalRes.data?.totalElements ?? journal.length
                });
                const receivableData = recvRes.data?.content ?? recvRes.data ?? [];
                const payableData = payRes.data?.content ?? payRes.data ?? [];
                setReceivables(receivableData);
                setReceivableMeta({
                    totalPages: recvRes.data?.totalPages ?? 1,
                    totalElements: recvRes.data?.totalElements ?? receivableData.length
                });
                setPayables(payableData);
                setPayableMeta({
                    totalPages: payRes.data?.totalPages ?? 1,
                    totalElements: payRes.data?.totalElements ?? payableData.length
                });
                setAssets(assetRes.data ?? []);
            } catch (error) {
                console.error("Failed to load accounting data", error);
                notify(t("accounting.loadError", "Finanzdaten konnten nicht geladen werden."), "error");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [
        notify,
        t,
        refreshFlag,
        journalPage,
        journalPageSize,
        receivablePage,
        receivablePageSize,
        payablePage,
        payablePageSize
    ]);

    useEffect(() => {
        if (!paymentModal.open) {
            return undefined;
        }
        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                closePaymentModal();
            }
            if (event.key === "Tab" && modalRef.current) {
                const focusable = modalRef.current.querySelectorAll(
                    "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])"
                );
                if (focusable.length === 0) {
                    return;
                }
                const first = focusable[0];
                const last = focusable[focusable.length - 1];
                if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last.focus();
                }
                if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first.focus();
                }
            }
        };
        document.addEventListener("keydown", handleKeyDown);
        setTimeout(() => {
            modalFirstInputRef.current?.focus();
        }, 0);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [paymentModal.open]);

    const formatCurrency = (value) => currencyFormatter.format(Number(value ?? 0));
    const formatDate = (value) => (value ? dateFormatter.format(new Date(value)) : "–");

    const matchesQuery = (value) => {
        if (!filters.query) {
            return true;
        }
        return String(value ?? "").toLowerCase().includes(filters.query.toLowerCase());
    };

    const isWithinDateRange = (value) => {
        if (!filters.from && !filters.to) {
            return true;
        }
        const dateValue = value ? new Date(value) : null;
        if (!dateValue) {
            return false;
        }
        if (filters.from && dateValue < new Date(filters.from)) {
            return false;
        }
        if (filters.to && dateValue > new Date(filters.to)) {
            return false;
        }
        return true;
    };

    const determineInvoiceStatus = (invoice) => {
        if (invoice.openAmount === 0 || invoice.status === "PAID") {
            return "paid";
        }
        if (invoice.dueDate) {
            const dueDate = new Date(invoice.dueDate);
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            const diffDays = Math.ceil((dueDate - today) / (1000 * 60 * 60 * 24));
            if (diffDays < 0) {
                return "overdue";
            }
            if (diffDays <= 7) {
                return "due-soon";
            }
        }
        return "open";
    };

    const agingBucketFor = (invoice) => {
        const anchor = invoice.dueDate ?? invoice.invoiceDate;
        if (!anchor) {
            return "Unbekannt";
        }
        const diffDays = Math.floor((new Date() - new Date(anchor)) / (1000 * 60 * 60 * 24));
        if (diffDays <= 30) return "0-30";
        if (diffDays <= 60) return "31-60";
        if (diffDays <= 90) return "61-90";
        return "90+";
    };

    const totalReceivables = useMemo(
        () => receivables.reduce((sum, invoice) => sum + Number(invoice.openAmount ?? invoice.amount ?? 0), 0),
        [receivables]
    );
    const totalPayables = useMemo(
        () => payables.reduce((sum, invoice) => sum + Number(invoice.openAmount ?? invoice.amount ?? 0), 0),
        [payables]
    );

    const receivablesAging = useMemo(() => {
        return receivables.reduce(
            (acc, invoice) => {
                const bucket = agingBucketFor(invoice);
                acc[bucket] = (acc[bucket] ?? 0) + Number(invoice.openAmount ?? invoice.amount ?? 0);
                return acc;
            },
            { "0-30": 0, "31-60": 0, "61-90": 0, "90+": 0 }
        );
    }, [receivables]);

    const payablesAging = useMemo(() => {
        return payables.reduce(
            (acc, invoice) => {
                const bucket = agingBucketFor(invoice);
                acc[bucket] = (acc[bucket] ?? 0) + Number(invoice.openAmount ?? invoice.amount ?? 0);
                return acc;
            },
            { "0-30": 0, "31-60": 0, "61-90": 0, "90+": 0 }
        );
    }, [payables]);

    const filteredJournalEntries = useMemo(() => {
        return journalEntries.filter((entry) =>
            isWithinDateRange(entry.entryDate)
            && (matchesQuery(entry.description) || matchesQuery(entry.documentReference))
        );
    }, [journalEntries, filters]);

    const filteredReceivables = useMemo(() => {
        return receivables.filter((invoice) => {
            const status = determineInvoiceStatus(invoice);
            if (filters.status !== "all" && filters.status !== status) {
                return false;
            }
            return (
                isWithinDateRange(invoice.dueDate ?? invoice.invoiceDate)
                && (matchesQuery(invoice.invoiceNumber) || matchesQuery(invoice.customerName))
            );
        });
    }, [receivables, filters]);

    const filteredPayables = useMemo(() => {
        return payables.filter((invoice) => {
            const status = determineInvoiceStatus(invoice);
            if (filters.status !== "all" && filters.status !== status) {
                return false;
            }
            return (
                isWithinDateRange(invoice.dueDate ?? invoice.invoiceDate)
                && (matchesQuery(invoice.invoiceNumber) || matchesQuery(invoice.vendorName))
            );
        });
    }, [payables, filters]);

    const filteredAssets = useMemo(() => {
        return assets.filter((asset) => matchesQuery(asset.assetName));
    }, [assets, filters]);

    const handleAccountSubmit = async (event) => {
        event.preventDefault();
        try {
            await api.post("/api/accounting/accounts", accountForm);
            notify(t("accounting.accountSaved", "Konto gespeichert."), "success");
            setAccountForm(initialAccount);
            setRefreshFlag((flag) => flag + 1);
        } catch (error) {
            console.error("Failed to create account", error);
            notify(t("accounting.accountSaveFailed", "Konto konnte nicht angelegt werden."), "error");
        }
    };

    const addJournalLine = () => {
        setJournalForm((prev) => ({
            ...prev,
            lines: [...prev.lines, { accountId: "", debit: "", credit: "", memo: "" }]
        }));
    };

    const updateJournalLine = (index, field, value) => {
        setJournalForm((prev) => {
            const lines = prev.lines.map((line, idx) => {
                if (idx !== index) {
                    return line;
                }
                if (field === "debit" && value) {
                    return { ...line, debit: value, credit: "" };
                }
                if (field === "credit" && value) {
                    return { ...line, credit: value, debit: "" };
                }
                return { ...line, [field]: value };
            });
            return { ...prev, lines };
        });
    };

    const removeJournalLine = (index) => {
        setJournalForm((prev) => ({
            ...prev,
            lines: prev.lines.filter((_, idx) => idx !== index)
        }));
    };

    const journalTotals = useMemo(() => {
        const totals = journalForm.lines.reduce(
            (acc, line) => {
                acc.debit += Number(line.debit || 0);
                acc.credit += Number(line.credit || 0);
                return acc;
            },
            { debit: 0, credit: 0 }
        );
        return {
            ...totals,
            diff: totals.debit - totals.credit
        };
    }, [journalForm.lines]);

    const isJournalBalanced = journalTotals.debit > 0 && journalTotals.debit === journalTotals.credit;

    const handleJournalSubmit = async (event) => {
        event.preventDefault();
        if (!isJournalBalanced) {
            notify(t("accounting.journalNotBalanced", "Soll und Haben müssen übereinstimmen."), "error");
            return;
        }
        try {
            const payload = {
                entryDate: journalForm.entryDate || undefined,
                description: journalForm.description,
                documentReference: journalForm.documentReference,
                source: "MANUAL",
                lines: journalForm.lines
                    .filter((line) => line.accountId && (line.debit || line.credit))
                    .map((line) => ({
                        accountId: Number(line.accountId),
                        debit: Number(line.debit || 0),
                        credit: Number(line.credit || 0),
                        memo: line.memo
                    }))
            };
            await api.post("/api/accounting/journal", payload);
            notify(t("accounting.journalSaved", "Journalbuchung erfasst."), "success");
            setShowJournalForm(false);
            setJournalForm({
                entryDate: "",
                description: "",
                documentReference: "",
                lines: [{ accountId: "", debit: "", credit: "", memo: "" }]
            });
            setJournalAttachment(null);
            setRefreshFlag((flag) => flag + 1);
        } catch (error) {
            console.error("Failed to create journal entry", error);
            notify(t("accounting.journalSaveFailed", "Journalbuchung konnte nicht gespeichert werden."), "error");
        }
    };

    const handleAssetSubmit = async (event) => {
        event.preventDefault();
        try {
            const payload = {
                assetName: assetForm.assetName,
                acquisitionDate: assetForm.acquisitionDate || undefined,
                acquisitionCost: Number(assetForm.acquisitionCost || 0),
                usefulLifeMonths: Number(assetForm.usefulLifeMonths || 0),
                residualValue: assetForm.residualValue ? Number(assetForm.residualValue) : undefined
            };
            await api.post("/api/accounting/assets", payload);
            notify(t("accounting.assetSaved", "Anlage erfasst."), "success");
            setAssetForm({ assetName: "", acquisitionDate: "", acquisitionCost: "", usefulLifeMonths: "", residualValue: "" });
            setRefreshFlag((flag) => flag + 1);
        } catch (error) {
            console.error("Failed to save asset", error);
            notify(t("accounting.assetSaveFailed", "Anlage konnte nicht gespeichert werden."), "error");
        }
    };

    const handleDepreciation = async (assetId) => {
        try {
            await api.post(`/api/accounting/assets/${assetId}/depreciate`);
            notify(t("accounting.depreciationRun", "Abschreibung gebucht."), "success");
            setRefreshFlag((flag) => flag + 1);
        } catch (error) {
            console.error("Failed to depreciate asset", error);
            notify(t("accounting.depreciationFailed", "Abschreibung fehlgeschlagen."), "error");
        }
    };

    const openPaymentModal = (invoice, type) => {
        setPaymentModal({
            open: true,
            type,
            invoice,
            amount: Number(invoice?.openAmount ?? invoice?.amount ?? 0).toFixed(2),
            paymentDate: "",
            memo: ""
        });
        setPaymentError("");
    };

    const closePaymentModal = () => {
        setPaymentModal({ open: false, type: "receivable", invoice: null, amount: "", paymentDate: "", memo: "" });
        setPaymentError("");
    };

    const handlePaymentSubmit = async (event) => {
        event.preventDefault();
        if (!paymentModal.invoice) {
            return;
        }
        if (Number(paymentModal.amount || 0) <= 0) {
            setPaymentError(t("accounting.paymentAmountInvalid", "Bitte einen gültigen Betrag angeben."));
            return;
        }
        const endpoint = paymentModal.type === "receivable"
            ? `/api/accounting/receivables/${paymentModal.invoice.id}/payments`
            : `/api/accounting/payables/${paymentModal.invoice.id}/payments`;
        try {
            setPaymentLoading(true);
            const payload = {
                amount: Number(paymentModal.amount || 0),
                paymentDate: paymentModal.paymentDate || undefined,
                memo: paymentModal.memo || undefined
            };
            await api.post(endpoint, payload);
            notify(t("accounting.paymentRecorded", "Zahlung verbucht."), "success");
            closePaymentModal();
            setRefreshFlag((flag) => flag + 1);
        } catch (error) {
            console.error("Failed to record payment", error);
            setPaymentError(t("accounting.paymentFailed", "Zahlung konnte nicht verbucht werden."));
        } finally {
            setPaymentLoading(false);
        }
    };

    const handleFilterChange = (key, value) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handlePeriodChange = (value) => {
        const today = new Date();
        const start = new Date(today.getFullYear(), today.getMonth(), 1);
        const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
        if (value === "this-month") {
            setFilters((prev) => ({
                ...prev,
                period: value,
                from: start.toISOString().slice(0, 10),
                to: end.toISOString().slice(0, 10)
            }));
            return;
        }
        if (value === "last-month") {
            const lastStart = new Date(today.getFullYear(), today.getMonth() - 1, 1);
            const lastEnd = new Date(today.getFullYear(), today.getMonth(), 0);
            setFilters((prev) => ({
                ...prev,
                period: value,
                from: lastStart.toISOString().slice(0, 10),
                to: lastEnd.toISOString().slice(0, 10)
            }));
            return;
        }
        setFilters((prev) => ({ ...prev, period: value }));
    };

    const downloadCsv = (rows, filename) => {
        if (!rows || rows.length === 0) {
            notify(t("accounting.exportEmpty", "Keine Daten für den Export vorhanden."), "error");
            return;
        }
        const header = Object.keys(rows[0]);
        const csv = [header.join(",")]
            .concat(rows.map((row) => header.map((key) => `"${String(row[key] ?? "").replace(/"/g, '""')}"`).join(",")))
            .join("\n");
        const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.setAttribute("download", filename);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    const handleExport = () => {
        if (activeTab === "accounts") {
            downloadCsv(
                accounts.map((account) => ({
                    code: account.code,
                    name: account.name,
                    type: account.type,
                    active: account.active ? "aktiv" : "inaktiv"
                })),
                "kontenplan.csv"
            );
            return;
        }
        if (activeTab === "journal") {
            downloadCsv(
                filteredJournalEntries.map((entry) => ({
                    date: entry.entryDate,
                    description: entry.description,
                    source: entry.source,
                    lines: (entry.lines ?? []).length
                })),
                "journal.csv"
            );
            return;
        }
        if (activeTab === "receivables") {
            downloadCsv(
                filteredReceivables.map((invoice) => ({
                    invoiceNumber: invoice.invoiceNumber,
                    customer: invoice.customerName,
                    dueDate: invoice.dueDate,
                    openAmount: invoice.openAmount ?? invoice.amount
                })),
                "debitoren.csv"
            );
            return;
        }
        if (activeTab === "payables") {
            downloadCsv(
                filteredPayables.map((invoice) => ({
                    invoiceNumber: invoice.invoiceNumber,
                    vendor: invoice.vendorName,
                    dueDate: invoice.dueDate,
                    openAmount: invoice.openAmount ?? invoice.amount
                })),
                "kreditoren.csv"
            );
            return;
        }
        if (activeTab === "assets") {
            downloadCsv(
                filteredAssets.map((asset) => ({
                    assetName: asset.assetName,
                    acquisitionDate: asset.acquisitionDate,
                    acquisitionCost: asset.acquisitionCost,
                    status: asset.status
                })),
                "anlagen.csv"
            );
        }
    };

    return (
        <div className="admin-page accounting-page">
            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <div>
                        <h1>{t("accounting.title", "Finanzbuchhaltung")}</h1>
                        <p className="muted">{t("accounting.subtitle", "Überblick über Konten, Buchungen und offene Posten")}</p>
                    </div>
                    <div className="header-actions">
                        <button type="button" className="secondary" onClick={handleExport}>
                            {t("accounting.exportCsv", "CSV Export")}
                        </button>
                        <button type="button" className="ghost" disabled>
                            {t("accounting.exportPdf", "PDF Export")}
                        </button>
                        <button type="button" className="ghost" disabled>
                            {t("accounting.import", "Import")}
                        </button>
                    </div>
                </header>

                <div className="command-bar">
                    <div className="command-group">
                        <label>
                            {t("accounting.from", "Von")}
                            <input
                                type="date"
                                value={filters.from}
                                onChange={(e) => handleFilterChange("from", e.target.value)}
                            />
                        </label>
                        <label>
                            {t("accounting.to", "Bis")}
                            <input
                                type="date"
                                value={filters.to}
                                onChange={(e) => handleFilterChange("to", e.target.value)}
                            />
                        </label>
                        <label>
                            {t("accounting.period", "Periode")}
                            <select value={filters.period} onChange={(e) => handlePeriodChange(e.target.value)}>
                                <option value="">{t("accounting.periodAll", "Alle")}</option>
                                <option value="this-month">{t("accounting.periodThisMonth", "Dieser Monat")}</option>
                                <option value="last-month">{t("accounting.periodLastMonth", "Letzter Monat")}</option>
                            </select>
                        </label>
                    </div>
                    <div className="command-group">
                        <label>
                            {t("accounting.status", "Status")}
                            <select value={filters.status} onChange={(e) => handleFilterChange("status", e.target.value)}>
                                <option value="all">{t("accounting.statusAll", "Alle")}</option>
                                <option value="open">{t("accounting.statusOpen", "Offen")}</option>
                                <option value="due-soon">{t("accounting.statusDueSoon", "Bald fällig")}</option>
                                <option value="overdue">{t("accounting.statusOverdue", "Überfällig")}</option>
                                <option value="paid">{t("accounting.statusPaid", "Bezahlt")}</option>
                            </select>
                        </label>
                        <label className="command-search">
                            {t("accounting.search", "Suche")}
                            <input
                                type="search"
                                placeholder={t("accounting.searchPlaceholder", "Belegnr., Kunde, Vendor")}
                                value={filters.query}
                                onChange={(e) => handleFilterChange("query", e.target.value)}
                            />
                        </label>
                    </div>
                </div>

                <div className="tab-bar">
                    {TABS.map((tab) => (
                        <button
                            key={tab.id}
                            type="button"
                            className={activeTab === tab.id ? "tab active" : "tab"}
                            onClick={() => setActiveTab(tab.id)}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>

                {activeTab === "overview" && (
                    <section className="overview-grid">
                        <article className="card">
                            <h2>{t("accounting.totalReceivables", "Offene Debitoren")}</h2>
                            <p className="metric">{formatCurrency(totalReceivables)}</p>
                            <p className="muted">{t("accounting.pageTotalHint", "Summe basierend auf aktueller Seite")}</p>
                        </article>
                        <article className="card">
                            <h2>{t("accounting.totalPayables", "Offene Kreditoren")}</h2>
                            <p className="metric">{formatCurrency(totalPayables)}</p>
                            <p className="muted">{t("accounting.pageTotalHint", "Summe basierend auf aktueller Seite")}</p>
                        </article>
                        <article className="card">
                            <h2>{t("accounting.agingReceivables", "Debitoren Aging")}</h2>
                            <div className="aging-grid">
                                {Object.entries(receivablesAging).map(([bucket, amount]) => (
                                    <div key={bucket}>
                                        <span className="muted">{bucket}</span>
                                        <strong>{formatCurrency(amount)}</strong>
                                    </div>
                                ))}
                            </div>
                        </article>
                        <article className="card">
                            <h2>{t("accounting.agingPayables", "Kreditoren Aging")}</h2>
                            <div className="aging-grid">
                                {Object.entries(payablesAging).map(([bucket, amount]) => (
                                    <div key={bucket}>
                                        <span className="muted">{bucket}</span>
                                        <strong>{formatCurrency(amount)}</strong>
                                    </div>
                                ))}
                            </div>
                        </article>
                    </section>
                )}

                {activeTab === "accounts" && (
                    <>
                        <section className="card">
                            <h2>{t("accounting.addAccount", "Neues Konto")}</h2>
                            <form className="form-grid" onSubmit={handleAccountSubmit}>
                                <label>
                                    {t("accounting.accountCode", "Kontonummer")}
                                    <input
                                        type="text"
                                        value={accountForm.code}
                                        onChange={(e) => setAccountForm({ ...accountForm, code: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.accountName", "Kontoname")}
                                    <input
                                        type="text"
                                        value={accountForm.name}
                                        onChange={(e) => setAccountForm({ ...accountForm, name: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.accountType", "Kontotyp")}
                                    <select
                                        value={accountForm.type}
                                        onChange={(e) => setAccountForm({ ...accountForm, type: e.target.value })}
                                    >
                                        {AccountTypeOptions.map((option) => (
                                            <option key={option} value={option}>{option}</option>
                                        ))}
                                    </select>
                                </label>
                                <label className="toggle">
                                    <span>{t("accounting.accountActive", "Aktiv")}</span>
                                    <input
                                        type="checkbox"
                                        checked={accountForm.active}
                                        onChange={(e) => setAccountForm({ ...accountForm, active: e.target.checked })}
                                    />
                                </label>
                                <button type="submit" className="primary">
                                    {t("accounting.saveAccount", "Konto speichern")}
                                </button>
                            </form>
                        </section>

                        <section className="card">
                            <h2>{t("accounting.accounts", "Kontenplan")}</h2>
                            {loading ? (
                                <p>{t("accounting.loading", "Lade...")}</p>
                            ) : (
                                <div className="table-wrapper">
                                    <table>
                                        <thead>
                                            <tr>
                                                <th>{t("accounting.accountClass", "Klasse")}</th>
                                                <th>{t("accounting.accountCode", "Kontonummer")}</th>
                                                <th>{t("accounting.accountName", "Kontoname")}</th>
                                                <th>{t("accounting.accountType", "Kontotyp")}</th>
                                                <th>{t("accounting.status", "Status")}</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {accounts.map((account) => (
                                                <tr key={account.id}>
                                                    <td>{account.code?.slice(0, 1) ?? "–"}</td>
                                                    <td>{account.code}</td>
                                                    <td>{account.name}</td>
                                                    <td>{account.type}</td>
                                                    <td>
                                                        <span className={`badge ${account.active ? "badge-success" : "badge-muted"}`}>
                                                            {account.active ? t("accounting.active", "Aktiv") : t("accounting.inactive", "Inaktiv")}
                                                        </span>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </section>
                    </>
                )}

                {activeTab === "journal" && (
                    <section className="card">
                        <div className="section-header">
                            <h2>{t("accounting.journal", "Journal")}</h2>
                            <button type="button" className="secondary" onClick={() => setShowJournalForm((prev) => !prev)}>
                                {showJournalForm
                                    ? t("accounting.closeJournalForm", "Schließen")
                                    : t("accounting.newJournalEntry", "Neue Buchung")}
                            </button>
                        </div>

                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("accounting.date", "Datum")}</th>
                                        <th>{t("accounting.description", "Beschreibung")}</th>
                                        <th>{t("accounting.source", "Quelle")}</th>
                                        <th className="numeric">{t("accounting.lines", "Zeilen")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredJournalEntries.map((entry) => (
                                        <tr key={entry.id} onClick={() => setSelectedJournalEntry(entry)}>
                                            <td>{formatDate(entry.entryDate)}</td>
                                            <td>{entry.description}</td>
                                            <td>{entry.source}</td>
                                            <td className="numeric">{entry.lines?.length ?? 0}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <div className="table-footer">
                            <div className="pagination">
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setJournalPage((page) => Math.max(page - 1, 0))}
                                    disabled={journalPage === 0}
                                >
                                    {t("common.previous", "Zurück")}
                                </button>
                                <span>
                                    {journalPage + 1} / {journalMeta.totalPages}
                                </span>
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setJournalPage((page) => Math.min(page + 1, journalMeta.totalPages - 1))}
                                    disabled={journalPage >= journalMeta.totalPages - 1}
                                >
                                    {t("common.next", "Weiter")}
                                </button>
                            </div>
                            <label>
                                {t("common.pageSize", "Einträge")}
                                <select
                                    value={journalPageSize}
                                    onChange={(e) => setJournalPageSize(Number(e.target.value))}
                                >
                                    {PAGE_SIZE_OPTIONS.map((size) => (
                                        <option key={size} value={size}>{size}</option>
                                    ))}
                                </select>
                            </label>
                        </div>

                        {selectedJournalEntry && (
                            <div className="detail-drawer">
                                <div className="detail-header">
                                    <strong>{selectedJournalEntry.description}</strong>
                                    <button type="button" className="ghost" onClick={() => setSelectedJournalEntry(null)}>
                                        {t("common.close", "Schließen")}
                                    </button>
                                </div>
                                <div className="detail-body">
                                    <p className="muted">
                                        {formatDate(selectedJournalEntry.entryDate)} · {selectedJournalEntry.source}
                                    </p>
                                    <ul className="detail-lines">
                                        {(selectedJournalEntry.lines ?? []).map((line) => (
                                            <li key={line.id ?? `${line.accountCode}-${line.memo}`}>
                                                <span>{line.accountCode} – {line.accountName}</span>
                                                <span>{line.memo || t("accounting.noMemo", "Kein Memo")}</span>
                                                <span className="numeric">{formatCurrency(line.debit || 0)}</span>
                                                <span className="numeric">{formatCurrency(line.credit || 0)}</span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            </div>
                        )}

                        {showJournalForm && (
                            <form className="form-grid" onSubmit={handleJournalSubmit}>
                                <label>
                                    {t("accounting.date", "Datum")}
                                    <input
                                        type="date"
                                        value={journalForm.entryDate}
                                        onChange={(e) => setJournalForm({ ...journalForm, entryDate: e.target.value })}
                                    />
                                </label>
                                <label>
                                    {t("accounting.description", "Beschreibung")}
                                    <input
                                        type="text"
                                        value={journalForm.description}
                                        onChange={(e) => setJournalForm({ ...journalForm, description: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.documentRef", "Belegnummer")}
                                    <input
                                        type="text"
                                        value={journalForm.documentReference}
                                        onChange={(e) => setJournalForm({ ...journalForm, documentReference: e.target.value })}
                                    />
                                </label>
                                <label>
                                    {t("accounting.attachment", "Beleganhang")}
                                    <input
                                        type="file"
                                        onChange={(e) => setJournalAttachment(e.target.files?.[0] ?? null)}
                                    />
                                </label>

                                <div className="journal-lines">
                                    {journalForm.lines.map((line, index) => (
                                        <div key={index} className="journal-line">
                                            <select
                                                value={line.accountId}
                                                onChange={(e) => updateJournalLine(index, "accountId", e.target.value)}
                                                required
                                            >
                                                <option value="">{t("accounting.chooseAccount", "Konto wählen")}</option>
                                                {accounts.map((account) => (
                                                    <option key={account.id} value={account.id}>
                                                        {account.code} – {account.name}
                                                    </option>
                                                ))}
                                            </select>
                                            <input
                                                type="number"
                                                step="0.01"
                                                placeholder={t("accounting.debit", "Soll")}
                                                value={line.debit}
                                                onChange={(e) => updateJournalLine(index, "debit", e.target.value)}
                                            />
                                            <input
                                                type="number"
                                                step="0.01"
                                                placeholder={t("accounting.credit", "Haben")}
                                                value={line.credit}
                                                onChange={(e) => updateJournalLine(index, "credit", e.target.value)}
                                            />
                                            <input
                                                type="text"
                                                placeholder={t("accounting.memo", "Memo (optional)")}
                                                value={line.memo}
                                                onChange={(e) => updateJournalLine(index, "memo", e.target.value)}
                                            />
                                            {journalForm.lines.length > 1 && (
                                                <button type="button" className="ghost" onClick={() => removeJournalLine(index)}>
                                                    {t("common.remove", "Entfernen")}
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                    <button type="button" className="secondary" onClick={addJournalLine}>
                                        {t("accounting.addLine", "Zeile hinzufügen")}
                                    </button>
                                </div>
                                <div className="journal-summary">
                                    <span>{t("accounting.totalDebit", "Soll")}: {formatCurrency(journalTotals.debit)}</span>
                                    <span>{t("accounting.totalCredit", "Haben")}: {formatCurrency(journalTotals.credit)}</span>
                                    <span className={isJournalBalanced ? "badge badge-success" : "badge badge-warning"}>
                                        {t("accounting.difference", "Differenz")}: {formatCurrency(journalTotals.diff)}
                                    </span>
                                </div>
                                <button type="submit" className="primary" disabled={!isJournalBalanced}>
                                    {t("accounting.saveJournal", "Buchung speichern")}
                                </button>
                                {journalAttachment && (
                                    <p className="muted">{t("accounting.attachmentReady", "Beleg wurde ausgewählt.")}</p>
                                )}
                            </form>
                        )}
                    </section>
                )}

                {activeTab === "receivables" && (
                    <section className="card">
                        <h2>{t("accounting.openReceivables", "Offene Debitoren")}</h2>
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("accounting.invoiceNumber", "Beleg")}</th>
                                        <th>{t("accounting.customer", "Kunde")}</th>
                                        <th>{t("accounting.dueDate", "Fällig")}</th>
                                        <th>{t("accounting.aging", "Aging")}</th>
                                        <th className="numeric">{t("accounting.amount", "Betrag")}</th>
                                        <th>{t("accounting.status", "Status")}</th>
                                        <th>{t("accounting.actions", "Aktionen")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredReceivables.map((invoice) => {
                                        const status = determineInvoiceStatus(invoice);
                                        return (
                                            <tr key={invoice.id} onClick={() => setSelectedReceivable(invoice)}>
                                                <td>{invoice.invoiceNumber}</td>
                                                <td>{invoice.customerName}</td>
                                                <td>{formatDate(invoice.dueDate)}</td>
                                                <td>{agingBucketFor(invoice)}</td>
                                                <td className="numeric">{formatCurrency(invoice.openAmount ?? invoice.amount ?? 0)}</td>
                                                <td>
                                                    <span className={`badge badge-${status}`}>
                                                        {status === "paid" && t("accounting.statusPaid", "Bezahlt")}
                                                        {status === "overdue" && t("accounting.statusOverdue", "Überfällig")}
                                                        {status === "due-soon" && t("accounting.statusDueSoon", "Bald fällig")}
                                                        {status === "open" && t("accounting.statusOpen", "Offen")}
                                                    </span>
                                                </td>
                                                <td>
                                                    <button
                                                        type="button"
                                                        className="secondary"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            openPaymentModal(invoice, "receivable");
                                                        }}
                                                    >
                                                        {t("accounting.recordPayment", "Zahlung erfassen")}
                                                    </button>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    {filteredReceivables.length === 0 && (
                                        <tr>
                                            <td colSpan="7">{t("accounting.noReceivables", "Keine offenen Debitoren")}</td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <div className="table-footer">
                            <div className="pagination">
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setReceivablePage((page) => Math.max(page - 1, 0))}
                                    disabled={receivablePage === 0}
                                >
                                    {t("common.previous", "Zurück")}
                                </button>
                                <span>
                                    {receivablePage + 1} / {receivableMeta.totalPages}
                                </span>
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setReceivablePage((page) => Math.min(page + 1, receivableMeta.totalPages - 1))}
                                    disabled={receivablePage >= receivableMeta.totalPages - 1}
                                >
                                    {t("common.next", "Weiter")}
                                </button>
                            </div>
                            <label>
                                {t("common.pageSize", "Einträge")}
                                <select
                                    value={receivablePageSize}
                                    onChange={(e) => setReceivablePageSize(Number(e.target.value))}
                                >
                                    {PAGE_SIZE_OPTIONS.map((size) => (
                                        <option key={size} value={size}>{size}</option>
                                    ))}
                                </select>
                            </label>
                        </div>

                        {selectedReceivable && (
                            <div className="detail-drawer">
                                <div className="detail-header">
                                    <strong>{selectedReceivable.invoiceNumber}</strong>
                                    <button type="button" className="ghost" onClick={() => setSelectedReceivable(null)}>
                                        {t("common.close", "Schließen")}
                                    </button>
                                </div>
                                <div className="detail-body">
                                    <p>{selectedReceivable.customerName}</p>
                                    <p className="muted">
                                        {t("accounting.invoiceDate", "Rechnungsdatum")}: {formatDate(selectedReceivable.invoiceDate)} ·
                                        {" "}{t("accounting.dueDate", "Fällig")}: {formatDate(selectedReceivable.dueDate)}
                                    </p>
                                    <div className="detail-metrics">
                                        <div>
                                            <span className="muted">{t("accounting.amount", "Betrag")}</span>
                                            <strong>{formatCurrency(selectedReceivable.amount ?? 0)}</strong>
                                        </div>
                                        <div>
                                            <span className="muted">{t("accounting.openAmount", "Offen")}</span>
                                            <strong>{formatCurrency(selectedReceivable.openAmount ?? selectedReceivable.amount ?? 0)}</strong>
                                        </div>
                                        <div>
                                            <span className="muted">{t("accounting.amountPaid", "Bezahlt")}</span>
                                            <strong>{formatCurrency(selectedReceivable.amountPaid ?? 0)}</strong>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </section>
                )}

                {activeTab === "payables" && (
                    <section className="card">
                        <h2>{t("accounting.openPayables", "Offene Kreditoren")}</h2>
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>{t("accounting.invoiceNumber", "Beleg")}</th>
                                        <th>{t("accounting.vendor", "Lieferant")}</th>
                                        <th>{t("accounting.dueDate", "Fällig")}</th>
                                        <th>{t("accounting.aging", "Aging")}</th>
                                        <th className="numeric">{t("accounting.amount", "Betrag")}</th>
                                        <th>{t("accounting.status", "Status")}</th>
                                        <th>{t("accounting.actions", "Aktionen")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredPayables.map((invoice) => {
                                        const status = determineInvoiceStatus(invoice);
                                        return (
                                            <tr key={invoice.id} onClick={() => setSelectedPayable(invoice)}>
                                                <td>{invoice.invoiceNumber}</td>
                                                <td>{invoice.vendorName}</td>
                                                <td>{formatDate(invoice.dueDate)}</td>
                                                <td>{agingBucketFor(invoice)}</td>
                                                <td className="numeric">{formatCurrency(invoice.openAmount ?? invoice.amount ?? 0)}</td>
                                                <td>
                                                    <span className={`badge badge-${status}`}>
                                                        {status === "paid" && t("accounting.statusPaid", "Bezahlt")}
                                                        {status === "overdue" && t("accounting.statusOverdue", "Überfällig")}
                                                        {status === "due-soon" && t("accounting.statusDueSoon", "Bald fällig")}
                                                        {status === "open" && t("accounting.statusOpen", "Offen")}
                                                    </span>
                                                </td>
                                                <td>
                                                    <button
                                                        type="button"
                                                        className="secondary"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            openPaymentModal(invoice, "payable");
                                                        }}
                                                    >
                                                        {t("accounting.recordPayment", "Zahlung erfassen")}
                                                    </button>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    {filteredPayables.length === 0 && (
                                        <tr>
                                            <td colSpan="7">{t("accounting.noPayables", "Keine offenen Kreditoren")}</td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                        <div className="table-footer">
                            <div className="pagination">
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setPayablePage((page) => Math.max(page - 1, 0))}
                                    disabled={payablePage === 0}
                                >
                                    {t("common.previous", "Zurück")}
                                </button>
                                <span>
                                    {payablePage + 1} / {payableMeta.totalPages}
                                </span>
                                <button
                                    type="button"
                                    className="ghost"
                                    onClick={() => setPayablePage((page) => Math.min(page + 1, payableMeta.totalPages - 1))}
                                    disabled={payablePage >= payableMeta.totalPages - 1}
                                >
                                    {t("common.next", "Weiter")}
                                </button>
                            </div>
                            <label>
                                {t("common.pageSize", "Einträge")}
                                <select
                                    value={payablePageSize}
                                    onChange={(e) => setPayablePageSize(Number(e.target.value))}
                                >
                                    {PAGE_SIZE_OPTIONS.map((size) => (
                                        <option key={size} value={size}>{size}</option>
                                    ))}
                                </select>
                            </label>
                        </div>

                        {selectedPayable && (
                            <div className="detail-drawer">
                                <div className="detail-header">
                                    <strong>{selectedPayable.invoiceNumber}</strong>
                                    <button type="button" className="ghost" onClick={() => setSelectedPayable(null)}>
                                        {t("common.close", "Schließen")}
                                    </button>
                                </div>
                                <div className="detail-body">
                                    <p>{selectedPayable.vendorName}</p>
                                    <p className="muted">
                                        {t("accounting.invoiceDate", "Rechnungsdatum")}: {formatDate(selectedPayable.invoiceDate)} ·
                                        {" "}{t("accounting.dueDate", "Fällig")}: {formatDate(selectedPayable.dueDate)}
                                    </p>
                                    <div className="detail-metrics">
                                        <div>
                                            <span className="muted">{t("accounting.amount", "Betrag")}</span>
                                            <strong>{formatCurrency(selectedPayable.amount ?? 0)}</strong>
                                        </div>
                                        <div>
                                            <span className="muted">{t("accounting.openAmount", "Offen")}</span>
                                            <strong>{formatCurrency(selectedPayable.openAmount ?? selectedPayable.amount ?? 0)}</strong>
                                        </div>
                                        <div>
                                            <span className="muted">{t("accounting.amountPaid", "Bezahlt")}</span>
                                            <strong>{formatCurrency(selectedPayable.amountPaid ?? 0)}</strong>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </section>
                )}

                {activeTab === "assets" && (
                    <section className="card-grid">
                        <article className="card">
                            <h2>{t("accounting.assetFormTitle", "Neue Anlage")}</h2>
                            <form className="form-grid" onSubmit={handleAssetSubmit}>
                                <label>
                                    {t("accounting.assetName", "Name")}
                                    <input
                                        type="text"
                                        value={assetForm.assetName}
                                        onChange={(e) => setAssetForm({ ...assetForm, assetName: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.acquisitionDate", "Anschaffungsdatum")}
                                    <input
                                        type="date"
                                        value={assetForm.acquisitionDate}
                                        onChange={(e) => setAssetForm({ ...assetForm, acquisitionDate: e.target.value })}
                                    />
                                </label>
                                <label>
                                    {t("accounting.acquisitionCost", "Kosten")}
                                    <input
                                        type="number"
                                        step="0.01"
                                        value={assetForm.acquisitionCost}
                                        onChange={(e) => setAssetForm({ ...assetForm, acquisitionCost: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.usefulLife", "Nutzungsdauer (Monate)")}
                                    <input
                                        type="number"
                                        value={assetForm.usefulLifeMonths}
                                        onChange={(e) => setAssetForm({ ...assetForm, usefulLifeMonths: e.target.value })}
                                        required
                                    />
                                </label>
                                <label>
                                    {t("accounting.residualValue", "Restwert")}
                                    <input
                                        type="number"
                                        step="0.01"
                                        value={assetForm.residualValue}
                                        onChange={(e) => setAssetForm({ ...assetForm, residualValue: e.target.value })}
                                    />
                                </label>
                                <button type="submit" className="primary">{t("accounting.saveAsset", "Speichern")}</button>
                            </form>
                        </article>
                        <article className="card">
                            <h2>{t("accounting.assetList", "Anlagenverzeichnis")}</h2>
                            <div className="table-wrapper">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>{t("accounting.assetName", "Name")}</th>
                                            <th>{t("accounting.acquisitionDate", "Anschaffungsdatum")}</th>
                                            <th className="numeric">{t("accounting.acquisitionCost", "Kosten")}</th>
                                            <th className="numeric">{t("accounting.accumulatedDepreciation", "Kumulierte Abschreibung")}</th>
                                            <th>{t("accounting.status", "Status")}</th>
                                            <th>{t("accounting.actions", "Aktionen")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredAssets.map((asset) => (
                                            <tr key={asset.id} onClick={() => setSelectedAsset(asset)}>
                                                <td>{asset.assetName}</td>
                                                <td>{formatDate(asset.acquisitionDate)}</td>
                                                <td className="numeric">{formatCurrency(asset.acquisitionCost ?? 0)}</td>
                                                <td className="numeric">{formatCurrency(asset.accumulatedDepreciation ?? 0)}</td>
                                                <td>
                                                    <span className={`badge ${asset.status === "ACTIVE" ? "badge-success" : "badge-muted"}`}>
                                                        {asset.status}
                                                    </span>
                                                </td>
                                                <td>
                                                    <button
                                                        type="button"
                                                        className="secondary"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            handleDepreciation(asset.id);
                                                        }}
                                                        disabled={asset.status !== "ACTIVE"}
                                                    >
                                                        {t("accounting.runDepreciation", "Abschreibung buchen")}
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                        {filteredAssets.length === 0 && (
                                            <tr>
                                                <td colSpan="6">{t("accounting.noAssets", "Keine Anlagen erfasst")}</td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>

                            {selectedAsset && (
                                <div className="detail-drawer">
                                    <div className="detail-header">
                                        <strong>{selectedAsset.assetName}</strong>
                                        <button type="button" className="ghost" onClick={() => setSelectedAsset(null)}>
                                            {t("common.close", "Schließen")}
                                        </button>
                                    </div>
                                    <div className="detail-body">
                                        <p className="muted">
                                            {t("accounting.acquisitionDate", "Anschaffungsdatum")}: {formatDate(selectedAsset.acquisitionDate)}
                                        </p>
                                        <div className="detail-metrics">
                                            <div>
                                                <span className="muted">{t("accounting.acquisitionCost", "Kosten")}</span>
                                                <strong>{formatCurrency(selectedAsset.acquisitionCost ?? 0)}</strong>
                                            </div>
                                            <div>
                                                <span className="muted">{t("accounting.accumulatedDepreciation", "Kumulierte Abschreibung")}</span>
                                                <strong>{formatCurrency(selectedAsset.accumulatedDepreciation ?? 0)}</strong>
                                            </div>
                                            <div>
                                                <span className="muted">{t("accounting.status", "Status")}</span>
                                                <strong>{selectedAsset.status}</strong>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </article>
                    </section>
                )}
            </main>

            {paymentModal.open && (
                <div className="modal-backdrop" onClick={(event) => event.target === event.currentTarget && closePaymentModal()}>
                    <div className="modal" ref={modalRef}>
                        <h3>{paymentModal.type === "receivable"
                            ? t("accounting.recordReceivablePayment", "Debitorenzahlung erfassen")
                            : t("accounting.recordPayablePayment", "Kreditorenzahlung erfassen")}</h3>
                        <form onSubmit={handlePaymentSubmit} className="form-grid">
                            <label>
                                {t("accounting.amount", "Betrag")}
                                <input
                                    ref={modalFirstInputRef}
                                    type="number"
                                    step="0.01"
                                    value={paymentModal.amount}
                                    onChange={(e) => setPaymentModal((prev) => ({ ...prev, amount: e.target.value }))}
                                    required
                                />
                            </label>
                            <label>
                                {t("accounting.paymentDate", "Zahlungsdatum")}
                                <input
                                    type="date"
                                    value={paymentModal.paymentDate}
                                    onChange={(e) => setPaymentModal((prev) => ({ ...prev, paymentDate: e.target.value }))}
                                />
                            </label>
                            <label>
                                Memo
                                <input
                                    type="text"
                                    value={paymentModal.memo}
                                    onChange={(e) => setPaymentModal((prev) => ({ ...prev, memo: e.target.value }))}
                                />
                            </label>
                            {paymentError && <p className="form-error">{paymentError}</p>}
                            <div className="modal-actions">
                                <button type="submit" className="primary" disabled={paymentLoading}>
                                    {paymentLoading
                                        ? t("accounting.paymentSaving", "Speichern …")
                                        : t("accounting.recordPayment", "Zahlung erfassen")}
                                </button>
                                <button type="button" className="ghost" onClick={closePaymentModal}>
                                    {t("common.cancel", "Abbrechen")}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminAccountingPage;
