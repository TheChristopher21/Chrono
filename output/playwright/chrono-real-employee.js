/* Insert inside the preview engine scope after its helpers. No API writes.
   Entry point: realEmployee(id). Delegates vacation, sickness, print, event
   editing and request decisions to existing engine actions. */
const realEmployeeView = { id: null, month: null };
const realEmployeeToday = '2026-09-14';

function realEmployeeDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return '';
  const date = new Date(value + 'T12:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? value : '';
}

function realEmployeePeriod(start, end) {
  const first = realEmployeeDate(start), last = realEmployeeDate(end);
  return first && last && first <= last ? first === last ? fmt(first) : fmt(first) + ' – ' + fmt(last) : 'Datum nicht vollständig hinterlegt';
}

function realEmployeePerson(id) {
  return people.find(person => String(person.id) === String(id));
}

function realEmployeeEvents(id) {
  return events.filter(event => String(event.p) === String(id));
}

function realEmployeeEventKind(event) {
  return event.kind === 'Krank' ? 'Krankheit' : event.overtime ? 'Überstundenfrei' : event.kind || 'Abwesenheit';
}

function realEmployeeEventRow(event) {
  const ongoing = event.start <= realEmployeeToday && (event.end || event.start) >= realEmployeeToday;
  const kind = realEmployeeEventKind(event);
  const valid = realEmployeeDate(event.start) && realEmployeeDate(event.end || event.start) && event.start <= (event.end || event.start);
  return `<div class="re-absence-row"><span class="re-event-icon ${event.kind === 'Krank' ? 're-sick' : ''}">${icon(event.kind === 'Krank' ? 'heart-pulse' : 'calendar-days')}</span><div class="re-row-copy"><strong>${E(kind)}${event.half ? ' · halbtags' : ''}</strong><span>${E(realEmployeePeriod(event.start, event.end || event.start))}${ongoing && valid ? ' · aktuell' : ''}</span>${event.comment ? `<p>${E(event.comment)}</p>` : ''}</div>${readOnly() ? '' : btn('eventedit', icon('pencil'), 're-icon-button', `data-id="${E(event.id)}" aria-label="${E(kind)} bearbeiten"`)}</div>`;
}

function realEmployeeCalendar(person) {
  const month = realEmployeeView.month;
  const first = new Date(month.getFullYear(), month.getMonth(), 1, 12);
  const offset = (first.getDay() + 6) % 7;
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = Math.ceil((offset + days) / 7) * 7;
  const list = realEmployeeEvents(person.id);
  const monthStart = ymd(first);
  const monthEnd = ymd(new Date(month.getFullYear(), month.getMonth() + 1, 0, 12));
  const monthEvents = list.filter(event => realEmployeeDate(event.start) && realEmployeeDate(event.end || event.start) && event.start <= (event.end || event.start) && event.start <= monthEnd && (event.end || event.start) >= monthStart).sort((a, b) => a.start.localeCompare(b.start));
  const selected = realEmployeeToday.slice(0, 7) === monthStart.slice(0, 7) ? realEmployeeToday : monthStart;
  return `<div class="re-section-heading"><div><h3>Abwesenheitskalender</h3><p>${E(month.toLocaleDateString('de-CH', { month: 'long', year: 'numeric' }))}</p></div><div class="re-calendar-nav">${btn('real-employee-month', icon('chevron-left'), 're-icon-button', 'data-direction="-1" aria-label="Voriger Kalendermonat"')}${btn('real-employee-today', 'Heute', 're-link')}${btn('real-employee-month', icon('chevron-right'), 're-icon-button', 'data-direction="1" aria-label="Nächster Kalendermonat"')}</div></div>
    <div class="re-calendar" role="group" aria-label="Personenbezogener Abwesenheitskalender"><div class="re-weekdays" aria-hidden="true">${['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].map(day => `<span>${day}</span>`).join('')}</div><div class="re-calendar-days">${Array.from({ length: cells }, (_, index) => {
      const date = new Date(first.getFullYear(), first.getMonth(), 1 - offset + index, 12), day = ymd(date);
      const dayEvents = list.filter(event => realEmployeeDate(event.start) && realEmployeeDate(event.end || event.start) && event.start <= day && (event.end || event.start) >= day);
      const outside = date.getMonth() !== month.getMonth();
      const label = fmt(day) + (dayEvents.length ? ', ' + dayEvents.map(realEmployeeEventKind).join(', ') : ', keine Abwesenheit eingetragen');
      return btn('real-employee-day', `<span>${date.getDate()}</span><span class="re-day-markers" aria-hidden="true">${dayEvents.slice(0, 3).map(event => `<i class="re-day-dot ${event.kind === 'Krank' ? 're-day-sick' : ''}"></i>`).join('')}${dayEvents.length > 3 ? '<b>+</b>' : ''}</span>`, `re-calendar-day${outside ? ' re-outside' : ''}${day === realEmployeeToday ? ' re-today' : ''}`, `data-p="${E(person.id)}" data-date="${day}" aria-label="${E(label)}"${day === realEmployeeToday ? ' aria-current="date"' : ''}`);
    }).join('')}</div></div>
    <div class="re-calendar-mobile"><label>Tag öffnen<input type="date" data-real-employee-date value="${selected}" required></label>${btn('real-employee-picked-day', 'Öffnen ' + icon('arrow-up-right'), 're-button', `data-p="${E(person.id)}"`)}<div class="re-mobile-month-list">${monthEvents.length ? monthEvents.map(realEmployeeEventRow).join('') : '<p class="re-muted">Diesen Monat sind keine Abwesenheiten eingetragen.</p>'}</div></div>
    <div class="re-calendar-legend"><span><i class="re-day-dot"></i>Urlaub / Überstundenfrei</span><span><i class="re-day-dot re-day-sick"></i>Krankheit</span></div>`;
}

