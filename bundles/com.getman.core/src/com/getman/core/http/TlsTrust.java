package com.getman.core.http;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/** Builds the {@link SSLContext} for a {@link TrustMode}. */
final class TlsTrust {

	private TlsTrust() {
	}

	/** {@code null} for {@link TrustMode#STRICT}: the HTTP client then uses the JDK default context. */
	static SSLContext sslContext(TrustMode mode) {
		if (mode == TrustMode.STRICT) {
			return null;
		}
		try {
			Predicate<String> lenientFor = mode == TrustMode.ALL ? host -> true : TlsTrust::isLocalHost;
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { new LenientTrustManager(defaultTrustManager(), lenientFor) }, null);
			return context;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not set up TLS: " + e.getMessage(), e);
		}
	}

	static boolean isLocalHost(String host) {
		if (host == null) {
			return false;
		}
		String h = host.toLowerCase(Locale.ROOT);
		if (h.startsWith("[") && h.endsWith("]")) {
			h = h.substring(1, h.length() - 1);
		}
		return h.equals("localhost") || h.endsWith(".localhost") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1")
				|| h.matches("127(\\.\\d{1,3}){3}");
	}

	static X509ExtendedTrustManager defaultTrustManager() throws GeneralSecurityException {
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore) null);
		for (TrustManager tm : factory.getTrustManagers()) {
			if (tm instanceof X509ExtendedTrustManager x) {
				return x;
			}
		}
		throw new GeneralSecurityException("No X509 trust manager available");
	}

	/**
	 * Accepts any server certificate for hosts matching {@code lenientFor} and delegates everything else to the
	 * JDK trust manager, which also checks the host name. Being an {@link X509ExtendedTrustManager}, it gets the
	 * peer host for each handshake, and the JDK does not add its own host-name check on top.
	 */
	static final class LenientTrustManager extends X509ExtendedTrustManager {
		private final X509ExtendedTrustManager delegate;
		private final Predicate<String> lenientFor;

		LenientTrustManager(X509ExtendedTrustManager delegate, Predicate<String> lenientFor) {
			this.delegate = delegate;
			this.lenientFor = lenientFor;
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {
			if (!lenientFor.test(engine == null ? null : engine.getPeerHost())) {
				delegate.checkServerTrusted(chain, authType, engine);
			}
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {
			String host = socket instanceof SSLSocket s ? s.getHandshakeSession() == null ? null
					: s.getHandshakeSession().getPeerHost() : null;
			if (!lenientFor.test(host)) {
				delegate.checkServerTrusted(chain, authType, socket);
			}
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			((X509TrustManager) delegate).checkServerTrusted(chain, authType); // no host known: be strict
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {
			delegate.checkClientTrusted(chain, authType, engine);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {
			delegate.checkClientTrusted(chain, authType, socket);
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			delegate.checkClientTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return delegate.getAcceptedIssuers();
		}
	}
}
