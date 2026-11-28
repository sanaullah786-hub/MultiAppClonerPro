package com.multiappcloner.pro.diag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes personal data and credentials from text before it is stored or copied into a report. */
public final class Redactor {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(bearer\\s+|basic\\s+)?[^\\s,;]+");
    private static final Pattern KEY_VALUE = Pattern.compile(
        "(?i)\\b(password|passwd|pwd|pass|token|access_token|refresh_token|id_token|secret|client_secret|api[_-]?key|apikey|auth|session(?:id)?|cookie|sid|otp|pin)\\b(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&\"']+)");
    private static final Pattern URL_QUERY = Pattern.compile("(?i)([?&](?:token|key|code|password|auth|access_token|sig|signature)=)[^&\\s]+");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w.:])\\+?\\d(?:[ \\-.()]?\\d){8,14}(?![\\w.:])");
    private static final Pattern LONG_SECRET = Pattern.compile("(?<![A-Za-z0-9_\\-./~=])[A-Za-z0-9_\\-]{32,}(?![A-Za-z0-9_\\-])");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*");
    private static final Pattern ANDROID_ID = Pattern.compile("(?i)(android_?id|imei|serial|advertising_?id|gaid)(\\s*[=:]\\s*)[A-Za-z0-9\\-]{6,}");

    public static String redact(String s) {
        if (s == null || s.length() == 0) return s;
        s = JWT.matcher(s).replaceAll("[jwt]");
        s = AUTH_HEADER.matcher(s).replaceAll("$1[redacted]");
        s = KEY_VALUE.matcher(s).replaceAll("$1$2[redacted]");
        s = URL_QUERY.matcher(s).replaceAll("$1[redacted]");
        s = ANDROID_ID.matcher(s).replaceAll("$1$2[redacted]");
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = replacePhones(s);
        s = replaceLongSecrets(s);
        return s;
    }

    /** Phone-like numbers, but not timestamps / versions / hex addresses (needs 9+ digits). */
    private static String replacePhones(String s) {
        Matcher m = PHONE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = m.group();
            int digits = 0;
            for (int i = 0; i < g.length(); i++) if (Character.isDigit(g.charAt(i))) digits++;
            boolean hasSeparatorOrPlus = g.indexOf('+') >= 0 || g.indexOf(' ') >= 0 || g.indexOf('-') >= 0 || g.indexOf('(') >= 0;
            m.appendReplacement(sb, Matcher.quoteReplacement(digits >= 9 && digits <= 15 && hasSeparatorOrPlus ? "[phone]" : g));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Long random-looking strings (keys, tokens). Class names and paths contain dots/slashes, so they do not match. */
    private static String replaceLongSecrets(String s) {
        Matcher m = LONG_SECRET.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = m.group();
            boolean digit = false, upper = false, lower = false;
            for (int i = 0; i < g.length(); i++) {
                char c = g.charAt(i);
                if (Character.isDigit(c)) digit = true;
                else if (Character.isUpperCase(c)) upper = true;
                else if (Character.isLowerCase(c)) lower = true;
            }
            int kinds = (digit ? 1 : 0) + (upper ? 1 : 0) + (lower ? 1 : 0);
            m.appendReplacement(sb, Matcher.quoteReplacement(kinds >= 2 && digit ? "[secret]" : g));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
