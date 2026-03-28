# Validation Constraint Mapping Flow

This document explains how the config-driven validation pipeline works in this project, with emphasis on:

- `ConfigDrivenConstraintMappingContributor.createConstraintMappings(...)`
- `ConfigDrivenConstraintMappingContributor.applyConstraints(...)`
- `ConstraintMergeService.merge(...)`

The goal of the pipeline is to take:

1. validation annotations already present on the Java model
2. extra constraints contributed by `FieldConstraintContributor` beans (including the built-in properties contributor for `com.ampp.business-validation-override.*`)

and turn them into one final set of Hibernate Validator rules that the application uses at runtime.

## High-level flow

The flow is:

1. `GeneratedClassMetadataCache` resolves configured classes and fields.
2. It extracts baseline constraints from field and getter annotations.
3. It validates configuration support for each field type.
4. `ConfigDrivenConstraintMappingContributor.createConstraintMappings(...)` iterates over every resolved class and field.
5. For each field, it gathers all `FieldConstraintContributor` outputs in Spring `@Order`.
6. `ConstraintMergeService.merge(...)` combines baseline constraints and all contributed constraints into one `EffectiveFieldConstraints` object.
7. `applyConstraints(...)` translates that `EffectiveFieldConstraints` object into Hibernate Validator programmatic mappings.

That means the policy decisions happen before `applyConstraints(...)`.

`applyConstraints(...)` is mostly an adapter from the merged internal model to Hibernate Validator's DSL.

## Key types

### `BaselineFieldConstraints`

Represents what was already declared in code with annotations like:

- `@NotNull`
- `@NotBlank`
- `@Min`
- `@Max`
- `@DecimalMin`
- `@DecimalMax`
- `@Size`
- `@Pattern`

These values come from `GeneratedClassMetadataCache`.

### `ValidationProperties.Constraints`

Represents what came from `application.yml` or other Spring config sources.

Examples:

```yaml
com:
  ampp:
    business-validation-override:
      - full-class-name: com.example.validatingforminput.PersonForm
        fields:
          - field-name: name
            constraints:
              not-blank:
                value: true
                message: Name is required
              size:
                min:
                  value: 4
                  message: Name must have at least 4 characters
                max:
                  value: 40
                  message: Name must have at most 40 characters
              pattern:
                regexes:
                  - ^[A-Za-z]+$
                message: Name must contain only letters
          - field-name: age
            constraints:
              decimal-min:
                value: 25.5
                inclusive: false
                message: Age must be greater than 25.5
              decimal-max:
                value: 58.5
                inclusive: false
                message: Age must be lower than 58.5
          - field-name: extensions
            constraints:
              extensions:
                rules:
                  - json-path: $.vendorExtensionCode
                    regex: ^[A-Z]{3}-[0-9]{4}$
                    message: Vendor extension code is invalid
```

### `FieldConstraintContributor`

Represents one source of configured constraints for a field:

```java
Optional<ValidationProperties.Constraints> contribute(
	String className,
	String fieldName,
	BaselineFieldConstraints baseline);
```

All contributors are evaluated for every resolved field in Spring order:

- lower `@Order` runs first
- the built-in `PropertiesFieldConstraintContributor` runs at `@Order(0)`
- exact strictness ties keep the first winner
- patterns and extensions are additive in contributor order

The built-in `PropertiesFieldConstraintContributor` adapts `com.ampp.business-validation-override.*` into this interface.
If a custom contributor should beat properties on an exact tie, it must use an order lower than `0`.

### `EffectiveFieldConstraints`

This is the final merged view used by the contributor.

It contains:

- `notNull`
- `notNullMessage`
- `notBlank`
- `notBlankMessage`
- `min` as a `NumericBound`
- `minMessage`
- `max` as a `NumericBound`
- `maxMessage`
- `sizeMin`
- `sizeMinMessage`
- `sizeMax`
- `sizeMaxMessage`
- `patterns`
- `extensionRules` (`jsonPath` + `regex`)

If `EffectiveFieldConstraints` says `min = NumericBound(25.5, false)`, then the contributor will add a programmatic `@DecimalMin(value = "25.5", inclusive = false)`.

