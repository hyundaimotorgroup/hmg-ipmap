package com.hmg.ipmap.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository
        extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByApiKey(String apiKey);

    Optional<UserEntity> findByName(String name);

    @EntityGraph(attributePaths = {"parent"})
    Page<UserEntity> findAll(Specification<UserEntity> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"parent"})
    Optional<UserEntity> findByApiKeyAndSourceIp(String apiKey, String sourceIp);

    Optional<UserEntity> findByApiKeyAndParentIsNull(String apiKey);
}
