package org.opendatamesh.platform.pp.devops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DevOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevOpsApplication.class, args);
	}

}
