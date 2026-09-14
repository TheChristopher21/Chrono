import { useEffect, useLayoutEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
    normalizeRefreshScopes,
    REFRESH_SCOPES,
    refreshScopesMatch,
    subscribeDataRefresh,
} from '../utils/dataRefresh.js';

export const shouldInvalidateQueryForRefresh = (query, changedScopes) => {
    const normalizedChanges = normalizeRefreshScopes(changedScopes);
    if (normalizedChanges.length === 0) return false;

    const queryScopes = normalizeRefreshScopes(query?.meta?.refreshScopes);
    if (queryScopes.length === 0) {
        return normalizedChanges.includes(REFRESH_SCOPES.GLOBAL);
    }
    return refreshScopesMatch(queryScopes, normalizedChanges);
};

const DataRefreshQueryBridge = ({ identity = 'signed-out' }) => {
    const queryClient = useQueryClient();
    const previousIdentityRef = useRef(identity);
    const identityGuardMountedRef = useRef(false);

    useLayoutEffect(() => {
        if (!identityGuardMountedRef.current) {
            identityGuardMountedRef.current = true;
            previousIdentityRef.current = identity;
            return;
        }
        if (previousIdentityRef.current !== identity) {
            queryClient.clear();
            previousIdentityRef.current = identity;
        }
    }, [identity, queryClient]);

    useEffect(() => subscribeDataRefresh((event) => {
        void queryClient.invalidateQueries({
            predicate: (query) => shouldInvalidateQueryForRefresh(query, event.scopes),
            refetchType: 'active',
        }).catch((error) => {
            console.error('React Query data refresh failed', error);
        });
    }), [queryClient]);

    return null;
};

export default DataRefreshQueryBridge;
