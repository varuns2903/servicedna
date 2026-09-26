package com.servicedna.graph.domain;

/** What an observed call went to. Only SERVICE targets are registered services. */
public enum TargetKind {
  SERVICE,
  DATABASE,
  EXTERNAL,
  TOPIC
}
