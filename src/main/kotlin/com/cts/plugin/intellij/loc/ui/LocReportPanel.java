package com.cts.plugin.intellij.loc.ui;

import com.cts.plugin.intellij.loc.settings.GenAiLocSettings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Swing panel displayed inside the "GenAI LOC Report" tool window.
 *
 * Features:
 * <ul>
 *   <li>Filter toolbar: View By (Developer / Project / Developer+Project), date range</li>
 *   <li>Summary cards: Total Events, GenAI/Manual Events, Lines +/~/−, Adoption %, File counts</li>
 *   <li>Events table: last 50 events with all LOC details</li>
 *   <li>Auto-refresh toggle (30 s interval)</li>
 *   <li>Data loads only on Refresh button click (not on panel open)</li>
 * </ul>
 */
public class LocReportPanel {

    private static final Logger LOG = Logger.getInstance(LocReportPanel.class);
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final Project project;
    private final JPanel mainPanel;

    // ── Filter controls ───────────────────────────────────────────────────────
    private final com.intellij.openapi.ui.ComboBox<String> cmbViewBy = new com.intellij.openapi.ui.ComboBox<>(new String[]{
            "Developer", "Project", "Developer + Project"
    });
    private final com.intellij.openapi.ui.ComboBox<String> cmbDateRange = new com.intellij.openapi.ui.ComboBox<>(new String[]{
            "Last 7 days", "Last 14 days", "Last 30 days", "Last 90 days", "All time"
    });
    private final JCheckBox chkAutoRefresh = new JCheckBox("Auto-refresh (30s)");
    private Timer autoRefreshTimer;
    // ── Summary labels and controls ───────────────────────────────────────────
    private final JBLabel lblTotalEvents       = new JBLabel("—");
    private final JBLabel lblGenAiEvents       = new JBLabel("—");
    private final JBLabel lblManualEvents      = new JBLabel("—");
    private final JBLabel lblLinesAdded        = new JBLabel("—");
    private final JBLabel lblLinesModified     = new JBLabel("—");
    private final JBLabel lblLinesDeleted      = new JBLabel("—");
    private final JBLabel lblGenAiAdoption     = new JBLabel("—");
    private final JBLabel lblHumanContribution = new JBLabel("—");
    private final JBLabel lblTotalFilesUpdated = new JBLabel("—");
    private final JBLabel lblTotalFilesAdded   = new JBLabel("—");
    private final JBLabel lblTotalFilesDeleted = new JBLabel("—");
    private final JBLabel lblTotalInputTokens  = new JBLabel("—");
    private final JBLabel lblTotalOutputTokens = new JBLabel("—");
    private final JBLabel lblStatus            = new JBLabel("Click Refresh to load data");
    // Human LOC % input
    // Per-row Human LOC %
    private final JTextField txtHumanLocPercent = new JTextField(5);
    private final JButton btnSaveHumanLoc = new JButton("Save");
    private int selectedRow = -1;
    // Map: row index -> Human LOC % value
    private final Map<Integer, Double> humanLocPercentMap = new HashMap<>();

    // ── Events table ───────────────────���──────────────────────────────────────
    // Add event ID as the first (hidden) column
    private final String[] COLUMNS = {
            "ID", "Timestamp", "File", "Tool", "Lines +", "Lines ~", "Lines −",
            "GenAI", "Model", "Agent", "Location",
            "Files Updated", "Files Added", "Files Deleted",
            "Human LOC %", "Human LOC", "GenAI LOC",
            "Input Tokens", "Output Tokens"
    };
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JBTable eventsTable = new JBTable(tableModel);

