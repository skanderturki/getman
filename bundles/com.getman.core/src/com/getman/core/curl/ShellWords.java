package com.getman.core.curl;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a pasted command line into words the way a shell would. Understands POSIX quoting (bash, zsh: single
 * quotes, double quotes, backslashes, {@code $'...'}), line continuations for POSIX ({@code \}), Windows cmd
 * ({@code ^}) and PowerShell (backtick), and cmd's {@code ^} escapes as produced by browsers' "Copy as cURL
 * (cmd)". Parsing stops at an unquoted {@code |}, {@code ;}, {@code &} or {@code >}.
 */
final class ShellWords {

	/** Words plus whether the command was cut short at a pipe, redirect or separator. */
	record Result(List<String> words, boolean truncated) {
	}

	private ShellWords() {
	}

	static Result split(String input) {
		String s = input.replace("\r\n", "\n").replace('\r', '\n');
		if (isCmdStyle(s)) {
			s = unescapeCmd(s);
		}
		s = s.replace("`\n", " "); // PowerShell continuation

		List<String> words = new ArrayList<>();
		StringBuilder word = new StringBuilder();
		boolean inWord = false;
		int i = 0;
		int n = s.length();
		while (i < n) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < n && s.charAt(i + 1) == '\n') { // POSIX continuation
				i += 2;
				continue;
			}
			if (Character.isWhitespace(c)) {
				if (inWord) {
					words.add(word.toString());
					word.setLength(0);
					inWord = false;
				}
				i++;
				continue;
			}
			if (!inWord && c == '#') { // comment to end of line
				while (i < n && s.charAt(i) != '\n') {
					i++;
				}
				continue;
			}
			if (c == '|' || c == ';' || c == '&' || c == '>') {
				if (inWord) {
					words.add(word.toString());
				}
				return new Result(words, true);
			}
			inWord = true;
			if (c == '\'') {
				int end = s.indexOf('\'', i + 1);
				if (end < 0) {
					throw new CurlParseException("A single quote (') is not closed.");
				}
				word.append(s, i + 1, end);
				i = end + 1;
			} else if (c == '$' && i + 1 < n && s.charAt(i + 1) == '\'') {
				i = readAnsiC(s, i + 2, word);
			} else if (c == '"') {
				i = readDoubleQuoted(s, i + 1, word);
			} else if (c == '\\' && i + 1 < n) {
				word.append(s.charAt(i + 1));
				i += 2;
			} else {
				word.append(c);
				i++;
			}
		}
		if (inWord) {
			words.add(word.toString());
		}
		return new Result(words, false);
	}

	/** Browsers' "Copy as cURL (cmd)" uses ^ for line continuations and to escape special characters. */
	private static boolean isCmdStyle(String s) {
		return s.contains("^\n") || s.contains("^\"");
	}

	private static String unescapeCmd(String s) {
		String joined = s.replace("^\n", " ");
		StringBuilder out = new StringBuilder(joined.length());
		for (int i = 0; i < joined.length(); i++) {
			char c = joined.charAt(i);
			if (c == '^' && i + 1 < joined.length()) {
				out.append(joined.charAt(++i));
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static int readDoubleQuoted(String s, int i, StringBuilder word) {
		int n = s.length();
		while (i < n) {
			char c = s.charAt(i);
			if (c == '"') {
				return i + 1;
			}
			if (c == '\\' && i + 1 < n) {
				char next = s.charAt(i + 1);
				if (next == '\n') {
					i += 2;
					continue;
				}
				if (next == '"' || next == '\\' || next == '$' || next == '`') {
					word.append(next);
					i += 2;
					continue;
				}
			}
			word.append(c);
			i++;
		}
		throw new CurlParseException("A double quote (\") is not closed.");
	}

	/** {@code $'...'} strings: backslash escapes like C. */
	private static int readAnsiC(String s, int i, StringBuilder word) {
		int n = s.length();
		while (i < n) {
			char c = s.charAt(i);
			if (c == '\'') {
				return i + 1;
			}
			if (c != '\\' || i + 1 >= n) {
				word.append(c);
				i++;
				continue;
			}
			char e = s.charAt(i + 1);
			i += 2;
			switch (e) {
			case 'n' -> word.append('\n');
			case 't' -> word.append('\t');
			case 'r' -> word.append('\r');
			case 'a' -> word.append('\u0007');
			case 'b' -> word.append('\b');
			case 'e', 'E' -> word.append('\u001b');
			case 'f' -> word.append('\f');
			case 'v' -> word.append('\u000b');
			case 'x' -> i = appendHex(s, i, 2, word);
			case 'u' -> i = appendHex(s, i, 4, word);
			case 'U' -> i = appendHex(s, i, 8, word);
			default -> word.append(e); // \\ \' \" and anything else
			}
		}
		throw new CurlParseException("A $'…' string is not closed.");
	}

	private static int appendHex(String s, int i, int maxDigits, StringBuilder word) {
		int end = i;
		while (end < s.length() && end - i < maxDigits && Character.digit(s.charAt(end), 16) >= 0) {
			end++;
		}
		if (end == i) {
			return i;
		}
		word.appendCodePoint(Integer.parseInt(s.substring(i, end), 16));
		return end;
	}
}
