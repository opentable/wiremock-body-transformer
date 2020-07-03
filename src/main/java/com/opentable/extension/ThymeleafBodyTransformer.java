/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opentable.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.BinaryFile;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.apache.commons.lang3.StringUtils;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ThymeleafBodyTransformer extends ResponseDefinitionTransformer {

    private static final String TRANSFORMER_NAME = "thymeleaf-body-transformer";
    private static final boolean APPLY_GLOBALLY = false;

    private static final Pattern interpolationPattern = Pattern.compile("\\$\\(.*?\\)");
    private static final Pattern randomIntegerPattern = Pattern.compile("!RandomInteger");

    private static ObjectMapper jsonMapper = initJsonMapper();
    private static ObjectMapper xmlMapper = initXmlMapper();

    private static ObjectMapper initJsonMapper() {
        return new ObjectMapper();
    }

    private static ObjectMapper initXmlMapper() {
        JacksonXmlModule configuration = new JacksonXmlModule();
        configuration.setXMLTextElementName("value");
        return new XmlMapper(configuration);
    }

    private TemplateEngine templateEngine = initThymeleaf();
    private static final Utils utils = new Utils();
    private static final WebhookClient http = new WebhookClient();

    private static TemplateEngine initThymeleaf() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new Java8TimeDialect());

        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode("TEXT");
        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine;
    }

    @Override
    public String getName() {
        return TRANSFORMER_NAME;
    }

    @Override
    public boolean applyGlobally() {
        return APPLY_GLOBALLY;
    }

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource fileSource, Parameters parameters) {
        if (hasEmptyResponseBody(responseDefinition)) {
            return responseDefinition;
        }

        Map<String, Object> object = null;
        String requestBody = request.getBodyAsString();

        // Trying to create map of request body or query string parameters
        try {
            object = jsonMapper.readValue(requestBody, Map.class);
        } catch (IOException e) {
            try {
                object = xmlMapper.readValue(requestBody, Map.class);
            } catch (IOException ex) {
                // Validate is a body has the 'name=value' parameters
                if (StringUtils.isNotEmpty(requestBody) && (requestBody.contains("&") || requestBody.contains("="))) {
                    object = new HashMap();
                    String[] pairedValues = requestBody.split("&");
                    for (String pair : pairedValues) {
                        String[] values = pair.split("=");
                        object.put(values[0], values.length > 1 ? decodeUTF8Value(values[1]) : "");
                    }
                } else if (request.getAbsoluteUrl().split("\\?").length == 2) { // Validate query string parameters
                    object = new HashMap();
                    String absoluteUrl = request.getAbsoluteUrl();
                    String[] pairedValues = absoluteUrl.split("\\?")[1].split("&");
                    for (String pair : pairedValues) {
                        String[] values = pair.split("=");
                        object.put(values[0], values.length > 1 ? decodeUTF8Value(values[1]) : "");
                    }
                } else {
                    System.err.println("[Body parse error] The body doesn't match any of 3 possible formats (JSON, XML, key=value).");
                }
            }
        }

        // Update the map with query parameters if any (if same names - replace)
        if (parameters != null) {
            String urlRegex = parameters.getString("urlRegex");

            if (urlRegex != null) {
                Pattern p = Pattern.compile(urlRegex);
                Matcher m = p.matcher(request.getUrl());

                // There may be more groups in the regex than the number of named capturing groups
                List<String> groups = getNamedGroupCandidates(urlRegex);

                if (m.matches() &&
                    groups.size() > 0 &&
                    groups.size() <= m.groupCount()) {

                    for (int i = 0; i < groups.size(); i++) {

                        if (object == null) {
                            object = new HashMap();
                        }

                        object.put(groups.get(i), m.group(i + 1));
                    }
                }
            }
        }

        if (object == null) {
            object = new HashMap();
        }
        Map<String, Object> finalObject = object;
        HttpHeaders headers = request.getHeaders();

        if (headers.size() > 0)
            headers
                .all()
                .forEach(httpHeader -> finalObject.put(httpHeader.key().replaceAll("-", ""), httpHeader.firstValue()));

        String responseBody = getResponseBody(responseDefinition, fileSource);

        // Create response by matching request map and response body parametrized values
        return ResponseDefinitionBuilder
            .like(responseDefinition).but()
            .withBodyFile(null)
            .withBody(transformResponse(object, responseBody))
            .build();
    }

    static Map<String, Object> session = new ConcurrentHashMap<>();
    static AtomicInteger counter = new AtomicInteger();

    private String transformResponse(Map<String, Object> requestObjects, String response) {

        Context context = new Context();
        context.setVariables(requestObjects);
        context.setVariable("session", session);
        context.setVariable("utils", utils);
        context.setVariable("counter", counter);
        context.setVariable("http", http);

        StringWriter stringWriter = new StringWriter();
        try {
            templateEngine.process(response, context, stringWriter);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        }

        return stringWriter.toString();
    }


    private CharSequence getValue(String group, Map requestObject) {
        if (randomIntegerPattern.matcher(group).find()) {
            return String.valueOf(new Random().nextInt(2147483647));
        }

        return getValueFromRequestObject(group, requestObject);
    }

    private CharSequence getValueFromRequestObject(String group, Map requestObject) {
        String fieldName = group.substring(2, group.length() - 1);
        String[] fieldNames = fieldName.split("\\.");
        Object tempObject = requestObject;
        for (String field : fieldNames) {
            if (tempObject instanceof Map) {
                tempObject = ((Map) tempObject).get(field);
            }
        }
        return String.valueOf(tempObject);
    }

    private boolean hasEmptyResponseBody(ResponseDefinition responseDefinition) {
        return responseDefinition.getBody() == null && responseDefinition.getBodyFileName() == null;
    }

    private String getResponseBody(ResponseDefinition responseDefinition, FileSource fileSource) {
        String body;
        if (responseDefinition.getBody() != null) {
            body = responseDefinition.getBody();
        } else {
            BinaryFile binaryFile = fileSource.getBinaryFileNamed(responseDefinition.getBodyFileName());
            body = new String(binaryFile.readContents(), StandardCharsets.UTF_8);
        }
        return body;
    }

    private static List<String> getNamedGroupCandidates(String regex) {
        List<String> namedGroups = new ArrayList<>();

        Matcher m = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*?)>").matcher(regex);

        while (m.find()) {
            namedGroups.add(m.group(1));
        }

        return namedGroups;
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


    static class Utils {
        final RsaJsonWebKey rsaJsonWebKey;
        static final Random random = new Random();

        Utils() {
            try {
                rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
                rsaJsonWebKey.setKeyId("k1");
            } catch (JoseException e) {
                e.printStackTrace();
                throw new IllegalStateException("Could not create rsaJsonWebKey");
            }
        }

        public String uuid() {
            return UUID.randomUUID().toString();
        }


        public List<Integer> list(int size) {
            return IntStream.range(0, size)
                .boxed()
                .collect(Collectors.toList());
        }

        public Random random() {
            return random;
        }

        public JwtClaims accessToken(String jwt) throws InvalidJwtException {
            return accessToken(jwt, false);
        }

        public JwtClaims accessToken(String jwt, boolean printClaims) throws InvalidJwtException {
            JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setSkipAllValidators()
                .setSkipSignatureVerification()
                .build();

            try {

                JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
                if (printClaims) {
                    jwtClaims.getClaimNames()
                        .forEach(name -> System.out.println("accessToken - claim " + name + ": " + jwtClaims.getClaimValue(name)));
                }
                return jwtClaims;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        public String jwt(String subject) throws JoseException {
            try {
                // Create the Claims, which will be the content of the JWT
                JwtClaims claims = new JwtClaims();
                claims.setExpirationTimeMinutesInTheFuture(10); // time when the token will expire (10 minutes from now)
                claims.setGeneratedJwtId(); // a unique identifier for the token
                claims.setIssuedAtToNow();  // when the token was issued/created (now)
                claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
                claims.setSubject(subject); // the subject/principal is whom the token is about

                // A JWT is a JWS and/or a JWE with JSON claims as the payload.
                // In this example it is a JWS so we create a JsonWebSignature object.
                JsonWebSignature jws = new JsonWebSignature();

                // The JWT is signed using the private key
                jws.setKey(rsaJsonWebKey.getPrivateKey());
                // The payload of the JWS is JSON content of the JWT Claims
                jws.setPayload(claims.toJson());
                // Set the signature algorithm on the JWT/JWS that will integrity protect the claims
                jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

                return jws.getCompactSerialization();

            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

    }


}
