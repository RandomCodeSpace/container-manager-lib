package io.github.randomcodespace.container.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.ImageBuildConfig;
import io.github.randomcodespace.container.dto.ImageInfo;
import io.github.randomcodespace.container.dto.LogOptions;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.CommandExecutionException;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.utils.JsonParserUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for interacting with Buildah, primarily via CLI. Buildah is focused on image building.
 * Container lifecycle management is limited to working containers used during the build process.
 */
public class BuildahStrategy extends AbstractCliStrategy {
  private static final Logger logger = LoggerFactory.getLogger(BuildahStrategy.class);

  @Override
  public ToolType getToolType() {
    return ToolType.BUILDAH;
  }

  // --- ContainerManager Implementation (Limited for Buildah) ---
  // Buildah's "containers" are typically working containers for image builds.

  @Override
  public CompletableFuture<String> createContainer(ContainerConfig config) {
    ensureInitialized();
    // `buildah from <image>` creates a working container
    // This is different from `docker create` or `podman create` for runtime.
    // The ContainerConfig DTO might not map perfectly. We primarily need the base image.
    if (config.getImageName() == null || config.getImageName().isBlank()) {
      throw new ContainerManagerException(
          "Base image name must be provided for Buildah 'from' command.");
    }
    List<String> args = new ArrayList<>(List.of("from"));
    // Add options like --name if Buildah supports it for `from` and if in config
    if (config.getContainerName() != null && !config.getContainerName().isBlank()) {
      args.add("--name");
      args.add(config.getContainerName());
    }
    args.add(config.getImageName());

    return executeCliCommand(args)
        .thenApply(
            result -> {
              // `buildah from` outputs the working container name/ID
              return handleCliResponse(result, "Buildah 'from' (createContainer) successful.")
                  .trim();
            });
  }

