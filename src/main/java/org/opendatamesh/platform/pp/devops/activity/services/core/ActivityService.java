package org.opendatamesh.platform.pp.devops.activity.services.core;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.GenericMappedAndFilteredCrudService;

import java.util.List;
import java.util.Set;

public interface ActivityService extends GenericMappedAndFilteredCrudService<ActivitySearchOptions, ActivityRes, Activity, String> {

    List<Activity> findActivitiesInStatus(String dataProductVersionUuid, String name, Set<ExecutionStatus> statuses);

    List<Activity> findAllOfDataProductVersion(String dataProductVersionUuid);
}