function realEmployeeDay(id, date) {
  const person = realEmployeePerson(id), day = realEmployeeDate(date);
  if (!person || !day) return;
  const dayEvents = realEmployeeEvents(id).filter(event => realEmployeeDate(event.start) && realEmployeeDate(event.end || event.start) && event.start <= day && (event.end || event.start) >= day);
  open(person.name + ' · ' + fmt(day), `<div class="re-day-detail"><p class="re-muted">${E(person.team || '')}</p><div class="re-day-entries">${dayEvents.length ? dayEvents.map(realEmployeeEventRow).join('') : '<div class="re-empty">Für diesen Tag ist keine Abwesenheit eingetragen.</div>'}</div><div class="re-bottom-actions">${btn('real-employee-back', icon('arrow-left') + ' Mitarbeiterübersicht', 're-link', `data-p="${E(person.id)}"`)}<div class="re-actions">${btn('plan-sick', 'Krankheit erfassen', 're-button', `data-p="${E(person.id)}" data-date="${day}" ${readOnly() ? 'disabled' : ''}`)}${btn('plan-vacation', icon('plus') + ' Urlaub eintragen', 're-button re-primary', `data-p="${E(person.id)}" data-date="${day}" ${readOnly() ? 'disabled' : ''}`)}</div></div></div>`);
}

