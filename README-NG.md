[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate.async/net.tascalate.async.parent.svg)](https://search.maven.org/artifact/net.tascalate.async/net.tascalate.async.parent/1.3.0/pom) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/releases/tag/1.3.0) [![license](https://img.shields.io/github/license/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/blob/master/LICENSE)

![Tascalate Logo](https://raw.githubusercontent.com/vsilaev/tascalate-async-await/refs/heads/master/logo_wide_dark.svg#gh-dark-mode-only)
![Tascalate Logo](https://raw.githubusercontent.com/vsilaev/tascalate-async-await/refs/heads/master/logo_wide_light.svg#gh-light-mode-only)
# Why async-await?
Asynchronous programming has been a reliable method for developing high-performance applications that fully utilize multiprocessor architectures. This approach typically involves assigning sub-tasks to separate threads, allowing the main thread to remain unblocked and improving overall performance. Typically, these sub-tasks fall into two categories: compute-bound operations or I/O-bound operations. Compute-bound operations involve performing calculations on a separate thread, allowing the main thread to continue its execution. On the other hand, I/O-bound operations relate to external tasks, such as reading files or initiating network communication, that don't necessarily require blocking a thread while they progress.

Traditionally, asynchronous programming is implemented by utilizing callbacks, which run once the operation has been completed. While APIs vary across languages and libraries, the basic principle remains consistent: start an asynchronous operation, receive a `Promise` or similar construct, and then link success or failure callbacks executed once the operation finishes. However, this design introduces several significant challenges:
1. Contextual variables need to be explicitly passed to callbacks. Although this issue can be mitigated to some extent with lambdas that capture lexical context, it may not entirely resolve the problem. Moreover, when using numerous complex lambdas, the readability and clarity of your code may suffer.  
2. Managing asynchronous workflows through callbacks is challenging: branching logic within a chain of asynchronous callbacks becomes cumbersome, constructs like `try-with-resources` cannot be seamlessly applied, and common control flow mechanisms, including `try/catch` blocks for error-handling, require special handling compared to synchronous code.  
3. Callbacks often execute on different threads. This necessitates careful handling of thread contexts during operation resumption - especially critical for applications running in managed environments like JEE or UI frameworks (JavaFX, Swing, etc). 

To address the outlined issues of readability and maintainability, several programming languages introduce the `async/await` model for asynchronous programming. This model simplifies making asynchronous calls, allowing developers to use them as effortlessly as synchronous ones by merely adding the `await` keyword. At the same time, it retains all the advantages of asynchronous operations. By leveraging the `await` keyword, developers can seamlessly integrate asynchronous calls within standard control flow constructs (including exception handling), in much the same way they would work with synchronous methods. An increasing number of languages now support this approach, including C# 5, TypeScript / ECMAScript 7, Kotlin, and Scala. 

The Tascalate Async/Await library brings this `async/await` programming style to Java projects starting from Java 8. Built on [continuations for Java](https://github.com/vsilaev/tascalate-javaflow), the library provides runtime APIs and bytecode enhancement tools that enable Java developers to work with syntax resembling C# 5 or ECMAScript 2017/2018, all within the pure Java.

# How to use ?
## ...with Gradle 7+
The first thing you have to do is to define Tascalate Async/Await build plugins in the `settings.gradle` file:
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "async-await") {
                useModule("net.tascalate.async:net.tascalate.async.tools.gradle:1.3.0")
            } else if (requested.id.id == "continuations") {
                useModule("net.tascalate.javaflow:net.tascalate.javaflow.tools.gradle:2.8.3")
            }
        }
    }
}
rootProject.name = '<your-project-name>'
```
Next, the following must be added to the `build.gradle`:
```groovy
plugins {
    id 'java'
    id 'async-await'
    id 'continuations'
    /* ASYNC-AWAIT should be after JAVA                */
    /* CONTINUATIONS should be added after ASYNC-AWAIT */
    /* other plugins if necessary                      */
}
...
dependencies {
    implementation 'net.tascalate.async:net.tascalate.async.runtime:1.3.0'
    // The rest is optional and per your project requirements
    /* Async/Await Extras + Tascalate Concurrent */
    /*
    implementation 'net.tascalate.async:net.tascalate.async.extras:1.3.0'
    implementation 'net.tascalate:net.tascalate.concurrent:0.9.11'
    */
    
    /* Necessary only for different providers */
    /*
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.provided:1.3.0'
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.propagated:1.3.0'
    */
}
```
## ...with Gradle before 7
As of Gradle 7+, you need to specify both build plugins and runtime dependencies. However, the procedure is slightly different. The minimal Gradle script should include the following prologue:
```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath 'net.tascalate.async:net.tascalate.async.tools.gradle:1.3.0'
        classpath 'net.tascalate.javaflow:net.tascalate.javaflow.tools.gradle:2.8.3'
        /* other plugins */
    }
}

apply plugin: "java"
/* ORDER IS IMPORTANT: Async/Await before Continuations! */
apply plugin: "async-await"
apply plugin: "continuations"

repositories {
    mavenCentral()
}

dependencies {
    implementation 'net.tascalate.async:net.tascalate.async.runtime:1.3.0'
    /* other dependencies */
}
```
The more advanced example with `Async/Await Extras` module + [Tascalate Concurrent](https://github.com/vsilaev/tascalate-concurrent) and `Async/Await SchedulerResolver-s` (discussed below) will be:
```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath 'net.tascalate.async:net.tascalate.async.tools.gradle:1.3.0'
        classpath 'net.tascalate.javaflow:net.tascalate.javaflow.tools.gradle:2.8.3'
        /* other plugins */
    }
}

