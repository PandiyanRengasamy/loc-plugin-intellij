#!/bin/bash
# Test Program Verification Checklist
# Run this script to verify all test components are in place

echo "════════════════════════════════════════════════════════════"
echo "LOC Plugin Test Program - Verification Checklist"
echo "════════════════════════════════════════════════════════════"
echo ""

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FAILED=0
SUCCESS=0

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_file() {
    local file=$1
    local description=$2

    if [ -f "$SCRIPT_DIR/$file" ]; then
        echo -e "${GREEN}✓${NC} $description"
        ((SUCCESS++))
    else
        echo -e "${RED}✗${NC} $description - FILE NOT FOUND: $file"
        ((FAILED++))
    fi
}

check_directory() {
    local dir=$1
    local description=$2

    if [ -d "$SCRIPT_DIR/$dir" ]; then
        echo -e "${GREEN}✓${NC} $description"
        ((SUCCESS++))
    else
        echo -e "${RED}✗${NC} $description - DIRECTORY NOT FOUND: $dir"
        ((FAILED++))
    fi
}

check_gradle_dependency() {
    local dependency=$1
    local description=$2

    if grep -q "$dependency" "$SCRIPT_DIR/build.gradle.kts"; then
        echo -e "${GREEN}✓${NC} $description"
        ((SUCCESS++))
    else
        echo -e "${YELLOW}⚠${NC} $description - NOT FOUND IN build.gradle.kts: $dependency"
        ((FAILED++))
    fi
}

echo "Checking Test Files..."
echo "─────────────────────────────────────────────────────────"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/model/CodeEventRequestTest.java" \
    "CodeEventRequest unit tests"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/service/EventDispatcherTest.java" \
    "EventDispatcher unit tests"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/util/CsvFallbackStoreTest.java" \
    "CsvFallbackStore unit tests"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/util/GenAiToolDetectorTest.java" \
    "GenAiToolDetector unit tests"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/integration/LocPluginIntegrationTest.java" \
    "Integration tests"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/LocPluginTestSuite.java" \
    "Test suite"
check_file "src/test/kotlin/com/cts/plugin/intellij/loc/testutil/TestEventBuilder.java" \
    "Test utility builder"

echo ""
echo "Checking Documentation Files..."
echo "─────────────────────────────────────────────────────────"
check_file "TEST_PROGRAM.md" "Comprehensive test documentation"
check_file "TESTING.md" "Quick start guide"
check_file "TEST_PROGRAM_SUMMARY.md" "Test program summary"

echo ""
echo "Checking Runner Scripts..."
echo "─────────────────────────────────────────────────────────"
check_file "run-tests.sh" "Linux/Mac test runner"
check_file "run-tests.bat" "Windows test runner"

echo ""
echo "Checking Directory Structure..."
echo "─────────────────────────────────────────────────────────"
check_directory "src/test/kotlin/com/cts/plugin/intellij/loc/model" \
    "Model test directory"
check_directory "src/test/kotlin/com/cts/plugin/intellij/loc/service" \
    "Service test directory"
check_directory "src/test/kotlin/com/cts/plugin/intellij/loc/util" \
    "Util test directory"
check_directory "src/test/kotlin/com/cts/plugin/intellij/loc/integration" \
    "Integration test directory"
check_directory "src/test/kotlin/com/cts/plugin/intellij/loc/testutil" \
    "Test utility directory"

echo ""
echo "Checking Build Configuration..."
echo "─────────────────────────────────────────────────────────"
check_gradle_dependency "junit-jupiter" "JUnit Jupiter"
check_gradle_dependency "junit-platform-suite" "JUnit Platform Suite"
check_gradle_dependency "mockito-core" "Mockito"
check_gradle_dependency "assertj-core" "AssertJ"
check_gradle_dependency "useJUnitPlatform" "JUnit Platform configuration"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "Verification Results"
echo "════════════════════════════════════════════════════════════"
echo -e "${GREEN}Passed: $SUCCESS${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo ""
    echo "You can now run tests with:"
    echo "  Windows: .\\run-tests.bat all"
    echo "  Linux/Mac: bash run-tests.sh all"
    echo "  Gradle: ./gradlew test"
    exit 0
else
    echo -e "${RED}✗ Some checks failed!${NC}"
    echo ""
    echo "Please ensure:"
    echo "  1. All test files are in the correct directories"
    echo "  2. build.gradle.kts is updated with test dependencies"
    echo "  3. Runner scripts are executable"
    exit 1
fi

