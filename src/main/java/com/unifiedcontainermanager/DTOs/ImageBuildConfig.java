package com.unifiedcontainermanager.DTOs;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

/** Configuration for building an image. */
@Getter
@Builder
@ToString
public class ImageBuildConfig {
  private final Path
      contextDirectory; // Path to the build context (directory containing Dockerfile)
  private final String
      dockerfilePath; // Relative path to the Dockerfile within the context directory (e.g.,

  // "Dockerfile")

  @Singular("tag")
  private final List<String> tags; // Desired tags for the built image (e.g., "myapp:latest")

  @Singular("buildArg")
  private final Map<String, String> buildArgs; // Build-time variables

  private final boolean noCache;
  private final boolean pullParent; // Always attempt to pull a newer version of the base image
  private final boolean removeIntermediateContainers;

  @Singular("label")
  private final Map<String, String> labels; // Labels to add to the image
  // Add other options like target stage for multi-stage builds, platform, etc.
}
