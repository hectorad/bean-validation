# Validation Spring Boot Starter

`validation-spring-boot-starter` turns the original sample app into a reusable Spring Boot validation framework with optional Feign and Kafka adapters.

## Modules

- `validation-core`
- `validation-hibernate`
- `validation-spring-boot-autoconfigure`
- `validation-feign`
- `validation-kafka`
- `validation-spring-boot-starter`
- `validation-sample-app`

## Coordinates

```xml
<dependency>
    <groupId>io.github.hectorad.validation</groupId>
    <artifactId>validation-spring-boot-starter</artifactId>
    <version>${validation.version}</version>
</dependency>
```

## Configuration

The property namespace is `hector.validation`.

```yaml
hector:
  validation:
    enabled: true
    fail-on-error: true
    overrides:
      - class-name: com.acme.forms.PersonForm
        fields:
          - field-name: name
            constraints:
              not-null:
                value: true
                message: Name is required
              size:
                min:
                  value: 5
```

Optional adapters:

- `hector.validation.feign.enabled=true`
- `hector.validation.kafka.enabled=true`

Servlet-only bypass:

- `hector.validation.http-bypass.enabled=true`

## SPI

Programmatic overrides use `ValidationOverrideContributor`.

```java
@Component
@Order(-100)
class MyOverrides implements ValidationOverrideContributor {

    @Override
    public List<ClassValidationOverride> getValidationOverrides() {
        ConstraintOverrideSet constraints = new ConstraintOverrideSet();
        constraints.getMin().setValue(21L);

        return List.of(
            new ClassValidationOverride(
                "com.acme.forms.PersonForm",
                List.of(new FieldValidationOverride("age", constraints))));
    }
}
```

## Build

```bash
./mvnw test
```

Additional docs:

- [Configuration Guide](docs/configuration-guide.md)
- [Constraint Mapping Flow](docs/validation-constraint-mapping.md)
