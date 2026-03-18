# Validation Constraint Mapping Flow

This document explains how validation overrides move from Spring Boot configuration and custom contributors into runtime Hibernate Validator mappings.

## High-level flow

1. Spring creates all `ValidationOverrideContributor` beans in order.
2. `PropertiesValidationOverrideContributor` adapts `hector.validation.overrides` into the same SPI as any custom contributor.
3. `ValidationOverrideRegistry` indexes all contributed class and field targets.
4. `GeneratedClassMetadataCache` resolves those targets, extracts baseline annotation constraints, and preserves non-modeled validation metadata.
5. `ConfigDrivenConstraintMappingContributor` asks `ConstraintMergeService` to merge baseline constraints with the ordered `ConstraintOverrideSet` list for each field.
6. The merged `EffectiveFieldConstraints` are translated into Hibernate Validator programmatic mappings.
7. `ValidationTroubleshootingAnalyzer` uses the same registry and merge path, so its view matches runtime behavior.

## Key types

### `ValidationOverrideContributor`

Public SPI for programmatic override sources:

```java
public interface ValidationOverrideContributor {

    List<ClassValidationOverride> getValidationOverrides();
}
```

The Spring container order of contributor beans defines precedence.

### `ClassValidationOverride`

Represents one target class plus its field overrides.

### `FieldValidationOverride`

Represents one target field and its `ConstraintOverrideSet`.

### `ConstraintOverrideSet`

The public override model for:

- `notNull`
- `notBlank`
- `min`
- `max`
- `decimalMin`
- `decimalMax`
- `size`
- `pattern`
- `extensions`

### `BaselineFieldConstraints`

Represents modeled constraints already declared in code, such as `@NotNull`, `@NotBlank`, `@Size`, or `@DecimalMin`.

### `EffectiveFieldConstraints`

Represents the final merged view that gets written into Hibernate Validator's programmatic DSL.

## Metadata extraction

`GeneratedClassMetadataCache` resolves every class and field named by the registry.

For each field it builds:

- `BaselineFieldConstraints` from modeled annotations
- `FieldValidationMetadata` for preserved constraint annotations, cascade metadata, and container-element metadata

Modeled constraints are not replayed as passthrough annotations because they are rebuilt from the merged effective model.

## Merge policy

`ConstraintMergeService` is the single merge authority.

Behavior:

- Boolean constraints use effective OR semantics.
- Baseline boolean constraints may still receive an override message from the ordered contributor list.
- Lower bounds pick the strictest effective minimum.
- Upper bounds pick the strictest effective maximum.
- Equal-strength numeric ties keep the earlier contributor.
- Size min and max are resolved independently.
- Pattern rules are additive and retain contributor order.
- Extension regex rules are additive and retain contributor order.
- Invalid combinations raise `InvalidConstraintConfigurationException`.

## Runtime mapping

`ConfigDrivenConstraintMappingContributor` is the Hibernate Validator bridge.

Per resolved field it:

1. Reads ordered contributions from `ValidationOverrideRegistry`
2. Calls `ConstraintMergeService.merge(...)`
3. Applies preserved validation metadata
4. Adds programmatic constraints for the effective merged result

The contributor ignores direct annotations for the field through Hibernate's DSL and then reapplies:

- preserved non-modeled constraints
- cascade metadata
- container-element metadata
- merged modeled constraints

That keeps the field behavior deterministic and avoids double-applying modeled constraints.

## Troubleshooting parity

`ValidationTroubleshootingAnalyzer` uses the same inputs as runtime validation:

- baseline metadata from `GeneratedClassMetadataCache`
- ordered contributions from `ValidationOverrideRegistry`
- merge decisions from `ConstraintMergeService`

This is intentional. If troubleshooting says a field should resolve to a given effective constraint set, runtime validation should match it.
