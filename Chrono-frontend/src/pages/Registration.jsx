import { useState, useEffect, useMemo } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";
import { BASE_FEATURE, FEATURE_CATALOG, TOGGLABLE_FEATURE_KEYS } from "../constants/registrationFeatures";

const INSTALL_FEE = 250;
const OPTIONAL_TRAINING_COST = 120;
const YEARLY_DISCOUNT_FACTOR = 10; // 2 Monate geschenkt

const Registration = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { notify } = useNotification();

    const companyId = useMemo(() => {
        const params = new URLSearchParams(location.search);
        const value = params.get("companyId");
        if (!value) return null;
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }, [location.search]);

    const [form, setForm] = useState({
        companyName: "",
        contactName: "",
        email: "",
        phone: "",
        additionalInfo: "",
    });

    const [employeeCount, setEmployeeCount] = useState(1);
    const [selectedFeatures, setSelectedFeatures] = useState(
        FEATURE_CATALOG.filter((f) => f.required).map((f) => f.key)
    );
    const [billingPeriod, setBillingPeriod] = useState("monthly");
    const [includeOptionalTraining, setIncludeOptionalTraining] = useState(false);

    const [calculatedPrice, setCalculatedPrice] = useState(0);
    const [priceBreakdown, setPriceBreakdown] = useState(null);
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);
    const [companyFeatureKeys, setCompanyFeatureKeys] = useState([]);
    const [featureError, setFeatureError] = useState("");
    const [isLoadingFeatures, setIsLoadingFeatures] = useState(false);

    const visibleFeatures = useMemo(() => {
        return FEATURE_CATALOG.filter(
            (feature) => feature.alwaysAvailable || companyFeatureKeys.includes(feature.key)
        );
    }, [companyFeatureKeys]);

    const visibleFeatureKeys = useMemo(() => new Set(visibleFeatures.map((feature) => feature.key)), [visibleFeatures]);

    useEffect(() => {
        let isMounted = true;
        if (companyId == null) {
            if (isMounted) {
                setCompanyFeatureKeys([]);
                setFeatureError("");
                setIsLoadingFeatures(false);
            }
            return () => {
                isMounted = false;
            };
        }

        const loadFeatures = async () => {
            setIsLoadingFeatures(true);
            setFeatureError("");
            try {
                const response = await api.get("/api/public/registration-features", {
                    params: { companyId },
                });
                const payload = response.data;
                const rawKeys = Array.isArray(payload)
                    ? payload
                    : Array.isArray(payload?.enabledFeatures)
                    ? payload.enabledFeatures
                    : [];
                const sanitized = rawKeys.filter((key) => TOGGLABLE_FEATURE_KEYS.includes(key));
                if (isMounted) {
                    setCompanyFeatureKeys(sanitized);
                }
            } catch (fetchError) {
                console.error("Fehler beim Laden der Firmenmodule", fetchError);
                if (isMounted) {
                    setFeatureError(
                        "Die freigeschalteten Module konnten nicht geladen werden. Es werden nur die Standardmodule angezeigt."
                    );
                    setCompanyFeatureKeys([]);
                }
            } finally {
                if (isMounted) {
                    setIsLoadingFeatures(false);
                }
            }
        };

        loadFeatures();

        return () => {
            isMounted = false;
        };
    }, [companyId]);

    useEffect(() => {
        setSelectedFeatures((prev) => prev.filter((key) => visibleFeatureKeys.has(key)));
    }, [visibleFeatureKeys]);

    // Preisberechnung
    useEffect(() => {
        const selectedFeatureObjects = FEATURE_CATALOG.filter((f) => selectedFeatures.includes(f.key));
        const perEmployeeAddOnRate = selectedFeatureObjects
            .filter((f) => f.priceType === "perEmployee")
            .reduce((acc, f) => acc + f.price, 0);
        const flatAddOnMonthly = selectedFeatureObjects
            .filter((f) => f.priceType === "flat")
            .reduce((acc, f) => acc + f.price, 0);

        const basePerEmployeeMonthly = BASE_FEATURE.price * employeeCount;
        const addOnPerEmployeeMonthly = perEmployeeAddOnRate * employeeCount;
        const addOnFlatMonthly = flatAddOnMonthly;
        const addOnCount = selectedFeatureObjects.length;

        let discountRate = 0;
        if (addOnCount >= 5) {
            discountRate = 0.15;
        } else if (addOnCount >= 3) {
            discountRate = 0.1;
        }

        const addOnTotalBeforeDiscount = addOnPerEmployeeMonthly + addOnFlatMonthly;
        const addOnDiscountValue = addOnTotalBeforeDiscount * discountRate;
        const discountPerEmployeeShare =
            addOnPerEmployeeMonthly > 0 && addOnTotalBeforeDiscount > 0
                ? (addOnDiscountValue * (addOnPerEmployeeMonthly / addOnTotalBeforeDiscount)) /
                  Math.max(employeeCount, 1)
                : 0;
        const effectivePerEmployeeRate = BASE_FEATURE.price + perEmployeeAddOnRate - discountPerEmployeeShare;
        const monthlyTotalBeforeFactor = basePerEmployeeMonthly + addOnTotalBeforeDiscount - addOnDiscountValue;

        let periodLabel = "pro Monat";
        let displayTotalPeriod = monthlyTotalBeforeFactor;
        if (billingPeriod === "yearly") {
            displayTotalPeriod = monthlyTotalBeforeFactor * YEARLY_DISCOUNT_FACTOR;
            periodLabel = "pro Jahr (12 Monate, 2 gratis)";
        }

        const installationFee = INSTALL_FEE;
        const optionalTraining = includeOptionalTraining ? OPTIONAL_TRAINING_COST : 0;
        const totalFirstPayment = displayTotalPeriod + installationFee + optionalTraining;

        setCalculatedPrice(totalFirstPayment);
        setPriceBreakdown({
            perEmployeeRate: effectivePerEmployeeRate,
            basePerEmployeeMonthly,
            addOnPerEmployeeMonthly,
            addOnFlatMonthly,
            addOnDiscountRate: discountRate,
            addOnDiscountValue,
            totalPerPeriod: displayTotalPeriod,
            installationFee,
            optionalTraining,
            periodLabel,
            isYearly: billingPeriod === "yearly",
            perPeriodSingle: monthlyTotalBeforeFactor,
            addOnCount,
        });
    }, [selectedFeatures, employeeCount, billingPeriod, includeOptionalTraining]);

    const handleChange = (e) =>
        setForm({ ...form, [e.target.name]: e.target.value });

    const handleFeatureChange = (featureKey) => {
        if (!visibleFeatureKeys.has(featureKey)) {
            return;
        }
        const featureObj = FEATURE_CATALOG.find((f) => f.key === featureKey);
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
            const selectedFeatureObjects = FEATURE_CATALOG.filter((f) => selectedFeatures.includes(f.key));
            const selectedFeatureNames = selectedFeatureObjects.map((f) => f.name);
            const payload = {
                ...form,
                selectedFeatures,
                selectedFeatureNames,
                employeeCount,
                billingPeriod,
                includeOptionalTraining,
                priceBreakdown,
                calculatedPrice,
                companyId: companyId ?? null,
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
        if (typeof value !== "number" || isNaN(value)) {
            return "0,00 CHF";
        }
        return value.toFixed(2).replace(".", ",") + " CHF";
    }

    const getFeaturePriceLabel = (feature) => {
        const formatted = formatCHF(feature.price);
        if (feature.priceType === "flat") {
            return `+${formatted} / Monat gesamt`;
        }
        return `+${formatted} / Monat je MA`;
    };

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
                            <p className="employee-note">
                                Für sehr große Unternehmen mit mehr als 200 Mitarbeitenden erstellen wir
                                gerne ein individuelles Angebot. Bitte stellen Sie eine direkte Anfrage
                                über unser Kontaktformular.
                            </p>
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
                            {visibleFeatures.map((feature) => (
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
                                        <strong>{getFeaturePriceLabel(feature)}</strong>
                                    </p>
                                    <ul className="features-list">
                                        <li>{feature.description}</li>
                                    </ul>
                                </div>
                            ))}
                        </div>

                        <div className="feature-availability-note">
                            {isLoadingFeatures ? (
                                <span>Freigeschaltete Module werden geladen...</span>
                            ) : featureError ? (
                                <span className="feature-warning">{featureError}</span>
                            ) : companyId != null ? (
                                <>
                                    <span>
                                        Module, die für Ihre Firma freigegeben wurden, sind auswählbar. Basis, Urlaub und NFC sind
                                        immer verfügbar.
                                    </span>
                                    {visibleFeatures.filter((feature) => !feature.alwaysAvailable).length === 0 && (
                                        <span className="feature-muted">
                                            Aktuell wurden keine zusätzlichen Module freigeschaltet. Bei Bedarf im SuperAdmin-Panel
                                            aktivieren.
                                        </span>
                                    )}
                                </>
                            ) : (
                                <>
                                    <span>
                                        Hinweis: Ohne Firmen-ID werden nur die Standardmodule angezeigt. Aktivierte Zusatzmodule sehen
                                        Sie über einen personalisierten Link.
                                    </span>
                                    <span className="feature-muted">
                                        Tipp: SuperAdmins können im Firmen-Panel entscheiden, welche Module angezeigt werden.
                                    </span>
                                </>
                            )}
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
                            Bundle-Rabatt: ab 3 Add-ons 10 % auf Zusatzmodule, ab 5 Add-ons 15 %.<br />
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
                                        {[BASE_FEATURE,
                                            ...FEATURE_CATALOG.filter((feature) =>
                                                selectedFeatures.includes(feature.key)
                                            ),
                                        ]
                                            .map((feature) => feature.name)
                                            .join(", ")}
                                    </span>
                                </p>
                                <p className="price-item">
                                    <span className="label">Preis je Mitarbeiter:</span>
                                    <span className="value">{formatCHF(priceBreakdown.perEmployeeRate)} / Monat</span>
                                </p>
                                {priceBreakdown.addOnFlatMonthly > 0 && (
                                    <p className="price-item">
                                        <span className="label">Fixpreis-Module gesamt:</span>
                                        <span className="value">{formatCHF(priceBreakdown.addOnFlatMonthly)}</span>
                                    </p>
                                )}
                                {priceBreakdown.addOnDiscountValue > 0 && (
                                    <p className="price-item sub-hint">
                                        <span className="label">Bundle-Rabatt ({Math.round(priceBreakdown.addOnDiscountRate * 100)} %):</span>
                                        <span className="value">{formatCHF(-priceBreakdown.addOnDiscountValue)}</span>
                                    </p>
                                )}
                                <p className="price-item">
                                    <span className="label">Gesamtkosten {priceBreakdown.periodLabel}:</span>
                                    <span className="value">{formatCHF(priceBreakdown.totalPerPeriod)}</span>
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
                    {formatCHF(priceBreakdown.perPeriodSingle)} {billingPeriod === "monthly" ? "monatlich" : "pro Monat (bei Jahreszahlung)"}
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
