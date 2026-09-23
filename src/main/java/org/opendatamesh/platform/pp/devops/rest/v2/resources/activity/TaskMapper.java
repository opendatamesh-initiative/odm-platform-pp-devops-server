package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "activity", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toEntity(TaskRes res);

    @Mapping(target = "activityUuid", source = "activity.uuid")
    TaskRes toRes(Task entity);

    @Mapping(target = "task", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TaskLog toEntity(TaskLogRes res);

    TaskLogRes toRes(TaskLog entity);

    @Mapping(target = "task", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TaskResult toEntity(TaskResultRes res);

    TaskResultRes toRes(TaskResult entity);
}
