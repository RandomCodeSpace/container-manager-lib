package io.github.randomcodespace.container.strategy;

import io.github.randomcodespace.container.core.ContainerManager;
import io.github.randomcodespace.container.core.ImageManager;
import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.detection.ToolDetector;
import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.ImageBuildConfig;
import io.github.randomcodespace.container.dto.ImageInfo;
import io.github.randomcodespace.container.dto.LogOptions;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.exceptions.ToolNotFoundException;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade for the Unified Container Management Library. Provides a single entry point for
 * interacting with containerization tools. Auto-detects available tools and delegates operations to
 * the appropriate strategy.
 */
public class ContainerService implements ContainerManager, ImageManager, Closeable {
  private static final Logger logger = LoggerFactory.getLogger(ContainerService.class);

  private final ToolDetector toolDetector;
  private ContainerToolStrategy activeStrategy;
  private boolean initialized = false;
  private DetectedToolInfo detectedToolInfo;

  /**
   * Constructs a ContainerService with a default tool detection order (Podman > Docker > Buildah).
   */
  public ContainerService() {
    this.toolDetector = new ToolDetector();
  }

  /**
   * Constructs a ContainerService with a specified preferred tool order for detection.
   *
   * @param preferredToolOrder The order in which to prefer tools.
   */
  public ContainerService(List<ToolType> preferredToolOrder) {
    this.toolDetector = new ToolDetector(preferredToolOrder);
  }

  /**
   * Initializes the ContainerService by detecting and selecting an active container tool strategy.
   * This method must be called before any other operations. This is a blocking call for
   * initialization simplicity in the constructor or an explicit init method. For fully async init,
   * return CompletableFuture<Void>.
   *
   * @throws ToolNotFoundException if no suitable container tool can be detected and initialized.
   */
  public void initialize() throws ToolNotFoundException {
    if (initialized) {
      logger.info(
          "ContainerService already initialized with strategy: {}", activeStrategy.getToolType());
      return;
    }
    try {
      logger.info("Initializing ContainerService, detecting tools...");
      Optional<DetectedToolInfo> toolInfoOpt =
          toolDetector.detectPreferredTool().get(); // Blocking for simplicity here

      if (toolInfoOpt.isEmpty()) {
        throw new ToolNotFoundException(
            "No compatible container tool (Docker, Podman, Buildah) found or could be verified on the system.");
      }

      this.detectedToolInfo = toolInfoOpt.get();
      logger.info("Selected tool: {}", detectedToolInfo);

      this.activeStrategy = createStrategy(detectedToolInfo);
      this.activeStrategy.initialize(detectedToolInfo); // Initialize the chosen strategy

      this.initialized = true;
      logger.info(
          "ContainerService initialized successfully with active strategy: {}",
          activeStrategy.getToolType());

    } catch (InterruptedException | ExecutionException e) {
      Thread.currentThread().interrupt(); // Restore interrupted status
      logger.error("Failed to initialize ContainerService during tool detection.", e);
      throw new ToolNotFoundException("Failed to detect container tools: " + e.getMessage(), e);
    } catch (ContainerManagerException e) {
      logger.error("Failed to initialize ContainerService strategy.", e);
      throw e; // Re-throw specific library exceptions
    }
  }

  /**
   * Asynchronously initializes the ContainerService.
   *
   * @return A CompletableFuture that completes when initialization is done, or exceptionally if it
   *     fails.
   */
  public CompletableFuture<Object> initializeAsync() {
    if (initialized) {
      logger.info(
          "ContainerService already initialized with strategy: {}", activeStrategy.getToolType());
      return CompletableFuture.completedFuture(null);
    }
    logger.info("Initializing ContainerService asynchronously, detecting tools...");
    return toolDetector
        .detectPreferredTool()
        .thenCompose(
            toolInfoOpt -> {
              if (toolInfoOpt.isEmpty()) {
                return CompletableFuture.failedFuture(
                    new ToolNotFoundException(
                        "No compatible container tool found or could be verified."));
              }
              this.detectedToolInfo = toolInfoOpt.get();
              logger.info("Selected tool: {}", detectedToolInfo);
              this.activeStrategy = createStrategy(detectedToolInfo);
              try {
                this.activeStrategy.initialize(
                    detectedToolInfo); // This might need to be async too if strategy init is heavy
                this.initialized = true;
                logger.info(
                    "ContainerService initialized successfully with active strategy: {}",
                    activeStrategy.getToolType());
                return CompletableFuture.completedFuture(null);
              } catch (ContainerManagerException e) {
                logger.error("Failed to initialize ContainerService strategy.", e);
                return CompletableFuture.failedFuture(e);
              }
            })
        .exceptionally(
            ex -> {
              logger.error("Failed to initialize ContainerService during tool detection.", ex);
              if (ex instanceof ToolNotFoundException
                  || ex.getCause() instanceof ToolNotFoundException)
                throw (ToolNotFoundException) ex.getCause();
              if (ex instanceof ContainerManagerException
                  || ex.getCause() instanceof ContainerManagerException)
                throw (ContainerManagerException) ex.getCause();
              throw new ToolNotFoundException(
                  "Failed to detect container tools: " + ex.getMessage(), ex);
            });
  }

