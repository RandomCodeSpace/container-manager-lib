package io.github.randomcodespace.container.exceptions;

public class ToolDetectionException extends ContainerManagerException {
  public ToolDetectionException(String message) {
    super(message);
  }

  public ToolDetectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
