package com.cts.plugin.intellij.loc.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;

import java.util.ArrayList;
import java.util.List;

/**
 * GenAiToolDetector
 *
 * Detects which GenAI coding-assistant plugins are currently installed
 * and enabled in this IntelliJ IDEA instance.
 *
 * Supported tools:
 *   COPILOT  – GitHub Copilot
 *   CLAUDE   – Claude / Amazon Q (Anthropic)
 *   CHATGPT  – ChatGPT / AI Assistant (JetBrains)
 *   GEMINI   – Gemini Code Assist (Google)
 *   CODEWHISPERER – Amazon CodeWhisperer
 *   OTHER    – any other installed AI plugin
 */
public class GenAiToolDetector {

    private static final Logger LOG = Logger.getInstance(GenAiToolDetector.class);

    // ── Known plugin IDs ──────────────────────────────────────────────────
    private static final String[][] KNOWN_TOOLS = {
            { "COPILOT",        "com.github.copilot" },
            { "COPILOT",        "GitHub.copilot" },                   // alternative ID in some versions
            { "COPILOT",        "com.github.github-copilot" },        // alternate format
            { "CLAUDE",         "com.anthropic.claude" },
            { "CLAUDE",         "amazon.q" },                      // Amazon Q (Claude-based)
            { "CHATGPT",        "com.intellij.ml.llm" },           // JetBrains AI Assistant
            { "CHATGPT",        "com.openai.chatgpt" },
            { "GEMINI",         "com.google.ide-plugin" },         // Gemini Code Assist
            { "GEMINI",         "com.google.gemini" },
            { "CODEWHISPERER",  "com.amazonaws.codewhisperer" },
            { "TABNINE",        "com.tabnine.TabNine" },
            { "CODEIUM",        "com.codeium.intellij" },
    };

    /**
     * Returns the first detected GenAI tool name, or "OTHER" if none found.
     * Use this as the default for {@code defaultGenAiTool}.
     */
    public static String detectPrimary() {
        List<String> detected = detectAll();
        String primary = detected.isEmpty() ? "OTHER" : detected.get(0);
        LOG.info("GenAiToolDetector: primary tool detected -> " + primary);
        return primary;
    }

    /**
     * Returns a list of ALL detected GenAI tool names (in detection order).
     * Useful when multiple AI plugins are installed.
     */
    public static List<String> detectAll() {
        List<String> found = new ArrayList<>();
        for (String[] entry : KNOWN_TOOLS) {
            String toolName = entry[0];
            String pluginId = entry[1];
            if (isPluginEnabled(pluginId) && !found.contains(toolName)) {
                LOG.info("GenAiToolDetector: detected plugin [" + pluginId + "] -> tool=" + toolName);
                found.add(toolName);
            }
        }
        if (found.isEmpty()) {
            LOG.warn("GenAiToolDetector: no known GenAI plugins detected, defaulting to OTHER");
        } else {
            LOG.info("GenAiToolDetector: all detected tools -> " + found);
        }
        return found;
    }

    /**
     * Returns true if the given plugin ID is installed and enabled.
     */
    public static boolean isPluginEnabled(String pluginId) {
        IdeaPluginDescriptor plugin =
                PluginManagerCore.getPlugin(PluginId.getId(pluginId));
        boolean enabled = plugin != null && plugin.isEnabled();
        LOG.debug("GenAiToolDetector: checking plugin [" + pluginId + "] -> " + (enabled ? "ENABLED" : "not found/disabled"));
        return enabled;
    }
}

