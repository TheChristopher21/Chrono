/** @vitest-environment jsdom */
import React from 'react';
import { cleanup,fireEvent,render,screen,waitFor } from '@testing-library/react';
import { afterEach,beforeEach,describe,expect,it,vi } from 'vitest';
const apiMock=vi.hoisted(() => ({ get:vi.fn(),post:vi.fn(),put:vi.fn() }));
vi.mock('../../utils/api.js',() => ({default:apiMock}));
import PmsHousekeepingWorkspace from './PmsHousekeepingWorkspace.jsx';
import { offlineScope, readOffline } from './pmsOfflineStore.js';
const property={id:5,name:'Hotel'};const rooms=[{id:8,number:'101'}];
const cleaning={id:10,version:0,roomId:8,roomNumber:'101',serviceDate:'2026-09-12',workType:'CLEAN',workStatus:'OPEN',type:'DEPARTURE',priority:90,estimatedMinutes:30,notes:null,assignedTo:'Anna'};
let tasks;
beforeEach(() => {
    localStorage.clear(); vi.clearAllMocks();tasks=[{...cleaning},{...cleaning,id:11,workType:'INSPECTION'},{...cleaning,id:12,workType:'TURNDOWN'}];
    apiMock.get.mockImplementation((path,config) => Promise.resolve({data:path.endsWith('/history') ? {items:[{id:config.params.page+1,toStatus:'OPEN',actor:'Anna',createdAt:'2026-09-12T08:00:00'}],totalElements:12,hasNext:config.params.page===0} : tasks}));
    apiMock.post.mockResolvedValue({data:{...cleaning,id:15,workType:'TURNDOWN'}});
    apiMock.put.mockImplementation((path,body) => Promise.resolve({data:{...tasks[0],...body,version:body.version+1}}));
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); localStorage.clear(); });
const open=() => render(<PmsHousekeepingWorkspace property={property} rooms={rooms} businessDate="2026-09-12" canManage />);
describe('independent housekeeping work',() => {
    it('transfers an offline command once and replaces the pending notice after the server acknowledges it', async () => {
        localStorage.setItem('token', `x.${btoa(JSON.stringify({ sub: 'device-tester' }))}.x`);
        const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
        apiMock.post.mockImplementation(async (_path, command) => {
            const saved = { ...cleaning, ...command.update, version: command.update.version + 1 };
            tasks = tasks.map((task) => task.id === command.taskId ? saved : task);
            return { data: saved };
        });
        open(); fireEvent.click(await screen.findByRole('button', { name: /Zimmer 101 · Reinigung/ }));
        fireEvent.click(screen.getByRole('checkbox', { name: 'Aufgaben auf diesem Gerät für Offline-Arbeit speichern' }));
        online.mockReturnValue(false); fireEvent(window, new Event('offline'));
        fireEvent.change(screen.getByLabelText('Arbeitsstatus'), { target: { value: 'IN_PROGRESS' } });
        fireEvent.click(screen.getByRole('button', { name: 'Aufgabe speichern' }));
        await screen.findByText('Änderung auf diesem Gerät vorgemerkt. Sie ist noch nicht im Hotel gespeichert.');
        expect(apiMock.post).not.toHaveBeenCalled();
        const queued = readOffline(offlineScope(5)).queue[0];
        online.mockReturnValue(true); fireEvent(window, new Event('online'));
        await screen.findByText('Vorgemerkte Änderung für Zimmer 101 im Hotel gespeichert.');
        expect(apiMock.post).toHaveBeenCalledOnce();
        expect(apiMock.post).toHaveBeenCalledWith('/api/pms/properties/5/housekeeping/offline-commands', { commandId: queued.commandId, taskId: 10, update: queued.update });
        expect(readOffline(offlineScope(5)).queue).toHaveLength(0);
        expect(screen.queryByText('Änderung auf diesem Gerät vorgemerkt. Sie ist noch nicht im Hotel gespeichert.')).toBeNull();
    });
    it('shows independent task kinds and creates an explicitly assigned evening service',async () => {
        open();await screen.findByRole('button',{name:/Zimmer 101 · Reinigung/});
        expect(screen.getByRole('button',{name:/Zimmer 101 · Kontrolle/})).toBeTruthy();expect(screen.getByRole('button',{name:/Zimmer 101 · Abendservice/})).toBeTruthy();
        fireEvent.change(screen.getByLabelText('Zimmer'),{target:{value:'8'}});fireEvent.change(screen.getByLabelText('Arbeitsart'),{target:{value:'TURNDOWN'}});fireEvent.change(screen.getByLabelText('Zugewiesen an'),{target:{value:'Abendteam'}});
        fireEvent.click(screen.getByRole('button',{name:'Aufgabe speichern'}));
        await waitFor(() => expect(apiMock.post).toHaveBeenCalledWith('/api/pms/properties/5/housekeeping/work-orders',expect.objectContaining({roomId:8,serviceDate:'2026-09-12',workType:'TURNDOWN',assignedTo:'Abendteam'})));
    });
    it('preserves a deferred assignment draft after conflict and retries only with acknowledged current version',async () => {
        apiMock.put.mockImplementationOnce(() => {tasks=[{...cleaning,version:3,assignedTo:'Ben',workStatus:'IN_PROGRESS'}];return Promise.reject({response:{status:409,data:{detail:'Zwischenzeitlich geändert'}}});});
        open();fireEvent.click(await screen.findByRole('button',{name:/Zimmer 101 · Reinigung/}));
        fireEvent.change(screen.getByLabelText('Arbeitsstatus'),{target:{value:'DEFERRED'}});fireEvent.change(screen.getByLabelText('Zugewiesen an'),{target:{value:'Clara'}});fireEvent.change(screen.getByLabelText('Arbeitsnotiz'),{target:{value:'Nach Rückkehr reinigen'}});
        fireEvent.click(screen.getByRole('button',{name:'Aufgabe speichern'}));
        await screen.findByText(/Aktueller Stand: In Arbeit, Ben, Version 3/);
        expect(screen.getByLabelText('Arbeitsnotiz').value).toBe('Nach Rückkehr reinigen');expect(screen.getByLabelText('Zugewiesen an').value).toBe('Clara');
        expect(screen.getByRole('button',{name:'Aufgabe speichern'}).disabled).toBe(true);
        fireEvent.click(screen.getByRole('button',{name:'Aktuellen Stand übernehmen, Eingaben behalten'}));fireEvent.click(screen.getByRole('button',{name:'Aufgabe speichern'}));
        await waitFor(() => expect(apiMock.put).toHaveBeenLastCalledWith('/api/pms/properties/5/housekeeping/work-orders/10',expect.objectContaining({version:3,workStatus:'DEFERRED',assignedTo:'Clara',notes:'Nach Rückkehr reinigen'})));
    });
    it('paginates the task history and disables edits for viewing staff',async () => {
        render(<PmsHousekeepingWorkspace property={property} rooms={rooms} businessDate="2026-09-12" canManage={false} />);
        fireEvent.click(await screen.findByRole('button',{name:/Zimmer 101 · Reinigung/}));
        await waitFor(() => expect(screen.getByRole('button',{name:'Verlauf weiter'}).disabled).toBe(false));
        fireEvent.click(screen.getByRole('button',{name:'Verlauf weiter'}));
        await waitFor(() => expect(apiMock.get).toHaveBeenCalledWith('/api/pms/properties/5/housekeeping/work-orders/10/history',expect.objectContaining({params:{page:1,size:10}})));
        expect(screen.getByLabelText('Zugewiesen an').closest('fieldset').disabled).toBe(true);
    });
});
