import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../utils/api';
import { CALCULATION_STATUS } from '../../components/CalculationStatusNotice.jsx';
import HourlyWeekOverview from './HourlyWeekOverview.jsx';

vi.mock('../../utils/api', () => ({
    default: {
        post: vi.fn(),
    },
}));

vi.mock('../../context/AuthContext.jsx', () => ({
    useAuth: () => ({
        currentUser: {
            username: 'hourly-user',
            customerTrackingEnabled: false,
        },
    }),
}));

const translate = (key, fallback) => fallback ?? key;

describe('HourlyWeekOverview', () => {
    beforeEach(() => {
        api.post.mockReset();
        api.post.mockResolvedValue({ data: {} });
    });

    it('notifies the dashboard state after saving a daily note', async () => {
        const user = userEvent.setup();
        const reloadData = vi.fn();
        const selectedMonday = new Date(2026, 7, 31);

        render(
            <HourlyWeekOverview
                t={translate}
                dailySummaries={[{
                    date: '2026-08-31',
                    workedMinutes: 60,
                    breakMinutes: 0,
                    entries: [{
                        id: 1,
                        entryTimestamp: '2026-08-31T08:00:00',
                        punchType: 'WORK_START',
                        source: 'MANUAL_PUNCH',
                        correctedByUser: false,
                    }],
                    dailyNote: 'Alt',
                    needsCorrection: false,
                }]}
                selectedMonday={selectedMonday}
                setSelectedMonday={vi.fn()}
                weeklyTotalMins={60}
                monthlyTotalMins={60}
                weekCalculationStatus={CALCULATION_STATUS.READY}
                monthCalculationStatus={CALCULATION_STATUS.READY}
                handleManualPunch={vi.fn()}
                punchMessage=""
                openCorrectionModal={vi.fn()}
                userProfile={{ username: 'hourly-user', customerTrackingEnabled: false }}
                customers={[]}
                recentCustomers={[]}
                projects={[]}
                tasks={[]}
                selectedCustomerId=""
                setSelectedCustomerId={vi.fn()}
                selectedProjectId=""
                setSelectedProjectId={vi.fn()}
                selectedTaskId=""
                setSelectedTaskId={vi.fn()}
                reloadData={reloadData}
                vacationRequests={[]}
            />,
        );

        await user.click(screen.getByRole('button', { name: 'Notiz bearbeiten' }));
        const editor = screen.getByPlaceholderText('Notiz eingeben...');
        await user.clear(editor);
        await user.type(editor, 'Neu gespeichert');
        await user.click(screen.getByRole('button', { name: 'Speichern' }));

        await waitFor(() => {
            expect(api.post).toHaveBeenCalledWith(
                '/api/timetracking/daily-note',
                { note: 'Neu gespeichert' },
                { params: { username: 'hourly-user', date: '2026-08-31' } },
            );
            expect(reloadData).toHaveBeenCalledWith({
                type: 'dailyNote',
                date: '2026-08-31',
                note: 'Neu gespeichert',
            });
        });
    });
});
