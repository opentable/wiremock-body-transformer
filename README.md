##Wiremock Body Transformer
Wiremock Body Transformer is a [Wiremock](http://wiremock.org/) extension that can take the request body and interpolates the variable into the response.
Built on the extensions platform of Wiremock, it allows your wiremock response to be dynamic and dependent on the request for a smarter testing process.

###Installation
```
<dependency>
		<groupId>com.opentable</groupId>
		<artifactId>wiremock-body-transformer</artifactId>
		<version>1.0.1</version>
</dependency>
```

###How It Works
The response body stub acts as a template where the variables come from the request JSON object similar to string interpolation.
The variable fields are injected via the `$(foo)` notation, where 'foo' is a json field in the request body, as in:
```
{
    "foo": "bar"
}
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
The attribute `opentable` is referenced via `$(foo.bar)` in your response body.


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


####As part of the [Wiremock standalone process](http://wiremock.org/running-standalone.html#running-standalone):
Including the extension upon start on the command line.
```
--extensions com.opentable.extension.BodyTransformer
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
        "responseTransformers": ["body-transformer"]
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
		"responseTransformers": ["body-transformer"]
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
