package io.github.randomcodespace.container.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

/** Configuration for creating a new container. This DTO abstracts common configuration options. */
@Getter
@Builder
@ToString
public class ContainerConfig {
  private final String imageName; // Includes tag, e.g., "ubuntu:latest"
  private final String containerName; // Optional name for the container
  private final List<String> command; // Command to run in the container
  private final List<String> entryPoint;

  @Singular("envVariable")
  private final Map<String, String> environmentVariables;

  // @Singular("portMapping")
  // private final Map<String, String> portMappings; // e.g., "8080:80" host:container
  // @Singular("volumeMapping")
  // private final Map<String, String> volumeMappings; // e.g., "/host/path:/container/path"
  private final String workingDir;
  private final Boolean autoRemove; // Whether to remove the container when it exits
  private final Boolean detach; // Run in detached mode
  private final Boolean tty; // Allocate a pseudo-TTY
  // Add more fields like CPU limits, memory limits, restart policy, labels, etc.
}
