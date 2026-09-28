package com.eventease.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "college_admins")
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class CollegeAdmin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false, unique = true, length = 50)
    private String adminId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @OneToMany(mappedBy = "collegeAdmin", fetch = FetchType.LAZY)
    @JsonManagedReference("admin-events")
    private List<CampusEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "collegeAdmin", fetch = FetchType.LAZY)
    @JsonManagedReference("admin-refresh-tokens")
    private List<RefreshToken> refreshTokens = new ArrayList<>();
}
