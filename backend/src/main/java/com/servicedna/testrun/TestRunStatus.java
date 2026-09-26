package com.servicedna.testrun;

public enum TestRunStatus {
  /** Waiting for a runner in the target environment to pick it up. */
  QUEUED,
  /** A runner claimed it and is sending the request. */
  RUNNING,
  /** The runner reported the entry call's result; waiting for the services' spans to arrive. */
  WAITING,
  COMPLETED,
  /** The request couldn't be sent (connection refused, unknown target, ...). */
  FAILED,
  TIMED_OUT
}
