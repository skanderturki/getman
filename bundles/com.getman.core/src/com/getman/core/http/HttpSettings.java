package com.getman.core.http;

import java.time.Duration;

/** Client-wide options. A preferences page can expose these later. */
public record HttpSettings(Duration connectTimeout, Duration requestTimeout, boolean followRedirects) {

	public static final HttpSettings DEFAULT = new HttpSettings(Duration.ofSeconds(10), Duration.ofSeconds(60), true);

	public HttpSettings {
		if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
			throw new IllegalArgumentException("connectTimeout must be positive");
		}
		if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
			throw new IllegalArgumentException("requestTimeout must be positive");
		}
	}
}
