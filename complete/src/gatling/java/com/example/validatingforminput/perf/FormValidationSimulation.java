package com.example.validatingforminput.perf;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Gatling simulation for the perf-only HTTP validation endpoints.
 *
 * <p>Drive via system properties:
 * <ul>
 *   <li>{@code baseUrl}       — default {@code http://localhost:8080}</li>
 *   <li>{@code rps}           — requests per second, default {@code 100}</li>
 *   <li>{@code duration}      — steady-state seconds, default {@code 120}</li>
 *   <li>{@code warmup}        — warmup seconds at the same RPS, default {@code 30}</li>
 *   <li>{@code bodyMode}      — {@code map} (default) or {@code raw}</li>
 *   <li>{@code payloadShape}  — {@code shallow} (default) or {@code deep}</li>
 *   <li>{@code run.label}     — free-form label echoed into the scenario name</li>
 * </ul>
 *
 * <p>Run like:
 * <pre>
 *   ./mvnw -Pperformance gatling:test \
 *     -DbaseUrl=http://localhost:8080 \
 *     -Drps=200 -Dduration=120 -Dwarmup=30 \
 *     -DbodyMode=raw -DpayloadShape=deep -Drun.label=deep-raw
 * </pre>
 */
public class FormValidationSimulation extends Simulation {

    private static final String BASE_URL = sysProp("baseUrl", "http://localhost:8080");
    private static final int RPS = Integer.parseInt(sysProp("rps", "100"));
    private static final int DURATION_SECONDS = Integer.parseInt(sysProp("duration", "120"));
    private static final int WARMUP_SECONDS = Integer.parseInt(sysProp("warmup", "30"));
    private static final String BODY_MODE = sysProp("bodyMode", "map");
    private static final String PAYLOAD_SHAPE = sysProp("payloadShape", "shallow");
    private static final String RUN_LABEL = sysProp("run.label", BODY_MODE + "-" + PAYLOAD_SHAPE);
    private static final String PATH = "/perf/validate/extensions/" + BODY_MODE;
    private static final String BODY = buildBody(BODY_MODE, PAYLOAD_SHAPE);

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("gatling-form-validation-perf");

    private final ScenarioBuilder scn = scenario("PerfValidation [" + RUN_LABEL + "]")
        .exec(http("POST " + PATH)
            .post(PATH)
            .body(StringBody(BODY))
            .check(status().is(200)));

    public FormValidationSimulation() {
        setUp(scn.injectOpen(
                constantUsersPerSec(RPS).during(Duration.ofSeconds(WARMUP_SECONDS)),
                constantUsersPerSec(RPS).during(Duration.ofSeconds(DURATION_SECONDS))))
            .protocols(httpProtocol)
            .maxDuration(Duration.ofSeconds(WARMUP_SECONDS + DURATION_SECONDS + 10L));
    }

    private static String buildBody(String bodyMode, String payloadShape) {
        return "raw".equalsIgnoreCase(bodyMode)
            ? PerfPayloadFixtures.rawRequestBody(payloadShape)
            : PerfPayloadFixtures.mapRequestBody(payloadShape);
    }

    private static String sysProp(String key, String fallback) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
