package com.zipai.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true, length = 20)
    public String username;
    @Column(nullable = false, unique = true, length = 254)
    public String email;
    @Column(nullable = false, length = 11)
    public String phone;
    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;
    @Column(nullable = false, length = 20)
    public String role = "member";
    @Column(nullable = false, length = 20)
    public String status = "active";
}
