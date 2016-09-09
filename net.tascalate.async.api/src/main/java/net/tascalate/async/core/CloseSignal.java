package net.tascalate.async.core;

@SuppressWarnings("serial")
class CloseSignal extends Error {
    private CloseSignal() {}

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    static final Error INSTANCE = new CloseSignal();
}
