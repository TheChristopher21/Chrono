// src/pages/Registration.jsx
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import { Link } from "react-router-dom";
import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";

// 🟦 Pakete neu benannt und Micro-Paket integriert
const PACKAGE_CONFIG = {
    Micro: {
        name: "Micro",
        baseMonthly: 0,
        perEmployeeMonthly: 7,
        min: 1,
        max: 3,
        support: "E-Mail Support (Antwort i.d.R. innerhalb 48h)",
        color: "#7952b3",
        bestFor: "Ideal für Startups & Kleinstbetriebe bis 3 Mitarbeiter.",
        badge: "Neu & günstig für kleine Teams!"
    },
    Starter: {
        name: "Starter",
        baseMonthly: 20,
        perEmployeeMonthly: 5,
        min: 1,
        max: 20,
        support: "Standard E-Mail Support (Antwort i.d.R. innerhalb 48h)",
        color: "#007bff",
        bestFor: "Für kleine Teams und Handwerksbetriebe.",
    },
    Business: {
        name: "Business",
        baseMonthly: 60,
        perEmployeeMonthly: 4.5,
        min: 5,
        max: 50,
        support: "Priorisierter E-Mail Support (Antwort i.d.R. innerhalb 24h)",
        color: "#17a2b8",
        bestFor: "Für Unternehmen mit mehreren Teams & klaren Prozessen.",
        badge: "Beliebteste Wahl"
    },
    Premium: {
        name: "Premium",
        baseMonthly: 160,
        perEmployeeMonthly: 4,
        min: 15,
        max: 100,
        support: "Premium E-Mail & Telefon-Support (Schnelle Reaktionszeiten), Persönlicher Ansprechpartner.",
        color: "#28a745",
        bestFor: "Für wachsende Unternehmen & höchste Ansprüche.",
    }
};

