package com.example.validatingforminput.perf;

import java.util.List;

public record ShoppingCartPayload(
    String cartCode,
    List<ShoppingCartItemPayload> items
) {
}
