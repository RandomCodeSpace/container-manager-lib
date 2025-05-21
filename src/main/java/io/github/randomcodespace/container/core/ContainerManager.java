package io.github.randomcodespace.container.core;

import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.LogOptions;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ContainerManager {

  /**
   * Creates a new container based on the provided configuration.
   *
   * @param config The configuration for the new container.
   * @return A CompletableFuture holding the ID of the created container.
   * @throws ContainerManagerException if creation fails.
   */
  CompletableFuture<String> createContainer(ContainerConfig config)
      throws ContainerManagerException;

  /**
   * Starts an existing container.
   *
   * @param containerId The ID of the container to start.
   * @return A CompletableFuture that completes when the start command is issued.
   * @throws ContainerManagerException if starting fails.
   */
  CompletableFuture<Void> startContainer(String containerId) throws ContainerManagerException;

  /**
   * Stops a running container.
   *
   * @param containerId The ID of the container to stop.
   * @param timeoutSeconds Optional timeout in seconds to wait before killing the container. Null
   *     for default.
   * @return A CompletableFuture that completes when the stop command is issued.
   * @throws ContainerManagerException if stopping fails.
   */
  CompletableFuture<Void> stopContainer(String containerId, Integer timeoutSeconds)
      throws ContainerManagerException;

  /**
   * Removes a container. The container should typically be stopped first.
   *
   * @param containerId The ID of the container to remove.
   * @param force If true, forcefully remove the container (e.g., if it's running).
   * @param removeVolumes If true, remove anonymous volumes associated with the container.
   * @return A CompletableFuture that completes when the remove command is issued.
   * @throws ContainerManagerException if removal fails.
   */
  CompletableFuture<Void> removeContainer(String containerId, boolean force, boolean removeVolumes)
      throws ContainerManagerException;

  /**
   * Lists containers.
   *
   * @param listAll If true, lists all containers (including stopped ones). Otherwise, lists only
   *     running containers.
   * @return A CompletableFuture holding a list of ContainerInfo objects.
   * @throws ContainerManagerException if listing fails.
   */
  CompletableFuture<List<ContainerInfo>> listContainers(boolean listAll)
      throws ContainerManagerException;

  /**
   * Retrieves logs from a container.
   *
   * @param containerId The ID of the container.
   * @param options Options for fetching logs (e.g., follow, tail).
   * @param logConsumer A consumer that will receive log lines as they are emitted. For
   *     non-following logs, this might be called multiple times until all logs are delivered. For
   *     following logs, this will be called continuously.
   * @return A CompletableFuture that completes when the log streaming setup is done (for follow) or
   *     all logs are fetched.
   * @throws ContainerManagerException if fetching logs fails.
   */
  CompletableFuture<Void> getContainerLogs(
      String containerId, LogOptions options, Consumer<String> logConsumer)
      throws ContainerManagerException;

  /**
   * Inspects a container and returns detailed information.
   *
   * @param containerIdOrName The ID or name of the container to inspect.
   * @return A CompletableFuture holding the ContainerInfo object.
   * @throws ContainerManagerException if inspection fails or container not found.
   */
  CompletableFuture<ContainerInfo> inspectContainer(String containerIdOrName)
      throws ContainerManagerException;

  // Potential future additions:
  // CompletableFuture<Void> pauseContainer(String containerId);
  // CompletableFuture<Void> unpauseContainer(String containerId);
  // CompletableFuture<Void> restartContainer(String containerId, Integer timeoutSeconds);
  // CompletableFuture<String> execInContainer(String containerId, List<String> command, ExecOptions
  // options);
}
