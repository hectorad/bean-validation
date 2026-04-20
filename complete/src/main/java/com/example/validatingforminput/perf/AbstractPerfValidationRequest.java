package com.example.validatingforminput.perf;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

abstract class AbstractPerfValidationRequest {

    @NotNull
    @NotBlank
    @Size(min = 9, max = 24)
    @Pattern(regexp = "^CART-[A-Z0-9]{4,19}$")
    private String cartId;

    @NotNull
    @NotBlank
    @Size(min = 8, max = 24)
    private String customerId;

    @NotNull
    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    @DecimalMin(value = "1000.00", inclusive = false)
    @DecimalMax("999999.99")
    private BigDecimal totalAmount;

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