apply plugin: "java"
/* ORDER IS IMPORTANT: Async/Await before Continuations! */
apply plugin: "async-await"
apply plugin: "continuations"

repositories {
    mavenCentral()
}

dependencies {
    implementation 'net.tascalate.async:net.tascalate.async.runtime:1.3.0'
    
    /* Async/Await Extras */
    implementation 'net.tascalate.async:net.tascalate.async.extras:1.3.0'
    
    /* Promise<T> implementation */
    /* Necessary because net.tascalate.async.extras uses it as an */
    /* 'optional' dependency to avoid concrete version lock-in.   */
    implementation 'net.tascalate:net.tascalate.concurrent:0.9.11'
    
    /* Necessary only for different providers */
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.provided:1.3.0'
    /*
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.propagated:1.3.0'
    */

    
    /* other dependencies */
}
/* Optional config */
'async-await' {
    /* ... */
}

'continuations' {
    /* ... */
}
```
## ...with Maven
First, add Maven dependency to the library runtime:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.runtime</artifactId>
    <version>1.3.0</version>
</dependency>
```
Second, add the following build plugins in the specified order:
```xml
<build>
  <plugins>
	  
    <plugin>
      <groupId>net.tascalate.async</groupId>
      <artifactId>net.tascalate.async.tools.maven</artifactId>
      <version>1.3.0</version>
      <executions>
        <execution>
          <id>tascalate-async-enhance-main-classes</id> 
          <phase>process-classes</phase>
          <goals>
            <goal>tascalate-async-enhance</goal>
          </goals>
        </execution>
	<!-- Only if you need to enhance test classes -->      
        <execution>
          <id>tascalate-async-enhance-test-classes</id> 
          <phase>process-test-classes</phase>
          <goals>
            <goal>tascalate-async-enhance</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
	  
    <plugin>
      <groupId>net.tascalate.javaflow</groupId>
      <artifactId>net.tascalate.javaflow.tools.maven</artifactId>
      <version>2.8.3</version>
      <executions>
        <execution>
          <id>javaflow-enhance-main-classes</id> 
          <phase>process-classes</phase>
          <goals>
            <goal>javaflow-enhance</goal>
          </goals>
        </execution>
        <!-- Only if you need to enhance test classes -->		
        <execution>
          <id>javaflow-enhance-test-classes</id> 
          <phase>process-test-classes</phase>
          <goals>
            <goal>javaflow-enhance</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
	  
  </plugins>
</build>
```
You are ready to start coding!
# Asynchronous Task Methods
The first type of function the library supports is an asynchronous task. An asynchronous task refers to a method (either an instance method or a class method) that is marked with the `net.tascalate.async.async` annotation and returns either a `CompletionStage<T>` or `void`. When returning `void`, it functions as a "fire-and-forget" task, designed mainly for use in event-handling scenarios within UI frameworks such as JavaFX or Swing. Let us write a simple example:
```java
import static net.tascalate.async.CallСontext.async;
import static net.tascalate.async.CallСontext.await;
import net.tascalate.async.async;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class MyClass {
    public @async CompletionStage<String> mergeStrings() {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited") );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix) {
        String value = prefix + await( produceString("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        return CompletableFuture.supplyAsync(() -> value, executor);
    }
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
}
```
Utilizing the statically imported methods from `net.tascalate.async.CallContext`, the code is designed to mimic the style of languages that natively support async/await functionality. Both `mergeStrings` and `decorateStrings` are asynchronous methods -- they are annotated with the `net.tascalate.async.async` annotation and return a `CompletionStage<T>`. Within these methods, you can invoke `await` to suspend execution until the provided `CompletionStage<T>` is resolved, whether successfully or with an exception. The key point here is that during the execution of `await`, the thread does not remain blocked. Instead, it is either returned to the corresponding `ThreadPool` or terminated in the case of Green Threads introduced in Java 21 and beyond. The exact behavior varies based on the specific underlying scheduler being used, which will be explained later.

Keep in mind that any `CompletionStage<T>` implementation from various libraries can be awaited, as demonstrated in `decorateStrings`, including unresolved results originating from another asynchronous method, such as in `mergeStrings`.

