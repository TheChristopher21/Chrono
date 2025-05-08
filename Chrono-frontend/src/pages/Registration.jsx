// src/pages/Registration.jsx
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";

import "../styles/RegistrationScoped.css";

import api from "../utils/api.js";
import { useNotification } from "../context/NotificationContext";

const Registration = () => {
    const navigate = useNavigate();
    const { notify } = useNotification();

    // Zustand für das Formular + gewähltes Paket
    const [form, setForm] = useState({
        companyName: "",
        contactName: "",
        email: "",
        phone: "",
        additionalInfo: "",
    });

    // Paket & Abrechnungsdetails
    const [selectedPackage, setSelectedPackage] = useState("");
    const [employeeCount, setEmployeeCount] = useState(5); // Standardwert
    const [billingPeriod, setBillingPeriod] = useState("monthly"); // "monthly" oder "yearly"

    // UI-States
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    // --- Eingaben im Formular (Firma, Kontakt, etc.) ---
    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    // --- Paket-Auswahl ---
    const handlePackageSelect = (pkg) => {
        setSelectedPackage(pkg);
    };

    // --- Mitarbeiteranzahl & Abrechnung ändern ---
    const handleEmployeeCountChange = (e) => {
        const val = parseInt(e.target.value, 10);
        if (!isNaN(val)) {
            setEmployeeCount(val);
        } else {
            setEmployeeCount(0);
        }
    };

    const handleBillingPeriodChange = (e) => {
        setBillingPeriod(e.target.value); // "monthly" oder "yearly"
    };

    // --- Kostenberechnung ---
    // Hier Beispielwerte aus deinen Paketen (Monatspreise, enthaltene MA, Zusatzkosten pro MA).
    const [calculatedPrice, setCalculatedPrice] = useState(0);

    useEffect(() => {
        setCalculatedPrice(calculatePrice(selectedPackage, employeeCount, billingPeriod));
    }, [selectedPackage, employeeCount, billingPeriod]);

    /**
     * Berechnet den Preis basierend auf:
     *  - ausgewähltem Paket
     *  - Mitarbeiteranzahl
     *  - Abrechnungsintervall (monthly / yearly)
     *
     * Beispielwerte kannst du anpassen.
     */
    function calculatePrice(pkg, empCount, period) {
        if (!pkg) return 0;

        // Default: 0
        let baseMonthly = 0;        // In €/Monat
        let includedEmp = 0;        // Anzahl inkl. Mitarbeiter
        let extraPerEmp = 0;        // € pro Zusatz-MA

        switch (pkg) {
            case "Small Team":
                baseMonthly = 19;
                includedEmp = 5;
                extraPerEmp = 2.5;
                break;
            case "Basic":
                baseMonthly = 49;
                includedEmp = 25;
                extraPerEmp = 2;
                break;
            case "Professional":
                baseMonthly = 99;
                includedEmp = 50;
                extraPerEmp = 1.8;
                break;
            case "Enterprise":
                // Enterprise machen wir "Auf Anfrage" => 0, oder fix 0 → du kannst
                // es so definieren, dass man hier immer 0 anzeigt + "Auf Anfrage"
                return 0;
            default:
                return 0;
        }

        // Zusatzgebühr falls man > includedEmp hat
        const extra = Math.max(0, empCount - includedEmp); // Falls negative -> 0
        const monthlyCost = baseMonthly + extra * extraPerEmp;

        // Falls yearly => 10x monatl. statt 12
        if (period === "yearly") {
            return monthlyCost * 10; // 10 Monate zahlen statt 12
        } else {
            return monthlyCost; // normaler Monatspreis
        }
    }

    // --- Absenden ---
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPackage) {
            setError("Bitte wählen Sie ein Paket aus.");
            return;
        }
        setIsSubmitting(true);
        try {
            // Payload vorbereiten: alle Formularfelder + gewählte Infos
            const payload = {
                ...form,
                chosenPackage: selectedPackage,
                employeeCount: employeeCount,
                billingPeriod: billingPeriod, // monthly oder yearly
                calculatedPrice: calculatedPrice, // z. B. 49 / 490 ...
            };

            const response = await api.post("/api/apply", payload);
            if (response.data.success) {
                setSuccess(true);
                notify(
                    "Ihre Bewerbung wurde erfolgreich gesendet. Wir melden uns per E‑Mail bei Ihnen."
                );
                // Weiterleitung nach 3s zur Login-Seite
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

    return (
        <>
            <Navbar />

            {/* WICHTIG: Wrapper mit .registration-page.scoped-registration */}
            <div className="registration-page scoped-registration">
                <div className="registration-content">

                    {/* Paket-Übersicht */}
                    <div className="pricing-section">
                        <h2>Unsere Preise &amp; Pakete</h2>
                        <p className="pricing-intro">
                            Wählen Sie das passende Paket für Ihr Unternehmen.
                            Alle Pakete bieten NFC-basiertes Stempeln, automatische Zeiterfassung
                            (WORK_START, BREAK, etc.), Urlaubsverwaltung und eine Admin-Oberfläche.
                            <br />
                            Sie können monatlich oder jährlich zahlen.
                            Bei jährlicher Zahlung erhalten Sie 2&nbsp;Monate gratis (12 zum Preis von 10).
                        </p>

                        {/* Abrechnung (monatlich/jährlich) */}
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
                                Jährlich (2 Monate geschenkt)
                            </label>
                        </div>

                        <div className="pricing-cards">
                            {/* Small-Team-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === "Small Team" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Small Team")}
                            >
                                <h3>Small Team</h3>
                                <p className="price-line">
                                    <strong>19 €/Monat*</strong> <br />
                                    <span className="yearly-hint">oder 190 €/Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 5 Mitarbeiter inkl.</li>
                                    <li>+2,50 € je zusätzlichem MA</li>
                                    <li>NFC-Stempeln &amp; Urlaubsverwaltung</li>
                                </ul>
                            </div>

                            {/* Basic-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === "Basic" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Basic")}
                            >
                                <h3>Basic</h3>
                                <p className="price-line">
                                    <strong>49 €/Monat*</strong> <br />
                                    <span className="yearly-hint">oder 490 €/Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 25 Mitarbeiter inkl.</li>
                                    <li>+2 € je zusätzlichem MA</li>
                                    <li>Auto-Punch-Out &amp; einfache Berichte</li>
                                </ul>
                            </div>

                            {/* Professional-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === "Professional" ? "selected" : ""}`}
                                onClick={() => handlePackageSelect("Professional")}
                            >
                                <h3>Professional</h3>
                                <p className="price-line">
                                    <strong>99 €/Monat*</strong> <br />
                                    <span className="yearly-hint">oder 990 €/Jahr</span>
                                </p>
                                <ul>
                                    <li>Bis 50 Mitarbeiter inkl.</li>
                                    <li>+1,80 € je zusätzlichem MA</li>
                                    <li>Alle Basic-Features + Premium-Support</li>
                                    <li>Erweiterte Berichte &amp; Statistik</li>
                                </ul>
                            </div>

                            {/* Enterprise-Karte */}

                        </div>

                        <p className="pricing-footnote">
                            * Preisangaben netto, zzgl. MwSt. &nbsp; | &nbsp; Jährliche Zahlung entspricht
                            10 Monaten zum Monatspreis (2 Monate geschenkt).
                        </p>
                    </div>

                    {/* Bewerbungsformular */}
                    <div className="application-section">
                        <h2>Firmenbewerbung</h2>

                        {error && <p className="error-message">{error}</p>}
                        {success ? (
                            <p className="success-message">
                                Ihre Bewerbung wurde erfolgreich gesendet. Wir werden uns in Kürze per E-Mail
                                bei Ihnen melden.
                            </p>
                        ) : (
                            <form onSubmit={handleSubmit}>

                                {/* Eingabe Firma + Kontakt */}
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
                                    placeholder="Telefonnummer (optional)"
                                />

                                {/* Mitarbeiteranzahl-Eingabe */}
                                {selectedPackage !== "Enterprise" && (
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
                                    placeholder="Weitere Informationen (z. B. Anforderungen, Fragen, etc.)"
                                    rows="5"
                                    required
                                />

                                {/* Preis-Vorschau (sofern kein Enterprise) */}
                                {selectedPackage && selectedPackage !== "Enterprise" && (
                                    <div className="price-preview">
                                        <strong>Preis:</strong>{" "}
                                        {calculatedPrice > 0
                                            ? `${calculatedPrice.toFixed(2).replace(".", ",")} € ${
                                                billingPeriod === "monthly" ? "pro Monat" : "pro Jahr"
                                            }`
                                            : "—"}
                                    </div>
                                )}

                                <button type="submit" disabled={isSubmitting}>
                                    {isSubmitting ? "Wird gesendet..." : "Bewerbung absenden"}
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </div>
        </>
    );
};

export default Registration;
