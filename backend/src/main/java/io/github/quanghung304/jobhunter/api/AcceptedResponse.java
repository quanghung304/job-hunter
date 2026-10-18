package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** 202 body of actions that run asynchronously (api-spec "Admin"). */
public record AcceptedResponse(@Schema(requiredMode = REQUIRED) boolean accepted) {
}
