package com.fahim.cloudide;

import android.util.Xml;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** A problem (sev 1 error, 2 warning) or a search hit (sev 3). */
final class Diag {
    final String file, msg;
    final int line, col, sev;

    Diag(String file, int line, int col, String msg, int sev) {
        this.file = file;
        this.line = line;
        this.col = col;
        this.msg = msg;
        this.sev = sev;
    }
}

/** Instant on-device checks: XML and JSON well-formedness. */
final class Check {
    static List<Diag> run(String rel, String text) {
        List<Diag> out = new ArrayList<>();
        String n = rel.toLowerCase(Locale.ROOT);
        try {
            if (n.endsWith(".xml")) {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(new StringReader(text));
                while (p.next() != XmlPullParser.END_DOCUMENT) { }
            } else if (n.endsWith(".json")) {
                JSONTokener t = new JSONTokener(text);
                t.nextValue();
                if (t.nextClean() != 0) throw t.syntaxError("Unexpected trailing content");
            }
        } catch (XmlPullParserException e) {
            String m = String.valueOf(e.getMessage());
            int i = m.indexOf(" (position");
            if (i > 0) m = m.substring(0, i);
            out.add(new Diag(rel, Math.max(1, e.getLineNumber()), Math.max(0, e.getColumnNumber()), m, 1));
        } catch (JSONException e) {
            String m = String.valueOf(e.getMessage());
            Matcher mt = Pattern.compile("at character (\\d+)").matcher(m);
            int line = 1;
            if (mt.find()) {
                int off = Integer.parseInt(mt.group(1));
                for (int i = 0; i < off && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
            }
            int c = m.indexOf(" of ");
            if (c > 0) m = m.substring(0, c);
            out.add(new Diag(rel, line, 0, m, 1));
        } catch (Exception ignored) {
        }
        return out;
    }
}

/** Turns a GitHub Actions log into a list of file/line problems. */
final class LogParser {
    static final Pattern LOC = Pattern.compile("/home/runner/work/[^/]+/[^/]+/([^\\s:]+):(\\d+)(?::(\\d+))?");
    static final String TS = "^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z\\s?";

    static List<Diag> parse(String log) {
        List<Diag> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = log.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].replaceFirst(TS, "").replace("\r", "");
            Matcher m = LOC.matcher(s);
            if (m.find()) {
                String low = s.toLowerCase(Locale.ROOT);
                int sev = 0;
                if (s.startsWith("e: ") || low.contains("error")) sev = 1;
                else if (s.startsWith("w: ") || low.contains("warning")) sev = 2;
                if (sev == 0) continue;
                String msg = s.substring(m.end()).replaceFirst("^[: ]*(?:AAPT: )?(?:error|warning)?:?\\s*", "").trim();
                if (msg.isEmpty() && i + 1 < lines.length) msg = lines[i + 1].replaceFirst(TS, "").trim();
                String key = m.group(1) + ":" + m.group(2) + ":" + msg;
                if (seen.add(key))
                    out.add(new Diag(m.group(1), Integer.parseInt(m.group(2)), m.group(3) == null ? 0 : Integer.parseInt(m.group(3)), msg, sev));
            } else if (s.contains("What went wrong:") && i + 1 < lines.length) {
                String msg = lines[i + 1].replaceFirst(TS, "").trim();
                if (seen.add("G:" + msg)) out.add(new Diag("", 0, 0, msg, 1));
            }
        }
        return out;
    }
}

/** Code formatting: re-indentation for brace languages, XML, and JSON pretty-print. */
final class Fmt {
    static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    static String format(Lang l, String t, int tab) throws Exception {
        if (l.id.equals("json")) {
            Object v = new JSONTokener(t).nextValue();
            if (v instanceof JSONObject) return ((JSONObject) v).toString(tab) + "\n";
            if (v instanceof JSONArray) return ((JSONArray) v).toString(tab) + "\n";
            return t;
        }
        if (l.id.equals("xml")) return xml(t, tab);
        if (l.brace) return braces(t, tab);
        throw new Exception("Format works for Java, Kotlin, Gradle, JS, C, CSS, JSON and XML");
    }

