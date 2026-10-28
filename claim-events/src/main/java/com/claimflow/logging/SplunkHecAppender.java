package com.claimflow.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends log events to Splunk's HTTP Event Collector (HEC) as JSON, so they can be searched in
 * Splunk by service, level or claim ID.
 *
 * <p>Logging must never slow down or break the application: events go into a bounded queue and a
 * background thread posts them in batches. If Splunk is down or slow, the queue fills and further
 * events are dropped (and counted) instead of blocking request threads.
 *
 * <p>Configured in logback-spring.xml under the "splunk" Spring profile.
 */
public class SplunkHecAppender extends AppenderBase<ILoggingEvent> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private String url;
    private String token;
    private String source = "claimflow";
    private String sourcetype = "_json";
    private String index;
    private int batchSize = 50;
    private long flushMillis = 1000;
    private int queueSize = 10_000;

    private BlockingQueue<ILoggingEvent> queue;
    private HttpClient http;
    private Thread worker;
    private String host;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();

    @Override
    public void start() {
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            addError("SplunkHecAppender needs url and token");
            return;
        }
        queue = new ArrayBlockingQueue<>(queueSize);
        // HEC speaks HTTP/1.1; skipping the HTTP/2 upgrade attempt also avoids a hang on plain-HTTP endpoints.
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        worker = new Thread(this::run, "splunk-hec");
        worker.setDaemon(true);
        super.start();
        worker.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing(); // capture MDC and the formatted message now
        if (!queue.offer(event)) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        List<ILoggingEvent> batch = new ArrayList<>(batchSize);
        while (isStarted() || !queue.isEmpty()) {
            try {
                ILoggingEvent first = queue.poll(flushMillis, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                }
            } catch (InterruptedException e) {
                queue.drainTo(batch); // shutting down: send what's left
            }
            if (!batch.isEmpty()) {
                post(batch);
                batch.clear();
            }
        }
    }

    private void post(List<ILoggingEvent> batch) {
        StringBuilder body = new StringBuilder();
        for (ILoggingEvent e : batch) {
            body.append(toJson(e)).append('\n');
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Splunk " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                sent.addAndGet(batch.size());
            } else {
                dropped.addAndGet(batch.size());
                addWarn("Splunk HEC answered " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception ex) {
            dropped.addAndGet(batch.size());
            addWarn("Splunk HEC unreachable: " + ex.getMessage());
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    String toJson(ILoggingEvent e) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("level", e.getLevel().toString());
        event.put("logger", e.getLoggerName());
        event.put("thread", e.getThreadName());
        event.put("message", e.getFormattedMessage());
        event.putAll(e.getMDCPropertyMap());
        if (e.getThrowableProxy() != null) {
            event.put("exception", e.getThrowableProxy().getClassName() + ": " + e.getThrowableProxy().getMessage());
        }
        Map<String, Object> hec = new LinkedHashMap<>();
        hec.put("time", e.getTimeStamp() / 1000.0);
        hec.put("host", host);
        hec.put("source", source);
        hec.put("sourcetype", sourcetype);
        if (index != null && !index.isBlank()) {
            hec.put("index", index);
        }
        hec.put("event", event);
        try {
            return JSON.writeValueAsString(hec);
        } catch (Exception ex) {
            return "{\"event\":\"unserialisable log event\"}";
        }
    }

    public long getSent() { return sent.get(); }
    public long getDropped() { return dropped.get(); }

    public void setUrl(String url) { this.url = url; }
    public void setToken(String token) { this.token = token; }
    public void setSource(String source) { this.source = source; }
    public void setSourcetype(String sourcetype) { this.sourcetype = sourcetype; }
    public void setIndex(String index) { this.index = index; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public void setFlushMillis(long flushMillis) { this.flushMillis = flushMillis; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
}
