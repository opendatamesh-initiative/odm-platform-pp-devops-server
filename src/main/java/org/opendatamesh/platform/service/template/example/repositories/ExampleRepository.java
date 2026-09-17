package org.opendatamesh.platform.service.template.example.repositories;

import org.opendatamesh.platform.service.template.example.entities.Example;
import org.opendatamesh.platform.service.template.example.entities.Example_;
import org.opendatamesh.platform.service.template.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.service.template.utils.repositories.SpecsUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public interface ExampleRepository extends PagingAndSortingAndSpecificationExecutorRepository<Example, String> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndUuidNot(String name, String uuid);

    class Specs extends SpecsUtils {

        public static Specification<Example> hasName(String name) {
            return (root, query, cb) -> {
                if (!StringUtils.hasText(name)) {
                    return cb.conjunction();
                }
                final String pattern = String.format("%%%s%%", escapeLikeParameter(name.toLowerCase(), LIKE_ESCAPE_CHAR));
                return cb.like(cb.lower(root.get(Example_.name)), pattern, LIKE_ESCAPE_CHAR);
            };
        }
    }
}
