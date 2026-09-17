package org.opendatamesh.platform.service.template.example.services.core;

import org.opendatamesh.platform.service.template.example.entities.Example;
import org.opendatamesh.platform.service.template.example.repositories.ExampleRepository;
import org.opendatamesh.platform.service.template.exceptions.BadRequestException;
import org.opendatamesh.platform.service.template.exceptions.ResourceConflictException;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleMapper;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleRes;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleSearchOptions;
import org.opendatamesh.platform.service.template.utils.repositories.PagingAndSortingAndSpecificationExecutorRepository;
import org.opendatamesh.platform.service.template.utils.repositories.SpecsUtils;
import org.opendatamesh.platform.service.template.utils.services.GenericMappedAndFilteredCrudServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExampleServiceImpl extends GenericMappedAndFilteredCrudServiceImpl<ExampleSearchOptions, ExampleRes, Example, String> implements ExampleService {

    private final ExampleMapper mapper;
    private final ExampleRepository repository;

    @Autowired
    public ExampleServiceImpl(ExampleMapper mapper, ExampleRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    protected PagingAndSortingAndSpecificationExecutorRepository<Example, String> getRepository() {
        return repository;
    }

    @Override
    protected Specification<Example> getSpecFromFilters(ExampleSearchOptions filters) {
        List<Specification<Example>> specs = new ArrayList<>();
        if (filters != null && StringUtils.hasText(filters.getName())) {
            specs.add(ExampleRepository.Specs.hasName(filters.getName()));
        }
        return SpecsUtils.combineWithAnd(specs);
    }

    @Override
    protected ExampleRes toRes(Example entity) {
        return mapper.toRes(entity);
    }

    @Override
    protected Example toEntity(ExampleRes resource) {
        return mapper.toEntity(resource);
    }

    @Override
    protected void validate(Example objectToValidate) {
        if (objectToValidate == null) {
            throw new BadRequestException("Example cannot be null");
        }
        String name = objectToValidate.getName();
        if (name != null) {
            name = name.trim();
            objectToValidate.setName(name);
        }
        if (!StringUtils.hasText(objectToValidate.getName())) {
            throw new BadRequestException("Name is required");
        }
        if (objectToValidate.getName().length() > 255) {
            throw new BadRequestException("Name cannot exceed 255 characters");
        }
        if (StringUtils.hasText(objectToValidate.getDisplayName()) && objectToValidate.getDisplayName().length() > 255) {
            throw new BadRequestException("Display name cannot exceed 255 characters");
        }
    }

    @Override
    protected void reconcile(Example objectToReconcile) {
        // no nested refs
    }

    @Override
    protected void beforeCreation(Example objectToCreate) {
        validateNaturalKeyConstraints(objectToCreate, null);
    }

    @Override
    protected void beforeOverwrite(Example objectToOverwrite) {
        validateNaturalKeyConstraints(objectToOverwrite, objectToOverwrite.getUuid());
    }

    @Override
    public ExampleRes overwriteResource(String uuid, ExampleRes resource) {
        resource.setUuid(uuid);
        return super.overwriteResource(uuid, resource);
    }

    private void validateNaturalKeyConstraints(Example example, String excludeUuid) {
        boolean existsByName;
        if (StringUtils.hasText(excludeUuid)) {
            existsByName = repository.existsByNameIgnoreCaseAndUuidNot(example.getName(), excludeUuid);
        } else {
            existsByName = repository.existsByNameIgnoreCase(example.getName());
        }
        if (existsByName) {
            throw new ResourceConflictException("An example with name '" + example.getName() + "' already exists");
        }
    }
}
