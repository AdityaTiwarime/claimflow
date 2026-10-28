package com.claimflow.claims;

import com.claimflow.adjudication.AdjudicationApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts the real adjudication service inside the test JVM, on the same embedded Kafka. */
final class AdjudicationRunner {
    private static ConfigurableApplicationContext context;

    private AdjudicationRunner() {}

    static synchronized void ensureStarted() {
        if (context == null) {
            context = new SpringApplicationBuilder(AdjudicationApplication.class)
                    .properties(AdjudicationApplication.CONFIG_NAME,
                            "spring.kafka.bootstrap-servers=" + System.getProperty("spring.embedded.kafka.brokers"),
                            "server.port=0")
                    .run();
            Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        }
    }
}
