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
As you can see, suspendable methods are similar to regular ones but require a special annotation - `@suspendable`. You should adhere to the usual rules for returning results from these methods. Additionally, calling `return async(<value>)` inside these methods is considered an error. 

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
                async.yield( someExternalAsyncServiceProducingWeatherForecast() );
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
When working with asynchronous code using `CompletionStage` or `CompletableFuture`, it's essential to consider where the execution continues once the corresponding stage is completed. In the standard `CompletionStage` API, the answer is clear: the code will proceed on the `Executor` specified as an additional parameter in the API method, as demonstrated below:
```java
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
...

CompletionStage<String> myCompletionStage = ... ; // Start asynchronous operation
ExecutorService myExecutor = Executors.newFixedThreadPool(4);
myCompletionStage.thenAcceptAsync(System.out::println, myExecutor);
``` 
In the example above, the code responsible for printing the result to the console will execute on the thread supplied by `myExecutor`.

In contrast to traditional practices, Tascalate Async/Await doesn’t allow you to directly define where the code execution should continue when the associated `await(future)` operation concludes. Rather than explicitly providing an `Executor`, the library employs a fully declarative and pluggable system to designate the asynchronous executor to be utilized. 

To begin, let's take a look at the `Scheduler` interface:
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
The `Scheduler` API serves two responsibilities: 
1. To execute a provided runnable task, which is generally done asynchronously -- but the actual behavior may vary depending on the implementation. 
2. To preserve the execution context of the active thread before it is suspended, allowing the context to be reinstated later when the code resumes execution following an `await(future)` call. The `execution context` usually encompasses a collection of thread-local variables—either managed directly via `ThreadLocal` or indirectly through APIs that utilize `ThreadLocal`, such as [RequestContextHolder](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/context/request/RequestContextHolder.html) in Spring. The `Scheduler` interface includes several factory methods that let you create specific `Scheduler` implementations by using a provided `ExecutorService` and, optionally, a `contextualizer`. This contextualizer is a function designed to capture the thread's current execution context and generate a runnable wrapper that re-applies this context within the new thread.
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
The attentive reader might observe a distinction between `interruptible` and `non-interruptible` schedulers; however, let us set that aside for now. Instead, let’s focus on exploring how the scheduler we’ve created can be applied to asynchronous methods.

## Explicit (method-scoped) schedulers
The most explicit and straightforward way to specify a `Scheduler` is to pass it explicitly to the asynchronous method as an annotated parameter:
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
The concept is straightforward: include a `Scheduler` parameter in each asynchronous method where it's utilized, ensuring the parameter is annotated with `@SchedulerProvider`, and pass it explicitly. Keep in mind that it's incorrect to have more than one parameter with the `@SchedulerProvider` annotation - only one is permissible. Additionally, providing an unannotated `Scheduler` won't have any special effect -- it will be treated as a standard parameter and not used for asynchronous execution. 

It's worth noting that the `currentScheduler` parameter in the `mergeStrings` method is directly passed to the `decorateStrings` method. This step is essential when you want to use the same scheduler across multiple asynchronous methods. By default, schedulers are not automatically propagated to nested calls. 

Also, observe that in both of the above methods, the `currentScheduler` parameter isn’t directly used with the `await(...)` operator. Instead, it's implicitly handled behind the scenes within the generated code. This leads to one crucial limitation: only one scheduler can be tied to any given asynchronous method, meaning it's not possible to assign distinct schedulers to different `await(...)` operations within the same method. If such a distinction is necessary, it's advisable to refactor your code to separate asynchronous methods, so each method can define its own specific scheduler.

## Introducing SchedulerResolver – Propagating Schedulers to Nested Calls 
As highlighted earlier, "schedulers are not automatically propagated to nested calls" This creates an excellent opportunity to discuss the pluggable scheduler providers mechanism, which helps to address this limitation effectively. 

To enable a pluggable scheduler provider, you must include the relevant Maven dependency and ensure the new artifact is added to your project's class-path or module-path. For instance:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.resolver.propagated</artifactId>
    <version>${actual-tascalate-async-await-version}</version>
    <scope>runtime</scope>
