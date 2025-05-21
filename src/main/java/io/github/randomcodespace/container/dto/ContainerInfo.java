package io.github.randomcodespace.container.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

/** Data Transfer Object representing information about a container. */
@Getter
@Builder
@ToString
@Jacksonized
public class ContainerInfo {
  private final String id;
  private final List<String> names;
  private final String image;
  private final String imageID;
  private final String command;
  private final OffsetDateTime created;
  private final String status; // e.g., "running", "exited", "created"
  private final String state; // More detailed state if available
  // private final List<PortMapping> ports; // Requires PortMapping DTO
  private final Map<String, String> labels;
  // Add other relevant fields: mounts, network settings etc.
}
