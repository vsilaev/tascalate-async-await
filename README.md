[![Maven Central](https://img.shields.io/maven-central/v/net.tascalate.async/net.tascalate.async.parent.svg)](https://search.maven.org/artifact/net.tascalate.async/net.tascalate.async.parent/1.0.0/pom) [![GitHub release](https://img.shields.io/github/release/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/releases/tag/1.0.0) [![license](https://img.shields.io/github/license/vsilaev/tascalate-async-await.svg)](https://github.com/vsilaev/tascalate-async-await/blob/master/LICENSE)
# Why async-await?
Asynchronous programming has long been a useful way to perform operations that don’t necessarily need to hold up the flow or responsiveness of an application. Generally, these are either compute-bound operations or I/O bound operations. Compute-bound operations are those where computations can be done on a separate thread, leaving the main thread to continue its own processing, while I/O bound operations involve work that takes place externally and may not need to block a thread while such work takes place. Common examples of I/O bound operations are file and network operations. 

Traditional asynchronous programming involves using callbacks these are executed on operation completion. The API differs across languages and libraries, but the idea is always the same: you are firing some asynchronous operation, get back some kind of `Promise` as a result and attach success/failure calbacks that are executed once asynchronous operation is completed. However, these approach is associated numerous hardships:
1. You should explicitly pass contextual variables to callbacks. Sure, you can use lambdas to capture lexical context, but this does not eliminate the problem completly. And sometimes even sacrifies readability of the code - when you have a lot of lambda functions with complex body.
2. Coordination of asynchronous operations with callbacks is dificult: any branching logic inside the chain of asynchronous callbacks is a pain; resource management provided by `try-with-resources` constructs are not possible with asynchronous callbacks as well as many other control flow statements; handling failures is radically different from the familiar `try/catch` used in synchronous code.
3. Different callbacks are executed on different threads. Hence special care should be taken where the application flow resumes. The issue is very critical when application runs in managed environment like JEE or UI framework (JavaFX, Swing, etc).

To alleviate aforementioned readability and maintainability issues some languages provides `async/await` asynchronous programming model. This lets developer make asynchronous calls just as easily as she can invoke synchronous ones, with the tiny addition of a keyword `await` and without sacrifying any of asynchronous programming benefits. With `await` keyword asynchronous calls may be used inside regular control flow statements (including exception handling) as naturally as calls to synchronous methods. The list of the languages that support this model is steadly growing: C# 5, ECMAScript 7, Kotlin, Scala. 

Tascalate Async/Await library enables `async/await` model for projects built with the Java 8 and beyond. The implementation is based on [continuations for Java](https://github.com/vsilaev/tascalate-javaflow) and provides runtime API + bytecode enchancement tools to let developers use syntax constructs similar to C# 5 or ECMAScript 2017/2018 with pure Java.

# How to use?
First, add Maven dependency to the library runtime:
```xml
<dependency>
    <groupId>net.tascalate.async</groupId>
    <artifactId>net.tascalate.async.runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```
Second, add the following build plugins in the specified order:
```xml
<build>
  <plugins>
    <plugin>
      <groupId>net.tascalate.async</groupId>
      <artifactId>net.tascalate.async.tools.maven</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
	  <phase>process-classes</phase>
	  <goals>
	    <goal>tascalate-async-enhance</goal>
	  </goals>
	</execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>net.tascalate.javaflow</groupId>
      <artifactId>net.tascalate.javaflow.tools.maven</artifactId>
      <version>2.3.1</version>
      <executions>
        <execution>
	  <phase>process-classes</phase>
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
# Asynchronous tasks
The first type of functions the library supports is asycnhronous task. Asynchronous task is a method (either instance or class method) that is annotated with `net.tascalate.async.async` annotation and returns `CompletionStage<T>` or `void`. In the later case it is a "fire-and-forget" task that is intended primarly to be used for event handlers inside UI framework (like JavaFX or Swing). Let us write a simple example:
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
Thanks to statically imported methods of `net.tascalate.async.CallСontext` the code looks very close to the one developed with languages having native support for async/await. Both `mergeStrings` and `decorateStrings` are asynchronous methods -- they are marked with `net.tascalate.async.async` annotation and returns `CompletionStage<T>`. Inside these methods you may call `await` to suspend the method till the `CompletionStage<T>` supplied as the argument is resolved (either sucessfully or exceptionally). Please notice, that you can await for any `CompletionStage<T>` implementation obtained from different libraries - like inside the `decorateStrings` method, including pending result of another asynchronous method - like in `mergeStrings`. 

To return a result from the asynchronous method you have to use syntatic construct `return async(value)`. You must always treat both of these statements (calling `async` method and `return`-ing its result) as the single syntatic construct and don't call `async` method separately or store it return value to variable while these will lead to unpredicatble results. It's especially important if your method body is not linear. Depending on your established coding practice how to deal with multiple returns you should use either...
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
It's worth to mention, that when developing code with async/await you should avoid so-called ["async/await hell"](https://medium.com/@7genblogger/escaping-so-called-async-await-hell-in-node-js-b5f5ba5fa9ca). In short, pay special attention what parts of your code may be executed in parallel and what parts require serial execution. Consider the following example:
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
In the above example all async methods `calculateRawItemsPrice`, `calculateShippingCost`, `calculateTaxes` are executed serially, one by one, hence the performance is degraded comparing to the following parallelized solution:
```java
public @async CompletionStage<Long> calculateTotalPrice(Order order) {
   CompletionStage<Long> rawItemsPrice = calculateRawItemsPrice(order);  
   CompletionStage<Long> shippingCost  = calculateShippingCost(order);  
   CompletionStage<Long> taxes         = calculateTaxes(order);  
   return async( await(rawItemsPrice) + await(shippingCost) + await(taxes) );
}
```
This way all inner async operations are started (almost) simualtenously and are running in parallel, unlike in the first example.

# Generators

# Suspendable methods
# Scheduler - where is my code executed?
# SchedulerResolver - what Scheduler to use?
