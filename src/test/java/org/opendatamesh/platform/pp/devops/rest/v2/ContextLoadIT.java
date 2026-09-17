package org.opendatamesh.platform.pp.devops.rest.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextLoadIT extends DevOpsApplicationIT {

    @Test
    void contextLoads() {
        assertThat(port).isNotBlank();
    }
}
