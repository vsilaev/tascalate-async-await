package net.tascalate.async.examples.nio;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.AsyncCall;
import net.tascalate.async.api.async;

public class EngineDemo implements Closeable {

	
	public static @async CompletionStage<String> run()  {
		try{
		try (
				EngineDemo other = new EngineDemo();
			) {
			String x = "" + other.hashCode();
			System.out.println(x);
			return AsyncCall.asyncResult("Done");
		} catch (final IOException ex) {
			throw ex;
		}
		} catch (final Throwable ex) {
			"".toString();
			return null;
		}
	}
	
	public void close() throws IOException {
		System.out.println("closed");
	}

}
