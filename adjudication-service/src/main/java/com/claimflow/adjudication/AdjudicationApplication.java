package com.claimflow.adjudication;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AdjudicationApplication {

    /**
     * Config lives in adjudication.yml rather than application.yml, so this service can run in the
     * same JVM as claims-service (the end-to-end tests do) without reading the other's settings.
     */
    public static final String CONFIG_NAME = "spring.config.name=adjudication";

    public static void main(String[] args) {
        new SpringApplicationBuilder(AdjudicationApplication.class).properties(CONFIG_NAME).run(args);
    }
}
