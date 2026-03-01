// src/pages/PayslipsPage.jsx
import { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import { useTranslation } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

// Styles
import "../styles/UserPayslipsPageScoped.css"; // <— NEU

const PayslipsPage = () => {
    const { t } = useTranslation();
    const { currentUser } = useAuth();
    const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN');

    // States...
    const [pendingSlips, setPendingSlips] = useState([]);
    const [approvedSlips, setApprovedSlips] = useState([]);
    const [users, setUsers] = useState([]);
    const [form, setForm] = useState({ userId: '', start: '', end: '', payoutDate: '' });
    const [filter, setFilter] = useState({ name: '', start: '', end: '' });
    const [logoFile, setLogoFile] = useState(null);
    const [scheduleVisible, setScheduleVisible] = useState(false);
    const [isPendingExpanded, setIsPendingExpanded] = useState(true);
    const [isApprovedExpanded, setIsApprovedExpanded] = useState(true);

    useEffect(() => {
        if (!currentUser) return;
        if (isAdmin) {
            api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || []));
            api.get('/api/payslips/admin/approved', { params: filter }).then(res => setApprovedSlips(res.data || []));
            api.get('/api/admin/users').then(res => setUsers(res.data || []));
        } else {
            // User-Ansicht
            api.get('/api/payslips/my', { params: filter }).then(res => setApprovedSlips(res.data || []));
        }
    }, [currentUser, filter, isAdmin]);

    // Admin-Funktionen...
    const approve = id => api.post(`/api/payslips/approve/${id}`).then(() => api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || [])));
    const approveAll = () => {
        const comment = prompt(t('payslips.approveAll'));
        if (comment !== null) {
            api.post('/api/payslips/approve-all', null, { params: { comment } }).then(() => api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || [])));
        }
    };
    const createPayslip = (e) => {
        e.preventDefault();
        if (!form.userId || !form.start || !form.end) return;
        api.post('/api/payslips/generate', null, { params: form }).then(() => {
            setForm({ userId: '', start: '', end: '', payoutDate: '' });
            api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || []));
        });
    };
    const scheduleAll = () => setScheduleVisible(true);
    const confirmScheduleAll = (day) => {
        api.post('/api/payslips/schedule-all', null, { params: { day } });
        setScheduleVisible(false);
    };
    const editPayoutDate = (id, current) => {
        const val = prompt(t('payslips.enterPayoutDate'), current || '');
        if (val) {
            api.post(`/api/payslips/set-payout/${id}`, null, { params: { payoutDate: val } }).then(() => api.get('/api/payslips/admin/pending').then(res => setPendingSlips(res.data || [])));
        }
    };
    const uploadLogo = () => {
        if (!logoFile) return;
        const formData = new FormData();
        formData.append('file', logoFile);
        api.put('/api/admin/company/logo', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
            .then(() => alert(t('payslips.logoSaved')))
            .catch(() => alert(t('payslips.logoSaveError')));
    };
    const printPdf = (id) => {
        const url = isAdmin ? `/api/payslips/admin/pdf/${id}` : `/api/payslips/pdf/${id}`;
        api.get(url, { responseType: 'blob', params: { lang: 'de' } })
            .then(res => {
                const fileURL = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
                window.open(fileURL, '_blank')?.print();
            })
            .catch(() => alert(t('payslips.printError')));
    };

    const renderAdminSections = () => (
        <>
            <div className="top-sections-grid">
                <section className="content-section">
                    <h3 className="section-title">{t('payslips.generateManual', 'Manuell Erstellen')}</h3>
                    <form className="generate-form" onSubmit={createPayslip}>
                        <div className="form-group">
                            <label htmlFor="user-select">{t('payslips.selectUser', 'Benutzer wählen')}</label>
                            <select id="user-select" value={form.userId} onChange={e => setForm({ ...form, userId: e.target.value })} required>
                                <option value="">{t('payslips.selectUser', 'Benutzer wählen')}</option>
                                {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
                            </select>
                        </div>
                        <div className="form-group">
                            <label htmlFor="start-date">{t('payslips.start', 'Startdatum')}</label>
                            <input id="start-date" type="date" value={form.start} onChange={e => setForm({ ...form, start: e.target.value })} required />
                        </div>
                        <div className="form-group">
                            <label htmlFor="end-date">{t('payslips.end', 'Enddatum')}</label>
                            <input id="end-date" type="date" value={form.end} onChange={e => setForm({ ...form, end: e.target.value })} required />
                        </div>
                        <button type="submit" className="button-primary">{t('payslips.generate', 'Erstellen')}</button>
                    </form>
                </section>
                <section className="content-section">
                    <h3 className="section-title">{t('payslips.logoUploadTitle', 'Firmenlogo für Abrechnungen')}</h3><br></br>
                    <p className="section-description">{t('payslips.logoUploadDesc', 'Dieses Logo erscheint auf allen PDF-Abrechnungen.')}</p>
                    <div className="logo-upload-form">
                        <label htmlFor="logo-upload-input" className="custom-file-upload">
                            {logoFile ? logoFile.name : t('payslips.selectFile', 'Datei auswählen')}
                        </label>
                        <input id="logo-upload-input" type="file" accept="image/*" onChange={e => setLogoFile(e.target.files[0])} style={{ display: 'none' }} />
                        <button type="button" onClick={uploadLogo} className="button-secondary">{t('payslips.saveLogo', 'Logo speichern')}</button>
                    </div>
                </section>
            </div>
            <section className="content-section">
                <div className="section-header" role="button" onClick={() => setIsPendingExpanded(!isPendingExpanded)}>
                    <h3 className="section-title">{t('payslips.pendingTitle')}</h3>
                    <span className="toggle-icon">{isPendingExpanded ? '▲' : '▼'}</span>
                </div>
                {isPendingExpanded && (
                    <>
                        <div className="controls-bar">
                            <button type="button" className="button-primary" onClick={approveAll}>{t('payslips.approveAll')}</button>
                            <button type="button" className="button-secondary" onClick={scheduleAll}>{t('payslips.scheduleAll')}</button>
                        </div>
                        <div className="table-wrapper">
                            <table className="payslip-table">
                                <thead>
                                <tr>
                                    <th>{t('payslips.user')}</th><th>{t('payslips.period')}</th><th>{t('payslips.gross')}</th>
                                    <th>{t('payslips.net')}</th><th>{t('payslips.payoutDate')}</th><th className="actions-col">{t('userManagement.table.actions')}</th>
                                </tr>
                                </thead>
                                <tbody>
                                {pendingSlips.map(ps => (
                                    <tr key={ps.id}>
                                        <td data-label={t('payslips.user')}>{ps.firstName} {ps.lastName}</td><td data-label={t('payslips.period')}>{ps.periodStart} – {ps.periodEnd}</td>
                                        <td data-label={t('payslips.gross')}>{ps.grossSalary?.toFixed(2)} {ps.currency || 'CHF'}</td><td data-label={t('payslips.net')}>{ps.netSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                                        <td data-label={t('payslips.payoutDate')}>{ps.payoutDate}</td>
                                        <td className="actions-col">
                                            <button type="button" className="button-secondary" onClick={() => editPayoutDate(ps.id, ps.payoutDate)}>{t('payslips.editPayout')}</button>
                                            <button type="button" className="button-success" onClick={() => approve(ps.id)}>{t('payslips.approve')}</button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}
            </section>
        </>
    );

    return (
        <>
            <Navbar />
            <div className={`${isAdmin ? 'admin-payslips-page' : 'user-payslips-page'} scoped-dashboard`}>
                <header className="dashboard-header">
                    <h1>{isAdmin ? t('navbar.payslips') : t('payslips.myPayslips', 'Meine Lohnabrechnungen')}</h1>
                </header>

                {isAdmin && renderAdminSections()}

                <section className="content-section">
                    <div className="section-header" role="button" onClick={() => setIsApprovedExpanded(!isApprovedExpanded)}>
                        <h3 className="section-title">{t('payslips.approvedTitle')}</h3>
                        <span className="toggle-icon">{isApprovedExpanded ? '▲' : '▼'}</span>
                    </div>
                    {isApprovedExpanded && (
                        <>
                            <div className="controls-bar filter-form">
                                {isAdmin && (
                                    <div className="form-group">
                                        <label htmlFor="filter-name">{t('payslips.filterName', 'Name')}</label>
                                        <input id="filter-name" type="text" className="filter-input" value={filter.name} onChange={e => setFilter({ ...filter, name: e.target.value })} />
                                    </div>
                                )}
                                <div className="form-group">
                                    <label htmlFor="filter-start">{t('payslips.start', 'Startdatum')}</label>
                                    <input id="filter-start" type="date" className="filter-input" value={filter.start} onChange={e => setFilter({ ...filter, start: e.target.value })} />
                                </div>
                                <div className="form-group">
                                    <label htmlFor="filter-end">{t('payslips.end', 'Enddatum')}</label>
                                    <input id="filter-end" type="date" className="filter-input" value={filter.end} onChange={e => setFilter({ ...filter, end: e.target.value })} />
                                </div>
                            </div>
                            <div className="table-wrapper">
                                <table className="payslip-table">
                                    <thead>
                                    <tr>
                                        {isAdmin && <th>{t('payslips.user')}</th>}
                                        <th>{t('payslips.period')}</th><th>{t('payslips.gross')}</th><th>{t('payslips.net')}</th>
                                        <th>{t('payslips.payoutDate')}</th><th className="actions-col">{t('userManagement.table.actions')}</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {approvedSlips.map(ps => (
                                        <tr key={ps.id}>
                                            {isAdmin && <td data-label={t('payslips.user')}>{ps.firstName} {ps.lastName}</td>}
                                            <td data-label={t('payslips.period')}>{ps.periodStart} – {ps.periodEnd}</td>
                                            <td data-label={t('payslips.gross')}>{ps.grossSalary?.toFixed(2)} {ps.currency || 'CHF'}</td><td data-label={t('payslips.net')}>{ps.netSalary?.toFixed(2)} {ps.currency || 'CHF'}</td>
                                            <td data-label={t('payslips.payoutDate')}>{ps.payoutDate}</td>
                                            <td className="actions-col">
                                                <button type="button" className="button-primary" onClick={() => printPdf(ps.id)}>{t('payslips.print')}</button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}
                </section>

                {isAdmin && <ScheduleAllModal visible={scheduleVisible} onConfirm={confirmScheduleAll} onClose={() => setScheduleVisible(false)} />}
            </div>
        </>
    );
};

export default PayslipsPage;