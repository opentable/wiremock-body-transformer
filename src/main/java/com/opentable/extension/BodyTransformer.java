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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.BinaryFile;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BodyTransformer extends ResponseDefinitionTransformer {

    private static final String TRANSFORMER_NAME = "body-transformer";
    private static final boolean APPLY_GLOBALLY = false;

    private static final Pattern interpolationPattern = Pattern.compile("\\$\\(.*?\\)");
    private static final Pattern randomIntegerPattern = Pattern.compile("!RandomInteger");

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

        Map requestMap = new RequestMapBuilder()
            .create(request)
            .updateWithParameters(request, parameters)
            .build();

        String responseBody = getResponseBody(responseDefinition, fileSource);

        // Create response by matching request map and response body parametrized values
        return ResponseDefinitionBuilder
            .like(responseDefinition).but()
            .withBodyFile(null)
            .withBody(transformResponse(requestMap, responseBody))
            .build();
    }

    private String transformResponse(Map requestObject, String response) {
        String modifiedResponse = response;

        Matcher matcher = interpolationPattern.matcher(response);
        while (matcher.find()) {
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

}
