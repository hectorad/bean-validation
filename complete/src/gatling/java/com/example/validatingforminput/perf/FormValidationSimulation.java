package com.example.validatingforminput.perf;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

/**
 * Gatling simulation that drives {@code POST /} on the validating-form-input app
 * to measure the performance cost of the Extensions constraint validator.
 *
 * <p>Drive via system properties:
 * <ul>
 *   <li>{@code baseUrl}       — default {@code http://localhost:8080}</li>
 *   <li>{@code rps}           — requests per second, default {@code 100}</li>
 *   <li>{@code duration}      — steady-state seconds, default {@code 120}</li>
 *   <li>{@code warmup}        — warmup seconds at the same RPS, default {@code 30}</li>
 *   <li>{@code payloadShape}  — {@code shallow} (default) or {@code deep}</li>
 *   <li>{@code run.label}     — free-form label echoed into the scenario name
 *                               so the Gatling HTML report is easy to tell apart</li>
 * </ul>
 *
 * <p>Run like:
 * <pre>
 *   ./mvnw -Pperformance gatling:test \
 *     -DbaseUrl=http://localhost:8080 \
 *     -Drps=200 -Dduration=120 -Dwarmup=30 \
 *     -DpayloadShape=deep -Drun.label=ext-on-deep
 * </pre>
 */
public class FormValidationSimulation extends Simulation {

	private static final String BASE_URL = sysProp("baseUrl", "http://localhost:8080");
	private static final int RPS = Integer.parseInt(sysProp("rps", "100"));
	private static final int DURATION_SECONDS = Integer.parseInt(sysProp("duration", "120"));
	private static final int WARMUP_SECONDS = Integer.parseInt(sysProp("warmup", "30"));
	private static final String PAYLOAD_SHAPE = sysProp("payloadShape", "shallow");
	private static final String RUN_LABEL = sysProp("run.label", PAYLOAD_SHAPE);

	// Shallow JSON: matches $.vendorExtensionCode
	private static final String SHALLOW_EXTENSIONS_JSON =
			"{\"vendorExtensionCode\":\"ABC-1234\"}";

	// Deep JSON: matches $.vendor.contact.codes[*].value (5-level traversal,
	// array-wildcard iterates 3 candidates). Also includes a top-level
	// vendorExtensionCode so the shallow JSONPath matches the same body —
	// that lets us re-run the shallow config against the deep payload to
	// isolate payload size from traversal depth.
	private static final String DEEP_EXTENSIONS_JSON =
			"{"
					+ "\"vendor\":{"
					+   "\"contact\":{"
					+     "\"codes\":["
					+       "{\"value\":\"ABC-1234\"},"
					+       "{\"value\":\"DEF-5678\"},"
					+       "{\"value\":\"GHI-9012\"}"
					+     "]"
					+   "}"
					+ "},"
					+ "\"vendorExtensionCode\":\"ABC-1234\""
					+ "}";

	private static final String BODY = buildBody(PAYLOAD_SHAPE);

	private final HttpProtocolBuilder httpProtocol = http
			.baseUrl(BASE_URL)
			.acceptHeader("text/html,application/xhtml+xml")
			.contentTypeHeader("application/x-www-form-urlencoded")
			.userAgentHeader("gatling-form-validation-perf");

	private final ScenarioBuilder scn = scenario("FormValidation [" + RUN_LABEL + "]")
			.exec(http("POST /")
					.post("/")
					.body(io.gatling.javaapi.core.CoreDsl.StringBody(BODY))
					.asFormUrlEncoded()
					.check(status().in(200, 302)));

	public FormValidationSimulation() {
		setUp(scn.injectOpen(
						constantUsersPerSec(RPS).during(Duration.ofSeconds(WARMUP_SECONDS)),
						constantUsersPerSec(RPS).during(Duration.ofSeconds(DURATION_SECONDS))))
				.protocols(httpProtocol)
				.maxDuration(Duration.ofSeconds(WARMUP_SECONDS + DURATION_SECONDS + 10L));
	}

	private static String buildBody(String shape) {
		String extensionsJson = "deep".equalsIgnoreCase(shape)
				? DEEP_EXTENSIONS_JSON
				: SHALLOW_EXTENSIONS_JSON;
		// name = 20 chars (satisfies Size min=20 override), age>=20, salary>1000.10
		return "name=" + URLEncoder.encode("Robert Johnson Smith", StandardCharsets.UTF_8)
				+ "&age=30"
				+ "&salary=2000.00"
				+ "&extensions=" + URLEncoder.encode(extensionsJson, StandardCharsets.UTF_8);
	}

	private static String sysProp(String key, String fallback) {
		String v = System.getProperty(key);
		return (v == null || v.isBlank()) ? fallback : v;
	}
}
