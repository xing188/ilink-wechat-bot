package com.ilink.test.ilinkcheck.application.tool.categories.expense;

import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Component
public class ExpenseTool implements ChatFunctionTool {

    private final ExpenseRepository expenseRepository;

    public ExpenseTool(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Tool(name = "addExpense", description = "记录一笔支出，包含金额、类别和说明。类别可自由填写，如餐饮、交通、购物、娱乐等。")
    @Transactional
    public String addExpense(
            @ToolParam(required = true, description = "支出金额，如 29.9、100、15.5")
            BigDecimal amount,
            @ToolParam(required = true, description = "支出类别，如餐饮、交通、购物、娱乐、日用品等")
            String category,
            @ToolParam(required = false, description = "支出说明，可选")
            String description,
            @ToolParam(required = false, description = "日期（yyyy-MM-dd），不传则默认为今天")
            String date) {

        LocalDate expenseDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        Expense expense = expenseRepository.save(
                new Expense(amount, category, description != null ? description : "", expenseDate));
        return String.format("💰 已记录支出：%.2f元（%s）[#%d]", expense.getAmount(), expense.getCategory(), expense.getId());
    }

    @Tool(name = "listExpenses", description = "查看支出记录。可按类别、日期范围筛选。")
    public String listExpenses(
            @ToolParam(required = false, description = "支出类别筛选，如餐饮、交通，不传则查全部")
            String category,
            @ToolParam(required = false, description = "起始日期（yyyy-MM-dd），不传则不限制")
            String startDate,
            @ToolParam(required = false, description = "结束日期（yyyy-MM-dd），不传则不限制")
            String endDate) {

        List<Expense> expenses;
        if (category != null && startDate != null && endDate != null) {
            expenses = expenseRepository.findByCategoryAndDateBetweenOrderByDateDesc(
                    category, LocalDate.parse(startDate), LocalDate.parse(endDate));
        } else if (category != null) {
            expenses = expenseRepository.findByCategoryOrderByDateDesc(category);
        } else if (startDate != null && endDate != null) {
            expenses = expenseRepository.findByDateBetweenOrderByDateDesc(
                    LocalDate.parse(startDate), LocalDate.parse(endDate));
        } else {
            expenses = expenseRepository.findAllByOrderByDateDesc();
        }

        if (expenses.isEmpty()) {
            return "暂无支出记录。";
        }

        BigDecimal total = BigDecimal.ZERO;
        StringJoiner sj = new StringJoiner("\n", "📊 支出记录：\n", "");
        for (int i = 0; i < expenses.size(); i++) {
            Expense e = expenses.get(i);
            total = total.add(e.getAmount());
            sj.add(String.format("%d. [#%d] %.2f元 %s %s",
                    i + 1, e.getId(), e.getAmount(), e.getCategory(), e.getDate()));
            if (!e.getDescription().isBlank()) {
                sj.add("   └ " + e.getDescription());
            }
        }
        sj.add("━━━━━━━━━━━");
        sj.add("合计：" + String.format("%.2f元", total));
        return sj.toString();
    }

    @Tool(name = "sumExpenses", description = "统计支出总额。可按类别统计，或统计一段时间内的总支出。")
    public String sumExpenses(
            @ToolParam(required = false, description = "支出类别，如餐饮、交通，不传则统计全部")
            String category,
            @ToolParam(required = false, description = "起始日期（yyyy-MM-dd）")
            String startDate,
            @ToolParam(required = false, description = "结束日期（yyyy-MM-dd）")
            String endDate) {

        BigDecimal total;
        StringBuilder sb = new StringBuilder("📈 支出统计");

        if (category != null && startDate != null && endDate != null) {
            List<Expense> expenses = expenseRepository.findByCategoryAndDateBetweenOrderByDateDesc(
                    category, LocalDate.parse(startDate), LocalDate.parse(endDate));
            total = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("（").append(category).append("，").append(startDate).append(" 至 ").append(endDate).append("）");
        } else if (category != null) {
            total = expenseRepository.sumByCategory(category);
            sb.append("（").append(category).append("）");
        } else if (startDate != null && endDate != null) {
            total = expenseRepository.sumByDateBetween(LocalDate.parse(startDate), LocalDate.parse(endDate));
            sb.append("（").append(startDate).append(" 至 ").append(endDate).append("）");
        } else {
            total = expenseRepository.sumByDateBetween(LocalDate.EPOCH, LocalDate.now().plusYears(100));
            sb.append("（全部）");
        }

        sb.append("：").append(String.format("%.2f元", total));
        return sb.toString();
    }
}
