package net.tascalate.async.examples.bank;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.examples.bank.FraudDetectionService.Result;

public class FraudDetectionWS {
	
	public CompletionStage<FraudDetectionService.Result> checkFraud(final BankAccount bankAccount, final BigDecimal amount) {
		final CompletableFuture<FraudDetectionService.Result> result = new CompletableFuture<>();
		new Thread(() -> {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				result.completeExceptionally(e);
			}
			if (bankAccount.accountNumber.startsWith("5"))
				result.complete(Result.DENY);
			else
				result.complete(Result.ALLOW);
		}).start();
		return result;
	}
}
