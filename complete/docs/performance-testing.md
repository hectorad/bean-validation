# Performance Testing

The repo now supports a Maven-first JMH benchmark for measuring the steady-state validation cost of the `Extensions` constraint.

## Run the benchmark

Use the Maven wrapper from the repo root:

```bash
./mvnw jmh:benchmark -Djmh.benchmarks=ExtensionsValidatorBenchmark
```

For a fast smoke run while you are iterating on the benchmark itself:

```bash
./mvnw jmh:benchmark \
  -Djmh.benchmarks=ExtensionsValidatorBenchmark \
  -Djmh.f=1 -Djmh.wi=1 -Djmh.i=1 -Djmh.w=200ms -Djmh.r=200ms
```

The default configuration is tuned for local comparisons:

- average time mode
- microseconds output
- 2 forks
- 5 warmup iterations at 1 second each
- 10 measurement iterations at 1 second each

Results are written to `target/jmh-results/extensions-validator.json`.

## What it measures

`ExtensionsValidatorBenchmark` runs five scenarios against `Validator.validate(PersonForm)`:

- `baseline_shallow_payload`
- `shallow_path_on_shallow_payload`
- `baseline_deep_payload`
- `shallow_path_on_deep_payload`
- `deep_path_on_deep_payload`

Those comparisons let you separate three costs:

- extension validator overhead on a small payload
- larger payload overhead while keeping JSONPath shallow
- extra traversal depth and wildcard iteration cost on the same deep payload

## Payload shapes

The benchmark uses `Map<String, Object>` payloads instead of raw JSON strings.

- shallow payload: top-level `vendorExtensionCode`
- deep payload: top-level `vendorExtensionCode` plus `vendor.contact.codes[*].value` with exactly 3 matching candidates

That means `shallow_path_on_deep_payload` and `deep_path_on_deep_payload` run on the same body, which keeps payload size constant while changing only the JSONPath traversal work.

## Notes

The benchmark validates its own setup before timing starts:

- shallow validator accepts the shallow payload
- shallow validator accepts the deep payload
- deep validator accepts the deep payload
- invalid shallow and deep variants each produce exactly one violation

If you want an end-to-end HTTP load test instead of isolated validator cost, the existing Gatling profile is still available under `src/gatling/java`.
