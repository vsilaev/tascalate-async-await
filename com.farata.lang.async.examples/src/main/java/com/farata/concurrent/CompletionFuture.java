package com.farata.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public interface CompletionFuture<V> extends Future<V>, CompletionStage<V> {

}
