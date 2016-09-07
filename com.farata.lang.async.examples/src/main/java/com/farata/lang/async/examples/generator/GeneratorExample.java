package com.farata.lang.async.examples.generator;

import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.concurrent.TaskExecutorService;
import net.tascalate.concurrent.TaskExecutors;

import com.farata.lang.async.api.Generator;
import com.farata.lang.async.api.async;
import com.farata.lang.async.core.AsyncExecutor;
import com.farata.lang.async.core.AsyncGenerator;

import static com.farata.lang.async.api.AsyncCall.*;

public class GeneratorExample {

	final private static TaskExecutorService executor = TaskExecutors.newFixedThreadPool(4);
	
	public static void main(String[] args) {
		final CompletionStage<String> result = new GeneratorExample().mergeStrings();
		result.whenComplete((v, e) -> {
			if (null == e) {
				System.out.println(v);
			} else {
				e.printStackTrace(System.err);
			}
			executor.shutdown();
		});
	}
	
	
	@async CompletionStage<String> mergeStrings() {
		StringJoiner joiner = new StringJoiner(", ");
		try (Generator<String> generator = produceStrings()) {
			String param = "GO!";
			while (generator.next(param)) {
				System.out.println("Received: " + generator.current());
				param = generator.current();
				joiner.add(generator.current());
			}
		}
		return asyncResult(joiner.toString());
	}

	@continuable Generator<String> produceStrings() {
		final AsyncGenerator<String> method = new AsyncGenerator<String>() {

			@Override
			public void run() {
				$$start$$();
				try {
					Object o;
					o = $$yield(waitString("ABC"), this);
					System.out.println("Processed: " + o + ", " + new Date());
					
					String s = await(waitString("InternalAsync"));
					System.out.println("INTERNALLY: " + s);
					
					o = $$yield(waitString("DEF"), this);
					System.out.println("Processed: " + o + ", " + new Date());
					
					o = $$yield("NO-WAIT", this);
					System.out.println("Processed: " + o + ", " + new Date());
					
					try (Generator<String> nested = moreStrings()) {
						while(nested.next()) {
							System.out.println("Nested: " + nested.current());
							if (Integer.parseInt(nested.current()) % 2 == 0) {
								o = $$yield(waitString("NESTED-" + nested.current()), this);
								System.out.println("Nested Processed: " + o + ", " + new Date());
							}
						}
					}
					
					String x;
					$$yield(x = await(waitString("AWYV")), this);
					
					System.out.println("Awaited&Yielded:" + x);
					
					o = $$yield(waitString("XYZ"), this);
					System.out.println("Processed Final: " + o + ", " + new Date());
					
				} finally {
					$$finish$$();
				}
			}
			
		};
		AsyncExecutor.execute(method);
		return method.generator;
	}
	
	@continuable Generator<String> moreStrings() {
		final AsyncGenerator<String> method = new AsyncGenerator<String>() {
			@Override
			public void run() {
				$$start$$();
				try {
					$$yield(waitString("111"), this);
					$$yield(waitString("222"), this);
					$$yield("333", this);
					$$yield(waitString("444"), this);
					
				} finally {
					$$finish$$();
				}
			}
		};
		AsyncExecutor.execute(method);
		return method.generator;
	}
	
	private CompletionStage<String> waitString(final String value) {
		return executor.submit(() -> {
			Thread.sleep(200L);
			return value;
		});
	}
}
