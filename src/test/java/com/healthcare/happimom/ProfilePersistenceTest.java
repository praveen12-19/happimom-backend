package com.healthcare.happimom;

import com.healthcare.happimom.dto.UserProfileDTO;
import com.healthcare.happimom.entity.*;
import com.healthcare.happimom.repository.*;
import com.healthcare.happimom.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProfilePersistenceTest {

    @BeforeAll
    public static void setupEnv() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            Path backendEnv = Paths.get("Backend/.env");
            if (Files.exists(backendEnv)) {
                envPath = backendEnv;
            }
        }
        if (Files.exists(envPath)) {
            try {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int equalIdx = line.indexOf('=');
                    if (equalIdx > 0) {
                        String key = line.substring(0, equalIdx).trim();
                        String value = line.substring(equalIdx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MotherDetailRepository motherDetailRepository;

    @Autowired
    private PartnerDetailRepository partnerDetailRepository;

    @Autowired
    private ParentDetailRepository parentDetailRepository;

    @Autowired
    private PregnancyTimelineRepository pregnancyTimelineRepository;

    @Autowired
    private DoctorClinicSupportRepository doctorClinicSupportRepository;

    @Autowired
    private ChildrenDetailRepository childrenDetailRepository;

    @Autowired
    private FileStorageRepository fileStorageRepository;

    @Test
    @DisplayName("Verify that Quick Complete Profile stores data across all entity tables in MySQL")
    public void testCompleteProfileStoresDataInDatabase() {
        String testEmail = "test_mom_" + System.currentTimeMillis() + "@happimom.com";

        // 1. Create initial user (simulating register / auth modal)
        User user = new User();
        user.setEmail(testEmail);
        user.setPassword("Secret@123");
        user.setName("Test Mom");
        user.setProfileComplete(false);
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId(), "User should have generated ID");
        assertFalse(savedUser.isProfileComplete(), "Profile should initially be incomplete");

        // 2. Prepare payload matching Frontend OnboardingModal.jsx
        UserProfileDTO payload = new UserProfileDTO();
        payload.setName("Ananya Sharma");
        payload.setAge(28);
        payload.setDob("1998-05-15");
        payload.setCity("Bengaluru");
        payload.setMobileNumber("9876543210");
        payload.setAddress("123 Indiranagar, 2nd Stage");
        payload.setBloodGroup("O+");
        payload.setHasChildren(true);
        payload.setChildrenCount(1);
        payload.setChildrenDetails("[{\"name\":\"Aarav Sharma\",\"age\":\"2\",\"dob\":\"2024-01-10\",\"gender\":\"Boy\"}]");
        payload.setPregnancyDate("2026-02-14");
        payload.setHusbandName("Rohit Sharma");
        payload.setHusbandContact("9876543211");
        payload.setParentName("Sunita Devi");
        payload.setParentContact("9876543212");
        payload.setEmergencyContact("Dr. Priya Rao");
        payload.setDoctorPhone("9876543213");
        payload.setDoctorAddress("Cloudnine Hospital, Old Airport Road");
        payload.setMedicalConditions("Mild gestational nausea");
        payload.setAllergies("Peanuts");
        payload.setMedicalDocuments("[{\"name\":\"scan_report.pdf\",\"url\":\"https://res.cloudinary.com/dummy/scan_report.pdf\",\"type\":\"application/pdf\",\"size\":102400,\"storage\":\"cloudinary\",\"uploadedAt\":\"2026-09-07T08:00:00.000Z\"}]");

        // 3. Execute profile completion
        User updatedUser = userService.completeProfile(savedUser.getId(), payload);

        // 4. Verify User Table
        assertNotNull(updatedUser);
        assertTrue(updatedUser.isProfileComplete(), "profileComplete should be true");
        assertEquals("Ananya Sharma", updatedUser.getName());

        // 5. Verify MotherDetail Table
        Optional<MotherDetail> motherOpt = motherDetailRepository.findByUserId(savedUser.getId());
        assertTrue(motherOpt.isPresent(), "MotherDetail must be persisted in mother_details table");
        MotherDetail mother = motherOpt.get();
        assertEquals("9876543210", mother.getMobileNumber());
        assertEquals("1998-05-15", mother.getDob());
        assertEquals(Integer.valueOf(28), mother.getAge());
        assertEquals(Integer.valueOf(28), updatedUser.getAge());
        assertEquals("O+", mother.getBloodGroup());
        assertEquals("Bengaluru", mother.getCity());
        assertEquals("123 Indiranagar, 2nd Stage", mother.getAddress());
        assertTrue(mother.getHasChildren());
        assertEquals(1, mother.getChildrenCount());

        // 6. Verify PartnerDetail Table
        Optional<PartnerDetail> partnerOpt = partnerDetailRepository.findByUserId(savedUser.getId());
        assertTrue(partnerOpt.isPresent(), "PartnerDetail must be persisted in partner_details table");
        PartnerDetail partner = partnerOpt.get();
        assertEquals("Rohit Sharma", partner.getPartnerName());
        assertEquals("9876543211", partner.getPartnerContact());

        // 7. Verify ParentDetail Table
        Optional<ParentDetail> parentOpt = parentDetailRepository.findByUserId(savedUser.getId());
        assertTrue(parentOpt.isPresent(), "ParentDetail must be persisted in parent_details table");
        ParentDetail parent = parentOpt.get();
        assertEquals("Sunita Devi", parent.getParentName());
        assertEquals("9876543212", parent.getParentContact());

        // 8. Verify PregnancyTimeline Table
        Optional<PregnancyTimeline> pregOpt = pregnancyTimelineRepository.findByUserId(savedUser.getId());
        assertTrue(pregOpt.isPresent(), "PregnancyTimeline must be persisted in pregnancy_timeline table");
        PregnancyTimeline preg = pregOpt.get();
        assertEquals("2026-02-14", preg.getPregnancyDate());
        assertEquals("Mild gestational nausea", preg.getMedicalConditions());
        assertEquals("Peanuts", preg.getAllergies());

        // 9. Verify DoctorClinicSupport Table
        Optional<DoctorClinicSupport> docOpt = doctorClinicSupportRepository.findByUserId(savedUser.getId());
        assertTrue(docOpt.isPresent(), "DoctorClinicSupport must be persisted in doctor_clinic_support table");
        DoctorClinicSupport doc = docOpt.get();
        assertEquals("Dr. Priya Rao", doc.getDoctorName());
        assertEquals("9876543213", doc.getDoctorPhone());
        assertEquals("Cloudnine Hospital, Old Airport Road", doc.getDoctorAddress());

        // 10. Verify ChildrenDetail Table
        List<ChildrenDetail> children = childrenDetailRepository.findByUserId(savedUser.getId());
        assertEquals(1, children.size(), "ChildrenDetail must be persisted in children_details table");
        ChildrenDetail child = children.get(0);
        assertEquals("Aarav Sharma", child.getName());
        assertEquals("2", child.getAge());
        assertEquals("2024-01-10", child.getDob());
        assertEquals("Boy", child.getGender());

        // 11. Verify FileStorage Table
        List<FileStorage> files = fileStorageRepository.findByUserId(savedUser.getId());
        assertEquals(1, files.size(), "FileStorage must be persisted in file_storage table");
        FileStorage file = files.get(0);
        assertEquals("scan_report.pdf", file.getFileName());
        assertEquals("https://res.cloudinary.com/dummy/scan_report.pdf", file.getFileUrl());

        // 12. Verify Fetch by getUserById
        User fetched = userService.getUserById(savedUser.getId());
        assertNotNull(fetched.getMotherDetails());
        assertNotNull(fetched.getPartnerDetails());
        assertNotNull(fetched.getParentDetails());
        assertNotNull(fetched.getPregnancyTimeline());
        assertNotNull(fetched.getDoctorClinicSupport());
        assertEquals(1, fetched.getChildrenDetails().size());
        assertEquals(1, fetched.getFileStorages().size());
    }
}
