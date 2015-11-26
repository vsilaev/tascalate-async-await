package com.farata.lang.async.examples.bank;

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
			System.out.println("FINISHED ITERATION #" + i);
			messageFuture.toCompletableFuture().join();
		}
	}
}
