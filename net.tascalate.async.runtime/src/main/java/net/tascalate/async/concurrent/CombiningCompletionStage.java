/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.concurrent;

import static net.tascalate.async.core.CompletionStageHelper.completeSuccess;
import static net.tascalate.async.core.CompletionStageHelper.completeFailure;
import static net.tascalate.async.core.CompletionStageHelper.cancelCompletionStage;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.tascalate.async.core.RestrictedCompletableFuture;

abstract public class CombiningCompletionStage<T, R> extends RestrictedCompletableFuture<R> {
    
    protected static class Outcome<T> {
        public final T value;
        public final Throwable error;
        
        private Outcome(T value, Throwable error) {
            this.value = value;
            this.error = error;
        }
        
        public static <T> Outcome<T> value(T value) {
            return new Outcome<>(value, null);
        }
        
        public static <T> Outcome<T> error(Throwable error) {
            return new Outcome<>(null, error);
        }
    }
    
    private final List<? extends CompletionStage<T>> sources;
    
    public CombiningCompletionStage(List<? extends CompletionStage<T>> sources) {
        this(true, sources);
    }
   
    public CombiningCompletionStage(boolean cancelRemaining, List<? extends CompletionStage<T>> sources) {
        Objects.requireNonNull(sources);
        
        this.sources = sources;
        
        int total = sources.size();
        AtomicReferenceArrayList<Outcome<T>> outcomes = new AtomicReferenceArrayList<>(total);
        List<Outcome<T>> safeOutcomes = Collections.unmodifiableList(outcomes);
        AtomicInteger pendingCount = new AtomicInteger(total);
        int idx = 0;
        for (CompletionStage<T> source : sources) {
            int id = idx++;
            source.whenComplete((r, e) -> {
                int remaining = pendingCount.decrementAndGet();
                outcomes.compareAndSet(id, null, e == null ?  Outcome.value(r) : Outcome.error(e));
                Outcome<R> finalResult = combine(total, remaining, safeOutcomes);
                if (null != finalResult) {
                    if (finalResult.error != null) {
                        completeFailure(this, finalResult.error);
                    } else {
                        completeSuccess(this, finalResult.value);
                    }
                    if (cancelRemaining) {
                        cancelSources(true);
                    }
                } else if (0 == remaining) {
                    completeFailure(this, new NoSuchElementException());
                }
            });
        }
        // Edge case of empty collection
        if (total == 0) {
            Outcome<R> finalResult = combine(0, 0, safeOutcomes);
            if (null != finalResult) {
                if (finalResult.error != null) {
                    completeFailure(this, finalResult.error);
                } else {
                    completeSuccess(this, finalResult.value);
                }
            } else {
                completeFailure(this, new NoSuchElementException());
            } 
        }
    }
    
