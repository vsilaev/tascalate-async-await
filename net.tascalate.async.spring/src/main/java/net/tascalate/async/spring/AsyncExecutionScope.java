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
package net.tascalate.async.spring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.NamedThreadLocal;

public class AsyncExecutionScope implements Scope {

    private final ThreadLocal<Map<String, ScopedObject>> threadScope = new NamedThreadLocal<>("AsyncExecutionScope");

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, ScopedObject> frame = threadScope.get();
        ScopedObject object = frame.computeIfAbsent(name, __ -> new ScopedObject());
        return object.get(objectFactory);
    }
    

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public  Object remove(String name) {
        Map<String, ScopedObject> frame = threadScope.get();
        if (null != frame) {
            ScopedObject object = frame.remove(name);
            return null != object ? object.get() : null;
        } else {
            return null;
        }
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        Map<String, ScopedObject> frame = threadScope.get();
        if (null != frame) {
            ScopedObject object = frame.computeIfAbsent(name, __ -> new ScopedObject());
            object.registerDestructionCallback(callback);
        }
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread().getName();
    }
    
    static class ScopedObject {
        private Object instance;
        private Runnable destructor;
        
        synchronized Object get() {
            return instance;
        }
        
        synchronized Object get(ObjectFactory<?> objectFactory) {
            if (null == instance) {
                instance = objectFactory.getObject();
            }
            return instance;
        }
        
        synchronized void registerDestructionCallback(Runnable callback) {
            destructor = callback;
        }
        
        void destroy() {
            Runnable destructor;
            synchronized (this) {
                destructor = this.destructor;
                this.destructor = null;
            }
            if (null != destructor) {
                destructor.run();
            }
        }
    }
    
    boolean hasFrame() {
        return threadScope.get() != null;
    }
    
    Map<String, ScopedObject> createOrGetFrame() {
        Map<String, ScopedObject> frame = threadScope.get();
        if (null == frame) {
            Map<String, ScopedObject> newFrame = new ConcurrentHashMap<>();
            threadScope.set(newFrame);
            return newFrame;
        } else {
            // No scope added
            return null;
        }
    }
    
    <T> T withFrame(boolean enforceNew, NewFrameCall<T> call) throws Throwable {
        if (enforceNew) {
            return withNewFrame(call);
        } else {
            return withNewOrExistingFrame(call);
        }
    }
    
    <T> T withNewOrExistingFrame(NewFrameCall<T> call) throws Throwable {
        Map<String, ScopedObject> frame = threadScope.get();
        if (null == frame) {
            Map<String, ScopedObject> newFrame = new ConcurrentHashMap<>();
            threadScope.set(newFrame);
            try {
                return call.apply(frame);
            } finally {
                threadScope.remove();
            }
        } else {
            // No scope added
            return call.apply(null);
        }
    }
    
    <T> T withNewFrame(NewFrameCall<T> call) throws Throwable {
        Map<String, ScopedObject> frame = new ConcurrentHashMap<>();
        Map<String, ScopedObject> previous = threadScope.get(); 
        threadScope.set(frame);
        try {
            return call.apply(frame);
        } finally {
            if (null == previous) {
                threadScope.remove();
            } else {
                threadScope.set(previous);
            }
        }
    }
    
    public Runnable contextualize(Runnable code) {
        Map<String, ScopedObject> frame = threadScope.get();
        if (null == frame) {
            return code;
        } else {
            return () -> {
                Map<String, ScopedObject> previous = threadScope.get();
                threadScope.set(frame);
                try {
                    code.run();
                } finally {
                    if (null == previous) {
                        threadScope.remove();
                    } else {
                        threadScope.set(previous);
                    }
                }
            };
        }
    }
    
    public static AsyncExecutionScope instance() {
        return INSTANCE;
    }
    
    private static final AsyncExecutionScope INSTANCE = new AsyncExecutionScope();
    
    @FunctionalInterface
    static interface NewFrameCall<T> {
        T apply(Map<String, ScopedObject> frame) throws Throwable;
    }

}