## `createConstraintMappings(...)`

Source: `ConfigDrivenConstraintMappingContributor`

This method builds the runtime validation mapping for every configured class and field.

### Step 1: loop over resolved classes

```java
for (ResolvedClassMapping resolvedClassMapping : resolvedClassMappings) {
```

`resolvedClassMappings` comes from `GeneratedClassMetadataCache`.

Each `ResolvedClassMapping` contains:

- the class name
- the actual `Class<?>`
- the resolved fields for that class

This list has already been validated for things like:

- class exists
- field exists
- duplicates

### Step 2: create one Hibernate Validator mapping per class

```java
ConstraintMapping constraintMapping = builder.addConstraintMapping();
TypeConstraintMappingContext<?> typeContext = constraintMapping.type(resolvedClassMapping.clazz());
```

This tells Hibernate Validator:

"I am about to define constraints for this Java type."

`typeContext` is the class-level builder that later gives field-level builders.

### Step 3: loop over resolved fields

```java
for (ResolvedFieldMapping resolvedFieldMapping : resolvedClassMapping.fields()) {
```

Each `ResolvedFieldMapping` contains:

- the field name
- the baseline constraints extracted from annotations

### Step 4: collect contributed constraints for the field

```java
Iterable<ValidationProperties.Constraints> contributedConstraints =
	contributedConstraints(className, fieldName, baseline);
```

Contributors are invoked in Spring order:

- lower `@Order` values run first
- the built-in properties contributor uses `@Order(0)`
- ties keep the first winner for bound/message ownership
- patterns and extensions are appended in contributor order
- custom contributors must use an order lower than `0` to beat properties on exact ties

### Step 5: merge baseline and all contributors

```java
EffectiveFieldConstraints effectiveConstraints = constraintMergeService.merge(
	resolvedFieldMapping.baselineConstraints(),
	contributedConstraints,
	resolvedClassMapping.className(),
	resolvedFieldMapping.fieldName());
```

This is where the actual constraint policy lives.

The contributor does not decide:

- whether config should strengthen baseline
- whether multiple regexes should accumulate
- whether `min > max` is invalid

That all happens in `merge(...)`.

### Step 6: install the merged constraints

```java
applyConstraints(typeContext, resolvedFieldMapping.fieldName(), effectiveConstraints);
```

After merge, the contributor turns the internal model into real Hibernate Validator constraints.

## `applyConstraints(...)`

Source: `ConfigDrivenConstraintMappingContributor`

This method takes one merged field snapshot and writes it into Hibernate Validator's programmatic mapping API.

### Method purpose

It does not decide what the final constraints should be.

It assumes that `effectiveConstraints` is already valid and final.

Its job is:

- select the target field
- ignore direct annotation processing for that field
- replay preserved non-modeled constraints and cascade metadata
- add programmatic constraints that match the merged result

### Step 1: get the field mapping context

```java
PropertyConstraintMappingContext propertyContext =
	typeContext.field(fieldName).ignoreAnnotations(true);
```

This line does two things:

1. `typeContext.field(fieldName)` selects the field on the current class.
2. `ignoreAnnotations(true)` tells Hibernate Validator not to apply the field's annotation-based constraints directly.

This is critical.

Without `ignoreAnnotations(true)`, the runtime validator could see both:

- the original annotations from the Java field
- the merged programmatic constraints added by the contributor

That would duplicate constraints and break the whole "merge into one final view" design.

So the contributor intentionally disables direct annotation processing and then re-applies:

- preserved non-modeled Bean Validation annotations (for example custom constraints such as `@Email`)
- preserved cascade metadata such as `@Valid` and `@ConvertGroup`
- the merged modeled constraints from `EffectiveFieldConstraints`

### Step 2: apply `@NotNull`

```java
if (effectiveConstraints.notNull()) {
	propertyContext.constraint(new NotNullDef());
}
```

If the merged result says the field must not be null, the contributor adds a programmatic `@NotNull`.

If the merged result says `false`, nothing is added.

There is no inverse rule like "allow null". The absence of `@NotNull` is what allows null.

