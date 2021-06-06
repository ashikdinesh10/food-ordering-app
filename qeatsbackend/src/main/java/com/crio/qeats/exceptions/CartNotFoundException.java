
package com.crio.qeats.exceptions;

public class CartNotFoundException extends QEatsException {

  private static final long serialVersionUID = -8937041136774288288L;

  @Override
  public int getErrorType() {
    return CART_NOT_FOUND;
  }
}