  @Override
  public CompletableFuture<Void> startContainer(String containerId) {
    // "Starting" a Buildah working container isn't a standard operation like runtime containers.
    // Commands are run inside it using `buildah run`.
    logger.warn(
        "startContainer is not directly applicable to Buildah working containers. Use 'run' for build steps.");
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("startContainer is not standard for Buildah."));
  }

  @Override
  public CompletableFuture<Void> stopContainer(String containerId, Integer timeoutSeconds) {
    logger.warn("stopContainer is not directly applicable to Buildah working containers.");
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("stopContainer is not standard for Buildah."));
  }

  @Override
  public CompletableFuture<Void> removeContainer(
      String containerId, boolean force, boolean removeVolumes) {
    ensureInitialized();
    // `buildah rm <containerId>`
    List<String> args = new ArrayList<>(List.of("rm"));
    // Buildah rm does not have a --force or --volumes flag in the same way.
    // It removes the working container.
    args.add(containerId);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(
                    result,
                    "Buildah removeContainer (working container) successful for " + containerId));
  }

  @Override
  public CompletableFuture<List<ContainerInfo>> listContainers(boolean listAll) {
    ensureInitialized();
    // `buildah containers --json`
    List<String> args = List.of("containers", "--json");
    // `listAll` might not be relevant as it lists working containers.
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput =
                  handleCliResponse(
                      result, "Buildah listContainers (working containers) successful.");
              Optional<List<BuildahContainersOutputEntry>> rawEntries =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<BuildahContainersOutputEntry>>() {});
              return rawEntries.orElse(Collections.emptyList()).stream()
                  .map(this::mapRawBuildahContainerToContainerInfo)
                  .collect(Collectors.toList());
            });
  }

  // Helper DTO for `buildah containers --json`
  @lombok.Data
  private static class BuildahContainersOutputEntry {
    // Fields based on typical `buildah containers --json` output. Verify with Buildah docs.
    public String containername; // or containerid
    public String containerid;
    public String imagename;
    public String imageid;
    // public long created; // May not be present or in different format
    // Buildah might not have all fields that map to ContainerInfo for runtime containers
  }

  private ContainerInfo mapRawBuildahContainerToContainerInfo(BuildahContainersOutputEntry raw) {
    return ContainerInfo.builder()
        .id(raw.containerid)
        .names(raw.containername != null ? List.of(raw.containername) : Collections.emptyList())
        .image(raw.imagename)
        .imageID(raw.imageid)
        .status("working_container") // Specific status for Buildah
        .build();
  }

  @Override
  public CompletableFuture<Void> getContainerLogs(
      String containerId, LogOptions options, Consumer<String> logConsumer) {
    // Buildah working containers don't produce "logs" in the same way as runtime containers.
    // Output from `buildah run` commands would be captured during that execution.
    logger.warn("getContainerLogs is not typically applicable to Buildah working containers.");
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("getContainerLogs is not standard for Buildah."));
  }

  @Override
  public CompletableFuture<ContainerInfo> inspectContainer(String containerIdOrName) {
    ensureInitialized();
    // `buildah inspect --type container <containerIdOrName>`
    List<String> args = List.of("inspect", "--type", "container", containerIdOrName);
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput =
                  handleCliResponse(
                      result, "Buildah inspect container successful for " + containerIdOrName);
              // Buildah inspect output is a single JSON object, not an array for a single item.
              Optional<Map<String, Object>> rawData =
                  JsonParserUtil.fromJson(jsonOutput, new TypeReference<Map<String, Object>>() {});
              if (rawData.isPresent()) {
                return mapRawBuildahInspectToContainerInfo(rawData.get());
              }
              throw new ContainerManagerException(
                  "Failed to parse inspect output or container not found: " + containerIdOrName);
            });
  }

  private ContainerInfo mapRawBuildahInspectToContainerInfo(Map<String, Object> rawData) {
    // Extract relevant fields from Buildah's inspect JSON for a working container
    // This structure will differ from Docker/Podman runtime container inspect.
    // Example:
    // String id = (String) rawData.get("ContainerID");
    // String name = (String) rawData.get("ContainerName");
    // String image = (String) ((Map<String,Object>)rawData.get("FromImage"));
    logger.warn(
        "mapRawBuildahInspectToContainerInfo needs full implementation based on actual JSON structure.");
    return ContainerInfo.builder()
        .id((String) rawData.getOrDefault("ContainerID", "UNKNOWN_ID"))
        .build(); // Highly simplified
  }

  // --- ImageManager Implementation ---

  @Override
  public CompletableFuture<Void> pullImage(
      String imageName, String tag, Consumer<String> progressConsumer) {
    ensureInitialized();
    // `buildah pull <imageName>:<tag>`
    // Buildah pull is often used before `buildah from`.
    String fullImageName = imageName + (tag != null && !tag.isBlank() ? ":" + tag : "");
    List<String> args = new ArrayList<>();
    args.add("pull");
    // Buildah pull has options like --quiet, --all-tags, but progress streaming is less common than
    // Docker/Podman.
    // For simplicity, we'll execute and let consumer handle raw output if provided.
    args.add(fullImageName);

    if (progressConsumer != null) {
      return executeCliCommandAndStreamOutput(args, progressConsumer)
          .thenAccept(
              exitCode -> {
                if (exitCode != 0) {
                  throw new CommandExecutionException(
                      "Buildah pullImage failed for " + fullImageName,
                      exitCode,
                      "See logs for details.");
                }
              });
    } else {
      return executeCliCommand(args)
          .thenAccept(
              result ->
                  handleCliResponse(result, "Buildah pullImage successful for " + fullImageName));
    }
  }

  @Override
  public CompletableFuture<List<ImageInfo>> listImages() {
    ensureInitialized();
    // `buildah images --json`
    List<String> args = List.of("images", "--json");
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput = handleCliResponse(result, "Buildah listImages successful.");
              Optional<List<BuildahImagesOutputEntry>> rawEntries =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<BuildahImagesOutputEntry>>() {});
              return rawEntries.orElse(Collections.emptyList()).stream()
                  .map(this::mapRawBuildahImageToImageInfo)
                  .collect(Collectors.toList());
            });
  }

  // Helper DTO for `buildah images --json`
  @lombok.Data
  private static class BuildahImagesOutputEntry {
    // Fields based on typical `buildah images --json` output. Verify with Buildah docs.
    public String id;
    public List<String> names; // Buildah usually lists all tags here
    public String digest;
    public String created; // String timestamp, e.g., "2 hours ago" or a full date
    public String size; // Human-readable size string
    public Map<String, String>
        labels; // May not be directly in `images` output, inspect might be needed
    // Buildah's output for `images --json` can be simpler than Docker/Podman.
  }

  private ImageInfo mapRawBuildahImageToImageInfo(BuildahImagesOutputEntry raw) {
    // Parse size and created time if possible. This is more complex for Buildah's typical output.
    long sizeBytes = 0; // Placeholder, need to parse raw.size
    // OffsetDateTime createdTime = null; // Placeholder, need to parse raw.created

    return ImageInfo.builder()
        .id(raw.id)
        .repoTags(raw.names != null ? raw.names : Collections.emptyList())
        .repoDigests(raw.digest != null ? List.of(raw.digest) : Collections.emptyList())
        .size(sizeBytes)
        // .created(createdTime)
        .labels(raw.labels != null ? raw.labels : Collections.emptyMap())
        .build();
  }

  @Override
  public CompletableFuture<Void> removeImage(
      String imageIdOrName, boolean force, boolean pruneChildren) {
    ensureInitialized();
    // `buildah rmi <imageIdOrName>`
    List<String> args = new ArrayList<>(List.of("rmi"));
    if (force) {
      args.add("--force");
    }
    // Buildah rmi doesn't have pruneChildren in the same way.
    args.add(imageIdOrName);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(result, "Buildah removeImage successful for " + imageIdOrName));
  }

  @Override
  public CompletableFuture<String> buildImage(
      ImageBuildConfig config, Consumer<String> outputConsumer) {
    ensureInitialized();
    // `buildah bud [OPTIONS] CONTEXT_DIRECTORY` or `buildah build-using-dockerfile ...`
    List<String> args = new ArrayList<>();
    args.add("bud"); // 'bud' is the common alias for build-using-dockerfile

    if (config.getTags() != null && !config.getTags().isEmpty()) {
      config.getTags().forEach(tag -> args.addAll(List.of("-t", tag)));
    }
    // DockerfilePath is usually relative to context, `bud` assumes Dockerfile in context root by
    // default.
    // If ImageBuildConfig.dockerfilePath is just "Dockerfile", it's often implicit.
    // If it's different, use -f
    if (config.getDockerfilePath() != null
        && !config.getDockerfilePath().equals("Dockerfile")
        && !config.getDockerfilePath().isBlank()) {
      args.add("-f");
      args.add(config.getContextDirectory().resolve(config.getDockerfilePath()).toString());
    }

    if (config.getBuildArgs() != null) {
      config.getBuildArgs().forEach((k, v) -> args.addAll(List.of("--build-arg", k + "=" + v)));
    }
    if (config.isNoCache()) args.add("--no-cache");
    if (config.isPullParent()) args.add("--pull-always"); // Buildah uses --pull or --pull-always
    if (config.isRemoveIntermediateContainers()) args.add("--rm");
    if (config.getLabels() != null) {
      config.getLabels().forEach((k, v) -> args.addAll(List.of("--label", k + "=" + v)));
    }

    args.add(config.getContextDirectory().toString());

    // Similar to Podman, getting the image ID from Buildah build output can be tricky.
    // Using --iidfile is the most reliable.
    // For MVP, stream output and leave ID parsing to caller or return placeholder.
    // args.add("--iidfile");
    // Path iidFile = Files.createTempFile("buildah_iid_", ".txt");
    // args.add(iidFile.toString());

    if (outputConsumer != null) {
      return executeCliCommandAndStreamOutput(args, outputConsumer)
          .thenApply(
              exitCode -> {
                if (exitCode != 0) {
                  throw new CommandExecutionException(
                      "Buildah buildImage failed.", exitCode, "See logs for details.");
                }
                // TODO: Read image ID from iidFile if used.
                logger.info("Buildah buildImage successful. Image ID needs to be retrieved.");
                return "IMAGE_ID_NEEDS_RETRIEVAL_BUILDAH"; // Placeholder
              });
    } else {
      return executeCliCommand(args)
          .thenApply(
              result -> {
                handleCliResponse(result, "Buildah buildImage successful.");
                // Buildah's output for `bud` typically ends with the image ID if successful.
                String[] lines = result.stdout().trim().split("\\r?\\n");
                if (lines.length > 0) {
                  String lastLine = lines[lines.length - 1];
                  // Check if it looks like an image ID (sha256 hash, possibly prefixed)
                  if (lastLine.matches("^[a-f0-9]{12,}$") || lastLine.matches("^[a-f0-9]{64}$")) {
                    return lastLine;
                  }
                  // Sometimes it's prefixed with "COMMIT <image_id>" or similar
                  String[] lastLineParts = lastLine.split("\\s+");
                  if (lastLineParts.length > 0
                      && (lastLineParts[lastLineParts.length - 1].matches("^[a-f0-9]{12,}$")
                          || lastLineParts[lastLineParts.length - 1].matches("^[a-f0-9]{64}$"))) {
                    return lastLineParts[lastLineParts.length - 1];
                  }
                }
                logger.warn("Could not reliably determine image ID from Buildah build output.");
                return "IMAGE_ID_PARSE_FAILED_BUILDAH";
              });
    }
  }

  @Override
  public CompletableFuture<ImageInfo> inspectImage(String imageIdOrName) {
    ensureInitialized();
    // `buildah inspect --type image <imageIdOrName>`
    List<String> args = List.of("inspect", "--type", "image", imageIdOrName);
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput =
                  handleCliResponse(result, "Buildah inspectImage successful for " + imageIdOrName);
              // Buildah inspect image outputs a single JSON object
              Optional<Map<String, Object>> rawData =
                  JsonParserUtil.fromJson(jsonOutput, new TypeReference<Map<String, Object>>() {});
              if (rawData.isPresent()) {
                return mapRawBuildahInspectToImageInfo(rawData.get());
              }
              throw new ContainerManagerException(
                  "Failed to parse inspect output or image not found: " + imageIdOrName);
            });
  }

  private ImageInfo mapRawBuildahInspectToImageInfo(Map<String, Object> rawData) {
    // Extract relevant fields from Buildah's inspect image JSON.
    // This structure will differ from Docker/Podman.
    // Example:
    // String id = (String) rawData.get("FromImageID"); // Or another ID field
    // List<String> tags = (List<String>)
    // ((Map<String,Object>)rawData.get("ImageAnnotations")).get("org.opencontainers.image.ref.name");
    logger.warn(
        "mapRawBuildahInspectToImageInfo needs full implementation based on actual JSON structure.");
    return ImageInfo.builder()
        .id((String) rawData.getOrDefault("OCIv1.config.digest", "UNKNOWN_ID_BUILDAH"))
        .build(); // Highly simplified
  }
}
