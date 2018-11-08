/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.examples.generator;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.yield;
import static net.tascalate.async.CallContext.awaitValue;

import java.util.function.Consumer;

import org.apache.commons.javaflow.core.StackRecorder;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.Sequence;
import net.tascalate.async.async;

import net.tascalate.concurrent.Promise;
import net.tascalate.javaflow.SuspendableStream;

public class RecursionTest {

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        System.out.println( consumer().get() );
        double finish = System.currentTimeMillis();
        System.out.println((finish - start) / 1000 + " seconds");
        System.out.println((finish - start) / 10_000 + " ns each");
    }
    
    @async static Promise<String> consumer() {
        System.out.println( StackRecorder.get().getRunnable().toString() );
        try (SuspendableStream<Object> g = producer().stream().map$(awaitValue())) {
            g.forEach(NOP);
        }
        return async("Done");
    }
    
    @async static AsyncGenerator<Object> producer() {
        System.out.println( StackRecorder.get().getRunnable().toString() );        
        for (int i = 0; i < 10_000_000; i++) {
            /*
            yield("");
            */
            yield(Sequence.empty());
        }
        return yield();
    }

    private static final Consumer<Object> NOP = v -> {};
}
