package com.fredlin.trading.app.engine;

import com.fredlin.trading.ops.Outcome;

public record SubmitResult<T>(Outcome outcome, T value, String reason, boolean duplicate) {}
