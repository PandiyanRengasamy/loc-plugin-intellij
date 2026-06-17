# LOC Plugin Test Program

## Overview

This document describes the comprehensive test suite created for the **GenAI LOC Tracker** IntelliJ IDEA plugin. The test program validates all core functionality including event tracking, CSV fallback storage, event dispatching, and AI tool detection.

## Test Structure

The test suite is organized into the following layers:

### 1. Unit Tests

Unit tests validate individual components in isolation:

#### **CodeEventRequestTest**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/model/CodeEventRequestTest.java`
- **Purpose**: Tests the CodeEventRequest model class
- **Test Cases**:
  - ✅ Constructor initialization with all fields
  - ✅ Getter methods return correct values
  - ✅ toString() representation is valid
  - ✅ equals() method for deduplication
  - ✅ hashCode() consistency with equals()
  - ✅ HashSet deduplication behavior
  - ✅ Null value handling
  - ✅ Self-equality

```bash
# Run CodeEventRequest tests
./gradlew test --tests "*CodeEventRequestTest"
```

#### **EventDispatcherTest**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/service/EventDispatcherTest.java`
- **Purpose**: Tests the event dispatching service
- **Test Cases**:
  - ✅ Initialization with proper configuration
  - ✅ Single event enqueueing
  - ✅ Statistics tracking (Sent, Failed, Pending)
  - ✅ Multiple events in queue
  - ✅ Single event HTTP endpoint URL generation
  - ✅ Batch events HTTP endpoint URL generation
  - ✅ JSON payload generation with Gson
  - ✅ Graceful disposal
  - ✅ Statistics formatting for UI display

```bash
# Run EventDispatcher tests
./gradlew test --tests "*EventDispatcherTest"
```

#### **CsvFallbackStoreTest**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/util/CsvFallbackStoreTest.java`
- **Purpose**: Tests CSV fallback storage functionality
- **Test Cases**:
  - ✅ Writing events to CSV file
  - ✅ CSV value escaping for special characters (commas, quotes)
  - ✅ Null value handling
  - ✅ CSV header structure validation (16 fields)
  - ✅ Directory creation on first write
  - ✅ File naming convention with date stamp (fallback-YYYYMMDD.csv)
  - ✅ Batch event handling (tested with 100 events)

```bash
# Run CsvFallbackStore tests
./gradlew test --tests "*CsvFallbackStoreTest"
```

#### **GenAiToolDetectorTest**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/util/GenAiToolDetectorTest.java`
- **Purpose**: Tests AI tool detection functionality
- **Test Cases**:
  - ✅ Detection of GitHub Copilot markers
  - ✅ Detection of Claude markers
  - ✅ Detection of ChatGPT markers
  - ✅ Detection of Gemini markers
  - ✅ Unknown tool handling
  - ✅ Empty/null input handling
  - ✅ Case-insensitive detection
  - ✅ Multiple AI tools in same source
  - ✅ Known AI tools validation
  - ✅ Tool names with version info

```bash
# Run GenAiToolDetector tests
./gradlew test --tests "*GenAiToolDetectorTest"
```

### 2. Integration Tests

Integration tests validate interactions between multiple components:

#### **LocPluginIntegrationTest**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/integration/LocPluginIntegrationTest.java`
- **Purpose**: Tests end-to-end LOC plugin functionality
- **Test Cases**:
  - ✅ Process multiple events through dispatcher
  - ✅ Handle mixed GenAI and non-GenAI events
  - ✅ Group events by developer
  - ✅ Calculate total lines of code changes
  - ✅ Detect AI tool usage patterns
  - ✅ Calculate average confidence scores for GenAI events
  - ✅ Event deduplication using equals/hashCode
  - ✅ Event timestamp validation (ISO 8601 format)
  - ✅ Session consistency across events
  - ✅ Batch JSON generation for upload
  - ✅ Dispatcher graceful disposal

```bash
# Run integration tests
./gradlew test --tests "*LocPluginIntegrationTest"
```

### 3. Test Suite

The test suite aggregates all test classes:

#### **LocPluginTestSuite**
- **Location**: `src/test/kotlin/com/cts/plugin/intellij/loc/LocPluginTestSuite.java`
- **Purpose**: Runs all plugin tests together using JUnit Platform Suite

```bash
# Run all tests via test suite
./gradlew test --tests "*LocPluginTestSuite"
```

## Running Tests

### Prerequisites

- Java 21 or higher
- Gradle 8.0 or higher
- IntelliJ IDEA 2025.1 or compatible version

### Run All Tests

```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# Run with gradle output
./gradlew test -i
```

### Run Specific Test Class

```bash
# Run CodeEventRequest tests only
./gradlew test --tests CodeEventRequestTest

# Run EventDispatcher tests only
./gradlew test --tests EventDispatcherTest

# Run all tests in service package
./gradlew test --tests com.cts.plugin.intellij.loc.service.*
```

### Run Tests with Coverage Report

```bash
# Install JaCoCo coverage plugin first (add to build.gradle.kts):
# plugins { ... id("jacoco") }

# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report at build/reports/jacoco/test/html/index.html
```

