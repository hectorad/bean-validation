# Configuration Guide

This guide covers everything you need to configure, customize, and extend the config-driven validation override framework.

For a deep dive into the internal pipeline, see [validation-constraint-mapping.md](validation-constraint-mapping.md).

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration Reference](#configuration-reference)
- [Merge Behavior](#merge-behavior)
- [Custom Messages](#custom-messages)
- [Extensions Validation](#extensions-validation)
- [Custom Contributors](#custom-contributors)
- [Message Validation](#message-validation)
- [Error Handling](#error-handling)
- [Architecture Overview](#architecture-overview)
- [Troubleshooting](#troubleshooting)

---

## Overview

This framework provides a config-driven validation override system built on Jakarta Bean Validation (Hibernate Validator). It lets you enhance or tighten validation constraints defined via Java annotations through external YAML configuration, without changing source code.

**Key capabilities:**

- Override or strengthen `@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Size`, and `@Pattern` constraints from YAML
- Add JSONPath + regex validation rules for `Map<String, Object>` extension fields
- Validate Spring `Message<?>` payloads with a reusable `MessageHandler` decorator
- Merge strategy that always selects the stricter constraint (configuration cannot weaken baseline annotations)
- Pluggable contributor SPI (`FieldConstraintContributor`) for programmatic constraint sources
- Configuration is validated eagerly at startup while global validation is enabled; invalid override config prevents the application from booting

**When to use this:** When the same domain model is shared across services or environments that need different validation strictness levels, configurable without code changes. Examples include per-tenant validation rules, environment-specific constraints (dev vs. production), or multi-region regulatory differences.

---

## Prerequisites

**Runtime requirements:**

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 | Required toolchain baseline |
| Spring Boot | 3.5.x | `spring-boot-starter-validation` required |
| Hibernate Validator | (transitive) | Included via `spring-boot-starter-validation` |
| JSONPath | (optional) | `com.jayway.jsonpath:json-path`, only needed for `extensions` constraint |
| Spring Messaging | (transitive/optional) | `org.springframework:spring-messaging`, used when wrapping `MessageHandler` delegates for payload validation |

**Maven dependencies:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- Only needed if using extensions constraint -->
<dependency>
    <groupId>com.jayway.jsonpath</groupId>
    <artifactId>json-path</artifactId>
</dependency>
```

**Gradle dependencies:**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-web'
// Only needed if using extensions constraint
implementation 'com.jayway.jsonpath:json-path'
```

**Build and verification:** Maven is the authoritative build. Run `./mvnw test` for canonical verification. Gradle remains supported with the same Java 21 toolchain baseline via `./gradlew test`.

**Auto-configuration:** The framework registers itself via Spring Boot's `AutoConfiguration.imports` mechanism. No `@Import` or `@ComponentScan` is needed. Default beans back off when the application provides replacements, and the customizer only activates when Hibernate Validator is available.

---

## Quick Start

### Step 1: Annotate your domain model with baseline constraints

```java
public class PersonForm {

    @NotNull
    @Size(min = 3, max = 30)
    private String name;

    @NotNull
    @Min(18)
    @Max(60)
    private Integer age;

    // getters and setters
}
```

### Step 2: Add validation overrides in `application.yml`

```yaml
com.ampp:
  businessValidationOverride:
    - fullClassName: com.example.validatingforminput.PersonForm
      fields:
        - fieldName: name
          constraints:
            - constraintType: Size
              params:
                min: 5
        - fieldName: age
          constraints:
            - constraintType: Min
              params:
                value: 21
```

### Step 3: Use validation as normal

```java
@PostMapping("/")
public String checkPersonInfo(@Valid PersonForm personForm, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return "form";
    }
    return "redirect:/results";
}
```

The overrides are applied transparently. With the configuration above:

- `name` now requires a minimum of 5 characters (up from 3 in the annotation)
- `age` now requires a minimum of 21 (up from 18 in the annotation)

The framework merges your YAML constraints with your annotation-based constraints at startup. The stricter value always wins.

---

## Configuration Reference

### Top-Level Structure

The configuration root is `com.ampp.businessValidationOverride`, which is a list of class mappings.

```yaml
com.ampp:
  validation-enabled: true
  request-validation-bypass:
    enabled: false
    header-name: X-Skip-Validation
    header-value: true
  businessValidationOverride:
    - fullClassName: <fully-qualified class name>  # required
      fields:                                        # required, non-empty
        - fieldName: <field name>                   # required
          constraints:                              # list of constraint entries
            - constraintType: <constraint type>     # required
              params:                               # type-specific parameters
                # ...
              message: <optional message>
```

Each class mapping identifies a Java class by its fully-qualified name and lists the fields to configure. Each field mapping specifies one or more constraint entries. This subtree is bound and validated only when `validation-enabled=true`.

---

### Top-Level Flags

| Property | Type | Default | Description |
|---|---|---|---|
| `validation-enabled` | `boolean` | `true` | Global kill switch. When `false`, Bean Validation is disabled for MVC, method validation, and direct validator usage, and `businessValidationOverride` is ignored. |
| `request-validation-bypass.enabled` | `boolean` | `false` | Enables a per-request validation bypass controlled by a trusted request header. |
| `request-validation-bypass.header-name` | `String` | `X-Skip-Validation` | Header name checked on the current servlet request. |
| `request-validation-bypass.header-value` | `String` | `true` | Exact header value required to bypass validation. |
| `message-validation.enabled` | `boolean` | `false` | Enables auto-configuration of `MessageValidationHandlerFactory` for wrapping user-supplied Spring `MessageHandler` delegates. |

```yaml
com.ampp:
  validation-enabled: true
  request-validation-bypass:
    enabled: true
    header-name: X-Skip-Validation
    header-value: true
```

Precedence is explicit: `validation-enabled=false` disables validation everywhere first and skips binding and validating `businessValidationOverride` entirely. When global validation remains enabled, the request-bypass header can skip validation only for work that runs on the current HTTP request thread. Startup validation and non-request code paths still validate normally.

**Trust boundary:** Treat the bypass header as an internal escape hatch. External traffic should not be allowed to set it directly; strip or overwrite it at the gateway or proxy layer before requests reach the application.

---

### Message Validation

Enable the message-validation module when you want to validate `Message<?>` payloads before a Spring `MessageHandler` delegate processes them.

```yaml
com.ampp:
  message-validation:
    enabled: true
```

The auto-configuration contributes a `messageValidationHandlerFactory` bean. It does not auto-create handlers, channels, or flows. Use the factory to wrap your own delegate `MessageHandler`.

The resulting decorator validates `message.getPayload()` with the existing `ExternalPayloadValidator`, forwards the original `Message<?>` unchanged when valid, and throws `MessagePayloadValidationException` when invalid.

Example direct construction:

```java
@Bean
MessageHandler validatedHandler(ExternalPayloadValidator externalPayloadValidator, MessageHandler delegate) {
    return new ValidatingPayloadMessageHandler(delegate, externalPayloadValidator);
}
```

Example factory usage:

```java
@Bean
MessageHandler validatedHandler(MessageValidationHandlerFactory factory, MessageHandler delegate) {
    return factory.create(delegate);
}
```

Invalid payloads surface through `MessagePayloadValidationException`, which extends `MessageHandlingException` and exposes the `ValidationResult<?>`.

---

### Constraint Entries

Each `fields[].constraints[]` item uses the same envelope:

| Property | Type | Description |
|---|---|---|
| `constraintType` | `String` | Required. One of `NotNull`, `NotBlank`, `Min`, `Max`, `DecimalMin`, `DecimalMax`, `Size`, `Pattern`, or `Extensions`. |
| `message` | `String` | Optional custom message for the entry. |
| `params` | `Object` | Optional type-specific parameters. |

#### `NotNull` and `NotBlank`

Use the entry itself to enable the constraint; no `params` are required.

```yaml
- constraintType: NotNull
  message: This field is required

- constraintType: NotBlank
  message: Name must not be blank
```

`NotBlank` only applies to `CharSequence` fields. Both use OR semantics: once the baseline or any config entry enables them, they stay enabled.

#### `Min` and `Max`

Use `params.value` for integer bounds:

```yaml
- constraintType: Min
  params:
    value: 21
  message: Must be at least 21

- constraintType: Max
  params:
    value: 55
  message: Must be at most 55
```

`Min` and `DecimalMin` compete for the strictest lower bound. `Max` and `DecimalMax` compete for the strictest upper bound.

#### `DecimalMin` and `DecimalMax`

Use `params.value` plus optional `params.inclusive`:

```yaml
- constraintType: DecimalMin
  params:
    value: 1000.50
    inclusive: false
  message: Salary must be greater than 1000.50

- constraintType: DecimalMax
  params:
    value: 250000.00
  message: Salary must be at most 250000.00
```

Setting `inclusive` without `value` is invalid and causes that field mapping to be skipped with a warning.

#### `Size`

Use `params.min` and/or `params.max`. If you want a custom violation message, provide one top-level `message` for the entry.

```yaml
- constraintType: Size
  params:
    min: 5
    max: 100
  message: Must be between 5 and 100 characters
```

`Size` only applies to `String`, `Collection`, `Map`, and array fields. `min` uses higher-wins; `max` uses lower-wins.

#### `Pattern`

Each `Pattern` entry represents exactly one regex. Use multiple entries when multiple regexes must all match.

```yaml
- constraintType: Pattern
  params:
    regexp: "^[A-Za-z ]+$"
  message: Invalid name format

- constraintType: Pattern
  params:
    regexp: "^[A-Z].*"
  message: Must start with an uppercase letter
```

Patterns are cumulative. Duplicate regex identities collapse into one effective rule, and a later configured message can replace the earlier one for that same regex identity.

#### `Extensions`

Each `Extensions` entry represents one JSONPath/regex rule.

```yaml
- constraintType: Extensions
  params:
    jsonPath: $.vendorExtensionCode
    regexp: "^[A-Z]{3}-[0-9]{4}$"
  message: Vendor extension code format is invalid
```

The field type must be `Map`, `Collection`, array, or `CharSequence`. See [Extensions Validation](#extensions-validation) for runtime behavior details.

---

## Merge Behavior

The framework merges baseline constraints (from annotations) with configured constraints (from YAML and any custom contributors) using a **strictest-wins** policy. The result is never weaker than the baseline.

### Merge Rules Summary

| Constraint Type | Merge Rule | Example |
|---|---|---|
| `NotNull` / `NotBlank` | OR -- any enabled entry wins | Baseline `@NotNull` + config entry = still required |
| `Min` / `DecimalMin` | Higher value wins | Baseline `@Min(18)` + config `value: 16` = 18 |
| `Max` / `DecimalMax` | Lower value wins | Baseline `@Max(60)` + config `value: 70` = 60 |
| `Size.params.min` | Higher value wins | `@Size(min=3)` + config `5` = 5 |
| `Size.params.max` | Lower value wins | `@Size(max=30)` + config `25` = 25 |
| Equal numeric bounds | Exclusive beats inclusive | Both at `10`: exclusive wins over inclusive |
| `Pattern` | Cumulative by identity (all distinct rules must match) | Baseline + config patterns both enforced; duplicate regex rules collapse |
| `Extensions` | Additive across contributors | Rules from all sources are combined |

### Walkthrough Example

Given `PersonForm.name` with these baseline annotations:

```java
@NotNull
@NotBlank
@Size(min = 3, max = 30)
@Pattern(regexp = "^[A-Za-z ]+$")
private String name;
```

And this YAML configuration:

```yaml
- fieldName: name
  constraints:
    - constraintType: Size
      params:
        min: 20
        max: 30
    - constraintType: Pattern
      params:
        regexp: "^[A-Za-z ]+$"
```

The merge produces:

| Constraint | Baseline | Config | Effective | Reason |
|---|---|---|---|---|
| notNull | `true` | (not set) | `true` | Baseline preserved |
| notBlank | `true` | (not set) | `true` | Baseline preserved |
| sizeMin | `3` | `20` | **`20`** | max(3, 20) -- config is stricter |
| sizeMax | `30` | `30` | `30` | min(30, 30) -- equal |
| patterns | `^[A-Za-z ]+$` | `^[A-Za-z ]+$` | Both active | Patterns accumulate |

> **Invalid merged bounds:** If the merged min exceeds the merged max (for numeric or size bounds), the framework logs a warning and skips the affected field mapping. Other valid mappings still apply.

---

## Custom Messages

Every constraint entry accepts an optional `message` property that overrides the default Jakarta Validation message template.

### Rules

- When a configured constraint "wins" the merge, its custom message is used at runtime.
- When the baseline annotation wins (because it is stricter), the custom message from config is discarded and the default Jakarta message template is used (e.g., `{jakarta.validation.constraints.Size.message}`).
- The first contributor that produces the winning bound keeps its message. Later contributors with equal strictness do not replace the message.

### Size Messages

`Size` uses the entry's top-level `message`.

```yaml
- constraintType: Size
  params:
    min: 20
    max: 30
  message: Name must be between 20 and 30 characters
```

If both bounds are present, the same message is used for either a too-short or too-long violation.

### Pattern Messages

Each `Pattern` entry has its own `message`. Baseline patterns from `@Pattern` annotations keep their own default messages.

### Extensions Messages

Each `Extensions` entry has its own independent `message` property.

---

## Extensions Validation

The `extensions` constraint validates entries in a `Map<String, Object>` field using JSONPath expressions paired with regular expressions.

### Restrictions

- The target field type must be `Map`, `Collection`, array, or `CharSequence`.

### Runtime Behavior

| Scenario | Result |
|---|---|
| `extensions` field is `null` | Validation passes |
| JSONPath resolves to nothing (`PathNotFoundException`) | Validation passes (path acts as a precondition) |
| JSONPath resolves to a scalar value | Regex is applied to the value |
| JSONPath resolves to a list | Each element is validated independently |
| Resolved value is `null` | That value is skipped |
| Resolved value is non-scalar (e.g., a nested object) | Validation fails |
| Regex does not match a scalar value | Validation fails |

### Example: Single Value

```yaml
- constraintType: Extensions
  params:
    jsonPath: $.vendorExtensionCode
    regexp: "^[A-Z]{3}-[0-9]{4}$"
  message: Vendor extension code format is invalid
```

Validates that `extensions.get("vendorExtensionCode")` matches the pattern `ABC-1234`.

### Example: Multi-Value JSONPath

```yaml
- constraintType: Extensions
  params:
    jsonPath: $.items[*].code
    regexp: "^[A-Z]{2}-\\d{3}$"
  message: Item code format is invalid
```

Evaluates `$.items[*].code` against the extensions map, then validates each resolved code element against the regex.

### Multiple Rules

Multiple rules can be defined. Each generates an independent constraint, and all must pass:

```yaml
- constraintType: Extensions
  params:
    jsonPath: $.vendorCode
    regexp: "^[A-Z]{3}$"
- constraintType: Extensions
  params:
    jsonPath: $.regionId
    regexp: "^\\d{4}$"
```

---

## Custom Contributors

The framework supports a pluggable SPI for providing constraint overrides from sources beyond YAML properties.

### The `FieldConstraintContributor` Interface

```java
public interface FieldConstraintContributor {

    Optional<ValidationProperties.Constraints> contribute(
        String className,
        String fieldName,
        BaselineFieldConstraints baseline
    );
}
```

Each contributor is invoked for every resolved field. It returns `Optional.empty()` if it has no constraints for that field, or a `Constraints` object that participates in the merge.

### Built-in Contributor

`PropertiesValidationOverrideContributor` adapts `com.ampp.businessValidationOverride` properties into the contributor interface. It is registered at `@Order(0)`.

### Writing a Custom Contributor

Register your contributor as a Spring bean with an `@Order` annotation:

```java
@Bean
@Order(-10)  // Runs before the properties contributor
public FieldConstraintContributor myCustomContributor() {
    return (className, fieldName, baseline) -> {
        if ("com.example.MyForm".equals(className) && "email".equals(fieldName)) {
            ValidationProperties.Constraints constraints = new ValidationProperties.Constraints();
            constraints.getNotBlank().setValue(true);
            constraints.getNotBlank().setMessage("Email is required");
            return Optional.of(constraints);
        }
        return Optional.empty();
    };
}
```

### Ordering Rules

- Contributors are sorted by Spring `@Order` (lower value runs first).
- The built-in properties contributor runs at `@Order(0)`.
- For numeric bounds and boolean constraints: exact strictness ties keep the **first** contributor's value and message.
- For patterns and extensions: rules are **additive** in contributor order.
- To beat the properties contributor on an exact tie, use an order lower than `0`.

---

## Error Handling

Configuration binding remains strict, but post-binding override processing is tolerant. Structurally invalid configuration still fails startup; invalid override mappings are logged and skipped so other valid mappings can continue to apply during startup and refresh.

### Configuration Binding Errors

Spring validates `ValidationProperties` on binding:

| Error | Cause |
|---|---|
| `fullClassName` is blank | Missing or empty class name |
| `fields` list is empty | No fields configured for a class |
| `fieldName` is blank | Missing or empty field name |
| `constraintType` is blank | Missing or empty constraint entry type |

### Metadata Resolution Warnings

`GeneratedClassMetadataCache` validates that configured classes and fields exist. When a mapping is invalid, it is skipped and a warning is logged:

| Error Message | Cause |
|---|---|
| `Configured class was not found: <className>` | Class not on classpath (check for typos) |
| `Configured field was not found. class=<class>, field=<field>` | Field does not exist on the class |

### Contributor/Registry Warnings

Duplicate or malformed contributor entries are also skipped with warnings:

| Warning Pattern | Cause |
|---|---|
| `Skipping duplicate validation override class mapping...` | Same class listed twice within one contributor |
| `Skipping duplicate validation override field mapping...` | Same field listed twice within one contributor/class |
| `className is missing` | Contributor entry omitted or blanked the class name |
| `fieldName is missing` | Contributor entry omitted or blanked the field name |

### Type Compatibility Warnings

| Error Message Pattern | Cause |
|---|---|
| `Constraint notBlank is not supported for...fieldType=java.lang.Integer` | `NotBlank` on a non-String field |
| `Constraint numeric bounds is not supported for...` | `Min` / `Max` / `DecimalMin` / `DecimalMax` on an unsupported type (e.g., `Boolean`, `Double`) |
| `Constraint size is not supported for...` | `Size` on a non-container field |
| `Constraint pattern is not supported for...` | `Pattern` on a non-String field |

### Merge-Time Warnings

| Error Message Pattern | Cause |
|---|---|
| `effectiveMin > effectiveMax` | Merged lower bound exceeds upper bound |
| `effectiveSizeMin > effectiveSizeMax` | Merged size min exceeds size max |
| `equal bounds cannot be exclusive` | Same value for min and max with either being exclusive |
| `<inclusive property> requires <value property>` | `inclusive` set without a corresponding `value` |
| `regex could not be compiled` | Invalid regex syntax |
| `jsonPath could not be compiled` | Invalid JSONPath syntax |
| `must be >= 0` | Negative size value |
| `exceeds Integer.MAX_VALUE` | Size value too large |
### Example: Invalid Range

```yaml
# This mapping is skipped with a warning:
# baseline @Min(18) stays at 18, config max is 10 -> 18 > 10
- fieldName: age
  constraints:
    - constraintType: Max
      params:
        value: 10
```

Warning: `Invalid numeric constraints. effectiveMin > effectiveMax for class=..., field=age, effectiveMin=18 (inclusive=true), effectiveMax=10 (inclusive=true)`

---

## Architecture Overview

The validation pipeline has four stages:

```
application.yml                Java annotations
      |                              |
      v                              v
ValidationProperties    GeneratedClassMetadataCache
      |                     (extracts baseline)
      v                              |
PropertiesValidationOverrideContributor
      |                         ResolvedClassMapping
      |                            + baseline
      +------> ConstraintMergeService <------+
              (strictest-wins merge)
                       |
                       v
              EffectiveFieldConstraints
                       |
                       v
       ConfigDrivenConstraintMappingContributor
          (Hibernate Validator programmatic API)
                       |
                       v
               Runtime validation
```

**Key classes:**

| Class | Responsibility |
|---|---|
| `ValidationProperties` | Spring `@ConfigurationProperties` model for YAML config |
| `GeneratedClassMetadataCache` | Resolves classes/fields, extracts baseline annotations, validates compatibility |
| `FieldConstraintContributor` | SPI for pluggable constraint sources |
| `PropertiesValidationOverrideContributor` | Built-in contributor backed by `com.ampp.businessValidationOverride` properties |
| `ConstraintMergeService` | Merges baseline + all contributed constraints using strictness rules |
| `ConfigDrivenConstraintMappingContributor` | Translates merged constraints into Hibernate Validator programmatic mappings |
| `ValidationAutoConfiguration` | Spring Boot auto-configuration that wires all beans |

**Data model records:**

| Record | Purpose |
|---|---|
| `BaselineFieldConstraints` | Constraints extracted from Java annotations |
| `EffectiveFieldConstraints` | Final merged constraints with messages |
| `NumericBound` | Numeric value + inclusive flag |
| `PatternRule` | Regex + optional message |
| `ExtensionRegexRule` | JSONPath + regex + optional message |

For a detailed walkthrough of the internal pipeline, see [validation-constraint-mapping.md](validation-constraint-mapping.md).

---

## Troubleshooting

**My override seems to have no effect.**
The merge algorithm always selects the stricter value. If your configured value is less strict than the annotation baseline (for example a `Min` entry with `params.value: 10` while the annotation is `@Min(18)`), the baseline wins.

**I see "Configured class was not found."**
Ensure `fullClassName` is the fully-qualified class name (e.g., `com.example.MyForm`, not `MyForm`) and the class is on the classpath. The invalid mapping is skipped; other valid mappings continue to load.

**I see "Configured field was not found."**
The `fieldName` must match the Java field name exactly (case-sensitive). It must be a declared field on the class (or a superclass), not a method-only property. The invalid mapping is skipped and does not block startup or refresh.

**I get "Constraint notBlank is not supported for field type Integer."**
`NotBlank` only works on `CharSequence` (String) fields. Remove the `NotBlank` entry from the numeric field configuration.

**How do I make a field accept null when it is annotated with `@NotNull`?**
You cannot. The framework uses OR semantics for boolean constraints: if any source (annotation or config) says `true`, the constraint is enabled. This is by design to prevent accidental weakening of validation rules.

**Can I configure validation for fields that have no annotations?**
Yes. The field just needs to exist on the class. You can add any supported constraint purely from YAML, and the merge will treat the baseline as empty.

**What happens if I set both `Min` and `DecimalMin` for the same field?**
Both sources compete. The framework compares all candidates (baseline annotation, `Min.params.value`, `DecimalMin.params.value`) and selects the strictest lower bound.

**Does the `Pattern` message apply per-regex or to the whole block?**
Each `Pattern` entry carries its own message. If you want different messages for different regexes, add multiple `Pattern` entries.

**Can I use Spring profiles to vary validation overrides per environment?**
Yes. Use standard Spring Boot profile-specific configuration files (e.g., `application-prod.yml`, `application-staging.yml`). The framework reads from whatever configuration source Spring resolves at runtime.

**How do `Size` messages work?**
`Size` entries accept a single top-level `message`. If the entry defines both `params.min` and `params.max`, that same message is used for either bound violation.
