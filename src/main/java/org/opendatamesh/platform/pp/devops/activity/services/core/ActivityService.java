package org.opendatamesh.platform.pp.devops.activity.services.core;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.GenericMappedAndFilteredCrudService;

import java.util.List;

public interface ActivityService extends GenericMappedAndFilteredCrudService<ActivitySearchOptions, ActivityRes, Activity, String> {

    /**
     * Returns the activities with a RUNNING task whose provider run id equals the given one,
     * without loading their tasks. A blank run id returns an empty list.
     */
    List<Activity> findActivitiesWithRunningTask(String providerRunId);
}
