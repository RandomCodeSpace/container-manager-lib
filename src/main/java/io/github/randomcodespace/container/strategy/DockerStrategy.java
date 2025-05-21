package io.github.randomcodespace.container.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.ImageBuildConfig;
import io.github.randomcodespace.container.dto.ImageInfo;
import io.github.randomcodespace.container.dto.LogOptions;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.ApiResponseException;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.exceptions.ToolNotFoundException;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for interacting with Docker using the docker-java library. This class implements the
 * ContainerToolStrategy interface to provide Docker-specific functionality for container and image
 * management operations.
 */
public class DockerStrategy implements ContainerToolStrategy, Closeable {
  private static final Logger logger = LoggerFactory.getLogger(DockerStrategy.class);

  private DockerClient dockerClient;
  private DockerHttpClient dockerHttpClient;
  private DetectedToolInfo toolInfo;
  private boolean initialized = false;

  /**
   * Gets the type of container tool this strategy supports.
   *
   * @return The ToolType, which is DOCKER for this implementation.
   */
  @Override
  public ToolType getToolType() {
    return ToolType.DOCKER;
  }

  /**
   * Initializes the strategy with information about the detected Docker tool. This method
   * establishes a connection to the Docker daemon and verifies it with a ping.
   *
   * @param toolInfo Information about the detected Docker tool (version, path, API status).
   * @throws ContainerManagerException if initialization fails or the provided toolInfo is invalid.
   */
  @Override
  public void initialize(DetectedToolInfo toolInfo) {
    if (toolInfo == null || toolInfo.getToolType() != ToolType.DOCKER) {
      throw new ContainerManagerException("Invalid ToolInfo for DockerStrategy initialization.");
    }
    this.toolInfo = toolInfo;

    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    this.dockerHttpClient =
        new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .build();

    this.dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);

