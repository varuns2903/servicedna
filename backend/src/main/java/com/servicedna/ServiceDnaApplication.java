package com.servicedna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServiceDnaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceDnaApplication.class, args);
	}

}
