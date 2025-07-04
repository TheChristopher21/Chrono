package com.chrono.chrono.services;

import com.chrono.chrono.dto.DashboardAnalyticsDTO;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardAnalyticsService {

    @Autowired
    private UserRepository userRepository;

    public List<DashboardAnalyticsDTO> getCompanyOvertimes(Long companyId) {
        return userRepository.findByCompany_Id(companyId).stream()
                .map(u -> new DashboardAnalyticsDTO(u.getUsername(), u.getTrackingBalanceInMinutes()))
                .collect(Collectors.toList());
    }
}
