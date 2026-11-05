package com.getman.core.http;

import java.time.Duration;

/** Client-wide options, edited on the GetMan preference page. */
public record HttpSettings(Duration connectTimeout, Duration requestTimeout, boolean followRedirects,
		TrustMode trustMode) {

	public static final HttpSettings DEFAULT = new HttpSettings(Duration.ofSeconds(10), Duration.ofSeconds(60), true,
			TrustMode.LOCALHOST);

	public HttpSettings {
		if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
			throw new IllegalArgumentException("connectTimeout must be positive");
		}
		if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
			throw new IllegalArgumentException("requestTimeout must be positive");
		}
		if (trustMode == null) {
			trustMode = TrustMode.LOCALHOST;
		}
	}
}
