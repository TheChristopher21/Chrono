import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import '../styles/Registration.css';
import api from '../utils/api.js';
import { useNotification } from '../context/NotificationContext';

const Registration = () => {
    const navigate = useNavigate();
    const { notify } = useNotification();

    // Zustand für das Formular (Firmendaten) + welches Paket ausgewählt ist
    const [form, setForm] = useState({
        companyName: '',
        contactName: '',
        email: '',
        phone: '',
        additionalInfo: '',
    });
    const [selectedPackage, setSelectedPackage] = useState(''); // Leerer String = kein Paket gewählt

    const [error, setError] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [success, setSuccess] = useState(false);

    // Ändern der Input-Felder
    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    // Klick auf die Pricing-Karte
    const handlePackageSelect = (packageName) => {
        setSelectedPackage(packageName);
    };

    // Absenden des Formulars
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPackage) {
            setError("Bitte wählen Sie ein Paket aus.");
            return;
        }
        setIsSubmitting(true);
        try {
            // Wir fügen das gewählte Paket ins Payload ein
            const payload = {
                ...form,
                chosenPackage: selectedPackage,
            };

            const response = await api.post('/api/apply', payload);
            if (response.data.success) {
                setSuccess(true);
                notify("Ihre Bewerbung wurde erfolgreich gesendet. Wir melden uns per E-Mail bei Ihnen.");
                // Nach 3 Sekunden zur Login-Seite navigieren
                setTimeout(() => {
                    navigate('/login', { replace: true });
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
            <div className="registration-page">
                <div className="registration-content">

                    {/* Paket-Übersicht (Klickbar) */}
                    <div className="pricing-section">
                        <h2>Unsere Preise</h2>
                        <div className="pricing-cards">

                            {/* Basic-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === 'Basic' ? 'selected' : ''}`}
                                onClick={() => handlePackageSelect('Basic')}
                            >
                                <h3>Basic</h3>
                                <p>€49/Monat</p>
                                <ul>
                                    <li>Feature 1</li>
                                    <li>Feature 2</li>
                                    <li>Feature 3</li>
                                </ul>
                            </div>

                            {/* Professional-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === 'Professional' ? 'selected' : ''}`}
                                onClick={() => handlePackageSelect('Professional')}
                            >
                                <h3>Professional</h3>
                                <p>€99/Monat</p>
                                <ul>
                                    <li>Feature A</li>
                                    <li>Feature B</li>
                                    <li>Feature C</li>
                                </ul>
                            </div>

                            {/* Enterprise-Karte */}
                            <div
                                className={`pricing-card ${selectedPackage === 'Enterprise' ? 'selected' : ''}`}
                                onClick={() => handlePackageSelect('Enterprise')}
                            >
                                <h3>Enterprise</h3>
                                <p>Auf Anfrage</p>
                                <ul>
                                    <li>Individuelle Features</li>
                                    <li>Premium Support</li>
                                </ul>
                            </div>
                        </div>
                    </div>

                    {/* Bewerbungs-/Registrierungsformular */}
                    <div className="application-section">
                        <h2>Firmenbewerbung</h2>

                        {error && <p className="error-message">{error}</p>}
                        {success ? (
                            <p className="success-message">
                                Ihre Bewerbung wurde erfolgreich gesendet. Wir werden uns in Kürze per E-Mail bei Ihnen melden.
                            </p>
                        ) : (
                            <form onSubmit={handleSubmit}>

                                {/* Firmenname */}
                                <input
                                    type="text"
                                    name="companyName"
                                    value={form.companyName}
                                    onChange={handleChange}
                                    placeholder="Firmenname"
                                    required
                                />

                                {/* Ansprechpartner */}
                                <input
                                    type="text"
                                    name="contactName"
                                    value={form.contactName}
                                    onChange={handleChange}
                                    placeholder="Ansprechpartner"
                                    required
                                />

                                {/* E-Mail */}
                                <input
                                    type="email"
                                    name="email"
                                    value={form.email}
                                    onChange={handleChange}
                                    placeholder="E-Mail"
                                    required
                                />

                                {/* Telefon optional */}
                                <input
                                    type="text"
                                    name="phone"
                                    value={form.phone}
                                    onChange={handleChange}
                                    placeholder="Telefonnummer (optional)"
                                />

                                {/* Weitere Infos */}
                                <textarea
                                    name="additionalInfo"
                                    value={form.additionalInfo}
                                    onChange={handleChange}
                                    placeholder="Weitere Informationen (z. B. Anforderungen, Fragen, etc.)"
                                    rows="5"
                                    required
                                />

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
