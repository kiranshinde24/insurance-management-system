package com.insurance.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "Customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Customer_ID")
    private Integer id;

    @Column(name = "Full_Name", nullable = false, length = 100)
    private String name;

    @Column(name = "DOB", nullable = false)
    private LocalDate dob;

    @Column(name = "PAN_Number", unique = true, length = 10)
    private String pan;

    @Column(name = "Phone", length = 15)
    private String phone;

    @Column(name = "Email", length = 100)
    private String email;

    // Maps to 'city' concept from frontend (Address column in DB)
    @Column(name = "Address", length = 200)
    private String city;

    // Link to the agent chosen at registration (stored in Address or separate)
    @Column(name = "Agent_ID")
    private Integer agentId;

    // Username for login (store in Users table, but link here for convenience)
    @Column(name = "Username", length = 50)
    private String username;

    // Password (hashed)
    @Column(name = "Password", length = 255)
    private String password;
}
