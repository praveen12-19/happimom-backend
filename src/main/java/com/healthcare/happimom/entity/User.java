package com.healthcare.happimom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean profileComplete = false;

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

    @Column(length = 1000)
    private String medicalConditions;

    @Column(length = 1000)
    private String allergies;
}
