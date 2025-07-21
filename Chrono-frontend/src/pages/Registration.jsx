// src/pages/Registration.jsx
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css"; // Dein CSS-File
import { Link } from "react-router-dom";

import api from "../utils/api"; // Annahme: api util ist konfiguriert
import { useNotification } from "../context/NotificationContext"; // Annahme: Notification Context ist konfiguriert

// KONFIGURATION DER PAKETE (Training entfernt)
const PACKAGE_CONFIG = {
    Small: {
        name: "Small",
        baseMonthly: 20,
        perEmployeeMonthly: 5,
        support: "Standard E-Mail Support (Antwort i.d.R. innerhalb 48h)",
        color: "#777",
    },
    Basic: {
        name: "Basic",
        baseMonthly: 60,
        perEmployeeMonthly: 4.5,
        support: "Priorisierter E-Mail Support (Antwort i.d.R. innerhalb 24h)",
        color: "#007bff", // Blauton, anpassbar
    },
    Professional: {
        name: "Professional",
        baseMonthly: 160,
        perEmployeeMonthly: 4,
        support: "Premium E-Mail & Telefon-Support (Schnelle Reaktionszeiten), Persönlicher Ansprechpartner.",
        color: "#28a745", // Grünton, anpassbar
    }
};

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

    const INSTALL_FEE = 150;
    const [includeOptionalTraining, setIncludeOptionalTraining] = useState(false);
    const OPTIONAL_TRAINING_COST = 120;

    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handlePackageSelect = (pkgName) => {
        setSelectedPackageName(pkgName);
    };

    const handleEmployeeCountChange = (e) => {
        let val = parseInt(e.target.value, 10);
        if (isNaN(val) || val < 1) val = 1;
        if (val > 100) val = 100;
        setEmployeeCount(val);
    };

    const handleBillingPeriodChange = (e) => {
        setBillingPeriod(e.target.value);
    };

    const handleOptionalTrainingChange = (e) => {
        setIncludeOptionalTraining(e.target.checked);
    };

    function calculatePriceDetails(pkgName, empCount, period, trainingSelected) {
        if (!pkgName || !PACKAGE_CONFIG[pkgName]) {
            return { total: 0, breakdown: null };
        }

        const pkg = PACKAGE_CONFIG[pkgName];
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

        const installationFee = INSTALL_FEE;
        let optionalTrainingCostTotal = 0;
        if (trainingSelected) {
            optionalTrainingCostTotal = OPTIONAL_TRAINING_COST;
        }

        const totalForFirstPayment = packageMonthlyTotal + installationFee + optionalTrainingCostTotal;

        return {
            total: totalForFirstPayment,
            breakdown: {
                serviceFee: period === 'yearly' ? serviceFeeMonthly * currentPeriodFactor : serviceFeeMonthly,
                employeeCost: period === 'yearly' ? employeeCostMonthly * currentPeriodFactor : employeeCostMonthly,
                packageSubtotal: packageMonthlyTotal, // Paketpreis (Grundgebühr + MA) für den gewählten Zeitraum
                installationFee: installationFee,
                optionalTrainingCost: optionalTrainingCostTotal,
                billingPeriodText: periodText,
                isYearly: period === 'yearly',
            }
        };
    }

    useEffect(() => {
        const { total, breakdown } = calculatePriceDetails(
            selectedPackageName,
            employeeCount,
            billingPeriod,
            includeOptionalTraining
        );
        setCalculatedPrice(total);
        setPriceBreakdown(breakdown);
    }, [selectedPackageName, employeeCount, billingPeriod, includeOptionalTraining]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPackageName) {
            setError("Bitte wählen Sie ein Paket aus.");
            return;
        }
        setIsSubmitting(true);
        setError("");
        try {
            // Der Payload enthält alle Infos, die du für dein Angebot brauchst
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
                packageBaseFeeMonthly: PACKAGE_CONFIG[selectedPackageName].baseMonthly,
                packagePerEmployeeMonthly: PACKAGE_CONFIG[selectedPackageName].perEmployeeMonthly,
            };
            console.log("Sende Registrierungsanfrage:", payload); // Für Testzwecke
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

    return (
        <>
            <Navbar />
            <div className="registration-page scoped-registration">
                <div className="registration-content">
                    <div className="pricing-section">
                        <h2>{t('registrationPage.pricingTitle')}</h2>
                        <p className="pricing-intro">{t('registrationPage.pricingIntro')}</p>

                        <div className="billing-toggle">
                            <label>
                                <input
                                    type="radio"
                                    name="billingPeriod"
                                    value="monthly"
                                    checked={billingPeriod === "monthly"}
                                    onChange={handleBillingPeriodChange}
                                />
                                {t('registrationPage.monthlyBilling')}
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    name="billingPeriod"
                                    value="yearly"
                                    checked={billingPeriod === "yearly"}
                                    onChange={handleBillingPeriodChange}
                                />
                                {t('registrationPage.yearlyBilling')}
                            </label>
                        </div>

                        <div className="pricing-cards">
                            {Object.values(PACKAGE_CONFIG).map((pkg) => (
                                <div
                                    key={pkg.name}
                                    className={`pricing-card ${selectedPackageName === pkg.name ? "selected" : ""}`}
                                    onClick={() => handlePackageSelect(pkg.name)}
                                    style={{ '--card-accent-color': selectedPackageName === pkg.name ? pkg.color : 'var(--c-border)' }}
                                >
                                    <h3 style={{ color: selectedPackageName === pkg.name ? pkg.color : 'var(--c-text-strong)' }}>{pkg.name}</h3>
                                    <p className="price-line base-fee">
                                        Service-Grundgebühr:<br /><strong>{formatCHF(pkg.baseMonthly)} / Monat</strong>
                                    </p>
                                    <p className="price-line employee-rate">
                                        Pro Mitarbeiter:<br /><strong>+ {formatCHF(pkg.perEmployeeMonthly)} / Monat</strong>
                                    </p>
                                    <p className="yearly-hint-card">
                                        Bei jährl. Zahlung: {formatCHF(pkg.baseMonthly * 10)} Grundgebühr + {formatCHF(pkg.perEmployeeMonthly * 10)} pro MA / Jahr
                                    </p>
                                    <ul className="features-list">
                                        <li><strong>Support:</strong> {pkg.support}</li>
                                        <li>Alle Chrono-App Funktionen</li>
                                    </ul>
                                </div>
                            ))}
                        </div>

                        <div className="central-employee-input">
                            <label htmlFor="employeeCount">{t('registrationPage.employeeCount')}</label>
                            <input
                                type="number"
                                id="employeeCount"
                                name="employeeCount"
                                min="1"
                                max="100"
                                value={employeeCount}
                                onChange={handleEmployeeCountChange}
                                disabled={isSubmitting}
                            />
                        </div>

                        <div className="training-option-section">
                            <h4>{t('registrationPage.trainingTitle')}</h4>
                            <label htmlFor="includeOptionalTraining" className="training-label">
                                <input
                                    type="checkbox"
                                    id="includeOptionalTraining"
                                    name="includeOptionalTraining"
                                    checked={includeOptionalTraining}
                                    onChange={handleOptionalTrainingChange}
                                />
                                {t('registrationPage.addTraining')} <strong>{formatCHF(OPTIONAL_TRAINING_COST)}</strong>
                            </label>
                        </div>

                        <p className="pricing-footnote">{t('registrationPage.footnote')}</p>
                    </div>

                    <div className="application-section">
                        <h2>{t('registrationPage.sendRequest')}</h2>
                        {selectedPackageName && priceBreakdown && (
                            <div className="price-preview card">
                                <h4>{t('registrationPage.yourConfig', 'Ihre Konfiguration')}: "{selectedPackageName}"</h4>
                                <p className="price-item">
                                    <span className="label">{t('usernameLabel')}:</span>
                                    <span className="value">{employeeCount}</span>
                                </p>
                                <p className="price-item">
                                    <span className="label">{t('registrationPage.billingType', 'Zahlungsweise')}:</span>
                                    <span className="value">{billingPeriod === "monthly" ? "Monatlich" : "Jährlich"}</span>
                                </p>
                                <hr />
                                <p className="price-item">
                                    <span className="label">{t('registrationPage.serviceFeeLabel', 'Voraussichtliche Service-Grundgebühr')}:</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.serviceFee)}{" "}
                                        {priceBreakdown.billingPeriodText.replace(" (entspricht 10 Monatsraten)", "")}
                                    </span>
                                </p>
                                <p className="price-item">
                                    <span className="label">{t('registrationPage.employeeCostLabel', 'Voraussichtliche Kosten für Mitarbeiter')}:</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.employeeCost)}{" "}
                                        {priceBreakdown.billingPeriodText.replace(" (entspricht 10 Monatsraten)", "")}
                                    </span>
                                </p>
                                <p className="price-item prominent-subtotal">
                                    <span className="label">
                                        {t('registrationPage.packageCostLabel', 'Voraussichtliche Paketkosten')} {billingPeriod === 'monthly' ? t('registrationPage.monthlyBilling') : t('registrationPage.yearlyBilling')}:
                                    </span>
                                    <span className="value">{formatCHF(priceBreakdown.packageSubtotal)}</span>
                                </p>
                                {priceBreakdown.isYearly && (
                                    <p className="price-item sub-hint">
                                        <span className="label">{t('registrationPage.perMonth', '(Entspricht ca. pro Monat):')}</span>
                                        <span className="value">
                                            {formatCHF(priceBreakdown.packageSubtotal / 10)}
                                        </span>
                                    </p>
                                )}
                                <hr />
                                <p className="price-item">
                                        <span className="label">{t('registrationPage.installFeeLabel', 'Einmalige Installationsgebühr')}:</span>
                                    <span className="value">{formatCHF(priceBreakdown.installationFee)}</span>
                                </p>
                                {includeOptionalTraining && (
                                    <p className="price-item">
                                        <span className="label">{t('registrationPage.trainingLabel', 'Optionales Intensiv-Onboarding')}:</span>
                                        <span className="value">{formatCHF(priceBreakdown.optionalTrainingCost)}</span>
                                    </p>
                                )}
                                <hr />
                                <div className="total-price-wrapper">
                                    <p className="price-item total-price">
                                        <span className="label">
                                            {t('registrationPage.totalDue', 'Voraussichtlicher Gesamtbetrag für erste Zahlung (bei Beauftragung):')}
                                        </span>
                                        <span className="value">{formatCHF(calculatedPrice)}</span>
                                    </p>
                                </div>
                                <p className="price-item sub-hint">
                                    <span className="label">{t('registrationPage.followupPayments', 'Die nachfolgenden Zahlungen für das Paket betragen:')}</span>
                                    <span className="value">
                                        {formatCHF(priceBreakdown.packageSubtotal)}{" "}
                                        {billingPeriod === "monthly" ? "monatlich" : "jährlich"}
                                    </span>
                                </p>
                            </div>
                        )}

                        {error && <p className="error-message">{error}</p>}
                        {success ? (
                            <div className="success-message-box">
                                <h3>{t('registrationPage.thanks')}</h3>
                                <p>Wir haben Ihre Konfiguration erhalten und prüfen diese.</p>
                                <p>
                                    Sie erhalten in Kürze (üblicherweise innerhalb von 1-2 Werktagen) ein individuelles Angebot und weitere Informationen per E-Mail an siefertchristopher@chrono-logisch.ch.
                                    <br />
                                    Bitte prüfen Sie auch Ihren Spam-Ordner.
                                </p>
                                <button onClick={() => navigate('/')} style={{ marginTop: '1rem' }}>
                                    {t('registrationPage.backHome')}
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
                                    {isSubmitting ? t('registrationPage.sending') : t('registrationPage.sendRequest')}
                                </button>
                            </form>
                        )}
                    </div>

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
