import { describe, expect, it } from 'vitest';
import {
    getDemoUsernameDisplayName,
    getUserDisplayName,
    getUserSearchText,
} from '../userDisplay.js';

describe('userDisplay', () => {
    it('prefers first and last name over technical usernames', () => {
        expect(getUserDisplayName({
            username: 'demo_5eb443f772fd_anna',
            firstName: 'Anna',
            lastName: 'Fischer',
        })).toBe('Anna Fischer');
    });

    it('formats known demo usernames when the user object is not loaded yet', () => {
        expect(getDemoUsernameDisplayName('demo_5eb443f772fd_ben')).toBe('Ben Keller');
        expect(getDemoUsernameDisplayName('demo_5eb443f772fd')).toBe('Demo Manager');
    });

    it('keeps the internal username searchable while displaying the friendly name', () => {
        const users = [{
            username: 'demo_5eb443f772fd_carla',
            firstName: 'Carla',
            lastName: 'Meier',
            department: 'Warehouse',
        }];

        const searchText = getUserSearchText('demo_5eb443f772fd_carla', users);

        expect(searchText).toContain('carla meier');
        expect(searchText).toContain('demo_5eb443f772fd_carla');
        expect(searchText).toContain('warehouse');
    });
});
