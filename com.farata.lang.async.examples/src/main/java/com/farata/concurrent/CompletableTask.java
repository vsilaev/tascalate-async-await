package com.farata.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.RunnableFuture;

public interface CompletableTask<V> extends RunnableFuture<V>, CompletionStage<V> {
}