function realEmployee(id) {
  const person = realEmployeePerson(id);
  if (!person) return open('Mitarbeiterübersicht', '<p class="re-empty">Diese Person ist nicht mehr verfügbar.</p>');
  if (String(realEmployeeView.id) !== String(person.id) || !realEmployeeView.month) {
    const sourceMonth = state.month instanceof Date && Number.isFinite(state.month.getTime()) ? state.month : new Date(realEmployeeToday + 'T12:00:00');
    realEmployeeView.month = new Date(sourceMonth.getFullYear(), sourceMonth.getMonth(), 1, 12);
  }
  realEmployeeView.id = person.id;
  const initials = String(person.name || '').trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('');
  const openRequests = requests.filter(request => String(request.p) === String(person.id) && request.status === 'Offen').sort((a, b) => String(b.date).localeCompare(String(a.date)));
  const personEvents = realEmployeeEvents(id);
  const upcoming = personEvents.filter(event => realEmployeeDate(event.start) && realEmployeeDate(event.end || event.start) && event.start <= (event.end || event.start) && (event.end || event.start) >= realEmployeeToday).sort((a, b) => a.start.localeCompare(b.start));
  const undated = personEvents.filter(event => !realEmployeeDate(event.start) || !realEmployeeDate(event.end || event.start) || event.start > (event.end || event.start));
  const leaveDate = realEmployeeDate(person.leaveBalanceAsOf);
  const hasLeave = typeof person.leaveRemaining === 'number' && Number.isFinite(person.leaveRemaining) && person.leaveRemaining >= 0 && leaveDate;
  const hasActual = typeof person.actual === 'string' && /^\d+:\d{2}$/.test(person.actual) && Number(person.actual.split(':')[1]) < 60;
  const hasTarget = typeof person.target === 'string' && /^\d+:\d{2}$/.test(person.target) && Number(person.target.split(':')[1]) < 60;
  const hasBalance = typeof person.balance === 'number' && Number.isFinite(person.balance);
  const hasIssue = Boolean(person.issue) && !person.ack;
  open('Mitarbeiterübersicht', `<div class="re-profile" data-real-employee="${E(person.id)}">
    <header class="re-identity"><div class="re-identity-copy"><span class="re-avatar" aria-hidden="true">${E(initials)}</span><div><h3>${E(person.name || 'Mitarbeiter')}</h3><p>${E(person.team || 'Kein Team zugeordnet')}${person.role ? ' · ' + E(person.role) : ''}</p>${person.username ? `<span class="re-muted">${E(person.username)}</span>` : ''}</div></div><div class="re-actions">${btn('real-employee-time', icon('clock-3') + ' Zeitprüfung', 're-button', `data-p="${E(person.id)}"`)}${btn('plan-vacation', icon('plus') + ' Urlaub eintragen', 're-button re-primary', `data-p="${E(person.id)}" ${readOnly() ? 'disabled' : ''}`)}</div></header>
    <div class="re-profile-grid"><div class="re-main-column">
      <section class="re-section re-time-section"><div class="re-section-heading"><div><h3>Stunden & Zeitkonto</h3>${person.hoursPeriodLabel ? `<p>${E(person.hoursPeriodLabel)}</p>` : ''}</div>${btn('print', icon('printer') + ' Bericht', 're-link', `data-p="${E(person.id)}"`)}</div><dl class="re-metrics"><div><dt>Erfasst</dt><dd>${hasActual ? E(person.actual) + '<span> h</span>' : '–'}</dd></div><div><dt>Soll</dt><dd>${hasTarget ? E(person.target) + '<span> h</span>' : '–'}</dd></div><div><dt>Gesamtsaldo</dt><dd class="${hasBalance && person.balance < 0 ? 're-negative' : hasBalance && person.balance > 0 ? 're-positive' : ''}">${hasBalance ? E(money(person.balance)) + '<span> h</span>' : '–'}</dd></div></dl>${!hasActual && !hasTarget && !hasBalance ? '<p class="re-muted">Für diese Person sind noch keine Stundenwerte hinterlegt.</p>' : ''}
      ${hasIssue ? `<div class="re-issue"><span class="re-issue-icon">${icon('circle-alert')}</span><div><strong>${E(person.issue)}</strong><p>${realEmployeeDate(person.issueDate) ? E(fmt(person.issueDate)) : 'Offener Hinweis in der Zeitprüfung'}</p></div>${btn('real-employee-time', 'Prüfen ' + icon('arrow-up-right'), 're-link', `data-p="${E(person.id)}"`)}</div>` : `<div class="re-clear">${icon('circle-check')}<span>${person.issue && person.ack ? 'Zeithinweis als geprüft markiert.' : 'Keine offenen Zeithinweise.'}</span></div>`}</section>
      <section class="re-section re-requests-section"><div class="re-section-heading"><div><h3>Offene Anträge <span class="re-count">${openRequests.length}</span></h3><p>Entscheidungen für ${E(String(person.name || '').split(' ')[0])}</p></div>${btn('real-employee-requests', 'Alle Anträge ' + icon('arrow-up-right'), 're-link', `data-p="${E(person.id)}"`)}</div>${openRequests.length ? openRequests.map(request => `<details class="re-request"><summary><span class="re-request-type-icon">${icon(request.type === 'Urlaub' ? 'calendar-days' : 'clock-3')}</span><span><strong>${E(request.type)}</strong><small>${E(realEmployeePeriod(request.date, request.end || request.date))}</small></span><span class="re-request-open">Prüfen ${icon('chevron-down')}</span></summary>${deskReviewRequest(request)}</details>`).join('') : '<div class="re-empty">Aktuell stehen keine Anträge zur Entscheidung aus.</div>'}</section>
      <section class="re-section" data-real-employee-calendar>${realEmployeeCalendar(person)}</section>
    </div><aside class="re-aside-column">
      <section class="re-section re-leave-section"><div class="re-section-heading"><h3>Urlaubskonto</h3>${icon('sun')}</div>${hasLeave ? `<div class="re-leave-value"><strong>${E(person.leaveRemaining.toLocaleString('de-CH'))}</strong><span>Tage verfügbar</span></div><p class="re-muted">Stand ${E(fmt(leaveDate))}</p>` : '<p class="re-muted">Kein aktueller Urlaubsstand hinterlegt.</p>'}${typeof person.leaveEntitlement === 'number' && Number.isFinite(person.leaveEntitlement) && person.leaveEntitlement >= 0 ? `<p class="re-leave-fact">Anspruch <strong>${E(person.leaveEntitlement.toLocaleString('de-CH'))} Tage</strong></p>` : ''}${typeof person.leavePlanned === 'number' && Number.isFinite(person.leavePlanned) && person.leavePlanned >= 0 ? `<p class="re-leave-fact">Geplant <strong>${E(person.leavePlanned.toLocaleString('de-CH'))} Tage</strong></p>` : ''}</section>
      <section class="re-section re-absences-section"><div class="re-section-heading"><div><h3>Aktuell & geplant</h3><p>Ab ${E(fmt(realEmployeeToday))}</p></div></div>${upcoming.length ? upcoming.map(realEmployeeEventRow).join('') : '<div class="re-empty">Keine aktuellen oder kommenden Abwesenheiten eingetragen.</div>'}${undated.length ? `<p class="re-muted re-incomplete">${undated.length === 1 ? 'Ein Abwesenheitseintrag hat' : E(undated.length) + ' Abwesenheitseinträge haben'} kein vollständiges Datum.</p>${undated.map(realEmployeeEventRow).join('')}` : ''}<div class="re-absence-actions">${btn('plan-sick', icon('heart-pulse') + ' Krankheit erfassen', 're-link', `data-p="${E(person.id)}" data-date="${realEmployeeToday}" ${readOnly() ? 'disabled' : ''}`)}</div></section>
    </aside></div><footer class="re-profile-footer"><span>${readOnly() ? 'Nur Ansicht' : 'Personenbezogene Übersicht'}</span>${btn('close', icon('arrow-left') + ' Zurück zur Arbeitsübersicht', 're-link')}</footer>
  </div>`);
}

