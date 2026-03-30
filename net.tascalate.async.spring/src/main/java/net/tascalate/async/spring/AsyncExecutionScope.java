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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.NamedThreadLocal;

public class AsyncExecutionScope implements Scope {
    
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
    
    static class Frame {
        private final Map<String, ScopedObject> scopedObjects = new ConcurrentHashMap<>();
        
        Object getExisting(String name) {
            ScopedObject object = scopedObjects.get(name);
            return null == object ? null : object.get();
        }
        
        Object get(String name, ObjectFactory<?> objectFactory) {
            ScopedObject object = scopedObjects.computeIfAbsent(name, __ -> new ScopedObject());
            return object.get(objectFactory);
        }
        
        Object remove(String name) {
            ScopedObject object = scopedObjects.remove(name);
            return null != object ? object.get() : null;
        }

        void registerDestructionCallback(String name, Runnable callback) {
            ScopedObject object = scopedObjects.computeIfAbsent(name, __ -> new ScopedObject());
            object.registerDestructionCallback(callback);
        }
        
        void destroy() {
            Map<String, AsyncExecutionScope.ScopedObject> copy = new HashMap<>(scopedObjects);
            scopedObjects.clear();
            copy.values()
                .stream()
                .forEach(ScopedObject::destroy);
        }
        
        Set<String> ownedKeys() {
            return new HashSet<>(scopedObjects.keySet());
        }
    }
    
    static class NestedFrame extends Frame {
        private final Frame parentFrame;
        private final Set<String> parentKeys;
        
        NestedFrame(Frame parentFrame) {
            this.parentFrame = parentFrame;
            // Save parent keys on creation
            // This way any new scoped objects in parent 
            // will be ignored -- otherwise get(name, factory) result is unstable
            this.parentKeys = new HashSet<>(parentFrame.ownedKeys());
        }
        
        Object getExisting(String name) {
            Object object = super.getExisting(name);
            return null == object && parentKeys.contains(name) ? parentFrame.getExisting(name) : object;
        }
        
        Object get(String name, ObjectFactory<?> objectFactory) {
            Object result = parentKeys.contains(name) ? parentFrame.getExisting(name) : null;
            if (null != result) {
                return result;
            } else {
                return super.get(name, objectFactory);
            }
        }
        
        Set<String> ownedKeys() {
            Set<String> result = super.ownedKeys();
            result.addAll(parentKeys);
            return result;
        }
    }
    
    private static final Frame INVALID_FRAME = new Frame();

    private final ThreadLocal<Frame> threadScope = new NamedThreadLocal<>("AsyncExecutionScope");

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Frame frame = threadScope.get();
        if (!isValidFrame(frame)) {
            throw new IllegalStateException("No valid async call scope available for the current thread");
        }
        return frame.get(name, objectFactory);
    }
    

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public  Object remove(String name) {
        Frame frame = threadScope.get();
        if (isValidFrame(frame)) {
            return frame.remove(name);
        } else {
            return null;
        }
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        Frame frame = threadScope.get();
        if (isValidFrame(frame)) {
            frame.registerDestructionCallback(name, callback);
        } else {
            // TODO Or Error?
        }
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread().getName();
    }

    
    boolean hasFrame() {
        return isValidFrame(threadScope.get());
    }
    
    private static boolean isValidFrame(Frame frame) {
        return frame != null && frame != INVALID_FRAME;
    }
    
    <T> T withFrame(boolean createNewFrame, boolean inheritOldFrame, NewFrameCall<T> call) throws Throwable {
        if (createNewFrame) {
            return withNewFrame(call, inheritOldFrame);
        } else {
            return withNewOrExistingFrame(call);
        }
    }
    
    <T> T withoutFrame(NewFrameCall<T> call) throws Throwable {
        Frame previous = threadScope.get();
        if (isValidFrame(previous)) {
            threadScope.set(INVALID_FRAME);
            try {
                return call.apply(null);
            } finally {
                resetThreadScope(previous);
            }
        } else {
            // No scope added
            return call.apply(null);
        }
    }
    
    private <T> T withNewOrExistingFrame(NewFrameCall<T> call) throws Throwable {
        Frame previous = threadScope.get();
        if (!isValidFrame(previous)) {
            Frame newFrame = new Frame();
            threadScope.set(newFrame);
            try {
                return call.apply(newFrame);
            } finally {
                resetThreadScope(previous);
            }
        } else {
            // No scope added
            return call.apply(null);
        }
    }
    
    private <T> T withNewFrame(NewFrameCall<T> call, boolean inheritOldFrame) throws Throwable {
        Frame previous = threadScope.get();
        Frame newFrame = inheritOldFrame && isValidFrame(previous) ? new NestedFrame(previous) : new Frame();
        threadScope.set(newFrame);
        try {
            return call.apply(newFrame);
        } finally {
            resetThreadScope(previous);
        }
    }
    
    public Runnable contextualize(Runnable code) {
        Frame frame = threadScope.get();
        if (null == frame) {
            return code;
        } else {
            return () -> {
                Frame previous = threadScope.get();
                threadScope.set(frame);
                try {
                    code.run();
                } finally {
                    resetThreadScope(previous);
                }
            };
        }
    }
    
    void resetThreadScope(Frame previous) {
        if (null == previous) {
            threadScope.remove();
        } else {
            threadScope.set(previous);
        }
    }
    
    public static AsyncExecutionScope instance() {
        return INSTANCE;
    }
    
    private static final AsyncExecutionScope INSTANCE = new AsyncExecutionScope();
    
    @FunctionalInterface
    static interface NewFrameCall<T> {
        T apply(Frame frame) throws Throwable;
    }

}
