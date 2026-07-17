package com.kita.workflow.common;

/**
 * Error envelope returned on non-2xx (contracts/workflow-api.md). {@code outcome} mirrors the value
 * written to the activity log; {@code code} is a short machine token; {@code reason} is human text.
 */
public record ErrorResponse(String outcome, String reason, String code) {}
