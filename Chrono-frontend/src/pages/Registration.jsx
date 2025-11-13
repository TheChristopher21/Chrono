import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Navbar from "../components/Navbar";
import "../styles/RegistrationScoped.css";
import { useNotification } from "../context/NotificationContext";

const COUNTRIES = [
    { value: "ch", label: "Schweiz" },
    { value: "de", label: "Deutschland" },
    { value: "other", label: "Andere" },
];

const Registration = () => {
    const navigate = useNavigate();
    const { notify } = useNotification();

    const [step, setStep] = useState(1);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [hasSubmitted, setHasSubmitted] = useState(false);
    const [error, setError] = useState("");

    const [formData, setFormData] = useState({
        companyName: "",
        firstName: "",
        lastName: "",
        email: "",
        password: "",
        country: COUNTRIES[0].value,
        employeeCount: "",
        acceptedTerms: false,
    });

    const stepIndicator = useMemo(() => `${step} von 2`, [step]);

    const handleChange = (event) => {
        const { name, type, value, checked } = event.target;
        setFormData((prev) => ({
            ...prev,
            [name]: type === "checkbox" ? checked : value,
        }));
    };

    const stepOneIsValid = useMemo(() => {
        return (
            formData.firstName.trim().length > 1 &&
            formData.lastName.trim().length > 1 &&
            formData.email.trim() !== "" &&
            formData.password.trim().length >= 8
        );
    }, [formData.firstName, formData.lastName, formData.email, formData.password]);

    const handleNext = (event) => {
        event.preventDefault();
        if (!stepOneIsValid) {
            setError("Bitte fülle die Felder von Schritt 1 vollständig aus (Passwort mindestens 8 Zeichen).");
            return;
        }
        setError("");
        setStep(2);
    };

    const handleBack = (event) => {
        event.preventDefault();
        setError("");
        setStep(1);
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!formData.acceptedTerms) {
            setError("Bitte akzeptiere die AGB und Datenschutzbestimmungen.");
            return;
        }
        setError("");
        setIsSubmitting(true);
        try {
            // Hier könnte ein echter API-Call eingefügt werden.
            await new Promise((resolve) => setTimeout(resolve, 600));
            setHasSubmitted(true);
            notify("Dein Konto wurde angelegt. Du kannst dich jetzt anmelden!", "success");
        } catch (submitError) {
            console.error("Registrierung fehlgeschlagen", submitError);
            setError("Etwas ist schiefgelaufen. Bitte versuche es erneut.");
            notify("Registrierung fehlgeschlagen", "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleGoToLogin = () => {
        navigate("/login");
    };

    return (
        <>
            <Navbar />
            <div className="registration-page register-flow">
                <div className="registration-wrapper">
                    <div className="breadcrumb">
                        <Link to="/">← Zurück zur Startseite</Link>
                    </div>

                    <div className="registration-hero">
                        <p className="step-pill">Schritt {stepIndicator}</p>
                        <h1>Jetzt kostenlos starten – ohne Kreditkarte.</h1>
                        <p className="registration-subline">
                            Erstelle dein Chrono-Konto in weniger als 1 Minute und teste alle Funktionen in Ruhe mit
                            deinem Team.
                        </p>
                    </div>

                    <div className="registration-layout">
                        <div className="registration-form-card">
                            {hasSubmitted ? (
                                <div className="registration-success">
                                    <h2>Geschafft! 🎉</h2>
                                    <p>
                                        Dein Zugang ist bereit. Wir haben dir gerade eine Bestätigung per E-Mail geschickt –
                                        melde dich im nächsten Schritt einfach mit deinen Daten an.
                                    </p>
                                    <button type="button" className="primary-button" onClick={handleGoToLogin}>
                                        Zum Login
                                    </button>
                                </div>
                            ) : (
                                <form onSubmit={step === 1 ? handleNext : handleSubmit} className="register-form">
                                    <fieldset disabled={isSubmitting}>
                                        {step === 1 ? (
                                            <>
                                                <label className="input-field">
                                                    <span>Vorname</span>
                                                    <input
                                                        type="text"
                                                        name="firstName"
                                                        value={formData.firstName}
                                                        onChange={handleChange}
                                                        placeholder="Max"
                                                        autoComplete="given-name"
                                                        required
                                                    />
                                                </label>
                                                <label className="input-field">
                                                    <span>Nachname</span>
                                                    <input
                                                        type="text"
                                                        name="lastName"
                                                        value={formData.lastName}
                                                        onChange={handleChange}
                                                        placeholder="Muster"
                                                        autoComplete="family-name"
                                                        required
                                                    />
                                                </label>
                                                <label className="input-field">
                                                    <span>E-Mail</span>
                                                    <input
                                                        type="email"
                                                        name="email"
                                                        value={formData.email}
                                                        onChange={handleChange}
                                                        placeholder="max@firma.ch"
                                                        autoComplete="email"
                                                        required
                                                    />
                                                </label>
                                                <label className="input-field">
                                                    <span>Passwort</span>
                                                    <input
                                                        type="password"
                                                        name="password"
                                                        value={formData.password}
                                                        onChange={handleChange}
                                                        placeholder="Mindestens 8 Zeichen"
                                                        autoComplete="new-password"
                                                        required
                                                    />
                                                </label>
                                            </>
                                        ) : (
                                            <>
                                                <label className="input-field">
                                                    <span>Firmenname</span>
                                                    <input
                                                        type="text"
                                                        name="companyName"
                                                        value={formData.companyName}
                                                        onChange={handleChange}
                                                        placeholder="Chrono AG"
                                                        autoComplete="organization"
                                                        required
                                                    />
                                                </label>
                                                <label className="input-field">
                                                    <span>Land</span>
                                                    <select
                                                        name="country"
                                                        value={formData.country}
                                                        onChange={handleChange}
                                                        required
                                                    >
                                                        {COUNTRIES.map((country) => (
                                                            <option key={country.value} value={country.value}>
                                                                {country.label}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                                <label className="input-field">
                                                    <span>Mitarbeiteranzahl (optional)</span>
                                                    <input
                                                        type="number"
                                                        name="employeeCount"
                                                        min={1}
                                                        value={formData.employeeCount}
                                                        onChange={handleChange}
                                                        placeholder="z. B. 25"
                                                    />
                                                </label>
                                                <label className="checkbox-field">
                                                    <input
                                                        type="checkbox"
                                                        name="acceptedTerms"
                                                        checked={formData.acceptedTerms}
                                                        onChange={handleChange}
                                                        required
                                                    />
                                                    <span>
                                                        Ich akzeptiere die <Link to="/agb">AGB</Link> und{' '}
                                                        <Link to="/datenschutz">Datenschutzbestimmungen</Link>.
                                                    </span>
                                                </label>
                                            </>
                                        )}
                                    </fieldset>
                                    {error && <p className="form-error">{error}</p>}
                                    <div className="form-actions">
                                        {step === 2 && (
                                            <button type="button" className="secondary-button" onClick={handleBack}>
                                                Zurück
                                            </button>
                                        )}
                                        <button
                                            type="submit"
                                            className="primary-button"
                                            disabled={isSubmitting || (step === 1 && !stepOneIsValid)}
                                        >
                                            {step === 1 ? "Weiter" : isSubmitting ? "Wird erstellt…" : "Konto erstellen"}
                                        </button>
                                    </div>
                                    {step === 1 ? (
                                        <p className="form-footnote">Keine Kreditkarte erforderlich.</p>
                                    ) : (
                                        <p className="form-footnote">
                                            Du bekommst im nächsten Schritt Zugang zu deinem Chrono-Dashboard.
                                        </p>
                                    )}
                                </form>
                            )}
                        </div>

                        <aside className="registration-aside">
                            <div className="trust-card">
                                <ul>
                                    <li>
                                        <span className="check-icon" aria-hidden="true">
                                            ✅
                                        </span>
                                        <span>14 Tage kostenlos testen</span>
                                    </li>
                                    <li>
                                        <span className="check-icon" aria-hidden="true">
                                            ✅
                                        </span>
                                        <span>Keine Kreditkarte erforderlich</span>
                                    </li>
                                    <li>
                                        <span className="check-icon" aria-hidden="true">
                                            ✅
                                        </span>
                                        <span>Schweizer Server &amp; DSGVO-konform</span>
                                    </li>
                                </ul>
                            </div>

                            <div className="testimonial-card">
                                <p className="testimonial-quote">
                                    „Seit wir Chrono nutzen, ist unsere Lohnabrechnung von 3 Stunden auf 20 Minuten geschrumpft.“
                                </p>
                                <p className="testimonial-author">– Anna Müller, Inhaberin einer Reinigungsfirma</p>
                            </div>
                        </aside>
                    </div>
                </div>
            </div>
        </>
    );
};

export default Registration;
