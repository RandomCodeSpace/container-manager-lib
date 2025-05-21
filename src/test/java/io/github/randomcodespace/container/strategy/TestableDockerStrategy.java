package io.github.randomcodespace.container.strategy;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;

/**
 * A testable version of DockerStrategy that allows for mocking the DockerClient. This class is used
 * only for testing purposes.
 */
public class TestableDockerStrategy extends DockerStrategy {
  private DockerClient mockDockerClient;
  private DockerHttpClient mockDockerHttpClient;

  public TestableDockerStrategy(
      DockerClient mockDockerClient, DockerHttpClient mockDockerHttpClient) {
    this.mockDockerClient = mockDockerClient;
    this.mockDockerHttpClient = mockDockerHttpClient;
  }

  @Override
  public void initialize(DetectedToolInfo toolInfo) {
    if (toolInfo == null || toolInfo.getToolType() != ToolType.DOCKER) {
      throw new ContainerManagerException("Invalid ToolInfo for DockerStrategy initialization.");
    }

    // Skip the client creation and use the mocked client
    try {
      // Set the fields directly using reflection
      java.lang.reflect.Field clientField = DockerStrategy.class.getDeclaredField("dockerClient");
      clientField.setAccessible(true);
      clientField.set(this, mockDockerClient);

      java.lang.reflect.Field httpClientField =
          DockerStrategy.class.getDeclaredField("dockerHttpClient");
      httpClientField.setAccessible(true);
      httpClientField.set(this, mockDockerHttpClient);

      java.lang.reflect.Field toolInfoField = DockerStrategy.class.getDeclaredField("toolInfo");
      toolInfoField.setAccessible(true);
      toolInfoField.set(this, toolInfo);

      // Verify connection with a ping
      mockDockerClient.pingCmd().exec();

      // Set initialized to true
      java.lang.reflect.Field initializedField =
          DockerStrategy.class.getDeclaredField("initialized");
      initializedField.setAccessible(true);
      initializedField.set(this, true);

    } catch (Exception e) {
      // Set initialized to false
      try {
        java.lang.reflect.Field initializedField =
            DockerStrategy.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(this, false);
      } catch (Exception ex) {
        // Ignore
      }
      throw new ContainerManagerException(
          "Failed to connect to Docker daemon: " + e.getMessage(), e);
    }
  }
}
