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
    private String husbandName;
    private String husbandContact;
    private String parentName;
    private String parentContact;
    private String marriageDate;
    private String pregnancyDate;

    private String bloodGroup;
    private Double height;
    private Double weight;
    private String emergencyContact;
    private String address;
    private String city;
    private String state;
    private String country;
    private String zipCode;

    private String medicalConditions;
    private String allergies;
}
