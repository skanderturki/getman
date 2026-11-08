package com.getman.core.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.getman.core.run.CollectionRunner.Outcome;

class RunReportTest {
	private final RunRecord run = RunStoreTest.run("c1", 1_700_000_000_000L);

	@Test
	void junitXmlIsWellFormedWithCounts() throws Exception {
		String xml = RunReport.write(run, RunReport.Format.JUNIT_XML);
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		Element suite = (Element) doc.getElementsByTagName("testsuite").item(0);
		assertEquals("orders-api › Customers", suite.getAttribute("name"));
		assertEquals("4", suite.getAttribute("tests"));
		assertEquals("1", suite.getAttribute("failures"));
		assertEquals("1", suite.getAttribute("errors"));
		assertEquals("1", suite.getAttribute("skipped"));
		assertEquals(4, doc.getElementsByTagName("testcase").getLength());
		Element failure = (Element) doc.getElementsByTagName("failure").item(0);
		assertEquals("Expected 201, got 500.", failure.getAttribute("message"));
		assertEquals("POST bad", ((Element) failure.getParentNode()).getAttribute("name"));
	}

	@Test
	void xmlEscapesAndDropsControlCharacters() {
		assertEquals("a &lt;b&gt; &amp; &quot;c&quot; &apos;d&apos; e", RunReport.xml("a <b> & \"c\" 'd' \u0001e"));
	}

	@Test
	void csvQuotesAndKeepsFormulasAsText() {
		String csv = RunReport.write(run, RunReport.Format.CSV);
		String[] lines = csv.split("\r\n");
		assertEquals("Result,Method,Request,URL,Status,Expected,Time (ms),Details", lines[0]);
		assertEquals("Failed,POST,bad,http://localhost:8080/api/bad,500,201,12,Expected 201, got 500.".replace(
				"Expected 201, got 500.", "\"Expected 201, got 500.\""), lines[2]);
		assertEquals("Not sent,POST,later,http://localhost:8080/api/later,,201,,", lines[4]);
		assertEquals("'=HYPERLINK(1)", RunReport.csvCell("=HYPERLINK(1)"));
		assertEquals("-12", RunReport.csvCell("-12"));
		assertEquals("\"say \"\"hi\"\"\"", RunReport.csvCell("say \"hi\""));
	}

	@Test
	void htmlEscapesEverything() {
		RunRecord evil = new RunRecord(null, 0, "c", "<script>x</script>", "", "", "Local", true,
				List.of(new RunRecord.Item("i", "GET", "<img src=x>", "http://h/?a=<b>", "", Outcome.NOT_CHECKED, 200,
						"200 OK", 5, "")));
		String html = RunReport.write(evil, RunReport.Format.HTML);
		assertFalse(html.contains("<script>x"));
		assertFalse(html.contains("<img"));
		assertTrue(html.contains("&lt;script&gt;"));
		assertTrue(html.contains("run by an AI assistant"));
		assertTrue(RunReport.write(run, RunReport.Format.HTML).contains("1 passed, 1 failed, 1 error, 1 not sent"));
	}

	@Test
	void formatAndFileName() {
		assertEquals(RunReport.Format.CSV, RunReport.Format.forFileName("C:\\x\\Run.CSV"));
		assertEquals(RunReport.Format.JUNIT_XML, RunReport.Format.forFileName("report.xml"));
		assertEquals(RunReport.Format.HTML, RunReport.Format.forFileName("report"));
		assertTrue(RunReport.fileName(run, RunReport.Format.JUNIT_XML).matches("orders-api-Customers-\\d{4}-\\d\\d-\\d\\d-\\d{4}\\.xml"),
				RunReport.fileName(run, RunReport.Format.JUNIT_XML));
	}
}
