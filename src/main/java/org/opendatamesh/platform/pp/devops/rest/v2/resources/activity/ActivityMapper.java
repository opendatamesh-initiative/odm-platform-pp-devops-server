package org.opendatamesh.platform.pp.devops.rest.v2.resources.activity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity;

@Mapper(componentModel = "spring", uses = TaskMapper.class)
public interface ActivityMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Activity toEntity(ActivityRes res);

    ActivityRes toRes(Activity entity);

    @Mapping(target = "tasks", ignore = true)
    ActivityRes toResWithoutTasks(Activity entity);

    @Mapping(target = "tasks", qualifiedByName = "withoutLogsAndResults")
    ActivityRes toEventRes(Activity entity);
}
