package com.crio.qeats.exchanges;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PostOrderRequest {
  @NotNull
  String cartId;

  public String getCartId() {
    return cartId;
  }

  public void setCartId(String cartId) {
    this.cartId = cartId;
  }
}