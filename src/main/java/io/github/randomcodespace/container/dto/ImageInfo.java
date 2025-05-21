package io.github.randomcodespace.container.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

/**
 * Data Transfer Object representing information about a container image. Using Java 17 records
 * would also be an option here for immutability and conciseness. For broader compatibility and if
 * mutability is needed during construction, Lombok's @Builder is used.
 */
@Getter
@Builder
@ToString
@Jacksonized // For Jackson deserialization with Lombok's builder
public class ImageInfo {
  private final String id;
  private final List<String> repoTags;
  private final List<String> repoDigests;
  private final String parentId;
  private final long size; // Size in bytes
  private final long virtualSize; // Virtual size in bytes
  private final OffsetDateTime created;
  private final Map<String, String> labels;
  // Add other relevant fields as needed, e.g., architecture, os
}
