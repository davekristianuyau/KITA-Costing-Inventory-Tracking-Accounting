package com.kita.procurement.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks statutory/tax identifiers so they never reach logs or audit detail (FR-004/023). Matching is
 * keyed on the field name rather than on digit shapes, so employee numbers, UUIDs, and money are left
 * untouched.
 */
public final class LogScrubber {

  /** {@code sssNo=03-1234567-8}, {@code "tin":"123-456-789"}, {@code tin: 123-456-789}. */
  private static final Pattern SENSITIVE =
      Pattern.compile(
          "(?i)([\"']?\\b(?:sss(?:No)?|philhealth(?:No)?|pagibig(?:No)?|hdmf(?:No)?|tin)\\b[\"']?"
              + "\\s*[=:]\\s*)([\"']?)([A-Za-z0-9][A-Za-z0-9-]*)\\2");

  private static final int VISIBLE_SUFFIX = 4;

  private LogScrubber() {}

  /** Mask all but the last four characters; values of four or fewer are hidden entirely. */
  public static String mask(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (value.length() <= VISIBLE_SUFFIX) {
      return "*".repeat(value.length());
    }
    int hidden = value.length() - VISIBLE_SUFFIX;
    return "*".repeat(hidden) + value.substring(hidden);
  }

  /** Mask any statutory/tax identifier appearing as a key/value pair in free text. */
  public static String scrub(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    Matcher m = SENSITIVE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String replacement = m.group(1) + m.group(2) + mask(m.group(3)) + m.group(2);
      m.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);
    return out.toString();
  }
}
