package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CustomerContact;

public class CustomerContactDTO {
    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String role;

    public CustomerContactDTO(Long id, String firstName, String lastName, String email, String phone, String role) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }

    public static CustomerContactDTO from(CustomerContact contact) {
        return new CustomerContactDTO(
                contact.getId(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getRoleTitle());
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getRole() {
        return role;
    }
}
