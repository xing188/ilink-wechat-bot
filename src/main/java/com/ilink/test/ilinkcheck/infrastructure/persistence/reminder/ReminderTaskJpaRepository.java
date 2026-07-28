package com.ilink.test.ilinkcheck.infrastructure.persistence.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReminderTaskJpaRepository extends JpaRepository<ReminderTaskDO, String> {

    List<ReminderTaskDO> findByUserIdAndStatusOrderByNextFireAtAsc(String userId, String status);

    List<ReminderTaskDO> findByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(String status, Instant now);

    List<ReminderTaskDO> findByStatusOrderByNextFireAtAsc(String status);
}
