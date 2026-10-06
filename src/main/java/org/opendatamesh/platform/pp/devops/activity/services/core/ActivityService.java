package org.opendatamesh.platform.pp.devops.activity.services.core;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.services.GenericMappedAndFilteredCrudService;

public interface ActivityService extends GenericMappedAndFilteredCrudService<ActivitySearchOptions, ActivityRes, Activity, String> {
}
