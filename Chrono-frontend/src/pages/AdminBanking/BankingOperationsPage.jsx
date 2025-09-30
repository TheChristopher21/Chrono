import React, { useCallback, useEffect, useState } from "react";
import Navbar from "../../components/Navbar.jsx";
import api from "../../utils/api.js";
import { useNotification } from "../../context/NotificationContext.jsx";
import { useTranslation } from "../../context/LanguageContext.jsx";

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
    const [accountForm, setAccountForm] = useState(initialAccount);

    const load = useCallback(async () => {
        try {
            const [accountRes, batchRes] = await Promise.all([
                api.get("/api/banking/accounts"),
                api.get("/api/banking/batches/open")
            ]);
            setAccounts(accountRes.data ?? []);
            setBatches(batchRes.data ?? []);
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

    return (
        <div className="admin-page">
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
                    <h2>{t("banking.pendingBatches", "Offene Zahlungsaufträge")}</h2>
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
                                        <td>{batch.instructions?.length ?? 0}</td>
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
            </main>
        </div>
    );
};

export default BankingOperationsPage;
