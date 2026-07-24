package com.ilink.test.ilinkcheck.application.tool.categories.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByDateDesc();

    List<Expense> findByCategoryOrderByDateDesc(String category);

    List<Expense> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);

    List<Expense> findByCategoryAndDateBetweenOrderByDateDesc(String category, LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.category = :category")
    BigDecimal sumByCategory(@Param("category") String category);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.date BETWEEN :start AND :end")
    BigDecimal sumByDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
