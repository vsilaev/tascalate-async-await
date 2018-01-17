# Why async-await?
Asynchronous programming has long been a useful way to perform operations that don’t necessarily need to hold up the flow or responsiveness of an application. Generally, these are either compute-bound operations or I/O bound operations. Compute-bound operations are those where computations can be done on a separate thread, leaving the main thread to continue its own processing, while I/O bound operations involve work that takes place externally and may not need to block a thread while such work takes place. Common examples of I/O bound operations are file and network operations. 

Traditional asynchronous programming involves using callbacks these are executed on operation completion. The API differs across languages and libraries, but the idea is always the same: you are firing some asynchronous operation, get back some kind of `Promise` as a result and attach success/failure calbacks that are executed once asynchronous operation is completed. However, these approach is associated numerous hardships:
1. You should explicitly pass contextual variables to callbacks. Sure, you can use lambdas to capture lexical context, but this does not eliminate the problem completly. And sometimes even sacrifies readability of the code - when you have a lot of lambda functions with complex body.
2. Coordination of asynchronous operations with callbacks is dificult: any branching logic inside the chain of asynchronous callbacks is a pain; resource management provided by `try-with-resources` constructs are not possible with asynchronous callbacks as well as many other control flow statements; handling failures is radically different from the familiar `try/catch` used in synchronous code.
3. Different callbacks are executed on different threads. Hence special care should be taken where the application flow resumes. The issue is very critical when application runs in managed environment like JEE or UI framework (JavaFX, Swing, etc).

To alleviate aforementioned readability and maintainability issues some languages provides `async/await` asynchronous programming model. This lets developer make asynchronous calls just as easily as she can invoke synchronous ones, with the tiny addition of a keyword `await` and without sacrifying any of asynchronous programming benefits. With `await` keyword asynchronous calls may be used inside regular control flow statements (including exception handling) as naturally as calls to synchronous methods. The list of the languages that support this model is steadly growing: C# 5, ECMAScript 7, Kotlin, Scala. 

Tascalate Async/Await library enables `async/await` model for projects built with the Java 8 and beyond. The implementation is based on [continuations for Java](https://github.com/vsilaev/tascalate-javaflow) and provides runtime API + bytecode enchancement tools to let developers use syntax constructs similar to C# 5 or ECMAScript 7 with pure Java.

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
				<version>2.1</version>
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
The first type of functions the library supports is asycnhronous task. Asynchronous task is a method (either instance or class method) that is annotated with `net.tascalate.async.api.async` annotation and returns `CompletionStage<T>` or `void`. In the later case it is a "fire-and-forget" task that is intended primarly to be used for event handlers inside UI framework (like JavaFX or Swing). Let us write a simple example:
```java
import static net.tascalate.async.api.AsyncCall.async;
import static net.tascalate.async.api.AsyncCall.await;
import net.tascalate.async.api.async;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class MyClass {
    public @async CompletionStage<String> mergeStringsAsync() {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
          String v = await( decorateStringsAsync(i, "async ", " awaited") );
          result.append(v).append('\n');
        }
        return async(result.toString());
    }
    
    public @async CompletionStage<String> decorateStringsAsync(int i, String prefix, String suffix) {
        String value = prefix + await( produceStringAsync("value " + i) ) + suffix;
        return async(value);
    }
    
    // Emulate some asynchronous business service call
    private static CompletionStage<String> produceStringAsync(String value) {
        return CompletableFuture.supplyAsync(() -> value, executor);
    }
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
}
```
# Suspendable methods
# Generators
# Scheduler - where is my code executed?
# SchedulerResolver - what Scheduler to use?
