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
package net.tascalate.async.extras;

import static net.tascalate.async.CallContext.send;

import java.time.Duration;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import net.tascalate.async.AsyncChannel;
import net.tascalate.async.Scheduler;
import net.tascalate.async.TypedChannel;
import net.tascalate.async.async;

import net.tascalate.async.spi.CurrentCallContext;

import net.tascalate.concurrent.CompletableTask;

public class Generators {

    private Generators() {}
    
    public static @async AsyncChannel<Duration> delays(Duration duration) {
        Executor executor = new CurrentSchedulerExecutor(CurrentCallContext.scheduler());
        while (true) {
            send( CompletableTask.delay(duration, executor) );
        }
    }
    
    public static @async AsyncChannel<Duration> delays(long timeout, TimeUnit timeUnit) {
        Executor executor = new CurrentSchedulerExecutor(CurrentCallContext.scheduler());
        while (true) {
            send( CompletableTask.delay(timeout, timeUnit, executor) );
        }
    }

    public static <T> Function<AsyncChannel<T>, PromisesGenerator<T>> promisesGenerator() {
        return DefaultPromisesGenerator::new;
    }
    
    static class CurrentSchedulerExecutor implements Executor {
        private final Scheduler scheduler;
        CurrentSchedulerExecutor(Scheduler scheduler) {
            this.scheduler = scheduler;
        }
        
        @Override
        public void execute(Runnable command) {
            scheduler.schedule(command);
        }
    }
}
