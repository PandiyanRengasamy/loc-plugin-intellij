package com.cts.plugin.intellij.loc.util;

import java.util.Arrays;
import java.util.List;

public class AiActionConstants {
    public static final List<String> ACTION_KEYWORDS = Arrays.asList(
        "keep all", "keep", "accept", "accept all",
        "apply", "apply all", "apply in editor",
        "apply suggestion", "accept suggestion",
        "accept solution", "insert at cursor", "insert code"
    );
}

