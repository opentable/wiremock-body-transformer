package com.opentable.extension;

import org.junit.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
public class ThymeleafTest {

    @Test
    public void testRenderFile() {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(templateResolver);
        Context context = new Context();
        context.setVariable("name", "World");
        StringWriter stringWriter = new StringWriter();
        templateEngine.process("test.html", context, stringWriter);
        assertEquals("<html lang=\"en\">\n" +
            "<body>\n" +
            "    <span>Hello, World</span>\n" +
            "</body>\n" +
            "</html>\n", stringWriter.toString());
    }

    @Test
    public void testRenderString() {
        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(templateResolver);
        Context context = new Context();
        context.setVariable("name", "World");
        StringWriter stringWriter = new StringWriter();

        String INPUT = "<html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\">\n" +
            "<body>\n" +
            "    <span th:text=\"'Hello, ' + ${name}\"></span>\n" +
            "</body>\n" +
            "</html>";
        templateEngine.process(INPUT, context, stringWriter);

        String OUTPUT = "<html lang=\"en\">\n" +
            "<body>\n" +
            "    <span>Hello, World</span>\n" +
            "</body>\n" +
            "</html>";
        assertEquals(OUTPUT, stringWriter.toString());
    }

    @Test
    public void testRenderStringFromTextPlain() {
        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(templateResolver);
        Context context = new Context();
        context.setVariable("name", "World");
        StringWriter stringWriter = new StringWriter();

        String INPUT = "aaaa [(${name})]";
        templateEngine.process(INPUT, context, stringWriter);

        String OUTPUT = "aaaa World";
        assertEquals(OUTPUT, stringWriter.toString());
    }


    static Map<String, Object> session = new HashMap<>();
    @Test
    public void testReuseData() {
        TemplateEngine templateEngine = new TemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateEngine.setTemplateResolver(templateResolver);
        Context context = new Context();
        context.setVariable("name", "junit");
        context.setVariable("session", session);
        StringWriter stringWriter = new StringWriter();

        String INPUT = "aaaa [(${name})] [(${session.put('zzz', name)})]";
        templateEngine.process(INPUT, context, stringWriter);

        context.clearVariables();
        context.setVariable("session", session);
        stringWriter = new StringWriter();
        INPUT = "aaaa [(${session.get('zzz')})]";
        templateEngine.process(INPUT, context, stringWriter);

        String OUTPUT = "aaaa junit";
        assertEquals(OUTPUT, stringWriter.toString());
    }
}
