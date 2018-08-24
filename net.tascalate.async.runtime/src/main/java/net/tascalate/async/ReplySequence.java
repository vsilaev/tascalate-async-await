package net.tascalate.async;

import java.util.concurrent.CompletionStage;

public interface ReplySequence<T, F extends CompletionStage<T>> extends Sequence<T, F> {
    @suspendable F next(Object producerParam);
}
