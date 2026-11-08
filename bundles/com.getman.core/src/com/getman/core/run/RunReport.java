package com.getman.core.run;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.getman.core.run.CollectionRunner.Outcome;

/** Writes a {@link RunRecord} as an HTML report, a CSV file or a JUnit XML file (read by CI servers). */
public final class RunReport {
	public enum Format {
		HTML("html"), CSV("csv"), JUNIT_XML("xml");

		private final String extension;

		Format(String extension) {
			this.extension = extension;
		}

		public String extension() {
			return extension;
		}

		/** The format for a file name's extension; HTML when there is none or it is unknown. */
		public static Format forFileName(String fileName) {
			String lower = fileName.toLowerCase(Locale.ROOT);
			for (Format f : values()) {
				if (lower.endsWith("." + f.extension)) {
					return f;
				}
			}
			return HTML;
		}
	}

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private RunReport() {
	}

	public static String write(RunRecord run, Format format) {
		return switch (format) {
		case HTML -> html(run);
		case CSV -> csv(run);
		case JUNIT_XML -> junitXml(run);
		};
	}

	/** "Passed", "Failed", "Sent" (no expected status), "Error" or "Not sent". */
	public static String label(Outcome outcome) {
		return switch (outcome) {
		case PASSED -> "Passed";
		case FAILED -> "Failed";
		case NOT_CHECKED -> "Sent";
		case ERROR -> "Error";
		case STOPPED -> "Not sent";
		};
	}

	/** A file name for the report, e.g. "orders-api-2026-11-08-1528.html". */
	public static String fileName(RunRecord run, Format format) {
		String base = run.title().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
		return (base.isEmpty() ? "run" : base) + "-"
				+ DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").format(local(run.startedAtMillis())) + "."
				+ format.extension();
	}

	static String when(RunRecord run) {
		return WHEN.format(local(run.startedAtMillis()));
	}

	private static java.time.ZonedDateTime local(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
	}

	private static String status(RunRecord.Item i) {
		return i.status() == 0 ? "" : i.statusLine().isEmpty() ? String.valueOf(i.status()) : i.statusLine();
	}

	// --- CSV -----------------------------------------------------------------------------------

	static String csv(RunRecord run) {
		StringBuilder sb = new StringBuilder();
		row(sb, List.of("Result", "Method", "Request", "URL", "Status", "Expected", "Time (ms)", "Details"));
		for (RunRecord.Item i : run.items()) {
			row(sb, List.of(label(i.outcome()), i.method(), i.name(), i.url(),
					i.status() == 0 ? "" : String.valueOf(i.status()), i.expected(),
					i.outcome() == Outcome.STOPPED ? "" : String.valueOf(i.durationMs()), i.message()));
		}
		return sb.toString();
	}

	private static void row(StringBuilder sb, List<String> cells) {
		for (int c = 0; c < cells.size(); c++) {
			if (c > 0) {
				sb.append(',');
			}
			sb.append(csvCell(cells.get(c)));
		}
		sb.append("\r\n");
	}

