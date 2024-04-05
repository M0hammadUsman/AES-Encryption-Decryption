package org.aes.helper;

import lombok.SneakyThrows;

import java.security.SecureRandom;

public class TimeCalculator {
	
	private TimeCalculator() {
	
	}
	
	@SneakyThrows
	public static long calculateRetryDelay(int retryIndex) {
		long exponentialDelay = (long) Math.pow(2, retryIndex);
		// Adding random ms to waitTillRetryAttempt so there are no retry attempts at exact same time
		long randomDelay = SecureRandom.getInstanceStrong().nextInt(1001);
		return exponentialDelay + randomDelay;
	}
	
}
