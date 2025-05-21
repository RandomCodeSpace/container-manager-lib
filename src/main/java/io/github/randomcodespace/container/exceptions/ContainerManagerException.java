package io.github.randomcodespace.container.exceptions;

public class ContainerManagerException extends RuntimeException {
  public ContainerManagerException(String message) {
    super(message);
  }

  public ContainerManagerException(String message, Throwable cause) {
    super(message, cause);
  }
}
