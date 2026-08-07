package com.company.pmo.module.cost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleCostDefaultRepository extends JpaRepository<RoleCostDefault, String> {

    List<RoleCostDefault> findAllByOrderBySortOrderAscCodeAsc();
}