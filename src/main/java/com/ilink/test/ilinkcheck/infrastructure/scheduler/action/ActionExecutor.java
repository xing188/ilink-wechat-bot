package com.ilink.test.ilinkcheck.infrastructure.scheduler.action;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderAction;

public interface ActionExecutor {

    ReminderAction supportedAction();

    String execute(String userId, String params);
}
