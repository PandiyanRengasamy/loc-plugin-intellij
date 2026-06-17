#!/bin/bash
# LOC Plugin Test Runner Script
# This script provides convenient commands to run various test configurations

set -e

PROJECT_DIR=$(dirname "$(readlink -f "$0")")
GRADLE="./gradlew"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

show_help() {
    cat << EOF
LOC Plugin Test Runner

Usage: $0 [COMMAND] [OPTIONS]

Commands:
  all              Run all tests
  unit             Run all unit tests
  integration      Run integration tests
  model            Run CodeEventRequest tests
  service          Run EventDispatcher tests
  util             Run utility tests (CSV, GenAiTool)
  suite            Run test suite
  coverage         Run tests with code coverage report
  debug            Run tests with debug logging
  watch            Run tests in watch mode (requires watchman)

Options:
  -v, --verbose    Show detailed output
  -q, --quiet      Minimal output
  -p, --parallel   Run tests in parallel (4 workers)
  -h, --help       Show this help message

Examples:
  $0 all                    # Run all tests
  $0 unit -v                # Run unit tests with verbose output
  $0 model                  # Run model tests only
  $0 integration -p         # Run integration tests in parallel
  $0 coverage               # Generate coverage report

Environment Variables:
  GRADLE_ARGS              Additional arguments to pass to gradle
  TEST_FILTER              Filter tests by name pattern
  COVERAGE_MIN_PCT         Minimum coverage percentage (default: 80)

EOF
}

# Parse arguments
COMMAND=${1:-all}
shift || true

# Build gradle command
GRADLE_CMD="$GRADLE"
TEST_PATTERNS=""
COVERAGE_MODE=false
PARALLEL_MODE=false

# Process options
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            GRADLE_CMD="$GRADLE_CMD --info"
            shift
            ;;
        -q|--quiet)
            GRADLE_CMD="$GRADLE_CMD -q"
            shift
            ;;
        -p|--parallel)
            GRADLE_CMD="$GRADLE_CMD --parallel --max-workers=4"
            PARALLEL_MODE=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            TEST_PATTERNS="$1"
            shift
            ;;
    esac
done

# Execute commands
case $COMMAND in
    all)
        print_header "Running All Tests"
        $GRADLE_CMD test $GRADLE_ARGS
        print_success "All tests completed!"
        ;;

    unit)
        print_header "Running Unit Tests"
        $GRADLE_CMD test --tests "*Test" $GRADLE_ARGS
        print_success "Unit tests completed!"
        ;;

    integration)
        print_header "Running Integration Tests"
        $GRADLE_CMD test --tests "*IntegrationTest" $GRADLE_ARGS
        print_success "Integration tests completed!"
        ;;

    model)
        print_header "Running Model Tests"
        $GRADLE_CMD test --tests "*CodeEventRequestTest" $GRADLE_ARGS
        print_success "Model tests completed!"
        ;;

    service)
        print_header "Running Service Tests"
        $GRADLE_CMD test --tests "*EventDispatcherTest" --tests "*ProjectServiceTest" $GRADLE_ARGS
        print_success "Service tests completed!"
        ;;

    util)
        print_header "Running Utility Tests"
        $GRADLE_CMD test --tests "*CsvFallbackStoreTest" --tests "*GenAiToolDetectorTest" $GRADLE_ARGS
        print_success "Utility tests completed!"
        ;;

    suite)
        print_header "Running Test Suite"
        $GRADLE_CMD test --tests "*LocPluginTestSuite" $GRADLE_ARGS
        print_success "Test suite completed!"
        ;;

    coverage)
        print_header "Running Tests with Coverage Report"
        if grep -q '"jacoco"' build.gradle.kts 2>/dev/null; then
            $GRADLE_CMD test jacocoTestReport $GRADLE_ARGS
            print_success "Coverage report generated!"
            echo -e "${BLUE}View report at: build/reports/jacoco/test/html/index.html${NC}"
        else
            print_warning "JaCoCo plugin not found in build.gradle.kts"
            print_warning "Add: plugins { ... id(\"jacoco\") }"
            $GRADLE_CMD test $GRADLE_ARGS
        fi
        ;;

    debug)
        print_header "Running Tests with Debug Logging"
        $GRADLE_CMD test --debug $GRADLE_ARGS
        ;;

    watch)
        print_header "Running Tests in Watch Mode"
        print_warning "This requires watchman to be installed"
        $GRADLE_CMD --continuous test $GRADLE_ARGS
        ;;

    clean)
        print_header "Cleaning Build Artifacts"
        $GRADLE_CMD clean
        print_success "Build artifacts cleaned!"
        ;;

    help|--help|-h)
        show_help
        ;;

    *)
        echo "Unknown command: $COMMAND"
        show_help
        exit 1
        ;;
esac

# Print test results summary
if [ -d "build/test-results/test" ]; then
    print_header "Test Results Summary"
    for xml_file in build/test-results/test/*.xml; do
        if [ -f "$xml_file" ]; then
            # Extract test counts (requires xmllint or grep)
            tests=$(grep -o 'tests="[0-9]*"' "$xml_file" | head -1 | sed 's/tests="\([0-9]*\)"/\1/')
            failures=$(grep -o 'failures="[0-9]*"' "$xml_file" | head -1 | sed 's/failures="\([0-9]*\)"/\1/')
            skipped=$(grep -o 'skipped="[0-9]*"' "$xml_file" | head -1 | sed 's/skipped="\([0-9]*\)"/\1/')

            if [ -n "$tests" ]; then
                echo "Tests: $tests | Failures: $failures | Skipped: $skipped"
            fi
        fi
    done
fi

exit 0

