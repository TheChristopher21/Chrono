/* Insert in the mock engine scope. Uses E, btn, icon, fmt, requests, people,
   events and readOnly; all writes stay with the existing delegated handlers. */
function deskReviewDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return '';
  const parsed = new Date(value + 'T12:00:00Z');
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value ? value : '';
}

function deskReviewPeriod(start, end) {
  const first = deskReviewDate(start), last = deskReviewDate(end);
  if (!first || !last || first > last) return '';
  return first === last ? fmt(first) : fmt(first) + ' – ' + fmt(last);
}

function deskReviewPerson(id) {
  return people.find(person => String(person.id) === String(id));
}

function deskReviewOverlaps(request, person) {
  const start = deskReviewDate(request.date), end = deskReviewDate(request.end);
  if (!start || !end || start > end) return '';
  if (!person || !person.team) {
    return '<p class="cr-muted">Für den Teamvergleich fehlt die Teamzuordnung.</p>';
  }
  const seen = new Set();
  const sameTeam = events.filter(event => {
    const owner = deskReviewPerson(event.p);
    if (!owner || owner.team !== person.team || String(event.requestId) === String(request.id)) return false;
    if (event.id != null && seen.has(String(event.id))) return false;
    if (event.id != null) seen.add(String(event.id));
    return true;
  });
  const invalid = sameTeam.filter(event => !deskReviewPeriod(event.start, event.end || event.start)).length;
  const overlaps = sameTeam.filter(event => {
    const first = deskReviewDate(event.start), last = deskReviewDate(event.end || event.start);
    return first && last && first <= last && first <= end && last >= start;
  }).sort((a, b) => a.start.localeCompare(b.start));
  const items = overlaps.map(event => {
    const owner = deskReviewPerson(event.p);
    const own = String(owner.id) === String(person.id);
    const first = event.start > start ? event.start : start;
    const last = (event.end || event.start) < end ? (event.end || event.start) : end;
    return `<li><div class="cr-overlap-person">${icon(own ? 'user' : 'users')}<span>${E(own ? 'Eigener Abwesenheitseintrag' : owner.name)}</span></div><span class="cr-muted">${E(event.kind || 'Abwesenheit')}${event.half ? ' · halbtags' : ''} · ${E(deskReviewPeriod(first, last))}</span></li>`;
  }).join('');
  return `<section class="cr-overlap-block" aria-label="Zeitgleiche Abwesenheiten im Team"><h3>Überschneidungen im Team <span class="cr-muted">${E(person.team)}</span></h3>${items ? `<ul class="cr-overlaps">${items}</ul>` : '<p class="cr-muted">Keine Überschneidung mit den datierten Abwesenheiten im Teamkalender.</p>'}${invalid ? `<p class="cr-data-note">${invalid === 1 ? 'Ein weiterer Team-Eintrag hat' : E(invalid) + ' weitere Team-Einträge haben'} kein vollständiges Datum und ${invalid === 1 ? 'kann' : 'können'} hier nicht verglichen werden.</p>` : ''}</section>`;
}

