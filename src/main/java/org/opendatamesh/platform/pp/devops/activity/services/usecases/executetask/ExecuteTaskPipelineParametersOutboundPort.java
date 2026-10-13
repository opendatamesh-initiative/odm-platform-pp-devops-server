package org.opendatamesh.platform.pp.devops.activity.services.usecases.executetask;

import org.opendatamesh.platform.pp.devops.activity.entities.Task;

import java.util.Map;

interface ExecuteTaskPipelineParametersOutboundPort {
    Map<String, String> resolvePipelineParameters(Task task);
}
