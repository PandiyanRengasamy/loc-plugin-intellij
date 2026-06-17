package com.cts.plugin.intellij.loc.model;

/**
 * FileChange — represents the LOC change for a single file within a GenAI session.
 *
 * When GitHub Copilot's "Keep All" is clicked, it can affect multiple files at once.
 * Instead of sending one HTTP request per file, the plugin bundles all changes into
 * a single {@link LOCRequestPayload} and includes each file's details here.
 *
 * Example JSON array entry:
 * <pre>
 * {
 *   "filePath"     : "C:/project/src/com/example/UserService.java",
 *   "fileName"     : "UserService.java",
 *   "className"    : "UserService",
 *   "linesAdded"   : 25,
 *   "linesModified": 4,
 *   "linesDeleted" : 2
 * }
 * </pre>
 */
public class FileChange {

    /** Full absolute path of the changed file. */
    private String filePath;

    /** File name including extension (e.g. {@code UserService.java}). */
    private String fileName;

    /**
     * Java/Kotlin class name — file name without extension
     * (e.g. {@code UserService}).
     */
    private String className;

    /** Number of lines added by the AI. */
    private int linesAdded;

    /** Number of lines modified (replaced) by the AI. */
    private int linesModified;

    /** Number of lines deleted by the AI. */
    private int linesDeleted;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FileChange(String filePath, String fileName, String className,
                      int linesAdded, int linesModified, int linesDeleted) {
        this.filePath      = filePath;
        this.fileName      = fileName;
        this.className     = className;
        this.linesAdded    = linesAdded;
        this.linesModified = linesModified;
        this.linesDeleted  = linesDeleted;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getFilePath()      { return filePath; }
    public String getFileName()      { return fileName; }
    public String getClassName()     { return className; }
    public int    getLinesAdded()    { return linesAdded; }
    public int    getLinesModified() { return linesModified; }
    public int    getLinesDeleted()  { return linesDeleted; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setFilePath(String filePath)          { this.filePath      = filePath; }
    public void setFileName(String fileName)          { this.fileName      = fileName; }
    public void setClassName(String className)        { this.className     = className; }
    public void setLinesAdded(int linesAdded)         { this.linesAdded    = linesAdded; }
    public void setLinesModified(int linesModified)   { this.linesModified = linesModified; }
    public void setLinesDeleted(int linesDeleted)     { this.linesDeleted  = linesDeleted; }

    // ── Convenience factory ───────────────────────────────────────────────────

    /**
     * Creates a {@link FileChange} deriving {@code className} automatically from
     * the file name by stripping its extension.
     *
     * @param filePath      absolute file path
     * @param fileName      file name including extension
     * @param linesAdded    lines added
     * @param linesModified lines modified
     * @param linesDeleted  lines deleted
     * @return a new {@code FileChange}
     */
    public static FileChange of(String filePath, String fileName,
                                int linesAdded, int linesModified, int linesDeleted) {
        String className = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        return new FileChange(filePath, fileName, className, linesAdded, linesModified, linesDeleted);
    }

    @Override
    public String toString() {
        return "FileChange{" +
                "fileName='" + fileName + '\'' +
                ", className='" + className + '\'' +
                ", linesAdded=" + linesAdded +
                ", linesModified=" + linesModified +
                ", linesDeleted=" + linesDeleted +
                '}';
    }
}

