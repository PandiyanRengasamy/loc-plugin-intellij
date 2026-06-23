package com.cts.plugin.intellij.loc.model;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * CodeEventRequest — mirrors the backend REST API contract.
 * Gson serializes this to JSON for the HTTP POST.
 *
 * One request is sent per "Keep All" / "Keep" click and bundles ALL changed files
 * into the {@link #fileChanges} array. The top-level linesAdded / linesModified /
 * linesDeleted fields hold the aggregated totals across all files.
 *
 * JSON shape:
 * <pre>
 * {
 *   "developerId"    : "dev-001",
 *   "projectId"      : "MyProject",
 *   ...
 *   "linesAdded"     : 35,        // total across all files
 *   "linesModified"  : 6,
 *   "linesDeleted"   : 2,
 *   "fileChanges": [
 *     { "filePath": "...", "fileName": "Foo.java", "className": "Foo",
 *       "linesAdded": 25, "linesModified": 4, "linesDeleted": 2 },
 *     { "filePath": "...", "fileName": "Bar.java", "className": "Bar",
 *       "linesAdded": 10, "linesModified": 2, "linesDeleted": 0 }
 *   ]
 * }
 * </pre>
 */
public class LOCRequestPayload {
    private static final Logger LOG = Logger.getInstance(LOCRequestPayload.class);

    private String  developerId;
    private String  developerName;
    private String  projectId;
    private String  sprintId;
    // Primary / first file (kept for backward compatibility)
    private String  filePath;
    private String  fileName;
    private String  className;
    private String  ideType;
    private String  genAiTool;
    private String  developmentMode;
    // Aggregated totals across all changed files
    private int     linesAdded;
    private int     linesModified;
    private int     linesDeleted;
    private boolean genAiGenerated;
    private Double  genAiConfidenceScore;
    private String  eventTimestamp;
    private String  sessionId;

    /** LLM model name used for code generation (e.g. "gpt-4o", "claude-3.5-sonnet", "gemini-pro"). */
    private String  llmModel;

    /** Agent name that generated the code (e.g. "Copilot", "Claude Code", "Gemini"). */
    private String  agentName;

    /**
     * Location where the AI-generated changes were accepted.
     * Values: CODE_EDITOR | COPILOT_CHAT | CLAUDE_CHAT | INLINE_SUGGESTION | UNKNOWN
     */
    private String  acceptedLocation;

    /** All files changed in this "Keep All" session. */
    private List<FileChange> fileChanges;

    /** Total number of files updated in this event. */
    private int totalFilesUpdated;
    /** Total number of files added in this event. */
    private int totalFilesAdded;
    /** Total number of files deleted in this event. */
    private int totalFilesDeleted;

    /** Number of input (prompt) tokens consumed by the LLM for this event. Null when unknown. */
    private Integer inputTokens;
    /** Number of output (completion) tokens produced by the LLM for this event. Null when unknown. */
    private Integer outputTokens;

    // ── Constructor (single-file, backward-compatible) ────────────────────────

    public LOCRequestPayload(
            String developerId, String developerName,
            String projectId, String sprintId,
            String filePath, String fileName, String className,
            String ideType, String genAiTool, String developmentMode,
            int linesAdded, int linesModified, int linesDeleted,
            boolean genAiGenerated, Double genAiConfidenceScore,
            String eventTimestamp, String sessionId
    ) {
        this(developerId, developerName, projectId, sprintId,
                filePath, fileName, className, ideType, genAiTool, developmentMode,
                linesAdded, linesModified, linesDeleted,
                genAiGenerated, genAiConfidenceScore, eventTimestamp, sessionId,
                null, null, null,
                new ArrayList<>());
        // Add the single file as an entry in fileChanges automatically
        this.fileChanges.add(new FileChange(filePath, fileName, className,
                linesAdded, linesModified, linesDeleted));
    }

    // ── Constructor (multi-file) ──────────────────────────────────────────────

    public LOCRequestPayload(
            String developerId, String developerName,
            String projectId, String sprintId,
            String filePath, String fileName, String className,
            String ideType, String genAiTool, String developmentMode,
            int linesAdded, int linesModified, int linesDeleted,
            boolean genAiGenerated, Double genAiConfidenceScore,
            String eventTimestamp, String sessionId,
            List<FileChange> fileChanges
    ) {
        this(developerId, developerName, projectId, sprintId,
                filePath, fileName, className, ideType, genAiTool, developmentMode,
                linesAdded, linesModified, linesDeleted,
                genAiGenerated, genAiConfidenceScore, eventTimestamp, sessionId,
                null, null, null,
                fileChanges);
    }

    // ── Constructor (full — with llmModel, agentName, acceptedLocation) ──────

    public LOCRequestPayload(
            String developerId, String developerName,
            String projectId, String sprintId,
            String filePath, String fileName, String className,
            String ideType, String genAiTool, String developmentMode,
            int linesAdded, int linesModified, int linesDeleted,
            boolean genAiGenerated, Double genAiConfidenceScore,
            String eventTimestamp, String sessionId,
            String llmModel, String agentName, String acceptedLocation,
            List<FileChange> fileChanges
    ) {
        this.developerId          = developerId;
        this.developerName        = developerName;
        this.projectId            = projectId;
        this.sprintId             = sprintId;
        this.filePath             = filePath;
        this.fileName             = fileName;
        this.className            = className;
        this.ideType              = ideType;
        this.genAiTool            = genAiTool;
        this.developmentMode      = developmentMode;
        this.linesAdded           = linesAdded;
        this.linesModified        = linesModified;
        this.linesDeleted         = linesDeleted;
        this.genAiGenerated       = genAiGenerated;
        this.genAiConfidenceScore = genAiConfidenceScore;
        this.eventTimestamp       = eventTimestamp;
        this.sessionId            = sessionId;
        this.llmModel             = llmModel;
        this.agentName            = agentName;
        this.acceptedLocation     = acceptedLocation;
        this.fileChanges          = fileChanges != null ? fileChanges : new ArrayList<>();
        LOG.debug("GenAI_LOC: CodeEventRequest created: files=" + this.fileChanges.size()
                + " dev=" + developerId
                + " tool=" + genAiTool
                + " +lines=" + linesAdded
                + " ~lines=" + linesModified
                + " -lines=" + linesDeleted
                + " genAi=" + genAiGenerated
                + " confidence=" + genAiConfidenceScore
                + " session=" + sessionId);
    }

    // Getters (required by Gson and for logging)
    public String  getDeveloperId()          { return developerId; }
    public String  getDeveloperName()        { return developerName; }
    public String  getProjectId()            { return projectId; }
    public String  getSprintId()             { return sprintId; }
    public String  getFilePath()             { return filePath; }
    public String  getFileName()             { return fileName; }
    public String  getClassName()            { return className; }
    public String  getIdeType()              { return ideType; }
    public String  getGenAiTool()            { return genAiTool; }
    public String  getDevelopmentMode()      { return developmentMode; }
    public int     getLinesAdded()           { return linesAdded; }
    public int     getLinesModified()        { return linesModified; }
    public int     getLinesDeleted()         { return linesDeleted; }
    public boolean isGenAiGenerated()        { return genAiGenerated; }
    public Double  getGenAiConfidenceScore() { return genAiConfidenceScore; }
    public String  getEventTimestamp()       { return eventTimestamp; }
    public String  getSessionId()            { return sessionId; }
    public String  getLlmModel()             { return llmModel; }
    public String  getAgentName()            { return agentName; }
    public String  getAcceptedLocation()     { return acceptedLocation; }
    public List<FileChange> getFileChanges() { return fileChanges; }
    public int     getTotalFilesUpdated()    { return totalFilesUpdated; }
    public int     getTotalFilesAdded()      { return totalFilesAdded; }
    public int     getTotalFilesDeleted()    { return totalFilesDeleted; }
    public Integer getInputTokens()          { return inputTokens; }
    public Integer getOutputTokens()         { return outputTokens; }

    // Setters for new fields
    public void setLlmModel(String llmModel)                 { this.llmModel = llmModel; }
    public void setAgentName(String agentName)                { this.agentName = agentName; }
    public void setAcceptedLocation(String acceptedLocation)  { this.acceptedLocation = acceptedLocation; }
    public void setTotalFilesUpdated(int totalFilesUpdated)   { this.totalFilesUpdated = totalFilesUpdated; }
    public void setTotalFilesAdded(int totalFilesAdded)       { this.totalFilesAdded = totalFilesAdded; }
    public void setTotalFilesDeleted(int totalFilesDeleted)   { this.totalFilesDeleted = totalFilesDeleted; }
    public void setInputTokens(Integer inputTokens)           { this.inputTokens = inputTokens; }
    public void setOutputTokens(Integer outputTokens)         { this.outputTokens = outputTokens; }

    /**
     * Adds a file-level change entry and updates the aggregated totals.
     *
     * @param fc the {@link FileChange} to add
     * @return {@code this} for chaining
     */
    public LOCRequestPayload addFileChange(FileChange fc) {
        this.fileChanges.add(fc);
        this.linesAdded    += fc.getLinesAdded();
        this.linesModified += fc.getLinesModified();
        this.linesDeleted  += fc.getLinesDeleted();
        return this;
    }

    @Override
    public String toString() {
        return "GenAI_LOC: CodeEventRequest{" +
                "developerId='"    + developerId    + '\'' +
                ", projectId='"    + projectId      + '\'' +
                ", sprintId='"     + sprintId       + '\'' +
                ", fileName='"     + fileName       + '\'' +
                ", className='"    + className      + '\'' +
                ", genAiTool='"    + genAiTool      + '\'' +
                ", devMode='"      + developmentMode + '\'' +
                ", linesAdded="    + linesAdded     +
                ", linesModified=" + linesModified  +
                ", linesDeleted="  + linesDeleted   +
                ", genAiGenerated="+ genAiGenerated +
                ", confidence="    + genAiConfidenceScore +
                ", timestamp='"    + eventTimestamp + '\'' +
                ", session='"      + sessionId      + '\'' +
                '}';
    }
    //generate hashCode and equals based on developerId, projectId, fileName, eventTimestamp (ignoring sessionId and genAiTool for deduplication purposes)
    @Override
    public int hashCode() {
        int result = developerId != null ? developerId.hashCode() : 0;
        result = 31 * result + (projectId != null ? projectId.hashCode() : 0);
        result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
        result = 31 * result + (eventTimestamp != null ? eventTimestamp.hashCode() : 0);
        return result;
    }
    // Two events are considered equal if they have the same developerId, projectId, fileName, and eventTimestamp
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LOCRequestPayload other = (LOCRequestPayload) obj;
        if (developerId != null ? !developerId.equals(other.developerId) : other.developerId != null) return false;
        if (projectId != null ? !projectId.equals(other.projectId) : other.projectId != null) return false;
        if (fileName != null ? !fileName.equals(other.fileName) : other.fileName != null) return false;
        return eventTimestamp != null ? eventTimestamp.equals(other.eventTimestamp) : other.eventTimestamp == null;
    }

    //help me to explain the equals and hashCode implementation in the comments is good or not, and if not, please improve it.

     /**
     * The hashCode method generates a hash code based on the developerId, projectId, fileName, and eventTimestamp fields.
     * This allows CodeEventRequest objects to be used effectively in hash-based collections like HashSet or HashMap.
     * The sessionId and genAiTool fields are intentionally excluded from the hash code calculation to allow for deduplication of events that may differ only in those fields.
     */
}
