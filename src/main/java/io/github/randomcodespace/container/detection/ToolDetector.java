package io.github.randomcodespace.container.detection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.utils.JsonParserUtil;
import io.github.randomcodespace.container.utils.ProcessExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolDetector {
  private static final Logger logger = LoggerFactory.getLogger(ToolDetector.class);

  private static final List<String> LINUX_COMMON_PATHS =
      Arrays.asList("/usr/bin", "/usr/local/bin", "/snap/bin");
  private static final List<String> MACOS_COMMON_PATHS =
      Arrays.asList("/usr/local/bin", "/opt/homebrew/bin");

  private final List<ToolType> preferredToolOrder;

  public ToolDetector(List<ToolType> preferredToolOrder) {
    if (preferredToolOrder == null || preferredToolOrder.isEmpty()) {
      this.preferredToolOrder = List.of(ToolType.PODMAN, ToolType.DOCKER, ToolType.BUILDAH);
    } else {
      this.preferredToolOrder = preferredToolOrder;
    }
  }

  public ToolDetector() {
    this(List.of(ToolType.PODMAN, ToolType.DOCKER, ToolType.BUILDAH));
  }

  public CompletableFuture<Optional<DetectedToolInfo>> detectPreferredTool() {
    List<CompletableFuture<Optional<DetectedToolInfo>>> detectionFutures = new ArrayList<>();

    for (ToolType toolType : preferredToolOrder) {
      detectionFutures.add(detectTool(toolType));
    }

    return CompletableFuture.allOf(detectionFutures.toArray(new CompletableFuture[0]))
        .thenApply(
            v -> {
              for (CompletableFuture<Optional<DetectedToolInfo>> future : detectionFutures) {
                try {
                  Optional<DetectedToolInfo> toolInfoOpt = future.get();
                  if (toolInfoOpt.isPresent()) {
                    logger.info("Preferred tool detected and verified: {}", toolInfoOpt.get());
                    return toolInfoOpt;
                  }
                } catch (InterruptedException | ExecutionException e) {
                  logger.warn("Error during detection of a tool, continuing search.", e);
                }
              }
              logger.warn("No preferred container tool detected or verified successfully.");
              return Optional.empty();
            });
  }

  public CompletableFuture<Optional<DetectedToolInfo>> detectTool(ToolType toolType) {
    String executableName = getExecutableName(toolType);
    if (executableName == null) {
      return CompletableFuture.completedFuture(Optional.empty());
    }

    return findExecutable(executableName)
        .thenCompose(
            executablePathOpt -> {
              if (executablePathOpt.isEmpty()) {
                logger.debug("Executable for {} not found.", toolType);
                return CompletableFuture.completedFuture(Optional.<DetectedToolInfo>empty());
              }
              Path executablePath = executablePathOpt.get();
              logger.debug("Found {} executable at: {}", toolType, executablePath);

              return getToolVersion(toolType, executablePath)
                  .thenCompose(
                      versionOpt -> {
                        if (versionOpt.isEmpty()) {
                          logger.warn(
                              "Could not determine version for {} at {}", toolType, executablePath);
                          return CompletableFuture.completedFuture(
                              Optional.<DetectedToolInfo>empty());
                        }
                        String version = versionOpt.get();
                        logger.info(
                            "Detected {} version: {} at {}", toolType, version, executablePath);

                        if (toolType == ToolType.DOCKER || toolType == ToolType.PODMAN) {
                          return checkApiService(toolType, executablePath)
                              .thenApply(
                                  apiAvailable ->
                                      Optional.of(
                                          new DetectedToolInfo(
                                              toolType, version, executablePath, apiAvailable)));
                        } else {
                          return CompletableFuture.completedFuture(
                              Optional.of(new DetectedToolInfo(toolType, version, executablePath)));
                        }
                      });
            })
        .exceptionally(
            ex -> {
              logger.error("Exception during detection of {}: {}", toolType, ex.getMessage(), ex);
              return Optional.empty();
            });
  }

  private String getExecutableName(ToolType toolType) {
    return switch (toolType) {
      case DOCKER -> "docker";
      case PODMAN -> "podman";
      case BUILDAH -> "buildah";
      default -> null;
    };
  }

  private CompletableFuture<Optional<Path>> findExecutable(String executableName) {
    return CompletableFuture.supplyAsync(
        () -> {
          String systemPath = System.getenv("PATH");
          if (systemPath != null) {
            Optional<Path> foundInPath =
                Arrays.stream(systemPath.split(System.getProperty("path.separator")))
                    .map(Paths::get)
                    .map(dir -> dir.resolve(executableName))
                    .filter(Files::isExecutable)
                    .findFirst();
            if (foundInPath.isPresent()) return foundInPath;
          }

          List<String> commonPaths = getOsSpecificCommonPaths();
          for (String commonDir : commonPaths) {
            Path potentialPath = Paths.get(commonDir, executableName);
            if (Files.isExecutable(potentialPath)) {
              return Optional.of(potentialPath);
            }
          }
          return Optional.empty();
        });
  }

  private List<String> getOsSpecificCommonPaths() {
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("mac")) {
      return MACOS_COMMON_PATHS;
    } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
      return LINUX_COMMON_PATHS;
    } else if (osName.contains("win")) {
      return List.of();
    }
    return List.of();
  }

  private CompletableFuture<Optional<String>> getToolVersion(
      ToolType toolType, Path executablePath) {
    List<String> command = new ArrayList<>();
    command.add(executablePath.toString());
    command.add("version");
    if (toolType == ToolType.PODMAN || toolType == ToolType.DOCKER) {
      command.add("--format");
      command.add("{{json .Client.Version}}");
      if (toolType == ToolType.PODMAN) {
        command.clear();
        command.add(executablePath.toString());
        command.add("version");
        command.add("--format=json");
      }
    }

    return ProcessExecutor.execute(command)
        .thenApply(
            result -> {
              if (result.exitCode() == 0 && result.stdout() != null && !result.stdout().isBlank()) {
                String output = result.stdout().trim();
                if ((toolType == ToolType.DOCKER || toolType == ToolType.PODMAN)
                    && output.startsWith("{")) {
                  try {
                    if (toolType == ToolType.PODMAN) {
                      Optional<Map<String, Object>> versionMapOpt =
                          JsonParserUtil.fromJson(
                              output, new TypeReference<Map<String, Object>>() {});
                      if (versionMapOpt.isPresent() && versionMapOpt.get().containsKey("Version")) {
                        return Optional.of(versionMapOpt.get().get("Version").toString());
                      }
                    } else if (toolType == ToolType.DOCKER) {
                      if (output.startsWith("\"") && output.endsWith("\"")) {
                        output = output.substring(1, output.length() - 1);
                      }
                      if (!output.contains("{")) return Optional.of(output);

                      Optional<Map<String, Object>> versionMapOpt =
                          JsonParserUtil.fromJson(
                              output, new TypeReference<Map<String, Object>>() {});
                      if (versionMapOpt.isPresent()
                          && versionMapOpt.get().get("Client") instanceof Map) {
                        Map<String, Object> clientMap =
                            (Map<String, Object>) versionMapOpt.get().get("Client");
                        if (clientMap.containsKey("Version")) {
                          return Optional.of(clientMap.get("Version").toString());
                        }
                      }
                    }
                  } catch (Exception e) {
                    logger.warn(
                        "Failed to parse JSON version output for {}, falling back to text. Output: {}",
                        toolType,
                        output,
                        e);
                  }
                }
                String[] parts = output.split("[\\s,]+");
                for (int i = 0; i < parts.length; i++) {
                  if (parts[i].equalsIgnoreCase("version") && i + 1 < parts.length) {
                    if (parts[i + 1].matches("\\d+(\\.\\d+)*(\\S*)?")) {
                      return Optional.of(parts[i + 1]);
                    }
                  }
                  if (parts[i].matches("\\d+(\\.\\d+)*(\\S*)?")) {
                    return Optional.of(parts[i]);
                  }
                }
                if (!output.isEmpty()) {
                  logger.warn(
                      "Could not reliably parse version for {}, using raw output: {}",
                      toolType,
                      output);
                  return Optional.of(output.split(System.lineSeparator())[0]);
                }
                return Optional.empty();
              }
              logger.warn(
                  "Failed to get version for {} from {}: exit code {}, stdout: '{}', stderr: '{}'",
                  toolType,
                  executablePath,
                  result.exitCode(),
                  result.stdout(),
                  result.stderr());
              return Optional.empty();
            });
  }

  private CompletableFuture<Boolean> checkApiService(ToolType toolType, Path executablePath) {
    if (toolType == ToolType.DOCKER) {
      return CompletableFuture.supplyAsync(
          () -> {
            try {
              DockerClientConfig config =
                  DefaultDockerClientConfig.createDefaultConfigBuilder().build();
              DockerHttpClient httpClient =
                  new ApacheDockerHttpClient.Builder()
                      .dockerHost(config.getDockerHost())
                      .sslConfig(config.getSSLConfig())
                      .build();
              DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
              dockerClient.pingCmd().exec();
              logger.info("Docker API service is available and responsive.");
              try {
                httpClient.close();
              } catch (IOException e) {
                logger.warn("Error closing DockerHttpClient after ping", e);
              }
              return true;
            } catch (Exception e) {
              logger.warn(
                  "Docker API service is NOT available or unresponsive: {}", e.getMessage());
              return false;
            }
          });
    } else if (toolType == ToolType.PODMAN) {
      logger.info(
          "Podman API service check: For MVP, this is simplified. A robust check (e.g., socket connection) is needed.");
      List<String> command =
          List.of(executablePath.toString(), "system", "info", "--format", "json");
      return ProcessExecutor.execute(command)
          .thenApply(
              result -> {
                if (result.exitCode() == 0
                    && result.stdout() != null
                    && !result.stdout().isBlank()) {
                  Optional<Map<String, Object>> infoMapOpt =
                      JsonParserUtil.fromJson(
                          result.stdout(), new TypeReference<Map<String, Object>>() {});
                  if (infoMapOpt.isPresent()) {
                    Map<String, Object> infoMap = infoMapOpt.get();
                    if (infoMap.containsKey("remoteSocket")
                        && ((Map<?, ?>) infoMap.get("remoteSocket")).get("path") != null) {
                      logger.info(
                          "Podman API service appears to be configured (remoteSocket found).");
                      return true;
                    }
                  }
                }
                logger.warn(
                    "Podman API service may not be available or responsive (based on 'podman system info').");
                return false;
              })
          .exceptionally(
              ex -> {
                logger.warn("Error checking Podman API service status via CLI.", ex);
                return false;
              });
    }
    return CompletableFuture.completedFuture(false);
  }
}
