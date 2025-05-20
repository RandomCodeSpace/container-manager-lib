package com.unifiedcontainermanager.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for JSON parsing using Jackson. */
public class JsonParserUtil {
  private static final Logger logger = LoggerFactory.getLogger(JsonParserUtil.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    // Configure ObjectMapper for flexibility and to handle Java 8+ time types
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    // Add other configurations as needed, e.g., date formats
  }

  /**
   * Parses a JSON string into an object of the specified type.
   *
   * @param jsonString The JSON string to parse.
   * @param valueType The class of the object to parse into.
   * @param <T> The type of the object.
   * @return An Optional containing the parsed object, or Optional.empty() if parsing fails.
   */
  public static <T> Optional<T> fromJson(String jsonString, Class<T> valueType) {
    if (jsonString == null || jsonString.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(objectMapper.readValue(jsonString, valueType));
    } catch (JsonProcessingException e) {
      logger.warn(
          "Failed to parse JSON string to {}: {}. JSON: {}",
          valueType.getSimpleName(),
          e.getMessage(),
          overview(jsonString));
      return Optional.empty();
    }
  }

  /**
   * Parses a JSON string into an object of the specified generic type.
   *
   * @param jsonString The JSON string to parse.
   * @param typeReference The TypeReference for the generic type.
   * @param <T> The type of the object.
   * @return An Optional containing the parsed object, or Optional.empty() if parsing fails.
   */
  public static <T> Optional<T> fromJson(String jsonString, TypeReference<T> typeReference) {
    if (jsonString == null || jsonString.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(objectMapper.readValue(jsonString, typeReference));
    } catch (JsonProcessingException e) {
      logger.warn(
          "Failed to parse JSON string to generic type {}: {}. JSON: {}",
          typeReference.getType().getTypeName(),
          e.getMessage(),
          overview(jsonString));
      return Optional.empty();
    }
  }

  /**
   * Serializes an object into a JSON string.
   *
   * @param object The object to serialize.
   * @return An Optional containing the JSON string, or Optional.empty() if serialization fails.
   */
  public static Optional<String> toJson(Object object) {
    if (object == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.writeValueAsString(object));
    } catch (JsonProcessingException e) {
      logger.warn(
          "Failed to serialize object to JSON: {}. Object: {}", e.getMessage(), object.toString());
      return Optional.empty();
    }
  }

  private static String overview(
      String text) { // Renamed to avoid conflict with potential 'overview' method
    if (text == null) return "null";
    return text.length() > 200 ? text.substring(0, 200) + "..." : text;
  }
}
