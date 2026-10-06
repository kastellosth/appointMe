package com.kastellosth.appointme.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional = false)
    @JoinColumn(name="tenant_id",nullable = false)
    private Tenant tenant;

    @Column(name="full_name",nullable = false)
    private String fullName ;

    @Column(nullable = true)
    private String email ;

    @Column(nullable = true)
    private String phone ;

    @Column(name= "created_at", nullable = false,updatable = false)
    private Instant createdAt= Instant.now();
}