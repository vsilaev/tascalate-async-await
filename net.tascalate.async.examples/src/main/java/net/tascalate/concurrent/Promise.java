package net.tascalate.concurrent;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public interface Promise<V> extends Future<V>, CompletionStage<V> {

}
