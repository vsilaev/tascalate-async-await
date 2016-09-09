package net.tascalate.async.examples.bank;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.async;
import static net.tascalate.async.api.AsyncCall.*;

public class AccountTransactionService {

	@async public CompletionStage<BigDecimal> deposit(final BankAccount account, final BigDecimal amount) throws InterruptedException {
		Thread.sleep(2000L);
		account.amount = account.amount.add(amount);
		return asyncResult(account.amount);
	}
	
	@async public CompletionStage<BigDecimal> withdraw(final BankAccount account, final BigDecimal amount) throws InterruptedException, InsufficientFundsException {
		Thread.sleep(30L);
		if (amount.compareTo(account.amount) < 0) {
			account.amount = account.amount.subtract(amount);
			return asyncResult(account.amount);
		} else {
			throw new InsufficientFundsException();
		}
	}
	
}