The list of supported return types for the async methods is:
1. `void`
2. `java.util.concurrent.CompletionStage`
3. `java.util.concurrent.CompletableFuture`
4. `net.tascalate.concurrent.Promise` (see my other project [Tascalate Concurrent](https://github.com/vsilaev/tascalate-concurrent))

For non-void result types, the resulting class also implements `java.util.concurrent.Future`, even in the case of [2] with `CompletionStage`. This allows you to safely upcast the resulting promise to `java.util.concurrent.Future` and utilize blocking methods if needed. Notably, you can use the `cancel(...)` method to terminate the future that is returned.

To produce a result from an asynchronous method, you should use the syntax `return async(value)`. It is essential to treat the act of invoking the `async` method and `return`-ing its output as a single unified construct. Avoid calling the `async` method in isolation or assigning its result to a variable, as this can result in unpredictable behavior. This guideline is particularly important for methods with non-linear control flows. Based on your coding conventions for handling multiple return statements, you should either...
```java
public @async CompletionStage<String> foo(int i) {
    switch (i) {
        case 1: return async("A");
        case 2: return async("B");
        case 3: return async("C");
        default:
            return async("<UNKNOWN>");
    }
}
```
...or...
```java
public @async CompletionStage<String> bar(int i) {
    String result;
    switch (i) {
        case 1: result = "A"; break;
        case 2: result = "B"; break;
        case 3: result = "C"; break;
        default:
            result = "<UNKNOWN>";
    }
    return async(result);
}
```
It’s fairly common to encounter a scenario where the final operation in your asynchronous task method is invoking an async function and returning its output. In such cases, you can directly return the `CompletionStage<T>` or a specific implementation of it from the async method.
```java
@async CompletionStage<Long> calculateDiscount(Order order) {
    CustomerProfile profile = await( loadCustomerProfile(order.getCustomerId()) );
    if (profile.isPremium()) {
        return calculatePremiumDiscount(order);
    } else {
        return calculateRegularDiscount(order);
    }
}

CompletionStage<CustomerProfile> loadCustomerProfile(Long customerId) {
    ...
}

CompletionStage<Long> calculateRegularDiscount(Order order) {
    ...
}

@async CompletionStage<Long> calculatePremiumDiscount(Order order) {
    ...
}
```

It’s important to highlight that when writing code using async/await, you should steer clear of the so-called ["async/await hell"](https://medium.com/@7genblogger/escaping-so-called-async-await-hell-in-node-js-b5f5ba5fa9ca). Put simply, be mindful of which sections of your code can run concurrently and which ones must be executed sequentially. Take the following example, for instance:
```java
public @async CompletionStage<Long> calculateTotalPrice(Order order) {
    Long rawItemsPrice = await( calculateRawItemsPrice(order) );  
    Long shippingCost  = await( calculateShippingCost(order) );  
    Long taxes         = await( calculateTaxes(order) );  
    return async(rawItemsPrice + shippingCost + taxes);
}

protected @async CompletionStage<Long> calculateRawItemsPrice(Order order) {
    ...
}

protected @async CompletionStage<Long> calculateShippingCost(Order order) {
    ...
}

protected @async CompletionStage<Long> calculateTaxes(Order order) {
    ...
}
```
In the given example, the async methods `calculateRawItemsPrice`, `calculateShippingCost` and `calculateTaxes` are executed sequentially, one after the other. As a result, the performance suffers compared to the parallelized solution shown below:
```java
public @async CompletionStage<Long> calculateTotalPrice(Order order) {
    CompletionStage<Long> rawItemsPrice = calculateRawItemsPrice(order);  
    CompletionStage<Long> shippingCost  = calculateShippingCost(order);  
    CompletionStage<Long> taxes         = calculateTaxes(order);  
    return async( await(rawItemsPrice) + await(shippingCost) + await(taxes) );
}
```
This way, all internal async operations initiate nearly at the same time and execute concurrently, in contrast to the first example.

## Comparison to Other Solutions
| Feature | Tascalate Async/Await | Java 21 Virtual Threads | Kotlin Coroutines |
|---------|----------------------|------------------------|-------------------|
| Java version | 8+ | 21+ | 8+ |
| Syntax | Annotation-based `@async` + `await()` | Standard blocking style | `suspend` + `await` |
| Bytecode weaving / Compiler | Required | Not needed | Compiler plugin |
| C# parity | High | Low | Medium |

# Suspendable methods
In certain situations, it becomes essential to wait for an asynchronous outcome within a helper method that is not inherently asynchronous. To address this scenario, Tascalate Async/Await offers the `@suspendable` annotation. Consequently, the earlier example can be restructured as shown below:
```java
import static net.tascalate.async.CallСontext.async;
import static net.tascalate.async.CallСontext.await;
import net.tascalate.async.async;
import net.tascalate.async.suspendable; // NEW ANNOTATION IMPORT

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class MyClass {
    public @async CompletionStage<String> mergeStrings() {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
	    // No await here -- moved to helper method
            String v = decorateStrings(i, "async ", " awaited"); 
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    // Async method refactored to suspendable
    private @suspendable String decorateStrings(int i, String prefix, String suffix) {
        String value = prefix + await( produceString("value " + i) ) + suffix;
        return value; // Just regular "return <value>" instead of "return async(<value>)"
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        return CompletableFuture.supplyAsync(() -> value, executor);
    }
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
}
```
As you can see, suspendable methods are similar to regular ones but require a special annotation - `@suspendable`. You should adhere to the usual rules for returning results from these methods. Additionally, calling `return async(<value>)` inside these methods is considered an error. The key aspect of `@suspendable` methods is that they can only be invoked from `@async` methods or other `@suspendable` methods.

In terms of performance, suspendable methods function the same as asynchronous task methods. As a result, the decision between using one or the other mainly comes down to how you intend to organize your code. A commonly suggested approach is to use asynchronous task methods for interfaces exposed to external clients, while reserving suspendable methods for internal implementation purposes. That said, the choice ultimately lies with the library user, provided they adhere to the rule: suspendable methods are only usable within an asynchronous context (either `@async` methods or other `@suspendable` methods).

On the implementation side, suspendable methods are technically built as continuable methods, following the conventions outlined in the [Tascalate JavaFlow](https://github.com/vsilaev/tascalate-javaflow) library. Consequently, you’re not limited to the `@suspendable` annotation but can apply any continuable annotation supported by the Tascalate JavaFlow framework.

# Asynchronous Generator Methods
## Overview
An async generator is a programming construct that produces a sequence of values asynchronously, allowing consumers to iterate over results as they become available without blocking.

It enables:
- **Asynchronous iteration** over data streams, where each value may require non-blocking awaiting an asynchronous operation (I/O, network, timer, etc.).
- **Pull-based consumption**, where the caller requests the next value and the generator produces it on demand.
- **Incremental production**, allowing the generator to pause between values and resume when iteration continues.
- **Efficient streaming**, avoiding the need to buffer or compute the entire sequence before iteration.

Async generators are available in numerous programming languages, the most notable examples are ECMAScript async generators (`async function*`) and .NET’s `IAsyncEnumerable<T>`. Typically, async generators follow this pattern:
- producer is an async function that defines an async generator.
- values are produced using `yield` (or `emit` or whatever) within this function.
- the consumer receives some kind of the async generator instance from the call to producer.
- the consumer awaits each item as it is yielded, using some or another form of iteration and await for each item return.
## Basic usage
Here is an example how asynchronous generator is done with Tascalate Async / Await:
```java
import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.await;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.AsyncYield;
import net.tascalate.async.async;

@async AsyncGenerator<String> produceAsyncStrings() {
    AsyncYield<String> async = AsyncGenerator.start();
    async.yield( "Start" ); // Yield ready value

    // Yield pending values    
    async.yield( asyncProduceValue("A") );
    async.yield( asyncProduceValue("B") );
    async.yield( asyncProduceValue("C") );

    /* Sequence<CompletionStage<String>> */ 
    var stringsDEF = AsyncGenerator.readyFirst(
        asyncProduceValue("F", 300),
        asyncProduceValue("E", 200),
        asyncProduceValue("D", 100)
    );
    // Emit a sequence of pending values (resolved come first) 
    async.yield( stringsDEF );

    // Forward values emited by another generator    
    async.yield( produceAsyncStringsXYZ() );
        
    async.yield( "Finish" );

    // Return from the function
    return async.yield();
}
    
@async AsyncGenerator<String> produceAsyncStringsXYZ() {
    var async = AsyncGenerator.<String>start();
    for (var v : List.of("X", "Y", "Z")) {
        async.yield( asyncProduceValue(v, 50) );
    }
    return async.yield();
}

// Helper functions to emulate asynchronously produced values   
<T> CompletionStage<T> asyncProduceValue(T  v) {
    return asyncProduceValue(v, 0L);
}

<T> CompletionStage<T> asyncProduceValue(T v, long  delayMillis) {
    return CompletableFuture.supplyAsync(() -> {
        if (delayMillis != 0) {
            try {
               Thread.sleep(delayMillis); 
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
        }
        return v;
    }, ForkJoinPool.commonPool());
}
```
 An **async generator method** is a method annotated with `@async` that returns `AsyncGenerator<T>`. Inside the method body, create an `AsyncYield<T>` helper by calling `AsyncGenerator.<T>start()`. Any variable name is allowed; choose a consistent name across the codebase such as `async` or a single letter like `g`.

Use the `AsyncYield<T>` variable to yield values of these forms:

1.  `CompletionStage<T>` or a subclass -- a single pending value.
    
2.  `T` -- a ready value.
    
3.  `net.tascalate.async.Sequence<? extends CompletionStage<? extends T>>` -- a sequence of pending values.
    
4.  `AsyncGenerator<T>` -- a nested generator, which is a special case of the sequence option.
    
End the method with `return async.yield()`. Treat `return async.yield()` as a single, atomic syntax construct -- do **not** assign the result of `async.yield()` to a variable for later reuse while this leads to unexpected behavior. Instead, call `async.yield()` directly at the point where you want to yield and immediately return its result.

```java
// Correct
return async.yield();

// Incorrect — do not do this
var someVar = async.yield();
return someVar;
```
Now let us take a look how the async generator can be consumed:
```java
@async CompletionStage<Long> consumeGenerator() {  
    try (AsyncGenerator<String> generator = produceAsyncStrings()) {
        CompletionStage<String> pendingValue;
        while ((pendingValue = generator.next()) != null) {
            String value = await(pendingValue);  
            System.out.println(value);  
        }  
    }
    return async(42L);  
}
```
From the *consumer* perspective, an `AsyncGenerator<T>` represents a `null`‑terminated sequence of pending values. The core iteration loop repeatedly calls `generator.next()` and stops when that call returns `null`. Each `next()` invocation yields a pending item of type `CompletionStage<T>`  or `null` if the generator has finished. After receiving a pending item you **must await** that promise until it settles to obtain the actual `T` value or an error. Calling `generator.next()` without awaiting a previously returned promise is allowed, but it does **not** avoid suspension: the *consumer* will still be suspended when it attempts to advance the generator without awaiting the not‑yet‑settled result. For predictable ordering and simpler error handling, prefer to `await` each returned promise before calling `next()` again.

What happens on the *producer* side i.e. inside the `produceAsyncStrings()` generator while the *consumer* iterates over? An `AsyncGenerator<T>` method starts **suspended** when created. Each time the *consumer* calls `generator.next()`, the generator **resumes**, executes until it reaches a `async.yield(...)` (or returns), and then **suspends** again. The value passed to `async.yield(...)` is what the *consumer* receives (a `CompletionStage<T>`, or `T` wrapped inside a completed future). The generator does not continue past the `yield` until the *consumer* advances the iteration again. When the generator yields a pending item (a `CompletionStage<T>`), the *consumer* receives that pending stage and will normally await it to obtain the actual `T` or an error. The generator remains suspended after yielding. In typical usage the generator will not resume until the *consumer* both (a) observes/awaits the yielded stage (so ordering and backpressure are preserved) and (b) calls `next()` again to request the following item. When the generator yields a `Sequence` of `CompletionStage`-s or another `AsyncGenerator`, the generator method is suspended until that entire sequence or nested generator has been fully consumed by the *consumer*.

Use the `generator` inside the *consumer* within a `try-with-resources` block so it is always closed when the *consumer* stops iterating or an error occurs. This guarantees the generator’s finalization logic runs even if the *consumer* returns early, throws an exception, or abandons iteration.
## Alternative iteration options
Instead of handling a null‑terminated sequence of `CompletionStage`-s  directly, you can use more common `iterator`  idiom in the `consumeGenerator()`:
```java
@async CompletionStage<Long> consumeGenerator() {  
    try (SuspendableIterator<CompletionStage<String>> iterator = produceAsyncStrings().iterator()) {
        while (iterator.next()) {
            CompletionStage<String> pendingValue = iterator.next();
            String value = await(pendingValue);  
            System.out.println(value);  
        }  
    }
    return async(42L);  
}
```
It's critical to admit that `SuspendableIterator` is an _iterator‑like_ API, not a subtype of `java.util.Iterator`, and `AsyncGenerator` does **not** implement `java.lang.Iterable`. It provides `hasNext()` and `next()` methods that may **suspend** the caller while awaiting asynchronous results, so it cannot be used with Java’s `for‑each` loop. The `SuspendableIterator` returned by an `AsyncGenerator` is `AutoCloseable`; use it inside a `try‑with‑resources` block so the underlying generator is always closed when iteration ends, the *consumer* returns early, or an exception occurs.

The alternative and the original version have the same performance traits. Simply pick the style you like better.

Both *consumer* styles discussed above allow attaching an asynchronous pipeline to each returned pending value before awaiting it. Additionally, errors can be managed while awaiting individual pending tasks, as will be demonstrated later, thereby allowing for sophisticated error handling and recovery strategies. If you do not need that flexibility, use the concise iterator form, `AsyncGenerator<T>.valuesIterator()`, that mirrors ECMAScript and C# async iterators:
```java
@async CompletionStage<Long> consumeGenerator() {  
    /* SuspendableIterator<String> */
    try (var viterator = produceAsyncStrings().valuesIterator()) {
        while (viterator.next()) {
            /* String */
            var value = viterator.next();
            System.out.println(value);  
        }  
    }
    return async(42L);  
}
```
The *consumer* iterates over each settled value directly in a simple `await foreach / for await`‑style loop. This form is shorter, easier to read, and is the best choice when you only need to process settled `T` values in order .

Compare the code above to the ECMA Script:
```javascript
async function consumeGenerator() {
  for await (const value of produceAsyncStrings()) {
    console.log(value);
  }
}
```
...or with C#:
```csharp
async Task<long> consumeGenerator() {
    await foreach (var value in produceAsyncStrings())  {
        Console.WriteLine(value);
    }
    return 42;
}
```
The Java version using Tascalate Async/Await is definitely more verbose, but the underlying semantics closely match those in ECMAScript and C#. .

**IMPORTANT:** Do not share an `AsyncGenerator`, its `iterator()` or `valuesIterator()` across multiple threads! These types *facilitate* asynchronous control flow but are not thread‑safe: they maintain internal suspension and lifecycle state that must be accessed from a single execution context at a time. Only three kinds of callers are guaranteed to provide the correct execution context for consuming an `AsyncGenerator`: asynchronous tasks, other asynchronous generators, and suspendable methods. If you must cross thread boundaries, convert yielded values into a thread‑safe handoff (will be shown below) rather than sharing the generator or its iterator directly.

## Controlling asynchronous generator from consumer
Tascalate Async / Await `AsyncGenerator` supports passing a value from the *consumer* back to the *producer* (generator) by calling `generator.next(param)`. When the consumer supplies `param`, that value becomes a part of the result of the corresponding `async.yield(...)` expression inside the generator method (the *producer* receives it when it resumes). This mirrors ECMAScript’s `next(value)` behavior and enables two‑way communication: the *consumer* can send control data, acknowledgements, or backpressure hints to the *producer*. Let's review the following example:
```java
@async CompletionStage<String> collectLetters(int  n) {
    try (var g = asyncLetter()) {
        var result = "";
        for (int i = 0; i < n; i++) {
            result += await( g.next(i) );
        }
        return  async(result);
    }
}

@async AsyncGenerator<String> asyncLetter() {
    var async = AsyncGenerator.<String>start();
    /*AsyncYield.Reply<String>*/
    var reply = async.yield(Sequence.empty()); // <== this requires explanation
    try {
        while (true) {
            var ch = (char)( ((Number)reply.param).intValue() % 26 + 65);
            reply = async.yield( asyncProduceValue(new String(new char[]{ch})) );
        }
    } finally {
        System.out.println("asyncLetter() generator is closed");
    }
}
```
The `asyncLetter` generator produces an effectively infinite stream of single‑letter strings. The *consumer* controls which letter is produced by passing a character code to `generator.next(param)`. The *consumer* iterates over the generated values and collects `n` generated letters as a string with characters `'A'..'A'+n` cycling. Inside the *producer*, each `async.yield(...)` returns an `AsyncYield.Reply<String>` object that exposes two fields: `value` for the resolved yielded value and `param` for the parameter supplied by the *consumer*. Note that, as in ECMAScript, there is no *consumer* parameter available for the very first `next()` call. To work around this, yield an empty sequence at the start of the generator so the *producer* can receive a parameter on the subsequent resume. Finally, even though the generator is infinite, its `finally` or close logic runs when the *consumer* breaks out of the loop and the generator is closed via `try‑with‑resources`, ensuring deterministic cleanup.

Use this feature sparingly and document the expected parameter type and semantics for each generator: mismatched expectations between producer and consumer can cause logic errors. Handle `null` or absent parameters explicitly and ensure the producer validates incoming values before using them.

## Efficient parallel iteration across multiple generators
`AsyncGenerator` is inherently designed to easy chain several generators. This makes it fairly simple to create a generic `concat` operator, as shown in the following example:
```java
static  @async <T> AsyncGenerator<T> concat(Iterable<? extends AsyncGenerator<? extends T>> generators) {
   var async = AsyncGenerator.<T>start();
   for (var g : generators) {
	     async.yield(g);
   }
   return async.yield(); 
}
```
However, implementing ZIP-like operators (i.e. combining results of several generators) efficiently is pretty tricky. For instance, imagine this scenario: we have a number of independent generators, each capable of providing a weather forecast when queried, and our goal is to create a generator that delivers the first forecast available. The naïve implementation is as follows:
```java
record WeatherForecast() {...}

@async AsyncGenerator<WeatherForecast> nextForecastA() {...}
@async AsyncGenerator<WeatherForecast> nextForecastB() {...}
@async AsyncGenerator<WeatherForecast> nextForecastC() {...}

@async AsyncGenerator<WeatherForecast> nextForecast() {
    var async = AsyncGenerator.<WeatherForecast>start();
    try (var ga = nextForecastA();
         var gb = nextForecastB();
         var gc = nextForecastC()) {
            
        while (true) {
            var pending = Stream.of(ga.next(), gb.next(), gc.next())
                                .filter(Objects::nonNull) 
                                .map(CompletionStage::toCompletableFuture)
                                .toList();
            if (pending.isEmpty()) {
                // This means neither of the generators has more results
                break;
            }
            // Ugly with std. Java
            CompletableFuture<WeatherForecast>[] array =
                (CompletableFuture<WeatherForecast>[])
                new  CompletableFuture[pending.size()];
            pending.toArray(array);
            
            var  fastest =
                (CompletableFuture<WeatherForecast>)
                (Object)CompletableFuture.anyOf(array);
                    
            // Pretty with Tascalate Concurrent (https://github.com/vsilaev/tascalate-concurrent)
            /*
            var fastest = net.tascalate.concurrent.Promises.any(false, pending);
            */
            async.yield(fastest);
        }
    }
    return  async.yield(); 
}
```
Also, the code looks pretty good: we are  selecting the first ready result with the `CompletableFuture.anyOf`, so the code should be parallel. However, there is a hidden caveat: calls to `AsyncGenerator.next()` are executed sequentially, meaning we have to wait for *all* generators  to yield their next pending value. This leads exactly to what we previously referred as *async/await hell* in the asynchronous task methods section. To address this challenge, the user can convert each `AsyncGenerator<T>` into a `ConcurrentGenerator<T>` and modify the code accordingly, as shown below:
```java
@async AsyncGenerator<WeatherForecast> nextForecast() {
    var async = AsyncGenerator.<WeatherForecast>start();
    try (var ga = nextForecastA().concurrent();
         var gb = nextForecastB().concurrent();
         var gc = nextForecastC().concurrent()) {
            
         while (true) {
             var  pendingItem = ConcurrentGenerator.any( true, ga.take(), gb.take(), gc.take() );
             WeatherForecast item;
             try {
                 item = await( pendingItem );
             } catch (NoSuchElementException ex) {
                 // All generators are over
                 break;
             }
             async.yield(item);

             // Alternative option is to yield pendingItem
             // However, the consumer of generator must be able
             // to handle NoSuchElementException on it's own
             /*
             try {
                 async.yield(pendingItem);
             } catch (NoSuchElementException ex) {
                 break;
             }
             */
         }
    }
    return  async.yield(); 
}
```
Let's review the code step by step. Initially, we transform an `AsyncGenerator<T>` into a `ConcurrentGenerator<T>` by invoking `AsyncGenerator.concurrent()`. Since the resulting object implements `AutoCloseable` and takes care of closing the underlying asynchronous generator, we utilize it within a `try-with-resources` block. The sole remaining API method in `ConcurrentGenerator<T>` is `take()`, which retrieves the next available item from the underlying asynchronous generator (if available). This method produces a result of type `ConcurrentGenerator.Result<T>` with the following interface:
```java
public  abstract  static  class Result<T> {
    public  boolean hasNext();
    public  boolean isValue();
    public T value();
    public T orElse(T substitution);
    public  final Stream<T> stream();
}
```
The API highlights that the value returned by `take` can either be a genuine value holder or a marker object signifying a completed generator. To differentiate between these scenarios, we can use `isValue` for inspection. When `ConcurrentGenerator.Result<T>` reflects a value holder, the contained `value()` can be accessed via the appropriate method. However, invoking this method on the termination marker object results in an exception. Furthermore, the result can be transformed into a `java.util.Stream` comprising 0 or 1 elements or utilized with a sentinel fallback using the `orElse` method. 

Afterwards we combine 3 returned promises into a single `CompletionStage<WeatherForecast>` via the call to `ConcurrentGenerator.any(cancelRemaining, <stages>)`, await the result in the asynchronous generator and yield it to the consumer. Notice, that while awaiting the result we can get `java.util.NoSuchElementException` when all of the generators are iterated over.

A key aspect to emphasize is that the code provided above cancels the pending item we are awaiting at every step (first parameter to `ConcurrentGenerator.any(...)` is `true`. In other words, the first available item from any of generators is selected while the remaining ones get canceled. This requires each individual generator, such as `nextForecastB`, to handle cancellation exceptions for each yielded item, as demonstrated below:
```java
@async AsyncGenerator<WeatherForecast> nextForecastB() {
    var  async = AsyncGenerator.<WeatherForecast>start();
    try {
        for (int  i = 0; i < 9; i++) {
            try {
                async.yield( someExternalAsyncServiceProducingWeatherForecast()( );
            } catch (CancellationException ex) {
                System.out.println("Skip item in Generator B");
            }
        }
    } finally {
        System.out.println("<< GEN B IS OVER ");
    }
    return  async.yield();
}
```
Of course, zipping can be done not just by taking the first item available from each generator, but also by gathering a collection of items from all the generators' steps as a single frame:
```java
@async AsyncGenerator<List<WeatherForecast>> nextForecastAll() {
    var async = AsyncGenerator.<List<WeatherForecast>>start();
    try (var ga = nextForecastA().concurrent();
         var gb = nextForecastB().concurrent();
         var gc = nextForecastC().concurrent()) {
        while (true) {
            var  pendingItem = ConcurrentGenerator.all(ga.take(), gb.take(), gc.take());
            try {
                var  item = await( pendingItem );
                async.yield(item);
            } catch (NoSuchElementException ex) {
                System.out.println("Over!!!");
                // All generators are over
                break;
            }
        }
    } finally {
        System.out.println("<< GEN :MERGED: IS OVER ");
    }
    return  async.yield();
}
```
As an extension to `all`, a more intriguing alternative is utilizing `ConcurrentGenerator.combine`, which facilitates the transformation of a generic `List` into a specialized application-dependent object. Refer to the library's source code for further insights.

# Scheduler & SchedulerResolver - where is my code executed?
## Introducing schedulers
When executing asynchronous code with `CompletionStage` / `CompletableFuture` it's critical to know where the code is resumed once the corresponding completion stage is settled. With regular `CompletionStage` API the answer is pretty straightforward: the code will be resumed with the `Executor` supplied as an additional parameter to the API method like below:
```java
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
...

CompletionStage<String> myCompletionStage = ... ; // Start asynchronous operation
ExecutorService myExecutor = Executors.newFixedThreadPool(4);
myCompletionStage.thenAcceptAsync(System.out::println, myExecutor);
``` 
In the example above the code to print result to the console will run on the thread provided by `myExecutor`.

However, for Tascalate Async / Await there is no way to specify where the code will be resumed once the corresponding `await(future)` operation is complete. Instead of the passing `Executor` explicitly, the library uses fully declarative pluggable mechanism to  specify asynchronous executor to run with.

First we must introduce the `Scheduler` interface:
```java
package net.tascalate.async;

public interface Scheduler {

   ...    
    abstract public CompletionStage<?> schedule(Runnable runnable);
        
    default Runnable contextualize(Runnable resumeContinuation) {
        return resumeContinuation;
    }
   ...
}
```
The `Schedulre` API has 2 responsibilities:
1. Execute supplied runnable command, most probably asynchronously (thought, this is implementation-dependent)
2. Capture execution context of the currently running thread before suspension so it can later be restored when the code is resumed after `await(future)`. The `execution context` is typically defined as a set of thread local variables - either via explicit usage of the `ThreadLocal` or via some API wrapping `ThreadLocal`-s (like [RequestContextHolder](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/context/request/RequestContextHolder.html) in Spring) 

There are several factory methods in `Scheduler` interface that create concrete `Scheduler` implementation using the `ExecutorService` supplied and optional `contextualizer` - a function that captures current thread execution context and creates a wrapper for the runnable to re-apply context on the new thread.
```
package net.tascalate.async;
...
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
...

public interface Scheduler {
    ...

    public static Scheduler nonInterruptible(Executor executor);
    
    public static Scheduler nonInterruptible(
        Executor executor, 
        Function<? super Runnable, ? extends Runnable> contextualizer);     

    public static Scheduler interruptible(ExecutorService executor);
    
    public static Scheduler interruptible(
        ExecutorService executor, 
        Function<? super Runnable, ? extends Runnable> contextualizer);

   ...
}
```

So the sequence to create `Scheduler` is:
```java
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.function.Function;

import net.tascalate.async.Scheduler 
...
// whatever ExecutorService impl. you need
ExecutorService myExecutor = Executors.newFixedThreadPool(4); 
Scheduler myScheduler = Scheduler.interruptible(myExecutor, 
                                                Function.identity() /* or actual contextualizer */);
```

The careful reader may notice that there is a divide between `interruptible` vs `non-interruptible` schedulers, but let us left this out of the scope for a while. Instead, let's discuss how to apply the scheduler created to the asynchronous methods.

 ## Explicit (method-scoped) schedulers
 The most explicit and straightforward way to specify asynchronous `Scheduler` is to pass it explicitly to the  asynchronous method as an annotated parameter:
```java
import static net.tascalate.async.CallСontext.async;
import static net.tascalate.async.CallСontext.await;
import net.tascalate.async.async;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider; 

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyClass {
   public static void main(String[] argv) {
       ExecutorService myExecutor = Executors.newFixedThreadPool(4);
       Scheduler myScheduler = Scheduler.interruptible(myExecutor);
       CompletionStage<String> myPromise = new MyClass().mergeStrings(myScheduler);
   }

    public @async CompletionStage<String> mergeStrings(@SchedulerProvider currentScheduler) {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited", currentScheduler) );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix, 
        @SchedulerProvider currentScheduler) {

        String value = prefix + await( produceString("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        ...
    }
}
```
The scenario is simple: add parameter of the type `Scheduler` annotated with `@SchedulerProvider` annotation to the each asynchronous method where this scheduler is used and pass it explicitly. Note that it's an error to have more than one parameter annotated with `@SchedulerProvider` - only one is allowed. Also, passing non-annotated `Scheduler` will have no effect -- it's treated just like regular parameter and do not used for asynchronous execution.

You may notice that `currentScheduler` parameter from the `mergeStrings` method is passed directly to the `decorateStrings` method. This is mandatory if you want to share the same scheduler across several asynchronous methods. By default schedulers are not inherited for nested calls.

Notice that in both methods above `currentScheduler` is not used with `await(...)` operator - it's used implicitly behinds the scenes in the generated code. This has one important implication: you can use only one scheduler per asynchronous method, there is no way to use different schedulers for different `await(...)` operations within the same method. If you ever need to then please re-factor your code to use separate asynchronous methods where individual schedulers may be defined per-method.

## SchedulerResolver introduction -- propagating scheduler to nested calls
As it was mentioned right above "by default schedulers are not inherited for nested calls". This is a good point to introduce pluggable scheduler providers mechanism and alleviate this limitation.

To use pluggable schedule provider you need to add corresponding Maven dependency and introduce new artifact on the project class-path / module-path. For example:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.resolver.propagated</artifactId>
    <version>${actual-tascalate-async-await-version}</version>
    <scope>runtime</scope>
</dependency>
```
Now let us rewrite the example above to automatically propagate scheduler of the outer asynchronous method to the inner one:
```java
import static net.tascalate.async.CallСontext.async;
import static net.tascalate.async.CallСontext.await;
import static net.tascalate.async.CallСontext.scheduler;

import net.tascalate.async.async;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider; 

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyClass {
   public static void main(String[] argv) {
       ExecutorService myExecutor = Executors.newFixedThreadPool(4);
       Scheduler myScheduler = Scheduler.interruptible(myExecutor);
       CompletionStage<String> myPromise = new MyClass().mergeStrings(myScheduler);
   }

    public @async CompletionStage<String> mergeStrings(@SchedulerProvider Scheduler currentScheduler) {
        System.out.println("Current scheduler (outer) - " + scheduler());
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited") );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix) {
        System.out.println("Current scheduler (inner) - " + scheduler());
        String value = prefix + await( produceString("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        ...
    }
}
```
You may see that the code was simplified, however no new specific code for the "propagating provider" was introduced. If you run this code you will see that `CallContext.scheduler()` reports the very same scheduler for both inner and outer methods. Btw, `CallContext.scheduler()` may be used with any combination of the scheduler providers and reports currently used `Scheduler` for all asynchronous, suspendable and generators methods.

## More useful SchedulerResolver - per-class/per-instance schedulers
The next and probably the most useful one `Scheduler` provider is a "provided" provider variant (no pun). The idea is that a `Scheduler` may be specified per class or per class instances as a filed  (an instance one or a static one) or as a getter-like method (no argument method with a `Scheduler` return type).

To use this provider you first need to add a new runtime dependency:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.resolver.provided</artifactId>
    <version>${actual-tascalate-async-await-version}</version>
    <scope>runtime</scope>
</dependency> 
```

Let us modify an example from the above to use the new provider:
```java
import static net.tascalate.async.CallСontext.async;
import static net.tascalate.async.CallСontext.await;
import static net.tascalate.async.CallСontext.scheduler;

import net.tascalate.async.async;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider; 

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyClass {
   public static void main(String[] argv) {
       ExecutorService myExecutor = Executors.newFixedThreadPool(4);
       Scheduler myScheduler = Scheduler.interruptible(myExecutor);
       CompletionStage<String> myPromise = new MyClass(myScheduler).mergeStrings();
   }

   @SchedulerProvider /* Mandatory annotation */
   private final Scheduler myScheduler;

   MyClass(Scheduler scheduler) {
     this.myScheduler = scheduler;
   }

    public @async CompletionStage<String> mergeStrings( ) {
        System.out.println("Current scheduler (outer) - " + scheduler());
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited") );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix) {
        System.out.println("Current scheduler (inner) - " + scheduler());
        String value = prefix + await( produceString("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        ...
    }
}
```

The `Scheduler` is provided as an instance field for all of `@async` instance methods of the class `MyClass`. You can initialize this variable in the constructor (as above) or at any time before invoking the `@async` method. In Spring / CDI environment the `scheduler` field might be injected by the container via corresponding annotation (`@Autowired` or `@Injected`). 

Please notice that when you are re-assigning the field during execution of the `@async` method it has no effect on the methods these are in progress -- only freshly invoked ones will see the change. However, special consideration should be taken on account: in the example above if you re-define the `scheduler` field after `mergeStrings` invocation but before `decorateStrings` invocation then methods will use different schedulers. Also, no special synchronization is performed by the library itself, and it's library's user responsibility to synchronize access to such fields.

As it was mentioned, you can use a getter-like method annotated with `@SchedulerProvider ` to supply scheduler. Use this option when you need different schedulers for the different object states, but, again, provide all necessary state synchronization on your own.

It's an error to provide a `Scheduler` with both a field and a method, or to have more than one filed or more than one getter-like method annotated with `@SchedulerProvider `.

It was mentioned that you can use both instance and static (class) field / method to provide a `Scheduler`. However, consider the following rules:
1. Instance-level provider supplies a `Scheduler` only to `@async` instance methods.
2. Class-level provider supplies a `Scheduler` to static `@async` methods AND to instance methods UNLESS there is a separate instance-level provider.
3. It's an error to have more than one class-level provider in the same class via static field(s) / static getter-like method(s) / the combination of thereof (same as with instance-level providers); however it's a fully supported scenario when you have both instance-level provider AND class-level provider.
4. Instance level provider will take precedence over the class-level provider for the `@async` instance methods.

Last but not least is a visibility of the `Scheduler` provider (field / getter-like method) inherited from the superclass. It follows the same visibility rules as for the regular fields / methods inheritance: public and protected are always visible; package private are visible when both classes are in the same package; and private members are not visible. Take this on account when runtime will report you about ambiguity of the `Scheduler` provider - most probably, your subclass inherits ones from the superclasses chain.

## Scoped SchedulerResolver -- overriding schedulers, providing own schedulers in DI environment 
TBD

# Interruptions/cancelation of @async methods & exception handling
TBD
