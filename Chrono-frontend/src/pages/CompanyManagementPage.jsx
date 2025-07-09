import React, { useEffect, useState } from 'react';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext'; // Importieren, falls noch nicht geschehen

const CompanyManagementPage = () => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
        // Firmenliste
    const [companies, setCompanies] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Neues Formular: "Nur Firma anlegen"
    const [newCompanyName, setNewCompanyName] = useState('');
    const [newCompanyCanton, setNewCompanyCanton] = useState(''); // NEU
    const [newSlackWebhook, setNewSlackWebhook] = useState('');
    const [newTeamsWebhook, setNewTeamsWebhook] = useState('');
    const [newNotifyVacation, setNewNotifyVacation] = useState(false);
    const [newNotifyOvertime, setNewNotifyOvertime] = useState(false);

    // Alternativ: "Firma + Admin" anlegen
    const [createWithAdmin, setCreateWithAdmin] = useState({
        companyName: '',
        adminUsername: '',
        adminPassword: '',
        adminEmail: '',
        adminFirstName: '',
        adminLastName: '',
        companyCanton: '' // NEU
        ,
        slackWebhookUrl: '',
        teamsWebhookUrl: '',
        notifyVacation: false,
        notifyOvertime: false
    });

    // Edit-Mode
    const [editingCompany, setEditingCompany] = useState(null); // Wird { id, name, active, cantonAbbreviation } enthalten
    const [paymentDetails, setPaymentDetails] = useState({});
    const [openPayments, setOpenPayments] = useState({});

    const [changelogVersion, setChangelogVersion] = useState('');
    const [changelogTitle, setChangelogTitle] = useState('');
    const [changelogContent, setChangelogContent] = useState('');

    useEffect(() => {
        fetchCompanies();
        const interval = setInterval(fetchCompanies, 30000);
        return () => clearInterval(interval);
    }, []);

    async function fetchCompanies() {
        setLoading(true);
        setError('');
        try {
            const res = await api.get('/api/superadmin/companies');
            setCompanies(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            console.error('Error fetching companies:', err);
            setError('Fehler beim Laden der Firmenliste');
        } finally {
            setLoading(false);
        }
    }

    async function handleCreateCompany(e) {
        e.preventDefault();
        if (!newCompanyName.trim()) return;
        try {
            // NEU: cantonAbbreviation im Payload
            const payload = {
                name: newCompanyName.trim(),
                active: true,
                cantonAbbreviation: newCompanyCanton.trim().toUpperCase() || null,
                slackWebhookUrl: newSlackWebhook || null,
                teamsWebhookUrl: newTeamsWebhook || null,
                notifyVacation: newNotifyVacation,
                notifyOvertime: newNotifyOvertime
            };
            await api.post('/api/superadmin/companies', payload);
            setNewCompanyName('');
            setNewCompanyCanton(''); // NEU
            setNewSlackWebhook('');
            setNewTeamsWebhook('');
            setNewNotifyVacation(false);
            setNewNotifyOvertime(false);
            fetchCompanies();
        } catch (err) {
            console.error('Error creating company:', err);
            // Hier ggf. User-Feedback geben
        }
    }

    async function handleCreateWithAdmin(e) {
        e.preventDefault();

        if (
            !createWithAdmin.companyName.trim() ||
            !createWithAdmin.adminUsername.trim() ||
            !createWithAdmin.adminPassword.trim()
        ) {
            alert('Bitte Firmenname, Admin-Username und Admin-Passwort angeben');
            return;
        }

        try {
            const payload = {
                companyName: createWithAdmin.companyName.trim(),
                adminUsername: createWithAdmin.adminUsername.trim(),
                adminPassword: createWithAdmin.adminPassword,
                adminEmail: createWithAdmin.adminEmail,
                adminFirstName: createWithAdmin.adminFirstName,
                adminLastName: createWithAdmin.adminLastName,
                // NEU: cantonAbbreviation im Payload
                cantonAbbreviation: createWithAdmin.companyCanton.trim().toUpperCase() || null,
                slackWebhookUrl: createWithAdmin.slackWebhookUrl || null,
                teamsWebhookUrl: createWithAdmin.teamsWebhookUrl || null,
                notifyVacation: createWithAdmin.notifyVacation,
                notifyOvertime: createWithAdmin.notifyOvertime
            };

            const res = await api.post('/api/superadmin/companies/create-with-admin', payload);
            console.log('Created Company + Admin:', res.data);

            setCreateWithAdmin({
                companyName: '',
                adminUsername: '',
                adminPassword: '',
                adminEmail: '',
                adminFirstName: '',
                adminLastName: '',
                companyCanton: '', // NEU
                slackWebhookUrl: '',
                teamsWebhookUrl: '',
                notifyVacation: false,
                notifyOvertime: false
            });

            fetchCompanies();
            alert('Firma + AdminUser wurden erfolgreich erstellt.');
        } catch (err) {
            console.error('Error create-with-admin:', err);
            let backendErrorMessage = err.message; // Standard-Axios-Fehlermeldung
            if (err.response && err.response.data) {
                if (typeof err.response.data === 'string' && err.response.data.length > 0) {
                    backendErrorMessage = err.response.data;
                } else if (err.response.data.message) {
                    backendErrorMessage = err.response.data.message;
                }
            }
            alert('Fehler beim Anlegen von Firma + Admin: ' + backendErrorMessage);
        }
    }

    async function toggleActive(co) {
        try {
            const updated = { // Sende alle Felder, die das Backend erwartet, oder nur die geänderten
                name: co.name,
                active: !co.active,
                cantonAbbreviation: co.cantonAbbreviation // Behalte den Kanton bei
            };
            await api.put(`/api/superadmin/companies/${co.id}`, updated);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Toggle', err);
        }
    }

    const handlePublishChangelog = async (e) => {
        e.preventDefault();
        try {
            await api.post('/api/changelog', {
                version: changelogVersion,
                title: changelogTitle,
                content: changelogContent
            });
            alert('Changelog erfolgreich veröffentlicht!');
            // Formular zurücksetzen
            setChangelogVersion('');
            setChangelogTitle('');
            setChangelogContent('');
        } catch (error) {
            console.error('Fehler beim Veröffentlichen des Changelogs:', error);
            alert('Fehler: Konnte Changelog nicht veröffentlichen.');
        }
    };
    function startEdit(company) {
        // Stelle sicher, dass cantonAbbreviation im State ist, auch wenn es null ist
        setEditingCompany({
            ...company,
            cantonAbbreviation: company.cantonAbbreviation || '',
            slackWebhookUrl: company.slackWebhookUrl || '',
            teamsWebhookUrl: company.teamsWebhookUrl || '',
            notifyVacation: company.notifyVacation || false,
            notifyOvertime: company.notifyOvertime || false
        });
    }

    async function handleSaveEdit(e) {
        e.preventDefault();
        if (!editingCompany || !editingCompany.name.trim()) return;

        try {
            const payload = {
                name: editingCompany.name.trim(),
                active: editingCompany.active,
                // NEU: cantonAbbreviation im Payload
                cantonAbbreviation: editingCompany.cantonAbbreviation.trim().toUpperCase() || null,
                slackWebhookUrl: editingCompany.slackWebhookUrl,
                teamsWebhookUrl: editingCompany.teamsWebhookUrl,
                notifyVacation: editingCompany.notifyVacation,
                notifyOvertime: editingCompany.notifyOvertime
            };
            await api.put(`/api/superadmin/companies/${editingCompany.id}`, payload);
            setEditingCompany(null);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Edit:', err);
        }
    }

    async function handleUpdatePayment(co, paid, method, canceled = false) {
        try {
            const body = { paid, paymentMethod: method, canceled };
            await api.put(`/api/superadmin/companies/${co.id}/payment`, body);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Update Payment:', err);
        }
    }

    async function handleDeleteCompany(id) {
        if (!window.confirm('Wirklich löschen? (Company muss leer sein)')) return;
        try {
            await api.delete(`/api/superadmin/companies/${id}`);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Löschen:', err);
            alert('Fehler beim Löschen (evtl. noch User in der Firma?)');
        }
    }

    async function fetchPayments(companyId) {
        try {
            const res = await api.get(`/api/superadmin/companies/${companyId}/payments`);
            setPaymentDetails((prev) => ({ ...prev, [companyId]: res.data || [] }));
        } catch (err) {
            console.error('Fehler beim Laden der Zahlungen:', err);
            setPaymentDetails((prev) => ({ ...prev, [companyId]: [] }));
        }
    }

    function togglePayments(co) {
        setOpenPayments((prev) => {
            const open = !prev[co.id];
            if (open && !paymentDetails[co.id]) {
                fetchPayments(co.id);
            }
            return { ...prev, [co.id]: open };
        });
    }

    return (
        <div className="company-management-page scoped-company">
            <Navbar />
            <h2 className="cmp-title">
                {t('company.management.title', 'Firmen-Verwaltung (SUPERADMIN)')}
            </h2>

            {loading ? (
                <p>{t('loading', 'Lade...')}</p>
            ) : error ? (
                <p style={{ color: 'red' }}>{error}</p>
            ) : (
                <>
                    <section className="cmp-section">
                        <h3>Nur Firma anlegen</h3>
                        <form onSubmit={handleCreateCompany} className="cmp-form">
                            <input
                                type="text"
                                placeholder="Firmenname"
                                value={newCompanyName}
                                onChange={(e) => setNewCompanyName(e.target.value)}
                                required
                            />
                            {/* NEUES FELD für Kanton */}
                            <input
                                type="text"
                                placeholder="Kanton (z.B. SG, ZH)"
                                value={newCompanyCanton}
                                onChange={(e) => setNewCompanyCanton(e.target.value)}
                                maxLength="2"
                                className="text-uppercase"
                            />
                            <input
                                type="text"
                                placeholder="Slack Webhook URL"
                                value={newSlackWebhook}
                                onChange={(e) => setNewSlackWebhook(e.target.value)}
                            />
                            <input
                                type="text"
                                placeholder="Teams Webhook URL"
                                value={newTeamsWebhook}
                                onChange={(e) => setNewTeamsWebhook(e.target.value)}
                            />
                            <label>
                                <input
                                    type="checkbox"
                                    checked={newNotifyVacation}
                                    onChange={(e) => setNewNotifyVacation(e.target.checked)}
                                />
                                Urlaub-Benachrichtigungen
                            </label>
                            <label>
                                <input
                                    type="checkbox"
                                    checked={newNotifyOvertime}
                                    onChange={(e) => setNewNotifyOvertime(e.target.checked)}
                                />
                                Überstundenwarnungen
                            </label>
                            <button type="submit">Erstellen</button>
                        </form>
                    </section>

                    <section className="cmp-section">
                        <h3>Firma + Admin anlegen</h3>
                        <form onSubmit={handleCreateWithAdmin} className="cmp-form">
                            <input
                                type="text"
                                placeholder="Firmenname (*)"
                                value={createWithAdmin.companyName}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, companyName: e.target.value })
                                }
                                required
                            />
                            {/* NEUES FELD für Kanton */}
                            <input
                                type="text"
                                placeholder="Kanton Firma (z.B. SG)"
                                value={createWithAdmin.companyCanton}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, companyCanton: e.target.value })
                                }
                                maxLength="2"
                                className="text-uppercase"
                            />
                            <input
                                type="text"
                                placeholder="Slack Webhook URL"
                                value={createWithAdmin.slackWebhookUrl}
                                onChange={(e) => setCreateWithAdmin({ ...createWithAdmin, slackWebhookUrl: e.target.value })}
                            />
                            <input
                                type="text"
                                placeholder="Teams Webhook URL"
                                value={createWithAdmin.teamsWebhookUrl}
                                onChange={(e) => setCreateWithAdmin({ ...createWithAdmin, teamsWebhookUrl: e.target.value })}
                            />
                            <label>
                                <input
                                    type="checkbox"
                                    checked={createWithAdmin.notifyVacation}
                                    onChange={(e) => setCreateWithAdmin({ ...createWithAdmin, notifyVacation: e.target.checked })}
                                />
                                Urlaub-Benachrichtigungen
                            </label>
                            <label>
                                <input
                                    type="checkbox"
                                    checked={createWithAdmin.notifyOvertime}
                                    onChange={(e) => setCreateWithAdmin({ ...createWithAdmin, notifyOvertime: e.target.checked })}
                                />
                                Überstundenwarnungen
                            </label>
                            <input
                                type="text"
                                placeholder="Admin-Username (*)"
                                value={createWithAdmin.adminUsername}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, adminUsername: e.target.value })
                                }
                                required
                            />
                            <input
                                type="password"
                                placeholder="Admin-Passwort (*)"
                                value={createWithAdmin.adminPassword}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, adminPassword: e.target.value })
                                }
                                required
                            />
                            <input
                                type="text"
                                placeholder="Admin Vorname (optional)"
                                value={createWithAdmin.adminFirstName}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, adminFirstName: e.target.value })
                                }
                            />
                            <input
                                type="text"
                                placeholder="Admin Nachname (optional)"
                                value={createWithAdmin.adminLastName}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, adminLastName: e.target.value })
                                }
                            />
                            <input
                                type="email"
                                placeholder="Admin E-Mail (optional)"
                                value={createWithAdmin.adminEmail}
                                onChange={(e) =>
                                    setCreateWithAdmin({ ...createWithAdmin, adminEmail: e.target.value })
                                }
                            />
                            <button type="submit">Firma+Admin erstellen</button>
                        </form>
                    </section>

                    <section className="cmp-section">
                        <h3>Bestehende Firmen</h3>
                        <ul className="cmp-list">
                            {companies.map((co) => (
                                <li key={co.id} className="cmp-item">
                                    {editingCompany && editingCompany.id === co.id ? (
                                        <form onSubmit={handleSaveEdit} className="cmp-inline-form">
                                            {/* ... Ihr Code für das Bearbeitungs-Formular ... */}
                                            <input
                                                type="text"
                                                value={editingCompany.name}
                                                onChange={(e) =>
                                                    setEditingCompany({ ...editingCompany, name: e.target.value })
                                                }
                                                required
                                            />
                                            <input
                                                type="text"
                                                placeholder="Kanton"
                                            value={editingCompany.cantonAbbreviation}
                                            onChange={(e) =>
                                                setEditingCompany({ ...editingCompany, cantonAbbreviation: e.target.value })
                                            }
                                            maxLength="2"
                                            className="text-uppercase"
                                            style={{ width: '80px' }}
                                        />
                                        <input
                                            type="text"
                                            placeholder="Slack Webhook URL"
                                            value={editingCompany.slackWebhookUrl}
                                            onChange={(e) => setEditingCompany({ ...editingCompany, slackWebhookUrl: e.target.value })}
                                        />
                                        <input
                                            type="text"
                                            placeholder="Teams Webhook URL"
                                            value={editingCompany.teamsWebhookUrl}
                                            onChange={(e) => setEditingCompany({ ...editingCompany, teamsWebhookUrl: e.target.value })}
                                        />
                                        <label>
                                            <input
                                                type="checkbox"
                                                checked={editingCompany.notifyVacation}
                                                onChange={(e) => setEditingCompany({ ...editingCompany, notifyVacation: e.target.checked })}
                                            />
                                            Urlaub-Benachrichtigungen
                                        </label>
                                        <label>
                                            <input
                                                type="checkbox"
                                                checked={editingCompany.notifyOvertime}
                                                onChange={(e) => setEditingCompany({ ...editingCompany, notifyOvertime: e.target.checked })}
                                            />
                                            Überstundenwarnungen
                                        </label>
                                        <label style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                                <input
                                                    type="checkbox"
                                                    checked={editingCompany.active}
                                                    onChange={(e) =>
                                                        setEditingCompany({ ...editingCompany, active: e.target.checked })
                                                    }
                                                />
                                                Aktiv?
                                            </label>
                                            <button type="submit">Speichern</button>
                                            <button type="button" onClick={() => setEditingCompany(null)}>
                                                Abbruch
                                            </button>
                                        </form>
                                    ) : (
                                        <>
                                            <div className="cmp-company-row">
                                                <div className="cmp-info">
                                                    <strong>{co.name}</strong>
                                                    {co.cantonAbbreviation && <span className="cmp-canton">({co.cantonAbbreviation})</span>}
                                                    <span className={co.active ? 'cmp-active' : 'cmp-inactive'}>
                        {co.active ? '(Aktiv)' : '(Inaktiv)'}
                    </span>
                                                    <span className="cmp-users">{co.userCount} User</span>
                                                    <span className="cmp-payment">
                        {co.paid ? 'Bezahlt' : 'Offen'}
                                                        {co.paymentMethod ? ` - ${co.paymentMethod}` : ''}
                                                        {co.canceled ? ' (gekündigt)' : ''}
                    </span>
                                                </div>
                                                <div className="cmp-btns">
                                                    <button onClick={() => startEdit(co)}>Bearbeiten</button>
                                                    <button onClick={() => toggleActive(co)}>
                                                        {co.active ? 'Deaktiv' : 'Aktiv'}
                                                    </button>
                                                    <button
                                                        onClick={() =>
                                                            handleUpdatePayment(
                                                                co,
                                                                !co.paid,
                                                                co.paymentMethod || 'manuell'
                                                            )
                                                        }
                                                    >
                                                        Payment {co.paid ? 'zurücksetzen' : 'bestätigen'}
                                                    </button>
                                                    <button onClick={() => togglePayments(co)}>
                                                        {openPayments[co.id]
                                                            ? 'Zahlungen ausblenden'
                                                            : 'Zahlungen anzeigen'}
                                                    </button>
                                                    <button className="danger" onClick={() => handleDeleteCompany(co.id)}>
                                                        Löschen
                                                    </button>
                                                </div>
                                            </div>

                                            {openPayments[co.id] && (
                                                <table className="cmp-payments-table">
                                                    <thead>
                                                    <tr>
                                                        <th>ID</th>
                                                        <th>Betrag</th>
                                                        <th>Status</th>
                                                        <th>Erstellt</th>
                                                    </tr>
                                                    </thead>
                                                    <tbody>
                                                    {(paymentDetails[co.id] || []).map((p) => (
                                                        <tr key={p.id}>
                                                            <td>{p.id}</td>
                                                            <td>
                                                                {(p.amount / 100).toFixed(2)}{' '}
                                                                {p.currency?.toUpperCase()}
                                                            </td>
                                                            <td>{p.status}</td>
                                                            <td>
                                                                {new Date(p.created * 1000).toLocaleString()}
                                                            </td>
                                                        </tr>
                                                    ))}
                                                    </tbody>
                                                </table>
                                            )}
                                        </>
                                    )}
                                </li>
                            ))}
                        </ul>
                        {currentUser?.roles?.includes('ROLE_SUPERADMIN') && (
                            <section className="cmp-section">
                                <h2>Release Notes / Changelog erstellen</h2>
                                <form onSubmit={handlePublishChangelog} className="cmp-form">
                                    <div className="form-group">
                                        <label htmlFor="version">Version (z.B. v1.1.0)</label>
                                        <input
                                            id="version"
                                            type="text"
                                            value={changelogVersion}
                                            onChange={(e) => setChangelogVersion(e.target.value)}
                                            required
                                            placeholder="v1.1.0"
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="title">Titel</label>
                                        <input
                                            id="title"
                                            type="text"
                                            value={changelogTitle}
                                            onChange={(e) => setChangelogTitle(e.target.value)}
                                            required
                                            placeholder="Neue Funktionen im Dashboard"
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="content">Änderungen (Markdown wird unterstützt)</label>
                                        <textarea
                                            id="content"
                                            rows="10"
                                            value={changelogContent}
                                            onChange={(e) => setChangelogContent(e.target.value)}
                                            required
                                            placeholder="- Neues Feature: ...\n- Bugfix: ..."
                                        ></textarea>
                                    </div>
                                    <button type="submit" className="button">Veröffentlichen</button>
                                </form>
                            </section>
                        )}
                    </section>
                </>
            )}
        </div>
    );
};

export default CompanyManagementPage;