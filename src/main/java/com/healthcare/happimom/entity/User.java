package com.healthcare.happimom.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"motherDetails", "partnerDetails", "parentDetails", "pregnancyTimeline", "doctorClinicSupport", "childrenDetails", "fileStorages", "memories", "appointments"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = false;

    // Relational Mappings to Child Tables
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private MotherDetail motherDetails;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private PartnerDetail partnerDetails;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private ParentDetail parentDetails;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private PregnancyTimeline pregnancyTimeline;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    private DoctorClinicSupport doctorClinicSupport;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ChildrenDetail> childrenDetails = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<FileStorage> fileStorages = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Memory> memories = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Appointment> appointments = new ArrayList<>();

    // Convenience backward-compatible getters for AI, Frontend, and existing services
    public String getPregnancyDate() {
        return pregnancyTimeline != null ? pregnancyTimeline.getPregnancyDate() : null;
    }

    public String getMedicalConditions() {
        return pregnancyTimeline != null ? pregnancyTimeline.getMedicalConditions() : null;
    }

    public String getAllergies() {
        return pregnancyTimeline != null ? pregnancyTimeline.getAllergies() : null;
    }

    public String getBloodGroup() {
        return motherDetails != null ? motherDetails.getBloodGroup() : null;
    }

    public String getCity() {
        return motherDetails != null ? motherDetails.getCity() : null;
    }

    public String getAddress() {
        return motherDetails != null ? motherDetails.getAddress() : null;
    }

    public String getMobileNumber() {
        return motherDetails != null ? motherDetails.getMobileNumber() : null;
    }

    public String getDob() {
        return motherDetails != null ? motherDetails.getDob() : null;
    }

    public Integer getAge() {
        return motherDetails != null ? motherDetails.getAge() : null;
    }

    public Boolean getHasChildren() {
        return motherDetails != null ? motherDetails.getHasChildren() : null;
    }

    public Integer getChildrenCount() {
        return motherDetails != null ? motherDetails.getChildrenCount() : null;
    }

    public String getHusbandName() {
        return partnerDetails != null ? partnerDetails.getPartnerName() : null;
    }

    public String getHusbandContact() {
        return partnerDetails != null ? partnerDetails.getPartnerContact() : null;
    }

    public String getParentName() {
        return parentDetails != null ? parentDetails.getParentName() : null;
    }

    public String getParentContact() {
        return parentDetails != null ? parentDetails.getParentContact() : null;
    }

    public String getEmergencyContact() {
        return doctorClinicSupport != null ? doctorClinicSupport.getDoctorName() : null;
    }

    public String getDoctorPhone() {
        return doctorClinicSupport != null ? doctorClinicSupport.getDoctorPhone() : null;
    }

    public String getDoctorAddress() {
        return doctorClinicSupport != null ? doctorClinicSupport.getDoctorAddress() : null;
    }
}