const INSTALL_FEE = 150;
const OPTIONAL_TRAINING_COST = 120;

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

    const [selectedPackageName, setSelectedPackageName] = useState("");
    const [employeeCount, setEmployeeCount] = useState(1);
    const [billingPeriod, setBillingPeriod] = useState("monthly");
    const [calculatedPrice, setCalculatedPrice] = useState(0);
    const [priceBreakdown, setPriceBreakdown] = useState(null);

    const [includeOptionalTraining, setIncludeOptionalTraining] = useState(false);

    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    // -------- Preislogik angepasst für Micro-Paket
    function calculatePriceDetails(pkgName, empCount, period, trainingSelected) {
        if (!pkgName || !PACKAGE_CONFIG[pkgName]) {
            return { total: 0, breakdown: null };
        }

        const pkg = PACKAGE_CONFIG[pkgName];

        // Paketlimits prüfen
        if (empCount < pkg.min || empCount > pkg.max) {
            return { total: 0, breakdown: null };
        }

        const serviceFeeMonthly = pkg.baseMonthly;
        const employeeCostMonthly = empCount * pkg.perEmployeeMonthly;
        let packageMonthlyTotal = serviceFeeMonthly + employeeCostMonthly;

        let currentPeriodFactor = 1;
        let periodText = "/ Monat";
        if (period === "yearly") {
            packageMonthlyTotal *= 10;
            currentPeriodFactor = 10;
            periodText = "/ Jahr (entspricht 10 Monatsraten)";
        }

        let optionalTrainingCostTotal = trainingSelected ? OPTIONAL_TRAINING_COST : 0;
        const totalForFirstPayment = packageMonthlyTotal + INSTALL_FEE + optionalTrainingCostTotal;

        return {
            total: totalForFirstPayment,
            breakdown: {
                serviceFee: period === 'yearly' ? serviceFeeMonthly * currentPeriodFactor : serviceFeeMonthly,
                employeeCost: period === 'yearly' ? employeeCostMonthly * currentPeriodFactor : employeeCostMonthly,
                packageSubtotal: packageMonthlyTotal,
                installationFee: INSTALL_FEE,
                optionalTrainingCost: optionalTrainingCostTotal,
                billingPeriodText: periodText,
                isYearly: period === 'yearly',
            }
        };
    }

    useEffect(() => {
        if (!selectedPackageName) {
            setCalculatedPrice(0);
            setPriceBreakdown(null);
            return;
        }
        const { total, breakdown } = calculatePriceDetails(
            selectedPackageName,
            employeeCount,
            billingPeriod,
            includeOptionalTraining
        );
        setCalculatedPrice(total);
        setPriceBreakdown(breakdown);
    }, [selectedPackageName, employeeCount, billingPeriod, includeOptionalTraining]);

    // Paket- und Mitarbeiter-Validierung
    const getPackageLimits = (pkg) => [pkg.min, pkg.max];

    const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });
    const handlePackageSelect = (pkgName) => setSelectedPackageName(pkgName);

    const handleEmployeeCountChange = (e) => {
        let val = parseInt(e.target.value, 10);
        if (isNaN(val) || val < 1) val = 1;
        if (val > 100) val = 100;
        setEmployeeCount(val);
    };
    const handleBillingPeriodChange = (e) => setBillingPeriod(e.target.value);
    const handleOptionalTrainingChange = (e) => setIncludeOptionalTraining(e.target.checked);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPackageName) {
            setError("Bitte wählen Sie ein Paket aus.");
            return;
        }
        const pkg = PACKAGE_CONFIG[selectedPackageName];
        if (employeeCount < pkg.min || employeeCount > pkg.max) {
            setError(`Das gewählte Paket ist für ${pkg.min}-${pkg.max} Mitarbeiter verfügbar.`);
            return;
        }
        setIsSubmitting(true);
        setError("");
        try {
            const payload = {
                ...form,
                chosenPackage: selectedPackageName,
                employeeCount,
                billingPeriod,
                includeOptionalTraining,
                optionalTrainingCost: includeOptionalTraining ? OPTIONAL_TRAINING_COST : 0,
                totalCalculatedFirstPayment: calculatedPrice,
                priceBreakdown: priceBreakdown,
                installationFee: INSTALL_FEE,
                packageBaseFeeMonthly: pkg.baseMonthly,
                packagePerEmployeeMonthly: pkg.perEmployeeMonthly,
            };
            await api.post("/api/apply", payload);
            setSuccess(true);
            notify("Anfrage erfolgreich gesendet! Sie erhalten in Kürze Ihr individuelles Angebot.", "success");
        } catch (err) {
            setError("Fehler bei der Übermittlung: " + (err.response?.data?.message || err.message));
            notify("Fehler bei der Übermittlung: " + (err.message), "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    function formatCHF(value) {
        if (typeof value !== 'number' || isNaN(value)) {
            return "0,00 CHF";
        }
        return value.toFixed(2).replace(".", ",") + " CHF";
    }

    // --------- HERO/INTRO UND UI ------------------------------------
    return (
        <>
            <Navbar />
            <div className="registration-page scoped-registration">
                <div className="registration-content">
                    <section className="hero-section">
                        <h1>Alles drin. Alles einfach. Alles Chrono.</h1>
                        <p className="hero-subline">
                            <strong>Zeiterfassung, Urlaubsmanagement und Lohnabrechnung in einem modernen, sicheren System.</strong>
                            <br />Wählen Sie das passende Paket und erhalten Sie unverbindlich Ihr Angebot.
                        </p>
                    </section>

                    <section className="unique-sell-callout">
                        <span role="img" aria-label="star">⭐</span>
                        <strong>Lohnabrechnung & Gehalts-PDFs inklusive!</strong> Kein Aufpreis – alles schon im Preis.
                        <span className="unique-badge">Ihr Rundum-sorglos-Paket.</span>
                    </section>

                    <section className="pricing-section">
                        <h2>Pakete & Preise</h2>
                        <p className="pricing-intro">
                            Für jede Unternehmensgröße das passende Modell – monatlich oder jährlich, jederzeit kündbar.
                            <br /><span className="money-back">100 % unverbindlich – Sie zahlen erst nach Vertragsabschluss!</span>
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
                                Monatliche Zahlung
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    name="billingPeriod"
                                    value="yearly"
                                    checked={billingPeriod === "yearly"}
                                    onChange={handleBillingPeriodChange}
                                />
                                Jährliche Zahlung <span className="deal-badge">2 Monate geschenkt!</span>
                            </label>
                        </div>

                        <div className="pricing-cards">
                            {Object.values(PACKAGE_CONFIG).map((pkg) => {
                                const [min, max] = getPackageLimits(pkg);
                                const active = selectedPackageName === pkg.name;
                                return (
                                    <div
                                        key={pkg.name}
                                        className={`pricing-card ${active ? "selected" : ""}`}
                                        onClick={() => setEmployeeCount(Math.max(employeeCount, min)) || handlePackageSelect(pkg.name)}
                                        style={{ '--card-accent-color': active ? pkg.color : 'var(--c-border)' }}
                                    >
                                        <div className="card-badge-row">
                                            {pkg.badge && <span className="card-badge">{pkg.badge}</span>}
                                            {active && <span className="selected-badge">Ausgewählt</span>}
                                        </div>
                                        <h3 style={{ color: active ? pkg.color : 'var(--c-text-strong)' }}>{pkg.name}</h3>
                                        <p className="price-line base-fee">
                                            Grundgebühr:<br /><strong>{formatCHF(pkg.baseMonthly)} / Monat</strong>
                                        </p>
                                        <p className="price-line employee-rate">
                                            Pro Mitarbeiter:<br /><strong>+ {formatCHF(pkg.perEmployeeMonthly)} / Monat</strong>
                                        </p>
                                        <p className="card-hint">
                                            <span>Mitarbeiterzahl: {min}–{max}</span>
                                        </p>
                                        {billingPeriod === "yearly" &&
                                            <div className="yearly-hint-card">
                                                Bei jährl. Zahlung: <strong>{formatCHF(pkg.baseMonthly * 10)}</strong> Grundgebühr + <strong>{formatCHF(pkg.perEmployeeMonthly * 10)}</strong> pro MA / Jahr
                                            </div>
                                        }
                                        <ul className="features-list">
                                            <li><strong>Support:</strong> {pkg.support}</li>
                                            <li>Alle Features inkl. Lohnabrechnung, Urlaubsmanagement, NFC-Zeiterfassung</li>
                                            <li>DSGVO-konform, Schweizer Hosting</li>
                                            {pkg.bestFor && <li className="best-for">{pkg.bestFor}</li>}
                                        </ul>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="central-employee-input">
                            <label htmlFor="employeeCount">Anzahl Mitarbeiter ({PACKAGE_CONFIG[selectedPackageName]?.min || 1}–{PACKAGE_CONFIG[selectedPackageName]?.max || 100}):</label>
                            <input
                                type="number"
                                id="employeeCount"
                                name="employeeCount"
                                min={PACKAGE_CONFIG[selectedPackageName]?.min || 1}
                                max={PACKAGE_CONFIG[selectedPackageName]?.max || 100}
                                value={employeeCount}
                                onChange={handleEmployeeCountChange}
                                disabled={isSubmitting || !selectedPackageName}
                            />
                        </div>

                        <div className="training-option-section">
                            <h4>Optionales Add-on:</h4>
                            <label htmlFor="includeOptionalTraining" className="training-label">
                                <input
                                    type="checkbox"
                                    id="includeOptionalTraining"
                                    name="includeOptionalTraining"
                                    checked={includeOptionalTraining}
                                    onChange={handleOptionalTrainingChange}
                                />
                                Individuelles Onboarding (1h, persönlich) für <strong>{formatCHF(OPTIONAL_TRAINING_COST)}</strong> (einmalig).
                            </label>
                        </div>
                        <p className="pricing-footnote">
                            * Alle Preise zzgl. MwSt. | Jährliche Zahlung = 2 Monate geschenkt.
                            <br />
                            Einmalige Installationspauschale: {formatCHF(INSTALL_FEE)} (inkl. System-Setup und Ersteinrichtung)
                        </p>
                    </section>

                    <section className="application-section">
                        <h2>Unverbindliche Angebotsanfrage senden</h2>
                        {selectedPackageName && priceBreakdown && (
                            <div className="price-preview card">
                                <h4>Ihre Konfiguration: <span className="preview-package">{selectedPackageName}</span></h4>
                                <p className="price-item">
                                    <span className="label">Mitarbeiter:</span>
                                    <span className="value">{employeeCount}</span>
                                </p>
                                <p className="price-item">
                                    <span className="label">Zahlungsweise:</span>
                                    <span className="value">{billingPeriod === "monthly" ? "Monatlich" : "Jährlich"}</span>
                                </p>
                                <hr />
                                <p className="price-item">
                                    <span className="label">Service-Grundgebühr:</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.serviceFee)} {priceBreakdown.billingPeriodText.replace(" (entspricht 10 Monatsraten)", "")}
                                    </span>
                                </p>
                                <p className="price-item">
                                    <span className="label">Kosten für Mitarbeiter:</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.employeeCost)} {priceBreakdown.billingPeriodText.replace(" (entspricht 10 Monatsraten)", "")}
                                    </span>
                                </p>
                                <p className="price-item prominent-subtotal">
                                    <span className="label">
                                        Paketkosten {billingPeriod === "monthly" ? "monatlich" : "jährlich"}:
                                    </span>
                                    <span className="value">{formatCHF(priceBreakdown.packageSubtotal)}</span>
                                </p>
                                {priceBreakdown.isYearly && (
                                    <p className="price-item sub-hint">
                                        <span className="label">(entspricht pro Monat):</span>
                                        <span className="value">
                                            {formatCHF(priceBreakdown.packageSubtotal / 10)}
                                        </span>
                                    </p>
                                )}
                                <hr />
                                <p className="price-item">
                                    <span className="label">Einmalige Installationsgebühr:</span>
                                    <span className="value">{formatCHF(priceBreakdown.installationFee)}</span>
                                </p>
                                {includeOptionalTraining && (
                                    <p className="price-item">
                                        <span className="label">Onboarding (optional):</span>
                                        <span className="value">{formatCHF(priceBreakdown.optionalTrainingCost)}</span>
                                    </p>
                                )}
                                <hr />
                                <div className="total-price-wrapper">
                                    <p className="price-item total-price">
                                        <span className="label">
                                            Gesamtbetrag für erste Zahlung:
                                        </span>
                                        <span className="value">{formatCHF(calculatedPrice)}</span>
                                    </p>
                                </div>
                                <p className="price-item sub-hint">
                                    <span className="label">Folgezahlungen:</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.packageSubtotal)} {billingPeriod === "monthly" ? "monatlich" : "jährlich"}
                                    </span>
                                </p>
                            </div>
                        )}

                        {error && <p className="error-message">{error}</p>}
                        {success ? (
                            <div className="success-message-box">
                                <h3>Vielen Dank für Ihre Anfrage!</h3>
                                <p>Wir haben Ihre Konfiguration erhalten und prüfen diese.</p>
                                <p>
                                    Sie erhalten in Kürze (üblicherweise innerhalb von 1–2 Werktagen) Ihr individuelles Angebot und weitere Informationen per E-Mail.
                                    <br />
                                    Bitte prüfen Sie auch Ihren Spam-Ordner.
                                </p>
                                <button onClick={() => navigate('/')} style={{ marginTop: '1rem' }}>
                                    Zurück zur Startseite
                                </button>
                            </div>
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
                                    placeholder="Ansprechpartner*in"
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
                                <textarea
                                    name="additionalInfo"
                                    value={form.additionalInfo}
                                    onChange={handleChange}
                                    placeholder="Weitere Informationen oder Fragen zu Ihrer Anfrage..."
                                    rows="4"
                                />
                                <button type="submit" disabled={isSubmitting || !selectedPackageName || calculatedPrice <= 0}>
                                    {isSubmitting ? "Anfrage wird gesendet..." : "Angebotsanfrage senden"}
                                </button>
                            </form>
                        )}
                    </section>
                    <div style={{ marginTop: "40px", textAlign: "center", fontSize: "0.9rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>Impressum</Link>
                        <Link to="/agb" style={{ marginRight: "1rem" }}>AGB</Link>
                        <a href="https://www.instagram.com" target="_blank" rel="noopener noreferrer">Instagram</a>
                    </div>
                </div>
            </div>
        </>
    );
};

export default Registration;
