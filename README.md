##Wiremock Body Transformer
Wiremock Body Transformer is a [Wiremock](http://wiremock.org/) extension that can take the request body and interpolates the variable into the response.
Built on the extensions platform of Wiremock, it allows your wiremock response to be dynamic and dependent on the request for a smarter testing process.

###Installation
```
<dependency>
		<groupId>com.opentable</groupId>
		<artifactId>wiremock-body-transformer</artifactId>
		<version>1.0.0</version>
</dependency>
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
        .withBody("Original body")
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
        "body": "Original body",
        "responseTransformers": ["body-transformer"]
    }
}
```

