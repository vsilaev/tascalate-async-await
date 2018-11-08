package net.tascalate.async;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public interface AsyncValue<T> extends CompletionStage<T>, Future<T> {

}
