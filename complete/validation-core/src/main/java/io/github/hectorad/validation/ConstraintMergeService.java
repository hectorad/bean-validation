package io.github.hectorad.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

public class ConstraintMergeService {

    public EffectiveFieldConstraints merge(
        BaselineFieldConstraints baseline,
        ConstraintOverrideSet configuredConstraints,
        String className,
        String fieldName
    ) {
        return merge(
            baseline,
            (configuredConstraints == null) ? List.of() : List.of(configuredConstraints),
            className,
            fieldName);
    }

    public EffectiveFieldConstraints merge(
        BaselineFieldConstraints baseline,
        List<ConstraintOverrideSet> configuredConstraints,
        String className,
        String fieldName
    ) {
        List<ConstraintOverrideSet> effectiveConfigs = defaultConstraints(configuredConstraints);
        BooleanConstraintState notNull = resolveBooleanConstraint(
            baseline.notNull(),
            effectiveConfigs,
            ConstraintOverrideSet::getNotNull);
        BooleanConstraintState notBlank = resolveBooleanConstraint(
            baseline.notBlank(),
            effectiveConfigs,
            ConstraintOverrideSet::getNotBlank);
        BoundCandidate minCandidate = resolveBound(
            baseline.min(),
            effectiveConfigs,
            ConstraintOverrideSet::getMin,
            ConstraintOverrideSet::getDecimalMin,
            className,
            fieldName,
            "decimal-min",
            NumericBound::stricterLower);
        BoundCandidate maxCandidate = resolveBound(
            baseline.max(),
            effectiveConfigs,
            ConstraintOverrideSet::getMax,
            ConstraintOverrideSet::getDecimalMax,
            className,
            fieldName,
            "decimal-max",
            NumericBound::stricterUpper);
        SizeBoundCandidate sizeMinCandidate = resolveSizeBound(
            baseline.sizeMin(),
            effectiveConfigs,
            constraint -> constraint.getSize().getMin(),
            className,
            fieldName,
            "size.min.value",
            true);
        SizeBoundCandidate sizeMaxCandidate = resolveSizeBound(
            baseline.sizeMax(),
            effectiveConfigs,
            constraint -> constraint.getSize().getMax(),
            className,
            fieldName,
            "size.max.value",
            false);

        NumericBound min = bound(minCandidate);
        NumericBound max = bound(maxCandidate);
        Integer sizeMin = sizeValue(sizeMinCandidate);
        Integer sizeMax = sizeValue(sizeMaxCandidate);

        validateNumericBounds(min, max, className, fieldName);
        if (sizeMin != null && sizeMax != null && sizeMin > sizeMax) {
            throw new InvalidConstraintConfigurationException(
                "Invalid size constraints. effectiveSizeMin > effectiveSizeMax for class="
                    + className + ", field=" + fieldName + ", effectiveSizeMin=" + sizeMin + ", effectiveSizeMax=" + sizeMax);
        }

        List<PatternRule> patterns = new ArrayList<>(baseline.patterns());
        List<ExtensionRegexRule> extensionRules = new ArrayList<>();
        for (ConstraintOverrideSet effectiveConfig : effectiveConfigs) {
            appendConfiguredPatterns(
                patterns,
                effectiveConfig.getPattern().getRegexes(),
                effectiveConfig.getPattern().getFlags(),
                effectiveConfig.getPattern().getMessage(),
                className,
                fieldName);
            extensionRules.addAll(
                toConfiguredExtensionRules(effectiveConfig.getExtensions().getRules(), className, fieldName));
        }

        return new EffectiveFieldConstraints(
            notNull.enabled(),
            notNull.message(),
            notBlank.enabled(),
            notBlank.message(),
            min,
            message(minCandidate),
            max,
            message(maxCandidate),
            sizeMin,
            message(sizeMinCandidate),
            sizeMax,
            message(sizeMaxCandidate),
            patterns,
            extensionRules);
    }

    private List<ConstraintOverrideSet> defaultConstraints(List<ConstraintOverrideSet> configuredConstraints) {
        if (configuredConstraints == null || configuredConstraints.isEmpty()) {
            return List.of();
        }
        return configuredConstraints.stream()
            .map(configuredConstraint -> configuredConstraint == null ? new ConstraintOverrideSet() : configuredConstraint)
            .toList();
    }