### Step 3: apply `@NotBlank`

```java
if (effectiveConstraints.notBlank()) {
	propertyContext.constraint(new NotBlankDef());
}
```

If the field must be non-blank, the contributor adds a programmatic `@NotBlank`.

This usually applies to string-like fields.

`@NotBlank` is stronger than `@NotNull` because it rejects:

- `null`
- `""`
- `"   "`

If both `notNull` and `notBlank` are true, both constraints are added.

That is logically redundant but harmless.

### Step 4: apply `@DecimalMin`

```java
if (effectiveConstraints.min() != null) {
	DecimalMinDef minDef = new DecimalMinDef()
		.value(effectiveConstraints.min().value().toPlainString())
		.inclusive(effectiveConstraints.min().inclusive());
	if (effectiveConstraints.minMessage() != null) {
		minDef.message(effectiveConstraints.minMessage());
	}
	propertyContext.constraint(minDef);
}
```

If the merged result has a numeric lower bound, the contributor adds `@DecimalMin`.

Example:

- `min = NumericBound(25.5, false)`
- valid values: `26`, `30`
- invalid values: `25`, `24`

### Step 5: apply `@DecimalMax`

```java
if (effectiveConstraints.max() != null) {
	DecimalMaxDef maxDef = new DecimalMaxDef()
		.value(effectiveConstraints.max().value().toPlainString())
		.inclusive(effectiveConstraints.max().inclusive());
	if (effectiveConstraints.maxMessage() != null) {
		maxDef.message(effectiveConstraints.maxMessage());
	}
	propertyContext.constraint(maxDef);
}
```

If the merged result has an upper bound, the contributor adds `@DecimalMax`.

Example:

- `max = NumericBound(58.5, false)`
- valid values: `58`, `40`
- invalid values: `59`

### Step 6: apply `@Size`

```java
if (effectiveConstraints.sizeMin() != null || effectiveConstraints.sizeMax() != null) {
	boolean splitByMessage = effectiveConstraints.sizeMin() != null
		&& effectiveConstraints.sizeMax() != null
		&& !Objects.equals(effectiveConstraints.sizeMinMessage(), effectiveConstraints.sizeMaxMessage());
	// if min/max messages differ, emit two SizeDef constraints
}
```

When both bounds exist and messages are equal (or both null), one `SizeDef` is used.
When both bounds exist and messages differ, the contributor emits separate min-only and max-only `SizeDef` constraints so each side can have its own message.

Supported shapes:

- min only
- max only
- both

Examples:

- `sizeMin = 4`, `sizeMax = null` means length/count must be at least 4
- `sizeMin = null`, `sizeMax = 30` means length/count must be at most 30
- `sizeMin = 4`, `sizeMax = 30` means both

### Step 7: apply `@Pattern`

```java
for (PatternRule patternRule : effectiveConstraints.patterns()) {
	propertyContext.constraint(new PatternDef().regexp(patternRule.regex()));
}
```

This loop adds one `@Pattern` constraint for every effective regex rule.

Each `PatternRule` contains:

- `regex`
- `message` (optional)

### Important behavior: distinct patterns are cumulative

If a field ends up with multiple distinct patterns, all of them are applied.

That means the field value must satisfy every pattern, not just one.

Example:

- baseline pattern: `^[A-Za-z ]+$`
- configured pattern: `^[A-Za-z]+$`

The final field accepts only values that satisfy both.

So `"John Doe"`:

- matches `^[A-Za-z ]+$`
- does not match `^[A-Za-z]+$`

Result: validation fails.

Equivalent regex identities are collapsed before the mapping is written, so a configured duplicate does not create duplicate violations for the same rule.

That is why configured patterns act as a hardening mechanism in this project.

### Step 8: apply configured `extensions` JSONPath rules

```java
for (ExtensionRegexRule extensionRule : effectiveConstraints.extensionRules()) {
	propertyContext.constraint(new GenericConstraintDef<>(ExtensionsJsonPathRegex.class)
		.param("jsonPath", extensionRule.jsonPath())
		.param("regex", extensionRule.regex()));
}
```

`extensions` rules are implemented with a custom Bean Validation constraint because Hibernate Validator has no built-in annotation for:

