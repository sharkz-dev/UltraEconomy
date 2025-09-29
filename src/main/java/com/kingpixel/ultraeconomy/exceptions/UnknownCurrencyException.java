package com.kingpixel.ultraeconomy.exceptions;

import com.kingpixel.ultraeconomy.config.Currencies;

/**
 * @author Carlos Varas Alonso - 28/09/2025 7:23
 */
public class UnknownCurrencyException extends RuntimeException {
  public UnknownCurrencyException(String currency) {
    super("Invalid currency: " + currency +
      ". Available currencies: " + String.join(", ", Currencies.CURRENCY_IDS));
  }
}

