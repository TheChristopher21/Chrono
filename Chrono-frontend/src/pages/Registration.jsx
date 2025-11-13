import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import api from "../utils/api";
import { useNotification } from "../context/NotificationContext";
import { BASE_FEATURE, FEATURE_CATALOG } from "../constants/registrationFeatures";

const COUNTRY_OPTIONS = [
    { value: "ch", label: "Schweiz" },
    { value: "de", label: "Deutschland" },
    { value: "other", label: "anderes" },
];

const MODULE_OPTIONS = [
    {
        key: BASE_FEATURE.key,
        label: BASE_FEATURE.name || "Zeiterfassung (Basis)",
        description:
            BASE_FEATURE.description || "Pflichtmodul – bildet die Grundlage für deinen Chrono-Account.",
        required: true,
    },
    ...FEATURE_CATALOG.filter((feature) => feature.key !== BASE_FEATURE.key).map((feature) => ({
        key: feature.key,
        label: feature.name,
        description: feature.description,
        required: Boolean(feature.required),
    })),
];

const INITIAL_CONFIGURATION = {
    country: COUNTRY_OPTIONS[0].value,
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

    const [configuration, setConfiguration] = useState(INITIAL_CONFIGURATION);
    const [form, setForm] = useState(INITIAL_FORM);
    const [selectedFeatures, setSelectedFeatures] = useState([BASE_FEATURE.key]);
    const [consents, setConsents] = useState({ terms: false, contact: false });
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

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
        const feature = MODULE_OPTIONS.find((option) => option.key === featureKey);
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
            setError("Bitte akzeptiere unsere Bedingungen, damit wir deine Anfrage bearbeiten können.");
            return;
        }

        if (configuration.employeeCount < 1 || configuration.employeeCount > 200) {
            setError("Bitte wähle eine Mitarbeiterzahl zwischen 1 und 200.");
            return;
        }

        setIsSubmitting(true);
        try {
            const selectedFeatureNames = MODULE_OPTIONS.filter((option) => selectedFeatures.includes(option.key)).map(
                (option) => option.label
            );

            const payload = {
                ...form,
                selectedFeatures,
                selectedFeatureNames,
                employeeCount: configuration.employeeCount,
                configuration,
                consents,
                billingPeriod: null,
                includeOptionalTraining: false,
                priceBreakdown: null,
                calculatedPrice: null,
                companyId: null,
            };

            await api.post("/api/apply", payload);
            setSuccess(true);
            notify("Danke! Deine Anfrage ist bei uns angekommen.", "success");
        } catch (submitError) {
            console.error("Fehler bei der Übermittlung", submitError);
            setError(
                "Fehler bei der Übermittlung: " + (submitError.response?.data?.message || submitError.message || "Unbekannter Fehler")
            );
            notify("Fehler bei der Übermittlung: " + (submitError.message || "Unbekannter Fehler"), "error");
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
                            <h1>Danke, deine Chrono-Anfrage ist bei uns eingegangen.</h1>
                            <p>
                                Wir prüfen deine Angaben und richten deinen Account persönlich ein. In der Regel erhältst du innerhalb
                                eines Werktags eine E-Mail mit deinen Zugangsdaten oder Rückfragen.
                            </p>
                            <p className="success-note">
                                Wenn du in der Zwischenzeit Fragen hast, erreichst du uns unter support@chrono-app.ch oder
                                +41&nbsp;71&nbsp;000&nbsp;00&nbsp;00.
                            </p>
                            <button type="button" className="primary-button" onClick={handleNavigateHome}>
                                Zur Startseite
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
                        <h1>Chrono anfragen &amp; Zugang erhalten</h1>
                        <p>
                            Sag uns kurz, wie dein Unternehmen aussieht und welche Module du brauchst. Wir prüfen deine Angaben und
                            richten deinen Chrono-Account persönlich für dich ein.
                        </p>
                        <div className="info-badge">🔒 Kein automatischer Account – wir schalten dich nach Prüfung frei.</div>
                    </section>

                    <form className="registration-card" onSubmit={handleSubmit}>
                        <div className="card-column configuration-column">
                            <h2>Konfiguration</h2>

                            <div className="form-group">
                                <h3>Unternehmen</h3>
                                <div className="form-control">
                                    <span className="label">Land</span>
                                    <div className="segmented-control" role="radiogroup" aria-label="Land auswählen">
                                        {COUNTRY_OPTIONS.map((option) => (
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
                                    <span className="label">Branche (optional)</span>
                                    <input
                                        id="industry"
                                        name="industry"
                                        type="text"
                                        placeholder="z. B. Agentur, Produktion, Dienstleistung"
                                        value={configuration.industry}
                                        onChange={(event) => handleConfigurationChange("industry", event.target.value)}
                                    />
                                </label>
                            </div>

                            <div className="form-group">
                                <h3>Mitarbeitende</h3>
                                <label className="form-control" htmlFor="employeeCount">
                                    <span className="label">
                                        Anzahl Mitarbeitende <span className="value">{configuration.employeeCount}</span>
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
                                    <span className="hint">Skaliert flexibel mit deinem Team.</span>
                                </div>

                                <label className="form-control" htmlFor="locations">
                                    <span className="label">Standorte (optional)</span>
                                    <input
                                        id="locations"
                                        name="locations"
                                        type="text"
                                        placeholder="z. B. St. Gallen, Zürich"
                                        value={configuration.locations}
                                        onChange={(event) => handleConfigurationChange("locations", event.target.value)}
                                    />
                                </label>
                            </div>

                            <div className="form-group">
                                <h3>Module wählen</h3>
                                <p className="group-subline">Welche Bereiche möchtest du mit Chrono abdecken?</p>
                                <div className="module-grid">
                                    {MODULE_OPTIONS.map((option) => {
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
                                                <span className="module-description">{option.description}</span>
                                            </label>
                                        );
                                    })}
                                </div>
                                <p className="module-hint">
                                    Die Auswahl hilft uns, dir ein passendes Setup und ein klares Angebot vorzuschlagen.
                                </p>
                            </div>
                        </div>

                        <div className="card-column contact-column">
                            <h2>Kontaktdaten &amp; Anfrage senden</h2>

                            <div className="manual-note">
                                Wir richten deinen Zugang persönlich ein und melden uns mit Rückfragen oder Zugangsdaten.
                            </div>

                            <label className="form-control" htmlFor="companyName">
                                <span className="label">Firmenname</span>
                                <input
                                    id="companyName"
                                    name="companyName"
                                    type="text"
                                    required
                                    value={form.companyName}
                                    onChange={handleFormChange}
                                    placeholder="z. B. Chrono Solutions GmbH"
                                />
                            </label>

                            <label className="form-control" htmlFor="contactName">
                                <span className="label">Ansprechperson</span>
                                <input
                                    id="contactName"
                                    name="contactName"
                                    type="text"
                                    required
                                    value={form.contactName}
                                    onChange={handleFormChange}
                                    placeholder="Vor- und Nachname"
                                />
                            </label>

                            <label className="form-control" htmlFor="email">
                                <span className="label">E-Mail</span>
                                <input
                                    id="email"
                                    name="email"
                                    type="email"
                                    required
                                    value={form.email}
                                    onChange={handleFormChange}
                                    placeholder="name@unternehmen.ch"
                                />
                            </label>

                            <label className="form-control" htmlFor="phone">
                                <span className="label">Telefon (optional)</span>
                                <input
                                    id="phone"
                                    name="phone"
                                    type="tel"
                                    value={form.phone}
                                    onChange={handleFormChange}
                                    placeholder="+41 71 000 00 00"
                                />
                            </label>

                            <label className="form-control" htmlFor="additionalInfo">
                                <span className="label">Weitere Informationen oder Fragen</span>
                                <textarea
                                    id="additionalInfo"
                                    name="additionalInfo"
                                    rows={4}
                                    value={form.additionalInfo}
                                    onChange={handleFormChange}
                                    placeholder="Was sollen wir bei der Einrichtung beachten?"
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
                                <span>
                                    Ich habe die AGB und Datenschutzerklärung gelesen und akzeptiert.
                                </span>
                            </label>

                            <label className="checkbox-control">
                                <input
                                    type="checkbox"
                                    name="contact"
                                    checked={consents.contact}
                                    onChange={handleConsentChange}
                                    required
                                />
                                <span>
                                    Ich bin damit einverstanden, dass Chrono mich per E-Mail/Telefon zur Einrichtung kontaktiert.
                                </span>
                            </label>

                            {error && <div className="form-error">{error}</div>}

                            <button type="submit" className="primary-button" disabled={isSubmitting}>
                                {isSubmitting ? "Senden …" : "Unverbindliche Anfrage senden"}
                            </button>

                            <p className="response-hint">
                                💡 Du erhältst von uns in der Regel innerhalb eines Werktags eine Rückmeldung mit Zugangsdaten oder
                                Rückfragen.
                            </p>
                        </div>
                    </form>
                </div>
            </main>
        </>
    );
};

export default Registration;