    try {
      dockerClient.pingCmd().exec();
      this.initialized = true;
      logger.info(
          "DockerStrategy initialized and connected to Docker daemon at {}.",
          config.getDockerHost());
    } catch (Exception e) {
      try {
        this.dockerHttpClient.close();
      } catch (IOException ex) {
        logger.warn("Error closing DockerHttpClient during failed initialization.", ex);
      }
      this.initialized = false;
      throw new ContainerManagerException(
          "Failed to connect to Docker daemon: " + e.getMessage(), e);
    }
  }

  /**
   * Checks if the strategy is properly initialized and ready to be used.
   *
   * @return true if initialized and connected to Docker daemon, false otherwise.
   */
  @Override
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Ensures that the strategy is initialized before performing operations.
   *
   * @throws ContainerManagerException if the strategy is not initialized or the connection failed.
   */
  private void ensureInitialized() {
    if (!initialized || dockerClient == null) {
      throw new ContainerManagerException(
          "DockerStrategy has not been initialized or connection failed.");
    }
  }

  /**
   * Creates a new container with the specified configuration.
   *
   * @param configDTO The configuration for the container to create.
   * @return A CompletableFuture that will complete with the ID of the created container.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<String> createContainer(ContainerConfig configDTO) {
    ensureInitialized();
    return CompletableFuture.supplyAsync(
        () -> {
          CreateContainerCmd cmd = dockerClient.createContainerCmd(configDTO.getImageName());

          if (configDTO.getContainerName() != null && !configDTO.getContainerName().isBlank()) {
            cmd.withName(configDTO.getContainerName());
          }
          if (configDTO.getCommand() != null && !configDTO.getCommand().isEmpty()) {
            cmd.withCmd(configDTO.getCommand());
          }
          if (configDTO.getEntryPoint() != null && !configDTO.getEntryPoint().isEmpty()) {
            cmd.withEntrypoint(configDTO.getEntryPoint());
          }
          if (configDTO.getEnvironmentVariables() != null
              && !configDTO.getEnvironmentVariables().isEmpty()) {
            List<String> envList =
                configDTO.getEnvironmentVariables().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.toList());
            cmd.withEnv(envList);
          }
          if (configDTO.getWorkingDir() != null && !configDTO.getWorkingDir().isBlank()) {
            cmd.withWorkingDir(configDTO.getWorkingDir());
          }
          if (configDTO.getTty() != null) {
            cmd.withTty(configDTO.getTty());
          }

          HostConfig hostConfig = new HostConfig();
          if (configDTO.getAutoRemove() != null) {
            hostConfig.withAutoRemove(configDTO.getAutoRemove());
          }
          cmd.withHostConfig(hostConfig);

          try {
            CreateContainerResponse response = cmd.exec();
            if (response.getWarnings() != null && response.getWarnings().length > 0) {
              for (String warning : response.getWarnings()) {
                logger.warn("Docker create container warning: {}", warning);
              }
            }
            logger.info("Container created successfully with ID: {}", response.getId());
            return response.getId();
          } catch (Exception e) {
            logger.error(
                "Failed to create Docker container for image {}: {}",
                configDTO.getImageName(),
                e.getMessage(),
                e);
            throw new ApiResponseException(
                "Failed to create Docker container: " + e.getMessage(), extractStatusCode(e), e);
          }
        });
  }

  /**
   * Starts a container with the specified ID.
   *
   * @param containerId The ID of the container to start.
   * @return A CompletableFuture that will complete when the container has been started.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<Void> startContainer(String containerId) {
    ensureInitialized();
    return CompletableFuture.runAsync(
        () -> {
          try {
            dockerClient.startContainerCmd(containerId).exec();
            logger.info("Container {} started successfully.", containerId);
          } catch (Exception e) {
            logger.error("Failed to start Docker container {}: {}", containerId, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to start Docker container " + containerId + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  /**
   * Stops a container with the specified ID.
   *
   * @param containerId The ID of the container to stop.
   * @param timeoutSeconds The timeout in seconds before the container is forcibly stopped.
   * @return A CompletableFuture that will complete when the container has been stopped.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<Void> stopContainer(String containerId, Integer timeoutSeconds) {
    ensureInitialized();
    return CompletableFuture.runAsync(
        () -> {
          try {
            StopContainerCmd cmd = dockerClient.stopContainerCmd(containerId);
            if (timeoutSeconds != null && timeoutSeconds > 0) {
              cmd.withTimeout(timeoutSeconds);
            }
            cmd.exec();
            logger.info("Container {} stopped successfully.", containerId);
          } catch (Exception e) {
            logger.error("Failed to stop Docker container {}: {}", containerId, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to stop Docker container " + containerId + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  /**
   * Removes a container with the specified ID.
   *
   * @param containerId The ID of the container to remove.
   * @param force Whether to force removal of the container.
   * @param removeVolumes Whether to remove volumes associated with the container.
   * @return A CompletableFuture that will complete when the container has been removed.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<Void> removeContainer(
      String containerId, boolean force, boolean removeVolumes) {
    ensureInitialized();
    return CompletableFuture.runAsync(
        () -> {
          try {
            RemoveContainerCmd cmd =
                dockerClient
                    .removeContainerCmd(containerId)
                    .withForce(force)
                    .withRemoveVolumes(removeVolumes);
            cmd.exec();
            logger.info("Container {} removed successfully.", containerId);
          } catch (Exception e) {
            logger.error(
                "Failed to remove Docker container {}: {}", containerId, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to remove Docker container " + containerId + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  /**
   * Lists containers managed by Docker.
   *
   * @param listAll Whether to list all containers, including stopped ones.
   * @return A CompletableFuture that will complete with a list of container information.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<List<ContainerInfo>> listContainers(boolean listAll) {
    ensureInitialized();
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            List<Container> containers =
                dockerClient.listContainersCmd().withShowAll(listAll).exec();
            return containers.stream()
                .map(this::mapDockerContainerToContainerInfo)
                .collect(Collectors.toList());
          } catch (Exception e) {
            logger.error("Failed to list Docker containers: {}", e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to list Docker containers: " + e.getMessage(), extractStatusCode(e), e);
          }
        });
  }

  /**
   * Retrieves logs from a container.
   *
   * @param containerId The ID of the container to get logs from.
   * @param options Options for filtering and formatting the logs.
   * @param logConsumer A consumer that will receive each line of the logs.
   * @return A CompletableFuture that will complete when all logs have been consumed.
   * @throws ApiResponseException if the Docker API returns an error.
   * @throws ContainerManagerException if the strategy is not initialized.
   */
  @Override
  public CompletableFuture<Void> getContainerLogs(
      String containerId, LogOptions options, Consumer<String> logConsumer) {
    ensureInitialized();
    CompletableFuture<Void> future = new CompletableFuture<>();
    LogContainerCmd cmd =
        dockerClient
            .logContainerCmd(containerId)
            .withStdOut(options.isStdout())
            .withStdErr(options.isStderr())
            .withTimestamps(options.isTimestamps())
            .withFollowStream(options.isFollow());

    if (options.getTail() != null && options.getTail() > 0) {
      cmd.withTail(options.getTail());
    } else if (options.getTail() != null
        && "all".equalsIgnoreCase(String.valueOf(options.getTail()))) {
      cmd.withTailAll();
    }

    if (options.getSince() != null && !options.getSince().isBlank()) {
      try {
        long sinceTimestamp = Long.parseLong(options.getSince());
        cmd.withSince((int) sinceTimestamp);
      } catch (NumberFormatException e) {
        logger.warn(
            "Invalid 'since' timestamp for logs (must be epoch seconds for Docker): {}",
            options.getSince());
      }
    }

    try {
      cmd.exec(
          new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
              logConsumer.accept(new String(frame.getPayload()).trim());
            }

            @Override
            public void onError(Throwable throwable) {
              logger.error(
                  "Error streaming logs for container {}: {}",
                  containerId,
                  throwable.getMessage(),
                  throwable);
              future.completeExceptionally(
                  new ApiResponseException(
                      "Error streaming logs: " + throwable.getMessage(),
                      extractStatusCode(throwable),
                      throwable));
            }

            @Override
            public void onComplete() {
              logger.debug("Log streaming complete for container {}.", containerId);
              future.complete(null);
            }
          });
    } catch (Exception e) {
      logger.error(
          "Failed to initiate log streaming for container {}: {}", containerId, e.getMessage(), e);
      future.completeExceptionally(
          new ApiResponseException(
              "Failed to initiate log streaming: " + e.getMessage(), extractStatusCode(e), e));
    }
    return future;
  }

  @Override
  public CompletableFuture<ContainerInfo> inspectContainer(String containerIdOrName) {
    ensureInitialized();
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            InspectContainerResponse response =
                dockerClient.inspectContainerCmd(containerIdOrName).exec();
            return mapInspectResponseToContainerInfo(response);
          } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            logger.warn("Container not found for inspection: {}", containerIdOrName);
            throw new ToolNotFoundException("Container not found: " + containerIdOrName, e);
          } catch (Exception e) {
            logger.error(
                "Failed to inspect Docker container {}: {}", containerIdOrName, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to inspect Docker container " + containerIdOrName + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  @Override
  public CompletableFuture<Void> pullImage(
      String imageName, String tag, Consumer<String> progressConsumer) {
    ensureInitialized();
    CompletableFuture<Void> future = new CompletableFuture<>();
    String fullImageName = imageName + (tag != null && !tag.isBlank() ? ":" + tag : "");

    PullImageCmd cmd = dockerClient.pullImageCmd(fullImageName);

    try {
      cmd.exec(
          new ResultCallback.Adapter<PullResponseItem>() {
            @Override
            public void onNext(PullResponseItem item) {
              if (progressConsumer != null) {
                StringBuilder sb = new StringBuilder();
                if (item.getId() != null) sb.append("ID: ").append(item.getId()).append(" ");
                if (item.getStatus() != null)
                  sb.append("Status: ").append(item.getStatus()).append(" ");
                if (item.getProgressDetail() != null) {
                  ResponseItem.ProgressDetail pd = item.getProgressDetail();
                  if (pd.getCurrent() != null && pd.getTotal() != null && pd.getTotal() > 0) {
                    sb.append(String.format("Progress: %d/%d", pd.getCurrent(), pd.getTotal()));
                  } else if (item.getProgress() != null) {
                    sb.append("Progress: ").append(item.getProgress());
                  }
                }
                if (!sb.toString().isBlank()) {
                  progressConsumer.accept(sb.toString().trim());
                }
              }
            }

            @Override
            public void onError(Throwable throwable) {
              logger.error(
                  "Error pulling image {}: {}", fullImageName, throwable.getMessage(), throwable);
              future.completeExceptionally(
                  new ApiResponseException(
                      "Error pulling image " + fullImageName + ": " + throwable.getMessage(),
                      extractStatusCode(throwable),
                      throwable));
            }

            @Override
            public void onComplete() {
              logger.info("Image {} pulled successfully.", fullImageName);
              future.complete(null);
            }
          });
    } catch (Exception e) {
      logger.error("Failed to initiate image pull for {}: {}", fullImageName, e.getMessage(), e);
      future.completeExceptionally(
          new ApiResponseException(
              "Failed to initiate image pull for " + fullImageName + ": " + e.getMessage(),
              extractStatusCode(e),
              e));
    }
    return future;
  }

  @Override
  public CompletableFuture<List<ImageInfo>> listImages() {
    ensureInitialized();
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            List<Image> images = dockerClient.listImagesCmd().withShowAll(true).exec();
            return images.stream()
                .map(this::mapDockerImageToImageInfo)
                .collect(Collectors.toList());
          } catch (Exception e) {
            logger.error("Failed to list Docker images: {}", e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to list Docker images: " + e.getMessage(), extractStatusCode(e), e);
          }
        });
  }

  @Override
  public CompletableFuture<Void> removeImage(
      String imageIdOrName, boolean force, boolean pruneChildren) {
    ensureInitialized();
    return CompletableFuture.runAsync(
        () -> {
          try {
            dockerClient
                .removeImageCmd(imageIdOrName)
                .withForce(force)
                .withNoPrune(!pruneChildren)
                .exec();
            logger.info("Image {} removed successfully.", imageIdOrName);
          } catch (Exception e) {
            logger.error("Failed to remove Docker image {}: {}", imageIdOrName, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to remove Docker image " + imageIdOrName + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  @Override
  public CompletableFuture<String> buildImage(
      ImageBuildConfig configDTO, Consumer<String> outputConsumer) {
    ensureInitialized();
    CompletableFuture<String> future = new CompletableFuture<>();

    BuildImageCmd cmd =
        dockerClient
            .buildImageCmd()
            .withDockerfile(
                configDTO.getContextDirectory().resolve(configDTO.getDockerfilePath()).toFile())
            .withDockerfilePath(configDTO.getDockerfilePath())
            .withBaseDirectory(configDTO.getContextDirectory().toFile());

    if (configDTO.getTags() != null && !configDTO.getTags().isEmpty()) {
      cmd.withTags(configDTO.getTags().stream().collect(Collectors.toSet()));
    }
    if (configDTO.getBuildArgs() != null && !configDTO.getBuildArgs().isEmpty()) {
      configDTO.getBuildArgs().forEach((key, value) -> cmd.withBuildArg(key, value));
    }
    cmd.withNoCache(configDTO.isNoCache());
    cmd.withPull(configDTO.isPullParent());
    cmd.withRemove(configDTO.isRemoveIntermediateContainers());
    if (configDTO.getLabels() != null && !configDTO.getLabels().isEmpty()) {
      cmd.withLabels(configDTO.getLabels());
    }

    try {
      cmd.exec(
          new ResultCallback.Adapter<BuildResponseItem>() {
            private String imageId;

            @Override
            public void onNext(BuildResponseItem item) {
              if (item.getStream() != null) {
                if (outputConsumer != null) outputConsumer.accept(item.getStream().trim());
                logger.trace("Build output: {}", item.getStream().trim());
              }
              if (item.isBuildSuccessIndicated() && item.getImageId() != null) {
                this.imageId = item.getImageId();
              }
              if (item.getErrorDetail() != null) {
                logger.error("Build error detail: {}", item.getErrorDetail().getMessage());
              }
            }

            @Override
            public void onError(Throwable throwable) {
              logger.error("Error building image: {}", throwable.getMessage(), throwable);
              future.completeExceptionally(
                  new ApiResponseException(
                      "Error building image: " + throwable.getMessage(),
                      extractStatusCode(throwable),
                      throwable));
            }

            @Override
            public void onComplete() {
              if (this.imageId != null) {
                logger.info("Image built successfully with ID: {}", this.imageId);
                future.complete(this.imageId);
              } else {
                logger.error(
                    "Image build completed, but no image ID was captured. Check build logs.");
                future.completeExceptionally(
                    new ContainerManagerException("Image build completed but no ID found."));
              }
            }
          });
    } catch (Exception e) {
      logger.error("Failed to initiate image build: {}", e.getMessage(), e);
      future.completeExceptionally(
          new ApiResponseException(
              "Failed to initiate image build: " + e.getMessage(), extractStatusCode(e), e));
    }
    return future;
  }

  @Override
  public CompletableFuture<ImageInfo> inspectImage(String imageIdOrName) {
    ensureInitialized();
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            InspectImageResponse response = dockerClient.inspectImageCmd(imageIdOrName).exec();
            return mapInspectResponseToImageInfo(response);
          } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            logger.warn("Image not found for inspection: {}", imageIdOrName);
            throw new ToolNotFoundException("Image not found: " + imageIdOrName, e);
          } catch (Exception e) {
            logger.error("Failed to inspect Docker image {}: {}", imageIdOrName, e.getMessage(), e);
            throw new ApiResponseException(
                "Failed to inspect Docker image " + imageIdOrName + ": " + e.getMessage(),
                extractStatusCode(e),
                e);
          }
        });
  }

  private ContainerInfo mapDockerContainerToContainerInfo(Container container) {
    OffsetDateTime createdDateTime =
        OffsetDateTime.ofInstant(Instant.ofEpochSecond(container.getCreated()), ZoneOffset.UTC);
    return ContainerInfo.builder()
        .id(container.getId())
        .names(
            container.getNames() != null ? List.of(container.getNames()) : Collections.emptyList())
        .image(container.getImage())
        .imageID(container.getImageId())
        .command(container.getCommand())
        .created(createdDateTime)
        .status(container.getStatus())
        .state(container.getState())
        .labels(container.getLabels() != null ? container.getLabels() : Collections.emptyMap())
        .build();
  }

  private ContainerInfo mapInspectResponseToContainerInfo(InspectContainerResponse response) {
    OffsetDateTime createdDateTime = null;
    if (response.getCreated() != null) {
      try {
        createdDateTime = OffsetDateTime.parse(response.getCreated());
      } catch (Exception e) {
        logger.warn("Could not parse container creation timestamp: {}", response.getCreated(), e);
      }
    }

    return ContainerInfo.builder()
        .id(response.getId())
        .names(
            response.getName() != null
                ? List.of(
                    response.getName().startsWith("/")
                        ? response.getName().substring(1)
                        : response.getName())
                : Collections.emptyList())
        .image(response.getConfig() != null ? response.getConfig().getImage() : null)
        .imageID(response.getImageId())
        .command(
            response.getPath()
                + (response.getArgs() != null ? " " + String.join(" ", response.getArgs()) : ""))
        .created(createdDateTime)
        .status(response.getState() != null ? response.getState().getStatus() : null)
        .state(response.getState() != null ? response.getState().getStatus() : null)
        .labels(
            response.getConfig() != null && response.getConfig().getLabels() != null
                ? response.getConfig().getLabels()
                : Collections.emptyMap())
        .build();
  }

  private ImageInfo mapDockerImageToImageInfo(Image image) {
    OffsetDateTime createdDateTime =
        OffsetDateTime.ofInstant(Instant.ofEpochSecond(image.getCreated()), ZoneOffset.UTC);
    return ImageInfo.builder()
        .id(image.getId())
        .repoTags(
            image.getRepoTags() != null ? List.of(image.getRepoTags()) : Collections.emptyList())
        .repoDigests(
            image.getRepoDigests() != null
                ? List.of(image.getRepoDigests())
                : Collections.emptyList())
        .parentId(image.getParentId())
        .size(image.getSize())
        .virtualSize(image.getVirtualSize())
        .created(createdDateTime)
        .labels(image.getLabels() != null ? image.getLabels() : Collections.emptyMap())
        .build();
  }

  private ImageInfo mapInspectResponseToImageInfo(InspectImageResponse response) {
    OffsetDateTime createdDateTime = null;
    if (response.getCreated() != null) {
      try {
        createdDateTime = OffsetDateTime.parse(response.getCreated());
      } catch (Exception e) {
        logger.warn("Could not parse image creation timestamp: {}", response.getCreated(), e);
      }
    }
    return ImageInfo.builder()
        .id(response.getId())
        .repoTags(response.getRepoTags() != null ? response.getRepoTags() : Collections.emptyList())
        .repoDigests(
            response.getRepoDigests() != null ? response.getRepoDigests() : Collections.emptyList())
        .parentId(response.getParent())
        .size(response.getSize() != null ? response.getSize() : 0L)
        .virtualSize(response.getVirtualSize() != null ? response.getVirtualSize() : 0L)
        .created(createdDateTime)
        .labels(
            response.getConfig() != null && response.getConfig().getLabels() != null
                ? response.getConfig().getLabels()
                : Collections.emptyMap())
        .build();
  }

  private int extractStatusCode(Throwable throwable) {
    if (throwable instanceof com.github.dockerjava.api.exception.DockerClientException) {
      if (throwable instanceof com.github.dockerjava.api.exception.NotFoundException) return 404;
      if (throwable instanceof com.github.dockerjava.api.exception.ConflictException) return 409;
      if (throwable instanceof com.github.dockerjava.api.exception.InternalServerErrorException)
        return 500;
      String message = throwable.getMessage();
      if (message != null) {
        String marker = "status code: ";
        int index = message.toLowerCase().indexOf(marker);
        if (index != -1) {
          String codeStr = message.substring(index + marker.length()).split("\\s")[0];
          try {
            return Integer.parseInt(codeStr);
          } catch (NumberFormatException e) {
          }
        }
      }
    }
    return 0;
  }

  @Override
  public void close() throws IOException {
    if (this.dockerHttpClient != null) {
      try {
        this.dockerHttpClient.close();
        logger.info("DockerHttpClient closed.");
      } catch (IOException e) {
        logger.warn("Error closing DockerHttpClient.", e);
        throw e;
      }
    }
    this.initialized = false;
  }
}
