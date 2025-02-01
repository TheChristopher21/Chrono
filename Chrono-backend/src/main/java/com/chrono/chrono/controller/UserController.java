// src/main/java/com/chrono/chrono/controller/UserController.java
package com.chrono.chrono.controller;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PutMapping("/update")
    public User updateUser(@RequestBody User updatedUser) {
        return userService.updateUser(updatedUser);
    }
}
