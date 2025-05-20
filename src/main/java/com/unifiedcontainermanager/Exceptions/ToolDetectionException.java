package com.unifiedcontainermanager.Exceptions;

public class ToolDetectionException extends ContainerManagerException {
  public ToolDetectionException(String message) {
    super(message);
  }

  public ToolDetectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
