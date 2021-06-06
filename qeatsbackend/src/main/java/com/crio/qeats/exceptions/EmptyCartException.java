
package com.crio.qeats.exceptions;

public class EmptyCartException extends QEatsException {

  private static final long serialVersionUID = 1L;

  public EmptyCartException(String message) {
    super(message);
  }

  @Override
  public int getErrorType() {
    return EMPTY_CART;
  }
}
