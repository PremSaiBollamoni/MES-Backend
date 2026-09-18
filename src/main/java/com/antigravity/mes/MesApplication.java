package com.antigravity.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/** Extends SpringBootServletInitializer so the WAR can be deployed to an external
 * servlet container (Tomcat); running the jar/main() directly still works unchanged. */
@SpringBootApplication
public class MesApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(MesApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(MesApplication.class, args);
	}

}
