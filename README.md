# Unified Java Container Management Library

A Java library to manage Docker, Podman, and Buildah through a unified interface.

## Overview

This library provides a consistent API for working with different container technologies:
- Docker
- Podman
- Buildah

The Unified Container Manager Library simplifies container operations by abstracting away the differences between container tools. It allows developers to:

- Build, run, and manage containers using a single API
- Switch between container technologies without changing code
- Automatically detect available container tools on the system
- Execute container operations with proper error handling and logging
- Support for both CLI-based and API-based container tool interactions

## Requirements

- Java 21 or higher
- Maven 3.6 or higher
- One or more container tools (Docker, Podman, or Buildah) installed on your system

## Building the Project

### Local Build

To build the project locally:

```bash
mvn clean package
```

The JAR file will be created in the `target` directory.

### GitHub Actions

This project includes a GitHub Actions workflow that automatically builds the JAR file and generates JavaDocs when code is pushed to the main/master branch or when a pull request is created against these branches.

The workflow:
1. Sets up a Java 21 environment
2. Builds the project with Maven
3. Generates JavaDocs
4. Uploads the resulting JAR and JavaDocs as build artifacts
5. Deploys the JavaDocs to GitHub Pages (only on push to main/master)

To access the built JAR from GitHub Actions:
1. Go to your GitHub repository
2. Navigate to the Actions tab
3. Select the latest workflow run
4. Download the artifact from the Artifacts section

#### JavaDocs

The project's JavaDocs are automatically generated and published to GitHub Pages on every push to the main/master branch. You can access the latest JavaDocs at:

```
https://[your-github-username].github.io/container-manager-lib/
```

Replace `[your-github-username]` with your actual GitHub username or organization name.

## Usage

Add the JAR to your project's dependencies and use the container management API as shown in the examples below:

### Basic Usage

```java
import com.unifiedcontainermanager.Strategy.ContainerService;
import com.unifiedcontainermanager.Core.ContainerManager;
import com.unifiedcontainermanager.Core.ImageManager;
import com.unifiedcontainermanager.Enums.ToolType;
import com.unifiedcontainermanager.DTOs.ContainerConfig;
import com.unifiedcontainermanager.DTOs.ImageBuildConfig;
import com.unifiedcontainermanager.DTOs.ContainerInfo;
import com.unifiedcontainermanager.DTOs.ImageInfo;
import com.unifiedcontainermanager.Exceptions.ContainerManagerException;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ContainerExample {
    public static void main(String[] args) {
        // Create a ContainerService with preferred tool order
        ContainerService service = new ContainerService(
            Arrays.asList(ToolType.DOCKER, ToolType.PODMAN, ToolType.BUILDAH)
        );

        try {
            // Initialize the service (detects available tools)
            service.initialize();

            // Get information about the active tool
            service.getActiveToolType().ifPresent(
                toolType -> System.out.println("Using: " + toolType)
            );

            // Use the service for container and image operations
            runContainerExample(service);
            buildImageExample(service);

        } catch (ContainerManagerException e) {
            System.err.println("Container operation failed: " + e.getMessage());
        } finally {
            try {
                service.close();
            } catch (Exception e) {
                System.err.println("Error closing service: " + e.getMessage());
            }
        }
    }

    private static void runContainerExample(ContainerService service) throws ContainerManagerException {
        // Create a container configuration
        ContainerConfig config = new ContainerConfig.ContainerConfigBuilder()
            .imageName("nginx")
            .tag("latest")
            .name("web-server")
            .portBindings(Arrays.asList("8080:80"))
            .build();

        // Create and start a container
        String containerId = service.createContainer(config).join();
        System.out.println("Created container: " + containerId);

        service.startContainer(containerId).join();
        System.out.println("Container started");

        // List running containers
        List<ContainerInfo> containers = service.listContainers(false).join();
        containers.forEach(container -> 
            System.out.println("Running container: " + container.getId() + " - " + container.getName())
        );

        // Stop and remove the container
        service.stopContainer(containerId, 10).join();
        service.removeContainer(containerId, true, true).join();
        System.out.println("Container stopped and removed");
    }

    private static void buildImageExample(ContainerService service) throws ContainerManagerException {
        // Pull an image
        CompletableFuture<Void> pullFuture = service.pullImage("alpine", "latest", 
            progress -> System.out.println("Pull progress: " + progress)
        );
        pullFuture.join();

        // Build a custom image
        ImageBuildConfig buildConfig = new ImageBuildConfig.ImageBuildConfigBuilder()
            .dockerfilePath("./Dockerfile")
            .tag("my-custom-image:latest")
            .build();

        String imageId = service.buildImage(buildConfig, 
            output -> System.out.println("Build output: " + output)
        ).join();
        System.out.println("Built image: " + imageId);

        // List available images
        List<ImageInfo> images = service.listImages().join();
        images.forEach(image -> 
            System.out.println("Image: " + image.getId() + " - " + image.getName())
        );
    }
}
```

### Asynchronous API Usage

The library provides a fully asynchronous API using CompletableFuture:

```java
// Initialize the service asynchronously
ContainerService service = new ContainerService();
service.initializeAsync()
    .thenCompose(v -> {
        // Create a container
        ContainerConfig config = new ContainerConfig.ContainerConfigBuilder()
            .imageName("redis")
            .tag("latest")
            .name("cache-server")
            .build();
        return service.createContainer(config);
    })
    .thenCompose(containerId -> {
        System.out.println("Container created: " + containerId);
        return service.startContainer(containerId)
            .thenApply(v -> containerId);
    })
    .thenCompose(containerId -> {
        System.out.println("Container started");
        // Do something with the running container

        // Then stop and remove it
        return service.stopContainer(containerId, null)
            .thenCompose(v -> service.removeContainer(containerId, true, true));
    })
    .exceptionally(ex -> {
        System.err.println("Operation failed: " + ex.getMessage());
        return null;
    })
    .join();
```

### Auto-Detection of Container Tools

The library automatically detects available container tools:

```java
// Create service with default tool detection
ContainerService service = new ContainerService();
service.initialize();

// Check which tool was detected and is being used
service.getActiveToolType().ifPresent(toolType -> {
    switch (toolType) {
        case DOCKER:
            System.out.println("Using Docker");
            break;
        case PODMAN:
            System.out.println("Using Podman");
            break;
        case BUILDAH:
            System.out.println("Using Buildah");
            break;
        default:
            System.out.println("Unknown tool");
    }
});
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

The MIT License is a permissive license that is short and to the point. It lets people do anything with your code with proper attribution and without warranty.
