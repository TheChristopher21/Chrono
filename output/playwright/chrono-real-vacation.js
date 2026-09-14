// Replace only renderVacation() in the local browser mockup.
// The existing planner state, form reader, calendar and action handlers remain in use.
function renderVacation() {
  const p = state.planner;
  if (!p) return;
  if (p.contextPerson) p.company = false;

  const person = people.find(item => String(item.id) === String(p.person));
  const locked = p.contextPerson || p.queue.length > 0 || !!p.edit;
  const showHours = p.overtime && !p.company;
  const hasDraft = !!(p.start || p.end);
  const complete = !!(p.start && p.end && p.end >= p.start);
  const overlaps = complete && p.queue.some(item => p.start <= item.end && p.end >= item.start);
  const errors = [];
  if (p.start && p.end && p.end < p.start) errors.push('Das Enddatum muss am oder nach dem Startdatum liegen.');
  if (p.half && complete && p.start !== p.end) errors.push('Halbtags ist nur für einen einzelnen Tag möglich.');
  if (showHours && complete && !(Number(p.hours) > 0)) errors.push('Bitte die abzuziehenden Überstunden angeben.');
  if (overlaps) errors.push('Der ausgewählte Zeitraum überschneidet sich mit einem geplanten Urlaub.');

  const periodText = item => item.start === item.end
    ? fmt(item.start)
    : `${fmt(item.start)} – ${fmt(item.end)}`;
  const kindText = item => `${item.half ? 'Halbtags' : 'Ganztags'} · ${item.overtime && !p.company ? 'Überstundenfrei' : 'Urlaub'}${item.overtime && !p.company && item.hours ? ` · ${E(item.hours)} h` : ''}`;
  const total = p.queue.length + (complete && !errors.length ? 1 : 0);
  const selectionText = complete
    ? periodText(p)
    : p.start ? `Ab ${fmt(p.start)} · Ende wählen` : 'Noch kein Zeitraum ausgewählt';
  const nextStep = p.start && !p.end
    ? 'Enddatum wählen. Du kannst dafür den Monat wechseln.'
    : !hasDraft && p.queue.length
      ? 'Weiteren Zeitraum wählen oder die geplanten Urlaube speichern.'
      : !hasDraft
        ? 'Start und Ende im Kalender wählen. Für einen Tag zweimal dasselbe Datum anklicken.'
        : p.edit
          ? 'Zeitraum prüfen und Änderungen speichern.'
          : 'Auswahl prüfen und speichern. Weitere Zeiträume lassen sich separat hinzufügen.';

  open(p.edit ? 'Urlaub bearbeiten' : 'Urlaub planen', `
    <form data-form="vacation" class="cp-vacation" aria-describedby="cp-vac-help">
      <div class="cp-vac-layout">
        <section class="cp-vac-selection" aria-label="Zeitraum auswählen">
          <div class="cp-vac-monthbar">
            ${btn('plannerprev', '←', 'cp-icon', 'aria-label="Voriger Planungsmonat"')}
            <h3>${p.month.toLocaleDateString('de-CH', { month: 'long', year: 'numeric' })}</h3>
            ${btn('plannernext', '→', 'cp-icon', 'aria-label="Nächster Planungsmonat"')}
          </div>
          <div class="cp-vac-calendar">${cal(p.month, true, p)}</div>
          <div class="cp-vac-legend" aria-label="Kalenderlegende">
            <span><i class="cp-vac-key cp-vac-key-selection" aria-hidden="true"></i>Auswahl</span>
            <span><i class="cp-vac-key cp-vac-key-planned" aria-hidden="true"></i>Geplant</span>
            <span><i class="cp-vac-key cp-vac-key-saved" aria-hidden="true"></i>Gespeichert</span>
          </div>
          <div class="cp-vac-dates">
            <label>Von<input name="start" type="date" value="${E(p.start)}" aria-describedby="cp-vac-help"></label>
            <label>Bis<input name="end" type="date" value="${E(p.end)}" min="${E(p.start)}" aria-invalid="${!!(p.start && p.end && p.end < p.start)}" aria-describedby="cp-vac-help"></label>
          </div>
          <div class="cp-vac-options">
            <label class="cp-check"><input name="half" type="checkbox" ${p.half ? 'checked' : ''}>Halbtags</label>
            <label class="cp-check" ${p.company ? 'hidden' : ''}><input name="overtime" type="checkbox" ${p.overtime ? 'checked' : ''} ${p.company ? 'disabled' : ''}>Überstunden nutzen</label>
          </div>
          <label class="cp-vac-hours" ${showHours ? '' : 'hidden'}>Überstundenabzug
            <span><input name="hours" type="number" step="0.25" min="0.25" value="${E(p.hours)}" placeholder="z. B. 4" ${showHours ? '' : 'disabled'} aria-describedby="cp-vac-help"><span>Stunden</span></span>
          </label>
          ${p.edit ? '' : `<div class="cp-vac-add">${btn('addperiod', 'Zeitraum hinzufügen', '', `aria-describedby="cp-vac-help" ${!complete || errors.length ? 'disabled' : ''}`)}</div>`}
        </section>

        <aside class="cp-vac-plan" aria-label="Urlaubsplanung">
          <div class="cp-vac-person">
            <h3>${p.company ? 'Betriebsurlaub' : 'Für wen?'}</h3>
            ${locked || p.company ? `
              <select name="person" hidden disabled aria-label="Mitarbeitende">${personOpts(p.person)}</select>
              <div class="cp-vac-person-value"><strong>${p.company ? 'Gesamtes Team' : E(person?.name || '')}</strong>${!p.company && person?.team ? `<span>${E(person.team)}</span>` : ''}</div>
            ` : `<label class="cp-vac-person-select"><span class="cp-vac-visually-hidden">Mitarbeitende</span><select name="person">${personOpts(p.person)}</select></label>`}
            ${p.contextPerson
              ? '<input name="company" type="checkbox" hidden disabled aria-label="Betriebsurlaub">'
              : `<label class="cp-check cp-vac-company"><input name="company" type="checkbox" ${p.company ? 'checked' : ''} ${locked ? 'disabled' : ''}>Betriebsurlaub für das gesamte Team</label>`}
          </div>

          <div class="cp-vac-periods">
            <div class="cp-vac-periods-head"><h3>${p.edit ? 'Zeitraum' : 'Geplante Zeiträume'}</h3>${total ? `<span class="cp-tag">${total}</span>` : ''}</div>
            ${p.queue.length && !p.edit ? `<ol class="cp-vac-period-list">${p.queue.map((item, i) => `
              <li class="cp-vac-period">
                <div><strong>${periodText(item)}</strong><span>${kindText(item)}</span></div>
                ${btn('removeperiod', '×', 'cp-icon', `data-i="${i}" aria-label="Zeitraum ${E(periodText(item))} entfernen"`)}
              </li>`).join('')}</ol>` : ''}
            <div class="cp-vac-current ${hasDraft ? 'cp-vac-current-active' : ''}">
              <span class="cp-vac-current-label">${hasDraft ? p.edit ? 'Bearbeitung' : 'Aktuelle Auswahl' : p.queue.length ? 'Weiterer Zeitraum' : 'Auswahl'}</span>
              <strong>${selectionText}</strong>
              ${complete ? `<span>${kindText(p)}</span>` : ''}
            </div>
          </div>

          <div class="cp-vac-save">
            <button type="button" class="cp-primary" data-act="localSubmit" ${errors.length || (!p.queue.length && !complete) || (hasDraft && !complete) || readOnly() ? 'disabled' : ''}>${p.edit ? 'Änderungen speichern' : total > 1 ? `${total} Zeiträume speichern` : 'Urlaub speichern'}</button>
            <div class="cp-vac-secondary">${p.edit ? btn('eventdelete', 'Urlaub löschen', 'cp-danger', `data-id="${p.edit}"`) : ''}${btn('close', 'Schließen')}</div>
          </div>
        </aside>

        <div class="cp-vac-status" id="cp-vac-help" aria-live="polite" aria-atomic="true">
          ${errors.length ? `<div class="cp-vac-validation" role="alert"><strong>Bitte prüfen</strong><ul>${errors.map(error => `<li>${error}</li>`).join('')}</ul></div>` : `<p class="cp-vac-hint">${nextStep}</p>`}
          ${p.saved ? `<p class="cp-vac-saved" role="status">${E(p.saved)}</p>` : ''}
        </div>
      </div>
    </form>`);
}
