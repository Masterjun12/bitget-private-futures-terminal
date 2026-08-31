package com.local.bitget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniJson {
    private MiniJson() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("unexpected JSON content at " + parser.index);
        }
        return value;
    }

    static String stringify(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            appendString(out, entry.getKey());
            out.append(':');
            appendString(out, entry.getValue());
        }
        return out.append('}').toString();
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(index))) index++;
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) throw error("unexpected end of JSON");
            char c = text.charAt(index);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't') return readLiteral("true", Boolean.TRUE);
            if (c == 'f') return readLiteral("false", Boolean.FALSE);
            if (c == 'n') return readLiteral("null", null);
            if (c == '-' || Character.isDigit(c)) return readNumber();
            throw error("unexpected character '" + c + "'");
        }

        private Map<String, Object> readObject() {
            index++;
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') throw error("object key expected");
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private List<Object> readArray() {
            index++;
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return result;
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) return result;
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char c = text.charAt(index++);
                if (c == '"') return result.toString();
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (atEnd()) throw error("unfinished escape");
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (index + 4 > text.length()) throw error("bad unicode escape");
                        result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                        break;
                    default: throw error("bad escape");
                }
            }
            throw error("unterminated string");
        }

        private Object readNumber() {
            int start = index;
            if (text.charAt(index) == '-') index++;
            while (!atEnd() && Character.isDigit(text.charAt(index))) index++;
            if (!atEnd() && text.charAt(index) == '.') {
                index++;
                while (!atEnd() && Character.isDigit(text.charAt(index))) index++;
            }
            if (!atEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (!atEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) index++;
                while (!atEnd() && Character.isDigit(text.charAt(index))) index++;
            }
            String value = text.substring(start, index);
            try {
                if (value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
                    return Long.parseLong(value);
                }
                return Double.parseDouble(value);
            } catch (NumberFormatException error) {
                throw error("invalid number");
            }
        }

        private Object readLiteral(String expected, Object value) {
            if (!text.startsWith(expected, index)) throw error("invalid literal");
            index += expected.length();
            return value;
        }

        private boolean consume(char expected) {
            if (!atEnd() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("expected '" + expected + "'");
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + index);
        }
    }
}
