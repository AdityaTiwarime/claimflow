package com.claimflow.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Runs the appender against a fake Splunk HEC endpoint and checks exactly what it receives. */
class SplunkHecAppenderTest {

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private HttpServer fakeSplunk;
    private SplunkHecAppender appender;

    private Logger logger(int status) throws Exception {
        fakeSplunk = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeSplunk.createContext("/services/collector/event", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"text\":\"Success\",\"code\":0}".getBytes();
            ex.sendResponseHeaders(status, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        fakeSplunk.start();

        // The real logback context (it carries the MDC adapter the appender reads claimId from).
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new SplunkHecAppender();
        appender.setContext(ctx);
        appender.setUrl("http://127.0.0.1:" + fakeSplunk.getAddress().getPort() + "/services/collector/event");
        appender.setToken("test-token");
        appender.setSource("claims-service");
        appender.setFlushMillis(100);
        appender.start();
        Logger log = ctx.getLogger("splunk-appender-test-" + System.nanoTime());
        log.setLevel(Level.INFO);
        log.setAdditive(false);
        log.addAppender(appender);
        return log;
    }

    @AfterEach
    void stop() {
        if (appender != null) appender.stop();
        if (fakeSplunk != null) fakeSplunk.stop(0);
    }

    @Test
    void sendsJsonEventsWithTheTokenAndClaimId() throws Exception {
        Logger log = logger(200);
        MDC.put("claimId", "CLM-ABC12345");
        log.info("claim {} approved", "CLM-ABC12345");
        MDC.remove("claimId");
        log.warn("second");
        log.debug("filtered out by level");

        await().atMost(Duration.ofSeconds(5)).until(() -> appender.getSent() == 2);
        assertThat(authHeaders).allMatch("Splunk test-token"::equals);
        List<JsonNode> events = bodies.stream().flatMap(b -> b.lines()).map(this::parse).toList();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("source").asText()).isEqualTo("claims-service");
        assertThat(events.get(0).get("sourcetype").asText()).isEqualTo("_json");
        assertThat(events.get(0).at("/event/message").asText()).isEqualTo("claim CLM-ABC12345 approved");
        assertThat(events.get(0).at("/event/claimId").asText()).isEqualTo("CLM-ABC12345");
        assertThat(events.get(1).at("/event/level").asText()).isEqualTo("WARN");
    }

    @Test
    void splunkErrorsAreCountedNotThrown() throws Exception {
        Logger log = logger(503);
        log.info("lost");
        await().atMost(Duration.ofSeconds(5)).until(() -> appender.getDropped() == 1);
        assertThat(appender.getSent()).isZero();
    }

    @Test
    void refusesToStartWithoutToken() {
        SplunkHecAppender a = new SplunkHecAppender();
        a.setContext(new LoggerContext());
        a.setUrl("http://localhost:8088/services/collector/event");
        a.start();
        assertThat(a.isStarted()).isFalse();
    }

    private JsonNode parse(String line) {
        try {
            return new ObjectMapper().readTree(line);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