    private boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private BooleanConstraintState resolveBooleanConstraint(
        boolean baselineEnabled,
        List<ConstraintOverrideSet> configuredConstraints,
        Function<ConstraintOverrideSet, ConstraintOverrideSet.ToggleConstraint> selector
    ) {
        if (baselineEnabled) {
            return new BooleanConstraintState(true, resolveBaselineBooleanMessage(configuredConstraints, selector));
        }
        for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
            ConstraintOverrideSet.ToggleConstraint candidate = selector.apply(configuredConstraint);
            if (isTrue(candidate.getValue())) {
                return new BooleanConstraintState(true, candidate.getMessage());
            }
        }
        return new BooleanConstraintState(false, null);
    }

    private String resolveBaselineBooleanMessage(
        List<ConstraintOverrideSet> configuredConstraints,
        Function<ConstraintOverrideSet, ConstraintOverrideSet.ToggleConstraint> selector
    ) {
        for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
            ConstraintOverrideSet.ToggleConstraint candidate = selector.apply(configuredConstraint);
            if (candidate.getMessage() != null && candidate.getValue() != Boolean.FALSE) {
                return candidate.getMessage();
            }
        }
        return null;
    }

    private BoundCandidate resolveBound(
        NumericBound baseline,
        List<ConstraintOverrideSet> configuredConstraints,
        Function<ConstraintOverrideSet, ConstraintOverrideSet.NumericConstraint> numericAccessor,
        Function<ConstraintOverrideSet, ConstraintOverrideSet.DecimalConstraint> decimalAccessor,
        String className,
        String fieldName,
        String decimalPropertyPrefix,
        BinaryOperator<NumericBound> numericSelector
    ) {
        BoundCandidate result = baselineCandidate(baseline);
        for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
            ConstraintOverrideSet.NumericConstraint numericConstraint = numericAccessor.apply(configuredConstraint);
            result = selectStricterBound(
                result,
                toInclusiveBound(numericConstraint.getValue(), numericConstraint.getMessage()),
                numericSelector);

            ConstraintOverrideSet.DecimalConstraint decimalConstraint = decimalAccessor.apply(configuredConstraint);
            result = selectStricterBound(
                result,
                toDecimalBound(
                    decimalConstraint.getValue(),
                    decimalConstraint.getInclusive(),
                    decimalConstraint.getMessage(),
                    className,
                    fieldName,
                    decimalPropertyPrefix + ".value",
                    decimalPropertyPrefix + ".inclusive"),
                numericSelector);
        }
        return result;
    }

    private SizeBoundCandidate resolveSizeBound(
        Integer baseline,
        List<ConstraintOverrideSet> configuredConstraints,
        Function<ConstraintOverrideSet, ConstraintOverrideSet.NumericConstraint> selector,
        String className,
        String fieldName,
        String valueProperty,
        boolean selectStricterLower
    ) {
        SizeBoundCandidate result = sizeCandidate(baseline);
        for (ConstraintOverrideSet configuredConstraint : configuredConstraints) {
            ConstraintOverrideSet.NumericConstraint candidate = selector.apply(configuredConstraint);
            SizeBoundCandidate candidateBound = toSizeBound(
                candidate.getValue(),
                candidate.getMessage(),
                className,
                fieldName,
                valueProperty);
            if (candidateBound == null || candidateBound.value() == null) {
                continue;
            }
            if (result == null
                || result.value() == null
                || (selectStricterLower
                    ? candidateBound.value().compareTo(result.value()) > 0
                    : candidateBound.value().compareTo(result.value()) < 0)) {
                result = candidateBound;
            }
        }
        return result;
    }

    private BoundCandidate selectStricterBound(
        BoundCandidate current,
        BoundCandidate candidate,
        BinaryOperator<NumericBound> numericSelector
    ) {
        if (candidate == null || candidate.bound() == null) {
            return current;
        }
        if (current == null || current.bound() == null) {
            return candidate;
        }

        NumericBound winner = numericSelector.apply(current.bound(), candidate.bound());
        return (winner == candidate.bound()) ? candidate : current;
    }

    private BoundCandidate toInclusiveBound(Long value, String message) {
        return (value == null) ? null : new BoundCandidate(NumericBound.inclusive(value), message);
    }

    private BoundCandidate baselineCandidate(NumericBound bound) {
        return new BoundCandidate(bound, null);
    }

    private BoundCandidate toDecimalBound(
        BigDecimal value,
        Boolean inclusive,
        String message,
        String className,
        String fieldName,
        String valuePropertyName,
        String inclusivePropertyName
    ) {
        if (value == null) {
            if (inclusive != null) {
                throw new InvalidConstraintConfigurationException(
                    "Invalid decimal constraint. " + inclusivePropertyName + " requires " + valuePropertyName
                        + " for class=" + className + ", field=" + fieldName);
            }
            return null;
        }
        return new BoundCandidate(new NumericBound(value, inclusive == null || inclusive), message);
    }

    private void validateNumericBounds(
        NumericBound min,
        NumericBound max,
        String className,
        String fieldName
    ) {
        if (min == null || max == null) {
            return;
        }

        int comparison = min.value().compareTo(max.value());
        if (comparison > 0) {
            throw new InvalidConstraintConfigurationException(
                "Invalid numeric constraints. effectiveMin > effectiveMax for class="
                    + className + ", field=" + fieldName + ", effectiveMin=" + render(min) + ", effectiveMax=" + render(max));
        }
        if (comparison == 0 && (!min.inclusive() || !max.inclusive())) {
            throw new InvalidConstraintConfigurationException(
                "Invalid numeric constraints. equal bounds cannot be exclusive for class="
                    + className + ", field=" + fieldName + ", effectiveMin=" + render(min) + ", effectiveMax=" + render(max));
        }
    }

    private String render(NumericBound bound) {
        return bound.value().toPlainString() + " (inclusive=" + bound.inclusive() + ")";
    }

    private void appendConfiguredPatterns(
        List<PatternRule> patterns,
        List<String> configuredRegexes,
        List<String> configuredFlags,
        String configuredMessage,
        String className,
        String fieldName
    ) {
        Set<jakarta.validation.constraints.Pattern.Flag> parsedFlags =
            parsePatternFlags(configuredFlags, className, fieldName);
        Map<PatternIdentity, Integer> patternIndexes = indexPatterns(patterns);
        for (int index = 0; index < configuredRegexes.size(); index++) {
            PatternRule configuredPattern =
                toConfiguredPatternRule(configuredRegexes.get(index), parsedFlags, configuredMessage, className, fieldName, index);
            PatternIdentity patternIdentity = new PatternIdentity(configuredPattern.regex(), configuredPattern.flags());
            Integer existingIndex = patternIndexes.get(patternIdentity);
            if (existingIndex != null) {
                PatternRule existingPattern = patterns.get(existingIndex);
                if (configuredPattern.message() != null && !Objects.equals(existingPattern.message(), configuredPattern.message())) {
                    patterns.set(
                        existingIndex,
                        new PatternRule(existingPattern.regex(), existingPattern.flags(), configuredPattern.message()));
                }
                continue;
            }
            patternIndexes.put(patternIdentity, patterns.size());
            patterns.add(configuredPattern);
        }
    }

    private Map<PatternIdentity, Integer> indexPatterns(List<PatternRule> patterns) {
        Map<PatternIdentity, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < patterns.size(); index++) {
            PatternRule patternRule = patterns.get(index);
            indexes.putIfAbsent(new PatternIdentity(patternRule.regex(), patternRule.flags()), index);
        }
        return indexes;
    }

    private PatternRule toConfiguredPatternRule(
        String regex,
        Set<jakarta.validation.constraints.Pattern.Flag> flags,
        String configuredMessage,
        String className,
        String fieldName,
        int index
    ) {
        String requiredRegex = requireNonEmpty("pattern", "regex", regex, className, fieldName, index);
        validateRegex("pattern", requiredRegex, className, fieldName, index);
        return new PatternRule(requiredRegex, flags, configuredMessage);
    }

    private Set<jakarta.validation.constraints.Pattern.Flag> parsePatternFlags(
        List<String> flagNames,
        String className,
        String fieldName
    ) {
        if (flagNames == null || flagNames.isEmpty()) {
            return EnumSet.noneOf(jakarta.validation.constraints.Pattern.Flag.class);
        }
        Set<jakarta.validation.constraints.Pattern.Flag> flags =
            EnumSet.noneOf(jakarta.validation.constraints.Pattern.Flag.class);
        for (String flagName : flagNames) {
            try {
                flags.add(jakarta.validation.constraints.Pattern.Flag.valueOf(flagName));
            }
            catch (IllegalArgumentException exception) {
                throw new InvalidConstraintConfigurationException(
                    "Invalid pattern flag '" + flagName + "' for class=" + className
                        + ", field=" + fieldName + ". Valid flags: "
                        + java.util.Arrays.toString(jakarta.validation.constraints.Pattern.Flag.values()),
                    exception);
            }
        }
        return flags;
    }

    private SizeBoundCandidate toSizeBound(
        Long value,
        String message,
        String className,
        String fieldName,
        String propertyName
    ) {
        Integer bound = toSizeInteger(value, className, fieldName, propertyName);
        return (bound == null) ? null : new SizeBoundCandidate(bound, message);
    }

    private SizeBoundCandidate sizeCandidate(Integer value) {
        return new SizeBoundCandidate(value, null);
    }

    private NumericBound bound(BoundCandidate candidate) {
        return (candidate == null) ? null : candidate.bound();
    }

    private Integer sizeValue(SizeBoundCandidate candidate) {
        return (candidate == null) ? null : candidate.value();
    }

    private String message(BoundCandidate candidate) {
        return (candidate == null) ? null : candidate.message();
    }

    private String message(SizeBoundCandidate candidate) {
        return (candidate == null) ? null : candidate.message();
    }

    private Integer toSizeInteger(Long value, String className, String fieldName, String propertyName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new InvalidConstraintConfigurationException(
                "Invalid size constraint. " + propertyName + " must be >= 0 for class="
                    + className + ", field=" + fieldName + ", value=" + value);
        }
        try {
            return Math.toIntExact(value);
        }
        catch (ArithmeticException exception) {
            throw new InvalidConstraintConfigurationException(
                "Invalid size constraint. " + propertyName + " exceeds Integer.MAX_VALUE for class="
                    + className + ", field=" + fieldName + ", value=" + value,
                exception);
        }
    }

    private List<ExtensionRegexRule> toConfiguredExtensionRules(
        List<ConstraintOverrideSet.ExtensionRule> configuredRules,
        String className,
        String fieldName
    ) {
        if (configuredRules == null || configuredRules.isEmpty()) {
            return List.of();
        }

        List<ExtensionRegexRule> extensionRules = new ArrayList<>();
        for (int index = 0; index < configuredRules.size(); index++) {
            extensionRules.add(toConfiguredExtensionRule(configuredRules.get(index), className, fieldName, index));
        }
        return extensionRules;
    }

    private ExtensionRegexRule toConfiguredExtensionRule(
        ConstraintOverrideSet.ExtensionRule configuredRule,
        String className,
        String fieldName,
        int index
    ) {
        if (configuredRule == null) {
            throw new InvalidConstraintConfigurationException(
                "Invalid extensions constraint. rule must be non-null for class="
                    + className + ", field=" + fieldName + ", index=" + index);
        }

        String jsonPath = requireNonEmpty("extensions", "jsonPath", configuredRule.getJsonPath(), className, fieldName, index);
        validateJsonPath(jsonPath, className, fieldName, index);
        String regex = requireNonEmpty("extensions", "regex", configuredRule.getRegex(), className, fieldName, index);
        validateRegex("extensions", regex, className, fieldName, index);
        return new ExtensionRegexRule(jsonPath, regex, configuredRule.getMessage());
    }

    private String requireNonEmpty(
        String constraintName,
        String propertyName,
        String value,
        String className,
        String fieldName,
        int index
    ) {
        if (value == null || value.isEmpty()) {
            throw new InvalidConstraintConfigurationException(
                "Invalid " + constraintName + " constraint. " + propertyName + " must be non-empty for class="
                    + className + ", field=" + fieldName + ", index=" + index);
        }
        return value;
    }

    private void validateRegex(
        String constraintName,
        String regex,
        String className,
        String fieldName,
        int index
    ) {
        try {
            Pattern.compile(regex);
        }
        catch (PatternSyntaxException exception) {
            throw new InvalidConstraintConfigurationException(
                "Invalid " + constraintName + " constraint. regex could not be compiled for class="
                    + className + ", field=" + fieldName + ", index=" + index + ", regex=" + regex,
                exception);
        }
    }

    private void validateJsonPath(String jsonPath, String className, String fieldName, int index) {
        try {
            JsonPath.compile(jsonPath);
        }
        catch (InvalidPathException | IllegalArgumentException exception) {
            throw new InvalidConstraintConfigurationException(
                "Invalid extensions constraint. jsonPath could not be compiled for class="
                    + className + ", field=" + fieldName + ", index=" + index + ", jsonPath=" + jsonPath,
                exception);
        }
    }

    private record BoundCandidate(NumericBound bound, String message) {
    }

    private record SizeBoundCandidate(Integer value, String message) {
    }

    private record BooleanConstraintState(boolean enabled, String message) {
    }

    private record PatternIdentity(String regex, Set<jakarta.validation.constraints.Pattern.Flag> flags) {

        private PatternIdentity {
            flags = Set.copyOf(flags);
        }
    }
}
