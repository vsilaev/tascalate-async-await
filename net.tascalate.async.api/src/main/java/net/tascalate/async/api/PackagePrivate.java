package net.tascalate.async.api;

import java.util.concurrent.CompletionStage;

final class PackagePrivate {
    private PackagePrivate() {}
    
    static final Scheduler SAME_THREAD_SCHEDULER = Scheduler.from(Runnable::run);
    
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
