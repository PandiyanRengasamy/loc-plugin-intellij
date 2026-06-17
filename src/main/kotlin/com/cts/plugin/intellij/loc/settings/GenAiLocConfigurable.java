package com.cts.plugin.intellij.loc.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * GenAiLocConfigurable
 *
 * Provides the Settings UI panel under File > Settings > Tools > GenAI LOC Tracker.
 * All fields bind directly to GenAiLocSettings.
 */
public class GenAiLocConfigurable implements Configurable {

    private static final Logger LOG = Logger.getInstance(GenAiLocConfigurable.class);

    private JPanel       rootPanel;
    private JTextField   backendUrlField;
    private JTextField   developerIdField;
    private JTextField   developerNameField;
    private JTextField   projectIdField;
    private JTextField   sprintIdField;
    private JComboBox<String> genAiToolCombo;
    private JCheckBox    enabledCheckBox;
    private JSpinner     batchSizeSpinner;
    private JSpinner     flushIntervalSpinner;
    private JSpinner     genAiThresholdSpinner;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "GenAI LOC Tracker";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        LOG.info("GenAiLocConfigurable: creating settings UI panel");
        rootPanel = new JPanel(new BorderLayout(10, 10));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill    = GridBagConstraints.HORIZONTAL;
        c.insets  = new Insets(4, 4, 4, 4);
        c.weightx = 1.0;

        // ── Connection Section ────────────────────────────────────────────
        addSectionTitle(formPanel, "Backend Connection", c, 0);

        backendUrlField = new JTextField();
        addRow(formPanel, "Backend URL:", backendUrlField, c, 1);

        // ── Identity Section ──────────────────────────────────────────────
        addSectionTitle(formPanel, "Developer Identity  (leave blank to auto-detect from Git)", c, 2);

        developerIdField   = new JTextField();
        developerNameField = new JTextField();
        addRow(formPanel, "Developer ID (email):", developerIdField,   c, 3);
        addRow(formPanel, "Developer Name:",       developerNameField, c, 4);

        // ── Project Section ───────────────────────────────────────────────
        addSectionTitle(formPanel, "Project", c, 5);

        projectIdField = new JTextField();
        sprintIdField  = new JTextField();
        addRow(formPanel, "Project ID:",  projectIdField, c, 6);
        addRow(formPanel, "Sprint ID:",   sprintIdField,  c, 7);

        // ── Tracking Section ──────────────────────────────────────────────
        addSectionTitle(formPanel, "Tracking", c, 8);

        genAiToolCombo = new JComboBox<>(getStrings());
        enabledCheckBox = new JCheckBox("Enable LOC tracking");
        addRow(formPanel, "Default GenAI Tool:", genAiToolCombo,   c, 9);
        addRow(formPanel, "",                    enabledCheckBox,  c, 10);

        batchSizeSpinner      = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        flushIntervalSpinner  = new JSpinner(new SpinnerNumberModel(30, 5, 300, 5));
        genAiThresholdSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        addRow(formPanel, "Batch Size (events):",      batchSizeSpinner,      c, 11);
        addRow(formPanel, "Flush Interval (seconds):", flushIntervalSpinner,  c, 12);
        addRow(formPanel, "GenAI Line Threshold:",     genAiThresholdSpinner, c, 13);

        // Filler to push controls to the top
        c.gridx = 0; c.gridy = 14; c.gridwidth = 2; c.weighty = 1.0;
        formPanel.add(new JPanel(), c);

