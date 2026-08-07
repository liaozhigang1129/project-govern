package com.hex.projectgovern.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTypeRepository extends JpaRepository<ProjectType, Long> {

    java.util.Optional<ProjectType> findByCode(String code);
    boolean existsByCode(String code);

}
