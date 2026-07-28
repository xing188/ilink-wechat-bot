package com.ilink.test.ilinkcheck.application.tool.categories.reminder;

import com.ilink.test.ilinkcheck.application.reminder.ReminderService;
import com.ilink.test.ilinkcheck.application.reminder.context.ReminderRequestContext;
import com.ilink.test.ilinkcheck.application.tool.ChatFunctionTool;
import com.ilink.test.ilinkcheck.application.tool.orchestration.ToolExecutionPolicy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ReminderTool implements ChatFunctionTool {

    private final ReminderService reminderService;

    public ReminderTool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Tool(
            name = "createReminder",
            description = """
                    创建定时提醒或待办事项。
                    当用户说“提醒我今天下午5点给张三发微信”“十分钟后开会”“每天早上8点推送科技新闻”等类似表述时调用。
                    传入用户原始表述即可，工具会自动解析时间和待办内容。
                    """
    )
    @ToolExecutionPolicy(readOnly = false)
    public String createReminder(
            @ToolParam(required = true, description = "用户原始提醒语句，例如“今天下午5点给张三发微信”")
            String request) {
        return reminderService.createReminder(ReminderRequestContext.currentUserId().orElse(null), request);
    }

    @Tool(
            name = "listPendingReminders",
            description = """
                    查看当前用户尚未完成的待办提醒。
                    当用户说“查看待办事项”“我有哪些提醒”“列出未完成提醒”类似表述时调用。
                    """
    )
    public String listPendingReminders() {
        return reminderService.listPendingReminders(ReminderRequestContext.currentUserId().orElse(null));
    }
}
