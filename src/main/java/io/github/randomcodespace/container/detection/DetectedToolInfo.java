package io.github.randomcodespace.container.detection;

import io.github.randomcodespace.container.enums.ToolType;
import java.nio.file.Path;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString
public class DetectedToolInfo {
  private final ToolType toolType;
  private final String version; // Full version string
  private final Path executablePath;
  private final boolean
      apiServiceAvailable; // True if the tool's API service (e.g., Docker daemon, Podman service)

  // is responsive

  // Constructor for CLI-only tools like Buildah or when API status is not yet checked/applicable
  public DetectedToolInfo(ToolType toolType, String version, Path executablePath) {
    this(toolType, version, executablePath, false);
  }
}