</dependency>
```
Let's rewrite the previous example so that the scheduler of the outer asynchronous method is automatically passed to the inner one:
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
The code was simplified but no dedicated "propagating provider" was added. As a result, when you run it, `CallContext.scheduler()` returns the same `Scheduler` instance for both the outer and inner methods.

As an extra takeaway, `CallContext.scheduler()` may be used with any combination of the scheduler providers and reports currently used `Scheduler` for all asynchronous, suspendable and generators methods.

## More useful SchedulerResolver — per-class and per-instance schedulers
The next provider variant lets you associate a `Scheduler` with a specific class. The resolver locates a `Scheduler` declared on the target type either as a field (static or instance) or as a no-argument method that returns `Scheduler`. When present, that `Scheduler` is used for asynchronous task and generator methods invoked on that class or class instance.

To use this provider you need to add a new *runtime* dependency:
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
The  `Scheduler`  is provided as an instance field for all  `@async`  instance methods in the  `MyClass`  class. You can initialize this variable in the constructor or at any time before invoking an  `@async`  method. In  Spring  or CDI  environments, the  `scheduler`  field may be injected by the container via the corresponding annotation (`@Autowired`  or  `@Inject`).

Please note that re-assigning the field during the execution of an  `@async`  method has no effect on methods currently in progress; only  newly invoked  methods will reflect the change. However, special consideration is required: in the example above, if you redefine the  `scheduler`  field after calling  `mergeStrings`  but before calling  `decorateStrings`, the methods will use different schedulers. Additionally, the library performs no internal  synchronization, so it is the user's responsibility to synchronize access to such fields.  Therefore the most robust and safe approach is to treat provider field as read-only.

As mentioned, you can use a getter-like method annotated with  `@SchedulerProvider`  to supply the scheduler. Use this option when you need different schedulers based on different  object states, but ensure you provide all necessary state synchronization.

It is an error to provide a  `Scheduler`  via both a field and a method, or to have more than one field or getter-like method annotated with  `@SchedulerProvider`.

As previously mentioned, both  instance  and  static (class)  fields or methods can provide a  `Scheduler`. However, the following rules apply:
1.  Instance-level providers  supply a  `Scheduler`  only to instance-level  `@async`  methods.
2.  Class-level providers  supply a  `Scheduler`  to static  `@async`  methods and, by default, to instance `@async` methods  **unless**  a separate instance-level provider is defined.
3.  Defining more than one class-level provider (via static fields, static getter-like methods, or a combination thereof) results in an  error. However, defining both an instance-level and a class-level provider within the same class is  fully supported.
4.  For instance-level  `@async`  methods, the  instance-level provider  takes precedence over the class-level provider.

Lastly, the visibility of a  `Scheduler` provider (field or getter-like method) inherited from a superclass follows standard inheritance rules: `public` and `protected` members are always visible; `package-private` members are visible only if both classes are in the same package; and `private` members are not visible. Keep this in mind if the runtime reports an ambiguity regarding the `Scheduler` provider, as your subclass likely inherits providers from its superclass hierarchy.

## Scoped SchedulerResolver -- overriding schedulers, providing own schedulers in DI environment 
Up to this point, we have introduced two externally added `SchedulerResolver`-s. A curious reader might naturally wonder: a) which one has higher precedence? and b) how do the previous examples function without explicitly specifying a `SchedulerResolver`? 

The explanation lies in the fact that `SchedulerResolver`-s form a prioritized chain, which includes certain built-in defaults. Below is a snippet from the `SchedulerResolver` API source code: 
```java 
public interface SchedulerResolver { 
    int priority(); 
    Scheduler resolve(Object owner, MethodHandles.Lookup ownerClassLookup, MethodDefinition methodDef); 
}
``` 
As shown, each `SchedulerResolver` is assigned a priority (lowest numbers indicate later execution in the chain). The chain is structured as follows, incorporating fallback providers: 
1. `Scheduler.sameThreadContextless()` --  this `SchedulerResolver` has the lowest priority and acts as the fallback when no other `SchedulerResolver` is configured. This explains how all prior examples work without a custom `SchedulerResolver` being explicitly added. 
2. The `Scheduler` installed via `Scheduler.installDefaultScheduler(scheduler)` -- this option is applicable for a single-time installation per application. When added, it supersedes the `SchedulerResolver` from step [1], but it is still open to being replaced by any custom `SchedulerResolver`. 
3. The `Scheduler` defined in `SchedulerScope.DEFAULTS` (supplied by the artifact `net.tascalate.async.resolver.scoped`), with a priority level of 10. 
4. The `SchedulerResolver` provided from the `net.tascalate.async.resolver.propagated` module, carrying a priority value of 100. 
5. The `Scheduler` set up via `SchedulerScope.DEFAULTS_OVERRIDE`, having a priority of 200, thereby allowing it to override a propagated `SchedulerResolver`. 
6. The `SchedulerResolver` explored earlier within `net.tascalate.async.resolver.provided`, corresponding to per-class or per-instance implementations, with a priority score of 500. 
7. The `Scheduler` specified with `SchedulerScope.PROVIDER_OVERRIDE` (available from the artifact `net.tascalate.async.resolver.scoped`) takes the highest precedence, with a priority level of 1000.
8. Finally, the explicit `Scheduler` method parameter, marked with `@SchedulerProvider` annotation, is applied when it is not null.

Armed with this chain, the library invokes the `resolve(...)` method on each resolver in turn (from highest priority to the lowest) to obtain a `Scheduler`, stopping as soon as the first non-null outcome is encountered. Each `SchedulerResolver` makes use of the supplied parameters to determine the appropriate scheduler. These parameters include: 
1. `owner` — the instance responsible for the asynchronous task or generator method. It will always be `null` for static methods. 
2. `ownerClassLookup` — the PRIVATE class lookup for the class that declares the asynchronous task or generator method.
3. `methodDef` — the metadata for the asynchronous task or generator method, containing details such as its name, parameter types, and return type.

As you can observe, the chain of resolvers offers significant flexibility, but also introduces complexity. To maintain manageability in your application, it's best to limit yourself to 2-4 `SchedulerResolver`-s at most. 

All `SchedulerResolver`s located in `net.tascalate.async.resolver.scoped` follow a consistent pattern. In particular, these providers are designed for use within some variation of *around-advice,* as demonstrated in the example below: 
```java
SchedulerScope.DEFAULTS.runWith(/* Scheduler */ theScheduler, /* Runnable */ codeToExecute);
``` 
This technique can be implemented via bytecode modification or with an AOP library such as AspectJ. Notably, the Tascalate Async/Await integration for Spring employs this exact strategy using web request filters. 

**IMPLEMENATION DETAILS**: `SchedulerResolver`-s are constructed using the Java Service API, adhering to the guidelines specified by the [ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html). If you’re comfortable with your understanding of Tascalate Async/Await up to this point, you can effortlessly create a custom `SchedulerResolver` tailored to meet your application’s needs.

# Interruptions/cancelation of @async methods & exception handling
TBD
