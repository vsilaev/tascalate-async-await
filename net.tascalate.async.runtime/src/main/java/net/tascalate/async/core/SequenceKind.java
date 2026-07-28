package net.tascalate.async.core;

import net.tascalate.async.CustomizableSequence;
import net.tascalate.async.Sequence;

public enum SequenceKind {
    READY_VALUES,
    SUSPENDABLE_CUSTOMIZABLE,
    SUSPENDABLE_REGULAR,
    NON_SUSPENDABLE_CUSTOMIZABLE,
    NON_SUSPENDABLE_REGULAR;
    
    public static SequenceKind kindOf(Sequence<?> sequence) {
        if (sequence instanceof SuspendableSequence) {
            return sequence instanceof CustomizableSequence 
                   ? SequenceKind.SUSPENDABLE_CUSTOMIZABLE
                   : SequenceKind.SUSPENDABLE_REGULAR;        
        } else if (sequence instanceof ReadyValueSequence) {
            return SequenceKind.READY_VALUES;
        } else {
            return sequence instanceof CustomizableSequence 
                    ? SequenceKind.NON_SUSPENDABLE_CUSTOMIZABLE
                    : SequenceKind.NON_SUSPENDABLE_REGULAR;        
        }
    }
}
