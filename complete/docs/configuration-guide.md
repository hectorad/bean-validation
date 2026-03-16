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
- [Pattern Flags](#pattern-flags)
- [Custom Contributors](#custom-contributors)
- [Error Handling](#error-handling)
- [Architecture Overview](#architecture-overview)
- [Troubleshooting](#troubleshooting)

---

## Overview

This framework provides a config-driven validation override system built on Jakarta Bean Validation (Hibernate Validator). It lets you enhance or tighten validation constraints defined via Java annotations through external YAML configuration, without changing source code.

**Key capabilities:**

- Override or strengthen `@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`, `@Size`, and `@Pattern` constraints from YAML
- Add JSONPath + regex validation rules for `Map<String, Object>` extension fields
- Merge strategy that always selects the stricter constraint (configuration cannot weaken baseline annotations)
- Pluggable contributor SPI (`FieldConstraintContributor`) for programmatic constraint sources
- All configuration is validated eagerly at startup; invalid config prevents the application from booting

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
  business-validation-override:
    - full-class-name: com.example.validatingforminput.PersonForm
      fields:
        - field-name: name
          constraints:
            size:
              min:
                value: 5
        - field-name: age
          constraints:
            min:
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

The configuration root is `com.ampp.business-validation-override`, which is a list of class mappings.

```yaml
com.ampp:
  validation-enabled: true
  request-validation-bypass:
    enabled: false
    header-name: X-Skip-Validation
    header-value: true
  business-validation-override:
    - full-class-name: <fully-qualified class name>  # required
      fields:                                         # required, non-empty
        - field-name: <field name>                    # required
          constraints:                                # constraint blocks
            # ...
```

Each class mapping identifies a Java class by its fully-qualified name and lists the fields to configure. Each field mapping specifies one or more constraint blocks.

---

### Top-Level Flags

| Property | Type | Default | Description |
|---|---|---|---|
| `validation-enabled` | `boolean` | `true` | Global kill switch. When `false`, Bean Validation is disabled for MVC, method validation, and direct validator usage. |
| `fail-on-error` | `boolean` | `true` | Controls whether invalid override metadata fails fast during startup. |
| `request-validation-bypass.enabled` | `boolean` | `false` | Enables a per-request validation bypass controlled by a trusted request header. |
| `request-validation-bypass.header-name` | `String` | `X-Skip-Validation` | Header name checked on the current servlet request. |
| `request-validation-bypass.header-value` | `String` | `true` | Exact header value required to bypass validation. |

```yaml
com.ampp:
  validation-enabled: true
  request-validation-bypass:
    enabled: true
    header-name: X-Skip-Validation
    header-value: true
```

Precedence is explicit: `validation-enabled=false` disables validation everywhere first. When global validation remains enabled, the request-bypass header can skip validation only for work that runs on the current HTTP request thread. Startup validation and non-request code paths still validate normally.

**Trust boundary:** Treat the bypass header as an internal escape hatch. External traffic should not be allowed to set it directly; strip or overwrite it at the gateway or proxy layer before requests reach the application.

---

### `not-null`

Ensures the field value is not `null`.

| Property | Type | Description |
|---|---|---|
| `value` | `Boolean` | Enables the constraint if `true`. |
| `message` | `String` | Custom error message. |

```yaml
not-null:
  value: true
  message: This field is required
```

**Merge rule:** OR semantics. If any source (annotation or config) says `true`, the constraint is enabled. Configuration cannot disable a baseline `@NotNull`.

---

### `not-blank`

Ensures a string field is not `null`, empty, or whitespace-only.

| Property | Type | Description |
|---|---|---|
| `value` | `Boolean` | Enables the constraint if `true`. |
| `message` | `String` | Custom error message. |

```yaml
not-blank:
  value: true
  message: Name must not be blank
```

**Restriction:** Only valid on `CharSequence` (String) fields. Startup fails if applied to a non-string field.

**Merge rule:** OR semantics, same as `not-null`.

---

### `min`

Applies an inclusive numeric lower bound.

| Property | Type | Description |
|---|---|---|
| `value` | `Long` | Override value. |
| `message` | `String` | Custom error message. |

```yaml
min:
  value: 21
  message: Must be at least 21
```

**Supported field types:** `Integer`, `Long`, `Short`, `Byte`, `BigDecimal`, `BigInteger`, `String`.

**Merge rule:** The strictest (highest) lower bound wins across all sources (baseline annotation, `min.value`, `decimal-min.value`). All sources compete and the highest value is selected.

---

### `max`

Applies an inclusive numeric upper bound.

| Property | Type | Description |
|---|---|---|
| `value` | `Long` | Override value. |
| `message` | `String` | Custom error message. |

```yaml
max:
  value: 55
  message: Must be at most 55
```

**Merge rule:** The strictest (lowest) upper bound wins.

---

### `decimal-min`

Applies a decimal lower bound with optional inclusivity control.

| Property | Type | Default | Description |
|---|---|---|---|
| `value` | `BigDecimal` | | Override value. |
| `inclusive` | `Boolean` | `true` | Whether the bound is inclusive. Requires `value`. |
| `message` | `String` | | Custom error message. |

```yaml
decimal-min:
  value: 1000.50
  inclusive: false
  message: Salary must be greater than 1000.50
```

**Important:** Setting `inclusive` without a corresponding `value` causes a startup failure.

**Merge rule:** Competes alongside `min` for the strictest lower bound. When two bounds have the same numeric value, exclusive is stricter than inclusive.

---

### `decimal-max`

Applies a decimal upper bound with optional inclusivity control.

| Property | Type | Default | Description |
|---|---|---|---|
| `value` | `BigDecimal` | | Override value. |
| `inclusive` | `Boolean` | `true` | Whether the bound is inclusive. Requires `value`. |
| `message` | `String` | | Custom error message. |

```yaml
decimal-max:
  value: 250000.00
  inclusive: true
  message: Salary must be at most 250000.00
```

**Merge rule:** Competes alongside `max` for the strictest upper bound.

---

### `size`

Constrains the size of a `String`, `Collection`, `Map`, or array.

The `min` and `max` sub-properties each accept:

| Property | Type | Description |
|---|---|---|
| `value` | `Long` | Override value. Must be >= 0 and <= `Integer.MAX_VALUE`. |
| `message` | `String` | Custom error message. |

```yaml
size:
  min:
    value: 5
    message: Must have at least 5 characters
  max:
    value: 100
    message: Must have at most 100 characters
```

**Restriction:** Only valid on `String`, `Collection`, `Map`, and array fields.

**Merge rule:** `size.min` uses higher-wins (stricter lower bound). `size.max` uses lower-wins (stricter upper bound).

**Message behavior:** When `min` and `max` have different messages, the framework emits two separate `@Size` constraints so each side displays its own message.

---

### `pattern`

Validates a string against one or more regular expressions.

| Property | Type | Description |
|---|---|---|
| `regexes` | `List<String>` | One or more regex patterns. All must match. |
| `flags` | `List<String>` | Pattern flag names (see [Pattern Flags](#pattern-flags)). |
| `message` | `String` | Shared message for all regexes in this block. |

```yaml
pattern:
  regexes:
    - "^[A-Za-z ]+$"
    - "^[A-Z].*"
  flags:
    - CASE_INSENSITIVE
  message: Invalid name format
```

**Restriction:** Only valid on `CharSequence` (String) fields.

**Merge rule:** Patterns are cumulative. Distinct `regex + flags` combinations are all enforced (AND semantics), but duplicate identities are collapsed into one effective rule. If configuration repeats a baseline-equivalent pattern and provides a message, that message becomes the effective message for the single surviving rule.

---

### `extensions`

Validates entries in a `Map<String, Object>` field using JSONPath expressions and regular expressions.

Each rule has:

| Property | Type | Description |
|---|---|---|
| `json-path` | `String` | JSONPath expression to extract a value. Required. |
| `regex` | `String` | Regex to match against the extracted value. Required. |
| `message` | `String` | Custom error message. Optional. |

```yaml
extensions:
  rules:
    - json-path: $.vendorExtensionCode
      regex: "^[A-Z]{3}-[0-9]{4}$"
      message: Vendor extension code format is invalid
```

**Restriction:** The field type must be `Map`, `Collection`, array, or `CharSequence`.

See [Extensions Validation](#extensions-validation) for runtime behavior details.

---

## Merge Behavior

The framework merges baseline constraints (from annotations) with configured constraints (from YAML and any custom contributors) using a **strictest-wins** policy. The result is never weaker than the baseline.

### Merge Rules Summary

| Constraint Type | Merge Rule | Example |
|---|---|---|
| `not-null` / `not-blank` | OR -- any `true` wins | Baseline `@NotNull` + config `false` = still required |
| `min` / `decimal-min` | Higher value wins | Baseline `@Min(18)` + config `min: 16` = 18 |
| `max` / `decimal-max` | Lower value wins | Baseline `@Max(60)` + config `max: 70` = 60 |
| `size.min` | Higher value wins | `@Size(min=3)` + config `5` = 5 |
| `size.max` | Lower value wins | `@Size(max=30)` + config `25` = 25 |
| Equal numeric bounds | Exclusive beats inclusive | Both at `10`: exclusive wins over inclusive |
| `pattern` | Cumulative by identity (all distinct rules must match) | Baseline + config patterns both enforced; duplicate `regex + flags` rules collapse |
| `extensions` | Additive across contributors | Rules from all sources are combined |

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
- field-name: name
  constraints:
    size:
      min:
        value: 20
      max:
        value: 30
    pattern:
      regexes:
        - "^[A-Za-z ]+$"
```

The merge produces:

| Constraint | Baseline | Config | Effective | Reason |
|---|---|---|---|---|
| notNull | `true` | (not set) | `true` | Baseline preserved |
| notBlank | `true` | (not set) | `true` | Baseline preserved |
| sizeMin | `3` | `20` | **`20`** | max(3, 20) -- config is stricter |
| sizeMax | `30` | `30` | `30` | min(30, 30) -- equal |
| patterns | `^[A-Za-z ]+$` | `^[A-Za-z ]+$` | Both active | Patterns accumulate |

> **Fail-fast:** If the merged min exceeds the merged max (for numeric or size bounds), the application fails to start. This is intentional to catch configuration errors early.

---

## Custom Messages

Every constraint block accepts an optional `message` property that overrides the default Jakarta Validation message template.

### Rules

- When a configured constraint "wins" the merge, its custom message is used at runtime.
- When the baseline annotation wins (because it is stricter), the custom message from config is discarded and the default Jakarta message template is used (e.g., `{jakarta.validation.constraints.Size.message}`).
- The first contributor that produces the winning bound keeps its message. Later contributors with equal strictness do not replace the message.

### Size Messages

Size constraints support independent messages for `min` and `max`:

```yaml
size:
  min:
    value: 20
    message: Name must have at least 20 characters
  max:
    value: 30
    message: Name must have at most 30 characters
```

When `min` and `max` have different messages, the framework emits two separate `@Size` constraints so each side displays its own message. When messages are the same (or both absent), a single `@Size` constraint covers both bounds.

### Pattern Messages

The `message` in a `pattern` block is shared across all regexes in that block. Baseline patterns from `@Pattern` annotations keep their own default messages.

### Extensions Messages

Each extension rule has its own independent `message` property.

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
extensions:
  rules:
    - json-path: $.vendorExtensionCode
      regex: "^[A-Z]{3}-[0-9]{4}$"
      message: Vendor extension code format is invalid
```

Validates that `extensions.get("vendorExtensionCode")` matches the pattern `ABC-1234`.

### Example: Multi-Value JSONPath

```yaml
extensions:
  rules:
    - json-path: $.items[*].code
      regex: "^[A-Z]{2}-\\d{3}$"
      message: Item code format is invalid
```

Evaluates `$.items[*].code` against the extensions map, then validates each resolved code element against the regex.

### Multiple Rules

Multiple rules can be defined. Each generates an independent constraint, and all must pass:

```yaml
extensions:
  rules:
    - json-path: $.vendorCode
      regex: "^[A-Z]{3}$"
    - json-path: $.regionId
      regex: "^\\d{4}$"
```

---

## Pattern Flags

Pattern flags modify regex matching behavior. They correspond to `jakarta.validation.constraints.Pattern.Flag` enum values.

### Supported Flags

| Flag | Description |
|---|---|
| `UNIX_LINES` | Only `\n` is recognized as a line terminator |
| `CASE_INSENSITIVE` | Case-insensitive matching |
| `COMMENTS` | Whitespace and comments in the pattern are ignored |
| `MULTILINE` | `^` and `$` match at line boundaries |
| `DOTALL` | `.` matches any character including line terminators |
| `UNICODE_CASE` | Unicode-aware case folding |
| `CANON_EQ` | Canonical equivalence matching |

### Usage

```yaml
pattern:
  regexes:
    - "^[a-z]+$"
  flags:
    - CASE_INSENSITIVE
    - MULTILINE
  message: Must contain only letters
```

### Important Notes

- Flag names are **case-sensitive**. `case_insensitive` causes a startup failure.
- Flags apply to **all regexes** in the same `pattern` block.
- Baseline patterns from `@Pattern` annotations keep their own flags from the annotation. Config flags do not retroactively affect baseline patterns.
- An invalid flag name causes a startup failure with an error message listing all valid flags.

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

`PropertiesFieldConstraintContributor` adapts `com.ampp.business-validation-override` properties into the contributor interface. It is registered at `@Order(0)`.

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

The framework validates all configuration eagerly at startup. If any validation fails, the Spring application context fails to initialize.

### Configuration Binding Errors

Spring validates `ValidationProperties` on binding:

| Error | Cause |
|---|---|
| `full-class-name` is blank | Missing or empty class name |
| `fields` list is empty | No fields configured for a class |
| `field-name` is blank | Missing or empty field name |

### Metadata Resolution Errors

`GeneratedClassMetadataCache` validates that configured classes and fields exist:

| Error Message | Cause |
|---|---|
| `Configured class was not found: <className>` | Class not on classpath (check for typos) |
| `Configured field was not found. class=<class>, field=<field>` | Field does not exist on the class |
| `Duplicate class mapping found in validation configuration: <className>` | Same class listed twice |
| `Duplicate field mapping found for class <class>: <field>` | Same field listed twice for one class |

### Type Compatibility Errors

| Error Message Pattern | Cause |
|---|---|
| `Constraint notBlank is not supported for...fieldType=java.lang.Integer` | `not-blank` on a non-String field |
| `Constraint numeric bounds is not supported for...` | Numeric constraint on an unsupported type (e.g., `Boolean`, `Double`) |
| `Constraint size is not supported for...` | `size` on a non-container field |
| `Constraint pattern is not supported for...` | `pattern` on a non-String field |

### Merge-Time Errors

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
| `Invalid pattern flag '<flag>'` | Unrecognized flag name (error lists valid flags) |

### Example: Invalid Range

```yaml
# This causes a startup failure:
# baseline @Min(18) stays at 18, config max is 10 -> 18 > 10
- field-name: age
  constraints:
    max:
      value: 10
```

Error: `Invalid numeric constraints. effectiveMin > effectiveMax for class=..., field=age, effectiveMin=18 (inclusive=true), effectiveMax=10 (inclusive=true)`

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
PropertiesField-            ResolvedClassMapping
ConstraintContributor           + baseline
      |                              |
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
| `PropertiesFieldConstraintContributor` | Built-in contributor backed by YAML properties |
| `ConstraintMergeService` | Merges baseline + all contributed constraints using strictness rules |
| `ConfigDrivenConstraintMappingContributor` | Translates merged constraints into Hibernate Validator programmatic mappings |
| `ValidationAutoConfiguration` | Spring Boot auto-configuration that wires all beans |

**Data model records:**

| Record | Purpose |
|---|---|
| `BaselineFieldConstraints` | Constraints extracted from Java annotations |
| `EffectiveFieldConstraints` | Final merged constraints with messages |
| `NumericBound` | Numeric value + inclusive flag |
| `PatternRule` | Regex + flags + optional message |
| `ExtensionRegexRule` | JSONPath + regex + optional message |

For a detailed walkthrough of the internal pipeline, see [validation-constraint-mapping.md](validation-constraint-mapping.md).

---

## Troubleshooting

**My override seems to have no effect.**
The merge algorithm always selects the stricter value. If your configured value is less strict than the annotation baseline (e.g., config `min: 10` but annotation says `@Min(18)`), the baseline wins.

**My application fails to start with "Configured class was not found."**
Ensure `full-class-name` is the fully-qualified class name (e.g., `com.example.MyForm`, not `MyForm`) and the class is on the classpath.

**I get "Configured field was not found."**
The `field-name` must match the Java field name exactly (case-sensitive). It must be a declared field on the class (or a superclass), not a method-only property.

**I get "Constraint notBlank is not supported for field type Integer."**
`not-blank` only works on `CharSequence` (String) fields. Remove the `not-blank` constraint from the numeric field configuration.

**How do I make a field accept null when it is annotated with `@NotNull`?**
You cannot. The framework uses OR semantics for boolean constraints: if any source (annotation or config) says `true`, the constraint is enabled. This is by design to prevent accidental weakening of validation rules.

**Can I configure validation for fields that have no annotations?**
Yes. The field just needs to exist on the class. You can add any supported constraint purely from YAML, and the merge will treat the baseline as empty.

**What happens if I set both `min` and `decimal-min` for the same field?**
Both sources compete. The framework compares all candidates (baseline annotation, `min.value`, `decimal-min.value`) and selects the strictest lower bound.

**Does the pattern `message` apply per-regex or to the whole block?**
It applies to the whole configured block. All configured regexes in one `pattern` block share the same message. Baseline patterns from annotations keep their own default Jakarta messages.

**Can I use Spring profiles to vary validation overrides per environment?**
Yes. Use standard Spring Boot profile-specific configuration files (e.g., `application-prod.yml`, `application-staging.yml`). The framework reads from whatever configuration source Spring resolves at runtime.

**I see two `@Size` violation messages for the same field.**
This happens when `size.min.message` and `size.max.message` are different. The framework emits two separate `@Size` constraints so each bound displays its own message. If you want a single message, set the same `message` on both `min` and `max` (or set it on only one and leave the other unset).
