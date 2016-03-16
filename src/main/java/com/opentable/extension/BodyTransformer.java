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
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BodyTransformer extends ResponseTransformer {

    private final Pattern interpolationPattern = Pattern.compile("\\$\\(.*?\\)");
    private final Pattern randomIntegerPattern = Pattern.compile("!RandomInteger");
    private ObjectMapper jsonMapper = new ObjectMapper();
    private ObjectMapper xmlMapper;

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files) {
        Map object = null;
        try {
            object = jsonMapper.readValue(request.getBodyAsString(), Map.class);
        } catch (IOException e) {
            try
            {
				JacksonXmlModule configuration = new JacksonXmlModule();
				//Set the default value name for xml elements like <user type="String">Dmytro</user>
				configuration.setXMLTextElementName("value");
				xmlMapper = new XmlMapper(configuration);
                object = xmlMapper.readValue(request.getBodyAsString(), Map.class);
            }
            catch (IOException ex)
            {
                //Validate is a body has the 'name=value' parameters
                String body = request.getBodyAsString();
                if(StringUtils.isNotEmpty(body) && (body.contains("&") || body.contains("=")))
                {
                    object = new HashMap();
                    String [] pairedValues = request.getBodyAsString().split("&");
                    for(String pair: pairedValues)
                    {
                        String[] values = pair.split("=");
                        object.put(values[0], values[1]);
                    }
                } else {
                    System.err.println("[Body parse error] The body doesn't match any of 3 possible formats (JSON, XML, key=value).");
                }
            }
        }

        if (hasEmptyBody(responseDefinition)) {
            return responseDefinition;
        }

        String body = getBody(responseDefinition, files);

        return ResponseDefinitionBuilder
                .like(responseDefinition).but()
                .withBodyFile(null)
                .withBody(transformResponse(object, body))
                .build();
    }

    public String name() {
        return "body-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    private String transformResponse(Map requestObject, String response) {
        String modifiedResponse = response;

        Matcher matcher = interpolationPattern.matcher(response);
        while (matcher.find())
        {
            String group = matcher.group();
            modifiedResponse = modifiedResponse.replace(group, getValue(group, requestObject));

        }

        return modifiedResponse;
    }


    private CharSequence getValue(String group, Map requestObject) {
        if (randomIntegerPattern.matcher(group).find()) {
            return String.valueOf(new Random().nextInt(2147483647));
        }

        return getValueFromRequestObject(group, requestObject);
    }

    private CharSequence getValueFromRequestObject(String group, Map requestObject) {
        String fieldName = group.substring(2,group.length() -1);
        String[] fieldNames = fieldName.split("\\.");
        Object tempObject = requestObject;
        for (String field : fieldNames) {
            if (tempObject instanceof Map) {
                tempObject = ((Map) tempObject).get(field);
            }
        }
        return String.valueOf(tempObject);
    }

    private boolean hasEmptyBody(ResponseDefinition responseDefinition) {
        return responseDefinition.getBody() == null && responseDefinition.getBodyFileName() == null;
    }

    private String getBody(ResponseDefinition responseDefinition, FileSource files) {
        String body;
        if (responseDefinition.getBody() != null) {
            body = responseDefinition.getBody();
        } else {
            BinaryFile binaryFile = files.getBinaryFileNamed(responseDefinition.getBodyFileName());
            body = new String(binaryFile.readContents(), StandardCharsets.UTF_8);
        }
        return body;
    }
}

