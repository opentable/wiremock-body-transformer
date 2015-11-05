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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.equalTo;

public class BodyTransformerTest {

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8080).extensions(new BodyTransformer()));

	@Test
	public void replaceVariableHolder() throws Exception {
		wireMockRule.stubFor(post(urlEqualTo("/get/this"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("content-type", "application/json")
				.withBody("{\"var\":$(var), \"got\":\"it\"}")
				.withTransform("body-transformer")));


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

	@Test
	public void replaceNestedVariables() throws Exception {
		wireMockRule.stubFor(post(urlEqualTo("/get/this"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("content-type", "application/json")
				.withBody("{\"var\":$(var), \"got\":\"it\", \"nested_attr\": \"$(nested.attr)\"}")
				.withTransform("body-transformer")));


		given()
			.contentType("application/json")
			.body("{\"var\":1111, \"nested\": {\"attr\": \"found\"}}}")
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
	public void nullVariableNotFound() throws Exception {
		wireMockRule.stubFor(post(urlEqualTo("/get/this"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("content-type", "application/json")
				.withBody("{\"var\":$(var)}")
				.withTransform("body-transformer")));

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
						.withTransform("body-transformer")));


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
}
