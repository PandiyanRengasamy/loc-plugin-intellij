package com.cts.plugin.intellij.loc.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that creates the "GenAI LOC Report" tool window.
 * Registered in plugin.xml — IntelliJ calls this once per project when the
 * tool window stripe button is first clicked.
 */
public class LocReportToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LocReportPanel panel = new LocReportPanel(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel.getContent(), "LOC Report", false);
        toolWindow.getContentManager().addContent(content);
    }
}

