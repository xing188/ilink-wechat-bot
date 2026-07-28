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

    @Tool(
            name = "cancelReminder",
            description = """
                    取消一条待办提醒。用户说“取消提醒”“删除提醒”“关掉提醒”等时调用。
                    可以先调用 listPendingReminders 让用户看到提醒 ID，再调用此工具取消。
                    """
    )
    public String cancelReminder(
            @ToolParam(required = true, description = "要取消的提醒 ID，来自 listPendingReminders 的结果")
            String reminderId) {
        return reminderService.cancelReminder(ReminderRequestContext.currentUserId().orElse(null), reminderId);
    }
}
