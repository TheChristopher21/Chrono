import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";
import { useTranslation } from "../context/LanguageContext";
import { BASE_FEATURE, FEATURE_CATALOG } from "../constants/registrationFeatures";

const COUNTRY_CODES = ["ch", "de", "other"];

const INITIAL_CONFIGURATION = {
    country: COUNTRY_CODES[0],
    industry: "",
    employeeCount: 10,
    locations: "",
};

const INITIAL_FORM = {
    companyName: "",
    contactName: "",
    email: "",
    phone: "",
    additionalInfo: "",
};

const Registration = () => {
    const navigate = useNavigate();
    const { notify } = useNotification();
    const { t } = useTranslation();

    const [configuration, setConfiguration] = useState(INITIAL_CONFIGURATION);
    const [form, setForm] = useState(INITIAL_FORM);
    const [selectedFeatures, setSelectedFeatures] = useState([BASE_FEATURE.key]);
    const [consents, setConsents] = useState({ terms: false, contact: false });
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    const countryOptions = useMemo(
        () =>
            COUNTRY_CODES.map((value) => ({
                value,
                label: t(`registration.countries.${value}`, value.toUpperCase()),
            })),
        [t]
    );

    const pricingConfig = useMemo(() => {
        if (configuration.country === "ch") {
            return { currency: "CHF", locale: "de-CH" };
        }
        if (configuration.country === "de") {
            return { currency: "EUR", locale: "de-DE" };
        }
        return null;
    }, [configuration.country]);

    const formatCurrency = (value) => {
        if (!pricingConfig) {
            return "";
        }

        return new Intl.NumberFormat(pricingConfig.locale, {
            style: "currency",
            currency: pricingConfig.currency,
        }).format(value);
    };

    const moduleOptions = useMemo(
        () => [
            {
                key: BASE_FEATURE.key,
                label: t(
                    `registration.modules.${BASE_FEATURE.key}.name`,
                    BASE_FEATURE.name || "Zeiterfassung (Basis)"
                ),
                description: t(
                    `registration.modules.${BASE_FEATURE.key}.description`,
                    BASE_FEATURE.description ||
                        "Pflichtmodul – bildet die Grundlage für deinen Chrono-Account."
                ),
                price: BASE_FEATURE.price,
                priceType: BASE_FEATURE.priceType,
                required: true,
            },
            ...FEATURE_CATALOG.filter((feature) => feature.key !== BASE_FEATURE.key).map((feature) => ({
                key: feature.key,
                label: t(`registration.modules.${feature.key}.name`, feature.name),
                description: t(
                    `registration.modules.${feature.key}.description`,
                    feature.description
                ),
                price: feature.price,
                priceType: feature.priceType,
                required: Boolean(feature.required),
            })),
        ],
        [t]
    );

    const selectedModuleOptions = useMemo(
        () => moduleOptions.filter((option) => selectedFeatures.includes(option.key)),
        [moduleOptions, selectedFeatures]
    );

    const priceSummary = useMemo(() => {
        if (!pricingConfig) {
            return null;
        }

        const breakdown = selectedModuleOptions.map((option) => {
            const total =
                option.priceType === "perEmployee"
                    ? option.price * configuration.employeeCount
                    : option.price;

            return {
                key: option.key,
                label: option.label,
                priceType: option.priceType,
                unitPrice: option.price,
                total,
            };
        });

        const total = breakdown.reduce((sum, item) => sum + item.total, 0);

        return {
            breakdown,
            total,
        };
    }, [configuration.employeeCount, pricingConfig, selectedModuleOptions]);

    const employeeSliderStyle = {
        "--progress": `${((configuration.employeeCount - 1) / 199) * 100}%`,
    };

    const handleConfigurationChange = (field, value) => {
        setConfiguration((prev) => ({ ...prev, [field]: value }));
    };

    const handleFormChange = (event) => {
        const { name, value } = event.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const handleConsentChange = (event) => {
        const { name, checked } = event.target;
        setConsents((prev) => ({ ...prev, [name]: checked }));
    };

    const toggleFeature = (featureKey) => {
        const feature = moduleOptions.find((option) => option.key === featureKey);
        if (!feature || feature.required) {
            return;
        }

        setSelectedFeatures((prev) =>
            prev.includes(featureKey) ? prev.filter((key) => key !== featureKey) : [...prev, featureKey]
        );
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");

        if (!consents.terms || !consents.contact) {
            setError(
                t(
                    "registration.errors.acceptTerms",
                    "Bitte akzeptiere unsere Bedingungen, damit wir deine Anfrage bearbeiten können."
                )
            );
            return;
        }

        if (configuration.employeeCount < 1 || configuration.employeeCount > 200) {
            setError(
                t(
                    "registration.errors.employeeRange",
                    "Bitte wähle eine Mitarbeiterzahl zwischen 1 und 200."
                )
            );
            return;
        }

        setIsSubmitting(true);
        try {
            const selectedFeatureNames = selectedModuleOptions.map((option) => option.label);

            const priceBreakdown = priceSummary
                ? {
                    country: configuration.country,
                    currency: pricingConfig.currency,
                    employeeCount: configuration.employeeCount,
                    items: priceSummary.breakdown.map((item) => ({
                        key: item.key,
                        label: item.label,
                        priceType: item.priceType,
                        unitPrice: item.unitPrice,
                        total: item.total,
                    })),
                    total: priceSummary.total,
                }
                : null;

            const calculatedPrice = priceSummary ? priceSummary.total : null;

            const payload = {
                ...form,
                selectedFeatures,
                selectedFeatureNames,
                employeeCount: configuration.employeeCount,
                configuration,
                consents,
                billingPeriod: null,
                includeOptionalTraining: false,
                priceBreakdown,
                calculatedPrice,
                companyId: null,
            };

            await api.post("/api/apply", payload);
            setSuccess(true);
            notify(
                t(
                    "registration.notifications.success",
                    "Danke! Deine Anfrage ist bei uns angekommen."
                ),
                "success"
            );
        } catch (submitError) {
            console.error("Fehler bei der Übermittlung", submitError);
            const rawMessage =
                submitError.response?.data?.message ||
                submitError.message ||
                t("registration.errors.unknown", "Unbekannter Fehler");
            const prefix = t(
                "registration.notifications.errorPrefix",
                "Fehler bei der Übermittlung: "
            );
            const combinedMessage = `${prefix}${rawMessage}`;
            setError(combinedMessage);
            notify(combinedMessage, "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleNavigateHome = () => {
        navigate("/");
    };

    if (success) {
        return (
            <>
                <Navbar />
                <main className="registration-page scoped-registration success-state">
                    <section className="registration-success">
                        <div className="success-card">
                            <h1>{t("registration.success.title", "Danke, deine Chrono-Anfrage ist bei uns eingegangen.")}</h1>
                            <p>{t("registration.success.text1")}</p>
                            <p className="success-note">{t("registration.success.note")}</p>
                            <button type="button" className="primary-button" onClick={handleNavigateHome}>
                                {t("registration.success.backButton", "Zur Startseite")}
                            </button>
                        </div>
                    </section>
                </main>
            </>
        );
    }

    return (
        <>
            <Navbar />
            <main className="registration-page scoped-registration">
                <div className="registration-content">
                    <section className="registration-hero">
                        <h1>{t("registration.hero.title", "Chrono anfragen & Zugang erhalten")}</h1>
                        <p>{t("registration.hero.text")}</p>
                        <div className="info-badge">{t("registration.hero.badge")}</div>
                    </section>

                    <form className="registration-card" onSubmit={handleSubmit}>
                        <div className="card-column configuration-column">
                            <h2>{t("registration.configuration.title", "Konfiguration")}</h2>

                            <div className="form-group">
                                <h3>{t("registration.configuration.companyTitle", "Unternehmen")}</h3>
                                <div className="form-control">
                                    <span className="label">{t("registration.configuration.countryLabel", "Land")}</span>
                                    <div
                                        className="segmented-control"
                                        role="radiogroup"
                                        aria-label={t(
                                            "registration.configuration.countryAria",
                                            "Land auswählen"
                                        )}
                                    >
                                        {countryOptions.map((option) => (
                                            <label
                                                key={option.value}
                                                className={`segment ${
                                                    configuration.country === option.value ? "is-active" : ""
                                                }`}
                                            >
                                                <input
                                                    type="radio"
                                                    name="country"
                                                    value={option.value}
                                                    checked={configuration.country === option.value}
                                                    onChange={(event) =>
                                                        handleConfigurationChange("country", event.target.value)
                                                    }
                                                />
                                                <span>{option.label}</span>
                                            </label>
                                            ))}
                                    </div>
                                </div>

                                <label className="form-control" htmlFor="industry">
                                    <span className="label">
                                        {t("registration.configuration.industryLabel", "Branche (optional)")}
                                    </span>
                                    <input
                                        id="industry"
                                        name="industry"
                                        type="text"
                                        placeholder={t(
                                            "registration.configuration.industryPlaceholder",
                                            "z. B. Agentur, Produktion, Dienstleistung"
                                        )}
                                        value={configuration.industry}
                                        onChange={(event) => handleConfigurationChange("industry", event.target.value)}
                                    />
                                </label>
                            </div>

                            <div className="form-group">
                                <h3>{t("registration.configuration.employeesTitle", "Mitarbeitende")}</h3>
                                <label className="form-control" htmlFor="employeeCount">
                                    <span className="label">
                                        {t(
                                            "registration.configuration.employeeLabel",
                                            "Anzahl Mitarbeitende"
                                        )}{" "}
                                        <span className="value">{configuration.employeeCount}</span>
                                    </span>
                                    <input
                                        id="employeeCount"
                                        name="employeeCount"
                                        type="range"
                                        min={1}
                                        max={200}
                                        value={configuration.employeeCount}
                                        style={employeeSliderStyle}
                                        onChange={(event) =>
                                            handleConfigurationChange("employeeCount", Number(event.target.value))
                                        }
                                    />
                                </label>
                                <div className="slider-input">
                                    <input
                                        type="number"
                                        min={1}
                                        max={200}
                                        value={configuration.employeeCount}
                                        onChange={(event) =>
                                            handleConfigurationChange(
                                                "employeeCount",
                                                Math.max(1, Math.min(200, Number(event.target.value) || 1))
                                            )
                                        }
                                    />
                                    <span className="hint">
                                        {t(
                                            "registration.configuration.sliderHint",
                                            "Skaliert flexibel mit deinem Team."
                                        )}
                                    </span>
                                </div>

                                <label className="form-control" htmlFor="locations">
                                    <span className="label">
                                        {t("registration.configuration.locationsLabel", "Standorte (optional)")}
                                    </span>
                                    <input
                                        id="locations"
                                        name="locations"
                                        type="text"
                                        placeholder={t(
                                            "registration.configuration.locationsPlaceholder",
                                            "z. B. St. Gallen, Zürich"
                                        )}
                                        value={configuration.locations}
                                        onChange={(event) => handleConfigurationChange("locations", event.target.value)}
                                    />
                                </label>
                            </div>

                            <div className="form-group">
                                <h3>{t("registration.modules.title", "Module wählen")}</h3>
                                <p className="group-subline">{t("registration.modules.subtitle")}</p>
                                <div className="module-grid">
                                    {moduleOptions.map((option) => {
                                        const isChecked = selectedFeatures.includes(option.key);
                                        const moduleClassName = [
                                            "module-option",
                                            isChecked ? "is-selected" : "",
                                            option.required ? "is-required" : "",
                                        ]
                                            .filter(Boolean)
                                            .join(" ");

                                        return (
                                            <label key={option.key} className={moduleClassName}>
                                                <input
                                                    type="checkbox"
                                                    name="modules"
                                                    value={option.key}
                                                    checked={isChecked}
                                                    disabled={option.required}
                                                    onChange={() => toggleFeature(option.key)}
                                                />
                                                <span className="module-label">{option.label}</span>
                                                <span className="module-price">
                                                    {pricingConfig
                                                        ? option.priceType === "perEmployee"
                                                            ? t(
                                                                "registration.pricing.perEmployeeShort",
                                                                "{{price}} / MA",
                                                                { price: formatCurrency(option.price) }
                                                            )
                                                            : t(
                                                                "registration.pricing.flatShort",
                                                                "{{price}} / Monat",
                                                                { price: formatCurrency(option.price) }
                                                            )
                                                        : t("registration.pricing.onRequest", "Preis auf Anfrage")}
                                                </span>
                                                <span className="module-description">{option.description}</span>
                                            </label>
                                        );
                                    })}
                                </div>
                                <p className="module-hint">{t("registration.modules.hint")}</p>
                                <div className="pricing-summary">
                                    <div className="pricing-header">
                                        <h4>{t("registration.pricing.title", "Preisübersicht")}</h4>
                                        {pricingConfig && (
                                            <span className="pricing-country">
                                                {t(
                                                    "registration.pricing.countryHint",
                                                    "Preise für {{country}}",
                                                    { country: t(`registration.countries.${configuration.country}`) }
                                                )}
                                            </span>
                                        )}
                                    </div>
                                    {pricingConfig && priceSummary ? (
                                        <>
                                            <ul className="pricing-list">
                                                {priceSummary.breakdown.map((item) => (
                                                    <li key={item.key} className="pricing-row">
                                                        <div className="pricing-row-info">
                                                            <span className="pricing-row-label">{item.label}</span>
                                                            <span className="pricing-row-detail">
                                                                {item.priceType === "perEmployee"
                                                                    ? t(
                                                                        "registration.pricing.perEmployeeDetail",
                                                                        "{{price}} × {{count}} MA",
                                                                        {
                                                                            price: formatCurrency(item.unitPrice),
                                                                            count: configuration.employeeCount,
                                                                        }
                                                                    )
                                                                    : t(
                                                                        "registration.pricing.flatDetail",
                                                                        "{{price}} / Monat",
                                                                        { price: formatCurrency(item.unitPrice) }
                                                                    )}
                                                            </span>
                                                        </div>
                                                        <span className="pricing-row-value">
                                                            {formatCurrency(item.total)}
                                                        </span>
                                                    </li>
                                                ))}
                                            </ul>
                                            <div className="pricing-total">
                                                <span>{t("registration.pricing.total", "Gesamt pro Monat")}</span>
                                                <span>{formatCurrency(priceSummary.total)}</span>
                                            </div>
                                            <p className="pricing-disclaimer">
                                                {t("registration.pricing.disclaimer", "Alle Preise exkl. MwSt.")}
                                            </p>
                                        </>
                                    ) : (
                                        <p className="pricing-on-request">
                                            {t(
                                                "registration.pricing.onRequestDetail",
                                                "Preise für dieses Land auf Anfrage."
                                            )}
                                        </p>
                                    )}
                                </div>
                            </div>
                        </div>

                        <div className="card-column contact-column">
                            <h2>{t("registration.contact.title", "Kontaktdaten & Anfrage senden")}</h2>

                            <div className="manual-note">
                                {t("registration.contact.note")}
                            </div>

                            <label className="form-control" htmlFor="companyName">
                                <span className="label">
                                    {t("registration.contact.companyLabel", "Firmenname")}
                                </span>
                                <input
                                    id="companyName"
                                    name="companyName"
                                    type="text"
                                    required
                                    value={form.companyName}
                                    onChange={handleFormChange}
                                    placeholder={t(
                                        "registration.contact.companyPlaceholder",
                                        "z. B. Chrono Solutions GmbH"
                                    )}
                                />
                            </label>

                            <label className="form-control" htmlFor="contactName">
                                <span className="label">
                                    {t("registration.contact.contactLabel", "Ansprechperson")}
                                </span>
                                <input
                                    id="contactName"
                                    name="contactName"
                                    type="text"
                                    required
                                    value={form.contactName}
                                    onChange={handleFormChange}
                                    placeholder={t(
                                        "registration.contact.contactPlaceholder",
                                        "Vor- und Nachname"
                                    )}
                                />
                            </label>

                            <label className="form-control" htmlFor="email">
                                <span className="label">
                                    {t("registration.contact.emailLabel", "E-Mail")}
                                </span>
                                <input
                                    id="email"
                                    name="email"
                                    type="email"
                                    required
                                    value={form.email}
                                    onChange={handleFormChange}
                                    placeholder={t(
                                        "registration.contact.emailPlaceholder",
                                        "name@unternehmen.ch"
                                    )}
                                />
                            </label>

                            <label className="form-control" htmlFor="phone">
                                <span className="label">
                                    {t("registration.contact.phoneLabel", "Telefon (optional)")}
                                </span>
                                <input
                                    id="phone"
                                    name="phone"
                                    type="tel"
                                    value={form.phone}
                                    onChange={handleFormChange}
                                    placeholder={t(
                                        "registration.contact.phonePlaceholder",
                                        "+41 71 000 00 00"
                                    )}
                                />
                            </label>

                            <label className="form-control" htmlFor="additionalInfo">
                                <span className="label">
                                    {t("registration.contact.moreLabel", "Weitere Informationen oder Fragen")}
                                </span>
                                <textarea
                                    id="additionalInfo"
                                    name="additionalInfo"
                                    rows={4}
                                    value={form.additionalInfo}
                                    onChange={handleFormChange}
                                    placeholder={t(
                                        "registration.contact.morePlaceholder",
                                        "Was sollen wir bei der Einrichtung beachten?"
                                    )}
                                />
                            </label>

                            <label className="checkbox-control">
                                <input
                                    type="checkbox"
                                    name="terms"
                                    checked={consents.terms}
                                    onChange={handleConsentChange}
                                    required
                                />
                                <span>{t("registration.contact.terms")}</span>
                            </label>

                            <label className="checkbox-control">
                                <input
                                    type="checkbox"
                                    name="contact"
                                    checked={consents.contact}
                                    onChange={handleConsentChange}
                                    required
                                />
                                <span>{t("registration.contact.contactConsent")}</span>
                            </label>

                            {error && <div className="form-error">{error}</div>}

                            <button type="submit" className="primary-button" disabled={isSubmitting}>
                                {isSubmitting
                                    ? t("registration.contact.sending", "Senden …")
                                    : t("registration.contact.submit", "Unverbindliche Anfrage senden")}
                            </button>

                            <p className="response-hint">{t("registration.contact.responseHint")}</p>
                        </div>
                    </form>
                </div>
            </main>
        </>
    );
};

export default Registration;
