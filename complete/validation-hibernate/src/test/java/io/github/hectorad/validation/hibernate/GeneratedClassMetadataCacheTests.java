package io.github.hectorad.validation.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.hectorad.validation.BaselineFieldConstraints;
import io.github.hectorad.validation.ClassValidationOverride;
import io.github.hectorad.validation.ConstraintOverrideSet;
import io.github.hectorad.validation.FieldValidationOverride;
import io.github.hectorad.validation.InvalidConstraintConfigurationException;
import io.github.hectorad.validation.ValidationOverrideContributor;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

class GeneratedClassMetadataCacheTests {

    @Test
    void shouldFailWhenClassDoesNotExist() {
        assertThatThrownBy(() -> cache(classOverride("com.example.missing.MissingPerson", fieldOverride("name"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Configured class was not found");
    }

    @Test
    void shouldFailWhenConfiguredFieldDoesNotExist() {
        assertThatThrownBy(() -> cache(classOverride(LocalPersonForm.class, fieldOverride("doesNotExist"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Configured field was not found");
    }

    @Test
    void shouldFailWhenNotBlankIsConfiguredForNonStringField() {
        assertThatThrownBy(() -> cache(classOverride(LocalPersonForm.class, fieldOverride("age", constraints ->
            constraints.getNotBlank().setValue(true)))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint notBlank is not supported");
    }

    @Test
    void shouldFailWhenSizeIsConfiguredForUnsupportedFieldType() {
        assertThatThrownBy(() -> cache(classOverride(LocalPersonForm.class, fieldOverride("age", constraints ->
            constraints.getSize().getMin().setValue(1L)))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint size is not supported");
    }

    @Test
    void shouldFailWhenPatternIsConfiguredForNonStringField() {
        assertThatThrownBy(() -> cache(classOverride(LocalPersonForm.class, fieldOverride("age", constraints ->
            constraints.getPattern().setRegexes(List.of("^\\d+$"))))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint pattern is not supported");
    }

    @Test
    void shouldFailWhenExtensionsRuleIsConfiguredForUnsupportedFieldType() {
        assertThatThrownBy(() -> cache(classOverride(UnsupportedExtensionsFieldTypeTarget.class, fieldOverride("extensions", constraints -> {
            ConstraintOverrideSet.ExtensionRule extensionRule = new ConstraintOverrideSet.ExtensionRule();
            extensionRule.setJsonPath("$.partner.code");
            extensionRule.setRegex("^[A-Z]+$");
            constraints.getExtensions().setRules(List.of(extensionRule));
        }))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint extensions is not supported");
    }

    @Test
    void shouldAllowExtensionsRuleOnAnySupportedFieldName() {
        ConstraintOverrideSet.ExtensionRule extensionRule = new ConstraintOverrideSet.ExtensionRule();
        extensionRule.setJsonPath("$.partner.code");
        extensionRule.setRegex("^[A-Z]+$");

        GeneratedClassMetadataCache cache = cache(classOverride(NonExtensionsMapTarget.class, fieldOverride("metadata", constraints ->
            constraints.getExtensions().setRules(List.of(extensionRule)))));

        assertThat(cache.getRequiredResolvedMapping(NonExtensionsMapTarget.class.getName()).fields())
            .singleElement()
            .extracting(ResolvedFieldMapping::fieldName)
            .isEqualTo("metadata");
    }

    @Test
    void shouldFailWhenMinIsConfiguredForUnsupportedFieldType() {
        assertThatThrownBy(() -> cache(classOverride(UnsupportedConstraintTarget.class, fieldOverride("active", constraints ->
            constraints.getMin().setValue(1L)))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint numeric bounds is not supported");
    }

    @Test
    void shouldExtractDecimalBoundsFromAnnotations() {
        GeneratedClassMetadataCache cache = cache(classOverride(LocalPersonForm.class, fieldOverride("salary")));
        BaselineFieldConstraints baseline = cache.getRequiredResolvedMapping(LocalPersonForm.class.getName())
            .fields()
            .getFirst()
            .baselineConstraints();

        assertThat(baseline.min()).isNotNull();
        assertThat(baseline.min().value()).isEqualByComparingTo("1000.00");
        assertThat(baseline.min().inclusive()).isFalse();
        assertThat(baseline.max()).isNotNull();
        assertThat(baseline.max().value()).isEqualByComparingTo("250000.00");
        assertThat(baseline.max().inclusive()).isTrue();
    }

    @Test
    void shouldFailWhenDecimalBoundsAreConfiguredForUnsupportedFieldType() {
        assertThatThrownBy(() -> cache(classOverride(UnsupportedDecimalConstraintTarget.class, fieldOverride("ratio", constraints ->
            constraints.getDecimalMin().setValue(new BigDecimal("1.5"))))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Constraint numeric bounds is not supported");
    }

    @Test
    void shouldFailWhenDecimalAnnotationValueIsMalformed() {
        assertThatThrownBy(() -> cache(classOverride(MalformedDecimalAnnotationTarget.class, fieldOverride("amount"))))
            .isInstanceOf(InvalidConstraintConfigurationException.class)
            .hasMessageContaining("Invalid DecimalMin annotation");
    }

    @Test
    void shouldMergeContributorsForSameClassAndField() {
        GeneratedClassMetadataCache cache = cache(
            List.of(
                contributor(classOverride(LocalPersonForm.class, fieldOverride("name"))),
                contributor(classOverride(LocalPersonForm.class, fieldOverride("age")))));

        assertThat(cache.getResolvedMappings()).singleElement().satisfies(mapping ->
            assertThat(mapping.fields()).extracting(ResolvedFieldMapping::fieldName).containsExactly("name", "age"));
    }

    @Test
    void shouldFailWhenDuplicateClassMappingExistsInsideSingleContributor() {
        assertThatThrownBy(() -> cache(List.of(contributor(
            classOverride(LocalPersonForm.class, fieldOverride("name")),
            classOverride(LocalPersonForm.class, fieldOverride("age"))))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate class mapping");
    }

    @Test
    void shouldFailWhenDuplicateFieldMappingExistsInsideSingleContributor() {
        assertThatThrownBy(() -> cache(classOverride(
            LocalPersonForm.class,
            fieldOverride("name"),
            fieldOverride("name"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate field mapping");
    }

    @Test
    void shouldSkipUnknownClassWhenFailOnErrorIsFalse() {
        GeneratedClassMetadataCache cache = cache(false, classOverride("com.example.missing.MissingPerson", fieldOverride("name")));

        assertThat(cache.getResolvedMappings()).isEmpty();
    }

    @Test
    void shouldSkipUnknownFieldWhenFailOnErrorIsFalse() {
        GeneratedClassMetadataCache cache = cache(false, classOverride(
            LocalPersonForm.class,
            fieldOverride("name"),
            fieldOverride("doesNotExist")));

        assertThat(cache.getResolvedMappings()).hasSize(1);
        assertThat(cache.getResolvedMappings().getFirst().fields())
            .singleElement()
            .extracting(ResolvedFieldMapping::fieldName)
            .isEqualTo("name");
    }

    @Test
    void shouldSkipIncompatibleConstraintWhenFailOnErrorIsFalse() {
        GeneratedClassMetadataCache cache = cache(false, classOverride(
            LocalPersonForm.class,
            fieldOverride("name"),
            fieldOverride("age", constraints -> constraints.getNotBlank().setValue(true))));

        assertThat(cache.getResolvedMappings()).hasSize(1);
        assertThat(cache.getResolvedMappings().getFirst().fields())
            .singleElement()
            .extracting(ResolvedFieldMapping::fieldName)
            .isEqualTo("name");
    }

    @Test
    void shouldAllowTargetsIntroducedByCustomContributor() {
        GeneratedClassMetadataCache cache = cache(List.of(
            contributor(classOverride(CustomContributorTarget.class, fieldOverride("name", constraints -> constraints.getSize().getMin().setValue(5L))))));

        assertThat(cache.getResolvedMappings()).singleElement().satisfies(mapping ->
            assertThat(mapping.className()).isEqualTo(CustomContributorTarget.class.getName()));
    }

    private GeneratedClassMetadataCache cache(ClassValidationOverride... overrides) {
        return cache(true, overrides);
    }

    private GeneratedClassMetadataCache cache(boolean failOnError, ClassValidationOverride... overrides) {
        return cache(failOnError, List.of(contributor(overrides)));
    }

    private GeneratedClassMetadataCache cache(List<ValidationOverrideContributor> contributors) {
        return cache(true, contributors);
    }

    private GeneratedClassMetadataCache cache(boolean failOnError, List<ValidationOverrideContributor> contributors) {
        return new GeneratedClassMetadataCache(new ValidationOverrideRegistry(contributors), failOnError);
    }

    private ValidationOverrideContributor contributor(ClassValidationOverride... overrides) {
        return () -> List.of(overrides);
    }

    private ClassValidationOverride classOverride(Class<?> type, FieldValidationOverride... fields) {
        return classOverride(type.getName(), fields);
    }

    private ClassValidationOverride classOverride(String className, FieldValidationOverride... fields) {
        return new ClassValidationOverride(className, List.of(fields));
    }

    private FieldValidationOverride fieldOverride(String fieldName) {
        return fieldOverride(fieldName, constraints -> {
        });
    }

    private FieldValidationOverride fieldOverride(String fieldName, Consumer<ConstraintOverrideSet> customizer) {
        ConstraintOverrideSet constraints = new ConstraintOverrideSet();
        customizer.accept(constraints);
        return new FieldValidationOverride(fieldName, constraints);
    }

    private static final class LocalPersonForm {

        @NotNull
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z ]+$")
        private String name;

        @NotNull
        @Min(18)
        @Max(60)
        private Integer age;

        @DecimalMin(value = "1000.00", inclusive = false)
        @DecimalMax("250000.00")
        private BigDecimal salary;

        private Map<String, Object> extensions;
    }

    private static final class UnsupportedConstraintTarget {

        @SuppressWarnings("unused")
        private Boolean active;
    }

    private static final class NonExtensionsMapTarget {

        @SuppressWarnings("unused")
        private Map<String, Object> metadata;
    }

    private static final class UnsupportedExtensionsFieldTypeTarget {

        @SuppressWarnings("unused")
        private Integer extensions;
    }

    private static final class UnsupportedDecimalConstraintTarget {

        @SuppressWarnings("unused")
        private Double ratio;
    }

    private static final class MalformedDecimalAnnotationTarget {

        @SuppressWarnings("unused")
        @DecimalMin("not-a-number")
        private Integer amount;
    }

    private static final class CustomContributorTarget {

        @SuppressWarnings("unused")
        private String name;
    }
}
