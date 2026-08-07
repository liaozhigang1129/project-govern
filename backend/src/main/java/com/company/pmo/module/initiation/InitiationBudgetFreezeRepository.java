package com.company.pmo.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InitiationBudgetFreezeRepository extends JpaRepository<InitiationBudgetFreeze, Long> {

    Optional<InitiationBudgetFreeze> findByInitiationIdAndDeletedFalse(Long initiationId);
}