function deskReviewRequest(request) {
  const person = deskReviewPerson(request.p);
  const vacation = request.type === 'Urlaub';
  const date = vacation ? deskReviewPeriod(request.date, request.end) : deskReviewPeriod(request.date, request.date);
  const closed = request.status !== 'Offen';
  const locked = readOnly() || closed;
  const attrs = `data-id="${E(request.id)}"`;
  const leaveDate = person && deskReviewDate(person.leaveBalanceAsOf);
  const leave = person && typeof person.leaveRemaining === 'number' && Number.isFinite(person.leaveRemaining) && person.leaveRemaining >= 0 && leaveDate
    ? `<p class="cr-balance">Verfügbarer Urlaub: <strong>${E(person.leaveRemaining.toLocaleString('de-CH'))} Tage</strong><span class="cr-muted"> · Stand ${E(fmt(leaveDate))}</span></p>` : '';
  const comparison = vacation
    ? `<div class="cr-period"><span class="cr-label">Beantragter Zeitraum</span><strong>${E(date || 'Zeitraum nicht vollständig hinterlegt')}</strong>${request.half === true ? '<span class="cr-muted">Halber Tag</span>' : ''}${request.overtime === true ? '<span class="cr-muted">Überstundenabbau</span>' : ''}</div>${leave}`
    : `<div class="cr-compare"><div><span class="cr-label">Bisher erfasst</span><strong>${E(request.old == null || request.old === '' ? 'Nicht hinterlegt' : request.old)}</strong></div><span class="cr-arrow">${icon('arrow-right')}</span><div><span class="cr-label">Beantragt</span><strong>${E(request.next == null || request.next === '' ? 'Nicht hinterlegt' : request.next)}</strong></div></div>`;
  const correctionMissing = !vacation && (request.next == null || request.next === '');
  return `<section class="cr-review" aria-label="Antrag prüfen">
    ${vacation ? '' : `<p class="cr-date">${icon('calendar-days')}<span>${E(date || 'Datum nicht hinterlegt')}</span></p>`}
    <div class="cr-context"><div>${comparison}</div><div class="cr-reason"><span class="cr-label">Grund des Antrags</span><p>${E(request.reason || 'Kein Grund hinterlegt.')}</p></div></div>
    ${vacation ? deskReviewOverlaps(request, person) : ''}
    ${!date || correctionMissing ? '<p class="cr-data-note">Vor dem Genehmigen müssen die fehlenden Antragsdaten ergänzt werden.</p>' : ''}
    <label class="cr-note"><span>Kommentar <span class="cr-muted">· optional</span></span><textarea data-note="${E(request.id)}" rows="2" placeholder="Rückmeldung zur Entscheidung" ${locked ? 'disabled' : ''}>${E(request.note || '')}</textarea></label>
    <div class="cr-footer"><div class="cr-secondary">${btn('requesttime', 'In Zeitprüfung öffnen ' + icon('arrow-up-right'), 'cr-link', attrs)}${closed ? `<span class="cr-muted">${E(request.status)}</span>` : readOnly() ? '<span class="cr-muted">Nur Ansicht</span>' : ''}</div><div class="cr-actions">${btn('deny', 'Ablehnen', 'cr-button', `${attrs} ${locked ? 'disabled' : ''}`)}${btn('approve', icon('check') + ' Genehmigen', 'cr-button cr-primary', `${attrs} ${locked || !date || correctionMissing || !person ? 'disabled' : ''}`)}</div></div>
  </section>`;
}

function deskReviewIssue(person) {
  const exact = deskReviewDate(person.issueDate);
  const start = deskReviewDate(person.issueStart), end = deskReviewDate(person.issueEnd);
  const period = exact ? fmt(exact) : deskReviewPeriod(start, end);
  const editDate = exact || (start && start === end ? start : '');
  const weekly = /wochenabweichung/i.test(person.issue || '');
  const hasAck = weekly && typeof person.ack === 'boolean';
  const action = editDate
    ? btn('editday', 'Tag bearbeiten ' + icon('arrow-up-right'), 'cr-button', `data-p="${E(person.id)}" data-date="${E(editDate)}" ${readOnly() ? 'disabled' : ''}`)
    : btn('person', 'In Zeitprüfung öffnen ' + icon('arrow-up-right'), 'cr-button', `data-p="${E(person.id)}"`);
  return `<section class="cr-review cr-issue" aria-label="Zeithinweis prüfen">
    <div class="cr-issue-context"><div><span class="cr-label">Zu prüfen</span><strong>${E(person.issue || 'Kein offener Zeithinweis')}</strong></div>${period ? `<p class="cr-date">${icon('calendar-days')}<span>${E(period)}</span></p>` : '<p class="cr-data-note">Für diesen Hinweis ist kein Datum hinterlegt. Die zugehörigen Einträge lassen sich in der Zeitprüfung zuordnen.</p>'}</div>
    ${hasAck ? `<label class="cr-ack"><input type="checkbox" data-ack="${E(person.id)}" ${person.ack ? 'checked' : ''} ${readOnly() ? 'disabled' : ''}><span>Wochenabweichung geprüft und in Ordnung</span></label>` : ''}
    <div class="cr-footer"><span class="cr-muted">${hasAck && person.ack ? 'Als geprüft markiert' : readOnly() ? 'Nur Ansicht' : ''}</span><div class="cr-actions">${action}${editDate ? btn('person', 'Zeitprüfung öffnen', 'cr-link', `data-p="${E(person.id)}"`) : ''}</div></div>
  </section>`;
}

function deskReview(key) {
  const match = /^([rp])(\d+)$/.exec(String(key));
  if (!match) return '';
  const item = match[1] === 'r'
    ? requests.find(request => String(request.id) === match[2])
    : deskReviewPerson(match[2]);
  if (!item) return '<section class="cr-review"><p class="cr-muted">Dieser Vorgang ist nicht mehr verfügbar.</p></section>';
  return match[1] === 'r' ? deskReviewRequest(item) : deskReviewIssue(item);
}
