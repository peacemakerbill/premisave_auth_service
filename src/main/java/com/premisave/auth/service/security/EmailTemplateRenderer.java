package com.premisave.auth.service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the HTML version of an email from templates in
 * resources/templates/email. Syntax, in the same spirit as the existing templates:
 *
 *   {{name}}                 value, HTML-escaped in the HTML version
 *   {{{name}}}               value inserted as is (only for fragments built here, never user input)
 *   {{#name}} ... {{/name}}  block shown only when the value is not empty
 *
 * Every value that can come from a client (User-Agent, names, addresses) goes through
 * {{name}} and is escaped, so a spoofed header cannot inject markup into an email.
 */
@Component
public class EmailTemplateRenderer {

    public record Row(String label, String value) {
    }

    public record Rendered(String html) {
    }

    private static final Pattern CONDITIONAL = Pattern.compile("\\{\\{#(\\w+)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\{(\\w+)}}}|\\{\\{(\\w+)}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Value("${email.support:thepeacemakerske@gmail.com}")
    private String supportEmail;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public Rendered render(String template, Map<String, String> variables, List<Row> rows) {
        Map<String, String> vars = new HashMap<>();
        vars.put("appName", "Premisave");
        vars.put("supportEmail", supportEmail);
        vars.put("frontendUrl", frontendUrl);
        vars.put("year", String.valueOf(Year.now().getValue()));
        vars.putAll(variables);

        Map<String, String> htmlVars = new HashMap<>(vars);
        htmlVars.put("detailsHtml", detailsHtml(rows));
        String content = apply(load("templates/email/" + template + ".html"), htmlVars, true);

        Map<String, String> layoutVars = new HashMap<>(htmlVars);
        layoutVars.put("body", content);
        String html = apply(load("templates/email/layout.html"), layoutVars, true);

        return new Rendered(html);
    }

    static String apply(String template, Map<String, String> vars, boolean html) {
        // 1. Conditional blocks are resolved on the template itself, before any value is inserted.
        Matcher cond = CONDITIONAL.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (cond.find()) {
            String value = vars.get(cond.group(1));
            String replacement = (value == null || value.isBlank()) ? "" : cond.group(2);
            cond.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        cond.appendTail(sb);

        // 2. All placeholders in one pass, so inserted values are never scanned for placeholders again.
        Matcher token = TOKEN.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (token.find()) {
            boolean rawToken = token.group(1) != null;
            String name = rawToken ? token.group(1) : token.group(2);
            String value = vars.getOrDefault(name, "");
            String replacement;
            if (html) {
                replacement = rawToken ? value : HtmlUtils.htmlEscape(value, "UTF-8");
            } else {
                replacement = stripControl(value);
            }
            token.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        token.appendTail(out);
        return out.toString();
    }

    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    /** The key and value table in security emails: a soft rounded card, one row per detail. */
    private static String detailsHtml(List<Row> rows) {
        if (rows == null) {
            return "";
        }
        List<Row> shown = rows.stream().filter(row -> row.value() != null && !row.value().isBlank()).toList();
        if (shown.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(
                "<table role=\"presentation\" class=\"soft\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                        + "style=\"margin:22px 0;background-color:#F6F4FD;border:1px solid #E6E2F7;border-radius:14px;\">");
        for (int i = 0; i < shown.size(); i++) {
            Row row = shown.get(i);
            String border = i == shown.size() - 1 ? "none" : "1px solid #E6E2F7";
            out.append("<tr><td class=\"rk\" style=\"padding:13px 18px;width:36%;vertical-align:top;font-family:").append(FONT)
                    .append(";font-size:11.5px;letter-spacing:0.6px;text-transform:uppercase;color:#7A77A3;border-bottom:")
                    .append(border).append(";\">")
                    .append(HtmlUtils.htmlEscape(row.label(), "UTF-8"))
                    .append("</td><td class=\"rv\" style=\"padding:13px 18px;vertical-align:top;font-family:").append(FONT)
                    .append(";font-size:14px;line-height:1.5;font-weight:600;color:#15123A;border-bottom:")
                    .append(border).append(";word-break:break-word;\">")
                    .append(HtmlUtils.htmlEscape(row.value(), "UTF-8"))
                    .append("</td></tr>");
        }
        return out.append("</table>").toString();
    }

    private static String stripControl(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
    }

    private String load(String path) {
        return cache.computeIfAbsent(path, p -> {
            try (InputStream in = new ClassPathResource(p).getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Email template missing: " + p, e);
            }
        });
    }
}