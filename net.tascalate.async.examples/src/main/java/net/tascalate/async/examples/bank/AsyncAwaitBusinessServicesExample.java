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
package net.tascalate.async.examples.bank;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.CompletionStage;

public class AsyncAwaitBusinessServicesExample {
	public static void main(final String[] argv) {
		
		final BankAccount account = new BankAccount("301303241007", BigDecimal.valueOf(1200.0));
		
		final MoneyWithdrawalTask task = new MoneyWithdrawalTask(account);
		
		for (int i = 1; i <= 3; i++) {
			System.out.println(new Date() + " START ITERATION #" + i);
			final CompletionStage<String> messageFuture;
			try {
				messageFuture = task.execute(BigDecimal.valueOf(510.50));
				System.out.println(new Date() + " FUTURE: " + messageFuture);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			
			messageFuture.whenComplete((result, error) -> {
				if (error != null) {
					System.out.println("Error: " + error);
				} else {
					System.out.println("Result: " + result);
				}
			});
			if (i == 2) {
			    //Future.class.cast(messageFuture).cancel(true);
			}
			System.out.println("FINISHED ITERATION #" + i);
			messageFuture.toCompletableFuture().join();
		}
	}
}
