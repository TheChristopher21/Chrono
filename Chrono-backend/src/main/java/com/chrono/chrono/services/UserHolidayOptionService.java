package com.chrono.chrono.services;

import com.chrono.chrono.dto.UserHolidayOptionDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserHolidayOption;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserHolidayOptionRepository;
import com.chrono.chrono.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserHolidayOptionService {

    private static final Logger logger = LoggerFactory.getLogger(UserHolidayOptionService.class);

    @Autowired
    private UserHolidayOptionRepository userHolidayOptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private TimeTrackingService timeTrackingService;


    @Transactional
    public UserHolidayOptionDTO getOrCreateHolidayOption(String username, LocalDate date) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            throw new IllegalArgumentException("Holiday options are only applicable to percentage-based users.");
        }

        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
        if (!holidayService.isHoliday(date, cantonAbbreviation)) {
            // Keine Option erstellen/zurückgeben, wenn es kein Feiertag ist
            return new UserHolidayOptionDTO(null, user.getId(), username, date, UserHolidayOption.HolidayHandlingOption.PENDING_DECISION);
        }

        Optional<UserHolidayOption> existingOption = userHolidayOptionRepository.findByUserAndHolidayDate(user, date);

        if (existingOption.isPresent()) {
            return UserHolidayOptionDTO.fromEntity(existingOption.get());
        } else {
            UserHolidayOption newOption = new UserHolidayOption(user, date, UserHolidayOption.HolidayHandlingOption.PENDING_DECISION);
            UserHolidayOption savedOption = userHolidayOptionRepository.save(newOption);
            logger.info("Created new UserHolidayOption for user {} on {} with PENDING_DECISION", username, date);
            return UserHolidayOptionDTO.fromEntity(savedOption);
        }
    }

    @Transactional(readOnly = true)
    public List<UserHolidayOptionDTO> getHolidayOptionsForUserAndWeek(String username, LocalDate mondayInWeek) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            return List.of(); // Nur für prozentuale User relevant
        }

        LocalDate endOfWeek = mondayInWeek.plusDays(6);
        List<UserHolidayOption> options = userHolidayOptionRepository.findByUserAndHolidayDateBetween(user, mondayInWeek, endOfWeek);

        // Erstelle DTOs für existierende Optionen und füge PENDING_DECISION für fehlende Feiertage hinzu
        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
        List<UserHolidayOptionDTO> result = options.stream().map(UserHolidayOptionDTO::fromEntity).collect(Collectors.toList());

        for (LocalDate date = mondayInWeek; !date.isAfter(endOfWeek); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            boolean isRealHoliday = holidayService.isHoliday(currentDate, cantonAbbreviation);
            if (isRealHoliday) {
                boolean found = result.stream().anyMatch(dto -> dto.getHolidayDate().equals(currentDate));
                if (!found) {
                    // Erstelle direkt eine "virtuelle" PENDING Option, wenn keine explizite gespeichert ist.
                    // Diese wird nicht gespeichert, sondern dient nur der Anzeige.
                    result.add(new UserHolidayOptionDTO(null, user.getId(), username, currentDate, UserHolidayOption.HolidayHandlingOption.PENDING_DECISION));
                }
            }
        }
        result.sort((d1, d2) -> d1.getHolidayDate().compareTo(d2.getHolidayDate()));
        return result;
    }


    @Transactional
    public UserHolidayOptionDTO setHolidayOption(String username, LocalDate date, UserHolidayOption.HolidayHandlingOption optionValue, String adminUsername) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new UserNotFoundException("Admin user not found: " + adminUsername));

        if (!Boolean.TRUE.equals(user.getIsPercentage())) {
            throw new IllegalArgumentException("Holiday options are only applicable to percentage-based users.");
        }

        String cantonAbbreviation = user.getCompany() != null ? user.getCompany().getCantonAbbreviation() : null;
        if (!holidayService.isHoliday(date, cantonAbbreviation)) {
            throw new IllegalArgumentException("Date " + date + " is not a holiday for user " + username + " in canton " + cantonAbbreviation);
        }

        // Sicherheitscheck: Admin muss in derselben Firma sein oder SuperAdmin
        if (!admin.getRoles().stream().anyMatch(r -> r.getRoleName().equals("ROLE_SUPERADMIN"))) {
            if (admin.getCompany() == null || user.getCompany() == null || !admin.getCompany().getId().equals(user.getCompany().getId())) {
                throw new SecurityException("Admin " + adminUsername + " is not authorized to set holiday options for user " + username);
            }
        }

        UserHolidayOption option = userHolidayOptionRepository.findByUserAndHolidayDate(user, date)
                .orElseGet(() -> new UserHolidayOption(user, date, optionValue));

        option.setHolidayHandlingOption(optionValue);
        UserHolidayOption savedOption = userHolidayOptionRepository.save(option);
        logger.info("Admin {} set holiday option for user {} on {} to {}", adminUsername, username, date, optionValue);

        // Saldo neu berechnen, nachdem die Option geändert wurde
        timeTrackingService.rebuildUserBalance(user);

        return UserHolidayOptionDTO.fromEntity(savedOption);
    }
}