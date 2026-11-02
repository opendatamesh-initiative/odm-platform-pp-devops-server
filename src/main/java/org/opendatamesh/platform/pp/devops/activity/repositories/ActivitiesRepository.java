package org.opendatamesh.platform.pp.devops.activity.repositories;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity_;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.activity.entities.Task;
import org.opendatamesh.platform.pp.devops.activity.entities.Task_;
import org.opendatamesh.platform.pp.devops.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.devops.utils.repositories.SpecsUtils;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

public interface ActivitiesRepository extends PagingAndSortingAndSpecificationExecutorRepository<Activity, String> {

    class Specs extends SpecsUtils {

        public static Specification<Activity> hasDataProductVersionUuid(String dataProductVersionUuid) {
            if (!StringUtils.hasText(dataProductVersionUuid)) {
                return null;
            }
            return (root, query, cb) -> cb.equal(root.get(Activity_.dataProductVersionUuid), dataProductVersionUuid);
        }

        public static Specification<Activity> hasDataProductFqn(String dataProductFqn) {
            if (!StringUtils.hasText(dataProductFqn)) {
                return null;
            }
            return (root, query, cb) -> cb.equal(root.get(Activity_.dataProductFqn), dataProductFqn);
        }

        public static Specification<Activity> hasDataProductVersionTag(String dataProductVersionTag) {
            if (!StringUtils.hasText(dataProductVersionTag)) {
                return null;
            }
            return (root, query, cb) -> cb.equal(root.get(Activity_.dataProductVersionTag), dataProductVersionTag);
        }

        public static Specification<Activity> hasName(String name) {
            if (!StringUtils.hasText(name)) {
                return null;
            }
            return (root, query, cb) -> cb.equal(root.get(Activity_.name), name);
        }

        public static Specification<Activity> hasStatusIn(Collection<ExecutionStatus> statuses) {
            if (CollectionUtils.isEmpty(statuses)) {
                return null;
            }
            return (root, query, cb) -> root.get(Activity_.status).in(statuses);
        }

        /**
         * Activities that have a RUNNING task with the given provider run id.
         * Not a search filter: the caller guarantees a non-blank id, and the result is never null.
         */
        public static Specification<Activity> hasRunningTaskWithProviderRunId(String providerRunId) {
            return (root, query, cb) -> {
                query.distinct(true);
                Join<Activity, Task> tasks = root.join(Activity_.tasks);
                return cb.and(
                        cb.equal(tasks.get(Task_.providerRunId), providerRunId),
                        cb.equal(tasks.get(Task_.status), ExecutionStatus.RUNNING)
                );
            };
        }
    }
}
