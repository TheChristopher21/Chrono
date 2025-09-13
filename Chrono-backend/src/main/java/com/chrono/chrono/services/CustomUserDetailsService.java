package com.chrono.chrono.services;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Cacheable(value = "userDetails", key = "#username")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Beim ersten Aufruf für diesen Username wird der User aus der Datenbank geladen.
        // Weitere Aufrufe innerhalb der Cache-TTL liefern dann den gecachten User.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (user.isDeleted()) {
            throw new UsernameNotFoundException("User not found");
        }
        List<SimpleGrantedAuthority> authorities = (user.getRoles() != null && !user.getRoles().isEmpty())
                ? user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleName()))
                .collect(Collectors.toList())
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), authorities);
    }
}
