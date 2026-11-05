package com.getman.core.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer so the plugin has no third-party dependencies.
 * <p>
 * Values map to: object → {@code LinkedHashMap<String,Object>} (key order kept), array → {@code List<Object>},
 * string → {@code String}, number → {@code Long} or {@code BigDecimal}, {@code Boolean}, {@code null}.
 */
public final class Json {

	private Json() {
	}

	/** Parses a JSON document. Throws {@link JsonException} with position info on invalid input. */
	public static Object parse(String text) {
		Parser parser = new Parser(text);
		parser.skipWhitespace();
		Object value = parser.readValue();
		parser.skipWhitespace();
		if (!parser.atEnd()) {
			throw parser.error("Unexpected content after JSON value");
		}
		return value;
	}

	/** Compact JSON. */
	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		writeValue(sb, value, -1, 0);
		return sb.toString();
	}

	/** JSON indented with two spaces. */
	public static String writePretty(Object value) {
		StringBuilder sb = new StringBuilder();
		writeValue(sb, value, 2, 0);
		return sb.toString();
	}

	/** Re-indents a JSON document. Throws {@link JsonException} if it is not valid JSON. */
	public static String prettify(String text) {
		return writePretty(parse(text));
	}

	private static void writeValue(StringBuilder sb, Object value, int indent, int depth) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String s) {
			writeString(sb, s);
		} else if (value instanceof Boolean || value instanceof Long || value instanceof Integer
				|| value instanceof Short || value instanceof Byte) {
			sb.append(value);
		} else if (value instanceof BigDecimal d) {
			sb.append(d.toString());
		} else if (value instanceof Number n) {
			double d = n.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new JsonException("Cannot write non-finite number " + d);
			}
			sb.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
		} else if (value instanceof Map<?, ?> map) {
			writeObject(sb, map, indent, depth);
		} else if (value instanceof Iterable<?> it) {
			writeArray(sb, it, indent, depth);
		} else {
			throw new JsonException("Cannot write " + value.getClass().getName() + " as JSON");
		}
	}

	private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent, int depth) {
		if (map.isEmpty()) {
			sb.append("{}");
			return;
		}
		sb.append('{');
		Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<?, ?> e = it.next();
			newline(sb, indent, depth + 1);
			writeString(sb, String.valueOf(e.getKey()));
			sb.append(indent >= 0 ? ": " : ":");
			writeValue(sb, e.getValue(), indent, depth + 1);
			if (it.hasNext()) {
				sb.append(',');
			}
		}
		newline(sb, indent, depth);
		sb.append('}');
	}

	private static void writeArray(StringBuilder sb, Iterable<?> items, int indent, int depth) {
		Iterator<?> it = items.iterator();
		if (!it.hasNext()) {
			sb.append("[]");
			return;
		}
		sb.append('[');
		while (it.hasNext()) {
			newline(sb, indent, depth + 1);
			writeValue(sb, it.next(), indent, depth + 1);
			if (it.hasNext()) {
				sb.append(',');
			}
		}
		newline(sb, indent, depth);
		sb.append(']');
	}

	private static void newline(StringBuilder sb, int indent, int depth) {
		if (indent >= 0) {
			sb.append('\n');
			sb.append(" ".repeat(indent * depth));
		}
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"' -> sb.append("\\\"");
			case '\\' -> sb.append("\\\\");
			case '\n' -> sb.append("\\n");
			case '\r' -> sb.append("\\r");
			case '\t' -> sb.append("\\t");
			case '\b' -> sb.append("\\b");
			case '\f' -> sb.append("\\f");
			default -> {
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
			}
		}
		sb.append('"');
	}

	private static final class Parser {
		private final String s;
		private int pos;

		Parser(String text) {
			this.s = text == null ? "" : text;
		}

		boolean atEnd() {
			return pos >= s.length();
		}

		void skipWhitespace() {
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
					pos++;
				} else {
					break;
				}
			}
		}

		Object readValue() {
			if (atEnd()) {
				throw error("Unexpected end of input");
			}
			char c = s.charAt(pos);
			return switch (c) {
			case '{' -> readObject();
			case '[' -> readArray();
			case '"' -> readString();
			case 't' -> readLiteral("true", Boolean.TRUE);
			case 'f' -> readLiteral("false", Boolean.FALSE);
			case 'n' -> readLiteral("null", null);
			default -> {
				if (c == '-' || (c >= '0' && c <= '9')) {
					yield readNumber();
				}
				throw error("Unexpected character '" + c + "'");
			}
			};
		}

		private Map<String, Object> readObject() {
			Map<String, Object> map = new LinkedHashMap<>();
			pos++; // {
			skipWhitespace();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipWhitespace();
				if (peek() != '"') {
					throw error("Expected string key");
				}
				String key = readString();
				skipWhitespace();
				expect(':');
				skipWhitespace();
				map.put(key, readValue());
				skipWhitespace();
				char c = next();
				if (c == '}') {
					return map;
				}
				if (c != ',') {
					pos--;
					throw error("Expected ',' or '}'");
				}
			}
		}

		private List<Object> readArray() {
			List<Object> list = new ArrayList<>();
			pos++; // [
			skipWhitespace();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (true) {
				skipWhitespace();
				list.add(readValue());
				skipWhitespace();
				char c = next();
				if (c == ']') {
					return list;
				}
				if (c != ',') {
					pos--;
					throw error("Expected ',' or ']'");
				}
			}
		}

		private String readString() {
			pos++; // opening quote
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (atEnd()) {
					throw error("Unterminated string");
				}
				char c = s.charAt(pos++);
				if (c == '"') {
					return sb.toString();
				}
				if (c == '\\') {
					if (atEnd()) {
						throw error("Unterminated escape");
					}
					char e = s.charAt(pos++);
					switch (e) {
					case '"' -> sb.append('"');
					case '\\' -> sb.append('\\');
					case '/' -> sb.append('/');
					case 'b' -> sb.append('\b');
					case 'f' -> sb.append('\f');
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case 'u' -> {
						if (pos + 4 > s.length()) {
							throw error("Invalid unicode escape");
						}
						try {
							sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
						} catch (NumberFormatException ex) {
							throw error("Invalid unicode escape");
						}
						pos += 4;
					}
					default -> {
						pos--;
						throw error("Invalid escape '\\" + e + "'");
					}
					}
				} else if (c < 0x20) {
					pos--;
					throw error("Control character in string");
				} else {
					sb.append(c);
				}
			}
		}

		private Object readNumber() {
			int start = pos;
			if (peek() == '-') {
				pos++;
			}
			if (peek() == '0') {
				pos++;
			} else if (isDigit(peek())) {
				while (isDigit(peek())) {
					pos++;
				}
			} else {
				throw error("Invalid number");
			}
			boolean integral = true;
			if (peek() == '.') {
				integral = false;
				pos++;
				if (!isDigit(peek())) {
					throw error("Invalid number");
				}
				while (isDigit(peek())) {
					pos++;
				}
			}
			if (peek() == 'e' || peek() == 'E') {
				integral = false;
				pos++;
				if (peek() == '+' || peek() == '-') {
					pos++;
				}
				if (!isDigit(peek())) {
					throw error("Invalid number");
				}
				while (isDigit(peek())) {
					pos++;
				}
			}
			String text = s.substring(start, pos);
			if (integral) {
				try {
					return Long.parseLong(text);
				} catch (NumberFormatException e) {
					// too large for a long
				}
			}
			return new BigDecimal(text);
		}

		private Object readLiteral(String word, Object value) {
			if (!s.startsWith(word, pos)) {
				throw error("Unexpected token");
			}
			pos += word.length();
			return value;
		}

		private char peek() {
			return atEnd() ? '\0' : s.charAt(pos);
		}

		private char next() {
			if (atEnd()) {
				throw error("Unexpected end of input");
			}
			return s.charAt(pos++);
		}

		private void expect(char c) {
			if (peek() != c) {
				throw error("Expected '" + c + "'");
			}
			pos++;
		}

		private static boolean isDigit(char c) {
			return c >= '0' && c <= '9';
		}

		JsonException error(String message) {
			int line = 1;
			int col = 1;
			for (int i = 0; i < pos && i < s.length(); i++) {
				if (s.charAt(i) == '\n') {
					line++;
					col = 1;
				} else {
					col++;
				}
			}
			return new JsonException(message + " at line " + line + ", column " + col);
		}
	}
}
