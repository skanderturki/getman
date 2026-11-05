package com.getman.core.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.Test;

import com.getman.core.model.RequestModel;
import com.getman.core.model.ResponseModel;

class TlsTrustTest {

	@Test
	void localHostNames() {
		for (String h : new String[] { "localhost", "LOCALHOST", "api.localhost", "127.0.0.1", "127.1.2.3", "::1",
				"[::1]" }) {
			assertTrue(TlsTrust.isLocalHost(h), h);
		}
		for (String h : new String[] { "example.com", "localhost.example.com", "10.0.0.1", "128.0.0.1", "", null }) {
			assertFalse(TlsTrust.isLocalHost(h), String.valueOf(h));
		}
	}

	/** A delegate that rejects everything, so we can see when the lenient manager consults it. */
	private static final X509ExtendedTrustManager REJECT_ALL = new X509ExtendedTrustManager() {
		@Override
		public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException {
			throw new CertificateException("rejected");
		}

		@Override
		public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException {
			throw new CertificateException("rejected");
		}

		@Override
		public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException {
			throw new CertificateException("rejected");
		}

		@Override
		public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] c, String a) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	};

	@Test
	void lenientOnlyForMatchingHosts() throws Exception {
		TlsTrust.LenientTrustManager tm = new TlsTrust.LenientTrustManager(REJECT_ALL, TlsTrust::isLocalHost);
		SSLContext ctx = SSLContext.getDefault();
		tm.checkServerTrusted(new X509Certificate[0], "RSA", ctx.createSSLEngine("localhost", 443));
		assertThrows(CertificateException.class,
				() -> tm.checkServerTrusted(new X509Certificate[0], "RSA", ctx.createSSLEngine("example.com", 443)));
		assertThrows(CertificateException.class, () -> tm.checkServerTrusted(new X509Certificate[0], "RSA"));
	}

	@Test
	void strictUsesJdkDefaults() {
		assertEquals(null, TlsTrust.sslContext(TrustMode.STRICT));
		assertTrue(TlsTrust.sslContext(TrustMode.LOCALHOST) != null);
	}

	@Test
	void selfSignedLocalServerEndToEnd() throws Exception {
		Path dir = Files.createTempDirectory("getman-tls");
		Path keystore = dir.resolve("server.p12");
		char[] password = "changeit".toCharArray();
		Path keytool = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
		if (!Files.isExecutable(keytool)) {
			return; // no keytool in this runtime: nothing to test end to end
		}
		Process p = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "srv", "-keyalg", "RSA",
				"-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
				"-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", "changeit", "-keypass",
				"changeit").redirectErrorStream(true).start();
		p.getInputStream().readAllBytes();
		assertTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "keytool failed");

		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (InputStream in = Files.newInputStream(keystore)) {
			ks.load(in, password);
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, password);
		SSLContext serverCtx = SSLContext.getInstance("TLS");
		serverCtx.init(kmf.getKeyManagers(), null, null);

		assertTrue(send(serverCtx, TrustMode.STRICT).getError().contains("certificate"));
		ResponseModel ok = send(serverCtx, TrustMode.LOCALHOST);
		assertFalse(ok.isError(), ok.getError());
		assertEquals(200, ok.getStatusCode());
		assertEquals("secure", ok.getBody());

		Files.deleteIfExists(keystore);
		Files.deleteIfExists(dir);
	}

	/** Serves one HTTPS request on 127.0.0.1 and sends it through an HttpService with {@code mode}. */
	private static ResponseModel send(SSLContext serverCtx, TrustMode mode) throws Exception {
		try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory().createServerSocket(0, 1,
				InetAddress.getLoopbackAddress())) {
			Thread t = new Thread(() -> {
				try (Socket s = server.accept()) {
					InputStream in = s.getInputStream();
					int matched = 0;
					int b;
					while (matched < 4 && (b = in.read()) >= 0) { // read until \r\n\r\n
						matched = (b == (matched % 2 == 0 ? '\r' : '\n')) ? matched + 1 : (b == '\r' ? 1 : 0);
					}
					OutputStream out = s.getOutputStream();
					out.write("HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nsecure"
							.getBytes(StandardCharsets.US_ASCII));
					out.flush();
				} catch (IOException e) {
					// the strict client aborts the handshake: expected
				}
			}, "test-tls-server");
			t.setDaemon(true);
			t.start();
			HttpService http = new HttpService(
					new HttpSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), true, mode));
			RequestModel r = new RequestModel();
			r.setUrl("https://127.0.0.1:" + server.getLocalPort() + "/");
			return http.send(r).get(20, TimeUnit.SECONDS);
		}
	}
}
