package com.ilink.test.ilinkcheck.application.reminder;

import com.ilink.test.ilinkcheck.application.reminder.model.ReminderRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository {

    ReminderRecord save(ReminderRecord reminder);

    Optional<ReminderRecord> findById(String reminderId);

    List<ReminderRecord> findPendingByUser(String userId);

    List<ReminderRecord> findDueReminders(Instant now, int limit);
}
