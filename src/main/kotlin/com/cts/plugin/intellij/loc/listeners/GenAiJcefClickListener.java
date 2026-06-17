package com.cts.plugin.intellij.loc.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * GenAiJcefClickListener — DISABLED.
 *
 * This listener was designed to detect Accept/Keep button clicks inside JCEF-based
 * tool windows by injecting JavaScript. However, GitHub Copilot Chat in IntelliJ
 * uses native Swing UI (not JCEF), so this listener never finds a JBCefBrowser
 * and only creates log noise with repeated polling.
 *
 * The detection of Copilot Chat code acceptance is already handled by:
 * - CopilotActionListener (action-based detection)
 * - GenAiCommandListener (command-based fallback)
 * - CopilotKeepAllMouseListener / GenAiMouseClickListener (Swing button clicks)
 *
 * This class is kept as a no-op placeholder for future use if a JCEF-based AI tool
 * (e.g., Claude, Gemini) is added that requires JS injection.
 */
public class GenAiJcefClickListener implements StartupActivity, Disposable {

    private static final Logger LOG = Logger.getInstance(GenAiJcefClickListener.class);

    @Override
    public void runActivity(@NotNull Project project) {
        // No-op: JCEF injection is disabled because current AI tools use native Swing UI.
        LOG.info("GenAiJcefClickListener — DISABLED (Copilot Chat uses native Swing, not JCEF)");
    }

    @Override
    public void dispose() {
        // Nothing to dispose
    }
}
