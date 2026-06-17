package com.cts.plugin.intellij.loc.listeners;

import com.cts.plugin.intellij.loc.model.LOCRequestPayload;
import com.cts.plugin.intellij.loc.service.GenAiLocProjectService;
import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.cts.plugin.intellij.loc.util.PendingLocEvent;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GenAiMouseClickListener — detects mouse clicks on Accept/Keep/Keep All buttons.
 *
 * Strategy:
 * 1. Registers a global AWT event dispatcher via IdeEventQueue
 * 2. On MOUSE_RELEASED, inspects the clicked component:
 *    - Checks the component's text (JButton, JLabel, AbstractButton)
 *    - Checks accessible name/description
 *    - Checks tooltip text
 * 3. If the text matches accept/keep keywords AND PendingLocEvent has entries → sends them
 *
 * Note: This works for Swing-based buttons (diff viewer Apply/Accept).
 * For JCEF (Copilot Chat), the AnActionListener approach is still needed since
 * JCEF renders buttons inside embedded Chromium (not accessible via Swing).
 * However, when JCEF triggers an IntelliJ action, the CopilotActionListener handles it.
 * This listener covers Swing-based diff/merge tool buttons and any custom Swing UI.
 */
public class GenAiMouseClickListener implements StartupActivity, Disposable {

    private static final Logger LOG = Logger.getInstance(GenAiMouseClickListener.class);

    /** Keywords to match in button/component text (case-insensitive) */
    private static final Set<String> ACCEPT_KEYWORDS = Set.of(
            "accept", "accept all", "keep", "keep all", "apply", "apply all",
            "insert", "insert at cursor", "apply to file", "accept suggestion",
            "accept change", "accept changes"
    );

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
        if (!GenAiLocSettings.getInstance().isEnabled()) {
            LOG.info("GenAiMouseClickListener — plugin disabled, skipping registration");
            return;
        }

        // Only register if a GenAI tool window is present (not in main code editor)
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        boolean hasGenAIToolWindow = false;
        for (String id : toolWindowManager.getToolWindowIds()) {
            if (id.toLowerCase().contains("copilot") || id.toLowerCase().contains("claude") || id.toLowerCase().contains("genai")) {
                hasGenAIToolWindow = true;
                break;
            }
        }
        if (!hasGenAIToolWindow) {
            LOG.info("GenAiMouseClickListener — no GenAI tool window found, not registering mouse listener");
            return;
        }

        LOG.info("GenAiMouseClickListener — registering global AWT mouse event dispatcher (GenAI tool window detected)");

        IdeEventQueue.EventDispatcher eventDispatcher = event -> {
            if (event instanceof MouseEvent mouseEvent) {
                if (mouseEvent.getID() == MouseEvent.MOUSE_RELEASED
                        && mouseEvent.getButton() == MouseEvent.BUTTON1) {
                    handleMouseClick(mouseEvent);
                }
            }
            return false; // don't consume the event
        };

