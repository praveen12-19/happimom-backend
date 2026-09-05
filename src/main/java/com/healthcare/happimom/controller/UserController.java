package com.healthcare.happimom.controller;

import com.healthcare.happimom.dto.UserProfileDTO;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        // Do not leak password to client
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<User> completeProfile(@PathVariable Long id, @RequestBody UserProfileDTO profileDTO) {
        User updatedUser = userService.completeProfile(id, profileDTO);
        updatedUser.setPassword(null);
        return ResponseEntity.ok(updatedUser);
    }
}
