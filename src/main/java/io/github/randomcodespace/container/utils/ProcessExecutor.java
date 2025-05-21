package io.github.randomcodespace.container.utils;

import io.github.randomcodespace.container.exceptions.CommandExecutionException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for executing external processes. Handles asynchronous stream consumption to
 * prevent deadlocks.
 */
public class ProcessExecutor {
  private static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);
  // Using a virtual thread per task executor for I/O bound tasks like stream reading.
  // Requires Java 19+ for Project Loom's virtual threads. For Java 17, use a cached thread pool.
  // For Java 17, replace with:
  private static final ExecutorService streamGobblerExecutor = Executors.newCachedThreadPool();

  // private static final ExecutorService streamGobblerExecutor =
  // Executors.newVirtualThreadPerTaskExecutor();

  /** Represents the result of a process execution. */
  public record ExecutionResult(int exitCode, String stdout, String stderr) {}

  /**
   * Executes a command and returns its result asynchronously.
   *
   * @param command The command and its arguments.
   * @return A CompletableFuture holding the ExecutionResult.
   */
  public static CompletableFuture<ExecutionResult> execute(List<String> command) {
    return CompletableFuture.supplyAsync(
        () -> {
          logger.debug("Executing command: {}", String.join(" ", command));
          ProcessBuilder processBuilder = new ProcessBuilder(command);
          try {
            Process process = processBuilder.start();

            StringBuilder stdoutBuilder = new StringBuilder();
            StringBuilder stderrBuilder = new StringBuilder();

            // Asynchronously consume stdout and stderr streams
            CompletableFuture<Void> stdoutFuture =
                consumeStream(process.getInputStream(), stdoutBuilder::append);
            CompletableFuture<Void> stderrFuture =
                consumeStream(process.getErrorStream(), stderrBuilder::append);

            // Wait for stream consumption to complete and process to exit
            CompletableFuture.allOf(stdoutFuture, stderrFuture).join();
            int exitCode = process.waitFor();

            String stdout = stdoutBuilder.toString().trim();
            String stderr = stderrBuilder.toString().trim();

            if (exitCode != 0) {
              logger.warn(
                  "Command failed with exit code {}: {}. Stderr: {}",
                  exitCode,
                  String.join(" ", command),
                  stderr);
            } else {
              logger.debug(
                  "Command succeeded: {}. Stdout: {}",
                  String.join(" ", command),
                  stdout.length() > 100 ? stdout.substring(0, 100) + "..." : stdout);
            }
            return new ExecutionResult(exitCode, stdout, stderr);

          } catch (IOException e) {
            logger.error("IOException during command execution: {}", String.join(" ", command), e);
            throw new CommandExecutionException(
                "I/O error executing command: " + e.getMessage(), -1, "", e);
          } catch (InterruptedException e) {
            logger.error("Command execution interrupted: {}", String.join(" ", command), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new CommandExecutionException(
                "Command execution interrupted: " + e.getMessage(), -1, "", e);
          }
        },
        streamGobblerExecutor); // Use a dedicated executor for the blocking parts
  }

  /**
   * Executes a command and streams its STDOUT to a consumer. This is useful for commands that
   * produce continuous output (e.g., logs, build progress).
   *
   * @param command The command and its arguments.
   * @param outputConsumer Consumer for each line of STDOUT.
   * @return A CompletableFuture holding the exit code.
   */
  public static CompletableFuture<Integer> executeAndStreamOutput(
      List<String> command, Consumer<String> outputConsumer) {
    return CompletableFuture.supplyAsync(
        () -> {
          logger.debug("Executing and streaming output for command: {}", String.join(" ", command));
          ProcessBuilder processBuilder = new ProcessBuilder(command);
          try {
            Process process = processBuilder.start();

            // Asynchronously consume stdout and stream to consumer
            CompletableFuture<Void> stdoutFuture =
                consumeAndStream(process.getInputStream(), outputConsumer);
            // Consume stderr to prevent blocking, but log it or handle as errors
            StringBuilder stderrBuilder = new StringBuilder();
            CompletableFuture<Void> stderrFuture =
                consumeStream(
                    process.getErrorStream(),
                    line -> {
                      logger.warn("[STDERR] {}: {}", String.join(" ", command), line);
                      stderrBuilder.append(line);
                    });

            CompletableFuture.allOf(stdoutFuture, stderrFuture).join();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
              logger.warn(
                  "Streaming command failed with exit code {}: {}. Stderr: {}",
                  exitCode,
                  String.join(" ", command),
                  stderrBuilder.toString().trim());
            } else {
              logger.debug("Streaming command succeeded: {}", String.join(" ", command));
            }
            return exitCode;

          } catch (IOException e) {
            logger.error(
                "IOException during streaming command execution: {}", String.join(" ", command), e);
            throw new CommandExecutionException(
                "I/O error executing streaming command: " + e.getMessage(), -1, "", e);
          } catch (InterruptedException e) {
            logger.error(
                "Streaming command execution interrupted: {}", String.join(" ", command), e);
            Thread.currentThread().interrupt();
            throw new CommandExecutionException(
                "Streaming command execution interrupted: " + e.getMessage(), -1, "", e);
          }
        },
        streamGobblerExecutor);
  }

  private static CompletableFuture<Void> consumeStream(
      InputStream inputStream, Consumer<String> lineConsumer) {
    return CompletableFuture.runAsync(
        () -> {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
              lineConsumer.accept(line + System.lineSeparator());
            }
          } catch (IOException e) {
            // Log or handle exception if necessary, e.g., if the process is killed prematurely
            logger.trace(
                "IOException while consuming stream (this can be normal if process exits quickly): {}",
                e.getMessage());
          }
        },
        streamGobblerExecutor);
  }

  private static CompletableFuture<Void> consumeAndStream(
      InputStream inputStream, Consumer<String> lineConsumer) {
    return CompletableFuture.runAsync(
        () -> {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
              lineConsumer.accept(line);
            }
          } catch (IOException e) {
            logger.warn("IOException while streaming output: {}", e.getMessage());
            // Depending on the use case, this might need to propagate as an error
          }
        },
        streamGobblerExecutor);
  }
}
