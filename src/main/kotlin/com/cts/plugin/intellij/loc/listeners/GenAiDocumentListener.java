package com.cts.plugin.intellij.loc.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NotNull;

/**
 * GenAiDocumentListener — EditorFactoryListener
 *
 * Fires when any Editor is created in IntelliJ IDEA.
 * Attaches a {@link GenAiDocumentListenerImpl} to each new editor's document so
 * that every document change is analysed for GenAI-generated code (Copilot,
 * Claude, ChatGPT, Gemini, etc.).
 *
 * Registered in plugin.xml as an applicationListener for EditorFactoryListener.
 */
public class GenAiDocumentListener implements EditorFactoryListener {

    private static final Logger LOG = Logger.getInstance(GenAiDocumentListener.class);

    /**
     * Called by IntelliJ when a new Editor window is created.
     * Attaches a {@link GenAiDocumentListenerImpl} to the editor's document.
     * The listener is automatically removed when the editor is released.
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        String projectName = editor.getProject() != null ? editor.getProject().getName() : "<no-project>";
        LOG.info("GenAiDocumentListener.editorCreated: attaching GenAiDocumentListenerImpl"
                + " project=" + projectName
                + " editorClass=" + editor.getClass().getSimpleName());

        // Attach our full per-tool detection listener to this editor's document.
        // Editor itself implements Disposable — passing it as the parent Disposable
        // ensures the document listener is automatically removed when the editor closes (no memory leak).
        editor.getDocument().addDocumentListener(
                new GenAiDocumentListenerImpl(editor),
                (Disposable) editor
        );

        LOG.info("GenAiDocumentListener: ✅ GenAiDocumentListenerImpl attached — project=" + projectName);
    }
}