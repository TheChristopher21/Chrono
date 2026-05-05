import { useContext, useEffect, useMemo, useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/AdminPayslipsPageScoped.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

const emptyForm = {
  userId: '',
  start: '',
  end: '',
  payoutDate: '',
  payoutOvertime: false,
  overtimeHours: ''
};

const hasPayrollWarning = (slip) => Number(slip?.grossSalary) < 0 || Number(slip?.netSalary) < 0;

const toComparableDate = (value) => {
  if (!value) return '';
  return String(value).slice(0, 10);
};

const getEmployeeName = (slip) => {
  const name = `${slip?.firstName ?? ''} ${slip?.lastName ?? ''}`.trim();
  return name || `User ${slip?.userId ?? '-'}`;
};

const getSlipStatus = (slip) => {
  if (slip?.lifecycleStatus) return slip.lifecycleStatus;
  if (slip?.approved) return 'approved';
  return hasPayrollWarning(slip) ? 'review' : 'open';
};

const getStatusMeta = (status, t = (key, fallback) => fallback ?? key) => {
  const map = {
    open: { label: t('adminPayslips.status.open', 'Offen'), tone: 'open' },
    review: { label: t('adminPayslips.status.review', 'In Pruefung'), tone: 'review' },
    approved: { label: t('adminPayslips.status.approved', 'Freigegeben'), tone: 'approved' },
    paid: { label: t('adminPayslips.status.paid', 'Ausbezahlt'), tone: 'paid' },
    archive: { label: t('adminPayslips.status.archive', 'Archiviert'), tone: 'archive' }
  };
  return map[status] ?? map.open;
};

const AdminPayslipsPage = () => {
  const [payslips, setPayslips] = useState([]);
  const [approvedSlips, setApprovedSlips] = useState([]);
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [filter, setFilter] = useState({ name: '', start: '', end: '', payoutDate: '' });
  const [logoFile, setLogoFile] = useState(null);
  const [selectedIds, setSelectedIds] = useState([]);
  const [activeTab, setActiveTab] = useState('all');
  const [detailSlip, setDetailSlip] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [wizardPreview, setWizardPreview] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [menuOpenId, setMenuOpenId] = useState(null);
  const [pageSize, setPageSize] = useState(25);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const { t } = useTranslation();
  const { language } = useContext(LanguageContext);
  const [printLang, setPrintLang] = useState('de');
  const [scheduleVisible, setScheduleVisible] = useState(false);

  const locale = language === 'en' ? 'en-US' : 'de-CH';
  const processSteps = [
    t('adminPayslips.process.prepare', 'Vorbereiten'),
    t('adminPayslips.process.review', 'Pruefen'),
    t('adminPayslips.process.approve', 'Freigeben'),
    t('adminPayslips.process.pay', 'Auszahlen'),
    t('adminPayslips.process.archive', 'Archiv'),
  ];

  const fetchPending = () => api.get('/api/payslips/admin/pending').then(res => {
    setPayslips(Array.isArray(res.data) ? res.data : []);
  });

  const fetchApproved = () => api.get('/api/payslips/admin/approved').then(res => {
    setApprovedSlips(Array.isArray(res.data) ? res.data : []);
  });

  const refreshPayslips = () => {
    setLoading(true);
    Promise.all([fetchPending(), fetchApproved()])
      .finally(() => setLoading(false));
  };

  const approve = (id, comment) => {
    return api.post(`/api/payslips/approve/${id}`, null, { params: { comment } }).then(() => {
      refreshPayslips();
    });
  };

  const deletePayslip = (id) => {
    if (window.confirm(t('payslips.deleteConfirm', 'Abrechnung wirklich loeschen?'))) {
      api.delete(`/api/payslips/${id}`).then(() => refreshPayslips());
    }
  };

  const editPayoutDate = (id, current) => {
    const val = prompt(t('payslips.enterPayoutDate', 'Auszahlungsdatum eingeben'), current || '');
    if (val) {
      api.post(`/api/payslips/set-payout/${id}`, null, { params: { payoutDate: val } })
        .then(() => refreshPayslips());
    }
  };

  const approveAll = () => {
    const comment = prompt(t('payslips.approveAll', 'Alle freigeben'));
    if (comment !== null) {
      api.post('/api/payslips/approve-all', null, { params: { comment } }).then(() => {
        setSelectedIds([]);
        refreshPayslips();
      });
    }
  };

  const scheduleAll = () => {
    setScheduleVisible(true);
  };

  const confirmScheduleAll = (day) => {
    api.post('/api/payslips/schedule-all', null, { params: { day } }).then(() => refreshPayslips());
    setScheduleVisible(false);
  };

  const exportCsv = () => {
    api.get('/api/payslips/admin/export', { responseType: 'blob', params: { lang: printLang } }).then(res => {
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'payslips.csv');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    });
  };

  const printPdf = (id) => {
    api.get(`/api/payslips/admin/pdf/${id}`, { responseType: 'blob', params: { lang: printLang } })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
        const win = window.open(url);
        setTimeout(() => {
          win?.print();
        }, 500);
      })
      .catch(() => alert(t('payslips.printError', 'PDF konnte nicht geoeffnet werden.')));
  };

  const reopen = (id) => {
    api.post(`/api/payslips/reopen/${id}`).then(() => refreshPayslips());
  };

  const createPayslip = () => {
    if (!form.userId || !form.start || !form.end) return;
    api.post('/api/payslips/generate', null, {
      params: {
        userId: form.userId,
        start: form.start,
        end: form.end,
        payoutDate: form.payoutDate,
        payoutOvertime: form.payoutOvertime,
        overtimeHours: form.payoutOvertime ? form.overtimeHours : null
      }
    }).then(() => {
      setForm(emptyForm);
      setWizardPreview(false);
      setCreateOpen(false);
      refreshPayslips();
    });
  };

  useEffect(() => {
    refreshPayslips();
    api.get('/api/admin/users').then(res => setUsers(Array.isArray(res.data) ? res.data : []));
  }, []);

  const backup = () => {
    api.get('/api/payslips/admin/backup');
  };

  const formatBilledOvertime = (slip) => {
    if (!slip?.payoutOvertime || !slip?.overtimeHours || slip.overtimeHours <= 0) {
      return '-';
    }
    return `${Number(slip.overtimeHours).toFixed(2)} ${t('payslips.hoursUnit', 'Std.')}`;
  };

  const uploadLogo = () => {
    if (!logoFile) return;
    const formData = new FormData();
    formData.append('file', logoFile);
    api.put('/api/admin/company/logo', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(() => {
      alert(t('payslips.logoSaved', 'Logo gespeichert'));
      setLogoFile(null);
      setSettingsOpen(false);
    }).catch(() => {
      alert(t('payslips.logoSaveError', 'Fehler beim Speichern'));
    });
  };

  const formatCurrency = (value, currency = 'CHF') => {
    const numericValue = Number(value);
    if (!Number.isFinite(numericValue)) return '-';

    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(numericValue);
    } catch {
      return `${numericValue.toFixed(2)} ${currency}`;
    }
  };

  const formatDate = (value) => {
    if (!value) return '-';
    const isoDate = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/);
    const date = isoDate
      ? new Date(Number(isoDate[1]), Number(isoDate[2]) - 1, Number(isoDate[3]))
      : new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return new Intl.DateTimeFormat(locale, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    }).format(date);
  };

  const allPayslips = useMemo(() => {
    const pending = payslips.map(slip => ({
      ...slip,
      lifecycleStatus: hasPayrollWarning(slip) ? 'review' : 'open',
      approved: false
    }));
    const approved = approvedSlips.map(slip => ({
      ...slip,
      lifecycleStatus: 'approved',
      approved: true
    }));

    return [...pending, ...approved].sort((a, b) => {
      const aDate = toComparableDate(a.payoutDate || a.periodEnd || a.periodStart);
      const bDate = toComparableDate(b.payoutDate || b.periodEnd || b.periodStart);
      return bDate.localeCompare(aDate);
    });
  }, [approvedSlips, payslips]);

  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds]);

  const filteredPayslips = useMemo(() => {
    const query = filter.name.trim().toLowerCase();

    return allPayslips.filter((slip) => {
      const status = getSlipStatus(slip);
      const name = getEmployeeName(slip).toLowerCase();
      const periodStart = toComparableDate(slip.periodStart);
      const periodEnd = toComparableDate(slip.periodEnd);
      const payoutDate = toComparableDate(slip.payoutDate);
      const matchesTab = activeTab === 'all' || status === activeTab;
      const matchesName = !query || name.includes(query);
      const matchesStart = !filter.start || periodStart >= filter.start;
      const matchesEnd = !filter.end || periodEnd <= filter.end;
      const matchesPayout = !filter.payoutDate || payoutDate === filter.payoutDate;

      return matchesTab && matchesName && matchesStart && matchesEnd && matchesPayout;
    });
  }, [activeTab, allPayslips, filter]);

  const totalPages = Math.max(1, Math.ceil(filteredPayslips.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const visiblePayslips = filteredPayslips.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  useEffect(() => {
    setPage(1);
  }, [activeTab, filter.name, filter.start, filter.end, filter.payoutDate, pageSize]);

  const counts = useMemo(() => ({
    all: allPayslips.length,
    open: allPayslips.filter(slip => getSlipStatus(slip) === 'open').length,
    review: allPayslips.filter(slip => getSlipStatus(slip) === 'review').length,
    approved: allPayslips.filter(slip => getSlipStatus(slip) === 'approved').length,
    paid: 0,
    archive: 0
  }), [allPayslips]);

  const totals = useMemo(() => {
    const selectedPeriodSlips = allPayslips.length ? allPayslips : [];
    const payoutTotal = selectedPeriodSlips.reduce((sum, slip) => sum + (Number(slip.netSalary) || 0), 0);
    const employerTotal = selectedPeriodSlips.reduce((sum, slip) => sum + (Number(slip.employerContributions) || 0), 0);
    const firstPayout = selectedPeriodSlips.find(slip => slip.payoutDate)?.payoutDate;
    const uniqueUsers = new Set(selectedPeriodSlips.map(slip => slip.userId).filter(Boolean)).size;

    return { payoutTotal, employerTotal, firstPayout, uniqueUsers };
  }, [allPayslips]);

  const tabs = [
    { id: 'all', label: t('all', 'Alle'), count: counts.all },
    { id: 'open', label: t('adminPayslips.status.open', 'Offen'), count: counts.open },
    { id: 'review', label: t('adminPayslips.status.review', 'In Pruefung'), count: counts.review },
    { id: 'approved', label: t('adminPayslips.status.approved', 'Freigegeben'), count: counts.approved },
    { id: 'paid', label: t('adminPayslips.status.paid', 'Ausbezahlt'), count: counts.paid },
    { id: 'archive', label: t('adminPayslips.tabs.archive', 'Archiv'), count: counts.archive }
  ];

  const selectedSlips = useMemo(
    () => allPayslips.filter(slip => selectedSet.has(slip.id)),
    [allPayslips, selectedSet]
  );

  const selectedPending = selectedSlips.filter(slip => getSlipStatus(slip) !== 'approved');

  const toggleSelected = (id) => {
    setSelectedIds(current => (
      current.includes(id)
        ? current.filter(selectedId => selectedId !== id)
        : [...current, id]
    ));
  };

  const toggleVisibleSelection = () => {
    const visibleIds = visiblePayslips.map(slip => slip.id);
    const allVisibleSelected = visibleIds.every(id => selectedSet.has(id));

    if (allVisibleSelected) {
      setSelectedIds(current => current.filter(id => !visibleIds.includes(id)));
    } else {
      setSelectedIds(current => Array.from(new Set([...current, ...visibleIds])));
    }
  };

  const bulkApprove = () => {
    if (!selectedPending.length) return;
    const comment = prompt(t('payslips.approveBulkComment', 'Kommentar fuer Freigabe (optional)'));
    if (comment === null) return;

    Promise.all(
      selectedPending.map(slip => api.post(`/api/payslips/approve/${slip.id}`, null, { params: { comment } }))
    ).then(() => {
      setSelectedIds([]);
      refreshPayslips();
    });
  };

  const bulkSetPayout = () => {
    if (!selectedSlips.length) return;
    const payoutDate = prompt(t('payslips.enterPayoutDate', 'Auszahlungsdatum eingeben'), selectedSlips[0]?.payoutDate || '');
    if (!payoutDate) return;

    Promise.all(
      selectedSlips.map(slip => api.post(`/api/payslips/set-payout/${slip.id}`, null, { params: { payoutDate } }))
    ).then(() => {
      setSelectedIds([]);
      refreshPayslips();
    });
  };

  const bulkPrintPdf = () => {
    selectedSlips.forEach(slip => printPdf(slip.id));
  };

  const bulkDelete = () => {
    if (!selectedSlips.length) return;
    if (!window.confirm(t(
      'adminPayslips.bulkDeleteConfirm',
      '{{count}} Abrechnung(en) wirklich loeschen?',
      { count: selectedSlips.length }
    ))) return;

    Promise.all(selectedSlips.map(slip => api.delete(`/api/payslips/${slip.id}`))).then(() => {
      setSelectedIds([]);
      refreshPayslips();
    });
  };

  const wizardUser = users.find(user => String(user.id) === String(form.userId));
  const previewGross = wizardUser?.salary ?? wizardUser?.monthlySalary ?? 0;
  const previewNet = Number(previewGross) ? Number(previewGross) * 0.86 : 0;

  return (
    <div className="admin-payslips-page scoped-dashboard">
      <Navbar />

      <header className="payroll-hero">
        <div className="payroll-title-block">
          <p className="payroll-eyebrow">{t('adminPayslips.centerEyebrow', 'Payroll-Center')}</p>
          <h1>{t('adminPayslips.centerTitle', 'Lohnabrechnung')}</h1>
          <p>{t('adminPayslips.centerSubtitle', 'Abrechnungslauf vorbereiten, pruefen, freigeben und archivieren.')}</p>
          <div className="payroll-run-summary">
            <span>{t('adminPayslips.employeeCount', '{{count}} Mitarbeitende', { count: totals.uniqueUsers || allPayslips.length })}</span>
            <span>{t('adminPayslips.nextPayout', 'Auszahlung {{date}}', { date: formatDate(totals.firstPayout) })}</span>
            <span>{loading ? t('adminPayslips.refreshing', 'Aktualisiere...') : t('adminPayslips.ready', 'Bereit')}</span>
          </div>
        </div>
        <div className="payroll-hero-actions">
          <button type="button" className="secondary-btn" onClick={() => setSettingsOpen(true)}>
            {t('adminPayslips.documentDesign', 'Dokumentdesign')}
          </button>
          <button type="button" className="primary-btn" onClick={() => setCreateOpen(true)}>
            {t('adminPayslips.newPayrollRun', '+ Neuer Abrechnungslauf')}
          </button>
        </div>
      </header>

      <section className="payroll-process" aria-label={t('adminPayslips.process.ariaLabel', 'Payroll Prozess')}>
        {processSteps.map((step, index) => (
          <div className={`process-step ${index < 3 ? 'is-active' : ''}`} key={step}>
            <span>{index + 1}</span>
            <strong>{step}</strong>
          </div>
        ))}
      </section>

      <section className="payroll-kpi-grid" aria-label={t('adminPayslips.kpis.ariaLabel', 'Payroll Kennzahlen')}>
        <article className="payroll-kpi">
          <span>{t('adminPayslips.status.open', 'Offen')}</span>
          <strong>{counts.open}</strong>
          <small>{t('adminPayslips.kpis.openHint', 'Abrechnungen bereit')}</small>
        </article>
        <article className="payroll-kpi kpi-warning">
          <span>{t('adminPayslips.status.review', 'In Pruefung')}</span>
          <strong>{counts.review}</strong>
          <small>{t('adminPayslips.kpis.reviewHint', 'Warnungen oder negative Werte')}</small>
        </article>
        <article className="payroll-kpi kpi-success">
          <span>{t('adminPayslips.status.approved', 'Freigegeben')}</span>
          <strong>{counts.approved}</strong>
          <small>{t('adminPayslips.kpis.approvedHint', 'PDF und Auszahlung bereit')}</small>
        </article>
        <article className="payroll-kpi">
          <span>{t('adminPayslips.kpis.totalPayout', 'Auszahlung gesamt')}</span>
          <strong>{formatCurrency(totals.payoutTotal)}</strong>
          <small>{t('adminPayslips.kpis.employerContributions', 'AG-Beitraege {{amount}}', { amount: formatCurrency(totals.employerTotal) })}</small>
        </article>
      </section>

      <section className="payroll-workspace">
        <div className="payroll-tabs" role="tablist" aria-label={t('adminPayslips.statusFilter', 'Status Filter')}>
          {tabs.map(tab => (
            <button
              type="button"
              role="tab"
              className={activeTab === tab.id ? 'is-active' : ''}
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
              <span>{tab.count}</span>
            </button>
          ))}
        </div>

        <div className="payroll-toolbar">
          <label>
            <span>{t('search', 'Suche')}</span>
            <input
              value={filter.name}
              placeholder="Name"
              onChange={event => setFilter({ ...filter, name: event.target.value })}
            />
          </label>
          <label>
            <span>{t('start', 'Start')}</span>
            <input
              type="date"
              value={filter.start}
              onChange={event => setFilter({ ...filter, start: event.target.value })}
            />
          </label>
          <label>
            <span>{t('end', 'Ende')}</span>
            <input
              type="date"
              value={filter.end}
              onChange={event => setFilter({ ...filter, end: event.target.value })}
            />
          </label>
          <label>
            <span>{t('adminPayslips.payout', 'Auszahlung')}</span>
            <input
              type="date"
              value={filter.payoutDate}
              onChange={event => setFilter({ ...filter, payoutDate: event.target.value })}
            />
          </label>
          <label>
            <span>{t('adminPayslips.printLanguage', 'Drucksprache')}</span>
            <select value={printLang} onChange={event => setPrintLang(event.target.value)}>
              <option value="de">DE</option>
              <option value="en">EN</option>
            </select>
          </label>
          <div className="toolbar-actions">
            <button type="button" onClick={approveAll}>{t('adminPayslips.approveAllButton', 'Alle freigeben')}</button>
            <button type="button" onClick={scheduleAll}>{t('adminPayslips.scheduleAllButton', 'Alle planen')}</button>
            <button type="button" onClick={exportCsv}>CSV Export</button>
            <button type="button" onClick={backup}>Backup</button>
          </div>
        </div>

        <div className="saved-views" aria-label={t('adminPayslips.savedViews', 'Gespeicherte Ansichten')}>
          <button type="button" onClick={() => setActiveTab('open')}>{t('adminPayslips.savedViewOpen', 'Offene Abrechnungen')}</button>
          <button type="button" onClick={() => setActiveTab('review')}>{t('adminPayslips.savedViewReview', 'Fehlerhafte')}</button>
          <button type="button" onClick={() => setFilter({ ...filter, payoutDate: new Date().toISOString().slice(0, 10) })}>{t('adminPayslips.savedViewToday', 'Auszahlung heute')}</button>
          <button type="button" onClick={() => setActiveTab('archive')}>{t('adminPayslips.tabs.archive', 'Archiv')}</button>
        </div>

        {selectedIds.length > 0 && (
          <div className="bulk-action-bar">
            <strong>{t('adminPayslips.selectedCount', '{{count}} ausgewaehlt', { count: selectedIds.length })}</strong>
            <button type="button" onClick={bulkApprove}>{t('adminPayslips.approveButton', 'Freigeben')}</button>
            <button type="button" onClick={bulkSetPayout}>{t('adminPayslips.schedulePayout', 'Auszahlung planen')}</button>
            <button type="button" onClick={bulkPrintPdf}>{t('adminPayslips.generatePdf', 'PDF generieren')}</button>
            <button type="button" onClick={exportCsv}>CSV Export</button>
            <button type="button" className="danger-btn" onClick={bulkDelete}>{t('delete', 'Loeschen')}</button>
            <button type="button" className="ghost-btn" onClick={() => setSelectedIds([])}>{t('adminPayslips.clearSelection', 'Auswahl aufheben')}</button>
          </div>
        )}

        <ScheduleAllModal
          visible={scheduleVisible}
          onConfirm={confirmScheduleAll}
          onClose={() => setScheduleVisible(false)}
        />

        <div className="payroll-table-wrap">
          <table className="payslip-table">
            <thead>
              <tr>
                <th className="check-col">
                  <input
                    type="checkbox"
                    aria-label={t('adminPayslips.selectVisible', 'Alle sichtbaren Abrechnungen auswaehlen')}
                    checked={visiblePayslips.length > 0 && visiblePayslips.every(slip => selectedSet.has(slip.id))}
                    onChange={toggleVisibleSelection}
                  />
                </th>
                <th>{t('employee', 'Mitarbeiter')}</th>
                <th>{t('period', 'Zeitraum')}</th>
                <th>{t('adminPayslips.gross', 'Brutto')}</th>
                <th>{t('adminPayslips.net', 'Netto')}</th>
                <th>{t('common.status', 'Status')}</th>
                <th>{t('adminPayslips.payout', 'Auszahlung')}</th>
                <th className="actions-col">{t('actions', 'Aktion')}</th>
              </tr>
            </thead>
            <tbody>
              {visiblePayslips.map((slip) => {
                const status = getSlipStatus(slip);
                const statusMeta = getStatusMeta(status, t);
                const warning = hasPayrollWarning(slip);
                const currency = slip.currency || 'CHF';

                return (
                  <tr key={slip.id} className={warning ? 'has-warning' : ''}>
                    <td data-label={t('selection', 'Auswahl')} className="check-col">
                      <input
                        type="checkbox"
                        checked={selectedSet.has(slip.id)}
                        onChange={() => toggleSelected(slip.id)}
                        aria-label={t('adminPayslips.selectEmployeeSlip', '{{name}} auswaehlen', { name: getEmployeeName(slip) })}
                      />
                    </td>
                    <td data-label={t('employee', 'Mitarbeiter')}>
                      <button type="button" className="employee-link" onClick={() => setDetailSlip(slip)}>
                        <strong>{getEmployeeName(slip)}</strong>
                        <span>{t('adminPayslips.billedOvertime', '{{hours}} abgerechnete Ueberstunden', { hours: formatBilledOvertime(slip) })}</span>
                      </button>
                    </td>
                    <td data-label={t('period', 'Zeitraum')}>{formatDate(slip.periodStart)} - {formatDate(slip.periodEnd)}</td>
                    <td data-label={t('adminPayslips.gross', 'Brutto')} className={Number(slip.grossSalary) < 0 ? 'amount-negative' : ''}>
                      {formatCurrency(slip.grossSalary, currency)}
                    </td>
                    <td data-label={t('adminPayslips.net', 'Netto')} className={Number(slip.netSalary) < 0 ? 'amount-negative' : ''}>
                      {formatCurrency(slip.netSalary, currency)}
                      {Number(slip.netSalary) < 0 && <span className="inline-warning">{t('adminPayslips.negativeNetSalary', 'Negativer Nettolohn')}</span>}
                    </td>
                    <td data-label={t('common.status', 'Status')}>
                      <span className={`status-chip status-${statusMeta.tone}`}>{statusMeta.label}</span>
                    </td>
                    <td data-label={t('adminPayslips.payout', 'Auszahlung')}>{formatDate(slip.payoutDate)}</td>
                    <td data-label={t('actions', 'Aktion')} className="actions-col">
                      {status === 'approved' ? (
                        <button type="button" className="compact-action" onClick={() => printPdf(slip.id)}>PDF</button>
                      ) : (
                        <button type="button" className="compact-action" onClick={() => setDetailSlip(slip)}>{t('adminPayslips.reviewButton', 'Pruefen')}</button>
                      )}
                      <button
                        type="button"
                        className="icon-action"
                        aria-label={t('adminPayslips.moreActions', 'Weitere Aktionen')}
                        onClick={() => setMenuOpenId(menuOpenId === slip.id ? null : slip.id)}
                      >
                        ...
                      </button>
                      {menuOpenId === slip.id && (
                        <div className="row-menu">
                          <button type="button" onClick={() => { setDetailSlip(slip); setMenuOpenId(null); }}>{t('adminPayslips.showDetails', 'Details anzeigen')}</button>
                          <button type="button" onClick={() => { printPdf(slip.id); setMenuOpenId(null); }}>{t('adminPayslips.printPdf', 'PDF drucken')}</button>
                          <button type="button" onClick={() => { editPayoutDate(slip.id, slip.payoutDate); setMenuOpenId(null); }}>{t('adminPayslips.changePayout', 'Auszahlung aendern')}</button>
                          {status !== 'approved' ? (
                            <button type="button" onClick={() => { approve(slip.id); setMenuOpenId(null); }}>{t('adminPayslips.approveButton', 'Freigeben')}</button>
                          ) : (
                            <button type="button" onClick={() => { reopen(slip.id); setMenuOpenId(null); }}>{t('adminPayslips.reopen', 'Zurueckziehen')}</button>
                          )}
                          <button type="button" className="danger-text" onClick={() => { deletePayslip(slip.id); setMenuOpenId(null); }}>{t('delete', 'Loeschen')}</button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
              {visiblePayslips.length === 0 && (
                <tr>
                  <td colSpan="8" className="empty-state">
                    {t('adminPayslips.emptyView', 'Keine Abrechnungen in dieser Ansicht.')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="payroll-pagination">
          <span>{t('adminPayslips.payslipCount', '{{count}} Abrechnungen', { count: filteredPayslips.length })}</span>
          <label>
            <span>{t('adminPayslips.perPage', 'Pro Seite')}</span>
            <select value={pageSize} onChange={event => setPageSize(Number(event.target.value))}>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </label>
          <div>
            <button type="button" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>{t('back', 'Zurueck')}</button>
            <span>{t('adminPayslips.pageIndicator', 'Seite {{current}} / {{total}}', { current: currentPage, total: totalPages })}</span>
            <button type="button" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>{t('next', 'Weiter')}</button>
          </div>
        </footer>
      </section>

      {detailSlip && (
        <PayslipDrawer
          slip={detailSlip}
          formatCurrency={formatCurrency}
          formatDate={formatDate}
          onClose={() => setDetailSlip(null)}
          onApprove={() => approve(detailSlip.id).then(() => setDetailSlip(null))}
          onPrint={() => printPdf(detailSlip.id)}
          onEditPayout={() => editPayoutDate(detailSlip.id, detailSlip.payoutDate)}
          onReopen={() => reopen(detailSlip.id)}
          t={t}
        />
      )}

      {createOpen && (
        <CreatePayslipModal
          form={form}
          setForm={setForm}
          users={users}
          wizardPreview={wizardPreview}
          setWizardPreview={setWizardPreview}
          onCreate={createPayslip}
          onClose={() => {
            setCreateOpen(false);
            setWizardPreview(false);
          }}
          previewGross={previewGross}
          previewNet={previewNet}
          formatCurrency={formatCurrency}
          t={t}
        />
      )}

      {settingsOpen && (
        <DocumentSettingsModal
          logoFile={logoFile}
          setLogoFile={setLogoFile}
          onSave={uploadLogo}
          onClose={() => setSettingsOpen(false)}
          t={t}
        />
      )}
    </div>
  );
};

const PayslipDrawer = ({ slip, formatCurrency, formatDate, onClose, onApprove, onPrint, onEditPayout, onReopen, t }) => {
  const status = getSlipStatus(slip);
  const statusMeta = getStatusMeta(status, t);
  const currency = slip.currency || 'CHF';
  const contributions = Array.isArray(slip.employerContribList) ? slip.employerContribList : [];

  return (
    <div className="drawer-layer" role="presentation">
      <button type="button" className="drawer-backdrop" aria-label={t('adminPayslips.closeDetails', 'Details schliessen')} onClick={onClose} />
      <aside className="payslip-drawer" aria-label={t('adminPayslips.drawerAria', 'Lohnabrechnung {{name}}', { name: getEmployeeName(slip) })}>
        <div className="drawer-head">
          <div>
            <span className={`status-chip status-${statusMeta.tone}`}>{statusMeta.label}</span>
            <h2>{t('adminPayslips.drawerTitle', 'Lohnabrechnung: {{name}}', { name: getEmployeeName(slip) })}</h2>
            <p>{formatDate(slip.periodStart)} - {formatDate(slip.periodEnd)}</p>
          </div>
          <button type="button" className="icon-action" onClick={onClose} aria-label={t('close', 'Schliessen')}>x</button>
        </div>

        <dl className="drawer-metrics">
          <div>
            <dt>{t('adminPayslips.gross', 'Brutto')}</dt>
            <dd className={Number(slip.grossSalary) < 0 ? 'amount-negative' : ''}>{formatCurrency(slip.grossSalary, currency)}</dd>
          </div>
          <div>
            <dt>{t('adminPayslips.net', 'Netto')}</dt>
            <dd className={Number(slip.netSalary) < 0 ? 'amount-negative' : ''}>{formatCurrency(slip.netSalary, currency)}</dd>
          </div>
          <div>
            <dt>{t('adminPayslips.deductions', 'Abzuege')}</dt>
            <dd>{formatCurrency(slip.deductions, currency)}</dd>
          </div>
          <div>
            <dt>{t('adminPayslips.payout', 'Auszahlung')}</dt>
            <dd>{formatDate(slip.payoutDate)}</dd>
          </div>
        </dl>

        <section className="drawer-section">
          <h3>{t('adminPayslips.employerContributionsTitle', 'Arbeitgeberbeitraege')}</h3>
          {contributions.length > 0 ? (
            <ul className="contribution-list">
              {contributions.map((entry, index) => (
                <li key={`${entry.type}-${index}`}>
                  <span>{entry.type}</span>
                  <strong>{formatCurrency(entry.amount, currency)}</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted-text">{t('adminPayslips.noEmployerContributions', 'Keine einzelnen Arbeitgeberbeitraege vorhanden.')}</p>
          )}
          <div className="drawer-total">
            <span>{t('adminPayslips.employerContributionsTotal', 'Summe AG-Beitraege')}</span>
            <strong>{formatCurrency(slip.employerContributions, currency)}</strong>
          </div>
        </section>

        <section className="drawer-section">
          <h3>{t('adminPayslips.moreDetails', 'Weitere Angaben')}</h3>
          <dl className="drawer-grid">
            <dt>{t('overtime', 'Ueberstunden')}</dt>
            <dd>{slip.payoutOvertime ? `${Number(slip.overtimeHours || 0).toFixed(2)} Std.` : '-'}</dd>
            <dt>{t('adminPayslips.allowances', 'Zulagen')}</dt>
            <dd>{formatCurrency(slip.allowances, currency)}</dd>
            <dt>Bonus</dt>
            <dd>{formatCurrency(slip.bonuses, currency)}</dd>
            <dt>Version</dt>
            <dd>{slip.version ?? '-'}</dd>
          </dl>
        </section>

        <div className="drawer-actions">
          <button type="button" onClick={onPrint}>{t('adminPayslips.openPdf', 'PDF oeffnen')}</button>
          {status === 'approved' ? (
            <button type="button" className="warning-btn" onClick={onReopen}>{t('adminPayslips.reopen', 'Zurueckziehen')}</button>
          ) : (
            <button type="button" className="primary-btn" onClick={onApprove}>{t('adminPayslips.approveButton', 'Freigeben')}</button>
          )}
          <button type="button" onClick={onEditPayout}>{t('adminPayslips.correct', 'Korrigieren')}</button>
        </div>
      </aside>
    </div>
  );
};

const CreatePayslipModal = ({
  form,
  setForm,
  users,
  wizardPreview,
  setWizardPreview,
  onCreate,
  onClose,
  previewGross,
  previewNet,
  formatCurrency,
  t
}) => (
  <div className="modal-layer" role="presentation">
    <button type="button" className="modal-backdrop" aria-label={t('adminPayslips.closeDialog', 'Dialog schliessen')} onClick={onClose} />
    <section className="payroll-modal" role="dialog" aria-modal="true" aria-labelledby="create-payslip-title">
      <div className="modal-head">
        <div>
          <p className="payroll-eyebrow">{t('adminPayslips.newRunEyebrow', 'Neuer Lauf')}</p>
          <h2 id="create-payslip-title">{t('adminPayslips.createRunTitle', 'Neuen Abrechnungslauf erstellen')}</h2>
        </div>
        <button type="button" className="icon-action" onClick={onClose} aria-label={t('close', 'Schliessen')}>x</button>
      </div>

      <div className="wizard-grid">
        <fieldset>
          <legend>{t('adminPayslips.stepPeriod', '1. Zeitraum')}</legend>
          <label>
            <span>{t('adminPayslips.startDate', 'Startdatum')}</span>
            <input type="date" value={form.start} onChange={event => setForm({ ...form, start: event.target.value })} />
          </label>
          <label>
            <span>{t('adminPayslips.endDate', 'Enddatum')}</span>
            <input type="date" value={form.end} onChange={event => setForm({ ...form, end: event.target.value })} />
          </label>
          <label>
            <span>{t('adminPayslips.payout', 'Auszahlung')}</span>
            <input type="date" value={form.payoutDate} onChange={event => setForm({ ...form, payoutDate: event.target.value })} />
          </label>
        </fieldset>

        <fieldset>
          <legend>{t('adminPayslips.stepEmployees', '2. Mitarbeitende')}</legend>
          <label>
            <span>{t('employee', 'Mitarbeiter')}</span>
            <select value={form.userId} onChange={event => setForm({ ...form, userId: event.target.value })}>
              <option value="">{t('adminPayslips.chooseUser', 'Benutzer waehlen')}</option>
              {users.map(user => (
                <option key={user.id} value={user.id}>{user.firstName || user.username} {user.lastName || ''}</option>
              ))}
            </select>
          </label>
          <div className="segmented-control" aria-label={t('adminPayslips.employeeSelection', 'Mitarbeitenden-Auswahl')}>
            <button type="button" className="is-active">{t('adminPayslips.singleEmployee', 'Einzeln')}</button>
            <button type="button" disabled>{t('adminPayslips.allActiveEmployees', 'Alle aktiven')}</button>
            <button type="button" disabled>{t('adminPayslips.department', 'Abteilung')}</button>
          </div>
        </fieldset>

        <fieldset>
          <legend>{t('adminPayslips.stepOptions', '3. Optionen')}</legend>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.payoutOvertime}
              onChange={event => setForm({ ...form, payoutOvertime: event.target.checked })}
            />
            <span>{t('adminPayslips.payOutOvertime', 'Ueberstunden auszahlen')}</span>
          </label>
          {form.payoutOvertime && (
            <label>
              <span>{t('adminPayslips.overtimeHours', 'Ueberstunden (Std.)')}</span>
              <input
                type="number"
                min="0"
                step="0.25"
                value={form.overtimeHours}
                onChange={event => setForm({ ...form, overtimeHours: event.target.value })}
              />
            </label>
          )}
          <label className="checkbox-label">
            <input type="checkbox" checked readOnly />
            <span>{t('adminPayslips.calculateDeductionsAutomatically', 'Abzuege automatisch berechnen')}</span>
          </label>
          <label className="checkbox-label">
            <input type="checkbox" checked readOnly />
            <span>{t('adminPayslips.showPreviewBeforeCreate', 'Vorschau vor Erstellung anzeigen')}</span>
          </label>
        </fieldset>
      </div>

      {wizardPreview && (
        <div className="run-preview">
          <h3>{t('adminPayslips.previewTitle', 'Vorschau Abrechnung')}</h3>
          <dl>
            <div>
              <dt>{t('adminPayslips.employees', 'Mitarbeitende')}</dt>
              <dd>{form.userId ? 1 : 0}</dd>
            </div>
            <div>
              <dt>{t('errorsLabel', 'Fehler')}</dt>
              <dd>{Number(previewNet) < 0 ? 1 : 0}</dd>
            </div>
            <div>
              <dt>{t('adminPayslips.totalGross', 'Gesamtes Brutto')}</dt>
              <dd>{formatCurrency(previewGross)}</dd>
            </div>
            <div>
              <dt>{t('adminPayslips.totalNet', 'Gesamtes Netto')}</dt>
              <dd>{formatCurrency(previewNet)}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="modal-actions">
        <button type="button" onClick={() => setWizardPreview(true)}>{t('adminPayslips.calculatePreview', 'Vorschau berechnen')}</button>
        <button type="button" className="primary-btn" onClick={onCreate} disabled={!form.userId || !form.start || !form.end}>
          {t('adminPayslips.createRunButton', 'Abrechnungslauf erstellen')}
        </button>
      </div>
    </section>
  </div>
);

const DocumentSettingsModal = ({ logoFile, setLogoFile, onSave, onClose, t }) => (
  <div className="modal-layer" role="presentation">
    <button type="button" className="modal-backdrop" aria-label={t('adminPayslips.closeDialog', 'Dialog schliessen')} onClick={onClose} />
    <section className="payroll-modal compact-modal" role="dialog" aria-modal="true" aria-labelledby="document-settings-title">
      <div className="modal-head">
        <div>
          <p className="payroll-eyebrow">{t('adminPayslips.documentDesign', 'Dokumentdesign')}</p>
          <h2 id="document-settings-title">PDF-Layout</h2>
        </div>
        <button type="button" className="icon-action" onClick={onClose} aria-label={t('close', 'Schliessen')}>x</button>
      </div>
      <div className="settings-grid">
        <label>
          <span>{t('adminPayslips.companyLogo', 'Firmenlogo')}</span>
          <input type="file" accept="image/*" onChange={event => setLogoFile(event.target.files?.[0] ?? null)} />
        </label>
        <label>
          <span>{t('language', 'Sprache')}</span>
          <select defaultValue="de">
            <option value="de">Deutsch</option>
            <option value="en">English</option>
          </select>
        </label>
        <label>
          <span>{t('adminPayslips.defaultPayoutDay', 'Standard-Auszahlungsdatum')}</span>
          <input type="number" min="1" max="31" defaultValue="25" />
        </label>
        <label>
          <span>{t('adminPayslips.numberRange', 'Nummernkreis')}</span>
          <input defaultValue="PAY-2026" />
        </label>
      </div>
      <div className="modal-actions">
        <button type="button" onClick={onClose}>{t('cancel', 'Abbrechen')}</button>
        <button type="button" className="primary-btn" onClick={onSave} disabled={!logoFile}>{t('adminPayslips.saveLogo', 'Logo speichern')}</button>
      </div>
    </section>
  </div>
);

export default AdminPayslipsPage;