    static String braces(String t, int tab) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean inBlock = false;
        String[] lines = t.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String s = lines[li].trim();
            if (s.isEmpty()) {
                if (li < lines.length - 1) out.append('\n');
                continue;
            }
            int net = 0, lead = 0;
            boolean leading = true, inStr = false, startsInBlock = inBlock;
            char q = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (inBlock) {
                    if (c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '/') { inBlock = false; i++; }
                    continue;
                }
                if (inStr) {
                    if (c == '\\') i++;
                    else if (c == q) inStr = false;
                    continue;
                }
                if (c == '/' && i + 1 < s.length()) {
                    char n = s.charAt(i + 1);
                    if (n == '/') break;
                    if (n == '*') { inBlock = true; i++; leading = false; continue; }
                }
                if (c == '"' || c == '\'') { inStr = true; q = c; leading = false; continue; }
                if (c == '{' || c == '(' || c == '[') { net++; leading = false; }
                else if (c == '}' || c == ')' || c == ']') { net--; if (leading) lead++; }
                else if (!Character.isWhitespace(c)) leading = false;
            }
            int ind = Math.max(0, depth - lead);
            out.append(spaces(ind * tab)).append(startsInBlock && s.startsWith("*") ? " " : "").append(s);
            if (li < lines.length - 1) out.append('\n');
            depth = Math.max(0, depth + net);
        }
        return out.toString();
    }

    static String xml(String t, int tab) {
        StringBuilder sb = new StringBuilder();
        int depth = 0, kind = 0;
        boolean inTag = false, inCom = false;
        String[] lines = t.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String s = lines[li].trim();
            if (s.isEmpty()) {
                if (li < lines.length - 1) sb.append('\n');
                continue;
            }
            int ind;
            if (inCom) ind = depth;
            else if (inTag) ind = depth + 1;
            else if (s.startsWith("</")) ind = Math.max(0, depth - 1);
            else ind = depth;
            sb.append(spaces(ind * tab)).append(s);
            if (li < lines.length - 1) sb.append('\n');
            for (int i = 0; i < s.length(); i++) {
                if (inCom) {
                    int e = s.indexOf("-->", i);
                    if (e < 0) break;
                    i = e + 2;
                    inCom = false;
                    continue;
                }
                char c = s.charAt(i);
                if (!inTag) {
                    if (c != '<') continue;
                    if (s.startsWith("<!--", i)) { inCom = true; i += 3; continue; }
                    inTag = true;
                    kind = s.startsWith("</", i) ? 1 : (s.startsWith("<?", i) || s.startsWith("<!", i)) ? 2 : 0;
                } else if (c == '"' || c == '\'') {
                    int e = s.indexOf(c, i + 1);
                    if (e < 0) break;
                    i = e;
                } else if (c == '>') {
                    boolean self = i > 0 && s.charAt(i - 1) == '/';
                    if (kind == 1) depth = Math.max(0, depth - 1);
                    else if (kind == 0 && !self) depth++;
                    inTag = false;
                }
            }
        }
        return sb.toString();
    }
}

/** Code snippets offered by autocomplete (trigger, body; $0 marks the caret). */
final class Snippets {
    static String[][] get(String id) {
        switch (id) {
            case "java":
                return new String[][]{
                        {"sout", "System.out.println($0);"},
                        {"fori", "for (int i = 0; i < $0; i++) {\n    \n}"},
                        {"psvm", "public static void main(String[] args) {\n    $0\n}"},
                        {"onCreate", "@Override\nprotected void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n    $0\n}"},
                        {"trycatch", "try {\n    $0\n} catch (Exception e) {\n    e.printStackTrace();\n}"},
                        {"toast", "Toast.makeText(this, \"$0\", Toast.LENGTH_SHORT).show();"},
                        {"logd", "Log.d(\"TAG\", \"$0\");"}};
            case "kotlin":
                return new String[][]{
                        {"main", "fun main() {\n    $0\n}"},
                        {"fori", "for (i in 0 until $0) {\n}"},
                        {"onCreate", "override fun onCreate(savedInstanceState: Bundle?) {\n    super.onCreate(savedInstanceState)\n    $0\n}"},
                        {"toast", "Toast.makeText(this, \"$0\", Toast.LENGTH_SHORT).show()"},
                        {"logd", "Log.d(\"TAG\", \"$0\")"}};
            case "xml":
                return new String[][]{
                        {"linear", "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:orientation=\"vertical\">\n    $0\n</LinearLayout>"},
                        {"textview", "<TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"$0\" />"},
                        {"button", "<Button\n    android:id=\"@+id/$0\"\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"Button\" />"},
                        {"edittext", "<EditText\n    android:id=\"@+id/$0\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"Hint\" />"}};
            default:
                return new String[0][];
        }
    }
}