  private ContainerToolStrategy createStrategy(DetectedToolInfo toolInfo) {
    return switch (toolInfo.getToolType()) {
      case DOCKER -> new DockerStrategy();
      case PODMAN -> new PodmanStrategy();
      case BUILDAH -> new BuildahStrategy();
      default ->
          throw new ContainerManagerException("Unsupported tool type: " + toolInfo.getToolType());
    };
  }

  private void ensureInitialized() {
    if (!initialized || activeStrategy == null) {
      throw new IllegalStateException(
          "ContainerService has not been initialized. Call initialize() or initializeAsync() first.");
    }
  }

  public Optional<ToolType> getActiveToolType() {
    return initialized ? Optional.of(activeStrategy.getToolType()) : Optional.empty();
  }

  public Optional<DetectedToolInfo> getActiveToolInfo() {
    return initialized ? Optional.of(detectedToolInfo) : Optional.empty();
  }

  // --- Delegating to Active Strategy ---

  // ContainerManager methods
  @Override
  public CompletableFuture<String> createContainer(ContainerConfig config) {
    ensureInitialized();
    return activeStrategy.createContainer(config);
  }

  @Override
  public CompletableFuture<Void> startContainer(String containerId) {
    ensureInitialized();
    return activeStrategy.startContainer(containerId);
  }

  @Override
  public CompletableFuture<Void> stopContainer(String containerId, Integer timeoutSeconds) {
    ensureInitialized();
    return activeStrategy.stopContainer(containerId, timeoutSeconds);
  }

  @Override
  public CompletableFuture<Void> removeContainer(
      String containerId, boolean force, boolean removeVolumes) {
    ensureInitialized();
    return activeStrategy.removeContainer(containerId, force, removeVolumes);
  }

  @Override
  public CompletableFuture<List<ContainerInfo>> listContainers(boolean listAll) {
    ensureInitialized();
    return activeStrategy.listContainers(listAll);
  }

  @Override
  public CompletableFuture<Void> getContainerLogs(
      String containerId, LogOptions options, Consumer<String> logConsumer) {
    ensureInitialized();
    return activeStrategy.getContainerLogs(containerId, options, logConsumer);
  }

  @Override
  public CompletableFuture<ContainerInfo> inspectContainer(String containerIdOrName) {
    ensureInitialized();
    return activeStrategy.inspectContainer(containerIdOrName);
  }

  // ImageManager methods
  @Override
  public CompletableFuture<Void> pullImage(
      String imageName, String tag, Consumer<String> progressConsumer) {
    ensureInitialized();
    return activeStrategy.pullImage(imageName, tag, progressConsumer);
  }

  @Override
  public CompletableFuture<List<ImageInfo>> listImages() {
    ensureInitialized();
    return activeStrategy.listImages();
  }

  @Override
  public CompletableFuture<Void> removeImage(
      String imageIdOrName, boolean force, boolean pruneChildren) {
    ensureInitialized();
    return activeStrategy.removeImage(imageIdOrName, force, pruneChildren);
  }

  @Override
  public CompletableFuture<String> buildImage(
      ImageBuildConfig config, Consumer<String> outputConsumer) {
    ensureInitialized();
    return activeStrategy.buildImage(config, outputConsumer);
  }

  @Override
  public CompletableFuture<ImageInfo> inspectImage(String imageIdOrName) {
    ensureInitialized();
    return activeStrategy.inspectImage(imageIdOrName);
  }

  /**
   * Closes any resources held by the active strategy (e.g., HTTP clients). It's good practice to
   * call this when the ContainerService is no longer needed.
   *
   * @throws IOException if closing resources fails.
   */
  @Override
  public void close() throws IOException {
    if (activeStrategy instanceof Closeable) {
      ((Closeable) activeStrategy).close();
    }
    // Add similar checks for other strategies if they need explicit closing.
    initialized = false;
    logger.info("ContainerService closed and resources released.");
  }
}
