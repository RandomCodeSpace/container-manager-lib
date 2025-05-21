package io.github.randomcodespace.container.exceptions;

public class ApiResponseException extends ContainerManagerException {
  private final int statusCode; // HTTP status code, for example

  public ApiResponseException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public ApiResponseException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " (Status Code: " + statusCode + ")";
  }
}
