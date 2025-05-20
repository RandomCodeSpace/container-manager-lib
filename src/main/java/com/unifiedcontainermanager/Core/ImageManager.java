package com.unifiedcontainermanager.Core;

import com.unifiedcontainermanager.DTOs.ImageBuildConfig;
import com.unifiedcontainermanager.DTOs.ImageInfo;
import com.unifiedcontainermanager.Exceptions.ContainerManagerException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for managing container images. Defines common operations applicable across different
 * containerization tools.
 */
public interface ImageManager {

  /**
   * Pulls an image from a registry.
   *
   * @param imageName The name of the image to pull (e.g., "ubuntu" or
   *     "[registry.hub.docker.com/library/ubuntu](https://registry.hub.docker.com/library/ubuntu)").
   * @param tag The tag of the image (e.g., "latest", "20.04"). If null, often defaults to "latest".
   * @param progressConsumer Optional consumer for progress updates during the pull.
   * @return A CompletableFuture that completes when the image pull is finished.
   * @throws ContainerManagerException if pulling fails.
   */
  CompletableFuture<Void> pullImage(String imageName, String tag, Consumer<String> progressConsumer)
      throws ContainerManagerException;

  /**
   * Lists images available locally.
   *
   * @return A CompletableFuture holding a list of ImageInfo objects.
   * @throws ContainerManagerException if listing fails.
   */
  CompletableFuture<List<ImageInfo>> listImages() throws ContainerManagerException;

  /**
   * Removes an image from local storage.
   *
   * @param imageIdOrName The ID or name/tag of the image to remove.
   * @param force If true, forcefully remove the image (e.g., even if containers are using it).
   * @param pruneChildren If true, remove dangling parent images.
   * @return A CompletableFuture that completes when the remove command is issued.
   * @throws ContainerManagerException if removal fails.
   */
  CompletableFuture<Void> removeImage(String imageIdOrName, boolean force, boolean pruneChildren)
      throws ContainerManagerException;

  /**
   * Builds an image from a Dockerfile or Containerfile.
   *
   * @param config Configuration for the image build process.
   * @param outputConsumer Optional consumer for build output lines.
   * @return A CompletableFuture holding the ID of the built image.
   * @throws ContainerManagerException if building fails.
   */
  CompletableFuture<String> buildImage(ImageBuildConfig config, Consumer<String> outputConsumer)
      throws ContainerManagerException;

  /**
   * Inspects an image and returns detailed information.
   *
   * @param imageIdOrName The ID or name/tag of the image to inspect.
   * @return A CompletableFuture holding the ImageInfo object.
   * @throws ContainerManagerException if inspection fails or image not found.
   */
  CompletableFuture<ImageInfo> inspectImage(String imageIdOrName) throws ContainerManagerException;

  // Potential future additions:
  // CompletableFuture<Void> pushImage(String imageName, String tag, AuthConfig authConfig);
  // CompletableFuture<Void> tagImage(String sourceImageIdOrName, String targetImageName, String
  // targetTag);
}
