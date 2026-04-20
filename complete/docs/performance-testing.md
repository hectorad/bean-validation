# Performance Testing

This repo now supports two complementary perf paths for the `Extensions` validator:

- JMH for isolated `Validator.validate(...)` cost
- Gatling for end-to-end HTTP cost through dedicated perf endpoints

The old `POST /` Thymeleaf form path is intentionally not used for `extensions` perf measurements because that MVC flow does not bind an `extensions` JSON payload into the form object.

## JMH

Run the full benchmark:

```bash
./mvnw jmh:benchmark -Djmh.benchmarks=ExtensionsValidatorBenchmark
```

Run the fast smoke version:

```bash
./mvnw jmh:benchmark \
  -Djmh.benchmarks=ExtensionsValidatorBenchmark \
  -Djmh.f=1 -Djmh.wi=1 -Djmh.i=1 -Djmh.w=200ms -Djmh.r=200ms
```

Default JMH settings:

- average time mode
- microseconds output
- 2 forks
- 5 warmup iterations at 1 second each
- 10 measurement iterations at 1 second each

Results are written to [extensions-validator.json](/Users/hectorad/Developer/gs-validating-form-input/complete/target/jmh-results/extensions-validator.json).

### JMH matrix

`ExtensionsValidatorBenchmark` measures four validator modes across two payload representations:

- `validationOff`: `com.ampp.validation-enabled=false`
- `baselineOn`: validation enabled, no `Extensions` override
- `shallowRule`: `jsonPath=$.cartCode`
- `deepRule`: `jsonPath=$.items[*].productOffering.tags.catalogCode`

Measured scenarios:

- `off_map_shallow_payload`
- `off_map_deep_payload`
- `baseline_map_shallow_payload`
- `baseline_map_deep_payload`
- `shallow_path_on_map_shallow_payload`
- `shallow_path_on_map_deep_payload`
- `deep_path_on_map_deep_payload`
- `off_json_shallow_payload`
- `off_json_deep_payload`
- `baseline_json_shallow_payload`
- `baseline_json_deep_payload`
- `shallow_path_on_json_shallow_payload`
- `shallow_path_on_json_deep_payload`
- `deep_path_on_json_deep_payload`

Payload shapes:

- shallow payload: top-level `cartCode`
- deep payload: top-level `cartCode` plus `items[*].productOffering.tags.catalogCode` with 3 wildcard candidates

The deep payload intentionally includes the top-level shallow key, so `shallow_path_on_*_deep_payload` and `deep_path_on_*_deep_payload` run on the same shopping-cart body and isolate traversal-depth cost from payload-size cost.

### JMH setup checks

The benchmark asserts correctness before timing starts:

- validation off accepts invalid map/raw payloads and malformed raw JSON strings
- baseline validation accepts valid shopping-cart payloads and ignores invalid extension content because no `Extensions` rule is active
- shallow validation accepts shallow and deep shopping-cart payloads, rejects invalid shallow payloads, and rejects malformed raw JSON
- deep validation accepts deep shopping-cart payloads and rejects invalid deep payloads

## HTTP / Gatling

The perf-only endpoints are disabled by default and are enabled with:

```bash
--com.ampp.perf-endpoints.enabled=true
```

Endpoints:

- `POST /perf/validate/extensions/map`
- `POST /perf/validate/extensions/raw`

### Start the app

Build the jar once:

```bash
./mvnw -DskipTests package
```

Validation off:

```bash
java -jar target/validating-form-input-0.0.1-SNAPSHOT.jar \
  --com.ampp.perf-endpoints.enabled=true \
  --com.ampp.validation-enabled=false
```

Shallow rule:

```bash
noglob java -jar target/validating-form-input-0.0.1-SNAPSHOT.jar \
  --com.ampp.perf-endpoints.enabled=true \
  --com.ampp.validation-enabled=true \
  --com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.perf.PerfMapValidationRequest \
  --com.ampp.businessValidationOverride[0].fields[0].fieldName=extensions \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=$.cartCode \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$ \
  --com.ampp.businessValidationOverride[1].fullClassName=com.example.validatingforminput.perf.PerfRawValidationRequest \
  --com.ampp.businessValidationOverride[1].fields[0].fieldName=extensions \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Extensions \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.jsonPath=$.cartCode \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$
```

Deep rule:

```bash
noglob java -jar target/validating-form-input-0.0.1-SNAPSHOT.jar \
  --com.ampp.perf-endpoints.enabled=true \
  --com.ampp.validation-enabled=true \
  --com.ampp.businessValidationOverride[0].fullClassName=com.example.validatingforminput.perf.PerfMapValidationRequest \
  --com.ampp.businessValidationOverride[0].fields[0].fieldName=extensions \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].constraintType=Extensions \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.jsonPath=$.items[*].productOffering.tags.catalogCode \
  --com.ampp.businessValidationOverride[0].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$ \
  --com.ampp.businessValidationOverride[1].fullClassName=com.example.validatingforminput.perf.PerfRawValidationRequest \
  --com.ampp.businessValidationOverride[1].fields[0].fieldName=extensions \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].constraintType=Extensions \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.jsonPath=$.items[*].productOffering.tags.catalogCode \
  --com.ampp.businessValidationOverride[1].fields[0].constraints[0].params.regexp=^[A-Z]{3}-[0-9]{4}$
```

### Run Gatling

Example:

```bash
./mvnw -Pperformance gatling:test \
  -DbaseUrl=http://localhost:8080 \
  -Drps=50 -Dduration=5 -Dwarmup=2 \
  -DbodyMode=map \
  -DpayloadShape=deep \
  -Drun.label=deep-map-deep
```

The most useful HTTP comparison set is the same deep payload across all three modes:

- `off/map`
- `off/raw`
- `shallow/map`
- `shallow/raw`
- `deep/map`
- `deep/raw`

Use `payloadShape=deep` for those runs so the shallow and deep validators inspect the same body.

The measured model under test is a shopping-cart-shaped payload carried inside the `extensions` field. The wrapper request body around it now uses cart-domain baseline fields such as `cartId`, `customerId`, `currency`, and `totalAmount`.

Generated Gatling reports go under `target/gatling/`.

## Current report

The latest combined write-up is in [extensions-validator-benchmark-report.md](/Users/hectorad/Developer/gs-validating-form-input/complete/docs/extensions-validator-benchmark-report.md).
