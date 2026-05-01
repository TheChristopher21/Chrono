import { useContext, useEffect, useMemo, useState } from 'react';
import Navbar from '../components/Navbar';
import api from '../utils/api';
import '../styles/AdminPayslipsPageScoped.css';
import { useTranslation, LanguageContext } from '../context/LanguageContext';
import ScheduleAllModal from '../components/ScheduleAllModal';

const PROCESS_STEPS = ['Vorbereiten', 'Pruefen', 'Freigeben', 'Auszahlen', 'Archiv'];

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

const getStatusMeta = (status) => {
  const map = {
    open: { label: 'Offen', tone: 'open' },
    review: { label: 'In Pruefung', tone: 'review' },
    approved: { label: 'Freigegeben', tone: 'approved' },
    paid: { label: 'Ausbezahlt', tone: 'paid' },
    archive: { label: 'Archiviert', tone: 'archive' }
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
    { id: 'all', label: 'Alle', count: counts.all },
    { id: 'open', label: 'Offen', count: counts.open },
    { id: 'review', label: 'In Pruefung', count: counts.review },
    { id: 'approved', label: 'Freigegeben', count: counts.approved },
    { id: 'paid', label: 'Ausbezahlt', count: counts.paid },
    { id: 'archive', label: 'Archiv', count: counts.archive }
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
    if (!window.confirm(`${selectedSlips.length} Abrechnung(en) wirklich loeschen?`)) return;

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
          <p className="payroll-eyebrow">Payroll-Center</p>
          <h1>{t('adminPayslips.centerTitle', 'Lohnabrechnung')}</h1>
          <p>{t('adminPayslips.centerSubtitle', 'Abrechnungslauf vorbereiten, pruefen, freigeben und archivieren.')}</p>
          <div className="payroll-run-summary">
            <span>{totals.uniqueUsers || allPayslips.length} Mitarbeitende</span>
            <span>Auszahlung {formatDate(totals.firstPayout)}</span>
            <span>{loading ? 'Aktualisiere...' : 'Bereit'}</span>
          </div>
        </div>
        <div className="payroll-hero-actions">
          <button type="button" className="secondary-btn" onClick={() => setSettingsOpen(true)}>
            Dokumentdesign
          </button>
          <button type="button" className="primary-btn" onClick={() => setCreateOpen(true)}>
            + Neuer Abrechnungslauf
          </button>
        </div>
      </header>

      <section className="payroll-process" aria-label="Payroll Prozess">
        {PROCESS_STEPS.map((step, index) => (
          <div className={`process-step ${index < 3 ? 'is-active' : ''}`} key={step}>
            <span>{index + 1}</span>
            <strong>{step}</strong>
          </div>
        ))}
      </section>

      <section className="payroll-kpi-grid" aria-label="Payroll Kennzahlen">
        <article className="payroll-kpi">
          <span>Offen</span>
          <strong>{counts.open}</strong>
          <small>Abrechnungen bereit</small>
        </article>
        <article className="payroll-kpi kpi-warning">
          <span>In Pruefung</span>
          <strong>{counts.review}</strong>
          <small>Warnungen oder negative Werte</small>
        </article>
        <article className="payroll-kpi kpi-success">
          <span>Freigegeben</span>
          <strong>{counts.approved}</strong>
          <small>PDF und Auszahlung bereit</small>
        </article>
        <article className="payroll-kpi">
          <span>Auszahlung gesamt</span>
          <strong>{formatCurrency(totals.payoutTotal)}</strong>
          <small>AG-Beitraege {formatCurrency(totals.employerTotal)}</small>
        </article>
      </section>

      <section className="payroll-workspace">
        <div className="payroll-tabs" role="tablist" aria-label="Status Filter">
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
            <span>Suche</span>
            <input
              value={filter.name}
              placeholder="Name"
              onChange={event => setFilter({ ...filter, name: event.target.value })}
            />
          </label>
          <label>
            <span>Start</span>
            <input
              type="date"
              value={filter.start}
              onChange={event => setFilter({ ...filter, start: event.target.value })}
            />
          </label>
          <label>
            <span>Ende</span>
            <input
              type="date"
              value={filter.end}
              onChange={event => setFilter({ ...filter, end: event.target.value })}
            />
          </label>
          <label>
            <span>Auszahlung</span>
            <input
              type="date"
              value={filter.payoutDate}
              onChange={event => setFilter({ ...filter, payoutDate: event.target.value })}
            />
          </label>
          <label>
            <span>Drucksprache</span>
            <select value={printLang} onChange={event => setPrintLang(event.target.value)}>
              <option value="de">DE</option>
              <option value="en">EN</option>
            </select>
          </label>
          <div className="toolbar-actions">
            <button type="button" onClick={approveAll}>Alle freigeben</button>
            <button type="button" onClick={scheduleAll}>Alle planen</button>
            <button type="button" onClick={exportCsv}>CSV Export</button>
            <button type="button" onClick={backup}>Backup</button>
          </div>
        </div>

        <div className="saved-views" aria-label="Gespeicherte Ansichten">
          <button type="button" onClick={() => setActiveTab('open')}>Offene Abrechnungen</button>
          <button type="button" onClick={() => setActiveTab('review')}>Fehlerhafte</button>
          <button type="button" onClick={() => setFilter({ ...filter, payoutDate: new Date().toISOString().slice(0, 10) })}>Auszahlung heute</button>
          <button type="button" onClick={() => setActiveTab('archive')}>Archiv</button>
        </div>

        {selectedIds.length > 0 && (
          <div className="bulk-action-bar">
            <strong>{selectedIds.length} ausgewaehlt</strong>
            <button type="button" onClick={bulkApprove}>Freigeben</button>
            <button type="button" onClick={bulkSetPayout}>Auszahlung planen</button>
            <button type="button" onClick={bulkPrintPdf}>PDF generieren</button>
            <button type="button" onClick={exportCsv}>CSV Export</button>
            <button type="button" className="danger-btn" onClick={bulkDelete}>Loeschen</button>
            <button type="button" className="ghost-btn" onClick={() => setSelectedIds([])}>Auswahl aufheben</button>
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
                    aria-label="Alle sichtbaren Abrechnungen auswaehlen"
                    checked={visiblePayslips.length > 0 && visiblePayslips.every(slip => selectedSet.has(slip.id))}
                    onChange={toggleVisibleSelection}
                  />
                </th>
                <th>Mitarbeiter</th>
                <th>Zeitraum</th>
                <th>Brutto</th>
                <th>Netto</th>
                <th>Status</th>
                <th>Auszahlung</th>
                <th className="actions-col">Aktion</th>
              </tr>
            </thead>
            <tbody>
              {visiblePayslips.map((slip) => {
                const status = getSlipStatus(slip);
                const statusMeta = getStatusMeta(status);
                const warning = hasPayrollWarning(slip);
                const currency = slip.currency || 'CHF';

                return (
                  <tr key={slip.id} className={warning ? 'has-warning' : ''}>
                    <td data-label="Auswahl" className="check-col">
                      <input
                        type="checkbox"
                        checked={selectedSet.has(slip.id)}
                        onChange={() => toggleSelected(slip.id)}
                        aria-label={`${getEmployeeName(slip)} auswaehlen`}
                      />
                    </td>
                    <td data-label="Mitarbeiter">
                      <button type="button" className="employee-link" onClick={() => setDetailSlip(slip)}>
                        <strong>{getEmployeeName(slip)}</strong>
                        <span>{formatBilledOvertime(slip)} abgerechnete Ueberstunden</span>
                      </button>
                    </td>
                    <td data-label="Zeitraum">{formatDate(slip.periodStart)} - {formatDate(slip.periodEnd)}</td>
                    <td data-label="Brutto" className={Number(slip.grossSalary) < 0 ? 'amount-negative' : ''}>
                      {formatCurrency(slip.grossSalary, currency)}
                    </td>
                    <td data-label="Netto" className={Number(slip.netSalary) < 0 ? 'amount-negative' : ''}>
                      {formatCurrency(slip.netSalary, currency)}
                      {Number(slip.netSalary) < 0 && <span className="inline-warning">Negativer Nettolohn</span>}
                    </td>
                    <td data-label="Status">
                      <span className={`status-chip status-${statusMeta.tone}`}>{statusMeta.label}</span>
                    </td>
                    <td data-label="Auszahlung">{formatDate(slip.payoutDate)}</td>
                    <td data-label="Aktion" className="actions-col">
                      {status === 'approved' ? (
                        <button type="button" className="compact-action" onClick={() => printPdf(slip.id)}>PDF</button>
                      ) : (
                        <button type="button" className="compact-action" onClick={() => setDetailSlip(slip)}>Pruefen</button>
                      )}
                      <button
                        type="button"
                        className="icon-action"
                        aria-label="Weitere Aktionen"
                        onClick={() => setMenuOpenId(menuOpenId === slip.id ? null : slip.id)}
                      >
                        ...
                      </button>
                      {menuOpenId === slip.id && (
                        <div className="row-menu">
                          <button type="button" onClick={() => { setDetailSlip(slip); setMenuOpenId(null); }}>Details anzeigen</button>
                          <button type="button" onClick={() => { printPdf(slip.id); setMenuOpenId(null); }}>PDF drucken</button>
                          <button type="button" onClick={() => { editPayoutDate(slip.id, slip.payoutDate); setMenuOpenId(null); }}>Auszahlung aendern</button>
                          {status !== 'approved' ? (
                            <button type="button" onClick={() => { approve(slip.id); setMenuOpenId(null); }}>Freigeben</button>
                          ) : (
                            <button type="button" onClick={() => { reopen(slip.id); setMenuOpenId(null); }}>Zurueckziehen</button>
                          )}
                          <button type="button" className="danger-text" onClick={() => { deletePayslip(slip.id); setMenuOpenId(null); }}>Loeschen</button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
              {visiblePayslips.length === 0 && (
                <tr>
                  <td colSpan="8" className="empty-state">
                    Keine Abrechnungen in dieser Ansicht.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <footer className="payroll-pagination">
          <span>{filteredPayslips.length} Abrechnungen</span>
          <label>
            <span>Pro Seite</span>
            <select value={pageSize} onChange={event => setPageSize(Number(event.target.value))}>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </label>
          <div>
            <button type="button" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)}>Zurueck</button>
            <span>Seite {currentPage} / {totalPages}</span>
            <button type="button" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)}>Weiter</button>
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
        />
      )}

      {settingsOpen && (
        <DocumentSettingsModal
          logoFile={logoFile}
          setLogoFile={setLogoFile}
          onSave={uploadLogo}
          onClose={() => setSettingsOpen(false)}
        />
      )}
    </div>
  );
};

const PayslipDrawer = ({ slip, formatCurrency, formatDate, onClose, onApprove, onPrint, onEditPayout, onReopen }) => {
  const status = getSlipStatus(slip);
  const statusMeta = getStatusMeta(status);
  const currency = slip.currency || 'CHF';
  const contributions = Array.isArray(slip.employerContribList) ? slip.employerContribList : [];

  return (
    <div className="drawer-layer" role="presentation">
      <button type="button" className="drawer-backdrop" aria-label="Details schliessen" onClick={onClose} />
      <aside className="payslip-drawer" aria-label={`Lohnabrechnung ${getEmployeeName(slip)}`}>
        <div className="drawer-head">
          <div>
            <span className={`status-chip status-${statusMeta.tone}`}>{statusMeta.label}</span>
            <h2>Lohnabrechnung: {getEmployeeName(slip)}</h2>
            <p>{formatDate(slip.periodStart)} - {formatDate(slip.periodEnd)}</p>
          </div>
          <button type="button" className="icon-action" onClick={onClose} aria-label="Schliessen">x</button>
        </div>

        <dl className="drawer-metrics">
          <div>
            <dt>Brutto</dt>
            <dd className={Number(slip.grossSalary) < 0 ? 'amount-negative' : ''}>{formatCurrency(slip.grossSalary, currency)}</dd>
          </div>
          <div>
            <dt>Netto</dt>
            <dd className={Number(slip.netSalary) < 0 ? 'amount-negative' : ''}>{formatCurrency(slip.netSalary, currency)}</dd>
          </div>
          <div>
            <dt>Abzuege</dt>
            <dd>{formatCurrency(slip.deductions, currency)}</dd>
          </div>
          <div>
            <dt>Auszahlung</dt>
            <dd>{formatDate(slip.payoutDate)}</dd>
          </div>
        </dl>

        <section className="drawer-section">
          <h3>Arbeitgeberbeitraege</h3>
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
            <p className="muted-text">Keine einzelnen Arbeitgeberbeitraege vorhanden.</p>
          )}
          <div className="drawer-total">
            <span>Summe AG-Beitraege</span>
            <strong>{formatCurrency(slip.employerContributions, currency)}</strong>
          </div>
        </section>

        <section className="drawer-section">
          <h3>Weitere Angaben</h3>
          <dl className="drawer-grid">
            <dt>Ueberstunden</dt>
            <dd>{slip.payoutOvertime ? `${Number(slip.overtimeHours || 0).toFixed(2)} Std.` : '-'}</dd>
            <dt>Zulagen</dt>
            <dd>{formatCurrency(slip.allowances, currency)}</dd>
            <dt>Bonus</dt>
            <dd>{formatCurrency(slip.bonuses, currency)}</dd>
            <dt>Version</dt>
            <dd>{slip.version ?? '-'}</dd>
          </dl>
        </section>

        <div className="drawer-actions">
          <button type="button" onClick={onPrint}>PDF oeffnen</button>
          {status === 'approved' ? (
            <button type="button" className="warning-btn" onClick={onReopen}>Zurueckziehen</button>
          ) : (
            <button type="button" className="primary-btn" onClick={onApprove}>Freigeben</button>
          )}
          <button type="button" onClick={onEditPayout}>Korrigieren</button>
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
  formatCurrency
}) => (
  <div className="modal-layer" role="presentation">
    <button type="button" className="modal-backdrop" aria-label="Dialog schliessen" onClick={onClose} />
    <section className="payroll-modal" role="dialog" aria-modal="true" aria-labelledby="create-payslip-title">
      <div className="modal-head">
        <div>
          <p className="payroll-eyebrow">Neuer Lauf</p>
          <h2 id="create-payslip-title">Neuen Abrechnungslauf erstellen</h2>
        </div>
        <button type="button" className="icon-action" onClick={onClose} aria-label="Schliessen">x</button>
      </div>

      <div className="wizard-grid">
        <fieldset>
          <legend>1. Zeitraum</legend>
          <label>
            <span>Startdatum</span>
            <input type="date" value={form.start} onChange={event => setForm({ ...form, start: event.target.value })} />
          </label>
          <label>
            <span>Enddatum</span>
            <input type="date" value={form.end} onChange={event => setForm({ ...form, end: event.target.value })} />
          </label>
          <label>
            <span>Auszahlung</span>
            <input type="date" value={form.payoutDate} onChange={event => setForm({ ...form, payoutDate: event.target.value })} />
          </label>
        </fieldset>

        <fieldset>
          <legend>2. Mitarbeitende</legend>
          <label>
            <span>Mitarbeiter</span>
            <select value={form.userId} onChange={event => setForm({ ...form, userId: event.target.value })}>
              <option value="">Benutzer waehlen</option>
              {users.map(user => (
                <option key={user.id} value={user.id}>{user.firstName || user.username} {user.lastName || ''}</option>
              ))}
            </select>
          </label>
          <div className="segmented-control" aria-label="Mitarbeitenden-Auswahl">
            <button type="button" className="is-active">Einzeln</button>
            <button type="button" disabled>Alle aktiven</button>
            <button type="button" disabled>Abteilung</button>
          </div>
        </fieldset>

        <fieldset>
          <legend>3. Optionen</legend>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.payoutOvertime}
              onChange={event => setForm({ ...form, payoutOvertime: event.target.checked })}
            />
            <span>Ueberstunden auszahlen</span>
          </label>
          {form.payoutOvertime && (
            <label>
              <span>Ueberstunden (Std.)</span>
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
            <span>Abzuege automatisch berechnen</span>
          </label>
          <label className="checkbox-label">
            <input type="checkbox" checked readOnly />
            <span>Vorschau vor Erstellung anzeigen</span>
          </label>
        </fieldset>
      </div>

      {wizardPreview && (
        <div className="run-preview">
          <h3>Vorschau Abrechnung</h3>
          <dl>
            <div>
              <dt>Mitarbeitende</dt>
              <dd>{form.userId ? 1 : 0}</dd>
            </div>
            <div>
              <dt>Fehler</dt>
              <dd>{Number(previewNet) < 0 ? 1 : 0}</dd>
            </div>
            <div>
              <dt>Gesamtes Brutto</dt>
              <dd>{formatCurrency(previewGross)}</dd>
            </div>
            <div>
              <dt>Gesamtes Netto</dt>
              <dd>{formatCurrency(previewNet)}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="modal-actions">
        <button type="button" onClick={() => setWizardPreview(true)}>Vorschau berechnen</button>
        <button type="button" className="primary-btn" onClick={onCreate} disabled={!form.userId || !form.start || !form.end}>
          Abrechnungslauf erstellen
        </button>
      </div>
    </section>
  </div>
);

const DocumentSettingsModal = ({ logoFile, setLogoFile, onSave, onClose }) => (
  <div className="modal-layer" role="presentation">
    <button type="button" className="modal-backdrop" aria-label="Dialog schliessen" onClick={onClose} />
    <section className="payroll-modal compact-modal" role="dialog" aria-modal="true" aria-labelledby="document-settings-title">
      <div className="modal-head">
        <div>
          <p className="payroll-eyebrow">Dokumentdesign</p>
          <h2 id="document-settings-title">PDF-Layout</h2>
        </div>
        <button type="button" className="icon-action" onClick={onClose} aria-label="Schliessen">x</button>
      </div>
      <div className="settings-grid">
        <label>
          <span>Firmenlogo</span>
          <input type="file" accept="image/*" onChange={event => setLogoFile(event.target.files?.[0] ?? null)} />
        </label>
        <label>
          <span>Sprache</span>
          <select defaultValue="de">
            <option value="de">Deutsch</option>
            <option value="en">English</option>
          </select>
        </label>
        <label>
          <span>Standard-Auszahlungsdatum</span>
          <input type="number" min="1" max="31" defaultValue="25" />
        </label>
        <label>
          <span>Nummernkreis</span>
          <input defaultValue="PAY-2026" />
        </label>
      </div>
      <div className="modal-actions">
        <button type="button" onClick={onClose}>Abbrechen</button>
        <button type="button" className="primary-btn" onClick={onSave} disabled={!logoFile}>Logo speichern</button>
      </div>
    </section>
  </div>
);

export default AdminPayslipsPage;
