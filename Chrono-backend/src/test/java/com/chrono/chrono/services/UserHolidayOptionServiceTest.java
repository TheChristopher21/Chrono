package com.chrono.chrono.services;

import com.chrono.chrono.dto.UserHolidayOptionDTO;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserHolidayOption;
import com.chrono.chrono.repositories.UserHolidayOptionRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserHolidayOptionServiceTest {

    @Mock
    private UserHolidayOptionRepository userHolidayOptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HolidayService holidayService;

    @Mock
    private TimeTrackingService timeTrackingService;

    @InjectMocks
    private UserHolidayOptionService userHolidayOptionService;

    @Test
    void setHolidayOption_keepsSavedDeductionChoiceForLegacyStGallenHoliday() {
        Company company = new Company("Chrono AG");
        company.setId(1L);
        company.setCantonAbbreviation("SG");
        company.setCustomHolidaySelectionEnabled(false);

        User user = new User();
        user.setId(2L);
        user.setUsername("gabriela");
        user.setCompany(company);
        user.setIsPercentage(true);

        User admin = new User();
        admin.setId(3L);
        admin.setUsername("admin");
        admin.setCompany(company);

        LocalDate holiday = LocalDate.of(2026, 1, 2);
        UserHolidayOption existingOption = new UserHolidayOption(
                user,
                holiday,
                UserHolidayOption.HolidayHandlingOption.PENDING_DECISION
        );

        when(userRepository.findByUsername("gabriela")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(holidayService.isHoliday(holiday, "SG")).thenReturn(true);
        when(userHolidayOptionRepository.findByUserAndHolidayDate(user, holiday)).thenReturn(Optional.of(existingOption));
        when(userHolidayOptionRepository.save(existingOption)).thenReturn(existingOption);

        UserHolidayOptionDTO dto = userHolidayOptionService.setHolidayOption(
                "gabriela",
                holiday,
                UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET,
                "admin"
        );

        assertEquals(UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET, dto.getHolidayHandlingOption());
        assertEquals(UserHolidayOption.HolidayHandlingOption.DO_NOT_DEDUCT_FROM_WEEKLY_TARGET, existingOption.getHolidayHandlingOption());
        verify(userHolidayOptionRepository).save(existingOption);
        verify(timeTrackingService).rebuildUserBalance(user);
    }
}
