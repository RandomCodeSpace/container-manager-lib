package com.unifiedcontainermanager.Exceptions;

public class ToolNotFoundException extends ContainerManagerException {
  public ToolNotFoundException(String message) {
    super(message);
  }

  public ToolNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
