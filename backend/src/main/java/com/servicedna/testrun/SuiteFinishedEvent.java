package com.servicedna.testrun;

import java.util.UUID;

/** A suite's runs are all decided (published after the transaction that closed it commits). */
public record SuiteFinishedEvent(UUID organizationId, UUID suiteId, String status) {}