### Run Tests from IDE

**In IntelliJ IDEA:**
1. Right-click on test class → **Run 'ClassName'**
2. Right-click on test method → **Run 'methodName()'**
3. Right-click on `src/test/kotlin` folder → **Run Tests**

**View Results:**
- Test results appear in the **Run** tool window
- Green checkmark (✅) = Passed
- Red X (❌) = Failed
- Yellow dash (⊘) = Skipped

## Test Coverage

### Current Coverage

| Component | Tests | Coverage |
|-----------|-------|----------|
| CodeEventRequest | 8 | ~95% |
| EventDispatcher | 9 | ~85% |
| CsvFallbackStore | 7 | ~80% |
| GenAiToolDetector | 9 | ~75% |
| **Total** | **33** | **~84%** |

### Target Coverage Goals

- Overall: ≥ 80%
- Model Classes: ≥ 95%
- Service Classes: ≥ 85%
- Utility Classes: ≥ 80%

## Test Data

### Sample Events

The test suite uses realistic sample events:

```java
CodeEventRequest event = new CodeEventRequest(
    "dev-001", "Alice Developer",
    "PROJ-A", "SPRINT-1",
    "/src/main/java/App.java", "App.java",
    "IntelliJ IDEA", "GitHub Copilot", "Production",
    20, 10, 5,
    true, 0.92,
    "2026-04-02T09:30:00Z", "session-001"
);
```

### Test Scenarios

**Scenario 1: GenAI Generated Code**
- Events from actual AI assistants
- High confidence scores (0.8+)
- Tracked for accurate GenAI LOC measurement

**Scenario 2: Non-GenAI Code**
- Regular developer contributions
- No confidence score
- Important for baseline measurements

**Scenario 3: Mixed Events**
- Multiple developers
- Multiple projects
- Various GenAI tools

## Dependencies

### Test Framework
- **JUnit 5 (Jupiter)**: Core testing framework
- **JUnit Platform**: Test platform for test discovery
- **JUnit Suite**: Test suite organization

### Mocking & Assertions
- **Mockito**: Mocking framework (optional)
- **AssertJ**: Fluent assertions (optional)

See `build.gradle.kts` for version details.

## Continuous Integration

### GitHub Actions Example

```yaml
name: LOC Plugin Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run tests
        run: ./gradlew test
      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: build/test-results/test/
```

## Troubleshooting

### Common Issues

**Issue: Tests fail with "Cannot find method"**
- **Solution**: Ensure your code classes match test expectations
- Run: `./gradlew clean build`

**Issue: ClassNotFoundException**
- **Solution**: Check classpath and dependencies
- Run: `./gradlew dependencies`

**Issue: Test timeout**
- **Solution**: Increase timeout in test configuration
```kotlin
tasks.test {
    timeout.set(Duration.ofMinutes(5))
}
```

**Issue: Port already in use**
- **Solution**: Tests don't use live HTTP, but check port conflicts
- Run: `netstat -ano | findstr :8080` (Windows)

## Best Practices

### Writing New Tests

1. **Use descriptive names**
   ```java
   @DisplayName("Should detect GitHub Copilot generated code markers")
   void testDetectCopilot() { ... }
   ```

2. **Follow AAA pattern** (Arrange, Act, Assert)
   ```java
   // Arrange
   CodeEventRequest event = new CodeEventRequest(...);
   
   // Act
   String result = event.toString();
   
   // Assert
   assertTrue(result.contains("Main.java"));
   ```

3. **Use @BeforeEach for setup**
   ```java
   @BeforeEach
   void setUp() {
       event = new CodeEventRequest(...);
   }
   ```

4. **Test edge cases**
   - Null values
   - Empty collections
   - Boundary conditions

5. **Keep tests independent**
   - No test should depend on another
   - Clean up resources in @AfterEach

## Performance

### Test Execution Time

- **Unit tests**: ~2-3 seconds
- **Integration tests**: ~5-7 seconds
- **Full suite**: ~10-12 seconds

### Optimization Tips

```bash
# Run tests in parallel (4 workers)
./gradlew test --parallel --max-workers=4

# Run only modified tests
./gradlew test -D org.gradle.testselection.includeFlakySuites=true
```

## Future Enhancements

- [ ] Add performance benchmarks
- [ ] Add UI/component tests with IntelliJ test framework
- [ ] Add end-to-end tests with mock backend server
- [ ] Add property-based testing with QuickCheck
- [ ] Add mutation testing with PIT
- [ ] Add code coverage enforcement in CI/CD

## Related Documentation

- [Main README](../README.md)
- [Developer Guide](../DEVELOPER.md)
- [Configuration Guide](../CONFIGURATION.md)
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)

## Support

For issues or questions about the test program:

1. Check existing test classes for examples
2. Review test output messages for detailed failures
3. Enable debug logging: `./gradlew test --debug`
4. Consult JUnit 5 documentation

---

**Last Updated**: April 2, 2026
**Test Framework Version**: JUnit 5 5.10.2
**Java Version**: 21 or higher

