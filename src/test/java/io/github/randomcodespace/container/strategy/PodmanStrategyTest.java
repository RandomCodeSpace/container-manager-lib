package io.github.randomcodespace.container.strategy;

import static org.junit.jupiter.api.Assertions.*;

import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.ImageBuildConfig;
import io.github.randomcodespace.container.dto.ImageInfo;
import io.github.randomcodespace.container.dto.LogOptions;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.utils.ProcessExecutor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PodmanStrategyTest {

  private PodmanStrategy podmanStrategy;
  private DetectedToolInfo toolInfo;
  private Path executablePath;

  @BeforeEach
  void setUp() throws Exception {
    // Create a platform-independent path for Podman
    executablePath = Paths.get("/usr/bin/podman");
    toolInfo = new DetectedToolInfo(ToolType.PODMAN, "3.4.4", executablePath, true);

    // Create a PodmanStrategy
    podmanStrategy = new PodmanStrategy();

    // Use reflection to set the private fields
    setPrivateField(podmanStrategy, "executablePath", executablePath);
    setPrivateField(podmanStrategy, "toolInfo", toolInfo);
    setPrivateField(podmanStrategy, "initialized", true);
  }

  // Helper method to set private fields using reflection
  private void setPrivateField(Object object, String fieldName, Object value) throws Exception {
    Field field = null;
    Class<?> clazz = object.getClass();

    // Try to find the field in the class hierarchy
    while (clazz != null && field == null) {
      try {
        field = clazz.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        // Field not found in current class, try the superclass
        clazz = clazz.getSuperclass();
      }
    }

    if (field == null) {
      throw new NoSuchFieldException(
          "Field '"
              + fieldName
              + "' not found in class hierarchy of "
              + object.getClass().getName());
    }

    field.setAccessible(true);
    field.set(object, value);
  }

  // Helper method to create a command matcher
  private ArgumentMatcher<List<String>> commandContains(String... substrings) {
    return command -> {
      if (command == null) return false;
      for (String substring : substrings) {
        boolean found = false;
        for (String part : command) {
          if (part.contains(substring)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    };
  }

  @Test
  void testInitialize() {
    // Setup
    PodmanStrategy freshStrategy = new PodmanStrategy();

    // Create a platform-independent path for Podman
    Path podmanPath = Paths.get("/usr/bin/podman");
    DetectedToolInfo validToolInfo =
        new DetectedToolInfo(ToolType.PODMAN, "3.4.4", podmanPath, true);

    // Execute
    freshStrategy.initialize(validToolInfo);

    // Verify
    assertTrue(freshStrategy.isInitialized());
    assertEquals(ToolType.PODMAN, freshStrategy.getToolType());
  }

  @Test
  void testInitializeWithInvalidToolInfo() {
    // Setup
    PodmanStrategy freshStrategy = new PodmanStrategy();

    // Create a platform-independent path for Docker
    Path dockerPath = Paths.get("/usr/bin/docker");
    DetectedToolInfo invalidToolInfo =
        new DetectedToolInfo(ToolType.DOCKER, "20.10.17", dockerPath, true);

    // Execute & Verify
    assertThrows(ContainerManagerException.class, () -> freshStrategy.initialize(invalidToolInfo));
    assertFalse(freshStrategy.isInitialized());
  }

  @Test
  void testCreateContainer() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result =
        new ProcessExecutor.ExecutionResult(0, "container123", "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Create container config
    ContainerConfig config =
        ContainerConfig.builder()
            .imageName("nginx")
            .containerName("test-container")
            .command(Arrays.asList("nginx", "-g", "daemon off;"))
            .build();

    // Execute
    CompletableFuture<String> future = testStrategy.createContainer(config);
    String containerId = future.get();

    // Verify
    assertEquals("container123", containerId);
  }

  @Test
  void testStartContainer() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<Void> future = testStrategy.startContainer("container123");
    future.get(); // Wait for completion

    // No explicit verification needed as we're using a mock result
  }

  @Test
  void testStopContainer() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<Void> future = testStrategy.stopContainer("container123", 10);
    future.get(); // Wait for completion

    // No explicit verification needed as we're using a mock result
  }

  @Test
  void testRemoveContainer() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<Void> future = testStrategy.removeContainer("container123", true, true);
    future.get(); // Wait for completion

    // No explicit verification needed as we're using a mock result
  }

  @Test
  void testListContainers() throws Exception {
    // Setup
    String jsonOutput =
        "[{\"ID\":\"container123\",\"Names\":[\"test-container\"],\"Image\":\"nginx\",\"ImageID\":\"image123\",\"Command\":\"nginx -g daemon off;\",\"CreatedAt\":1620000000,\"State\":\"running\",\"Status\":\"Up 2 hours\",\"Labels\":{\"label1\":\"value1\"}}]";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, jsonOutput, "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<List<ContainerInfo>> future = testStrategy.listContainers(true);
    List<ContainerInfo> containers = future.get();

    // Verify
    assertEquals(1, containers.size());
    ContainerInfo containerInfo = containers.get(0);
    assertEquals("container123", containerInfo.getId());
    assertEquals("nginx", containerInfo.getImage());
    assertEquals("image123", containerInfo.getImageID());
  }

  @Test
  void testGetContainerLogs() throws Exception {
    // Setup
    String logOutput = "Log line 1\nLog line 2\nLog line 3";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, logOutput, "");
    TestablePodmanStrategy testStrategy =
        new TestablePodmanStrategy(0); // Use exit code constructor

    // Set the mock result for streaming output
    setPrivateField(testStrategy, "mockResult", result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Create log options
    LogOptions options =
        LogOptions.builder().stdout(true).stderr(true).timestamps(true).tail(10).build();

    // Create a consumer to collect log lines
    List<String> logLines = new ArrayList<>();
    Consumer<String> logConsumer = logLines::add;

    // Execute
    CompletableFuture<Void> future =
        testStrategy.getContainerLogs("container123", options, logConsumer);
    future.get(); // Wait for completion

    // Verify
    assertEquals(3, logLines.size());
    assertEquals("Log line 1", logLines.get(0));
    assertEquals("Log line 2", logLines.get(1));
    assertEquals("Log line 3", logLines.get(2));
  }

  // --- Image Manager Tests ---

  @Test
  void testPullImage() throws Exception {
    // Setup
    String pullOutput = "Pulling image nginx:latest...\nDownloading layers...\nPull complete";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, pullOutput, "");
    TestablePodmanStrategy testStrategy =
        new TestablePodmanStrategy(0); // Use exit code constructor

    // Set the mock result for streaming output
    setPrivateField(testStrategy, "mockResult", result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Create a consumer to collect pull output
    List<String> outputLines = new ArrayList<>();
    Consumer<String> outputConsumer = outputLines::add;

    // Execute
    CompletableFuture<Void> future = testStrategy.pullImage("nginx", "latest", outputConsumer);
    future.get(); // Wait for completion

    // Verify
    assertEquals(3, outputLines.size());
    assertEquals("Pulling image nginx:latest...", outputLines.get(0));
    assertEquals("Pull complete", outputLines.get(2));
  }

  @Test
  void testListImages() throws Exception {
    // Setup
    String jsonOutput =
        "[{\"Id\":\"image123\",\"RepositoryTags\":[\"nginx:latest\"],\"Digests\":[\"sha256:abc123\"],\"ParentId\":\"parent123\",\"Size\":10485760,\"Created\":1620000000,\"Labels\":{\"label1\":\"value1\"}}]";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, jsonOutput, "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<List<ImageInfo>> future = testStrategy.listImages();
    List<ImageInfo> images = future.get();

    // Verify
    assertEquals(1, images.size());
    ImageInfo imageInfo = images.get(0);
    assertEquals("image123", imageInfo.getId());
    assertTrue(imageInfo.getRepoTags().contains("nginx:latest"));
  }

  @Test
  void testRemoveImage() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<Void> future = testStrategy.removeImage("image123", true, false);
    future.get(); // Wait for completion

    // No explicit verification needed as we're using a mock result
  }

  @Test
  void testBuildImage() throws Exception {
    // Setup
    String buildOutput =
        "STEP 1: FROM nginx:latest\nSTEP 2: COPY index.html /usr/share/nginx/html/\nSuccessfully built image123";
    ProcessExecutor.ExecutionResult result =
        new ProcessExecutor.ExecutionResult(0, buildOutput, "");
    TestablePodmanStrategy testStrategy =
        new TestablePodmanStrategy(0); // Use exit code constructor

    // Set the mock result for streaming output
    setPrivateField(testStrategy, "mockResult", result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Create a consumer to collect build output
    List<String> outputLines = new ArrayList<>();
    Consumer<String> outputConsumer = outputLines::add;

    // Create image build config
    ImageBuildConfig config =
        ImageBuildConfig.builder()
            .contextDirectory(Paths.get("/tmp/build"))
            .dockerfilePath("Dockerfile")
            .tags(new HashSet<>(Arrays.asList("myapp:latest")))
            .noCache(false)
            .pullParent(true)
            .removeIntermediateContainers(true)
            .build();

    // Execute
    CompletableFuture<String> future = testStrategy.buildImage(config, outputConsumer);
    String imageId = future.get();

    // Verify
    assertNotNull(imageId);
    assertEquals(3, outputLines.size());
    assertTrue(outputLines.get(0).contains("FROM nginx:latest"));
    assertTrue(outputLines.get(2).contains("Successfully built"));
  }

  @Test
  void testInspectImage() throws Exception {
    // Setup
    String jsonOutput =
        "[{\"Id\":\"image123\",\"RepoTags\":[\"nginx:latest\"],\"RepoDigests\":[\"nginx@sha256:abc123\"],\"Created\":\"2023-05-20T12:34:56.789Z\",\"Size\":10485760,\"VirtualSize\":20971520,\"Labels\":{\"label1\":\"value1\"}}]";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, jsonOutput, "");
    TestablePodmanStrategy testStrategy = new TestablePodmanStrategy(result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Execute
    CompletableFuture<ImageInfo> future = testStrategy.inspectImage("nginx:latest");
    ImageInfo imageInfo = future.get();

    // Verify
    assertEquals("image123", imageInfo.getId());
    // Note: The actual verification might be limited because the mapRawInspectToImageInfo method
    // in PodmanStrategy is a simplified implementation for MVP
  }
}
