package net.tascalate.async.api;

import java.util.concurrent.CompletionStage;

final class PackagePrivate {
    public PackagePrivate() {}
    
    static final ContextualExecutor SAME_THREAD_EXECUTOR = ContextualExecutor.from(Runnable::run);
    
    static final Generator<?> EMPTY_GENERATOR = new Generator<Object>() {

        @Override
        public CompletionStage<Object> next(Object producerParam) {
            return null;
        }

        @Override
        public CompletionStage<Object> next() {
            return null;
        }

        @Override
        public void close() {

        }
        
    };
    
}
