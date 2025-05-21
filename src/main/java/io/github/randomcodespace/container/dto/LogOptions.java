package io.github.randomcodespace.container.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** Options for retrieving container logs. */
@Getter
@Builder
@ToString
public class LogOptions {
  private final boolean follow; // Stream logs
  private final boolean timestamps;
  private final Integer tail; // Number of lines to show from the end of the logs
  private final String since; // Show logs since timestamp (e.g., RFC3339Nano or Unix timestamp)
  private final String until; // Show logs before a timestamp
  private final boolean stdout; // Include STDOUT
  private final boolean stderr; // Include STDERR

  public static LogOptions defaults() {
    return LogOptions.builder().stdout(true).stderr(true).build();
  }
}
