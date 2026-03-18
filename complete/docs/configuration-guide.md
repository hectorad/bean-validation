# Configuration Guide

This project is now a Maven multi-module validation framework centered on `validation-spring-boot-starter`.

The framework namespace is:

- Java packages: `io.github.hectorad.validation`
- Spring Boot properties: `hector.validation`

For the internal mapping pipeline, see [validation-constraint-mapping.md](validation-constraint-mapping.md).

## Modules

- `validation-core`: public API, override model, merge policy, `ExternalPayloadValidator`
- `validation-hibernate`: Hibernate Validator mapping, metadata extraction, troubleshooting
- `validation-spring-boot-autoconfigure`: Boot properties, bean wiring, bypass strategy, default beans
- `validation-feign`: optional Feign response validation auto-configuration
- `validation-kafka`: optional Kafka record validation auto-configuration
- `validation-spring-boot-starter`: consumer-facing starter
- `validation-sample-app`: demo MVC application

## Quick Start

Add the starter:

```xml
<dependency>
    <groupId>io.github.hectorad.validation</groupId>
    <artifactId>validation-spring-boot-starter</artifactId>
    <version>${validation.version}</version>
</dependency>
```

Define baseline constraints in code:

```java
public class PersonForm {

    @NotNull
    @NotBlank
    @Size(min = 3, max = 30)
    private String name;

    @NotNull
    @Min(18)
    @Max(60)
    private Integer age;
}
```

Add overrides in `application.yml`:

```yaml
hector:
  validation:
    overrides:
      - class-name: com.acme.forms.PersonForm
        fields:
          - field-name: name
            constraints:
              size:
                min:
                  value: 5
                  message: Name must have at least 5 characters
          - field-name: age
            constraints:
              min:
                value: 21
                message: Age must be at least 21
```

Use validation normally through MVC, method validation, or `ExternalPayloadValidator`.

## Property Reference

### Top-level flags

| Property | Type | Default | Description |
|---|---|---|---|
| `hector.validation.enabled` | `boolean` | `true` | Global validation switch |
| `hector.validation.fail-on-error` | `boolean` | `true` | Fail fast on invalid override metadata |
| `hector.validation.feign.enabled` | `boolean` | `false` | Enable Feign payload validation |
| `hector.validation.kafka.enabled` | `boolean` | `false` | Enable Kafka record validation |

### HTTP bypass

| Property | Type | Default | Description |
|---|---|---|---|
| `hector.validation.http-bypass.enabled` | `boolean` | `false` | Enables servlet header-based bypass |
| `hector.validation.http-bypass.header-name` | `String` | `X-Skip-Validation` | Header name to inspect |
| `hector.validation.http-bypass.header-value` | `String` | `true` | Exact value required to bypass |

Example:

```yaml
hector:
  validation:
    http-bypass:
      enabled: true
      header-name: X-Skip-Validation
      header-value: true
```

Notes:

- The bypass strategy is only registered in servlet applications.
- `hector.validation.enabled=false` still wins globally.
- Treat the bypass header as an internal control, not a public API.

### Override structure

```yaml
hector:
  validation:
    overrides:
      - class-name: com.acme.forms.PersonForm
        fields:
          - field-name: name
            constraints:
              not-null:
                value: true
                message: Name is required
              not-blank:
                value: true
                message: Name must not be blank
              size:
                min:
                  value: 4
                  message: Name is too short
                max:
                  value: 40
                  message: Name is too long
              pattern:
                regexes:
                  - "^[A-Za-z]+$"
                flags:
                  - CASE_INSENSITIVE
                message: Name must contain only letters
          - field-name: age
            constraints:
              min:
                value: 21
              decimal-max:
                value: 59.5
                inclusive: false
          - field-name: extensions
            constraints:
              extensions:
                rules:
                  - json-path: $.vendorExtensionCode
                    regex: "^[A-Z]{3}-[0-9]{4}$"
                    message: Vendor extension code is invalid
```

### Supported constraint blocks

| Constraint | Properties |
|---|---|
| `not-null` | `value`, `message` |
| `not-blank` | `value`, `message` |
| `min` | `value`, `message` |
| `max` | `value`, `message` |
| `decimal-min` | `value`, `inclusive`, `message` |
| `decimal-max` | `value`, `inclusive`, `message` |
| `size.min` | `value`, `message` |
| `size.max` | `value`, `message` |
| `pattern` | `regexes`, `flags`, `message` |
| `extensions.rules[]` | `json-path`, `regex`, `message` |

## Merge Behavior

The merge policy lives in `ConstraintMergeService`.

Rules:

- Configuration can strengthen baseline constraints, not weaken them.
- Boolean constraints (`not-null`, `not-blank`) use effective OR semantics.
- Baseline boolean constraints can still take an override message from the contributor pipeline.
- Lower bounds choose the strictest value.
- Upper bounds choose the strictest value.
- Equal-strength ties keep the earlier contributor.
- `pattern` and `extensions` rules are additive in contributor order.
- Invalid effective combinations like `min > max` fail startup when `fail-on-error=true`.

## SPI: Programmatic Overrides

The public SPI is `ValidationOverrideContributor`.

```java
@Component
@Order(-100)
class TenantValidationContributor implements ValidationOverrideContributor {

    @Override
    public List<ClassValidationOverride> getValidationOverrides() {
        ConstraintOverrideSet constraints = new ConstraintOverrideSet();
        constraints.getSize().getMin().setValue(8L);
        constraints.getSize().getMin().setMessage("Name must have at least 8 characters");

        return List.of(
            new ClassValidationOverride(
                "com.acme.forms.PersonForm",
                List.of(new FieldValidationOverride("name", constraints))));
    }
}
```

Notes:

- Spring ordering controls contributor precedence.
- Property-backed overrides are exposed through the built-in `PropertiesValidationOverrideContributor`.
- Contributors may introduce new target classes and fields without any YAML entry.
- Troubleshooting and runtime validation both read from the same contributor pipeline.

## Adapter Modules

### Feign

Set:

```yaml
hector:
  validation:
    feign:
      enabled: true
```

When Feign is on the classpath, decoded responses are validated and invalid payloads raise `FeignResponseValidationException`.

### Kafka

Set:

```yaml
hector:
  validation:
    kafka:
      enabled: true
```

When Spring Kafka is on the classpath, the framework registers a validating record interceptor and delegates invalid payload handling to `KafkaValidationFailureHandler`.

## Troubleshooting

Common causes of startup failure:

- `class-name` does not exist on the application classpath
- `field-name` does not exist on the target class
- `not-blank` is applied to a non-`CharSequence` field
- numeric or size constraints are applied to unsupported field types
- `decimal-min.inclusive` or `decimal-max.inclusive` is set without a corresponding value
- effective bounds become invalid after merging

Canonical verification:

```bash
./mvnw test
```
