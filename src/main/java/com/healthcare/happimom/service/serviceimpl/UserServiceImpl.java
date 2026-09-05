package com.healthcare.happimom.service.serviceimpl;

import com.healthcare.happimom.dto.AuthResponseDTO;
import com.healthcare.happimom.dto.LoginDTO;
import com.healthcare.happimom.dto.RegisterDTO;
import com.healthcare.happimom.dto.UserProfileDTO;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.exception.UserAlreadyExistsException;
import com.healthcare.happimom.exception.UserNotFoundException;
import com.healthcare.happimom.repository.UserRepository;
import com.healthcare.happimom.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthResponseDTO register(RegisterDTO registerDTO) {
        if (userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + registerDTO.getEmail() + " already exists");
        }

        User user = new User();
        user.setEmail(registerDTO.getEmail());
        user.setPassword(registerDTO.getPassword());
        user.setProfileComplete(false);

        User savedUser = userRepository.save(user);

        return new AuthResponseDTO(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.isProfileComplete(),
                "User registered successfully");
    }

    @Override
    public AuthResponseDTO login(LoginDTO loginDTO) {
        User user = userRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + loginDTO.getEmail()));

        if (!user.getPassword().equals(loginDTO.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return new AuthResponseDTO(
                user.getId(),
                user.getEmail(),
                user.isProfileComplete(),
                "Login successful");
    }

    @Override
    public User completeProfile(Long userId, UserProfileDTO profileDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setName(profileDTO.getName());
        user.setAge(profileDTO.getAge());
        user.setDob(profileDTO.getDob());
        user.setHusbandName(profileDTO.getHusbandName());
        user.setHusbandContact(profileDTO.getHusbandContact());
        user.setParentName(profileDTO.getParentName());
        user.setParentContact(profileDTO.getParentContact());
        user.setMarriageDate(profileDTO.getMarriageDate());
        user.setPregnancyDate(profileDTO.getPregnancyDate());

        user.setBloodGroup(profileDTO.getBloodGroup());
        user.setHeight(profileDTO.getHeight());
        user.setWeight(profileDTO.getWeight());
        user.setEmergencyContact(profileDTO.getEmergencyContact());
        user.setAddress(profileDTO.getAddress());
        user.setCity(profileDTO.getCity());
        user.setState(profileDTO.getState());
        user.setCountry(profileDTO.getCountry());
        user.setZipCode(profileDTO.getZipCode());

        user.setMedicalConditions(profileDTO.getMedicalConditions());
        user.setAllergies(profileDTO.getAllergies());

        user.setProfileComplete(true);

        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}
