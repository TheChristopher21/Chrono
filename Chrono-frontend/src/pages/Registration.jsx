// src/pages/Registration.jsx
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import { Link } from "react-router-dom";

import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";

const Registration = () => {
    const navigate = useNavigate();
    const { notify } = useNotification();

    const [form, setForm] = useState({
        companyName: "",
        contactName: "",
        email: "",
        phone: "",
        additionalInfo: "",
    });

    // Nur noch 3 Pakete (Small / Basic / Professional)
    const [selectedPackage, setSelectedPackage] = useState("");
    const [employeeCount, setEmployeeCount] = useState(5);
    const [billingPeriod, setBillingPeriod] = useState("monthly"); // "monthly" oder "yearly"

    // Preise in CHF
    const [calculatedPrice, setCalculatedPrice] = useState(0);

    // Einmalige Installationskosten (in CHF)
    const INSTALL_FEE = 150;

    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    // Eingaben für das Formular (Firma, Kontakt, etc.)
    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    // Paket auswählen
    const handlePackageSelect = (pkg) => {
        setSelectedPackage(pkg);
    };

    // Mitarbeiterzahl
    const handleEmployeeCountChange = (e) => {
        const val = parseInt(e.target.value, 10);
        if (!isNaN(val)) setEmployeeCount(val);
    };

    // Abrechnungsmodus (monatlich / jährlich)
    const handleBillingPeriodChange = (e) => {
        setBillingPeriod(e.target.value);
    };

    /**
     * 3 Pakete in CHF:
     *   - Small: 19 CHF/Monat, bis 5 MA, +3 CHF pro extra-MA
     *   - Basic: 49 CHF/Monat, bis 25 MA, +2.5 CHF pro extra-MA
     *   - Professional: 99 CHF/Monat, bis 50 MA, +2.0 CHF pro extra-MA
     *
     * Bei yearly: 2 Monate geschenkt => 10 * (Monatspreis).
     */
    function calculatePrice(pkg, empCount, period) {
        let baseMonthly = 0;
        let includedEmp = 0;
        let extraPerEmp = 0;

        switch (pkg) {
            case "Small":
                baseMonthly = 19;
                includedEmp = 5;
                extraPerEmp = 3.0;
                break;
            case "Basic":
                baseMonthly = 49;
                includedEmp = 25;
                extraPerEmp = 2.5;
                break;
            case "Professional":
                baseMonthly = 99;
                includedEmp = 50;
                extraPerEmp = 2.0;
                break;
            default:
                return 0;
        }

        const extraMA = Math.max(0, empCount - includedEmp);
        let monthlyCost = baseMonthly + extraMA * extraPerEmp;

        // Jährlich => 10 * monthly
        if (period === "yearly") {
            monthlyCost = monthlyCost * 10;
        }
        // Endpreis = Packet-Preis plus die einmalige Installationsgebühr
        return monthlyCost + INSTALL_FEE;
    }

    // Re-Berechnung bei Änderungen
    useEffect(() => {
        setCalculatedPrice(calculatePrice(selectedPackage, employeeCount, billingPeriod));
    }, [selectedPackage, employeeCount, billingPeriod]);

    // Absenden
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPackage) {
            setError("Bitte wählen Sie ein Paket aus.");
            return;
        }
        setIsSubmitting(true);
        try {
            const payload = {
                ...form,
                chosenPackage: selectedPackage,
                employeeCount,
                billingPeriod,
                calculatedPrice,
            };
            const response = await api.post("/api/apply", payload);
            if (response.data.success) {
                setSuccess(true);
                notify("Ihre Bewerbung wurde erfolgreich gesendet. Wir melden uns per E-Mail.");
                setTimeout(() => {
                    navigate("/login", { replace: true });
                }, 3000);
            } else {
                setError(response.data.message || "Bewerbung fehlgeschlagen");
            }
        } catch (err) {
            setError("Fehler: " + err.message);
        } finally {
            setIsSubmitting(false);
        }
    };

    // Format-Helfer, um in der Vorschau "xxx.xx CHF" auszugeben
    function formatCHF(value) {
        return value.toFixed(2).replace(".", ",") + " CHF";
    }

    return (
        <>
            <Navbar />

            <div className="registration-page scoped-registration">
                <div className="registration-content">
                    {/* Pakete + Preise */}
                    <div className="pricing-section">
                        <h2>Unsere Preise &amp; Pakete</h2>
                        <p className="pricing-intro">
                            NFC-Stempeln, automatische Zeiterfassung und Berichte sind in jedem Paket enthalten.
                            <br />
                            Der Unterschied liegt vor allem in der Anzahl mitgelieferter Mitarbeiterplätze und dem Support-Level.
                        </p>

                        <div className="billing-toggle">
                            <label>
                                <input
                                    type="radio"
                                    name="billingPeriod"
                                    value="monthly"
                                    checked={billingPeriod === "monthly"}
                                    onChange={handleBillingPeriodChange}
                                />
                                Monatlich
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    name="billingPeriod"
                                    value="yearly"
                                    checked={billingPeriod === "yearly"}
                                    onChange={handleBillingPeriodChange}
                                />
                                Jährlich (2 Monate gratis)
                            </label>
                        </div>

                        <div className="pricing-cards">
                            {/* Small */}
                            <div
                                className={`pricing-card ${selectedPackage === "Small" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Small")}
                            >
                                <h3>Small</h3>
                                <p className="price-line">
                                    <strong>19 CHF / Monat*</strong><br />
                                    <span className="yearly-hint">oder 190 CHF / Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 5 Mitarbeiter inklusive</li>
                                    <li>E-Mail-Support (Mo-Fr)</li>
                                    <li>Alle Berichte &amp; Urlaubsverwaltung</li>
                                </ul>
                            </div>

                            {/* Basic */}
                            <div
                                className={`pricing-card ${selectedPackage === "Basic" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Basic")}
                            >
                                <h3>Basic</h3>
                                <p className="price-line">
                                    <strong>49 CHF / Monat*</strong><br />
                                    <span className="yearly-hint">oder 490 CHF / Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 25 Mitarbeiter inklusive</li>
                                    <li>E-Mail + Chat-Support (Mo-Fr 8-18 Uhr)</li>
                                    <li>Alle Berichte &amp; Urlaubsverwaltung</li>
                                </ul>
                            </div>

                            {/* Professional */}
                            <div
                                className={`pricing-card ${selectedPackage === "Professional" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Professional")}
                            >
                                <h3>Professional</h3>
                                <p className="price-line">
                                    <strong>99 CHF / Monat*</strong><br />
                                    <span className="yearly-hint">oder 990 CHF / Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 50 Mitarbeiter inklusive</li>
                                    <li>Premium-Support (E-Mail, Chat &amp; Telefon)</li>
                                    <li>Alle Berichte &amp; Urlaubsverwaltung</li>
                                </ul>
                            </div>
                        </div>

                        <p className="pricing-footnote">
                            * Alle Preise netto zzgl. MwSt.
                            &nbsp;|&nbsp; Bei jährlicher Zahlung nur 10 statt 12 Monatsraten.
                        </p>
                    </div>

                    {/* Formular */}
                    <div className="application-section">
                        <h2>Firmen-Anmeldung</h2>

                        {error && <p className="error-message">{error}</p>}
                        {success ? (
                            <p className="success-message">
                                Ihre Bewerbung wurde erfolgreich gesendet. Wir melden uns in Kürze per E-Mail.
                            </p>
                        ) : (
                            <form onSubmit={handleSubmit}>
                                <input
                                    type="text"
                                    name="companyName"
                                    value={form.companyName}
                                    onChange={handleChange}
                                    placeholder="Firmenname"
                                    required
                                />
                                <input
                                    type="text"
                                    name="contactName"
                                    value={form.contactName}
                                    onChange={handleChange}
                                    placeholder="Ansprechpartner"
                                    required
                                />
                                <input
                                    type="email"
                                    name="email"
                                    value={form.email}
                                    onChange={handleChange}
                                    placeholder="E-Mail"
                                    required
                                />
                                <input
                                    type="text"
                                    name="phone"
                                    value={form.phone}
                                    onChange={handleChange}
                                    placeholder="Telefon (optional)"
                                />

                                {selectedPackage && (
                                    <div className="emp-count-wrapper">
                                        <label htmlFor="employeeCount">Geschätzte Mitarbeiteranzahl:</label>
                                        <input
                                            type="number"
                                            id="employeeCount"
                                            name="employeeCount"
                                            min="1"
                                            value={employeeCount}
                                            onChange={handleEmployeeCountChange}
                                        />
                                    </div>
                                )}

                                <textarea
                                    name="additionalInfo"
                                    value={form.additionalInfo}
                                    onChange={handleChange}
                                    placeholder="Weitere Informationen oder Fragen..."
                                    rows="4"
                                />

                                {selectedPackage && (
                                    <div className="price-preview">
                                        <strong>Voraussichtlicher Preis:</strong>&nbsp;
                                        {calculatedPrice > 0 ? (
                                            <>
                                                {formatCHF(calculatedPrice)}{" "}
                                                {billingPeriod === "monthly" ? "pro Monat (inkl. 150 CHF Installation)" : "pro Jahr (inkl. 150 CHF Installation)"}
                                            </>
                                        ) : (
                                            "—"
                                        )}
                                        <br />
                                        <span style={{ fontSize: "0.85rem", color: "var(--c-muted)" }}>
                      Einmalige Installationsgebühr: {formatCHF(150)}
                    </span>
                                    </div>
                                )}

                                <button type="submit" disabled={isSubmitting}>
                                    {isSubmitting ? "Wird gesendet..." : "Bewerbung absenden"}
                                </button>
                            </form>
                        )}
                    </div>

                    {/* Impressum/AGB, etc. */}
                    <div style={{ marginTop: "40px", textAlign: "center", fontSize: "0.9rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>Impressum</Link>
                        <Link to="/agb">AGB</Link>
                    </div>
                </div>
            </div>
        </>
    );
};

export default Registration;
