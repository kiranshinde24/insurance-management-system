package com.insurance.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Agent")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Agent_ID")
    private Integer id;

    @Column(name = "Agent_Name", nullable = false, length = 100)
    private String name;

    @Column(name = "Phone", length = 15)
    private String phone;

    @Column(name = "Email", length = 100)
    private String email;

    @Column(name = "Commission_Percentage")
    private Double comm;

    // Login credentials for agent portal
    @Column(name = "Username", length = 50, unique = true)
    private String username;

    @Column(name = "Password", length = 255)
    private String password;
}
