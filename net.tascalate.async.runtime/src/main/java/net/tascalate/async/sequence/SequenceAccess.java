/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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
package net.tascalate.async.sequence;

import net.tascalate.async.CustomizableSequence;
import net.tascalate.async.Sequence;
import net.tascalate.async.SequenceKind;
import net.tascalate.async.suspendable;
import net.tascalate.async.core.AbstractAsyncMethod;

public final class SequenceAccess {
    
    private SequenceAccess() {
        
    }
    
    public static <T> T nextReadyValue(Sequence<? extends T> sequence) {
        // Avoid @suspendable ceremony
        ReadyValuesSequence<? extends T> typedSequence = 
            (ReadyValuesSequence<? extends T>)sequence;
        return typedSequence.takeNext();
    }
    
    public static <T> T nextReadyValue(Sequence<? extends T> sequence, Object param) {
        // Avoid @suspendable ceremony
        ReadyValuesSequence<? extends T> typedSequence = 
            (ReadyValuesSequence<? extends T>)sequence;
        return typedSequence.takeNext(param);
    }
    
    public static @suspendable <T> T nextPendingValue(Sequence<? extends T> sequence, AbstractAsyncMethod caller) {
        PendingValuesSequence<? extends T> typedSequence = 
                (PendingValuesSequence<? extends T>)sequence;        
        return typedSequence.takeNext(caller);
    }
    
    public static @suspendable <T> T nextPendingValue(Sequence<? extends T> sequence, Object param, AbstractAsyncMethod caller) {
        PendingValuesSequence<? extends T> typedSequence = 
                (PendingValuesSequence<? extends T>)sequence;        
        return typedSequence.takeNext(param, caller);
    }
    
    public static @suspendable <T> T __next(Sequence<? extends T> sequence, AbstractAsyncMethod caller) {
        SequenceKind kind = SequenceKind.kindOf(sequence);
        switch (kind) {
            case READY_VALUES_CUSTOMIZABLE:
            case READY_VALUES_REGULAR: {     
                ReadyValuesSequence<? extends T> typedSequence = (ReadyValuesSequence<? extends T>)sequence;
                return typedSequence.takeNext();
            }
            case PENDING_VALUES_CUSTOMIZABLE:
            case PENDING_VALUES_REGULAR: {
                PendingValuesSequence<? extends T> typedSequence = (PendingValuesSequence<? extends T>)sequence;        
                return typedSequence.takeNext(caller);
            }
            default:
                return sequence.next();    
        }
    }
    
    public static @suspendable <T> T __next(CustomizableSequence<? extends T> sequence, Object param, AbstractAsyncMethod caller) {
        SequenceKind kind = SequenceKind.kindOf(sequence);
        switch (kind) {
            case READY_VALUES_CUSTOMIZABLE: 
            case READY_VALUES_REGULAR:{     
                @SuppressWarnings("unchecked")
                ReadyValuesSequence<? extends T> typedSequence = (ReadyValuesSequence<? extends T>)sequence;
                return typedSequence.takeNext(param);
            }
            case PENDING_VALUES_CUSTOMIZABLE:
            case PENDING_VALUES_REGULAR: {
                @SuppressWarnings("unchecked")
                PendingValuesSequence<? extends T> typedSequence = (PendingValuesSequence<? extends T>)sequence;        
                return typedSequence.takeNext(param, caller);
            }
            default:
                return sequence.next(param);    
        }
    }

}
