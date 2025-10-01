package com.kingpixel.ultraeconomy.exceptions;

import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 28/09/2025 7:23
 */
public class UnknownAccountException extends RuntimeException {
  public UnknownAccountException(UUID uuid) {
    super("Unknown account with uuid: " + uuid);
  }
}