        rootPanel.add(formPanel, BorderLayout.CENTER);
        reset();
        return rootPanel;
    }

    private static String @NotNull [] getStrings() {
        return new String[]{
                "COPILOT", "CHATGPT", "CODE_ASSIST", "TABNINE", "CODEWHISPERER", "OTHER"
        };
    }

    @Override
    public boolean isModified() {
        GenAiLocSettings s = GenAiLocSettings.getInstance();
        boolean modified = !backendUrlField.getText().equals(s.getBackendUrl())
                || !developerIdField.getText().equals(s.getDeveloperId())
                || !developerNameField.getText().equals(s.getDeveloperName())
                || !projectIdField.getText().equals(s.getProjectId())
                || !sprintIdField.getText().equals(s.getSprintId())
                || !genAiToolCombo.getSelectedItem().equals(s.getDefaultGenAiTool())
                || enabledCheckBox.isSelected() != s.isEnabled()
                || (int) batchSizeSpinner.getValue()      != s.getBatchSize()
                || (int) flushIntervalSpinner.getValue()  != s.getFlushIntervalSeconds()
                || (int) genAiThresholdSpinner.getValue() != s.getGenAiLineThreshold();
        LOG.debug("GenAiLocConfigurable: isModified -> " + modified);
        return modified;
    }

    @Override
    public void apply() {
        LOG.info("GenAiLocConfigurable: applying settings changes");
        GenAiLocSettings s = GenAiLocSettings.getInstance();
        s.setBackendUrl(backendUrlField.getText().trim());
        s.setDeveloperId(developerIdField.getText().trim());
        s.setDeveloperName(developerNameField.getText().trim());
        s.setProjectId(projectIdField.getText().trim());
        s.setSprintId(sprintIdField.getText().trim());
        s.setDefaultGenAiTool((String) genAiToolCombo.getSelectedItem());
        s.setEnabled(enabledCheckBox.isSelected());
        s.setBatchSize((int) batchSizeSpinner.getValue());
        s.setFlushIntervalSeconds((int) flushIntervalSpinner.getValue());
        s.setGenAiLineThreshold((int) genAiThresholdSpinner.getValue());
        LOG.info("GenAiLocConfigurable: settings applied -> " +
                "backendUrl=" + s.getBackendUrl() +
                ", developerId=" + s.getDeveloperId() +
                ", developerName=" + s.getDeveloperName() +
                ", projectId=" + s.getProjectId() +
                ", sprintId=" + s.getSprintId() +
                ", genAiTool=" + s.getDefaultGenAiTool() +
                ", enabled=" + s.isEnabled() +
                ", batchSize=" + s.getBatchSize() +
                ", flushInterval=" + s.getFlushIntervalSeconds() +
                ", genAiThreshold=" + s.getGenAiLineThreshold());
    }

    @Override
    public void reset() {
        LOG.info("GenAiLocConfigurable: resetting UI fields from stored settings");
        GenAiLocSettings s = GenAiLocSettings.getInstance();
        backendUrlField.setText(s.getBackendUrl());
        developerIdField.setText(s.getDeveloperId());
        developerNameField.setText(s.getDeveloperName());
        projectIdField.setText(s.getProjectId());
        sprintIdField.setText(s.getSprintId());
        genAiToolCombo.setSelectedItem(s.getDefaultGenAiTool());
        enabledCheckBox.setSelected(s.isEnabled());
        batchSizeSpinner.setValue(s.getBatchSize());
        flushIntervalSpinner.setValue(s.getFlushIntervalSeconds());
        genAiThresholdSpinner.setValue(s.getGenAiLineThreshold());
        LOG.debug("GenAiLocConfigurable: reset complete -> developerID=" + s.getDeveloperId() +
                ", genAiTool=" + s.getDefaultGenAiTool() +
                ", enabled=" + s.isEnabled());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addSectionTitle(JPanel p, String title, GridBagConstraints c, int row) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weighty = 0;
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        p.add(label, c);
        c.gridwidth = 1;
    }

    private void addRow(JPanel p, String labelText, JComponent field, GridBagConstraints c, int row) {
        c.gridx = 0; c.gridy = row; c.weightx = 0.2;
        p.add(new JLabel(labelText), c);
        c.gridx = 1; c.weightx = 0.8;
        p.add(field, c);
    }
}