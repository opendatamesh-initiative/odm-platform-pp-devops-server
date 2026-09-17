package org.opendatamesh.platform.service.template.rest.v2.resources.example;

import org.mapstruct.Mapper;
import org.opendatamesh.platform.service.template.example.entities.Example;

@Mapper(componentModel = "spring")
public interface ExampleMapper {
    ExampleRes toRes(Example entity);

    Example toEntity(ExampleRes res);
}