    abstract protected Outcome<R> combine(int total, int remaining, List<Outcome<T>> outcomes);
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            cancelSources(mayInterruptIfRunning);
            return true;
        } else {
            return false;
        }
    }
    
    final void cancelSources(boolean mayInterruptIfRunning) {
        sources.forEach(c -> cancelCompletionStage(c, mayInterruptIfRunning));
    }
    
    public static <T> CompletionStage<T> any(List<? extends CompletionStage<T>> sources) {
        return any(sources, true, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<T> any(List<? extends CompletionStage<T>> sources, boolean cancelRemaining) {
        return any(sources, cancelRemaining, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<T> any(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter) {
        return any(sources, true, filter, Function.identity());
    }
    
    public static <T> CompletionStage<T> any(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter) {
        return any(sources, cancelRemaining, filter, Function.identity());
    }
    
    public static <T, R> CompletionStage<R> any(List<? extends CompletionStage<T>> sources, Function<? super T, R> mapper) {
        return any(sources, true, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<R> any(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Function<? super T, R> mapper) {
        return any(sources, cancelRemaining, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<R> any(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return any(sources, true, filter, mapper);        
    }
    
    public static <T, R> CompletionStage<R> any(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return new CombiningCompletionStage<T, R>(cancelRemaining, sources) {
            @Override
            protected Outcome<R> combine(int total, int remaining, List<Outcome<T>> outcomes) {
                return outcomes.stream()
                               .filter(Objects::nonNull)
                               .filter(o -> o.error == null && filter.test(o.value))
                               .map(o -> Outcome.value(mapper.apply(o.value)))
                               .findFirst()
                               .orElseGet(() -> remaining != 0 ? null : findFirstError(outcomes));
            }
        };
    }
    
    public static <T> CompletionStage<T> anyStrict(List<? extends CompletionStage<T>> sources) {
        return anyStrict(sources, true, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<T> anyStrict(List<? extends CompletionStage<T>> sources, boolean cancelRemaining) {
        return anyStrict(sources, cancelRemaining, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<T> anyStrict(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter) {
        return anyStrict(sources, true, filter, Function.identity());
    }
    
    public static <T> CompletionStage<T> anyStrict(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter) {
        return anyStrict(sources, cancelRemaining, filter, Function.identity());
    }
    
    public static <T, R> CompletionStage<R> anyStrict(List<? extends CompletionStage<T>> sources, Function<? super T, R> mapper) {
        return anyStrict(sources, true, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<R> anyStrict(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Function<? super T, R> mapper) {
        return anyStrict(sources, cancelRemaining, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<R> anyStrict(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return anyStrict(sources, true, filter, mapper);
    }
    
    public static <T, R> CompletionStage<R> anyStrict(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return new CombiningCompletionStage<T, R>(cancelRemaining, sources) {
            @Override
            protected Outcome<R> combine(int total, int remaining, List<Outcome<T>> outcomes) {
                Outcome<R> firstError = findFirstError(outcomes);
                if (null != firstError) {
                    return firstError;
                }
                
                return outcomes.stream()
                               .filter(Objects::nonNull)
                               .filter(o -> o.error == null && filter.test(o.value))
                               .map(o -> Outcome.value(mapper.apply(o.value)))
                               .findFirst()
                               .orElse(null);
            }
        };
    }
    
    public static <T> CompletionStage<List<T>> all(List<? extends CompletionStage<T>> sources) {
        return all(sources, true, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<List<T>> all(List<? extends CompletionStage<T>> sources, boolean cancelRemaining) {
        return all(sources, cancelRemaining, acceptAll(), Function.identity());
    }
    
    public static <T> CompletionStage<List<T>> all(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter) {
        return all(sources, true, filter, Function.identity());
    }
    
    public static <T> CompletionStage<List<T>> all(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter) {
        return all(sources, cancelRemaining, filter, Function.identity());
    }

    public static <T, R> CompletionStage<List<R>> all(List<? extends CompletionStage<T>> sources, Function<? super T, R> mapper) {
        return all(sources, true, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<List<R>> all(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Function<? super T, R> mapper) {
        return all(sources, cancelRemaining, acceptAll(), mapper);
    }
    
    public static <T, R> CompletionStage<List<R>> all(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return all(sources, true, filter, mapper);
    }
    
    public static <T, R> CompletionStage<List<R>> all(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter, Function<? super T, R> mapper) {
        return combine(sources, cancelRemaining, filter, mapper, Function.identity());
    }
    
    public static <T, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, Function<? super List<T>, ? extends X> combiner) {
        return combine(sources, true, acceptAll(), Function.identity(), combiner);
    }
    
    public static <T, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Function<? super List<T>, ? extends X> combiner) {
        return combine(sources, cancelRemaining, acceptAll(), Function.identity(), combiner);
    }
    
    public static <T, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter, Function<? super List<T>, ? extends X> combiner) {
        return combine(sources, true, filter, Function.identity(), combiner);
    }
    
    public static <T, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter, Function<? super List<T>, ? extends X> combiner) {
        return combine(sources, cancelRemaining, filter, Function.identity(), combiner);
    }
    
    public static <T, R, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, Function<? super T, R> mapper, Function<? super List<R>, ? extends X> combiner) {
        return combine(sources, true, acceptAll(), mapper, combiner);
    }
    
    public static <T, R, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Function<? super T, R> mapper, Function<? super List<R>, ? extends X> combiner) {
        return combine(sources, cancelRemaining, acceptAll(), mapper, combiner);
    }
    
    public static <T, R, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, Predicate<? super T> filter, Function<? super T, R> mapper, Function<? super List<R>, ? extends X> combiner) {
        return combine(sources, true, filter, mapper, combiner);
    }
    
    public static <T, R, X> CompletionStage<X> combine(List<? extends CompletionStage<T>> sources, boolean cancelRemaining, Predicate<? super T> filter, Function<? super T, R> mapper, Function<? super List<R>, ? extends X> combiner) {
        return new CombiningCompletionStage<T, X>(cancelRemaining, sources) {
            @Override
            protected Outcome<X> combine(int total, int remaining, List<Outcome<T>> outcomes) {
                Outcome<X> firstError = findFirstError(outcomes);
                if (null != firstError) {
                    return firstError;
                }
                
                if (remaining > 0) {
                    return null;
                }
                
                List<R> values = outcomes.stream()
                                         .filter(Objects::nonNull)
                                         .filter(o -> o.error == null && filter.test(o.value))
                                         .map(o -> mapper.apply(o.value))
                                         .collect(Collectors.toList());
                
                if (values.size() == total) {
                    return Outcome.value(combiner.apply(values));
                } else {
                    return Outcome.error(new NoSuchElementException());
                }
            }
        };
    }
    
    static <T, R> Outcome<R> findFirstError(List<Outcome<T>> outcomes) {
        return outcomes.stream()
                       .filter(Objects::nonNull)
                       .filter(o -> o.error != null)
                       .map(o -> Outcome.<R>error(o.error))
                       .findFirst()
                       .orElse(null);
    }
    
    @SuppressWarnings("unchecked")
    static <T> Predicate<T> acceptAll() {
        return (Predicate<T>)ACCEPT_ALL;
    }
    
    private static final Predicate<Object> ACCEPT_ALL = __ -> true;
}
