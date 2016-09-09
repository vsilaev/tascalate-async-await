package net.tascalate.async.examples.bank;

import static net.tascalate.async.api.AsyncCall.asyncResult;
import static net.tascalate.async.api.AsyncCall.await;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.async;
import net.tascalate.async.examples.bank.FraudDetectionService.Result;

public class MoneyWithdrawalTask {

	private BankAccount bankAccount;
	
	private FraudDetectionService fraudDetectionService = new FraudDetectionService();
	private AccountTransactionService accountTransactionService = new AccountTransactionService();

	public MoneyWithdrawalTask(final BankAccount bankAccount) {
		this.bankAccount = bankAccount;
	}

	private static String timeStamp(final String s) {
		return new Date() + ": " + s;
	}

	private String formatOutput(final String operation) {
		return "Transfer [" + operation + "] to/from bank account #" + bankAccount.accountNumber;
	}
	
	@async public CompletionStage<String> execute(final BigDecimal amount) throws InterruptedException {
		
		class DemoPrint {
			@async CompletionStage<Integer> go() {
				System.out.println("Inner Class " + amount + " " + MoneyWithdrawalTask.this);
				return asyncResult(10);
			}
		}
		
		try {
			await(
				new DemoPrint().go()
			);
			final FraudDetectionService.Result fraudCheckResult = await(fraudDetectionService.checkFraud(bankAccount, amount));
			final BigDecimal currentBalance = await(accountTransactionService.withdraw(bankAccount, amount));
			
			if (fraudCheckResult == Result.DENY) {
				throw new IllegalStateException("Fraud detected");
			}
			return asyncResult(timeStamp(formatOutput("withdraw")) + ": success, balance is " + currentBalance);
					
		} catch (final InsufficientFundsException ex) {
			return asyncResult(timeStamp(formatOutput("withdraw")) + ": failed, insufficient funds (" + bankAccount.amount + ")"); 
		}
	}
	
}
