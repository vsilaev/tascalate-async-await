package com.farata.lang.async.core;

import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import com.farata.lang.async.api.Generator;

abstract public class AsyncGenerator<T> extends AsyncMethodBody {
    final public LazyGenerator<T> generator;

    protected AsyncGenerator() {
        generator = new LazyGenerator<>();
    }

    protected @continuable void $$start$$() {
        generator.begin();
    }

    protected void $$finish$$() {
        generator.end();
    }

    protected @continuable static <T> Object $$yield(T readyValue, AsyncGenerator<T> self) {
        return self.generator.produce(readyValue);
    }

    protected @continuable static <T> Object $$yield(CompletionStage<T> pendingValue, AsyncGenerator<T> self) {
        return self.generator.produce(pendingValue);
    }

    protected @continuable static <T> Object $$yield(Generator<T> values, AsyncGenerator<T> self) {
        return self.generator.produce(values);
    }
}
