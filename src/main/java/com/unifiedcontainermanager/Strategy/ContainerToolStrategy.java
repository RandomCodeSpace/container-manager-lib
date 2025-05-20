package com.unifiedcontainermanager.Strategy;

import com.unifiedcontainermanager.Core.ContainerManager;
import com.unifiedcontainermanager.Core.ImageManager;
import com.unifiedcontainermanager.Detection.DetectedToolInfo;
import com.unifiedcontainermanager.Enums.ToolType;

public interface ContainerToolStrategy extends ContainerManager, ImageManager {
  ToolType getToolType();

  void initialize(DetectedToolInfo toolInfo);

  boolean isInitialized();
}
