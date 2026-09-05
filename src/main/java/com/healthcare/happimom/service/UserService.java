package com.healthcare.happimom.service;

import com.healthcare.happimom.dto.AuthResponseDTO;
import com.healthcare.happimom.dto.LoginDTO;
import com.healthcare.happimom.dto.RegisterDTO;
import com.healthcare.happimom.dto.UserProfileDTO;
import com.healthcare.happimom.entity.User;

public interface UserService {

    AuthResponseDTO register(RegisterDTO registerDTO);

    AuthResponseDTO login(LoginDTO loginDTO);

    User completeProfile(Long userId, UserProfileDTO profileDTO);

    User getUserById(Long userId);
}