	static String csvCell(String value) {
		String v = value;
		// Spreadsheets run cells starting with these as formulas: keep them text.
		if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9.]+")) {
			v = "'" + v;
		}
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

	// --- JUnit XML -----------------------------------------------------------------------------

	static String junitXml(RunRecord run) {
		CollectionRunner.Summary s = run.summary();
		String time = seconds(run.durationMs());
		String suite = "name=\"" + xml(run.title()) + "\" tests=\"" + s.total() + "\" failures=\"" + s.failed()
				+ "\" errors=\"" + s.errors() + "\" skipped=\"" + s.stopped() + "\" time=\"" + time + "\"";
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<testsuites ").append(suite).append(">\n");
		sb.append("  <testsuite ").append(suite).append(" timestamp=\"")
				.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(local(run.startedAtMillis())).replaceAll("\\.\\d+$", ""))
				.append("\">\n");
		sb.append("    <properties>\n");
		property(sb, "environment", run.environment());
		property(sb, "collection", run.collectionName());
		if (!run.folderName().isEmpty()) {
			property(sb, "folder", run.folderName());
		}
		sb.append("    </properties>\n");
		String classname = xml(run.title().replace(" › ", "."));
		for (RunRecord.Item i : run.items()) {
			sb.append("    <testcase classname=\"").append(classname).append("\" name=\"")
					.append(xml(i.method() + " " + i.label())).append("\" time=\"").append(seconds(i.durationMs()))
					.append('"');
			String detail = i.method() + " " + i.url() + (i.status() == 0 ? "" : "\n" + status(i));
			switch (i.outcome()) {
			case FAILED -> sb.append(">\n      <failure message=\"").append(xml(i.message()))
					.append("\" type=\"status\">").append(xml(detail)).append("</failure>\n    </testcase>\n");
			case ERROR -> sb.append(">\n      <error message=\"").append(xml(i.message())).append("\">")
					.append(xml(detail)).append("</error>\n    </testcase>\n");
			case STOPPED -> sb.append(">\n      <skipped message=\"Not sent: the run was stopped.\"/>\n    </testcase>\n");
			case NOT_CHECKED -> sb.append(">\n      <system-out>").append(xml(detail + "\nNo expected status: not checked."))
					.append("</system-out>\n    </testcase>\n");
			case PASSED -> sb.append("/>\n");
			}
		}
		sb.append("  </testsuite>\n</testsuites>\n");
		return sb.toString();
	}

	private static void property(StringBuilder sb, String name, String value) {
		sb.append("      <property name=\"").append(name).append("\" value=\"").append(xml(value)).append("\"/>\n");
	}

	private static String seconds(long ms) {
		return String.format(Locale.ROOT, "%.3f", ms / 1000.0);
	}

	/** Escapes for XML text and attributes, dropping characters XML 1.0 does not allow. */
	static String xml(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int k = 0; k < s.length(); k++) {
			char ch = s.charAt(k);
			switch (ch) {
			case '&' -> sb.append("&amp;");
			case '<' -> sb.append("&lt;");
			case '>' -> sb.append("&gt;");
			case '"' -> sb.append("&quot;");
			case '\'' -> sb.append("&apos;");
			default -> {
				if (ch >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') {
					sb.append(ch);
				}
			}
			}
		}
		return sb.toString();
	}

	// --- HTML ----------------------------------------------------------------------------------

	static String html(RunRecord run) {
		CollectionRunner.Summary s = run.summary();
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
				.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
				.append("<title>Run ").append(xml(run.title())).append("</title>\n<style>\n")
				.append("body{font-family:system-ui,-apple-system,'Segoe UI',sans-serif;margin:24px;color:#1f2328;background:#fff}\n")
				.append("h1{font-size:20px;margin:0 0 4px}\n.meta{color:#59636e;margin:0 0 16px}\n")
				.append(".summary{font-size:15px;margin:0 0 16px;padding:10px 12px;border-radius:6px;background:#f6f8fa}\n")
				.append(".summary.bad{background:#ffebe9}.summary.good{background:#dafbe1}\n")
				.append("table{border-collapse:collapse;width:100%;font-size:14px}\n")
				.append("th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #d1d9e0;vertical-align:top}\n")
				.append("th{background:#f6f8fa}\ntd.url{font-family:ui-monospace,Consolas,monospace;font-size:12px;word-break:break-all}\n")
				.append(".passed{color:#1a7f37;font-weight:600}.failed,.error{color:#cf222e;font-weight:600}\n")
				.append(".stopped{color:#9a6700}.sent{color:#59636e}\n")
				.append("footer{margin-top:16px;color:#59636e;font-size:12px}\n</style>\n</head>\n<body>\n");
		sb.append("<h1>Run ").append(xml(run.title())).append("</h1>\n<p class=\"meta\">")
				.append(xml(when(run))).append(" · ")
				.append(run.environment().isEmpty() ? "no environment" : "environment " + xml(run.environment()))
				.append(run.viaAssistant() ? " · run by an AI assistant" : "").append("</p>\n");
		sb.append("<p class=\"summary ").append(run.hasProblems() ? "bad" : s.passed() > 0 ? "good" : "")
				.append("\">").append(xml(s.toString())).append(" · ").append(s.total())
				.append(s.total() == 1 ? " request" : " requests").append(", ").append(run.durationMs())
				.append(" ms</p>\n");
		sb.append("<table>\n<thead><tr><th>Result</th><th>Request</th><th>URL</th><th>Status</th><th>Expected</th>")
				.append("<th>Time</th><th>Details</th></tr></thead>\n<tbody>\n");
		for (RunRecord.Item i : run.items()) {
			String css = switch (i.outcome()) {
			case PASSED -> "passed";
			case FAILED -> "failed";
			case ERROR -> "error";
			case STOPPED -> "stopped";
			case NOT_CHECKED -> "sent";
			};
			String mark = switch (i.outcome()) {
			case PASSED -> "✓ ";
			case FAILED, ERROR -> "✗ ";
			default -> "";
			};
			sb.append("<tr><td class=\"").append(css).append("\">").append(mark).append(label(i.outcome()))
					.append("</td><td>").append(xml(i.method())).append(' ').append(xml(i.name()))
					.append("</td><td class=\"url\">").append(xml(i.url())).append("</td><td>").append(xml(status(i)))
					.append("</td><td>").append(xml(i.expected())).append("</td><td>")
					.append(i.outcome() == Outcome.STOPPED ? "" : i.durationMs() + " ms").append("</td><td>")
					.append(xml(i.message())).append("</td></tr>\n");
		}
		sb.append("</tbody>\n</table>\n<footer>Generated by GetMan. Requests without an expected status are only ")
				.append("sent, not checked.</footer>\n</body>\n</html>\n");
		return sb.toString();
	}
}
