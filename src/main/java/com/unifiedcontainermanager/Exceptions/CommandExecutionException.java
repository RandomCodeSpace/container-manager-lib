package com.unifiedcontainermanager.Exceptions;

public class CommandExecutionException extends ContainerManagerException {
  private final int exitCode;
  private final String commandOutput; // Could be STDOUT or STDERR

  public CommandExecutionException(String message, int exitCode, String commandOutput) {
    super(message);
    this.exitCode = exitCode;
    this.commandOutput = commandOutput;
  }

  public CommandExecutionException(
      String message, int exitCode, String commandOutput, Throwable cause) {
    super(message, cause);
    this.exitCode = exitCode;
    this.commandOutput = commandOutput;
  }

  public int getExitCode() {
    return exitCode;
  }

  public String getCommandOutput() {
    return commandOutput;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " (Exit Code: " + exitCode + ", Output: " + commandOutput + ")";
  }
}
