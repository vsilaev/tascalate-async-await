package net.tascalate.async.sequence;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.core.CompletionStageHelper;

public enum CancelPolicy {
    NONE {
        @Override
        void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
            
        }
    },
    ENLISTED {
        @Override
        void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
            enlistedPromises.forEach(p -> CompletionStageHelper.cancelCompletionStage(p, true));
        }
    },
    ALL {
        @Override
        void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises) {
            ENLISTED.apply(enlistedPromises, pendingPromises);
            while (pendingPromises.hasNext()) {
                CompletionStage<?> nextPromise = pendingPromises.next();
                CompletionStageHelper.cancelCompletionStage(nextPromise, true);
            }
        }            
    };
    
    abstract void apply(Set<CompletionStage<?>> enlistedPromises, Iterator<? extends CompletionStage<?>> pendingPromises);
}
