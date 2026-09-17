package org.opendatamesh.platform.service.template.example.services.core;

import org.opendatamesh.platform.service.template.example.entities.Example;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleRes;
import org.opendatamesh.platform.service.template.rest.v2.resources.example.ExampleSearchOptions;
import org.opendatamesh.platform.service.template.utils.services.GenericMappedAndFilteredCrudService;

public interface ExampleService extends GenericMappedAndFilteredCrudService<ExampleSearchOptions, ExampleRes, Example, String> {
}
