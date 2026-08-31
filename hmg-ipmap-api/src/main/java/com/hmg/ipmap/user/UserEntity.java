package com.hmg.ipmap.user;

import com.hmg.ipmap.common.entity.AuditableEntity;
import com.hmg.ipmap.common.enums.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "\"user\"")
public class UserEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    private UserEntity parent;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_ip")
    private String sourceIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    private UserType userType;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_template")
    private UserResponseTemplateEnum responseTemplate;
}
