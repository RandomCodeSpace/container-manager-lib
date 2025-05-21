package io.github.randomcodespace.container.strategy;

import io.github.randomcodespace.container.detection.DetectedToolInfo;
import io.github.randomcodespace.container.enums.ToolType;
import io.github.randomcodespace.container.exceptions.CommandExecutionException;
import io.github.randomcodespace.container.exceptions.ContainerManagerException;
import io.github.randomcodespace.container.utils.ProcessExecutor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for strategies that primarily interact with a tool via its CLI. Provides
 * common CLI execution logic.
 */
public abstract class AbstractCliStrategy implements ContainerToolStrategy {
  private static final Logger logger = LoggerFactory.getLogger(AbstractCliStrategy.class);

  protected Path executablePath;
  protected DetectedToolInfo toolInfo;
  protected boolean initialized = false;

  @Override
  public void initialize(DetectedToolInfo toolInfo) {
    if (toolInfo == null || toolInfo.getExecutablePath() == null) {
      throw new ContainerManagerException(
          "ToolInfo and executable path must not be null for CLI strategy initialization.");
    }
    if (toolInfo.getToolType() != getToolType()) {
      throw new ContainerManagerException(
          "Attempting to initialize "
              + getToolType()
              + " strategy with ToolInfo for "
              + toolInfo.getToolType());
    }
    this.toolInfo = toolInfo;
    this.executablePath = toolInfo.getExecutablePath();
    this.initialized = true;
    logger.info("{} CLI strategy initialized with executable: {}", getToolType(), executablePath);
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  protected void ensureInitialized() {
    if (!initialized) {
      throw new ContainerManagerException(
          getToolType() + " strategy has not been initialized. Call initialize() first.");
    }
  }

  /**
   * Executes a CLI command for the specific tool.
   *
   * @param arguments List of arguments for the command (excluding the executable itself).
   * @return A CompletableFuture holding the execution result.
   */
  protected CompletableFuture<ProcessExecutor.ExecutionResult> executeCliCommand(
      List<String> arguments) {
    ensureInitialized();
    List<String> command = new ArrayList<>();
    command.add(executablePath.toString());
    command.addAll(arguments);
    return ProcessExecutor.execute(command);
  }

  /**
   * Executes a CLI command and streams its STDOUT.
   *
   * @param arguments List of arguments.
   * @param outputConsumer Consumer for STDOUT lines.
   * @return CompletableFuture holding the exit code.
   */
  protected CompletableFuture<Integer> executeCliCommandAndStreamOutput(
      List<String> arguments, java.util.function.Consumer<String> outputConsumer) {
    ensureInitialized();
    List<String> command = new ArrayList<>();
    command.add(executablePath.toString());
    command.addAll(arguments);
    return ProcessExecutor.executeAndStreamOutput(command, outputConsumer);
  }

  /**
   * Helper method to handle CLI command results, throwing an exception if the command failed.
   *
   * @param result The execution result.
   * @param successMessage A message to log on success.
   * @return The stdout of the command if successful.
   * @throws CommandExecutionException if the command failed (non-zero exit code).
   */
  protected String handleCliResponse(ProcessExecutor.ExecutionResult result, String successMessage)
      throws CommandExecutionException {
    if (result.exitCode() == 0) {
      logger.debug(
          successMessage + " STDOUT: {}",
          result.stdout().length() > 100
              ? result.stdout().substring(0, 100) + "..."
              : result.stdout());
      return result.stdout();
    } else {
      String errorMessage =
          String.format(
              "%s command failed with exit code %d. STDERR: %s. STDOUT: %s",
              getToolType(), result.exitCode(), result.stderr(), result.stdout());
      logger.error(errorMessage);
      throw new CommandExecutionException(
          errorMessage,
          result.exitCode(),
          result.stderr().isEmpty() ? result.stdout() : result.stderr());
    }
  }

  @Override
  public abstract ToolType getToolType();
}
