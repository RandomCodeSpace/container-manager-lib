package io.github.randomcodespace.container.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.dto.ContainerConfig;
import io.github.randomcodespace.container.dto.ContainerInfo;
import io.github.randomcodespace.container.dto.ImageBuildConfig;
import io.github.randomcodespace.container.dto.ImageInfo;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.utils.ProcessExecutor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BuildahStrategyTest {

  private BuildahStrategy buildahStrategy;
  private DetectedToolInfo toolInfo;
  private Path executablePath;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws Exception {
    // Create a platform-independent path for Buildah
    executablePath = Paths.get("/usr/bin/buildah");
    toolInfo = new DetectedToolInfo(ToolType.BUILDAH, "1.26.4", executablePath, false);

    // Create a BuildahStrategy
    buildahStrategy = new BuildahStrategy();

    // Use reflection to set the private fields
    setPrivateField(buildahStrategy, "executablePath", executablePath);
    setPrivateField(buildahStrategy, "toolInfo", toolInfo);
    setPrivateField(buildahStrategy, "initialized", true);
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
    BuildahStrategy freshStrategy = new BuildahStrategy();

    // Create a platform-independent path for Buildah
    Path buildahPath = Paths.get("/usr/bin/buildah");
    DetectedToolInfo validToolInfo =
        new DetectedToolInfo(ToolType.BUILDAH, "1.26.4", buildahPath, false);

    // Execute
    freshStrategy.initialize(validToolInfo);

    // Verify
    assertTrue(freshStrategy.isInitialized());
    assertEquals(ToolType.BUILDAH, freshStrategy.getToolType());
  }

  @Test
  void testInitializeWithInvalidToolInfo() {
    // Setup
    BuildahStrategy freshStrategy = new BuildahStrategy();

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
    TestableBuildahStrategy testStrategy = new TestableBuildahStrategy(result);

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
  void testRemoveContainer() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");

    try (MockedStatic<ProcessExecutor> mockedProcessExecutor = mockStatic(ProcessExecutor.class)) {
      mockedProcessExecutor
          .when(
              () ->
                  ProcessExecutor.execute(
                      argThat(commandContains("buildah", "rm", "container123"))))
          .thenReturn(CompletableFuture.completedFuture(result));

      // Execute
      CompletableFuture<Void> future = buildahStrategy.removeContainer("container123", true, true);
      future.get(); // Wait for completion

      // Verify
      mockedProcessExecutor.verify(
          () -> ProcessExecutor.execute(argThat(commandContains("buildah", "rm", "container123"))));
    }
  }

  @Test
  void testListContainers() throws Exception {
    // Setup
    String jsonOutput =
        "[{\"containerid\":\"container123\",\"containername\":\"test-container\",\"imagename\":\"nginx\",\"imageid\":\"image123\",\"command\":\"nginx -g daemon off;\",\"createdAt\":\"2023-05-20T12:34:56.789Z\",\"status\":\"running\"}]";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, jsonOutput, "");
    TestableBuildahStrategy testStrategy = new TestableBuildahStrategy(result);

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
  void testBuildImage() throws Exception {
    // Setup
    String buildOutput =
        "STEP 1: FROM nginx:latest\nSTEP 2: COPY index.html /usr/share/nginx/html/\nSuccessfully built image123";
    ProcessExecutor.ExecutionResult result =
        new ProcessExecutor.ExecutionResult(0, buildOutput, "");
    TestableBuildahStrategy testStrategy =
        new TestableBuildahStrategy(0); // Use exit code constructor

    // Set the mock result for streaming output
    setPrivateField(testStrategy, "mockResult", result);

    // Set up the testStrategy with the necessary fields
    setPrivateField(testStrategy, "executablePath", executablePath);
    setPrivateField(testStrategy, "toolInfo", toolInfo);
    setPrivateField(testStrategy, "initialized", true);

    // Create image build config
    ImageBuildConfig config =
        ImageBuildConfig.builder()
            .contextDirectory(tempDir)
            .dockerfilePath("Dockerfile")
            .tags(Collections.singleton("myapp:latest"))
            .build();

    // Create a consumer to collect build output
    List<String> outputLines = new ArrayList<>();
    Consumer<String> outputConsumer = outputLines::add;

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
  void testListImages() throws Exception {
    // Setup
    String jsonOutput =
        "[{\"id\":\"image123\",\"names\":[\"myapp:latest\"],\"digest\":\"sha256:abc123\",\"created\":\"2023-05-20T12:34:56.789Z\",\"size\":10485760}]";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, jsonOutput, "");

    try (MockedStatic<ProcessExecutor> mockedProcessExecutor = mockStatic(ProcessExecutor.class)) {
      mockedProcessExecutor
          .when(
              () ->
                  ProcessExecutor.execute(argThat(commandContains("buildah", "images", "--json"))))
          .thenReturn(CompletableFuture.completedFuture(result));

      // Execute
      CompletableFuture<List<ImageInfo>> future = buildahStrategy.listImages();
      List<ImageInfo> images = future.get();

      // Verify
      assertEquals(1, images.size());
      ImageInfo imageInfo = images.get(0);
      assertEquals("image123", imageInfo.getId());
      assertEquals(List.of("myapp:latest"), imageInfo.getRepoTags());

      mockedProcessExecutor.verify(
          () -> ProcessExecutor.execute(argThat(commandContains("buildah", "images", "--json"))));
    }
  }

  @Test
  void testRemoveImage() throws Exception {
    // Setup
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, "", "");

    try (MockedStatic<ProcessExecutor> mockedProcessExecutor = mockStatic(ProcessExecutor.class)) {
      mockedProcessExecutor
          .when(
              () ->
                  ProcessExecutor.execute(
                      argThat(commandContains("buildah", "rmi", "--force", "image123"))))
          .thenReturn(CompletableFuture.completedFuture(result));

      // Execute
      CompletableFuture<Void> future = buildahStrategy.removeImage("image123", true, false);
      future.get(); // Wait for completion

      // Verify
      mockedProcessExecutor.verify(
          () ->
              ProcessExecutor.execute(
                  argThat(commandContains("buildah", "rmi", "--force", "image123"))));
    }
  }

  @Test
  void testPullImage() throws Exception {
    // Setup
    String pullOutput = "Pulling image nginx:latest...\nDownloading layers...\nPull complete";
    ProcessExecutor.ExecutionResult result = new ProcessExecutor.ExecutionResult(0, pullOutput, "");
    TestableBuildahStrategy testStrategy =
        new TestableBuildahStrategy(0); // Use exit code constructor

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
}
