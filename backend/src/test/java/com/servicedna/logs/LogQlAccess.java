package com.servicedna.logs;

/** Test access to package-private LogQL helpers. */
public final class LogQlAccess {
  private LogQlAccess() {}

  public static String attribute(String filter) {
    return LogQl.attribute(filter);
  }
}
