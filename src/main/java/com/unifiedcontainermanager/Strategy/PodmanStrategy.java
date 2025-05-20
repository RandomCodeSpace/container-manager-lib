package com.unifiedcontainermanager.Strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.unifiedcontainermanager.DTOs.*;
import com.unifiedcontainermanager.Detection.DetectedToolInfo;
import com.unifiedcontainermanager.Enums.ToolType;
import com.unifiedcontainermanager.Exceptions.CommandExecutionException;
import com.unifiedcontainermanager.Exceptions.ContainerManagerException;
import com.unifiedcontainermanager.Utils.JsonParserUtil;
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
 * Strategy for interacting with Podman. For MVP, this will primarily use CLI interactions. API
 * interaction (Libpod or Compat) would be a more advanced implementation.
 */
public class PodmanStrategy extends AbstractCliStrategy {
  private static final Logger logger = LoggerFactory.getLogger(PodmanStrategy.class);
  private boolean preferApi =
      false; // Could be set based on DetectedToolInfo.isApiServiceAvailable()

  @Override
  public ToolType getToolType() {
    return ToolType.PODMAN;
  }

  @Override
  public void initialize(DetectedToolInfo toolInfo) {
    super.initialize(toolInfo);
    // If Podman API service is detected as available, we might prefer it.
    this.preferApi = toolInfo.isApiServiceAvailable();
    if (this.preferApi) {
      logger.info(
          "Podman API service is available. This strategy might attempt API calls (not fully implemented in this MVP).");
      // Initialize API client if one exists/is implemented
    } else {
      logger.info(
          "Podman API service not detected or not preferred. Using CLI interaction for Podman.");
    }
  }

  // --- ContainerManager Implementation (CLI-based for MVP) ---

  @Override
  public CompletableFuture<String> createContainer(ContainerConfig config) {
    ensureInitialized();
    // podman create [OPTIONS] IMAGE [COMMAND [ARG...]]
    List<String> args = new ArrayList<>();
    args.add("create");
    if (config.getContainerName() != null && !config.getContainerName().isBlank()) {
      args.add("--name");
      args.add(config.getContainerName());
    }
    if (config.getDetach() != null && config.getDetach()) {
      // For 'create', detach is implicit, but some options might imply foreground for other
      // commands
    }
    if (config.getAutoRemove() != null && config.getAutoRemove()) {
      args.add("--rm");
    }
    if (config.getTty() != null && config.getTty()) {
      args.add("-t"); // Podman often uses -t for TTY like Docker
    }
    if (config.getEnvironmentVariables() != null) {
      config
          .getEnvironmentVariables()
          .forEach(
              (k, v) -> {
                args.add("-e");
                args.add(k + "=" + v);
              });
    }
    // Add port mappings, volume mappings, entrypoint, workingDir etc.
    // Example: config.getPortMappings().forEach((h,c) -> args.addAll(List.of("-p", h+":"+c)));

    args.add(config.getImageName());

    if (config.getCommand() != null && !config.getCommand().isEmpty()) {
      args.addAll(config.getCommand());
    }

    return executeCliCommand(args)
        .thenApply(
            result -> {
              // Successful `podman create` outputs the container ID
              return handleCliResponse(result, "Podman createContainer successful.").trim();
            });
  }

