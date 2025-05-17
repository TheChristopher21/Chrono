import React, { useEffect, useState } from 'react';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import Navbar from '../components/Navbar';
import '../styles/CompanyManagementScoped.css'; // Scoped CSS für Light/Dark etc.

const CompanyManagementPage = () => {
    const { t } = useTranslation();

    // Firmenliste
    const [companies, setCompanies] = useState([]);
    const [loading, setLoading]     = useState(true);
    const [error, setError]         = useState('');

    // Neues Formular: "Nur Firma anlegen"
    const [newCompanyName, setNewCompanyName] = useState('');

    // Alternativ: "Firma + Admin" anlegen
    const [createWithAdmin, setCreateWithAdmin] = useState({
        companyName   : '',
        adminUsername : '',
        adminPassword : '',
        adminEmail    : '',
        adminFirstName: '',
        adminLastName : ''
    });

    // Edit-Mode
    const [editingCompany, setEditingCompany] = useState(null);

    useEffect(() => {
        fetchCompanies();
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

    // ------------------------------------------------------
    // (1) "Nur Firma" anlegen
    // ------------------------------------------------------
    async function handleCreateCompany(e) {
        e.preventDefault();
        if (!newCompanyName.trim()) return;
        try {
            const payload = { name: newCompanyName.trim(), active: true };
            await api.post('/api/superadmin/companies', payload);
            setNewCompanyName('');
            fetchCompanies();
        } catch (err) {
            console.error('Error creating company:', err);
        }
    }

    // ------------------------------------------------------
    // (2) Firma + Admin in einem Rutsch anlegen
    // ------------------------------------------------------
    async function handleCreateWithAdmin(e) {
        e.preventDefault();
        if (!createWithAdmin.companyName.trim() ||
            !createWithAdmin.adminUsername.trim() ||
            !createWithAdmin.adminPassword.trim()
        ) {
            alert('Bitte Firmenname, Admin-Username und Admin-Passwort angeben');
            return;
        }
        try {
            const payload = {
                companyName   : createWithAdmin.companyName.trim(),
                adminUsername : createWithAdmin.adminUsername.trim(),
                adminPassword : createWithAdmin.adminPassword,
                adminEmail    : createWithAdmin.adminEmail,
                adminFirstName: createWithAdmin.adminFirstName,
                adminLastName : createWithAdmin.adminLastName
            };
            const res = await api.post('/api/superadmin/companies/create-with-admin', payload);
            // optional: Zeig dem User an, was erstellt wurde
            console.log('Created Company + Admin:', res.data);
            // Felder zurücksetzen
            setCreateWithAdmin({
                companyName: '', adminUsername: '', adminPassword: '',
                adminEmail: '', adminFirstName: '', adminLastName: ''
            });
            fetchCompanies();
            alert(`Firma + AdminUser wurden erstellt.`);
        } catch (err) {
            console.error('Error create-with-admin:', err);
            alert('Fehler beim Anlegen von Firma + Admin');
        }
    }

    // ------------------------------------------------------
    // (3) Toggle Active
    // ------------------------------------------------------
    async function toggleActive(co) {
        try {
            const updated = {
                name  : co.name,
                active: !co.active
            };
            await api.put(`/api/superadmin/companies/${co.id}`, updated);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Toggle', err);
        }
    }

    // ------------------------------------------------------
    // (4) Edit: Name / Active
    // ------------------------------------------------------
    function startEdit(company) {
        setEditingCompany({ ...company });
    }
    async function handleSaveEdit(e) {
        e.preventDefault();
        if (!editingCompany.name.trim()) return;
        try {
            const payload = {
                name  : editingCompany.name.trim(),
                active: editingCompany.active
            };
            await api.put(`/api/superadmin/companies/${editingCompany.id}`, payload);
            setEditingCompany(null);
            fetchCompanies();
        } catch (err) {
            console.error('Fehler beim Edit:', err);
        }
    }

    // ------------------------------------------------------
    // (5) Delete
    // ------------------------------------------------------
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

    // ========================================================================
    return (
        <div className="company-management-page scoped-company">
            <Navbar />
            <h2 className="cmp-title">
                {t('company.management.title','Firmen-Verwaltung (SUPERADMIN)')}
            </h2>

            {loading ? <p>{t("loading")}</p> : error ? <p style={{color:'red'}}>{error}</p> : (
                <>
                    {/*  --------  Sektion: Nur-Firma anlegen  -------- */}
                    <section className="cmp-section">
                        <h3>{t('Nur Firma anlegen')}</h3>
                        <form onSubmit={handleCreateCompany} className="cmp-form">
                            <input
                                type="text"
                                placeholder="Firmenname"
                                value={newCompanyName}
                                onChange={e => setNewCompanyName(e.target.value)}
                            />
                            <button type="submit">Erstellen</button>
                        </form>
                    </section>

                    {/*  --------  Sektion: Firma + Admin anlegen  -------- */}
                    <section className="cmp-section">
                        <h3>{t('Firma + Admin anlegen')}</h3>
                        <form onSubmit={handleCreateWithAdmin} className="cmp-form">
                            <input
                                type="text"
                                placeholder="Firmenname"
                                value={createWithAdmin.companyName}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, companyName: e.target.value})}
                            />
                            <input
                                type="text"
                                placeholder="Admin-Username"
                                value={createWithAdmin.adminUsername}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, adminUsername: e.target.value})}
                            />
                            <input
                                type="password"
                                placeholder="Admin-Passwort"
                                value={createWithAdmin.adminPassword}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, adminPassword: e.target.value})}
                            />
                            <input
                                type="text"
                                placeholder="Admin Vorname (optional)"
                                value={createWithAdmin.adminFirstName}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, adminFirstName: e.target.value})}
                            />
                            <input
                                type="text"
                                placeholder="Admin Nachname (optional)"
                                value={createWithAdmin.adminLastName}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, adminLastName: e.target.value})}
                            />
                            <input
                                type="email"
                                placeholder="Admin E-Mail (optional)"
                                value={createWithAdmin.adminEmail}
                                onChange={e => setCreateWithAdmin({...createWithAdmin, adminEmail: e.target.value})}
                            />

                            <button type="submit">Firma+Admin erstellen</button>
                        </form>
                    </section>

                    {/*  --------  Liste aller Companies  -------- */}
                    <section className="cmp-section">
                        <h3>Bestehende Firmen</h3>
                        <ul className="cmp-list">
                            {companies.map(co => (
                                <li key={co.id} className="cmp-item">
                                    {editingCompany && editingCompany.id === co.id ? (
                                        <form onSubmit={handleSaveEdit} className="cmp-inline-form">
                                            <input
                                                type="text"
                                                value={editingCompany.name}
                                                onChange={e => setEditingCompany({...editingCompany, name:e.target.value})}
                                            />
                                            <label style={{display:'flex', alignItems:'center', gap:'4px'}}>
                                                <input
                                                    type="checkbox"
                                                    checked={editingCompany.active}
                                                    onChange={e => setEditingCompany({...editingCompany, active: e.target.checked})}
                                                />
                                                Aktiv?
                                            </label>
                                            <button type="submit">Speichern</button>
                                            <button type="button" onClick={() => setEditingCompany(null)}>Abbruch</button>
                                        </form>
                                    ) : (
                                        <div className="cmp-company-row">
                                            <div className="cmp-info">
                                                <strong>{co.name}</strong>
                                                <span className={co.active ? 'cmp-active' : 'cmp-inactive'}>
                          {co.active ? '(Aktiv)' : '(Inaktiv)'}
                        </span>
                                                <span className="cmp-users">
                          {co.userCount} User
                        </span>
                                            </div>
                                            <div className="cmp-btns">
                                                <button onClick={() => startEdit(co)}>Bearbeiten</button>
                                                <button onClick={() => toggleActive(co)}>
                                                    {co.active ? 'Deaktiv' : 'Aktiv'}
                                                </button>
                                                <button className="danger" onClick={() => handleDeleteCompany(co.id)}>Löschen</button>
                                            </div>
                                        </div>
                                    )}
                                </li>
                            ))}
                        </ul>
                    </section>
                </>
            )}
        </div>
    );
};

export default CompanyManagementPage;
