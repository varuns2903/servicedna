package com.servicedna.incident.domain;

public enum IncidentEventType {
  CREATED,
  STATUS_CHANGED,
  ACKNOWLEDGED,
  ESCALATED,
  POST_MORTEM_UPDATED,
  ALERT_TRIGGERED,
  SEVERITY_CHANGED
}