  @Override
  public CompletableFuture<Void> startContainer(String containerId) {
    ensureInitialized();
    List<String> args = List.of("start", containerId);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(result, "Podman startContainer successful for " + containerId));
  }

  @Override
  public CompletableFuture<Void> stopContainer(String containerId, Integer timeoutSeconds) {
    ensureInitialized();
    List<String> args = new ArrayList<>(List.of("stop"));
    if (timeoutSeconds != null && timeoutSeconds > 0) {
      args.add("-t");
      args.add(String.valueOf(timeoutSeconds));
    }
    args.add(containerId);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(result, "Podman stopContainer successful for " + containerId));
  }

  @Override
  public CompletableFuture<Void> removeContainer(
      String containerId, boolean force, boolean removeVolumes) {
    ensureInitialized();
    List<String> args = new ArrayList<>(List.of("rm"));
    if (force) {
      args.add("-f");
    }
    if (removeVolumes) {
      args.add("-v"); // Podman uses -v for removing anonymous volumes
    }
    args.add(containerId);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(result, "Podman removeContainer successful for " + containerId));
  }

  @Override
  public CompletableFuture<List<ContainerInfo>> listContainers(boolean listAll) {
    ensureInitialized();
    // podman ps --format json
    List<String> args = new ArrayList<>(List.of("ps", "--format", "json"));
    if (listAll) {
      args.add("-a");
    }
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput = handleCliResponse(result, "Podman listContainers successful.");
              // Podman's `ps --format json` outputs a JSON array of objects.
              Optional<List<PodmanPsOutputEntry>> rawEntries =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<PodmanPsOutputEntry>>() {});
              return rawEntries.orElse(Collections.emptyList()).stream()
                  .map(this::mapRawPsEntryToContainerInfo)
                  .collect(Collectors.toList());
            });
  }

  // Helper DTO for Podman `ps --format json` output
  // Fields need to match the actual JSON output from `podman ps --format json`
  // This is a simplified example. Check Podman docs for exact fields.
  @lombok.Data // For getters, setters, toString, etc.
  private static class PodmanPsOutputEntry {
    // Field names should match the JSON keys from `podman ps --format json`
    // Example fields, verify with actual Podman output:
    public String ID; // Or "Id"
    public String Image;
    public String ImageID; // Added for test compatibility
    public String Command;
    public long CreatedAt; // Podman might output epoch seconds
    public String Status;
    public List<String> Names; // Podman often outputs names as an array
    public String State;
    public Map<String, String>
        Labels; // Podman ps format might not include labels directly, inspect might be needed
    // Add other fields as needed
  }

  private ContainerInfo mapRawPsEntryToContainerInfo(PodmanPsOutputEntry raw) {
    // Mapping logic from PodmanPsOutputEntry to your common ContainerInfo DTO
    // This requires knowing the exact structure of Podman's JSON output.
    return ContainerInfo.builder()
        .id(raw.ID)
        .image(raw.Image)
        .imageID(raw.ImageID) // Added for test compatibility
        .command(raw.Command)
        // .created(OffsetDateTime.from(Instant.ofEpochSecond(raw.CreatedAt))) // Example if epoch
        .status(raw.Status)
        .state(raw.State)
        .names(raw.Names != null && !raw.Names.isEmpty() ? raw.Names : Collections.emptyList())
        .labels(raw.Labels != null ? raw.Labels : Collections.emptyMap())
        .build();
  }

  @Override
  public CompletableFuture<Void> getContainerLogs(
      String containerId, LogOptions options, Consumer<String> logConsumer) {
    ensureInitialized();
    List<String> args = new ArrayList<>(List.of("logs"));
    if (options.isFollow()) args.add("-f");
    if (options.isTimestamps()) args.add("-t");
    if (options.getTail() != null && options.getTail() > 0) {
      args.add("--tail");
      args.add(String.valueOf(options.getTail()));
    }
    if (options.getSince() != null && !options.getSince().isBlank()) {
      args.add("--since");
      args.add(options.getSince());
    }
    // Podman might not support --until in the same way or at all for `logs`
    args.add(containerId);

    return executeCliCommandAndStreamOutput(args, logConsumer)
        .thenAccept(
            exitCode -> {
              if (exitCode != 0
                  && !(options.isFollow()
                      && exitCode == 130)) { // 130 is often SIGINT when follow is interrupted
                logger.warn(
                    "Podman getContainerLogs for {} finished with non-zero exit code: {}",
                    containerId,
                    exitCode);
                // Depending on behavior, might throw exception or just log
                // For follow, non-zero might be normal if user stops following.
              }
              logger.debug("Podman getContainerLogs for {} completed.", containerId);
            });
  }

  @Override
  public CompletableFuture<ContainerInfo> inspectContainer(String containerIdOrName) {
    ensureInitialized();
    // podman inspect <container>
    List<String> args = List.of("inspect", containerIdOrName);
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput =
                  handleCliResponse(
                      result, "Podman inspectContainer successful for " + containerIdOrName);
              // Podman inspect outputs a JSON array with a single element for a container
              Optional<List<Map<String, Object>>> inspectionDataList =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<Map<String, Object>>>() {});
              if (inspectionDataList.isPresent() && !inspectionDataList.get().isEmpty()) {
                Map<String, Object> rawData = inspectionDataList.get().get(0);
                return mapRawInspectToContainerInfo(rawData); // Implement this mapping
              }
              throw new ContainerManagerException(
                  "Failed to parse inspect output or container not found: " + containerIdOrName);
            });
  }

  // --- ImageManager Implementation (CLI-based for MVP) ---

  @Override
  public CompletableFuture<Void> pullImage(
      String imageName, String tag, Consumer<String> progressConsumer) {
    ensureInitialized();
    String fullImageName = imageName + (tag != null && !tag.isBlank() ? ":" + tag : "");
    List<String> args = List.of("pull", fullImageName);

    // Podman pull output can be streamed for progress.
    // If progressConsumer is null, we can use the simpler executeCliCommand.
    if (progressConsumer != null) {
      return executeCliCommandAndStreamOutput(args, progressConsumer)
          .thenAccept(
              exitCode -> {
                if (exitCode != 0) {
                  throw new CommandExecutionException(
                      "Podman pullImage failed for " + fullImageName,
                      exitCode,
                      "See logs for details.");
                }
                logger.info("Podman pullImage successful for {}", fullImageName);
              });
    } else {
      return executeCliCommand(args)
          .thenAccept(
              result ->
                  handleCliResponse(result, "Podman pullImage successful for " + fullImageName));
    }
  }

  @Override
  public CompletableFuture<List<ImageInfo>> listImages() {
    ensureInitialized();
    // podman images --format json
    List<String> args = List.of("images", "--format", "json");
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput = handleCliResponse(result, "Podman listImages successful.");
              // Podman's `images --format json` outputs a JSON array.
              Optional<List<PodmanImagesOutputEntry>> rawEntries =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<PodmanImagesOutputEntry>>() {});
              return rawEntries.orElse(Collections.emptyList()).stream()
                  .map(this::mapRawImageEntryToImageInfo)
                  .collect(Collectors.toList());
            });
  }

  // Helper DTO for Podman `images --format json` output
  // Fields need to match the actual JSON output from `podman images --format json`
  @lombok.Data
  private static class PodmanImagesOutputEntry {
    public String Id; // Or ID
    public List<String> RepositoryTags; // Or similar field name like "Names" or "RepoTags"
    public List<String> Digests; // Or RepoDigests
    public String ParentId;
    public long
        Size; // Podman usually shows size in human-readable, need to check if JSON provides bytes
    public long Created; // Epoch seconds
    public Map<String, String> Labels;
    // Add other fields as needed
  }

  private ImageInfo mapRawImageEntryToImageInfo(PodmanImagesOutputEntry raw) {
    // Mapping logic from PodmanImagesOutputEntry to your common ImageInfo DTO
    return ImageInfo.builder()
        .id(raw.Id)
        .repoTags(raw.RepositoryTags != null ? raw.RepositoryTags : Collections.emptyList())
        .repoDigests(raw.Digests != null ? raw.Digests : Collections.emptyList())
        .parentId(raw.ParentId)
        .size(raw.Size) // Ensure this is bytes
        // .created(OffsetDateTime.from(Instant.ofEpochSecond(raw.Created)))
        .labels(raw.Labels != null ? raw.Labels : Collections.emptyMap())
        .build();
  }

  @Override
  public CompletableFuture<Void> removeImage(
      String imageIdOrName, boolean force, boolean pruneChildren) {
    ensureInitialized();
    List<String> args = new ArrayList<>(List.of("rmi"));
    if (force) {
      args.add("-f");
    }
    // Podman rmi does not have a direct --prune-children. It might prune by default if no tags
    // point to parents.
    // This option might need specific handling or be documented as tool-dependent.
    args.add(imageIdOrName);
    return executeCliCommand(args)
        .thenAccept(
            result ->
                handleCliResponse(result, "Podman removeImage successful for " + imageIdOrName));
  }

  @Override
  public CompletableFuture<String> buildImage(
      ImageBuildConfig config, Consumer<String> outputConsumer) {
    ensureInitialized();
    // podman build [OPTIONS] CONTEXT_DIRECTORY
    List<String> args = new ArrayList<>();
    args.add("build");

    if (config.getTags() != null && !config.getTags().isEmpty()) {
      config.getTags().forEach(tag -> args.addAll(List.of("-t", tag)));
    }
    if (config.getDockerfilePath() != null && !config.getDockerfilePath().isBlank()) {
      args.add("-f");
      args.add(config.getContextDirectory().resolve(config.getDockerfilePath()).toString());
    }
    if (config.getBuildArgs() != null) {
      config.getBuildArgs().forEach((k, v) -> args.addAll(List.of("--build-arg", k + "=" + v)));
    }
    if (config.isNoCache()) args.add("--no-cache");
    if (config.isPullParent())
      args.add("--pull-always"); // Podman uses --pull-always or --pull=true
    if (config.isRemoveIntermediateContainers()) args.add("--rm"); // For intermediate containers
    if (config.getLabels() != null) {
      config.getLabels().forEach((k, v) -> args.addAll(List.of("--label", k + "=" + v)));
    }

    args.add(config.getContextDirectory().toString());

    // Podman build output should be streamed.
    // The last line of successful `podman build` output often contains the image ID.
    // This needs careful parsing or a more structured way if Podman offers it (e.g. --iidfile).
    // For MVP, we'll stream and assume the caller might parse the ID from logs, or we try to
    // capture it.
    // A more robust way is to use `--iidfile <file>` and read the ID from there.

    // For simplicity in MVP, we'll just execute and not try to parse image ID from streamed output.
    // The `outputConsumer` will get all build logs.
    // Returning a placeholder ID or throwing if ID cannot be determined.
    // A better approach for ID: use --iidfile and read it.
    // args.add("--iidfile");
    // Path iidFile = Files.createTempFile("podman_iid_", ".txt");
    // args.add(iidFile.toString());

    if (outputConsumer != null) {
      return executeCliCommandAndStreamOutput(args, outputConsumer)
          .thenApply(
              exitCode -> {
                if (exitCode != 0) {
                  throw new CommandExecutionException(
                      "Podman buildImage failed.", exitCode, "See logs for details.");
                }
                // TODO: Read image ID from iidFile if used.
                // For now, returning a placeholder or requiring user to find from logs.
                logger.info(
                    "Podman buildImage successful. Image ID needs to be retrieved (e.g., from logs or iidfile).");
                return "IMAGE_ID_NEEDS_RETRIEVAL"; // Placeholder
              });
    } else {
      // If no consumer, execute and try to get ID from final output (less reliable)
      return executeCliCommand(args)
          .thenApply(
              result -> {
                handleCliResponse(result, "Podman buildImage successful.");
                // Try to parse image ID from result.stdout() - this is fragile.
                // Example: "Successfully tagged localhost/myimage:latest\n<image_id_sha256>"
                // Or "Getting image source signatures..." followed by ID on a new line.
                String[] lines = result.stdout().trim().split("\\r?\\n");
                if (lines.length > 0) {
                  // Often the last line or one of the last lines is the ID.
                  // This is highly dependent on Podman's output format.
                  String lastLine = lines[lines.length - 1];
                  if (lastLine.matches("^[a-f0-9]{64}$")
                      || lastLine.matches("^[a-f0-9]{12,}$")) { // sha256 or short ID
                    return lastLine;
                  }
                  // Check if it's in "Writing image sha256:..." format
                  for (String line : lines) {
                    if (line.contains("Writing image sha256:")) {
                      return line.substring(line.indexOf("sha256:") + "sha256:".length()).trim();
                    }
                  }
                }
                logger.warn("Could not reliably determine image ID from Podman build output.");
                return "IMAGE_ID_PARSE_FAILED";
              });
    }
  }

  @Override
  public CompletableFuture<ImageInfo> inspectImage(String imageIdOrName) {
    ensureInitialized();
    List<String> args =
        List.of(
            "inspect",
            imageIdOrName); // Podman inspect is usually `podman image inspect` or just `podman
    // inspect` for images/containers
    return executeCliCommand(args)
        .thenApply(
            result -> {
              String jsonOutput =
                  handleCliResponse(result, "Podman inspectImage successful for " + imageIdOrName);
              // Podman inspect image outputs a JSON array with a single image details object
              Optional<List<Map<String, Object>>> inspectionDataList =
                  JsonParserUtil.fromJson(
                      jsonOutput, new TypeReference<List<Map<String, Object>>>() {});
              if (inspectionDataList.isPresent() && !inspectionDataList.get().isEmpty()) {
                Map<String, Object> rawData = inspectionDataList.get().get(0);
                return mapRawInspectToImageInfo(rawData); // Implement this mapping
              }
              throw new ContainerManagerException(
                  "Failed to parse inspect output or image not found: " + imageIdOrName);
            });
  }

  // Placeholder for mapping raw JSON from `podman inspect <container>` to ContainerInfo
  private ContainerInfo mapRawInspectToContainerInfo(Map<String, Object> rawData) {
    // Detailed mapping based on Podman's inspect JSON structure
    // This is a complex object, refer to Podman documentation for fields.
    // Example:
    // String id = (String) rawData.get("Id");
    // String name = (String) ((Map<String,Object>)rawData.get("Config")).get("Image"); // Or from
    // Name field
    logger.warn(
        "mapRawInspectToContainerInfo for Podman needs full implementation based on actual JSON structure.");
    return ContainerInfo.builder()
        .id((String) rawData.getOrDefault("Id", "UNKNOWN_ID"))
        .build(); // Highly simplified
  }

  // Placeholder for mapping raw JSON from `podman inspect <image>` to ImageInfo
  private ImageInfo mapRawInspectToImageInfo(Map<String, Object> rawData) {
    // Detailed mapping based on Podman's inspect image JSON structure
    logger.warn(
        "mapRawInspectToImageInfo for Podman needs full implementation based on actual JSON structure.");
    return ImageInfo.builder()
        .id((String) rawData.getOrDefault("Id", "UNKNOWN_ID"))
        .build(); // Highly simplified
  }
}
