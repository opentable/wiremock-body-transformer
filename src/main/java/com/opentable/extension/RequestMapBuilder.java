package com.opentable.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestMapBuilder {

    private static final String XML_TEXT_ELEMENT_NAME = "value";
    private static final String URL_REGEX_PARAMETER_NAME = "urlRegex";

    private static ObjectMapper jsonMapper = initJsonMapper();
    private static ObjectMapper xmlMapper = initXmlMapper();

    private static ObjectMapper initJsonMapper() {
        return new ObjectMapper();
    }

    private static ObjectMapper initXmlMapper() {
        JacksonXmlModule configuration = new JacksonXmlModule();
        configuration.setXMLTextElementName(XML_TEXT_ELEMENT_NAME);
        return new XmlMapper(configuration);
    }

    private Map requestMap;

    public Map build() {
        if (requestMap == null) {
            requestMap = new HashMap();
        }

        return requestMap;
    }

    public RequestMapBuilder create(Request request) {
        if (requestMap == null) {
            requestMap = new HashMap();
        }

        String requestBody = request.getBodyAsString();

        try {
            requestMap = jsonMapper.readValue(requestBody, Map.class);
        } catch (IOException e) {
            try {
                requestMap = xmlMapper.readValue(requestBody, Map.class);
            } catch (IOException ex) {
                // Validate is a body has the 'name=value' parameters
                if (StringUtils.isNotEmpty(requestBody) && (requestBody.contains("&") || requestBody.contains("="))) {
                    requestMap = new HashMap();
                    String[] pairedValues = requestBody.split("&");
                    for (String pair : pairedValues) {
                        String[] values = pair.split("=");
                        requestMap.put(values[0], values.length > 1 ? decodeUTF8Value(values[1]) : "");
                    }
                } else if (request.getAbsoluteUrl().split("\\?").length == 2) { // Validate query string parameters
                    requestMap = new HashMap();
                    String absoluteUrl = request.getAbsoluteUrl();
                    String[] pairedValues = absoluteUrl.split("\\?")[1].split("&");
                    for (String pair : pairedValues) {
                        String[] values = pair.split("=");
                        requestMap.put(values[0], values.length > 1 ? decodeUTF8Value(values[1]) : "");
                    }
                } else {
                    System.err.println("[Body parse error] The body doesn't match any of 3 possible formats (JSON, XML, key=value).");
                }
            }
        }

        return this;
    }

    public RequestMapBuilder updateWithParameters(Request request, Parameters parameters) {
        if (requestMap == null) {
            requestMap = new HashMap();
        }

        if (parameters != null) {
            String urlRegex = parameters.getString(URL_REGEX_PARAMETER_NAME);

            if (urlRegex != null) {
                Pattern p = Pattern.compile(urlRegex);
                Matcher m = p.matcher(request.getUrl());

                // There may be more groups in the regex than the number of named capturing groups
                List<String> groups = getNamedGroupCandidates(urlRegex);

                if (m.matches() && groups.size() > 0 && groups.size() <= m.groupCount()) {
                    for (int i = 0; i < groups.size(); i++) {
                        requestMap.put(groups.get(i), m.group(i + 1));
                    }
                }
            }
        }

        return this;
    }

    private String decodeUTF8Value(String value) {

        String decodedValue = "";
        try {
            decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            System.err.println("[Body parse error] Can't decode one of the request parameter. It should be UTF-8 charset.");
        }

        return decodedValue;
    }

    private static List<String> getNamedGroupCandidates(String regex) {
        List<String> namedGroups = new ArrayList<>();

        Matcher m = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*?)>").matcher(regex);

        while (m.find()) {
            namedGroups.add(m.group(1));
        }

        return namedGroups;
    }

}