- read a JSON value by JSONPath
- then validate that value with a regex

Runtime behavior of this validator:

- if the target field itself is `null`, skip validation
- evaluate the configured `jsonPath` against the target field value
- apply `regex` to each resolved scalar value
- if the target field is a blank JSON string, skip validation
- if the path is missing, treat it as "condition not met" and skip validation
- if a resolved value is `null`, skip that value
- if a resolved scalar value does not match, raise a violation
- if a resolved value is non-scalar (for example, an object), raise a violation

## `ConstraintMergeService.merge(...)`

Source: `ConstraintMergeService`

This method takes:

- baseline constraints from code
- contributed constraints from one or more `FieldConstraintContributor` sources

and produces one `EffectiveFieldConstraints`.

This is the method that decides what "stronger" means.

### Input normalization

The merge starts from baseline constraints, then folds each contributor in order.

The mapping contributor now supplies constraints lazily as an `Iterable`, so the merge path no longer needs a per-field intermediate list.
For backward compatibility, the single-source and list-based overloads still exist and delegate to the iterable-based merge path.

### Boolean constraints: `notNull` and `notBlank`

```java
boolean notNull = baseline.notNull()
	|| isTrue(effectiveConfig.getNotNull().getValue());
boolean notBlank = baseline.notBlank()
	|| isTrue(effectiveConfig.getNotBlank().getValue());
```

These are merged with OR semantics across baseline and all contributors.

If any source says the constraint should be enabled, the final result is enabled.

That means the config can make a field stricter, but not weaker.

Example:

- baseline has `@NotNull`
- config says `not-null.value=false`

Result: still true, because baseline already required it.

### Numeric lower bounds use the stricter lower limit

```java
NumericBound min = strictestLowerBound(
	baseline.min(),
	toInclusiveBound(effectiveConfig.getMin().getValue()),
	toDecimalBound(effectiveConfig.getDecimalMin().getValue(), ...));
```

Lower bounds are merged by taking the highest numeric value.

Why?

Because for lower bounds, larger means stricter.

Example:

- baseline min = 18
- configured min = 16
- decimal min = 18.5 exclusive
- hard min = 21

Result: 21

If two lower bounds have the same numeric value, exclusive is stricter than inclusive.
If strictness is exactly equal, the first contributor that produced the winning bound keeps ownership (including message).

The same logic still applies to `sizeMin`.

### Numeric upper bounds use the stricter upper limit

```java
NumericBound max = strictestUpperBound(
	baseline.max(),
	toInclusiveBound(effectiveConfig.getMax().getValue()),
	toDecimalBound(effectiveConfig.getDecimalMax().getValue(), ...));
```

Upper bounds are merged by taking the lowest numeric value.

Why?

Because for upper bounds, smaller means stricter.

Example:

- baseline max = 60
- configured max = 70
- decimal max = 60.5 exclusive
- hard max = 55

Result: 55

If strictness is exactly equal, the first contributor that produced the winning bound keeps ownership (including message).

If two upper bounds have the same numeric value, exclusive is stricter than inclusive.

The same logic still applies to `sizeMax`.

### Size values are converted to `Integer`

```java
toSizeInteger(...)
```

Size in bean validation uses `int`, not `long`.

So configured size values are validated and converted:

- `null` stays null
- negative values are rejected
- values larger than `Integer.MAX_VALUE` are rejected
- otherwise they are converted with `Math.toIntExact(...)`

This is a defensive boundary so invalid configuration is detected early; callers log a warning and skip the bad mapping.

### Incompatible merged ranges are rejected

```java
if (min != null && max != null && min > max) {
	throw ...
}
if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
	throw ...
}
```

Even if each individual piece looked valid, the final combination can still be impossible.

Examples:

- min = 70 and max = 50
- min = 10 exclusive and max = 10 inclusive
- sizeMin = 40 and sizeMax = 30

The merge service rejects these immediately.

### Patterns accumulate

```java
List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
appendConfiguredPatterns(
	patterns,
	effectiveConfig.getPattern().getRegexes(),
	effectiveConfig.getPattern().getMessage(),
	className,
	fieldName);
```

