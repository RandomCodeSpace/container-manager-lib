package io.github.randomcodespace.container.TestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility class for platform-independent operations in tests. */
public class PlatformUtils {

  /**
   * Returns a platform-appropriate path for a container tool executable.
   *
   * @param toolName The name of the tool (e.g., "docker", "podman", "buildah")
   * @return A Path object representing the executable path
   */
  public static Path getToolPath(String toolName) {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    if (isWindows) {
      // On Windows, executables typically have .exe extension and are in different locations
      switch (toolName.toLowerCase()) {
        case "docker":
          return Paths.get("C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe");
        case "podman":
          return Paths.get("C:\\Program Files\\RedHat\\Podman\\podman.exe");
        case "buildah":
          // Buildah is not commonly installed on Windows, but we'll provide a path anyway
          return Paths.get("C:\\Program Files\\buildah\\buildah.exe");
        default:
          throw new IllegalArgumentException("Unknown tool: " + toolName);
      }
    } else {
      // On Unix-like systems, executables are typically in /usr/bin
      return Paths.get("/usr/bin/" + toolName);
    }
  }
}
