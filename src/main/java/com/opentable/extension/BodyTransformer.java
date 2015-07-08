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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BodyTransformer extends ResponseTransformer {

    private final Pattern pattern = Pattern.compile("\\$\\(.*?\\)");
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files) {
        Map object = null;
        try {
            object = mapper.readValue(request.getBodyAsString(), Map.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (responseDefinition.getBody() == null) {
            return responseDefinition;
        }

        return ResponseDefinitionBuilder
                .like(responseDefinition).but()
                .withBody(transformResponse(object, responseDefinition.getBody()))
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

        Matcher matcher = pattern.matcher(response);
        while (matcher.find())
        {
            String group = matcher.group();
            modifiedResponse = modifiedResponse.replace(group, getValueFromRequestObject(group, requestObject));

        }

        return modifiedResponse;
    }

    private CharSequence getValueFromRequestObject(String group, Map requestObject) {
        String fieldName = group.substring(2,group.length() -1);
        String[] fieldNames = fieldName.split("\\.");
        Object tempObject = requestObject;
        for (int i = 0; i < fieldNames.length; i++) {
            String field = fieldNames[i];
            if (tempObject instanceof Map) {
                tempObject = ((Map) tempObject).get(field);
            }
        }
        return String.valueOf(tempObject);
    }
}

