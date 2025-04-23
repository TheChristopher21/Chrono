package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.exceptions.UserNotFoundException;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        // 🛡️ TrackingBalance sicherstellen (niemals null)
        if (user.getTrackingBalanceInMinutes() == null) {
            user.setTrackingBalanceInMinutes(0);
        }

        return user;
    }

    public User updateUser(User updatedUser) {
        User user = userRepository.findByUsername(updatedUser.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + updatedUser.getUsername()));

        user.setFirstName(updatedUser.getFirstName());
        user.setLastName(updatedUser.getLastName());
        user.setEmail(updatedUser.getEmail());

        if (updatedUser.getAnnualVacationDays() != null) {
            user.setAnnualVacationDays(updatedUser.getAnnualVacationDays());
        }

        if (updatedUser.getIsHourly() != null) {
            user.setIsHourly(updatedUser.getIsHourly());
        }
        if (updatedUser.getIsPercentage() != null) {
            user.setIsPercentage(updatedUser.getIsPercentage());
        }
        if (updatedUser.getWorkPercentage() != null) {
            user.setWorkPercentage(updatedUser.getWorkPercentage());
        }

        // ✅ Überstunden absichern
        if (updatedUser.getTrackingBalanceInMinutes() == null) {
            user.setTrackingBalanceInMinutes(0);
        } else {
            user.setTrackingBalanceInMinutes(updatedUser.getTrackingBalanceInMinutes());
        }

        return userRepository.save(user);
    }
}
