package com.cts.plugin.intellij.loc;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Test Suite for GenAI LOC Tracker Plugin
 * 
 * This suite runs all unit tests for the LOC tracking plugin components:
 * - CodeEventRequest model tests
 * - EventDispatcher service tests
 * - CsvFallbackStore utility tests
 * - GenAiToolDetector utility tests
 */
@Suite
@SelectClasses({
        com.cts.plugin.intellij.loc.model.CodeEventRequestTest.class,
        com.cts.plugin.intellij.loc.service.EventDispatcherTest.class,
        com.cts.plugin.intellij.loc.util.CsvFallbackStoreTest.class,
        com.cts.plugin.intellij.loc.util.GenAiToolDetectorTest.class
})
public class LocPluginTestSuite {
    // Test suite definition
}