Patterns are not replaced.

The baseline pattern list is copied first, then configured regexes are appended.

That means configured patterns make the field stricter by adding more rules.

### Configured regexes are validated

`appendConfiguredPatterns(...)` does three things before adding a regex:

1. validates that each regex is not null or empty
2. validates that each regex compiles successfully

If any check fails, the method throws `InvalidConstraintConfigurationException`.

### `extensions` rules are validated and merged

```java
List<ExtensionRegexRule> extensionRules = new ArrayList<>();
appendConfiguredExtensions(extensionRules, effectiveConfig.getExtensions().getRules(), className, fieldName);
```

`appendConfiguredExtensions(...)` validates each configured rule:

1. rule entry must be non-null
2. `jsonPath` must be non-empty and compile successfully
3. `regex` must be non-empty and compile successfully

The metadata layer (`GeneratedClassMetadataCache`) also enforces that the field type must support JSONPath evaluation (`Map`, `Collection`, array, or JSON `String`).

### Final result

At the end, the method returns:

```java
new EffectiveFieldConstraints(
	notNull, notNullMessage,
	notBlank, notBlankMessage,
	min, minMessage,
	max, maxMessage,
	sizeMin, sizeMinMessage,
	sizeMax, sizeMaxMessage,
	patterns, extensionRules)
```

This object is the single source of truth for that field.

## End-to-end example

Assume `PersonForm.name` has these baseline annotations:

```java
@NotNull
@NotBlank
@Size(min = 3, max = 30)
@Pattern(regexp = "^[A-Za-z ]+$")
private String name;
```

And config adds:

```yaml
com:
  ampp:
    business-validation-override:
      - full-class-name: com.example.validatingforminput.PersonForm
        fields:
          - field-name: name
            constraints:
              size:
                min:
                  value: 4
                max:
                  value: 40
              pattern:
                regexes:
                  - ^[A-Za-z]+$
```

### Baseline extracted from annotations

- `notNull = true`
- `notBlank = true`
- `sizeMin = 3`
- `sizeMax = 30`
- `patterns = ["^[A-Za-z ]+$"]`

### Configured constraints

- `sizeMin = 4`
- `sizeMax = 40`
- `patterns = ["^[A-Za-z]+$"]`

### After merge

- `notNull = true`
- `notBlank = true`
- `sizeMin = 4` because max(3, 4) = 4
- `sizeMax = 30` because min(30, 40) = 30
- `patterns = ["^[A-Za-z ]+$", "^[A-Za-z]+$"]`

### What `applyConstraints(...)` installs

- `@NotNull`
- `@NotBlank`
- `@Size(min = 4, max = 30)`
- `@Pattern(regexp = "^[A-Za-z ]+$")`
- `@Pattern(regexp = "^[A-Za-z]+$")`

So the effective validation behavior is:

- name cannot be null
- name cannot be blank
- name length must be between 4 and 30
- name may only contain letters and spaces
- name may also satisfy the stricter no-space regex

Because both patterns are active, `"John Doe"` fails.

## What the contributor does not do

The contributor is not responsible for:

- class lookup
- field lookup
- duplicate detection
- extracting annotations
- determining stricter min/max
- validating regex syntax
- validating JSONPath syntax
- validating impossible min/max combinations

Those concerns are intentionally handled before or outside `applyConstraints(...)`.

This separation keeps the code easier to reason about:

- metadata code resolves what exists
- merge code decides the final policy
- contributor code installs the final rules

## Practical summary

If you want to reason about a validation result:

1. Look at baseline annotations on the field and getter.
2. Look at all `FieldConstraintContributor` outputs for that field (including properties-backed config).
3. Look at how `ConstraintMergeService.merge(...)` combines them.
4. Look at `applyConstraints(...)` to see how the merged result becomes real runtime constraints.

If you want to change behavior:

- change metadata extraction if the app is missing baseline annotations
- change merge logic if you want different precedence or hardening rules
- change `applyConstraints(...)` only if you want to emit different validator definitions

Most policy changes belong in `ConstraintMergeService`, not in the contributor.
