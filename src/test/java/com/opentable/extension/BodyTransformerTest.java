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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

public class BodyTransformerTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8080).extensions(new BodyTransformer()));

    @Test
    public void replaceVariableHolder() throws Exception {
        testTopLevelField("{\"var\":1111}");
    }
    @Test
    public void replaceVariableHolderForXml() throws Exception {
        testTopLevelField("<root><var>1111</var></root>");
    }

    private void testTopLevelField(String requestBody)
    {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("{\"var\":$(var), \"got\":\"it\"}")
                .withTransformers("body-transformer")));
        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("var", equalTo(1111))
            .body("got", equalTo("it"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void replaceNestedVariables() throws Exception {
        final String requestBody = "{\"var\":1111, \"nested\": {\"attr\": \"found\"}}}";
        testNestedField(requestBody);
    }
    @Test
    public void replaceNestedVariablesForXml() throws Exception {
        final String requestBody = "<root><var>1111</var><nested><attr>found</attr></nested></root>";
        testNestedField(requestBody);
    }

    @Test
    public void replaceVariableHolderFromKeyValueRequest() throws Exception {
        final String requestBody = "utf8=%E2%9C%93&auth_cv_result=M&req_locale=en-us&decision_case_priority=3";
        testKeyValueBodyRequest(requestBody);

    }

	@Test
	public void replaceVariableHolderFromKeyRequest() throws Exception {
		final String requestBody = "EmptyKey=";
		testKeyBodyRequest(requestBody);
	}

    private void testKeyValueBodyRequest(String body) {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("{\"auth_cv_result\":\"$(auth_cv_result)\"}")
                .withTransformers("body-transformer")));
        given()
            .contentType("application/x-www-form-urlencoded")
            .body(body)
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("auth_cv_result", equalTo("M"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));

    }
	private void testKeyBodyRequest(String body) {
		wireMockRule.stubFor(post(urlEqualTo("/get/this"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("content-type", "application/json")
				.withBody("{\"EmptyKey\":\"\"}")
				.withTransformers("body-transformer")));
		given()
			.contentType("application/x-www-form-urlencoded")
			.body(body)
		.when()
			.post("/get/this")
		.then()
			.statusCode(200)
			.body("EmptyKey", equalTo(""));

		wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));

	}

	private void testNestedField(String requestBody)
	{
		wireMockRule.stubFor(post(urlEqualTo("/get/this"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("content-type", "application/json")
				.withBody("{\"var\":$(var), \"got\":\"it\", \"nested_attr\": \"$(nested.attr)\"}")
				.withTransformers("body-transformer")));
		given()
			.contentType("application/json")
			.body(requestBody)
		.when()
			.post("/get/this")
		.then()
			.statusCode(200)
			.body("var", equalTo(1111))
			.body("got", equalTo("it"))
			.body("nested_attr", equalTo("found"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void replaceElementAttributesForXml() throws Exception {
        final String requestBody = "<root><var type=\"number\">1111</var><nested><attr type=\"string\">found</attr></nested></root>";
        testXMLFieldAttributes(requestBody);
    }

    private void testXMLFieldAttributes(String requestBody) {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("{\"var_type\": \"$(var.type)\", \"var_value\": $(var.value), " +
                        "\"nested_attr_type\":\"$(nested.attr.type)\", \"nested_attr_value\":\"$(nested.attr.value)\"}")
                .withTransformers("body-transformer")));

        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("var_type", equalTo("number"))
            .body("var_value", equalTo(1111))
            .body("nested_attr_type", equalTo("string"))
            .body("nested_attr_value", equalTo("found"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void nullVariableNotFound() throws Exception {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("{\"var\":$(var)}")
                .withTransformers("body-transformer")));

        given()
            .contentType("application/json")
            .body("{\"something\":\"different\"}")
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("var", equalTo(null));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void doesNotApplyGlobally() throws Exception {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBody("{\"var\":$(var)}")));

        given()
            .contentType("application/json")
            .body("{\"var\":\"foo\"}")
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body(equalTo("{\"var\":$(var)}"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void injectRandomInteger() {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("content-type", "application/json")
                        .withBody("{\"randomNumber\":$(!RandomInteger), \"got\":\"it\"}")
                        .withTransformers("body-transformer")));

        given()
            .contentType("application/json")
            .body("{\"var\":1111}")
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("randomNumber", isA(Integer.class))
            .body("got", equalTo("it"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }

    @Test
    public void withBodyFileName() throws Exception {
        wireMockRule.stubFor(post(urlEqualTo("/get/this"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("content-type", "application/json")
                .withBodyFile("body.json")
                .withTransformers("body-transformer")));

        given()
            .contentType("application/json")
            .body("{\"var\":1111}")
        .when()
            .post("/get/this")
        .then()
            .statusCode(200)
            .body("var", equalTo(1111))
            .body("got", equalTo("it"));

        wireMockRule.verify(postRequestedFor(urlEqualTo("/get/this")));
    }
}
