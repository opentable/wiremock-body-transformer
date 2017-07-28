## Wiremock Body Transformer
Wiremock Body Transformer is a [Wiremock](http://wiremock.org/) extension that can take the request body and interpolates the variable into the response.
Built on the extensions platform of Wiremock, it allows your wiremock response to be dynamic and dependent on the request for a smarter testing process.

### Installation
```
<dependency>
		<groupId>com.opentable</groupId>
		<artifactId>wiremock-body-transformer</artifactId>
		<version>1.1.6</version>
</dependency>
```

### How It Works
The body transformer supports __JSON__, __XML__, __x-www-form-urlencoded__ and __query string__ formats.

The response body stub acts as a template where the variables come from the request json/xml/form body similar to string interpolation.
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

Form fields value can be UTF-8 encoded, empty or not empty. 
````
utf8=%E2%9C%93&foo=bar&emptyFoo=&encodedFoo=Encoded+Foo+Value
````
`$(foo)` will return `bar`,
`$(emptyFoo)` will return empty string,
`$(encodedFoo)` will return `Encoded Foo Value`.

Query string parameters can be used simply by passing them at end of URL.

````
myurl.com?foo=bar&baz=bak
````

To get parameters values in this example, simply put their names in stub or a file that will be returned with the default notation as showed below.

```
{
	"msg": "This is a json response file",
	"param1": "$(foo)",
	"param2": "$(bar)"
}
```

This response will be retuned as follows:

```
{
	"msg": "This is a json response file",
	"param1": bar,
	"param2": bak
}
```
### Nested Fields
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
Pass a regex parameter named `urlRegex` to match the url and extract relevant groups. Use named capturing groups in your regex to pass in group names.

##### Example
Fetching a url like: /params/slash1/10/slash2/20?one=value1&two=value2&three=value3 using the following code in Java:
```
    wireMockRule.stubFor(get(urlMatching("/params/slash1/[0-9]+?/slash2/[0-9]+?.*"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"slash1\":\"$(slash1Var)\", \"slash2\":\"$(slash2Var)\", \"one\":\"$(oneVar)\", \"two\":\"$(twoVar)\", \"three\":\"$(threeVar)\"}")
            .withTransformers("body-transformer")
        .withTransformerParameter("urlRegex", "/params/slash1/(?<slash1Var>.*?)/slash2/(?<slash2Var>.*?)\\?one=(?<oneVar>.*?)\\&two=(?<twoVar>.*?)\\&three=(?<threeVar>.*?)")));
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

**Be careful**, when using 'urlRegex', the value captured by the named group in the regex will take precedence over the variables in the xml/json/key-value request body.
##### Example
Fetching url `/param/10` with body `var=11&got=it`
```
    wireMockRule.stubFor(post(urlMatching("/param/[0-9]+?"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"var\":\"$(var)\",\"got\":\"it\"}")
            .withTransformers("body-transformer")
            .withTransformerParameter("urlRegex", "/param/(?<var>.*?)")));
```
returns a body that looks like:
```
{
    "var":"10",
    "got":"it"
}
```

So, the **$(var)** was replaced with url regex **var=10** instead of the body **var=11**

### Usage

#### As part of [Unit Testing with Wiremock](http://wiremock.org/extending-wiremock.html):

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

#### As part of the [Wiremock standalone process](http://wiremock.org/running-standalone.html#running-standalone):
[\[Download the body transformer extension jar file here.\]](https://github.com/opentable/wiremock-body-transformer/releases/download/wiremock-body-transformer-1.1.6/wiremock-body-transformer-1.1.6.jar)

[\[Download the Wiremock standalone jar here.\]](http://repo1.maven.org/maven2/com/github/tomakehurst/wiremock-standalone/2.3.1/wiremock-standalone-2.3.1.jar)

Including the extension upon start on the command line via the `--extensions` flag. Note that the BodyTransformer jar is added to the classpath.

For Unix:
```
java -cp "wiremock-body-transformer-1.1.6.jar:wiremock-2.3.1-standalone.jar" com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose --extensions com.opentable.extension.BodyTransformer
```

For Windows:
```
java -cp "wiremock-body-transformer-1.1.6.jar;wiremock-2.3.1-standalone.jar" com.github.tomakehurst.wiremock.standalone.WireMockServerRunner --verbose --extensions com.opentable.extension.BodyTransformer
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

### Example
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

### Additional Features

#### Random Integer Generator
With the pattern `$(!RandomInteger)` inside the stub response body, a random positive integer will be interpolated in that position.

##### Example
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

