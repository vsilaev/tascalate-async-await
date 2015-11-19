package com.farata.lang.async.examples;

import java.math.BigDecimal;

public class BankAccount {

	public BigDecimal amount;
	public String accountNumber;
	
	public BankAccount(final String accountNumber, final BigDecimal amount) {
		this.accountNumber = accountNumber;
		this.amount = amount;
	}
	
}
