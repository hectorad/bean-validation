@org.springframework.modulith.ApplicationModule(
	allowedDependencies = {
		"core :: api",
		"core :: spi",
		"core :: runtime",
		"feign :: api",
		"feign :: spi",
		"feign :: runtime",
		"kafka :: spi",
		"kafka :: runtime"
	}
)
package com.example.validation.autoconfigure;
