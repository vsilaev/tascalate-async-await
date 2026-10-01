/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.mutiny;

import java.util.function.LongConsumer;

import io.smallrye.mutiny.subscription.MultiSubscriber;
import net.tascalate.async.AsyncGenerator;

abstract class CrossPlatformMultiSubscriber<T> implements MultiSubscriber<T> {
    
    private final AsyncGenerator.Sink<T> sink;
    private final long batchSize;
    
    protected CrossPlatformMultiSubscriber(AsyncGenerator.Sink<T> sink, long batchSize) {
        this.sink = sink;
        this.batchSize = batchSize;
    }
    
    
    public void onSubscribe(Object subscription) {
        LongConsumer requestAction = MutinyCompatibilityBridge.getSubscriptionRequester(subscription);
        Runnable cancelAction = MutinyCompatibilityBridge.getSubscriptionCanceler(subscription);

        sink.subscribe(
            requestAction::accept, 
            () -> {
                cancelAction.run();
                sink.emitCompletion();
            }
        );

        requestAction.accept(batchSize > 0 ? batchSize : Long.MAX_VALUE);
    }

    @Override
    public void onItem(T item) {
        sink.emitNextItem(item);
    }

    @Override
    public void onFailure(Throwable failure) {
        sink.emitError(failure);
    }

    @Override
    public void onCompletion() {
        sink.emitCompletion();
    }
}
