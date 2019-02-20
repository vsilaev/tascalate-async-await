package net.tascalate.async.examples.generator;

import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.tascalate.async.CallContext;
import net.tascalate.async.async;

public interface IDemo {
    default @async CompletionStage<String> run() {
        String result = 
        Stream.of("AZ", "B", "CZ")
              .filter(v -> v.endsWith("Z"))
              .map(x -> "K" + x)
              .collect(Collectors.joining("|"));
        return CallContext.async(result);
    }
}
