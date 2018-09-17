/**
 * ﻿Copyright 2015-2018 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContextualRunnable implements Runnable {
    private final Runnable delegate;
    private final List<? extends ContextVar<?>> contextVars;
    
    private List<Object> capturedContext;
    
    ContextualRunnable(Runnable delegate, List<? extends ContextVar<?>> contextVars) {
        this.delegate = delegate;
        this.contextVars = null == contextVars ? 
            Collections.emptyList() : 
            Collections.unmodifiableList(contextVars);
    }
    
    ContextualRunnable captureCurrentContext() {
        capturedContext = captureContextVars();
        return this;
    }

    @Override
    public void run() {
        List<Object> originalContext = captureContextVars(); 
        restoreContextVars(capturedContext);
        try {
            delegate.run();
        } finally {
            restoreContextVars(originalContext);
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s[contexVars={%s}, capturedContext={%s}]", getClass().getSimpleName(), contextVars, capturedContext);
    }
    
    public static Function<Runnable, Runnable> relayContextVars(ContextVar<?> contextVar) {
        return _relayContextVars(Collections.singletonList(contextVar));
    }
    
    public static Function<Runnable, Runnable> relayContextVars(ThreadLocal<?> threadLocal) {
        return relayContextVars(ContextVar.from(threadLocal));
    }

    public static Function<Runnable, Runnable> relayContextVars(ContextVar<?>... contextVars) {
        return _relayContextVars(Arrays.asList(contextVars));
    }
    
    public static Function<Runnable, Runnable> relayContextVars(ThreadLocal<?>... threadLocals) {
        return _relayContextVars(Arrays.stream(threadLocals).map(ContextVar::from).collect(Collectors.toList()));
    }

    public static Function<Runnable, Runnable> relayContextVars1(List<? extends ContextVar<?>> contextVars) {
        return _relayContextVars(
            contextVars == null ? Collections.emptyList() : new ArrayList<>(contextVars)
        );
    }
    
    public static Function<Runnable, Runnable> relayContextVars2(List<? extends ThreadLocal<?>> threadLocals) {
        return _relayContextVars(
            threadLocals == null ? Collections.emptyList() : threadLocals
                .stream()
                .map(tl -> ContextVar.from((ThreadLocal<?>)tl))
                .collect(Collectors.toList())
        );
    }
    
    private static Function<Runnable, Runnable> _relayContextVars(List<? extends ContextVar<?>> contextVars) {
        if (null == contextVars || contextVars.isEmpty()) {
            return Function.identity();
        }
        return r -> new ContextualRunnable(r, contextVars).captureCurrentContext();
    }
    
    private List<Object> captureContextVars() {
        return contextVars.stream().map(v -> v.get()).collect(Collectors.toList());
    }
    
    private void restoreContextVars(List<Object> contextState) {
        Iterator<? extends ContextVar<?>> vars = contextVars.iterator();
        Iterator<Object> values = contextState.iterator();
        while (vars.hasNext() && values.hasNext()) {
            @SuppressWarnings("unchecked")
            ContextVar<Object> contextVar = (ContextVar<Object>)vars.next();
            Object contextVal = values.next();
            if (null == contextVal) {
                contextVar.remove();
            } else {
                contextVar.set(contextVal);
            }
        }
    }
}
