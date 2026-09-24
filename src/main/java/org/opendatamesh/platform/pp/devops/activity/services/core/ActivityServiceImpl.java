package org.opendatamesh.platform.pp.devops.activity.services.core;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskLog;
import org.opendatamesh.platform.pp.devops.activity.entities.TaskResult;
import org.opendatamesh.platform.pp.devops.activity.repositories.ActivitiesRepository;
import org.opendatamesh.platform.pp.devops.exceptions.BadRequestException;
import org.opendatamesh.platform.pp.devops.exceptions.NotFoundException;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityMapper;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivityRes;
import org.opendatamesh.platform.pp.devops.rest.v2.resources.activity.ActivitySearchOptions;
import org.opendatamesh.platform.pp.devops.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.devops.utils.repositories.SpecsUtils;
import org.opendatamesh.platform.pp.devops.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.opendatamesh.platform.pp.devops.utils.services.TransactionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActivityServiceImpl
        extends GenericMappedAndFilteredCrudServiceImpl<ActivitySearchOptions, ActivityRes, Activity, String>
        implements ActivityService {

    @Autowired
    private ActivitiesRepository activitiesRepository;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private TransactionHandler transactionHandler;

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<Activity, String> getRepository() {
        return activitiesRepository;
    }

    @Override
    protected ActivityRes toRes(Activity entity) {
        return activityMapper.toRes(entity);
    }

    @Override
    protected Activity toEntity(ActivityRes resource) {
        return activityMapper.toEntity(resource);
    }

    @Override
    public ActivityRes overwriteResource(String uuid, ActivityRes resource) {
        resource.setUuid(uuid);
        return super.overwriteResource(uuid, resource);
    }

    @Override
    public Page<ActivityRes> findAllResourcesFiltered(Pageable pageable, ActivitySearchOptions filters) {
        return transactionHandler.runInTransaction(() ->
                findAllFiltered(pageable, filters).map(activityMapper::toResWithoutTasks)
        );
    }

    @Override
    protected Specification<Activity> getSpecFromFilters(ActivitySearchOptions filters) {
        List<Specification<Activity>> specs = new ArrayList<>();
        if (filters != null) {
            addIfPresent(specs, ActivitiesRepository.Specs.hasDataProductVersionUuid(filters.getDataProductVersionUuid()));
            addIfPresent(specs, ActivitiesRepository.Specs.hasDataProductFqn(filters.getDataProductFqn()));
            addIfPresent(specs, ActivitiesRepository.Specs.hasDataProductVersionTag(filters.getDataProductVersionTag()));
            addIfPresent(specs, ActivitiesRepository.Specs.hasName(filters.getName()));
            addIfPresent(specs, ActivitiesRepository.Specs.hasStatus(filters.getStatus()));
        }
        return SpecsUtils.combineWithAnd(specs);
    }

    private static void addIfPresent(List<Specification<Activity>> specs, Specification<Activity> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    @Override
    protected void validate(Activity activity) {
        if (activity.getTasks() == null) {
            activity.setTasks(new ArrayList<>());
        }

        activity.setUuid(trimToNull(activity.getUuid()));
        activity.setDataProductVersionUuid(trimToNull(activity.getDataProductVersionUuid()));
        activity.setDataProductFqn(trimToNull(activity.getDataProductFqn()));
        activity.setDataProductVersionTag(trimToNull(activity.getDataProductVersionTag()));
        activity.setName(trimToNull(activity.getName()));

        if (!StringUtils.hasText(activity.getDataProductVersionUuid())) {
            throw new BadRequestException("Data product version UUID is required");
        }
        if (activity.getDataProductVersionUuid().length() > 36) {
            throw new BadRequestException("Data product version UUID cannot exceed 36 characters");
        }
        if (!StringUtils.hasText(activity.getName())) {
            throw new BadRequestException("Name is required");
        }
        if (activity.getName().length() > 255) {
            throw new BadRequestException("Name cannot exceed 255 characters");
        }
        if (activity.getDataProductFqn() != null && activity.getDataProductFqn().length() > 255) {
            throw new BadRequestException("Data product FQN cannot exceed 255 characters");
        }
        if (activity.getDataProductVersionTag() != null && activity.getDataProductVersionTag().length() > 255) {
            throw new BadRequestException("Data product version tag cannot exceed 255 characters");
        }

        Set<String> taskUuids = new HashSet<>();
        for (Task task : activity.getTasks()) {
            if (task == null) {
                throw new BadRequestException("Task entry cannot be null");
            }
            task.setUuid(trimToNull(task.getUuid()));
            task.setName(trimToNull(task.getName()));
            task.setDescription(trimToNull(task.getDescription()));
            task.setProviderRunId(trimToNull(task.getProviderRunId()));

            if (task.getName() != null && task.getName().length() > 255) {
                throw new BadRequestException("Task name cannot exceed 255 characters");
            }
            if (task.getProviderRunId() != null && task.getProviderRunId().length() > 255) {
                throw new BadRequestException("Provider run id cannot exceed 255 characters");
            }
            if (task.getDescription() != null && task.getDescription().length() > 10000) {
                throw new BadRequestException("Task description cannot exceed 10000 characters");
            }
            if (StringUtils.hasText(task.getUuid()) && !taskUuids.add(task.getUuid())) {
                throw new BadRequestException("Duplicate task uuid in activity");
            }

            if (task.getLogs() == null) {
                task.setLogs(new ArrayList<>());
            }
            if (task.getResults() == null) {
                task.setResults(new ArrayList<>());
            }

            Set<String> logUuids = new HashSet<>();
            for (TaskLog log : task.getLogs()) {
                if (log == null) {
                    throw new BadRequestException("Log entry cannot be null");
                }
                log.setUuid(trimToNull(log.getUuid()));
                log.setContent(trimToNull(log.getContent()));
                if (!StringUtils.hasText(log.getContent())) {
                    throw new BadRequestException("Log content is required");
                }
                if (log.getContent().length() > 1048576) {
                    throw new BadRequestException("Log content cannot exceed 1048576 characters");
                }
                if (StringUtils.hasText(log.getUuid()) && !logUuids.add(log.getUuid())) {
                    throw new BadRequestException("Duplicate log uuid in task");
                }
            }

            Set<String> resultUuids = new HashSet<>();
            for (TaskResult result : task.getResults()) {
                if (result == null) {
                    throw new BadRequestException("Result entry cannot be null");
                }
                result.setUuid(trimToNull(result.getUuid()));
                result.setContent(trimToNull(result.getContent()));
                if (!StringUtils.hasText(result.getContent())) {
                    throw new BadRequestException("Result content is required");
                }
                if (result.getContent().length() > 1048576) {
                    throw new BadRequestException("Result content cannot exceed 1048576 characters");
                }
                if (StringUtils.hasText(result.getUuid()) && !resultUuids.add(result.getUuid())) {
                    throw new BadRequestException("Duplicate result uuid in task");
                }
            }
        }
    }

    @Override
    protected void reconcile(Activity activity) {
        if (activity.getStatus() == null) {
            activity.setStatus(ExecutionStatus.PENDING);
        }
        if (activity.getTasks() == null) {
            activity.setTasks(new ArrayList<>());
        }
        for (Task task : activity.getTasks()) {
            if (task.getStatus() == null) {
                task.setStatus(ExecutionStatus.PENDING);
            }
            task.setActivity(activity);
            if (task.getLogs() == null) {
                task.setLogs(new ArrayList<>());
            }
            if (task.getResults() == null) {
                task.setResults(new ArrayList<>());
            }
            for (TaskLog log : task.getLogs()) {
                log.setTask(task);
            }
            for (TaskResult result : task.getResults()) {
                result.setTask(task);
            }
        }
    }

    @Override
    protected void beforeCreation(Activity activity) {
        activity.setUuid(null);
        if (activity.getTasks() == null) {
            return;
        }
        for (Task task : activity.getTasks()) {
            task.setUuid(null);
            if (task.getLogs() != null) {
                for (TaskLog log : task.getLogs()) {
                    log.setUuid(null);
                }
            }
            if (task.getResults() != null) {
                for (TaskResult result : task.getResults()) {
                    result.setUuid(null);
                }
            }
        }
    }

    @Override
    protected void beforeOverwrite(Activity incoming) {
        if (incoming.getTasks() == null) {
            incoming.setTasks(new ArrayList<>());
        }
        for (Task task : incoming.getTasks()) {
            if (task.getLogs() == null) {
                task.setLogs(new ArrayList<>());
            }
            if (task.getResults() == null) {
                task.setResults(new ArrayList<>());
            }
        }

        Activity persisted = activitiesRepository.findById(incoming.getUuid())
                .orElseThrow(() -> new NotFoundException("Resource with id=" + incoming.getUuid() + " not found"));

        Map<String, Task> existingTasksByUuid = indexTasks(persisted.getTasks());
        List<Task> reconciledTasks = new ArrayList<>();

        for (Task incomingTask : incoming.getTasks()) {
            Task existingTask = StringUtils.hasText(incomingTask.getUuid())
                    ? existingTasksByUuid.get(incomingTask.getUuid())
                    : null;
            if (existingTask != null) {
                copyTaskScalars(incomingTask, existingTask);
                replaceLogs(existingTask, incomingTask.getLogs());
                replaceResults(existingTask, incomingTask.getResults());
                existingTask.setActivity(persisted);
                reconciledTasks.add(existingTask);
            } else {
                incomingTask.setUuid(null);
                clearChildUuids(incomingTask);
                incomingTask.setActivity(persisted);
                for (TaskLog log : incomingTask.getLogs()) {
                    log.setTask(incomingTask);
                }
                for (TaskResult result : incomingTask.getResults()) {
                    result.setTask(incomingTask);
                }
                reconciledTasks.add(incomingTask);
            }
        }

        persisted.getTasks().clear();
        persisted.getTasks().addAll(reconciledTasks);
        incoming.setTasks(persisted.getTasks());
    }

    private static Map<String, Task> indexTasks(List<Task> tasks) {
        Map<String, Task> byUuid = new HashMap<>();
        if (tasks == null) {
            return byUuid;
        }
        for (Task task : tasks) {
            if (StringUtils.hasText(task.getUuid())) {
                byUuid.put(task.getUuid(), task);
            }
        }
        return byUuid;
    }

    private static void copyTaskScalars(Task source, Task target) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setSortOrder(source.getSortOrder());
        target.setStatus(source.getStatus());
        target.setProviderRunId(source.getProviderRunId());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
    }

    private static void replaceLogs(Task managedTask, List<TaskLog> incomingLogs) {
        Map<String, TaskLog> existingByUuid = new HashMap<>();
        for (TaskLog log : managedTask.getLogs()) {
            if (StringUtils.hasText(log.getUuid())) {
                existingByUuid.put(log.getUuid(), log);
            }
        }
        List<TaskLog> reconciled = new ArrayList<>();
        for (TaskLog incomingLog : incomingLogs) {
            TaskLog existing = StringUtils.hasText(incomingLog.getUuid())
                    ? existingByUuid.get(incomingLog.getUuid())
                    : null;
            if (existing != null) {
                existing.setContent(incomingLog.getContent());
                existing.setGeneratedAt(incomingLog.getGeneratedAt());
                existing.setTask(managedTask);
                reconciled.add(existing);
            } else {
                incomingLog.setUuid(null);
                incomingLog.setTask(managedTask);
                reconciled.add(incomingLog);
            }
        }
        managedTask.getLogs().clear();
        managedTask.getLogs().addAll(reconciled);
    }

    private static void replaceResults(Task managedTask, List<TaskResult> incomingResults) {
        Map<String, TaskResult> existingByUuid = new HashMap<>();
        for (TaskResult result : managedTask.getResults()) {
            if (StringUtils.hasText(result.getUuid())) {
                existingByUuid.put(result.getUuid(), result);
            }
        }
        List<TaskResult> reconciled = new ArrayList<>();
        for (TaskResult incomingResult : incomingResults) {
            TaskResult existing = StringUtils.hasText(incomingResult.getUuid())
                    ? existingByUuid.get(incomingResult.getUuid())
                    : null;
            if (existing != null) {
                existing.setContent(incomingResult.getContent());
                existing.setGeneratedAt(incomingResult.getGeneratedAt());
                existing.setTask(managedTask);
                reconciled.add(existing);
            } else {
                incomingResult.setUuid(null);
                incomingResult.setTask(managedTask);
                reconciled.add(incomingResult);
            }
        }
        managedTask.getResults().clear();
        managedTask.getResults().addAll(reconciled);
    }

    private static void clearChildUuids(Task task) {
        if (task.getLogs() != null) {
            for (TaskLog log : task.getLogs()) {
                log.setUuid(null);
            }
        }
        if (task.getResults() != null) {
            for (TaskResult result : task.getResults()) {
                result.setUuid(null);
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
