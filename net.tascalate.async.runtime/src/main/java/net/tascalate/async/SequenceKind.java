package net.tascalate.async;

public enum SequenceKind {
    READY_VALUES_CUSTOMIZABLE,
    READY_VALUES_REGULAR,
    PENDING_VALUES_CUSTOMIZABLE,
    PENDING_VALUES_REGULAR,
    GENERIC_CUSTOMIZABLE,
    GENERIC_REGULAR;
    
    public static SequenceKind kindOf(Sequence<?> sequence) {
        Object kind = sequence.kind();
        if (null == kind) {
            return sequence instanceof CustomizableSequence 
                    ? SequenceKind.GENERIC_CUSTOMIZABLE
                    : SequenceKind.GENERIC_REGULAR;                
        } else {
            return (SequenceKind)kind;
        }
    }
}
