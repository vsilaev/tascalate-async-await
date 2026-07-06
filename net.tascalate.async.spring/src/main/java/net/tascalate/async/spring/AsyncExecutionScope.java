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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import net.tascalate.async.core.InternalCallContext;
import net.tascalate.async.spi.ThreadVar;

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
        
        CompletionStage<Void> asyncDestroy(Throwable error) {
            AsyncCloseable ac = null;
            synchronized (this) {
                if (instance instanceof AsyncCloseable) {
                    ac = (AsyncCloseable)instance;
                }
            }
            return ac == null ? null : ac.close(error);
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
        
        CompletionStage<Void> destroy(Throwable error, boolean passErrorToDestructor) {
            Map<String, AsyncExecutionScope.ScopedObject> copy = new HashMap<>(scopedObjects);
            scopedObjects.clear();
            
            AtomicReference<Throwable> primaryErrorRef = new AtomicReference<>(error);
            
            List<CompletableFuture<Void>> allAsyncClose =
            copy.values()
                .stream()
                .map(v -> applyDestructors(v, passErrorToDestructor ? error : null, primaryErrorRef))
                .filter(Objects::nonNull)
                .map(CompletionStage::toCompletableFuture)
                .collect(Collectors.toList());
            
            if (!allAsyncClose.isEmpty()) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Void>[] f = allAsyncClose.toArray(new CompletableFuture[allAsyncClose.size()]);
                return CompletableFuture.allOf(f);
            } else {
                Throwable actualError = primaryErrorRef.get();
                if (null == actualError) {
                    return CompletableFuture.completedFuture(null); 
                } else {
                    CompletableFuture<Void> result = new CompletableFuture<>();
                    result.completeExceptionally(actualError);
                    return result;
                }
            }
        }
        
        static CompletionStage<Void> applyDestructors(ScopedObject v, Throwable originalError, AtomicReference<Throwable> primaryErrorRef) {
            CompletionStage<Void> result = v.asyncDestroy(originalError);
            if (null == result) {
                invokeSyncDestroy(v, primaryErrorRef);
                return null;
            } else {
                return result.whenComplete((r, e) -> {
                   if (null != e) {
                       enlistError(e, primaryErrorRef);
                   }
                   invokeSyncDestroy(v, primaryErrorRef);
                });
            }
        }
        
        static void invokeSyncDestroy(ScopedObject v, AtomicReference<Throwable> primaryErrorRef) {
            try {
                v.destroy();  
              } catch (Throwable suppressedError) {
                  enlistError(suppressedError, primaryErrorRef);
              }
        }
        
        static void enlistError(Throwable nextError, AtomicReference<Throwable> primaryErrorRef) {
            if (InternalCallContext.isExitSignal(nextError)) {
                return;
            }
            if (!primaryErrorRef.compareAndSet(null, nextError)) {
                primaryErrorRef.get().addSuppressed(nextError);
            }
            
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

    private final ThreadVar<Frame> threadVar = new ThreadVar<>("AsyncExecutionScope", INVALID_FRAME);

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Frame frame = threadVar.value();
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
        Frame frame = threadVar.value();
        if (isValidFrame(frame)) {
            return frame.remove(name);
        } else {
            return null;
        }
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        Frame frame = threadVar.value();
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
        return isValidFrame(threadVar.value());
    }
    
    private static boolean isValidFrame(Frame frame) {
        return frame != null && frame != INVALID_FRAME;
    }
    
    <R> R withFrame(boolean createNewFrame, boolean inheritOldFrame, ThreadVar.ThrowableFunction<Frame, R> call) throws Throwable {
        if (createNewFrame) {
            return withNewFrame(call, inheritOldFrame);
        } else {
            return withNewOrExistingFrame(call);
        }
    }
    
    <R> R withoutFrame(ThreadVar.ThrowableFunction<Frame, R> call) throws Throwable {
        Frame previous = threadVar.value();
        if (isValidFrame(previous)) {
            return callWithScope(previous, INVALID_FRAME, call);
        } else {
            // No scope added
            return call.apply(null);
        }
    }
    
    private <R> R withNewOrExistingFrame(ThreadVar.ThrowableFunction<Frame, R> call) throws Throwable {
        Frame previous = threadVar.value();
        if (!isValidFrame(previous)) {
            return callWithScope(previous, new Frame(), call);
        } else {
            // No scope added
            return call.apply(null);
        }
    }
    
    private <T> T withNewFrame(ThreadVar.ThrowableFunction<Frame, T> call, boolean inheritOldFrame) throws Throwable {
        Frame previous = threadVar.value();
        Frame newFrame = inheritOldFrame && isValidFrame(previous) ? new NestedFrame(previous) : new Frame();
        return callWithScope(previous, newFrame, call);
    }
    
    public Runnable contextualize(Runnable code) {
        Frame frame = threadVar.value();
        if (null == frame) {
            return code;
        } else {
            return () -> threadVar.runWith(frame, code);
        }
    }
    
    private <T> T callWithScope(Frame previousFrame, Frame newFrame, ThreadVar.ThrowableFunction<Frame, T> call) throws Throwable {
        return threadVar.applyWith(previousFrame, newFrame, call);
    }

    public static AsyncExecutionScope instance() {
        return INSTANCE;
    }
    
    private static final AsyncExecutionScope INSTANCE = new AsyncExecutionScope();
}
