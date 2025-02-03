// src/main/java/com/chrono/chrono/dto/UserDTO.java
package com.chrono.chrono.dto;

import java.util.List;

public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> roles;

    public UserDTO(Long id, String username, String firstName, String lastName, String email, List<String> roles) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roles = roles;
    }

    // Getter (und ggf. Setter, falls benötigt)
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
}
