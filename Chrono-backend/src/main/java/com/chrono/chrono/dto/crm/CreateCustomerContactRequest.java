package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CustomerContact;

public class CreateCustomerContactRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String role;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public CustomerContact toEntity() {
        CustomerContact contact = new CustomerContact();
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail(email);
        contact.setPhone(phone);
        contact.setRoleTitle(role);
        return contact;
    }
}
