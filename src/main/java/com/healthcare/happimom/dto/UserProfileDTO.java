package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private String name;
    private Integer age;
    private String dob;
    private String city;
    private String mobileNumber;
    private String address;
    private Boolean hasChildren;
    private Integer childrenCount;
    private String childrenDetails;
    private String pregnancyDate;
    private String bloodGroup;
    private String husbandName;
    private String husbandContact;
    private String parentName;
    private String parentContact;
    private String emergencyContact;
    private String doctorPhone;
    private String doctorAddress;
    private String medicalConditions;
    private String allergies;
    private String medicalDocuments;

    // Optional location fields
    private String state;
    private String country;
    private String zipCode;
    private Double height;
    private Double weight;
    private String marriageDate;
}