r.addEventListener('click', event => {
  const button = event.target.closest('button[data-act^="real-employee-"]');
  if (!button || !r.contains(button)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const action = button.dataset.act;
  const person = realEmployeePerson(button.dataset.p ?? realEmployeeView.id);
  if (!person) return;
  if (action === 'real-employee-back') return realEmployee(person.id);
  if (action === 'real-employee-time') {
    state.person = person.id; state.search = person.name; state.issue = '';
    if (person.hidden) state.showHidden = true;
    return nav('time');
  }
  if (action === 'real-employee-requests') {
    state.reqSearch = person.name; state.reqStatus = 'Alle'; state.reqType = 'Alle';
    const first = requests.find(request => String(request.p) === String(person.id));
    if (first) state.selected = first.id;
    return nav('requests');
  }
  if (action === 'real-employee-day') return realEmployeeDay(person.id, button.dataset.date);
  if (action === 'real-employee-picked-day') {
    const input = q('[data-real-employee-date]');
    if (!input || !realEmployeeDate(input.value)) { if (input) input.reportValidity(); return; }
    return realEmployeeDay(person.id, input.value);
  }
  if (action === 'real-employee-month' || action === 'real-employee-today') {
    const month = realEmployeeView.month;
    realEmployeeView.month = action === 'real-employee-today'
      ? new Date(realEmployeeToday.slice(0, 7) + '-01T12:00:00')
      : new Date(month.getFullYear(), month.getMonth() + (button.dataset.direction === '-1' ? -1 : 1), 1, 12);
    const calendar = q('[data-real-employee-calendar]');
    if (calendar) { calendar.innerHTML = realEmployeeCalendar(person); icons(); }
  }
}, true);
