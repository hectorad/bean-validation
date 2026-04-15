package com.example.validatingforminput.perf;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

abstract class AbstractPerfValidationRequest {

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }
}
