package org.opendatamesh.platform.pp.devops.activity.repositories;

import org.opendatamesh.platform.pp.devops.activity.entities.Activity;
import org.opendatamesh.platform.pp.devops.activity.entities.Activity_;
import org.opendatamesh.platform.pp.devops.activity.entities.ExecutionStatus;
import org.opendatamesh.platform.pp.devops.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.pp.devops.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

public interface ActivitiesRepository extends PagingAndSortingAndSpecificationExecutorRepository<Activity, String> {

    List<Activity> findByDataProductVersionUuidAndNameAndStatusIn(String dataProductVersionUuid, String name, Collection<ExecutionStatus> statuses);

    List<Activity> findByDataProductVersionUuid(String dataProductVersionUuid);

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

        public static Specification<Activity> hasStatus(ExecutionStatus status) {
            if (status == null) {
                return null;
            }
            return (root, query, cb) -> cb.equal(root.get(Activity_.status), status);
        }
    }
}
