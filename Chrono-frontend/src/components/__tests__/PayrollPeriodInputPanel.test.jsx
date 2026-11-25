import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayrollPeriodInputPanel from '../PayrollPeriodInputPanel';
import api from '../../utils/api';
vi.mock('../../utils/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => { vi.resetAllMocks(); api.get.mockResolvedValue({ data: [] }); api.post.mockImplementation((_url, input) => Promise.resolve({ data: { id: 8, userId: input.userId, periodStart: input.start, periodEnd: input.end, source: input.source } })); });
async function basic() {
  await screen.findByRole('form', { name: 'Zusätzliche Monatsgrundlagen' });
  await userEvent.selectOptions(screen.getByLabelText('Mitarbeitende für Monatsgrundlagen'), '7');
  for (const [label, value] of [['Beginn der Lohnperiode', '2026-01-01'], ['Ende der Lohnperiode', '2026-01-31'], ['Grundlage der Monatsdaten / Durchschnittsberechnung', 'Durchschnittslohn aus bestätigten Vorperioden']]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
}
it('stores paid absence minutes, average wage and percentage as independent monthly inputs', async () => {
  render(<PayrollPeriodInputPanel employees={[{ id: 7, name: 'Testperson' }]} />); await basic();
  await userEvent.click(screen.getByRole('button', { name: 'Bezahlte Abwesenheit hinzufügen' }));
  for (const [label, value] of [['Abwesenheitstag 1', '2026-01-15'], ['Ausfallminuten 1', '240'], ['Bezahlter Stunden-/Durchschnittsansatz 1', '32.5'], ['Vergütungsanteil (%) 1', '100']]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
  await userEvent.click(screen.getByRole('checkbox', { name: /Die Daten enthalten/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/company/payroll-configuration/period-inputs', expect.objectContaining({ data: { absences: [{ date: '2026-01-15', type: 'VACATION', minutes: 240, hourlyRate: '32.5', payRate: '1.000000' }], germanConcurrentIncome: null, swissOtherAperiodicGross: null } })));
  expect(await screen.findByRole('status')).toHaveTextContent('Monatsgrundlagen gespeichert');
});
it('keeps receipt, work and entitlement dates distinct and requires the complete-payment confirmation', async () => {
  render(<PayrollPeriodInputPanel employees={[{ id: 7, name: 'Testperson' }]} />); await basic();
  await userEvent.click(screen.getByRole('button', { name: 'Nachzahlung hinzufügen' }));
  for (const [label, value] of [['Eindeutige Zahlungsreferenz 1', 'REF-2026-1'], ['Arbeitsperiode ab · Nachzahlung 1', '2026-01-01'], ['Arbeitsperiode bis · Nachzahlung 1', '2026-01-15'], ['Tatsächlicher Zufluss · Nachzahlung 1', '2026-01-31'], ['Rechtsanspruch entstanden am · Nachzahlung 1', '2026-01-15'], ['Vollständige Bruttozahlung 1', '500.25']]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
  await userEvent.click(screen.getByRole('checkbox', { name: /Die Daten enthalten/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));expect(api.post).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('checkbox', { name: /Die vollständige Zahlung samt/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ data: expect.objectContaining({ payments: [{ reference: 'REF-2026-1', kind: 'REGULAR_ARREARS', earnedFrom: '2026-01-01', earnedThrough: '2026-01-15', receivedOn: '2026-01-31', claimEstablishedOn: '2026-01-15', gross: '500.25', thirteenthSettled: true }] }) })));
});
it('submits dated foreign employments without duplicating the aggregate foreign wage', async () => {
  render(<PayrollPeriodInputPanel employees={[{ id: 7, name: 'Testperson' }]} />); await basic();
  await userEvent.selectOptions(screen.getByLabelText('Weitere Monatsgrundlagen'), 'DE');
  await userEvent.click(screen.getByRole('button', { name: 'Arbeitgeber mit abweichenden SV-Daten hinzufügen' }));
  for (const [label, value] of [['Eindeutige Arbeitgeberbezeichnung · Arbeitgeber 1', 'Employer B'], ['SV-Beginn · Arbeitgeber 1', '2026-01-26'], ['SV-Ende · Arbeitgeber 1', '2026-01-31'], ['Tatsächliches RV-/ALV-Entgelt · Arbeitgeber 1', '360'], ['Tatsächliches KV-/PV-Entgelt · Arbeitgeber 1', '360']]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
  await userEvent.click(screen.getByRole('checkbox', { name: /Einzugsstellenmeldung bestätigt/ }));await userEvent.click(screen.getByRole('checkbox', { name: /Die Daten enthalten/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ data: expect.objectContaining({ germanConcurrentIncome: expect.objectContaining({ otherPensionGross: null, otherHealthGross: null, employments: [{ employer: 'Employer B', from: '2026-01-26', through: '2026-01-31', pensionGross: '360', healthGross: '360' }] }) }) })));
});
it('requires explicit gross and pension amounts for every insurance coverage interval', async () => {
  render(<PayrollPeriodInputPanel employees={[{ id: 7, name: 'Testperson' }]} />); await basic();
  await userEvent.click(screen.getByRole('button', { name: 'Versicherungsabschnitt hinzufügen' }));
  for (const [label, value] of [['Deckungsbeginn · Abschnitt 1', '2026-01-01'], ['Deckungsende · Abschnitt 1', '2026-01-31'], ['SV-Bruttolohn · Abschnitt 1', '5000'], ['BVG Arbeitnehmer · Abschnitt 1', '0']]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
  await userEvent.click(screen.getByRole('checkbox', { name: /Die Daten enthalten/ }));await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));expect(api.post).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('BVG Arbeitgeber · Abschnitt 1'), { target: { value: '0' } });
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ data: expect.objectContaining({ swissInsuranceAllocations: [{ from: '2026-01-01', through: '2026-01-31', gross: '5000', pensionEmployee: '0', pensionEmployer: '0' }] }) })));
});
it('requires the collection-agency confirmation for German concurrent social insurance', async () => {
  render(<PayrollPeriodInputPanel employees={[{ id: 7, name: 'Testperson' }]} />); await basic();
  await userEvent.selectOptions(screen.getByLabelText('Weitere Monatsgrundlagen'), 'DE');
  fireEvent.change(screen.getByLabelText('Weiteres RV-/ALV-Entgelt im Überschneidungszeitraum'), { target: { value: '5000' } });
  fireEvent.change(screen.getByLabelText('Weiteres KV-/PV-Entgelt im Überschneidungszeitraum'), { target: { value: '5000' } });
  await userEvent.click(screen.getByRole('checkbox', { name: /Die Daten enthalten/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  expect(api.post).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole('checkbox', { name: /Einzugsstellenmeldung bestätigt/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Monatsgrundlagen als neue Version speichern' }));
  await waitFor(() => expect(api.post).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ data: expect.objectContaining({ germanConcurrentIncome: { otherPensionGross: '5000', otherHealthGross: '5000', pensionOneOffBase: null, healthOneOffBase: null, fundAllocationConfirmed: true } }) })));
});
it('captures actual 2025 originals without inventing zero amounts or allowing another payment', async()=>{
  render(<PayrollPeriodInputPanel employees={[{id:7,name:'Testperson'}]} />);await basic();
  fireEvent.change(screen.getByLabelText('Beginn der Lohnperiode'),{target:{value:'2025-12-01'}});fireEvent.change(screen.getByLabelText('Ende der Lohnperiode'),{target:{value:'2025-12-31'}});
  await userEvent.selectOptions(screen.getByLabelText('Land der Altgrundlagen 2025'),'DE');
  expect(screen.getByRole('button',{name:'Nachzahlung hinzufügen'})).toBeDisabled();expect(screen.getByLabelText('Tatsächlich abgezogene Kirchensteuer 2025')).toHaveValue(null);
  await userEvent.click(screen.getByRole('checkbox',{name:/Die Daten enthalten/}));await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));expect(api.post).not.toHaveBeenCalled();
  for(const [label,value] of [['Tatsächlicher Grundlohn 2025','5000'],['Tatsächliche Zuschläge 2025','0'],['Tatsächlich bezahlte Überstunden 2025','0'],['Tatsächlich bezahlter 13. Monatslohn 2025','0'],['Anspruchsbasis des 13. Monatslohns 2025','5000'],['Tatsächlich abgezogene Lohn-/Quellensteuer 2025','793.33'],['Belegte Versicherungstage 2025','30'],['Belegte RV-/ALV-Basis 2025','5000'],['Belegte KV-/PV-Basis 2025','5000'],['Tatsächlich abgezogener Solidaritätszuschlag 2025','0'],['Tatsächlich abgezogene Kirchensteuer 2025','0']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({data:expect.objectContaining({historicalBaseline:expect.objectContaining({start:'2025-12-01',end:'2025-12-31',base:'5000',withholding:'793.33',insuranceDays:30,churchTax:'0'})})})));
});
it('keeps old work dates and actual 2026 cash dates with explicit extra pension amounts',async()=>{
  render(<PayrollPeriodInputPanel employees={[{id:7,name:'Testperson'}]} />);await basic();await userEvent.click(screen.getByRole('button',{name:'Nachzahlung hinzufügen'}));
  for(const [label,value] of [['Eindeutige Zahlungsreferenz 1','HIST-2025'],['Arbeitsperiode ab · Nachzahlung 1','2025-12-01'],['Arbeitsperiode bis · Nachzahlung 1','2025-12-31'],['Tatsächlicher Zufluss · Nachzahlung 1','2026-01-31'],['Rechtsanspruch entstanden am · Nachzahlung 1','2025-12-31'],['Vollständige Bruttozahlung 1','500'],['BVG-Zusatz Arbeitnehmer · Nachzahlung 1','0'],['BVG-Zusatz Arbeitgeber · Nachzahlung 1','25']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('checkbox',{name:/Die vollständige Zahlung samt/}));await userEvent.click(screen.getByRole('checkbox',{name:/Die Daten enthalten/}));await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({data:expect.objectContaining({payments:[expect.objectContaining({earnedFrom:'2025-12-01',receivedOn:'2026-01-31',bvgEmployeeExtra:'0',bvgEmployerExtra:'25'})]})})));
});
it('keeps a retroactive German agreement distinct and records the evidenced employee recovery confirmation',async()=>{
  render(<PayrollPeriodInputPanel employees={[{id:7,name:'Testperson'}]} />);await basic();await userEvent.click(screen.getByRole('button',{name:'Nachzahlung hinzufügen'}));
  await userEvent.selectOptions(screen.getByLabelText('Art der Nachzahlung 1'),'RETROACTIVE_RAISE');
  for(const [label,value] of [['Eindeutige Zahlungsreferenz 1','RAISE-2025'],['Arbeitsperiode ab · Nachzahlung 1','2025-12-01'],['Arbeitsperiode bis · Nachzahlung 1','2025-12-31'],['Tatsächlicher Zufluss · Nachzahlung 1','2026-01-31'],['Rechtsanspruch entstanden am · Nachzahlung 1','2026-01-10'],['Vollständige Bruttozahlung 1','500']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('checkbox',{name:/Nachholung des zusätzlichen Arbeitnehmer/}));await userEvent.click(screen.getByRole('checkbox',{name:/Die vollständige Zahlung samt/}));await userEvent.click(screen.getByRole('checkbox',{name:/Die Daten enthalten/}));await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({data:expect.objectContaining({payments:[expect.objectContaining({kind:'RETROACTIVE_RAISE',claimEstablishedOn:'2026-01-10',employeeSocialRecoveryConfirmed:true})]})})));
});
it('preserves an employer-only pension entry as explicit employee unknown rather than dropping it',async()=>{
  render(<PayrollPeriodInputPanel employees={[{id:7,name:'Testperson'}]} />);await basic();await userEvent.click(screen.getByRole('button',{name:'Nachzahlung hinzufügen'}));
  for(const [label,value] of [['Eindeutige Zahlungsreferenz 1','BVG-UNKNOWN'],['Arbeitsperiode ab · Nachzahlung 1','2025-12-01'],['Arbeitsperiode bis · Nachzahlung 1','2025-12-31'],['Tatsächlicher Zufluss · Nachzahlung 1','2026-01-31'],['Rechtsanspruch entstanden am · Nachzahlung 1','2025-12-31'],['Vollständige Bruttozahlung 1','500'],['BVG-Zusatz Arbeitgeber · Nachzahlung 1','100']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('checkbox',{name:/Die vollständige Zahlung samt/}));await userEvent.click(screen.getByRole('checkbox',{name:/Die Daten enthalten/}));await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({data:expect.objectContaining({payments:[expect.objectContaining({bvgEmployeeExtra:null,bvgEmployerExtra:'100'})]})})));
});
it('preserves a signed original Swiss withholding allocation with its actual canton phase',async()=>{
  render(<PayrollPeriodInputPanel employees={[{id:7,name:'Testperson'}]} />);await basic();
  fireEvent.change(screen.getByLabelText('Beginn der Lohnperiode'),{target:{value:'2025-12-01'}});fireEvent.change(screen.getByLabelText('Ende der Lohnperiode'),{target:{value:'2025-12-31'}});await userEvent.selectOptions(screen.getByLabelText('Land der Altgrundlagen 2025'),'CH');
  for(const [label,value] of [['Tatsächlicher Grundlohn 2025','5000'],['Tatsächliche Zuschläge 2025','0'],['Tatsächlich bezahlte Überstunden 2025','0'],['Tatsächlich bezahlter 13. Monatslohn 2025','0'],['Anspruchsbasis des 13. Monatslohns 2025','0'],['Tatsächlich abgezogene Lohn-/Quellensteuer 2025','-12.30'],['Belegte Versicherungstage 2025','30'],['Belegte ALV-/UVG-Basis 2025','5000'],['Belegte KTG-Basis 2025','5000'],['Vertragliche KTG-Jahreslohngrenze 2025 (0 = unbeschränkt)','0']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('button',{name:'Ursprüngliche Steuerzuordnung hinzufügen'}));for(const [label,value] of [['Kanton der ursprünglichen Steuer 1','zh'],['Beginn des ursprünglichen Kantonsabschnitts 1','2025-12-01'],['Ursprünglicher Steuerabzug / Erstattung 1','-12.30']])fireEvent.change(screen.getByLabelText(label),{target:{value}});
  await userEvent.click(screen.getByRole('checkbox',{name:/Die Daten enthalten/}));await userEvent.click(screen.getByRole('button',{name:'Monatsgrundlagen als neue Version speichern'}));
  await waitFor(()=>expect(api.post).toHaveBeenCalledWith(expect.any(String),expect.objectContaining({data:expect.objectContaining({historicalBaseline:expect.objectContaining({withholding:'-12.30',taxAllocations:[{canton:'ZH',from:'2025-12-01',amount:'-12.30'}]})})})));
});
