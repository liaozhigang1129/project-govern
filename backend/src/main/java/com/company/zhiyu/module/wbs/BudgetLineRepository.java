package com.company.zhiyu.module.wbs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {
    List<BudgetLine> findByProjectIdAndDeletedFalseOrderByCategory(Long projectId);
}
