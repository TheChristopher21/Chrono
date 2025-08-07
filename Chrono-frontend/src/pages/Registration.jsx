import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import { Link } from "react-router-dom";
import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";

// Baukasten-Features (anpassbar)
const BASE_FEATURE = {
    key: "base",
    name: "Zeiterfassung (Basis)",
    price: 5,
    required: true,
    description: "Digitale Zeiterfassung für alle Mitarbeiter (Pflichtmodul).",
};
const FEATURES = [
    {
        key: "vacation",
        name: "Urlaubs- & Abwesenheitsmodul",
        price: 1,
        required: false,
        description: "Digitale Urlaubsanträge, Abwesenheitsübersicht, Freigabe-Workflow.",
    },
    {
        key: "payroll",
        name: "Lohnabrechnung",
        price: 4,
        required: false,
        description: "Lohnabrechnung (DE & CH), Gehaltsabrechnungen als PDF, Abrechnungsexport.",
    },
    {
        key: "projects",
        name: "Projektzeiten & Kundenverwaltung",
        price: 1,
        required: false,
        description: "Erfassen von Projektzeiten und Kunden, Berichte & Auswertungen.",
    },
    {
        key: "nfc",
        name: "NFC-Stempeluhr",
        price: 0.5,
        required: false,
        description: "Stempeln per NFC-Karte oder Chip am Terminal oder Smartphone.",
    },
    {
        key: "chatbot",
        name: "Integrierter Support-Chatbot",
        price: 0.5,
        required: false,
        description: "KI-basierte Hilfe & Erklärungen direkt in der App.",
    },
    {
        key: "premiumSupport",
        name: "Premium-Support",
        price: 1,
        required: false,
        description: "Telefonischer Support & priorisierte Bearbeitung.",
    },
    {
        key: "roster",
        name: "Dienstplan & Schichtplanung",
        price: 1.20,
        required: false,
        description: "Intelligente Schichtplanung mit Drag & Drop, Konflikterkennung, Mitarbeiterwünschen, Urlaubsabgleich und Export als PDF/Excel."
    }
];

