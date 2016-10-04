##Wiremock Body Transformer
Wiremock Body Transformer is a [Wiremock](http://wiremock.org/) extension that can take the request body and interpolates the variable into the response.
Built on the extensions platform of Wiremock, it allows your wiremock response to be dynamic and dependent on the request for a smarter testing process.

###Installation
```
<dependency>
		<groupId>com.opentable</groupId>
		<artifactId>wiremock-body-transformer</artifactId>
		<version>1.1.1</version>
</dependency>
```

###How It Works
The body transformer supports both __JSON__ and __XML__ formats.

The response body stub acts as a template where the variables come from the request json/xml body similar to string interpolation.
The variable fields are injected via the `$(foo)` notation, where 'foo' is a json field in the request body.
```
{
    "foo": "bar"
}
```

XML elements might have additional attributes and can be reached by using property name `$(foo.type)` and `$(foo.value)`.
Keep in mind that the root element (in this case `<root></root>`) doesn't present in the resulting map.
```
<root><foo type="string">bar</foo></root>
```

###Nested Fields
You can specify nested fields via dot notations.
For example:
```
{
	"foo": {
		"bar": "opentable"
	}
}
```
The value `opentable` is referenced via `$(foo.bar)` in your response body.

```
<root><foo><bar type="string">opentable</bar></foo></root>
```
The value `opentable` is referenced via `$(foo.bar.value)` and type 'string' via `$(foo.bar.type)` in your response body.

### URL Pattern Matching
You can use this feature to extract query parameters or parts of the URL. Pass in additional transformer parameters to do url pattern matching. 
Pass a regex parameter named `urlRegex` to match the url and extract relevant groups. Pass in the comma-delimited group names as a `groupNames` parameter.

##### Example
Fetching a url like: /params/slash1/10/slash2/20?one=value1&two=value2&three=value3 using the following code in Java:
```
    wireMockRule.stubFor(get(urlMatching("/params/slash1/[0-9]+?/slash2/[0-9]+?.*"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"slash1\":\"$(slash1Var)\", \"slash2\":\"$(slash2Var)\", \"one\":\"$(oneVar)\", \"two\":\"$(twoVar)\", \"three\":\"$(threeVar)\"}")
            .withTransformers("body-transformer")
        .withTransformerParameter("urlRegex", "/params/slash1/(.*?)/slash2/(.*?)\\?one=(.*?)\\&two=(.*?)\\&three=(.*?)")
        .withTransformerParameter("groupNames", "slash1Var,slash2Var,oneVar,twoVar,threeVar")));
```
returns a body that looks like:
```
{
    "slash1": "10",
    "slash2": "20",
    "one": "value1",
    "two": "value2",
    "three": "value3"
}
```

###Usage

####As part of [Unit Testing with Wiremock](http://wiremock.org/extending-wiremock.html): 

Instantiating the Wiremock server with the `BodyTransformer` instance.
```
new WireMockServer(wireMockConfig().extensions(new BodyTransformer()));
```
Specifying the transformer when stubbing.
```
wireMock.stubFor(get(urlEqualTo("/local-transform")).willReturn(aResponse()
        .withStatus(200)
        .withBody("{\"name\": \"$(var)\"}")
        .withTransformers("body-transformer")));
```

Using file to specify body response. This will read the specified file and return the content as json body.
```
wireMockRule.stubFor(post(urlEqualTo("/get/this"))
	.willReturn(aResponse()
		.withStatus(200)
		.withHeader("content-type", "application/json")
		.withBodyFile("body.json")
		.withTransformers("body-transformer")));
```

####As part of the [Wiremock standalone process](http://wiremock.org/running-standalone.html#running-standalone):
[\[Download the body transformer extension jar file here.\]](https://github.com/opentable/wiremock-body-transformer/releases/download/wiremock-body-transformer-1.1.1/wiremock-body-transformer-1.1.1.jar)

[\[Download the Wiremock standalone jar here.\]](http://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/2.1.12/wiremock-standalone-2.1.12.jar)

Including the extension upon start on the command line via the `--extensions` flag. Note that the BodyTransformer jar is added to the classpath.

For Unix:
```
java -cp "wiremock-body-transformer-1.1.1.jar:wiremock-1.57-standalone.jar" com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose --extensions com.opentable.extension.BodyTransformer
```

For Windows:
```
java -cp "wiremock-body-transformer-1.1.1.jar;wiremock-1.57-standalone.jar" com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose --extensions com.opentable.extension.BodyTransformer
```

Add the transformer into the specific stub via the "body-transformer" name.
```
{
    "request": {
        "method": "GET",
        "url": "/local-transform"
    },
    "response": {
        "status": 200,
        "body": "{\"name\": \"$(var)\"}",
        "transformers": ["body-transformer"]
    }
}
```

###Example
For the following stub:
```
{
	"request": {
		"method": "POST",
		"urlPath": "/transform",
		"bodyPatterns": [
			{
				"matchesJsonPath": "$.name"
			}
		]
	},
	"response": {
		"status": 200,
		"body": "{\"responseName\": \"$(name)\"}",
		"headers": {
			"Content-Type": "application/json"
		},
		"transformers": ["body-transformer"]
	}
}
```
A request body of :
```
{
    "name": "Joe"
}
```
would return a response body of:
```
{
    "responseName": "Joe"
}
```


###Additional Features

####Random Integer Generator
With the pattern `$(!RandomInteger)` inside the stub response body, a random positive integer will be interpolated in that position.

#####Example
```
{
	"request": {
		"method": "POST",
		"urlPath": "/transform",
	},
	"response": {
		"status": 200,
		"body": "{\"randomInteger\": \"$(!RandomInteger)\"}",
		"headers": {
			"Content-Type": "application/json"
		},
		"transformers": ["body-transformer"]
	}
}
```
The sample response body will return:
```
{
    "randomInteger": 56542
}
```

