package com.cts.plugin.intellij.loc.util;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

/**
 * Central icon registry for the GenAI LOC Tracker plugin.
 *
 * Icons are loaded from the classpath via IntelliJ's {@link IconLoader},
 * which handles HiDPI (@2x) variants and dark theme (@_dark) variants
 * automatically when matching files are present beside the base icon.
 *
 * Icon file: src/main/resources/icon/loc_icon.jpg
 */
public final class Icons {

    /** Main plugin icon — used on actions, tool windows, and notifications. */
    public static final Icon LOC_ICON = IconLoader.getIcon("/icon/loc_icon.svg", Icons.class);

    private Icons() { /* utility class */ }
}

