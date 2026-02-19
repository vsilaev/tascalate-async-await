[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate.async/net.tascalate.async.parent.svg)](https://search.maven.org/artifact/net.tascalate.async/net.tascalate.async.parent/1.2.9/pom) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/releases/tag/1.2.9) [![license](https://img.shields.io/github/license/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/blob/master/LICENSE)

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
## ...with Maven
First, add Maven dependency to the library runtime:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.runtime</artifactId>
    <version>1.2.9</version>
</dependency>
```
Second, add the following build plugins in the specified order:
```xml
<build>
  <plugins>
	  
    <plugin>
      <groupId>net.tascalate.async</groupId>
      <artifactId>net.tascalate.async.tools.maven</artifactId>
      <version>1.2.9</version>
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
      <version>2.7.8</version>
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
## ...with Gradle
As with Maven, you have to specify both build plugins and runtime dependencies. The minimal Gradle scipt should have the following prologue:
```groovy
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath 'net.tascalate.async:net.tascalate.async.tools.gradle:1.2.9'
        classpath 'net.tascalate.javaflow:net.tascalate.javaflow.tools.gradle:2.7.8'
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
    implementation 'net.tascalate.async:net.tascalate.async.runtime:1.2.9'
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
        classpath 'net.tascalate.async:net.tascalate.async.tools.gradle:1.2.9'
        classpath 'net.tascalate.javaflow:net.tascalate.javaflow.tools.gradle:2.7.8'
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
    implementation 'net.tascalate.async:net.tascalate.async.runtime:1.2.9'
    
    /* Async/Await Extras */
    implementation 'net.tascalate.async:net.tascalate.async.extras:1.2.9'
    
    /* Promise<T> implementation */
    /* Necessary because net.tascalate.async.extras uses it as an */
    /* 'optional' dependency to avoid concrete version lock-in.   */
    implementation 'net.tascalate:net.tascalate.concurrent:0.9.9'
    
    /* Necessary only for different providers */
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.provided:1.2.9'
    /*
    runtimeOnly 'net.tascalate.async:net.tascalate.async.resolver.propagated:1.2.9'
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

# Asynchronous tasks
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

# Suspendable methods
Sometimes it is necessary to await for asynchronous result in some helper method that per se should not be asynchronous. To support this use case Tascalate Async/Await provides `@suspendable` annotation. The original example above hence can be rewritten as following:
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
As you see, suspendable methods are just like regular ones but with special annotation - `@suspendable`. You should follow regular rules about returning results from this methods, moreover - it's an error to call `return async(<value>)` inside these methods. The important thing about `@suspendable` methods is that they may be called only from `@async` methods or from other `@suspendable` methods.

Performance-wise suspendable methods behaves the same as asynchronous task methods, so the question "which kind should be used" is justy a matter of orginizing and structuring your code . The recommended approach is to use asynchronous task methods when they are exposed to outside clients and suspendable ones for internal implementation details. However, the final decision is up to library user till s/he holds the rule that suspendable methods may be called only from asynchronous context (`@async` methods or other `@suspendable` methods) as stated above.

Implemenation notes: technically suspendable methods are implemented as continuable methods that follow rules defined by [Tascalate JavaFlow](https://github.com/vsilaev/tascalate-javaflow) library, so you may use any continuable annotation that is supported by Tascalate JavaFlow, not only `@suspendable`.

# Generators
TDB

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

However, for Tascalate Async / Await there is no way to specify explicitly where the code will be resumed once the corresponding `await(future)` operation is complete. Instead of the passing `Executor` explicitly, the library uses more declarative pluggable mechanism to  specify asynchronous executor to run with.

First we must introduce the `Scheduler` interface:
```java
package net.tascalate.async;

public interface Scheduler {

   ...    
    
    default Runnable contextualize(Runnable resumeContinuation) {
        return resumeContinuation;
    }
    
    abstract public CompletionStage<?> schedule(Runnable runnable);
   ...
}
```
The `Schedulre` API has 2 responsibilities:
1. Execute supplied runnable command, most probably asynchronously (thought, this is implementation-dependent)
2. Capture execution context of the currently running thread before suspension so it can later be restored when the code is resumed after `await(future)`. The `execution context` is typically defined as a set of thread local variables - either via explicit usage of the `ThreadLocal` or via some API wrapping `ThreadLocal`-s (like [SimpAttributesContextHolder](https://docs.spring.io/spring-framework/docs/5.1.4.RELEASE_to_5.1.5.RELEASE/Spring%20Framework%205.1.5.RELEASE/org/springframework/messaging/simp/SimpAttributesContextHolder.html) in Spring) 

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
ExecutorService myExecutor = Executors.newFixedThreadPool(4); // Or whatever ExecutorService impl. you need
Scheduler myScheduler = Scheduler.interruptible(myExecutor, Function.identity());
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

You may notice that `currentScheduler` parameter from the `mergeStrings` method is passed directly to the `decorateStrings` method. This is mandatory if you want to share the same scheduler accross several asynchronous methods. By default schedulers are not inherited for nested calls.

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
import net.tascalate.async.async;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider; 
import net.tascalate.async.spi.CurrentCallContext;

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
        System.out.println("Current scheduler (outer) - " + CurrentCallContext.scheduler());
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited") );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix) {
        System.out.println("Current scheduler (inner) - " + CurrentCallContext.scheduler());
        String value = prefix + await( produceString("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceString(String value) {
        ...
    }
}
```
You may see that the code was simplified, however no new specific code for the "propagating provider" was introduced. If you run this code you will see that `CurrentCallContext.scheduler()` reports the very same scheduler for both inner and outer methods. Btw, `CurrentCallContext.scheduler()` may be used with any combination of the scheduler providers and reports currently used `Scheduler` for all asynchronous, suspendable and generators methods.

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
import net.tascalate.async.async;
import net.tascalate.async.Scheduler;
import net.tascalate.async.SchedulerProvider; 
import net.tascalate.async.spi.CurrentCallContext;

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
   private final Scheduler scheduler;

   MyClass(Scheduler scheduler) {
     this.scheduler = scheduler;
   }

    public @async CompletionStage<String> mergeStrings( ) {
        System.out.println("Current scheduler (outer) - " + CurrentCallContext.scheduler());
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            String v = await( decorateStrings(i, "async ", " awaited") );
            result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStrings(int i, String prefix, String suffix) {
        System.out.println("Current scheduler (inner) - " + CurrentCallContext.scheduler());
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
3. It's an error to have more than one class-level provider in the same class via static field(s) / static getter-like method(s) / the combination of thereof (same as with instance-level providers); however it's a fully supported scenario when you have both instance-level provider AND class-level provider: instance level provider will take precedence over the class-level provider for the `@async` instance methods.

Last but not least is a visibility of the `Scheduler` provider (field / getter-like method) inherited from the superclass. It follows the same visibility rules as for the regular fields / methods inheritance: public and protected are always visible; package private are visible when both classes are in the same package; and private members are not visible. Take this on account when runtime will report you about ambiguity of the `Scheduler` provider - most probably, your subclass inherits ones from the superclasses chain.

## Scoped SchedulerResolver -- overriding schedulers, providing own schedulers in DI environment
TBD

# Interruptions/cancelation of @async methods & exception handling
TBD
