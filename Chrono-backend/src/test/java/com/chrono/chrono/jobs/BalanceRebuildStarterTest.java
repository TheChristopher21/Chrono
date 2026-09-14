package com.chrono.chrono.jobs;

import com.chrono.chrono.services.TimeTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BalanceRebuildStarterTest {

    @Test
    void onApplicationEvent_doesNotRebuildBalancesByDefault() {
        TimeTrackingService timeTrackingService = mock(TimeTrackingService.class);
        BalanceRebuildStarter starter = new BalanceRebuildStarter(timeTrackingService);
        ReflectionTestUtils.setField(starter, "balanceRebuildOnStartup", false);

        starter.onApplicationEvent(null);

        verifyNoInteractions(timeTrackingService);
    }

    @Test
    void onApplicationEvent_rebuildsBalancesWhenExplicitlyEnabled() {
        TimeTrackingService timeTrackingService = mock(TimeTrackingService.class);
        BalanceRebuildStarter starter = new BalanceRebuildStarter(timeTrackingService);
        ReflectionTestUtils.setField(starter, "balanceRebuildOnStartup", true);

        starter.onApplicationEvent(null);

        verify(timeTrackingService).rebuildAllUserBalancesOnce();
    }
}
