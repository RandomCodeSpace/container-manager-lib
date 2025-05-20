package com.unifiedcontainermanager.Strategy;

import com.unifiedcontainermanager.Utils.ProcessExecutor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A testable version of PodmanStrategy that allows for mocking the CLI command execution. This
 * class is used only for testing purposes.
 */
public class TestablePodmanStrategy extends PodmanStrategy {

  private ProcessExecutor.ExecutionResult mockResult;
  private Integer mockExitCode;

  public TestablePodmanStrategy(ProcessExecutor.ExecutionResult mockResult) {
    this.mockResult = mockResult;
  }

  public TestablePodmanStrategy(Integer mockExitCode) {
    this.mockExitCode = mockExitCode;
  }

  @Override
  protected CompletableFuture<ProcessExecutor.ExecutionResult> executeCliCommand(
      List<String> arguments) {
    if (mockResult != null) {
      return CompletableFuture.completedFuture(mockResult);
    }
    return super.executeCliCommand(arguments);
  }

  @Override
  protected CompletableFuture<Integer> executeCliCommandAndStreamOutput(
      List<String> arguments, Consumer<String> outputConsumer) {
    if (mockExitCode != null) {
      if (outputConsumer != null && mockResult != null && mockResult.stdout() != null) {
        for (String line : mockResult.stdout().split("\n")) {
          outputConsumer.accept(line);
        }
      }
      return CompletableFuture.completedFuture(mockExitCode);
    }
    return super.executeCliCommandAndStreamOutput(arguments, outputConsumer);
  }
}