        IdeEventQueue.getInstance().addDispatcher(eventDispatcher, this);
        LOG.info("GenAiMouseClickListener — registered successfully for project: " + project.getName());
    }

    private void handleMouseClick(MouseEvent event) {
        Component source = event.getComponent();
        if (source == null) {
            LOG.debug("GenAiMouseClickListener.handleMouseClick — source component is null, ignoring");
            return;
        }

        // SAFETY: Never trigger on single/double click in the code editor window
        // This ensures that clicks in the code editor area (including caret/selection) are always ignored and cannot trigger LOC/service logic
        if (isEditorComponent(source)) {
            LOG.debug("GenAiMouseClickListener.handleMouseClick — click inside editor text area (" + source.getClass().getName() + "), ignoring");
            return;
        }

        // Get the deepest component at the click point
        Component target = source;
        if (source instanceof Container container) {
            Point point = event.getPoint();
            LOG.debug("GenAiMouseClickListener.handleMouseClick — click at x=" + point.x + " y=" + point.y
                    + " on container=" + container.getClass().getSimpleName());
            Component deep = SwingUtilities.getDeepestComponentAt(container, point.x, point.y);
            if (deep != null) {
                // Extra safety: check if deepest component is editor
                if (isEditorComponent(deep)) {
                    LOG.debug("GenAiMouseClickListener.handleMouseClick — click inside editor text area (deepest: " + deep.getClass().getName() + "), ignoring");
                    return;
                }
                target = deep;
                LOG.debug("GenAiMouseClickListener.handleMouseClick — deepest component=" + deep.getClass().getSimpleName());
            }
        }

        // Extract text from the clicked component
        String componentText = extractComponentText(target);
        if (componentText == null || componentText.isBlank()) {
            LOG.debug("GenAiMouseClickListener.handleMouseClick — no text extracted from component="
                    + target.getClass().getSimpleName() + ", ignoring");
            return;
        }

        String lowerText = componentText.toLowerCase().trim();
        LOG.debug("GenAiMouseClickListener — click detected on component text: '" + lowerText + "'");

        // Check if the text matches any accept/keep keyword
        boolean isAcceptClick = ACCEPT_KEYWORDS.stream().anyMatch(keyword ->
                lowerText.equals(keyword) || lowerText.contains(keyword));

        if (!isAcceptClick) {
            return;
        }

        LOG.info("GenAiMouseClickListener — ACCEPT/KEEP click detected! text='" + componentText + "'");

        // Debounce — avoid double-processing
        if (!processing.compareAndSet(false, true)) {
            LOG.info("GenAiMouseClickListener — already processing, skipping");
            return;
        }

        // Wait briefly for document changes to propagate, then drain PendingLocEvent
        scheduler.schedule(() -> {
            try {
                drainPendingEvents(componentText);
            } finally {
                processing.set(false);
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    /**
     * Extract readable text from a Swing component.
     * Checks: JButton text, JLabel text, AbstractButton text, tooltip, accessible name.
     */
    private String extractComponentText(Component component) {
        LOG.debug("GenAiMouseClickListener.extractComponentText — inspecting component=" + component.getClass().getSimpleName());
        if (component instanceof AbstractButton button) {
            String text = button.getText();
            if (text != null && !text.isBlank()) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found AbstractButton text='" + text + "'");
                return text;
            }
            // Fallback to tooltip
            if (button.getToolTipText() != null) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found AbstractButton tooltip='" + button.getToolTipText() + "'");
                return button.getToolTipText();
            }
        }
        if (component instanceof JLabel label) {
            String text = label.getText();
            if (text != null && !text.isBlank()) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found JLabel text='" + text + "'");
                return text;
            }
        }
        if (component instanceof JComponent jc) {
            // Check accessible context
            if (jc.getAccessibleContext() != null) {
                String accName = jc.getAccessibleContext().getAccessibleName();
                if (accName != null && !accName.isBlank()) {
                    LOG.debug("GenAiMouseClickListener.extractComponentText — found accessibleName='" + accName + "'");
                    return accName;
                }
                String accDesc = jc.getAccessibleContext().getAccessibleDescription();
                if (accDesc != null && !accDesc.isBlank()) {
                    LOG.debug("GenAiMouseClickListener.extractComponentText — found accessibleDescription='" + accDesc + "'");
                    return accDesc;
                }
            }
            // Check tooltip
            String tooltip = jc.getToolTipText();
            if (tooltip != null && !tooltip.isBlank()) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found JComponent tooltip='" + tooltip + "'");
                return tooltip;
            }
        }

        // Walk up to parent to check (e.g., icon inside a button)
        Component parent = component.getParent();
        if (parent instanceof AbstractButton button) {
            String text = button.getText();
            if (text != null && !text.isBlank()) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found parent AbstractButton text='" + text + "'");
                return text;
            }
            if (button.getToolTipText() != null) {
                LOG.debug("GenAiMouseClickListener.extractComponentText — found parent AbstractButton tooltip='" + button.getToolTipText() + "'");
                return button.getToolTipText();
            }
        }

        LOG.debug("GenAiMouseClickListener.extractComponentText — no text found for component=" + component.getClass().getSimpleName());
        return null;
    }

    /**
     * Drains all pending LOC events and sends them to the backend.
     */
    private void drainPendingEvents(String triggerText) {
        LOG.info("GenAiMouseClickListener.drainPendingEvents — START triggered by: '" + triggerText + "'");
        // PendingLocEvent.hasPending requires a filePath; check via consumeLatest
        LOCRequestPayload event = PendingLocEvent.consumeLatest();
        if (event == null) {
            LOG.info("GenAiMouseClickListener.drainPendingEvents — no pending LOC events to drain after click: " + triggerText);
            return;
        }

        LOG.info("GenAiMouseClickListener.drainPendingEvents — found pending event(s), draining...");

        int sent = 0;
        while (event != null) {
            event.setAcceptedLocation("MOUSE_CLICK:" + triggerText.toUpperCase().trim());
            LOG.info("GenAiMouseClickListener.drainPendingEvents — processing event: file=" + event.getFileName()
                    + " +lines=" + event.getLinesAdded() + " ~lines=" + event.getLinesModified()
                    + " acceptedLocation=" + event.getAcceptedLocation());
            Project project = findActiveProject();
            if (project != null) {
                LOG.info("GenAiMouseClickListener.drainPendingEvents — using project=" + project.getName());
                GenAiLocProjectService service = project.getService(GenAiLocProjectService.class);
                if (service != null) {
                    service.enqueue(event);
                    service.flushNow();
                    sent++;
                    LOG.info("GenAiMouseClickListener.drainPendingEvents — SENT event: file=" + event.getFileName()
                            + " +lines=" + event.getLinesAdded() + " totalSent=" + sent);
                } else {
                    LOG.warn("GenAiMouseClickListener.drainPendingEvents — GenAiLocProjectService is NULL for project=" + project.getName());
                }
            } else {
                LOG.warn("GenAiMouseClickListener.drainPendingEvents — no active project found, event dropped: file=" + event.getFileName());
            }
            event = PendingLocEvent.consumeLatest();
        }

        LOG.info("GenAiMouseClickListener.drainPendingEvents — END: sent=" + sent + " from trigger='" + triggerText + "'");
    }

    private Project findActiveProject() {
        LOG.debug("GenAiMouseClickListener.findActiveProject — searching open projects");
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (!p.isDisposed() && !p.isDefault()) {
                LOG.debug("GenAiMouseClickListener.findActiveProject — found project=" + p.getName());
                return p;
            }
        }
        LOG.warn("GenAiMouseClickListener.findActiveProject — no active non-default project found");
        return null;
    }

    @Override
    public void dispose() {
        LOG.info("GenAiMouseClickListener — disposing");
        scheduler.shutdown();
    }

    /**
     * Returns true if the component is part of the main code editor window (not a diff/merge/GenAI tool window).
     */
    private static boolean isEditorComponent(Component comp) {
        if (comp == null) return false;
        String cls = comp.getClass().getName().toLowerCase();
        // Covers all known editor component types in IntelliJ
        if (cls.contains("editorcomponent") || cls.contains("editorimpl") || cls.contains("jtextarea") || cls.contains("jeditorpane") || cls.contains("editorwindow")) {
            return true;
        }
        // Walk up parent chain to check if any parent is an editor
        Component parent = comp.getParent();
        int depth = 0;
        while (parent != null && depth < 8) {
            String pcls = parent.getClass().getName().toLowerCase();
            if (pcls.contains("editorcomponent") || pcls.contains("editorimpl") || pcls.contains("jtextarea") || pcls.contains("jeditorpane") || pcls.contains("editorwindow")) {
                return true;
            }
            parent = parent.getParent();
            depth++;
        }
        return false;
    }
}
