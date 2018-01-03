package net.tascalate.async.generator;

import net.tascalate.async.api.Generator;
import net.tascalate.async.api.PromisesGenerator;
import net.tascalate.async.api.ValuesGenerator;

public final class GeneratorDecorators {
    
    private GeneratorDecorators() {}
    
    public static <T> ValuesGenerator<T> values(Generator<T> original) {
        return new ValuesGeneratorImpl<>(original);
    }
    
    public static <T> PromisesGenerator<T> promises(Generator<T> original) {
        return new PromisesGeneratorImpl<>(original);
    }
}
