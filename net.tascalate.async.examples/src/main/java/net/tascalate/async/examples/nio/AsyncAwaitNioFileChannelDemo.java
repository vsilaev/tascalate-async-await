/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.examples.nio;

import static net.tascalate.async.CallContext.async;
import static net.tascalate.async.CallContext.await;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.async;
import net.tascalate.nio.channels.AsynchronousFileChannel;

public class AsyncAwaitNioFileChannelDemo {

	public static void main(final String[] argv) throws Exception {
		final AsyncAwaitNioFileChannelDemo demo = new AsyncAwaitNioFileChannelDemo();
		final CompletionStage<String> result = demo.processFile("./.project");
		
		System.out.println("Returned to caller " + LocalTime.now());
		final CompletionStage<?> waiter = result.whenComplete((r, e) -> {
			if ( e != null ) {
				System.out.println("Error " +  LocalTime.now());
				e.printStackTrace(System.out);
			} else {
				System.out.println("Result " +  LocalTime.now());
				System.out.println(r);
			}
		});
		
		// Need to wait because NIO uses daemon threads that do not prevent program exit
		System.out.println("Start waiting for result to prevent program close...");
		waiter.toCompletableFuture().join();
		
	}

	
	
	public @async CompletionStage<String> processFile(final String fileName) throws IOException {
		final Path path = Paths.get(new File(fileName).toURI());
		try (
				final AsynchronousFileChannel file = AsynchronousFileChannel.open(path, Collections.singleton(StandardOpenOption.READ), null);
				final FileLock lock = await(file.lockAll(true))
			) {
			System.out.println("In process, shared lock: " + lock);
			final ByteBuffer buffer = ByteBuffer.allocateDirect((int)file.size());
			
			await( file.read(buffer, 0L) );
			System.out.println("In process, bytes read: " + buffer);
			buffer.rewind();
   
			final String result = processBytes(buffer);
			 
			return async(result);
			
		} catch (final IOException ex) {
			ex.printStackTrace(System.out);
			throw ex;
		}
	}
	
	private String processBytes(final ByteBuffer buffer) throws IOException {
		final StringBuilder result = new StringBuilder();
		final char[] chars = new char[4096];
		try (final InputStreamReader in = new InputStreamReader(new ByteBufferInputStream(buffer))) {
			int count = 0;
			while( (count = in.read(chars)) > 0) {
				result.append(chars, 0, count);
			};
			return result.toString();
		}
	}
}