const INSTALL_FEE = 150;
const OPTIONAL_TRAINING_COST = 120;
const YEARLY_DISCOUNT_FACTOR = 10; // 2 Monate geschenkt

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

    const [employeeCount, setEmployeeCount] = useState(1);
    const [selectedFeatures, setSelectedFeatures] = useState(
        FEATURES.filter((f) => f.required).map((f) => f.key)
    );
    const [billingPeriod, setBillingPeriod] = useState("monthly");
    const [includeOptionalTraining, setIncludeOptionalTraining] = useState(false);

    const [calculatedPrice, setCalculatedPrice] = useState(0);
    const [priceBreakdown, setPriceBreakdown] = useState(null);
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    // Preisberechnung
    useEffect(() => {
        // Alle gewählten Features: Pflicht + Zusatz
        let allFeatures = [BASE_FEATURE, ...FEATURES.filter(f => selectedFeatures.includes(f.key))];
        let sumFeaturesPerEmployee = allFeatures.reduce((acc, f) => acc + f.price, 0);
        let totalPerPeriod = sumFeaturesPerEmployee * employeeCount;

        let periodText = "/ Monat";
        let periodFactor = 1;
        let displayTotalPeriod = totalPerPeriod;
        if (billingPeriod === "yearly") {
            displayTotalPeriod = totalPerPeriod * YEARLY_DISCOUNT_FACTOR;
            periodText = "/ Jahr (entspricht 10 Monatsraten)";
            periodFactor = YEARLY_DISCOUNT_FACTOR;
        }

        const installationFee = INSTALL_FEE;
        const optionalTraining = includeOptionalTraining ? OPTIONAL_TRAINING_COST : 0;
        const totalFirstPayment = displayTotalPeriod + installationFee + optionalTraining;

        setCalculatedPrice(totalFirstPayment);
        setPriceBreakdown({
            sumFeaturesPerEmployee,
            totalPerPeriod: displayTotalPeriod,
            installationFee,
            optionalTraining,
            periodText,
            isYearly: billingPeriod === "yearly",
            perPeriodSingle: totalPerPeriod,
        });
    }, [selectedFeatures, employeeCount, billingPeriod, includeOptionalTraining]);

    const handleChange = (e) =>
        setForm({ ...form, [e.target.name]: e.target.value });

    const handleFeatureChange = (featureKey) => {
        const featureObj = FEATURES.find((f) => f.key === featureKey);
        if (featureObj.required) return; // Pflichtmodul, nicht abwählbar
        setSelectedFeatures((prev) =>
            prev.includes(featureKey)
                ? prev.filter((k) => k !== featureKey)
                : [...prev, featureKey]
        );
    };

    const handleEmployeeCountChange = (e) => {
        let val = parseInt(e.target.value, 10);
        if (isNaN(val) || val < 1) val = 1;
        if (val > 200) val = 200;
        setEmployeeCount(val);
    };
    const handleBillingPeriodChange = (e) =>
        setBillingPeriod(e.target.value);
    const handleOptionalTrainingChange = (e) =>
        setIncludeOptionalTraining(e.target.checked);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (employeeCount < 1) {
            setError("Bitte geben Sie mindestens 1 Mitarbeiter an.");
            return;
        }
        setIsSubmitting(true);
        setError("");
        try {
            const payload = {
                ...form,
                selectedFeatures,
                employeeCount,
                billingPeriod,
                includeOptionalTraining,
                priceBreakdown,
                calculatedPrice,
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
                        <h1>Baukasten-Preismodell – Zahle nur, was du brauchst!</h1>
                        <p className="hero-subline">
                            <strong>Wähle individuell die Module für deine perfekte Zeiterfassungslösung.</strong>
                            <br />
                            Sofort transparent, jederzeit unverbindlich anfragen.
                        </p>
                    </section>

                    <section className="unique-sell-callout">
                        <span role="img" aria-label="star">⭐</span>
                        <strong>Modular, fair und flexibel!</strong>
                        <span className="unique-badge">Jetzt zusammenstellen & sparen.</span>
                    </section>

                    <section className="pricing-section">
                        <h2>Baukasten & Preise</h2>
                        <p className="pricing-intro">
                            Wähle deine Wunsch-Module und erhalte sofort den Preis.<br />
                            <span className="money-back">100 % unverbindlich – keine Zahlung bis Vertragsabschluss.</span>
                        </p>

                        <div className="central-employee-input">
                            <label htmlFor="employeeCount">Anzahl Mitarbeiter (1–200):</label>
                            <input
                                type="number"
                                id="employeeCount"
                                name="employeeCount"
                                min={1}
                                max={200}
                                value={employeeCount}
                                onChange={handleEmployeeCountChange}
                                disabled={isSubmitting}
                            />
                        </div>

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

                        <div className="pricing-cards" style={{ flexWrap: "wrap", justifyContent: "flex-start" }}>
                            {/* Pflichtmodul */}
                            <div className="pricing-card selected" style={{ '--card-accent-color': '#475bff' }}>
                                <div className="card-badge-row">
                                    <span className="selected-badge">Pflichtmodul</span>
                                </div>
                                <h3>{BASE_FEATURE.name}</h3>
                                <p className="price-line base-fee">
                                    <strong>{formatCHF(BASE_FEATURE.price)} / Monat je MA</strong>
                                </p>
                                <ul className="features-list">
                                    <li>{BASE_FEATURE.description}</li>
                                </ul>
                            </div>
                            {/* Zusatzmodule */}
                            {FEATURES.map((feature) => (
                                <div
                                    key={feature.key}
                                    className={`pricing-card${selectedFeatures.includes(feature.key) ? " selected" : ""}`}
                                    onClick={() => handleFeatureChange(feature.key)}
                                    style={{ '--card-accent-color': selectedFeatures.includes(feature.key) ? "#2ecc71" : "var(--c-border)" }}
                                >
                                    <div className="card-badge-row">
                                        {feature.required && <span className="selected-badge">Pflicht</span>}
                                        {selectedFeatures.includes(feature.key) && !feature.required && (
                                            <span className="selected-badge">Gewählt</span>
                                        )}
                                    </div>
                                    <h3>{feature.name}</h3>
                                    <p className="price-line base-fee">
                                        <strong>
                                            +{formatCHF(feature.price)} / Monat je MA
                                        </strong>
                                    </p>
                                    <ul className="features-list">
                                        <li>{feature.description}</li>
                                    </ul>
                                </div>
                            ))}
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
                            * Alle Preise zzgl. MwSt. | Jährliche Zahlung = 2 Monate geschenkt.<br />
                            Einmalige Installationspauschale: {formatCHF(INSTALL_FEE)} (inkl. System-Setup und Ersteinrichtung)
                        </p>
                    </section>

                    <section className="application-section">
                        <h2>Unverbindliche Angebotsanfrage senden</h2>
                        {priceBreakdown && (
                            <div className="price-preview card">
                                <h4>Ihre Konfiguration:</h4>
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
                                    <span className="label">Ausgewählte Features:</span>
                                    <span className="value">
                    {[BASE_FEATURE, ...FEATURES.filter(f => selectedFeatures.includes(f.key))]
                        .map(f => f.name)
                        .join(", ")}
                  </span>
                                </p>
                                <p className="price-item">
                                    <span className="label">Gesamtkosten {billingPeriod === "monthly" ? "pro Monat" : "pro Jahr"}:</span>
                                    <span className="value">
                    {formatCHF(priceBreakdown.totalPerPeriod)} {priceBreakdown.periodText.replace(" (entspricht 10 Monatsraten)", "")}
                  </span>
                                </p>
                                {priceBreakdown.isYearly && (
                                    <p className="price-item sub-hint">
                                        <span className="label">(entspricht pro Monat):</span>
                                        <span className="value">
                      {formatCHF(priceBreakdown.perPeriodSingle)}
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
                                        <span className="value">{formatCHF(priceBreakdown.optionalTraining)}</span>
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
                    {formatCHF(priceBreakdown.totalPerPeriod)} {billingPeriod === "monthly" ? "monatlich" : "jährlich"}
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
                                <button type="submit" disabled={isSubmitting || calculatedPrice <= 0}>
                                    {isSubmitting ? "Anfrage wird gesendet..." : "Angebotsanfrage senden"}
                                </button>
                            </form>
                        )}
                    </section>
                    <div style={{ marginTop: "40px", textAlign: "center", fontSize: "0.9rem" }}>
                        <Link to="/impressum" style={{ marginRight: "1rem" }}>Impressum</Link>
                        <Link to="/agb" style={{ marginRight: "1rem" }}>AGB</Link>
                        <a href="https://www.instagram.com/itschronologisch" target="_blank" rel="noopener noreferrer">Instagram</a>
                    </div>
                </div>
            </div>
        </>
    );
};

export default Registration;
