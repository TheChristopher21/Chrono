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
    const [loading, setLoading] = useState(true);
    const [accountForm, setAccountForm] = useState(initialAccount);
    const [refreshFlag, setRefreshFlag] = useState(0);

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const [accRes, journalRes, recvRes, payRes] = await Promise.all([
                    api.get("/api/accounting/accounts"),
                    api.get("/api/accounting/journal"),
                    api.get("/api/accounting/receivables/open"),
                    api.get("/api/accounting/payables/open")
                ]);
                setAccounts(accRes.data ?? []);
                const journal = journalRes.data?.content ?? journalRes.data ?? [];
                setJournalEntries(journal);
                setReceivables(recvRes.data ?? []);
                setPayables(payRes.data ?? []);
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
                                    {invoice.invoiceNumber} – {invoice.customerName} – CHF {Number(invoice.amount ?? 0).toFixed(2)}
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
                                    {invoice.invoiceNumber} – {invoice.vendorName} – CHF {Number(invoice.amount ?? 0).toFixed(2)}
                                </li>
                            ))}
                            {payables.length === 0 && <li>{t("accounting.noPayables", "Keine offenen Kreditoren")}</li>}
                        </ul>
                    </article>
                </section>
            </main>
        </div>
    );
};

export default AdminAccountingPage;
