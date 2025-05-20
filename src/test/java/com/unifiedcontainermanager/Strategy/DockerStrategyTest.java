package com.unifiedcontainermanager.Strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.transport.DockerHttpClient;
import com.unifiedcontainermanager.DTOs.ContainerConfig;
import com.unifiedcontainermanager.DTOs.ContainerInfo;
import com.unifiedcontainermanager.DTOs.ImageBuildConfig;
import com.unifiedcontainermanager.DTOs.ImageInfo;
import com.unifiedcontainermanager.Detection.DetectedToolInfo;
import com.unifiedcontainermanager.Enums.ToolType;
import com.unifiedcontainermanager.Exceptions.ContainerManagerException;
import com.unifiedcontainermanager.TestUtils.PlatformUtils;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DockerStrategyTest {

  @Mock private DockerClient dockerClient;

  @Mock private PingCmd pingCmd;

  @Mock private CreateContainerCmd createContainerCmd;

  @Mock private CreateContainerResponse createContainerResponse;

  @Mock private StartContainerCmd startContainerCmd;

  @Mock private StopContainerCmd stopContainerCmd;

  @Mock private RemoveContainerCmd removeContainerCmd;

  @Mock private ListContainersCmd listContainersCmd;

  @Mock private LogContainerCmd logContainerCmd;

  @Mock private InspectContainerCmd inspectContainerCmd;

  @Mock private InspectContainerResponse inspectContainerResponse;

  @Mock private PullImageCmd pullImageCmd;

  @Mock private ListImagesCmd listImagesCmd;

  @Mock private RemoveImageCmd removeImageCmd;

  @Mock private BuildImageCmd buildImageCmd;

  @Mock private InspectImageCmd inspectImageCmd;

  @Mock private InspectImageResponse inspectImageResponse;

  private DockerStrategy dockerStrategy;
  private DetectedToolInfo toolInfo;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws Exception {
    // Create a platform-independent path for Docker
    Path mockDockerPath = Paths.get("/usr/bin/docker");

    // Create a DetectedToolInfo for Docker with the mock path
    toolInfo = new DetectedToolInfo(ToolType.DOCKER, "20.10.17", mockDockerPath, true);

    // Create a DockerStrategy
    dockerStrategy = new DockerStrategy();

    // Use reflection to set the private fields
    setPrivateField(dockerStrategy, "dockerClient", dockerClient);
    setPrivateField(dockerStrategy, "toolInfo", toolInfo);
    setPrivateField(dockerStrategy, "initialized", true);
  }

  // Helper method to set private fields using reflection
  private void setPrivateField(Object object, String fieldName, Object value) throws Exception {
    Field field = object.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(object, value);
  }

  @Test
  void testInitialize() throws Exception {
    // Setup
    when(dockerClient.pingCmd()).thenReturn(pingCmd);
    doNothing().when(pingCmd).exec();

    DockerHttpClient mockHttpClient = mock(com.github.dockerjava.transport.DockerHttpClient.class);
    TestableDockerStrategy testStrategy = new TestableDockerStrategy(dockerClient, mockHttpClient);

    // Execute
    testStrategy.initialize(toolInfo);

    // Verify
    assertTrue(testStrategy.isInitialized());
    assertEquals(ToolType.DOCKER, testStrategy.getToolType());
    verify(dockerClient).pingCmd();
    verify(pingCmd).exec();
  }

  @Test
  void testInitializeWithInvalidToolInfo() {
    // Setup
    DockerStrategy freshStrategy = new DockerStrategy();
    DetectedToolInfo invalidToolInfo =
        new DetectedToolInfo(ToolType.PODMAN, "3.4.4", PlatformUtils.getToolPath("podman"), true);

    // Execute & Verify
    assertThrows(ContainerManagerException.class, () -> freshStrategy.initialize(invalidToolInfo));
    assertFalse(freshStrategy.isInitialized());
  }

  @Test
  void testInitializeWithConnectionFailure() throws Exception {
    // Setup
    when(dockerClient.pingCmd()).thenReturn(pingCmd);
    doThrow(new RuntimeException("Connection failed")).when(pingCmd).exec();

    DockerHttpClient mockHttpClient = mock(DockerHttpClient.class);
    TestableDockerStrategy testStrategy = new TestableDockerStrategy(dockerClient, mockHttpClient);

    // Execute & Verify
    assertThrows(ContainerManagerException.class, () -> testStrategy.initialize(toolInfo));
    assertFalse(testStrategy.isInitialized());

    // Verify the mocks were used
    verify(dockerClient).pingCmd();
    verify(pingCmd).exec();
  }

  @Test
  void testCreateContainer() throws Exception {
    // Setup
    when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
    when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
    when(createContainerCmd.withCmd(anyList())).thenReturn(createContainerCmd);
    when(createContainerCmd.withHostConfig(any(HostConfig.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.exec()).thenReturn(createContainerResponse);
    when(createContainerResponse.getId()).thenReturn("container123");

    // Create container config
    ContainerConfig config =
        ContainerConfig.builder()
            .imageName("nginx")
            .containerName("test-container")
            .command(Arrays.asList("nginx", "-g", "daemon off;"))
            .build();

    // Execute
    CompletableFuture<String> future = dockerStrategy.createContainer(config);
    String containerId = future.get();

    // Verify
    assertEquals("container123", containerId);
    verify(dockerClient).createContainerCmd("nginx");
    verify(createContainerCmd).withName("test-container");
    verify(createContainerCmd).withCmd(Arrays.asList("nginx", "-g", "daemon off;"));
    verify(createContainerCmd).withHostConfig(any(HostConfig.class));
    verify(createContainerCmd).exec();
  }

  @Test
  void testStartContainer() throws Exception {
    // Setup
    when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
    doNothing().when(startContainerCmd).exec();

    // Execute
    CompletableFuture<Void> future = dockerStrategy.startContainer("container123");
    future.get(); // Wait for completion

    // Verify
    verify(dockerClient).startContainerCmd("container123");
    verify(startContainerCmd).exec();
  }

  @Test
  void testStopContainer() throws Exception {
    // Setup
    when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopContainerCmd);
    when(stopContainerCmd.withTimeout(anyInt())).thenReturn(stopContainerCmd);
    doNothing().when(stopContainerCmd).exec();

    // Execute
    CompletableFuture<Void> future = dockerStrategy.stopContainer("container123", 10);
    future.get(); // Wait for completion

    // Verify
    verify(dockerClient).stopContainerCmd("container123");
    verify(stopContainerCmd).withTimeout(10);
    verify(stopContainerCmd).exec();
  }

  @Test
  void testRemoveContainer() throws Exception {
    // Setup
    when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeContainerCmd);
    when(removeContainerCmd.withForce(anyBoolean())).thenReturn(removeContainerCmd);
    when(removeContainerCmd.withRemoveVolumes(anyBoolean())).thenReturn(removeContainerCmd);
    doNothing().when(removeContainerCmd).exec();

    // Execute
    CompletableFuture<Void> future = dockerStrategy.removeContainer("container123", true, true);
    future.get(); // Wait for completion

    // Verify
    verify(dockerClient).removeContainerCmd("container123");
    verify(removeContainerCmd).withForce(true);
    verify(removeContainerCmd).withRemoveVolumes(true);
    verify(removeContainerCmd).exec();
  }

  @Test
  void testListContainers() throws Exception {
    // Setup
    Container container1 = mock(Container.class);
    when(container1.getId()).thenReturn("container123");
    when(container1.getNames()).thenReturn(new String[] {"test-container"});
    when(container1.getImage()).thenReturn("nginx");
    when(container1.getImageId()).thenReturn("image123");
    when(container1.getCommand()).thenReturn("nginx -g daemon off;");
    when(container1.getCreated()).thenReturn(1620000000L);
    when(container1.getStatus()).thenReturn("running");
    when(container1.getState()).thenReturn("running");
    when(container1.getLabels()).thenReturn(Collections.singletonMap("label1", "value1"));

    when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
    when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
    when(listContainersCmd.exec()).thenReturn(Arrays.asList(container1));

    // Execute
    CompletableFuture<List<ContainerInfo>> future = dockerStrategy.listContainers(true);
    List<ContainerInfo> containers = future.get();

    // Verify
    assertEquals(1, containers.size());
    ContainerInfo containerInfo = containers.get(0);
    assertEquals("container123", containerInfo.getId());
    assertEquals("nginx", containerInfo.getImage());
    assertEquals("image123", containerInfo.getImageID());
    assertEquals("nginx -g daemon off;", containerInfo.getCommand());
    assertEquals("running", containerInfo.getStatus());
    assertEquals("running", containerInfo.getState());
    assertEquals(Collections.singletonMap("label1", "value1"), containerInfo.getLabels());

    verify(dockerClient).listContainersCmd();
    verify(listContainersCmd).withShowAll(true);
    verify(listContainersCmd).exec();
  }

  @Test
  void testInspectContainer() throws Exception {
    // Setup
    when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
    when(inspectContainerCmd.exec()).thenReturn(inspectContainerResponse);
    when(inspectContainerResponse.getId()).thenReturn("container123");
    when(inspectContainerResponse.getName()).thenReturn("/test-container");
    when(inspectContainerResponse.getImageId()).thenReturn("image123");
    when(inspectContainerResponse.getPath()).thenReturn("nginx");
    when(inspectContainerResponse.getArgs()).thenReturn(new String[] {"-g", "daemon off;"});
    when(inspectContainerResponse.getCreated()).thenReturn("2023-05-20T12:34:56.789Z");

    // Mock ContainerState
    InspectContainerResponse.ContainerState state =
        mock(InspectContainerResponse.ContainerState.class);
    when(state.getStatus()).thenReturn("running");
    when(inspectContainerResponse.getState()).thenReturn(state);

    // Mock ContainerConfig
    // Note: This is a different class than our DTO ContainerConfig
    com.github.dockerjava.api.model.ContainerConfig dockerConfig =
        mock(com.github.dockerjava.api.model.ContainerConfig.class);
    when(dockerConfig.getImage()).thenReturn("nginx");
    when(dockerConfig.getLabels()).thenReturn(Collections.singletonMap("label1", "value1"));
    when(inspectContainerResponse.getConfig()).thenReturn(dockerConfig);

    // Execute
    CompletableFuture<ContainerInfo> future = dockerStrategy.inspectContainer("container123");
    ContainerInfo containerInfo = future.get();

    // Verify
    assertEquals("container123", containerInfo.getId());
    assertEquals(List.of("test-container"), containerInfo.getNames());
    assertEquals("nginx", containerInfo.getImage());
    assertEquals("image123", containerInfo.getImageID());
    assertEquals("nginx -g daemon off;", containerInfo.getCommand());
    assertEquals("running", containerInfo.getStatus());
    assertEquals("running", containerInfo.getState());
    assertEquals(Collections.singletonMap("label1", "value1"), containerInfo.getLabels());

    verify(dockerClient).inspectContainerCmd("container123");
    verify(inspectContainerCmd).exec();
  }

  @Test
  void testInspectContainerNotFound() throws Exception {
    // Setup
    when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
    when(inspectContainerCmd.exec()).thenThrow(new NotFoundException("Container not found"));

    // Execute & Verify
    CompletableFuture<ContainerInfo> future = dockerStrategy.inspectContainer("nonexistent");

    // Since the exception is wrapped in a CompletableFuture, we need to check for
    // ExecutionException
    // with ToolNotFoundException as the cause
    ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
    assertTrue(
        exception.getCause()
            instanceof com.unifiedcontainermanager.Exceptions.ToolNotFoundException);

    verify(dockerClient).inspectContainerCmd("nonexistent");
    verify(inspectContainerCmd).exec();
  }

  // --- Image Manager Tests ---

  @Test
  void testPullImage() throws Exception {
    // Setup
    when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);

    // Create a consumer to collect progress updates
    List<String> progressUpdates = new ArrayList<>();
    Consumer<String> progressConsumer = progressUpdates::add;

    // Mock the ResultCallback for PullResponseItem
    ResultCallback.Adapter<PullResponseItem> mockCallback = mock(ResultCallback.Adapter.class);
    doAnswer(
            invocation -> {
              // Simulate callback completion
              progressConsumer.accept("Downloading: [==>] 10%");
              progressConsumer.accept("Downloading: [=====>] 50%");
              progressConsumer.accept("Downloading: [==========>] 100%");
              progressConsumer.accept("Download complete");
              return mockCallback;
            })
        .when(pullImageCmd)
        .exec(any());

    // Execute
    CompletableFuture<Void> future = dockerStrategy.pullImage("nginx", "latest", progressConsumer);

    // Verify
    verify(dockerClient).pullImageCmd("nginx:latest");
    verify(pullImageCmd).exec(any());
    assertEquals(4, progressUpdates.size());
    assertTrue(progressUpdates.get(0).contains("10%"));
    assertTrue(progressUpdates.get(2).contains("100%"));
  }

  @Test
  void testListImages() throws Exception {
    // Setup
    Image image1 = mock(Image.class);
    when(image1.getId()).thenReturn("image123");
    when(image1.getRepoTags()).thenReturn(new String[] {"nginx:latest"});
    when(image1.getRepoDigests()).thenReturn(new String[] {"nginx@sha256:abc123"});
    when(image1.getParentId()).thenReturn("parent123");
    when(image1.getSize()).thenReturn(10485760L);
    when(image1.getVirtualSize()).thenReturn(20971520L);
    when(image1.getCreated()).thenReturn(1620000000L);
    when(image1.getLabels()).thenReturn(Collections.singletonMap("label1", "value1"));

    when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
    when(listImagesCmd.withShowAll(anyBoolean())).thenReturn(listImagesCmd);
    when(listImagesCmd.exec()).thenReturn(Arrays.asList(image1));

    // Execute
    CompletableFuture<List<ImageInfo>> future = dockerStrategy.listImages();
    List<ImageInfo> images = future.get();

    // Verify
    assertEquals(1, images.size());
    ImageInfo imageInfo = images.get(0);
    assertEquals("image123", imageInfo.getId());
    assertEquals(List.of("nginx:latest"), imageInfo.getRepoTags());
    assertEquals(List.of("nginx@sha256:abc123"), imageInfo.getRepoDigests());
    assertEquals("parent123", imageInfo.getParentId());
    assertEquals(10485760L, imageInfo.getSize());
    assertEquals(20971520L, imageInfo.getVirtualSize());

    verify(dockerClient).listImagesCmd();
    verify(listImagesCmd).withShowAll(true);
    verify(listImagesCmd).exec();
  }

  @Test
  void testRemoveImage() throws Exception {
    // Setup
    when(dockerClient.removeImageCmd(anyString())).thenReturn(removeImageCmd);
    when(removeImageCmd.withForce(anyBoolean())).thenReturn(removeImageCmd);
    when(removeImageCmd.withNoPrune(anyBoolean())).thenReturn(removeImageCmd);
    doNothing().when(removeImageCmd).exec();

    // Execute
    CompletableFuture<Void> future = dockerStrategy.removeImage("image123", true, true);
    future.get(); // Wait for completion

    // Verify
    verify(dockerClient).removeImageCmd("image123");
    verify(removeImageCmd).withForce(true);
    verify(removeImageCmd).withNoPrune(false); // Note: pruneChildren is inverted to noPrune
    verify(removeImageCmd).exec();
  }

  @Test
  void testBuildImage() throws Exception {
    // Setup
    when(dockerClient.buildImageCmd()).thenReturn(buildImageCmd);
    when(buildImageCmd.withDockerfile(any())).thenReturn(buildImageCmd);
    when(buildImageCmd.withDockerfilePath(anyString())).thenReturn(buildImageCmd);
    when(buildImageCmd.withBaseDirectory(any())).thenReturn(buildImageCmd);
    when(buildImageCmd.withTags(anySet())).thenReturn(buildImageCmd);
    when(buildImageCmd.withNoCache(anyBoolean())).thenReturn(buildImageCmd);
    when(buildImageCmd.withPull(anyBoolean())).thenReturn(buildImageCmd);
    when(buildImageCmd.withRemove(anyBoolean())).thenReturn(buildImageCmd);

    // Create a consumer to collect build output
    List<String> buildOutput = new ArrayList<>();
    Consumer<String> outputConsumer = buildOutput::add;

    // Mock the ResultCallback for BuildResponseItem
    ResultCallback.Adapter<BuildResponseItem> mockCallback = mock(ResultCallback.Adapter.class);
    doAnswer(
            invocation -> {
              // Simulate callback with build output and successful completion
              BuildResponseItem item1 = mock(BuildResponseItem.class);
              when(item1.getStream()).thenReturn("Step 1: FROM nginx:latest\n");

              BuildResponseItem item2 = mock(BuildResponseItem.class);
              when(item2.getStream())
                  .thenReturn("Step 2: COPY index.html /usr/share/nginx/html/\n");

              BuildResponseItem successItem = mock(BuildResponseItem.class);
              when(successItem.isBuildSuccessIndicated()).thenReturn(true);
              when(successItem.getImageId()).thenReturn("image123");
              when(successItem.getStream()).thenReturn("Successfully built image123\n");

              // Call onNext for each item
              ResultCallback.Adapter<BuildResponseItem> callback = invocation.getArgument(0);
              callback.onNext(item1);
              callback.onNext(item2);
              callback.onNext(successItem);
              callback.onComplete();

              return mockCallback;
            })
        .when(buildImageCmd)
        .exec(any());

    // Create image build config
    ImageBuildConfig config =
        ImageBuildConfig.builder()
            .contextDirectory(tempDir)
            .dockerfilePath("Dockerfile")
            .tags(new HashSet<>(Arrays.asList("myapp:latest")))
            .noCache(false)
            .pullParent(true)
            .removeIntermediateContainers(true)
            .build();

    // Execute
    CompletableFuture<String> future = dockerStrategy.buildImage(config, outputConsumer);
    String imageId = future.get();

    // Verify
    assertEquals("image123", imageId);
    assertEquals(3, buildOutput.size());
    assertTrue(buildOutput.get(0).contains("Step 1"));
    assertTrue(buildOutput.get(2).contains("Successfully built"));

    verify(dockerClient).buildImageCmd();
    verify(buildImageCmd).withDockerfile(any());
    verify(buildImageCmd).withDockerfilePath("Dockerfile");
    verify(buildImageCmd).withBaseDirectory(any());
    verify(buildImageCmd).withTags(anySet());
    verify(buildImageCmd).exec(any());
  }

  @Test
  void testInspectImage() throws Exception {
    // Setup
    when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
    when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
    when(inspectImageResponse.getId()).thenReturn("image123");
    when(inspectImageResponse.getRepoTags()).thenReturn(Arrays.asList("nginx:latest"));
    when(inspectImageResponse.getRepoDigests()).thenReturn(Arrays.asList("nginx@sha256:abc123"));
    when(inspectImageResponse.getParent()).thenReturn("parent123");
    when(inspectImageResponse.getSize()).thenReturn(10485760L);
    when(inspectImageResponse.getVirtualSize()).thenReturn(20971520L);
    when(inspectImageResponse.getCreated()).thenReturn("2023-05-20T12:34:56.789Z");

    // Mock ContainerConfig (different from our DTO)
    com.github.dockerjava.api.model.ContainerConfig dockerConfig =
        mock(com.github.dockerjava.api.model.ContainerConfig.class);
    when(dockerConfig.getLabels()).thenReturn(Collections.singletonMap("label1", "value1"));
    when(inspectImageResponse.getConfig()).thenReturn(dockerConfig);

    // Execute
    CompletableFuture<ImageInfo> future = dockerStrategy.inspectImage("nginx:latest");
    ImageInfo imageInfo = future.get();

    // Verify
    assertEquals("image123", imageInfo.getId());
    assertEquals(List.of("nginx:latest"), imageInfo.getRepoTags());
    assertEquals(List.of("nginx@sha256:abc123"), imageInfo.getRepoDigests());
    assertEquals("parent123", imageInfo.getParentId());
    assertEquals(10485760L, imageInfo.getSize());
    assertEquals(20971520L, imageInfo.getVirtualSize());
    assertEquals(Collections.singletonMap("label1", "value1"), imageInfo.getLabels());

    verify(dockerClient).inspectImageCmd("nginx:latest");
    verify(inspectImageCmd).exec();
  }

  @Test
  void testInspectImageNotFound() throws Exception {
    // Setup
    when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
    when(inspectImageCmd.exec()).thenThrow(new NotFoundException("Image not found"));

    // Execute & Verify
    CompletableFuture<ImageInfo> future = dockerStrategy.inspectImage("nonexistent");

    // Since the exception is wrapped in a CompletableFuture, we need to check for
    // ExecutionException
    // with ToolNotFoundException as the cause
    ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get());
    assertTrue(
        exception.getCause()
            instanceof com.unifiedcontainermanager.Exceptions.ToolNotFoundException);

    verify(dockerClient).inspectImageCmd("nonexistent");
    verify(inspectImageCmd).exec();
  }
}
