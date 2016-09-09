package net.tascalate.async.examples.bank;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.async;
import static net.tascalate.async.api.AsyncCall.*;

public class FraudDetectionService {
	
	public enum Result {
		ALLOW, DENY;
	}
	
	
	final private FraudDetectionWS ws = new FraudDetectionWS();
	
	@async public CompletionStage<Result> checkFraud(final BankAccount bankAccount, final BigDecimal amount) throws InterruptedException {
		final Result resultFromWs = await( ws.checkFraud(bankAccount, amount) );
		return asyncResult( resultFromWs );
	}

}
