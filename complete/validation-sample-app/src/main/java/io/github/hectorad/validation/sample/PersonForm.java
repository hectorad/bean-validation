package io.github.hectorad.validation.sample;


import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.*;

public class PersonForm {

	@NotNull
	@NotBlank
	@Size(min=3, max=30)
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

	public String getName() {
		return this.name;
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

	public Map<String, Object> getExtensions() {
		return extensions;
	}

	public void setExtensions(Map<String, Object> extensions) {
		this.extensions = extensions;
	}

	public String toString() {
		return "Person(Name: " + this.name + ", Age: " + this.age + ", Salary: " + this.salary + ", Extensions: " + this.extensions + ")";
	}
}