    public LocReportPanel(Project project) {
        this.project = project;
        this.mainPanel = buildUI();
        // Data is NOT loaded automatically — user clicks Refresh
        // Add row selection listener for per-row Human LOC %
        // Track previous row for focus loss logic
        final int[] prevRow = { -1 };
        eventsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int newRow = eventsTable.getSelectedRow();
                // Save value for previous row if needed
                if (prevRow[0] >= 0) {
                    saveHumanLocPercentForRow(prevRow[0]);
                }
                selectedRow = newRow;
                if (selectedRow >= 0) {
                    txtHumanLocPercent.setEnabled(true);
                    btnSaveHumanLoc.setEnabled(true);
                    // Prefill with existing value if available
                    Double val = humanLocPercentMap.get(selectedRow);
                    txtHumanLocPercent.setText(val != null ? String.valueOf(val) : "");
                } else {
                    txtHumanLocPercent.setEnabled(false);
                    btnSaveHumanLoc.setEnabled(false);
                    txtHumanLocPercent.setText("");
                }
                prevRow[0] = selectedRow;
            }
        });
        txtHumanLocPercent.setEnabled(false);
        btnSaveHumanLoc.setEnabled(false);
        // Focus loss: save value for current row
        txtHumanLocPercent.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (selectedRow >= 0) {
                    saveHumanLocPercentForRow(selectedRow);
                }
            }
        });
    }

    public JComponent getContent() {
        return mainPanel;
    }

    // ── UI Construction ──���──────────────────��───────────��─────────────────────

    private JPanel buildUI() {
        // Main vertical panel to hold everything
        JPanel verticalPanel = new JPanel();
        verticalPanel.setLayout(new BoxLayout(verticalPanel, BoxLayout.Y_AXIS));

        // ── Filter toolbar (top-most) ─────────────────────────────────────────
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.setBorder(JBUI.Borders.empty(4, 10, 0, 10));

        filterBar.add(new JBLabel("View by:"));
        cmbViewBy.setSelectedIndex(0);
        filterBar.add(cmbViewBy);

        filterBar.add(Box.createHorizontalStrut(12));
        filterBar.add(new JBLabel("Date range:"));
        cmbDateRange.setSelectedIndex(2); // default: Last 30 days
        filterBar.add(cmbDateRange);

        filterBar.add(Box.createHorizontalStrut(12));
        chkAutoRefresh.setSelected(false);
        chkAutoRefresh.addActionListener(e -> toggleAutoRefresh());
        filterBar.add(chkAutoRefresh);
        verticalPanel.add(filterBar);

        // ── Summary cards ────────────────���───────────────────��────────────��───
        JPanel summaryPanel = new JPanel(new GridLayout(3, 5, 10, 6));
        summaryPanel.setBorder(JBUI.Borders.empty(6, 10, 10, 10));

        summaryPanel.add(createCard("Total Events",    lblTotalEvents));
        summaryPanel.add(createCard("GenAI Events",    lblGenAiEvents));
        summaryPanel.add(createCard("Manual Events",   lblManualEvents));
        summaryPanel.add(createCard("Lines Added",     lblLinesAdded));
        summaryPanel.add(createCard("Lines Modified",  lblLinesModified));

        summaryPanel.add(createCard("Lines Deleted",   lblLinesDeleted));
        summaryPanel.add(createCard("GenAI Adoption",  lblGenAiAdoption));
        summaryPanel.add(createCard("Human Contribution %", lblHumanContribution));
        summaryPanel.add(createCard("Files Updated",   lblTotalFilesUpdated));
        summaryPanel.add(createCard("Files Added",     lblTotalFilesAdded));

        summaryPanel.add(createCard("Files Deleted",   lblTotalFilesDeleted));
        summaryPanel.add(createCard("Input Tokens",    lblTotalInputTokens));
        summaryPanel.add(createCard("Output Tokens",   lblTotalOutputTokens));
        // Fill remaining cells for grid alignment
        for (int i = 0; i < 2; i++) summaryPanel.add(new JLabel(""));
        verticalPanel.add(summaryPanel);

        // ── Human LOC controls ──────────��─────────────────────────────────���───
        JPanel rowHumanLocPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rowHumanLocPanel.add(new JLabel("Human LOC % for selected row:"));
        txtHumanLocPercent.setToolTipText("Enter human-written LOC percent (0-100)");
        rowHumanLocPanel.add(txtHumanLocPercent);
        rowHumanLocPanel.add(btnSaveHumanLoc);
        btnSaveHumanLoc.addActionListener(e -> saveAllHumanLocPercents());
        verticalPanel.add(rowHumanLocPanel);

        // ── Download and Refresh buttons ─────────────────────────────────────--
        JPanel downloadPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton refreshBtn = new JButton("↻ Refresh");
        JButton btnDownload = new JButton("Download CSV");
        downloadPanel.add(refreshBtn);
        downloadPanel.add(btnDownload);
        verticalPanel.add(downloadPanel);
        refreshBtn.addActionListener(e -> refreshData());
        btnDownload.addActionListener(e -> downloadTableDataAsCSV());

        // ── Events table in scroll pane (with horizontal scroll) ────────────────
        eventsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        eventsTable.setRowHeight(24);
        eventsTable.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 3; i <= 5; i++) eventsTable.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        for (int i = 10; i <= 12; i++) eventsTable.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        com.intellij.ui.components.JBScrollPane tableScrollPane = new com.intellij.ui.components.JBScrollPane(eventsTable);
        tableScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        verticalPanel.add(tableScrollPane);

        // ── Status controls ───────────────────────────────────────────────────
        JPanel southPanel = new JPanel(new BorderLayout());
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(JBUI.Borders.emptyLeft(10));
        statusPanel.add(lblStatus, BorderLayout.CENTER);
        southPanel.add(statusPanel, BorderLayout.CENTER);
        southPanel.setBorder(JBUI.Borders.empty(4, 10));
        verticalPanel.add(southPanel);

        // ── Wrap in a scroll pane for vertical scrolling ──────────────────────
        JScrollPane scrollPane = new com.intellij.ui.components.JBScrollPane(verticalPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        return mainPanel;
    }
    // Save the human LOC percent for a given row (on focus loss or row change)
    private void saveHumanLocPercentForRow(int row) {
        String humLocValue = txtHumanLocPercent.getText().trim();
        if (row < 0 || humLocValue.isEmpty()) {
            LOG.info("Row " + row + ": No Human LOC % to save (empty input or no row selected)");
            return;
        }
        try {
            double percent = Double.parseDouble(humLocValue);
            if (percent < 0 || percent > 100) {
                if (row == selectedRow) {
                    JOptionPane.showMessageDialog(mainPanel, "Please enter a value between 0 and 100.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                }
                return;
            }
            humanLocPercentMap.put(row, percent);
            // Update Human LOC % column in table
            if (row < tableModel.getRowCount()) {
                tableModel.setValueAt(percent, row, COL_HUMAN_LOC_PCT);
            }
        } catch (NumberFormatException ex) {
            if (row == selectedRow) {
                JOptionPane.showMessageDialog(mainPanel, "Please enter a valid number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Save all Human LOC % values for all rows (batch save)
    private void saveAllHumanLocPercents() {
        LOG.info("Save Human LOC % — selected row: " + selectedRow + ", current input: '" + txtHumanLocPercent.getText().trim() + "'");
        // Commit any in-progress edits in the Human LOC % text field
        if (eventsTable.isEditing()) {
            eventsTable.getCellEditor().stopCellEditing();
        }
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(mainPanel, "No row selected.", "Nothing to Save", JOptionPane.INFORMATION_MESSAGE);
            LOG.info("No row selected when attempting to save Human LOC %");
            return;
        }
        String humLocValue = txtHumanLocPercent.getText().trim();
        LOG.info("Attempting to save Human LOC % for row " + selectedRow + ": input value = '" + humLocValue + "'");
        if (humLocValue.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Please enter a Human LOC % value before saving.", "Missing Value", JOptionPane.WARNING_MESSAGE);
            LOG.info("Row " + selectedRow + ": No Human LOC % to save (empty input or no row selected)");
            return;
        }
        double percent;
        try {
            percent = Double.parseDouble(humLocValue);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(mainPanel, "Please enter a valid number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            LOG.info("Row " + selectedRow + ": Invalid number format: '" + humLocValue + "'");
            return;
        }
        if (percent < 0 || percent > 100) {
            JOptionPane.showMessageDialog(mainPanel, "Please enter a value between 0 and 100.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            LOG.info("Row " + selectedRow + ": Invalid Human LOC % value: " + percent);
            return;
        }
        // Get event ID from table
        String eventId = (String) tableModel.getValueAt(selectedRow, 0); // ID is now column 0
        LOG.info("Tables Selected Row's Calling backend API for eventId=" + eventId + ", percent=" + percent);
        boolean ok = sendHumanLocPercentToBackend(eventId, percent);
        if (ok) {
            // Update Human LOC % column in table
            if (selectedRow < tableModel.getRowCount()) {
                tableModel.setValueAt(percent, selectedRow, COL_HUMAN_LOC_PCT);
            }
            lblStatus.setText("Saved Human LOC % value.");
        } else {
            lblStatus.setText("Failed to save Human LOC % value.");
        }
        // Optionally update summary/adoption
        updateSummaryFromTable();
        // Clear UI
        txtHumanLocPercent.setText("");
        eventsTable.clearSelection();
        txtHumanLocPercent.setEnabled(false);
        btnSaveHumanLoc.setEnabled(false);

        // Refresh table from backend to verify persistence
        refreshData();
    }

    /**
     * Send the Human LOC % value for a file/timestamp to the backend.
     * Returns true if successful, false otherwise.
     */
    // Now uses event ID and correct endpoint/method
    private boolean sendHumanLocPercentToBackend(String eventId, double percent) {
        try {
            GenAiLocSettings settings = GenAiLocSettings.getInstance();
            String baseUrl = settings.getBackendUrl().replace("/events", "");
            // Compose the endpoint: /events/{id}/human-loc
            String url = baseUrl + "/events/" + eventId + "/human-loc";
            LOG.info("Sending Human LOC % to backend: eventId=" + eventId + ", percent=" + percent + ", url=" + url);
            JsonObject payload = new JsonObject();
            payload.addProperty("humanLocPercent", percent);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return true;
            } else {
                LOG.warn("Failed to save Human LOC %: HTTP " + resp.statusCode() + ": " + resp.body());
                return false;
            }
        } catch (Exception ex) {
            LOG.warn("Failed to save Human LOC %: " + ex.getMessage());
            return false;
        }
    }

    // Column index constants derived from COLUMNS array
    private static final int COL_GENAI_FLAG     = 7;   // "GenAI"        → "Yes"/"No"
    private static final int COL_LINES_ADDED    = 4;   // "Lines +"
    private static final int COL_LINES_MODIFIED = 5;   // "Lines ~"
    private static final int COL_LINES_DELETED  = 6;   // "Lines −"
    private static final int COL_FILES_UPDATED  = 11;  // "Files Updated"
    private static final int COL_FILES_ADDED    = 12;  // "Files Added"
    private static final int COL_FILES_DELETED  = 13;  // "Files Deleted"
    private static final int COL_HUMAN_LOC_PCT  = 14;  // "Human LOC %"
    private static final int COL_HUMAN_LOC      = 15;  // "Human LOC"
    private static final int COL_GENAI_LOC      = 16;  // "GenAI LOC"
    private static final int COL_INPUT_TOKENS   = 17;  // "Input Tokens"
    private static final int COL_OUTPUT_TOKENS  = 18;  // "Output Tokens"

    /**
     * Recomputes ALL summary cards by reading directly from the table model.
     * Must be called on the EDT after rows have been populated.
     */
    private void updateSummaryFromTable() {
        int rowCount = tableModel.getRowCount();
        int genAiEvents = 0, manualEvents = 0;
        int totalLinesAdded = 0, totalLinesModified = 0, totalLinesDeleted = 0;
        int totalFilesUpdated = 0, totalFilesAdded = 0, totalFilesDeleted = 0;
        int totalInputTokens = 0, totalOutputTokens = 0;
        double totalHumanLoc = 0.0, totalGenAiLoc = 0.0;

        for (int i = 0; i < rowCount; i++) {
            if ("Yes".equals(tableModel.getValueAt(i, COL_GENAI_FLAG))) genAiEvents++;
            else manualEvents++;

            totalLinesAdded    += parseIntCell(tableModel.getValueAt(i, COL_LINES_ADDED));
            totalLinesModified += parseIntCell(tableModel.getValueAt(i, COL_LINES_MODIFIED));
            totalLinesDeleted  += parseIntCell(tableModel.getValueAt(i, COL_LINES_DELETED));

            totalFilesUpdated += parseIntCell(tableModel.getValueAt(i, COL_FILES_UPDATED));
            totalFilesAdded   += parseIntCell(tableModel.getValueAt(i, COL_FILES_ADDED));
            totalFilesDeleted += parseIntCell(tableModel.getValueAt(i, COL_FILES_DELETED));

            totalInputTokens  += parseIntCell(tableModel.getValueAt(i, COL_INPUT_TOKENS));
            totalOutputTokens += parseIntCell(tableModel.getValueAt(i, COL_OUTPUT_TOKENS));

            totalHumanLoc += Math.max(0, parseDoubleCell(tableModel.getValueAt(i, COL_HUMAN_LOC)));
            totalGenAiLoc += Math.max(0, parseDoubleCell(tableModel.getValueAt(i, COL_GENAI_LOC)));
        }

        double totalLoc = totalHumanLoc + totalGenAiLoc;
        double adoptionPct = totalLoc > 0 ? Math.max(0, Math.min(100, (totalGenAiLoc / totalLoc) * 100.0)) : 0.0;
        double humanPct    = totalLoc > 0 ? Math.max(0, Math.min(100, (totalHumanLoc / totalLoc) * 100.0)) : 0.0;

        lblTotalEvents.setText(String.valueOf(rowCount));
        lblGenAiEvents.setText(String.valueOf(genAiEvents));
        lblManualEvents.setText(String.valueOf(manualEvents));
        lblLinesAdded.setText(String.valueOf(totalLinesAdded));
        lblLinesModified.setText(String.valueOf(totalLinesModified));
        lblLinesDeleted.setText(String.valueOf(totalLinesDeleted));
        lblTotalFilesUpdated.setText(String.valueOf(totalFilesUpdated));
        lblTotalFilesAdded.setText(String.valueOf(totalFilesAdded));
        lblTotalFilesDeleted.setText(String.valueOf(totalFilesDeleted));
        lblTotalInputTokens.setText(String.valueOf(totalInputTokens));
        lblTotalOutputTokens.setText(String.valueOf(totalOutputTokens));
        lblGenAiAdoption.setText(String.format("%.1f%%", adoptionPct));
        lblHumanContribution.setText(String.format("%.1f%%", humanPct));
    }

    private static int parseIntCell(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString().trim()); } catch (Exception ignored) { return 0; }
    }

    private static double parseDoubleCell(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString().trim()); } catch (Exception ignored) { return 0.0; }
    }

    private JPanel createCard(String title, JBLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(6, 10)
        ));

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setForeground(JBColor.GRAY);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 18f));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // ── Auto-refresh toggle ─────────────────────���───���─────────────────────────

    private void toggleAutoRefresh() {
        if (chkAutoRefresh.isSelected()) {
            autoRefreshTimer = new Timer(30_000, e -> refreshData());
            autoRefreshTimer.setRepeats(true);
            autoRefreshTimer.start();
            LOG.info("GenAI LOC Report — auto-refresh ON (30s)");
        } else {
            if (autoRefreshTimer != null) {
                autoRefreshTimer.stop();
                autoRefreshTimer = null;
            }
            LOG.info("GenAI LOC Report — auto-refresh OFF");
        }
    }

    // ── Date range helper ─────────────────────────────────────��──────────────��

    private int getDateRangeDays() {
        String selected = (String) cmbDateRange.getSelectedItem();
        if (selected == null) return 30;
        return switch (selected) {
            case "Last 7 days"  -> 7;
            case "Last 14 days" -> 14;
            case "Last 30 days" -> 30;
            case "Last 90 days" -> 90;
            case "All time"     -> 3650;
            default             -> 30;
        };
    }

    // ── Data Loading ──────────────────────────────────────────────────────────

    private void refreshData() {
        LOG.info("Refreshing GenAI LOC Report data for project: " + project.getName());
        lblStatus.setText("Loading...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                GenAiLocSettings settings = GenAiLocSettings.getInstance();
                String baseUrl = settings.getBackendUrl().replace("/events", "");  // → /api/v1/genai-loc
                String developerId = settings.getDeveloperId();
                String projectId   = settings.getProjectId().isBlank() ? project.getName() : settings.getProjectId();

                int days = getDateRangeDays();
                String to   = LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "T23:59:59";
                String from = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_DATE) + "T00:00:00";

                String viewBy = (String) cmbViewBy.getSelectedItem();
                LOG.info("LOC Refresh btn - Fetch data with viewBy=" + viewBy + ", developerId=" + developerId + ", projectId=" + projectId + ", from=" + from + ", to=" + to);

                // Only fetch events, summary will be calculated from events
                loadEvents(baseUrl, viewBy, developerId, projectId, from, to);

                String filterInfo = buildFilterInfo(viewBy, developerId, projectId, days);
                LOG.info("LOC Refresh btn  - GenAI LOC Report — data refreshed successfully: " + filterInfo);
                SwingUtilities.invokeLater(() -> lblStatus.setText(
                        "Last refreshed: " + java.time.LocalTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        + "  |  " + filterInfo));

            } catch (Exception ex) {
                LOG.warn("GenAI LOC Report — refresh failed: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(() ->
                        lblStatus.setText("⚠ Error: " + ex.getMessage()));
            }
        });
    }

    private String buildFilterInfo(String viewBy, String devId, String projId, int days) {
        String range = days >= 3650 ? "All time" : "Last " + days + " days";
        return switch (viewBy) {
            case "Project"              -> "Project: " + projId + "  |  " + range;
            case "Developer + Project"  -> "Developer: " + devId + "  |  Project: " + projId + "  |  " + range;
            default                     -> "Developer: " + devId + "  |  " + range;
        };
    }

    private void loadEvents(String baseUrl, String viewBy, String devId, String projId,
                            String from, String to) {
        try {
            String url = getURL(baseUrl, viewBy, devId, projId, from, to);
            LOG.info("GenAi-LOC: Refresh btn - Fetching events from backend with URL: " + url);
            String json = httpGet(url);
            LOG.info("GenAi-LOC: Refresh btn - Raw JSON response for events: " + json);
            JsonArray content = GSON.fromJson(json, JsonArray.class);

            java.util.List<Object[]> rows = new java.util.ArrayList<>();

            for (int i = 0; i < content.size(); i++) {
                JsonObject resJs = content.get(i).getAsJsonObject();
                int fUpd = intVal(resJs, "totalFilesUpdated");
                int fAdd = intVal(resJs, "totalFilesAdded");
                int fDel = intVal(resJs, "totalFilesDeleted");
                // Prefer backend value for Human LOC %, fall back to local map, then 0
                Double humanLocPercent = null;
                if (resJs.has("humanLocPercent") && !resJs.get("humanLocPercent").isJsonNull()) {
                    try {
                        humanLocPercent = resJs.get("humanLocPercent").getAsDouble();
                    } catch (Exception ignored) {
                        LOG.error("GenAI-LOC: Error- While get humanLocPercent value from Json.  Invalid humanLocPercent value for event ID " + str(resJs, "id") + ": " + resJs.get("humanLocPercent"));
                    }
                }
                LOG.info("GenAI-LOC: Event ID " + str(resJs, "id") + " - humanLocPercent from backend: " + humanLocPercent);
                if (humanLocPercent == null) {
                    humanLocPercent = humanLocPercentMap.getOrDefault(i, 0.0);
                    LOG.info("GenAI-LOC: Event ID " + str(resJs, "id") + " - humanLocPercent from local map: " + humanLocPercent);
                }

                int linesAdded    = intVal(resJs, "linesAdded");
                int linesModified = intVal(resJs, "linesModified");
                int linesDeleted  = intVal(resJs, "linesDeleted");
                int totalLoc      = linesAdded + linesModified + linesDeleted;
                double humanLoc   = totalLoc * (humanLocPercent / 100.0);
                double genAiLoc   = totalLoc - humanLoc;

                String genAiGeneratedStr = (resJs.has("genAiGenerated") && resJs.get("genAiGenerated").getAsBoolean()) ? "Yes" : "No";
                Object[] row = new Object[]{
                        str(resJs, "id"),              // 0  ID (hidden)
                        str(resJs, "eventTimestamp"),  // 1  Timestamp
                        str(resJs, "fileName"),        // 2  File
                        str(resJs, "genAiTool"),       // 3  Tool
                        str(resJs, "linesAdded"),      // 4  Lines +
                        str(resJs, "linesModified"),   // 5  Lines ~
                        str(resJs, "linesDeleted"),    // 6  Lines −
                        genAiGeneratedStr,             // 7  GenAI
                        str(resJs, "llmModel"),        // 8  Model
                        str(resJs, "agentName"),       // 9  Agent
                        str(resJs, "acceptedLocation"),// 10 Location
                        String.valueOf(fUpd),          // 11 Files Updated
                        String.valueOf(fAdd),          // 12 Files Added
                        String.valueOf(fDel),          // 13 Files Deleted
                        humanLocPercent,               // 14 Human LOC %
                        String.format("%.2f", humanLoc),  // 15 Human LOC
                        String.format("%.2f", genAiLoc),  // 16 GenAI LOC
                        String.valueOf(intVal(resJs, "inputTokens")),   // 17 Input Tokens
                        String.valueOf(intVal(resJs, "outputTokens"))   // 18 Output Tokens
                };
                rows.add(row);
            }

            // Populate table on EDT, then derive all summary fields from the table
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                for (Object[] row : rows) {
                    tableModel.addRow(row);
                }
                updateSummaryFromTable();
            });
        } catch (Exception ex) {
            LOG.warn("Failed to load events: " + ex.getMessage());
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                lblTotalFilesUpdated.setText("N/A");
                lblTotalFilesAdded.setText("N/A");
                lblTotalFilesDeleted.setText("N/A");
                lblTotalEvents.setText("N/A");
                lblGenAiEvents.setText("N/A");
                lblManualEvents.setText("N/A");
                lblLinesAdded.setText("N/A");
                lblLinesModified.setText("N/A");
                lblLinesDeleted.setText("N/A");
                lblGenAiAdoption.setText("N/A");
                lblHumanContribution.setText("N/A");
                lblTotalInputTokens.setText("N/A");
                lblTotalOutputTokens.setText("N/A");
            });
        }
    }

    private static @NotNull String getURL(String baseUrl, String viewBy, String devId, String projId, String from, String to) {
        String url;
        switch (viewBy) {
            case "Project":
                url = baseUrl + "/events/project/" + projId
                        + "?from=" + from + "&to=" + to
                        + "&sort=eventTimestamp,desc";
                break;
            case "Developer + Project":
                url = baseUrl + "/events/developer/" + devId + "/project/" + projId
                        + "?from=" + from + "&to=" + to
                        + "&sort=eventTimestamp,desc";
                break;
            default:
                // Use /all endpoint for Developer view (fetch all, not paginated)
                url = baseUrl + "/events/developer/" + devId + "/all"
                        + "?from=" + from + "&to=" + to;
                break;
        }
        return url;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private static String str(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "—";
        JsonElement el = obj.get(key);
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    private static int intVal(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return 0;
        return obj.get(key).getAsInt();
    }

    private void downloadTableDataAsCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Table Data as CSV");
        int userSelection = fileChooser.showSaveDialog(mainPanel);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            java.io.File fileToSave = fileChooser.getSelectedFile();
            try (java.io.FileWriter fw = new java.io.FileWriter(fileToSave)) {
                // Write header
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    fw.write(tableModel.getColumnName(i));
                    if (i < tableModel.getColumnCount() - 1) fw.write(",");
                }
                fw.write("\n");
                // Write rows
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    for (int col = 0; col < tableModel.getColumnCount(); col++) {
                        Object val = tableModel.getValueAt(row, col);
                        String cell = val == null ? "" : val.toString().replace("\"", "\"\"");
                        if (cell.contains(",") || cell.contains("\"")) {
                            cell = '"' + cell + '"';
                        }
                        fw.write(cell);
                        if (col < tableModel.getColumnCount() - 1) fw.write(",");
                    }
                    fw.write("\n");
                }
                fw.flush();
                JOptionPane.showMessageDialog(mainPanel, "Table data exported successfully.", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainPanel, "Failed to export table data: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}