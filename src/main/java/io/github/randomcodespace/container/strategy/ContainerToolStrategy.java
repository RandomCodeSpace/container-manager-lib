package io.github.randomcodespace.container.strategy;

import io.github.randomcodespace.container.core.ContainerManager;
import io.github.randomcodespace.container.core.ImageManager;
import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.enums.ToolType;

public interface ContainerToolStrategy extends ContainerManager, ImageManager {
  ToolType getToolType();

  void initialize(DetectedToolInfo toolInfo);

  boolean isInitialized();
}
