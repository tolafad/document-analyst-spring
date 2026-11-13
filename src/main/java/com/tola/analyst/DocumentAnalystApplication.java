package com.tola.analyst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocumentAnalystApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocumentAnalystApplication.class, args);
	}

}
