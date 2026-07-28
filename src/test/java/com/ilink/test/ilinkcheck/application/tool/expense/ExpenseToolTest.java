package com.ilink.test.ilinkcheck.application.tool.expense;

import com.ilink.test.ilinkcheck.application.tool.categories.expense.Expense;
import com.ilink.test.ilinkcheck.application.tool.categories.expense.ExpenseRepository;
import com.ilink.test.ilinkcheck.application.tool.categories.expense.ExpenseTool;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

class ExpenseToolTest {

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final ExpenseTool expenseTool = new ExpenseTool(expenseRepository);

    @Test
    void shouldAddExpense() {
        Expense saved = new Expense(new BigDecimal("29.90"), "餐饮", "午餐", LocalDate.now());
        when(expenseRepository.save(any())).thenReturn(saved);

        String result = expenseTool.addExpense(new BigDecimal("29.90"), "餐饮", "午餐", null);
        assertThat(result).contains("29.90");
        assertThat(result).contains("餐饮");
        assertThat(result).contains("已记录");
    }

    @Test
    void shouldListExpenses() {
        Expense e1 = new Expense(new BigDecimal("15"), "交通", "地铁", LocalDate.now());
        Expense e2 = new Expense(new BigDecimal("50"), "餐饮", "晚餐", LocalDate.now());
        when(expenseRepository.findAllByOrderByDateDesc()).thenReturn(List.of(e1, e2));

        String result = expenseTool.listExpenses(null, null, null);
        assertThat(result).contains("交通");
        assertThat(result).contains("餐饮");
        assertThat(result).contains("65.00");
    }

    @Test
    void shouldReturnEmptyWhenNoExpenses() {
        when(expenseRepository.findAllByOrderByDateDesc()).thenReturn(List.of());
        assertThat(expenseTool.listExpenses(null, null, null)).isEqualTo("暂无支出记录。");
    }

    @Test
    void shouldSumByCategory() {
        when(expenseRepository.sumByCategory("餐饮")).thenReturn(new BigDecimal("200.00"));
        String result = expenseTool.sumExpenses("餐饮", null, null);
        assertThat(result).contains("餐饮");
        assertThat(result).contains("200.00");
    }

    @Test
    void shouldSumAll() {
        when(expenseRepository.sumByDateBetween(LocalDate.EPOCH, LocalDate.now().plusYears(100)))
                .thenReturn(new BigDecimal("1500.00"));
        String result = expenseTool.sumExpenses(null, null, null);
        assertThat(result).contains("全部");
        assertThat(result).contains("1500.00");
    }
}
