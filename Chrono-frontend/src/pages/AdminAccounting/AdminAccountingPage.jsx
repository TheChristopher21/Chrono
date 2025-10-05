import React, { useEffect, useMemo, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";
import "../../styles/AdminAccountingPageScoped.css";


const initialAccount = {
    code: "",
    name: "",
    type: "ASSET"
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
    const [journalForm, setJournalForm] = useState({
        entryDate: "",
        description: "",
        documentReference: "",
        lines: [{ accountId: "", debit: "", credit: "", memo: "" }]
    });
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

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [accRes, journalRes, recvRes, payRes, assetRes] = await Promise.all([
                    api.get("/api/accounting/accounts"),
                    api.get("/api/accounting/journal"),
                    api.get("/api/accounting/receivables/open"),
                    api.get("/api/accounting/payables/open"),
                    api.get("/api/accounting/assets")
                ]);
                setAccounts(accRes.data ?? []);
                const journal = journalRes.data?.content ?? journalRes.data ?? [];
                setJournalEntries(journal);
                const receivableData = recvRes.data?.content ?? recvRes.data ?? [];
                const payableData = payRes.data?.content ?? payRes.data ?? [];
                setReceivables(receivableData);
                setPayables(payableData);
                setAssets(assetRes.data ?? []);
            } catch (error) {
                console.error("Failed to load accounting data", error);
                notify(t("accounting.loadError", "Finanzdaten konnten nicht geladen werden."), "error");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [notify, t, refreshFlag]);

    const totalReceivables = useMemo(() => receivables.reduce((sum, invoice) => sum + (invoice.amount ?? 0), 0), [receivables]);
    const totalPayables = useMemo(() => payables.reduce((sum, invoice) => sum + (invoice.amount ?? 0), 0), [payables]);

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
            const lines = prev.lines.map((line, idx) => (idx === index ? { ...line, [field]: value } : line));
            return { ...prev, lines };
        });
    };

    const removeJournalLine = (index) => {
        setJournalForm((prev) => ({
            ...prev,
            lines: prev.lines.filter((_, idx) => idx !== index)
        }));
    };

    const handleJournalSubmit = async (event) => {
        event.preventDefault();
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
    };

    const closePaymentModal = () => {
        setPaymentModal({ open: false, type: "receivable", invoice: null, amount: "", paymentDate: "", memo: "" });
    };

    const handlePaymentSubmit = async (event) => {
        event.preventDefault();
        if (!paymentModal.invoice) {
            return;
        }
        const endpoint = paymentModal.type === "receivable"
            ? `/api/accounting/receivables/${paymentModal.invoice.id}/payments`
            : `/api/accounting/payables/${paymentModal.invoice.id}/payments`;
        try {
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
            notify(t("accounting.paymentFailed", "Zahlung konnte nicht verbucht werden."), "error");
        }
    };

    return (
        <div className="admin-page accounting-page">

            <Navbar />
            <main className="admin-content">
                <header className="admin-header">
                    <h1>{t("accounting.title", "Finanzbuchhaltung")}</h1>
                    <p className="muted">{t("accounting.subtitle", "Überblick über Konten, Buchungen und offene Posten")}</p>
                </header>

                <section className="card-grid">
                    <article className="card">
                        <h2>{t("accounting.totalReceivables", "Offene Debitoren")}</h2>
                        <p className="metric">CHF {totalReceivables.toFixed(2)}</p>
                    </article>
                    <article className="card">
                        <h2>{t("accounting.totalPayables", "Offene Kreditoren")}</h2>
                        <p className="metric">CHF {totalPayables.toFixed(2)}</p>
                    </article>
                </section>

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
                                        <th>{t("accounting.accountCode", "Kontonummer")}</th>
                                        <th>{t("accounting.accountName", "Kontoname")}</th>
                                        <th>{t("accounting.accountType", "Kontotyp")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {accounts.map((account) => (
                                        <tr key={account.id}>
                                            <td>{account.code}</td>
                                            <td>{account.name}</td>
                                            <td>{account.type}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section className="card">
                    <h2>{t("accounting.journal", "Journal")}</h2>
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>{t("accounting.date", "Datum")}</th>
                                    <th>{t("accounting.description", "Beschreibung")}</th>
                                    <th>{t("accounting.source", "Quelle")}</th>
                                    <th>{t("accounting.lines", "Zeilen")}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {journalEntries.map((entry) => (
                                    <tr key={entry.id}>
                                        <td>{entry.entryDate}</td>
                                        <td>{entry.description}</td>
                                        <td>{entry.source}</td>
                                        <td>
                                            {(entry.lines ?? []).map((line) => (
                                                <div key={line.id ?? `${line.account?.code}-${line.memo}`}>
                                                    <strong>{line.account?.code}</strong> {line.memo}
                                                    {" "}
                                                    (D {Number(line.debit ?? 0).toFixed(2)} / C {Number(line.credit ?? 0).toFixed(2)})
                                                </div>
                                            ))}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section className="card-grid">
                    <article className="card">
                        <h3>{t("accounting.openReceivables", "Offene Debitoren")}</h3>
                        <ul className="list-unstyled">
                            {receivables.map((invoice) => (
                                <li key={invoice.id}>
                                    <div className="invoice-line">
                                        <div>
                                            {invoice.invoiceNumber} – {invoice.customerName} – CHF {Number(invoice.openAmount ?? invoice.amount ?? 0).toFixed(2)}
                                            {invoice.amountPaid > 0 && (
                                                <span className="muted"> ({t("accounting.paid", "bezahlt")}: CHF {Number(invoice.amountPaid).toFixed(2)})</span>
                                            )}
                                        </div>
                                        <button type="button" className="secondary" onClick={() => openPaymentModal(invoice, "receivable")}>
                                            {t("accounting.recordPayment", "Zahlung erfassen")}
                                        </button>
                                    </div>
                                </li>
                            ))}
                            {receivables.length === 0 && <li>{t("accounting.noReceivables", "Keine offenen Debitoren")}</li>}
                        </ul>
                    </article>
                    <article className="card">
                        <h3>{t("accounting.openPayables", "Offene Kreditoren")}</h3>
                        <ul className="list-unstyled">
                            {payables.map((invoice) => (
                                <li key={invoice.id}>
                                    <div className="invoice-line">
                                        <div>
                                            {invoice.invoiceNumber} – {invoice.vendorName} – CHF {Number(invoice.openAmount ?? invoice.amount ?? 0).toFixed(2)}
                                            {invoice.amountPaid > 0 && (
                                                <span className="muted"> ({t("accounting.paid", "bezahlt")}: CHF {Number(invoice.amountPaid).toFixed(2)})</span>
                                            )}
                                        </div>
                                        <button type="button" className="secondary" onClick={() => openPaymentModal(invoice, "payable")}>
                                            {t("accounting.recordPayment", "Zahlung erfassen")}
                                        </button>
                                    </div>
                                </li>
                            ))}
                            {payables.length === 0 && <li>{t("accounting.noPayables", "Keine offenen Kreditoren")}</li>}
                        </ul>
                    </article>
                </section>

                <section className="card">
                    <div className="section-header">
                        <h2>{t("accounting.manualJournal", "Manuelle Buchung")}</h2>
                        <button type="button" className="secondary" onClick={() => setShowJournalForm((prev) => !prev)}>
                            {showJournalForm
                                ? t("accounting.closeJournalForm", "Schließen")
                                : t("accounting.newJournalEntry", "Neue Buchung")}
                        </button>
                    </div>
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
                                            placeholder={t("accounting.memo", "Memo")}
                                            value={line.memo}
                                            onChange={(e) => updateJournalLine(index, "memo", e.target.value)}
                                            required
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
                            <button type="submit" className="primary">
                                {t("accounting.saveJournal", "Buchung speichern")}
                            </button>
                        </form>
                    )}
                </section>

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
                                        <th>{t("accounting.acquisitionCost", "Kosten")}</th>
                                        <th>{t("accounting.accumulatedDepreciation", "Kumulierte Abschreibung")}</th>
                                        <th>{t("accounting.status", "Status")}</th>
                                        <th>{t("accounting.actions", "Aktionen")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {assets.map((asset) => (
                                        <tr key={asset.id}>
                                            <td>{asset.assetName}</td>
                                            <td>{asset.acquisitionDate}</td>
                                            <td>CHF {Number(asset.acquisitionCost ?? 0).toFixed(2)}</td>
                                            <td>CHF {Number(asset.accumulatedDepreciation ?? 0).toFixed(2)}</td>
                                            <td>{asset.status}</td>
                                            <td>
                                                <button
                                                    type="button"
                                                    className="secondary"
                                                    onClick={() => handleDepreciation(asset.id)}
                                                    disabled={asset.status !== "ACTIVE"}
                                                >
                                                    {t("accounting.runDepreciation", "Abschreibung buchen")}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    {assets.length === 0 && (
                                        <tr>
                                            <td colSpan="6">{t("accounting.noAssets", "Keine Anlagen erfasst")}</td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </article>
                </section>
            </main>

            {paymentModal.open && (
                <div className="modal-backdrop">
                    <div className="modal">
                        <h3>{paymentModal.type === "receivable"
                            ? t("accounting.recordReceivablePayment", "Debitorenzahlung erfassen")
                            : t("accounting.recordPayablePayment", "Kreditorenzahlung erfassen")}</h3>
                        <form onSubmit={handlePaymentSubmit} className="form-grid">
                            <label>
                                {t("accounting.amount", "Betrag")}
                                <input
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
                            <div className="modal-actions">
                                <button type="submit" className="primary">{t("accounting.recordPayment", "Zahlung erfassen")}</button>
                                <button type="button" className="ghost" onClick={closePaymentModal}>{t("common.cancel", "Abbrechen")}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminAccountingPage;
