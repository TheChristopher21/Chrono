package com.chrono.chrono.entities.listeners;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserAudit;
import com.chrono.chrono.repositories.UserAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserAuditListener {
    private static final Map<Long, User> PREVIOUS = new HashMap<>();

    private static UserAuditRepository repo;

    @Autowired
    public void init(UserAuditRepository r) {
        repo = r;
    }

    @PostLoad
    public void postLoad(User user) {
        PREVIOUS.put(user.getId(), copy(user));
    }

    @PreUpdate
    public void preUpdate(User user) {
        User old = PREVIOUS.get(user.getId());
        if (old != null) {
            if (diff(old.getBankAccount(), user.getBankAccount())) {
                repo.save(new UserAudit(user, "bankAccount", old.getBankAccount(), user.getBankAccount()));
            }
            if (diff(old.getSocialSecurityNumber(), user.getSocialSecurityNumber())) {
                repo.save(new UserAudit(user, "socialSecurityNumber", old.getSocialSecurityNumber(), user.getSocialSecurityNumber()));
            }
            if (diff(old.getEmail(), user.getEmail())) {
                repo.save(new UserAudit(user, "email", old.getEmail(), user.getEmail()));
            }
        }
        PREVIOUS.put(user.getId(), copy(user));
    }

    private boolean diff(String a, String b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return !a.equals(b);
    }

    private User copy(User u) {
        User c = new User();
        c.setId(u.getId());
        c.setBankAccount(u.getBankAccount());
        c.setSocialSecurityNumber(u.getSocialSecurityNumber());
        c.setEmail(u.getEmail());
        return c;
    }
}
