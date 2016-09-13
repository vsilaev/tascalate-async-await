package net.tascalate.async.core;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import net.tascalate.async.api.Generator;

public class ReadyValuesGenerator<T> implements Generator<T> {
    
    public final static Generator<?> EMPTY = new Generator<Object>() {

        @Override
        public boolean next(Object producerParam) {
            return false;
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public Object current() {
            throw new NoSuchElementException();
        }

        @Override
        public void close() {

        }
        
    };
    
    final private Iterator<? extends T> readyValues;
    
    private T current = null;
    private boolean hasValue = false;

    public ReadyValuesGenerator(final Stream<? extends T> readyValues) {
        this(readyValues.iterator());
    }
    
    public ReadyValuesGenerator(final Iterable<? extends T> readyValues) {
        this(readyValues.iterator());
    }
    
    protected ReadyValuesGenerator(final Iterator<? extends T> readyValues) {
        this.readyValues = readyValues;
    }

    @Override
    public boolean next(Object producerParam) {
        if (readyValues.hasNext()) {
            current = readyValues.next();
            return hasValue = true;
        } else {
            current = null;
            return hasValue = false;
        }
    }

    @Override
    public T current() {
        if (hasValue) {
            return current;
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void close() {}
    
};
