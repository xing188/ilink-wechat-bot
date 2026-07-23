package com.ilink.test.ilinkcheck.infrastructure.persistence.reminder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReminderTaskMapper extends BaseMapper<ReminderTaskDO> {
}
