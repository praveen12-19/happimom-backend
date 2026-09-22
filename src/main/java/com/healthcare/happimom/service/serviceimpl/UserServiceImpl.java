package com.healthcare.happimom.service.serviceimpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.happimom.dto.AuthResponseDTO;
import com.healthcare.happimom.dto.LoginDTO;
import com.healthcare.happimom.dto.RegisterDTO;
import com.healthcare.happimom.dto.UserProfileDTO;
import com.healthcare.happimom.entity.*;
import com.healthcare.happimom.exception.UserAlreadyExistsException;
import com.healthcare.happimom.exception.UserNotFoundException;
import com.healthcare.happimom.repository.*;
import com.healthcare.happimom.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final MotherDetailRepository motherDetailRepository;
    private final PartnerDetailRepository partnerDetailRepository;
    private final ParentDetailRepository parentDetailRepository;
    private final PregnancyTimelineRepository pregnancyTimelineRepository;
    private final DoctorClinicSupportRepository doctorClinicSupportRepository;
    private final ChildrenDetailRepository childrenDetailRepository;
    private final FileStorageRepository fileStorageRepository;
    private final ObjectMapper objectMapper;

    public UserServiceImpl(UserRepository userRepository,
                           MotherDetailRepository motherDetailRepository,
                           PartnerDetailRepository partnerDetailRepository,
                           ParentDetailRepository parentDetailRepository,
                           PregnancyTimelineRepository pregnancyTimelineRepository,
                           DoctorClinicSupportRepository doctorClinicSupportRepository,
                           ChildrenDetailRepository childrenDetailRepository,
                           FileStorageRepository fileStorageRepository,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.motherDetailRepository = motherDetailRepository;
        this.partnerDetailRepository = partnerDetailRepository;
        this.parentDetailRepository = parentDetailRepository;
        this.pregnancyTimelineRepository = pregnancyTimelineRepository;
        this.doctorClinicSupportRepository = doctorClinicSupportRepository;
        this.childrenDetailRepository = childrenDetailRepository;
        this.fileStorageRepository = fileStorageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthResponseDTO register(RegisterDTO registerDTO) {
        if (userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + registerDTO.getEmail() + " already exists");
        }

        User user = new User();
        user.setEmail(registerDTO.getEmail());
        user.setPassword(registerDTO.getPassword());
        user.setName(registerDTO.getEmail().split("@")[0]);
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

        // 1. Core User fields
        if (profileDTO.getName() != null && !profileDTO.getName().isBlank()) {
            user.setName(profileDTO.getName().trim());
        }
        user.setProfileComplete(true);

        // 2. MotherDetail
        MotherDetail mother = motherDetailRepository.findByUserId(userId).orElse(new MotherDetail());
        mother.setUser(user);
        if (profileDTO.getMobileNumber() != null) mother.setMobileNumber(profileDTO.getMobileNumber().trim());
        if (profileDTO.getDob() != null) mother.setDob(profileDTO.getDob());
        if (profileDTO.getAge() != null) {
            mother.setAge(profileDTO.getAge());
        } else if (profileDTO.getDob() != null && !profileDTO.getDob().isBlank()) {
            try {
                java.time.LocalDate birthDate = java.time.LocalDate.parse(profileDTO.getDob().trim());
                mother.setAge(java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears());
            } catch (Exception ignored) {}
        }
        if (profileDTO.getBloodGroup() != null) mother.setBloodGroup(profileDTO.getBloodGroup());
        if (profileDTO.getAddress() != null) mother.setAddress(profileDTO.getAddress().trim());
        if (profileDTO.getCity() != null) mother.setCity(profileDTO.getCity().trim());
        if (profileDTO.getHasChildren() != null) mother.setHasChildren(profileDTO.getHasChildren());
        if (profileDTO.getChildrenCount() != null) mother.setChildrenCount(profileDTO.getChildrenCount());
        mother = motherDetailRepository.save(mother);
        user.setMotherDetails(mother);

        // 3. PartnerDetail
        PartnerDetail partner = partnerDetailRepository.findByUserId(userId).orElse(new PartnerDetail());
        partner.setUser(user);
        if (profileDTO.getHusbandName() != null) partner.setPartnerName(profileDTO.getHusbandName().trim());
        if (profileDTO.getHusbandContact() != null) partner.setPartnerContact(profileDTO.getHusbandContact().trim());
        partner = partnerDetailRepository.save(partner);
        user.setPartnerDetails(partner);

        // 4. ParentDetail
        ParentDetail parent = parentDetailRepository.findByUserId(userId).orElse(new ParentDetail());
        parent.setUser(user);
        if (profileDTO.getParentName() != null) parent.setParentName(profileDTO.getParentName().trim());
        if (profileDTO.getParentContact() != null) parent.setParentContact(profileDTO.getParentContact().trim());
        parent = parentDetailRepository.save(parent);
        user.setParentDetails(parent);

        // 5. PregnancyTimeline
        PregnancyTimeline pregnancy = pregnancyTimelineRepository.findByUserId(userId).orElse(new PregnancyTimeline());
        pregnancy.setUser(user);
        if (profileDTO.getPregnancyDate() != null) pregnancy.setPregnancyDate(profileDTO.getPregnancyDate());
        if (profileDTO.getMedicalConditions() != null) pregnancy.setMedicalConditions(profileDTO.getMedicalConditions().trim());
        if (profileDTO.getAllergies() != null) pregnancy.setAllergies(profileDTO.getAllergies().trim());
        pregnancy = pregnancyTimelineRepository.save(pregnancy);
        user.setPregnancyTimeline(pregnancy);

        // 6. DoctorClinicSupport
        DoctorClinicSupport doctor = doctorClinicSupportRepository.findByUserId(userId).orElse(new DoctorClinicSupport());
        doctor.setUser(user);
        if (profileDTO.getEmergencyContact() != null) doctor.setDoctorName(profileDTO.getEmergencyContact().trim());
        if (profileDTO.getDoctorPhone() != null) doctor.setDoctorPhone(profileDTO.getDoctorPhone().trim());
        if (profileDTO.getDoctorAddress() != null) doctor.setDoctorAddress(profileDTO.getDoctorAddress().trim());
        doctor = doctorClinicSupportRepository.save(doctor);
        user.setDoctorClinicSupport(doctor);

        // 7. Children Details
        if (Boolean.TRUE.equals(profileDTO.getHasChildren()) && profileDTO.getChildrenDetails() != null && !profileDTO.getChildrenDetails().isBlank()) {
            try {
                List<Map<String, String>> childList = objectMapper.readValue(
                        profileDTO.getChildrenDetails(),
                        new TypeReference<List<Map<String, String>>>() {}
                );
                childrenDetailRepository.deleteByUserId(userId);
                List<ChildrenDetail> newChildren = new ArrayList<>();
                for (Map<String, String> cMap : childList) {
                    ChildrenDetail cd = new ChildrenDetail();
                    cd.setUser(user);
                    cd.setName(cMap.getOrDefault("name", ""));
                    cd.setAge(cMap.getOrDefault("age", ""));
                    cd.setDob(cMap.getOrDefault("dob", ""));
                    cd.setGender(cMap.getOrDefault("gender", ""));
                    newChildren.add(childrenDetailRepository.save(cd));
                }
                user.setChildrenDetails(newChildren);
            } catch (Exception e) {
                log.warn("Could not parse childrenDetails: {}", e.getMessage());
            }
        }

        // 8. Medical Documents (FileStorage)
        if (profileDTO.getMedicalDocuments() != null && !profileDTO.getMedicalDocuments().isBlank()) {
            try {
                List<Map<String, Object>> files = objectMapper.readValue(
                        profileDTO.getMedicalDocuments(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                List<FileStorage> fileList = new ArrayList<>();
                for (Map<String, Object> fMap : files) {
                    FileStorage fs = new FileStorage();
                    fs.setUser(user);
                    fs.setFileName((String) fMap.getOrDefault("name", "Document"));
                    fs.setFileUrl((String) fMap.getOrDefault("url", ""));
                    fs.setFileType((String) fMap.getOrDefault("type", "application/pdf"));
                    fs.setStorage((String) fMap.getOrDefault("storage", "local"));
                    fs.setTopic((String) fMap.getOrDefault("topic", "Medical Document"));
                    fs.setUploadedAt((String) fMap.getOrDefault("uploadedAt", String.valueOf(System.currentTimeMillis())));
                    fileList.add(fileStorageRepository.save(fs));
                }
                user.setFileStorages(fileList);
            } catch (Exception e) {
                log.warn("Could not parse medicalDocuments: {}", e.getMessage());
            }
        }

        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        // Load relational mappings eagerly
        motherDetailRepository.findByUserId(userId).ifPresent(user::setMotherDetails);
        partnerDetailRepository.findByUserId(userId).ifPresent(user::setPartnerDetails);
        parentDetailRepository.findByUserId(userId).ifPresent(user::setParentDetails);
        pregnancyTimelineRepository.findByUserId(userId).ifPresent(user::setPregnancyTimeline);
        doctorClinicSupportRepository.findByUserId(userId).ifPresent(user::setDoctorClinicSupport);
        user.setChildrenDetails(childrenDetailRepository.findByUserId(userId));
        user.setFileStorages(fileStorageRepository.findByUserId(userId));

        return user;
    }
}
