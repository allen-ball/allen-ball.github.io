---
title: >-
    Spring Boot Part 3: Dependency Injection and @RestController
canonical_url: https://blog.hcf.dev/article/2019-12-15-spring-boot-part-03
tags:
 - Java
 - Spring
permalink: article/2019-12-15-spring-boot-part-03
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
  spring: >-
    https://docs.spring.io/spring/docs/5.3.7/javadoc-api
  spring-boot: >-
    https://docs.spring.io/spring-boot/docs/2.5.0/api
  spring-framework: >-
    https://docs.spring.io/spring-framework/docs/5.3.7/javadoc-api
  spring-security: >-
    https://docs.spring.io/spring-security/site/docs/5.5.0/api
---

This series of articles will examine [Spring Boot] features.  This third
article builds on the series by demonstrating the basics of Spring
*Dependency Injection*.  To create demonstrable code the example also
creates a [`@RestController`][RestController] implementation, a simple
shared property server where clients may put and get property values.

Complete source code for the
[series](https://github.com/allen-ball/spring-boot-web-server)
and for this
[part](https://github.com/allen-ball/spring-boot-web-server/tree/trunk/part-03)
are available on [Github](https://github.com/allen-ball).


## @ComponentScan and Dependency Injection

This is not an exhaustive description and only describes the simplest (but
arguably most common) features of Spring's dependency injection.

The Spring Boot process starts in the `Launcher::main` method (unchanged
from the implementation described in parts
[1](/article/2019-11-16-spring-boot-part-01) and
[2](/article/2019-11-17-spring-boot-part-02) of this series) with the
[construction][SpringBootApplication.init] of a
[`SpringApplication`][SpringApplication] and invocation of its
[`run`][SpringApplication.run] method.  Annotating the class with
[`@SpringBootApplication`][SpringBootApplication] is the equivalent of
annotating with [`@Configuration`][Configuration],
[`@EnableAutoConfiguration`][EnableAutoConfiguration], and
[`@ComponentScan`][ComponentScan].

``` java
@SpringBootApplication
@NoArgsConstructor @ToString @Log4j2
public class Launcher {
    public static void main(String[] argv) throws Exception {
        SpringApplication application = new SpringApplication(Launcher.class);

        application.run(argv);
    }
}
```

[`SpringApplication`][SpringApplication] starts its analysis with the
[`application.Launcher`][application.Launcher] class.  The
[`@EnableAutoConfiguration`][EnableAutoConfiguration] indicates Spring Boot
should attempt to "guess" as necessary.  The
[`@ComponentScan`][ComponentScan] annotation indicates that Spring Boot
should start scanning classes in the `application` package (containing
package for `Launcher`) for classes annotated with
[`@Component`][Component].  Note: Annotations-types annotated with
`@Component` are also components.  For example,
[`@Configuration`][Configuration], [`@Controller`][Controller], and
[`@RestController`][RestController] are all `@Component`s, but not
vice-versa.

For each class annotated with [`@Component`][Component], Spring:

1. Instantiates a single instance,

2. For each instance field annotated with [`@Value`][Value], evaluate the
[SpEL](https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/core.html#expressions-beandef)
expression<sup id="ref1">[1](#endnote1)</sup> and initialize the field with
the result,

3. For each method annotated with [`@Bean`][Bean] within a
[`@Configuration`][Configuration] class, invoke the method exactly once to
obtain the bean value, and,

4. For each field annotated with [`@Autowired`][Autowired], assign the
corresponding value obtained by evaluating a [`@Bean`][Bean] method.

Again, the above is a gross oversimplification, not exhaustive, and relies
on handwaving but should be enough to get started.

The
[sample code](https://github.com/allen-ball/spring-boot-web-server/tree/trunk/part-03)
for this article does not require [`@Value`][Value] injection but a previous
[article](/article/2019-10-19-spring-embedded-mysqld) provides examples in
its `MysqldConfiguration` implementation:

``` java
    @Value("${mysqld.home}")
    private File home;

    @Value("${mysqld.defaults.file:${mysqld.home}/my.cnf}")
    private File defaults;

    @Value("${mysqld.datadir:${mysqld.home}/data}")
    private File datadir;

    @Value("${mysqld.port}")
    private Integer port;

    @Value("${mysqld.socket:${mysqld.home}/socket}")
    private File socket;

    @Value("${logging.path}/mysqld.log")
    private File console;
```

The above code takes advantage of specifying default values in SpEL
expressions and automated type conversion.

The simple property server implemented here-in creates a "dictionary" bean
within the [`DictionaryConfiguration`][DictionaryConfiguration]:

``` java
@Configuration
@NoArgsConstructor @ToString @Log4j2
public class DictionaryConfiguration {
    @Bean
    public Map<String,String> dictionary() {
        return new ConcurrentSkipListMap<>();
    }
}
```

And that bean is wired into the
[`DictionaryRestController`][DictionaryRestController] as follows:

``` java
@RestController
...
@NoArgsConstructor @ToString @Log4j2
public class DictionaryRestController {
    @Autowired private Map<String,String> dictionary = null;
    ...
}
```

The next section describes the implementation of the
[`@RestController`][RestController].


## @RestController Implementation

The [`@RestController`][RestController] implemented here-in provides the following web API:

| Method                                   | URI                                         | Query Parameters | Returns                                                    |
|------------------------------------------|---------------------------------------------|------------------|------------------------------------------------------------|
| `GET`                                    | <http://localhost:8080/dictionary/get>      | *key*            | The value associated with *key* (may be `null`)            |
| `GET`<sup id="ref2">[2](#endnote2)</sup> | <http://localhost:8080/dictionary/put>      | *key*=*value*    | The previous value associated with *key* (may be `null`)   |
| `GET`                                    | <http://localhost:8080/dictionary/remove>   | *key*            | The value previously associated with *key* (may be `null`) |
| `GET`                                    | <http://localhost:8080/dictionary/size>     | NONE             | int                                                        |
| `GET`                                    | <http://localhost:8080/dictionary/entrySet> | NONE             | Array of key-value pairs                                   |
| `GET`                                    | <http://localhost:8080/dictionary/keySet>   | NONE             | Array of key values                                        |

[`DictionaryRestController`][DictionaryRestController] is annotated with
[`@RestController`][RestController] and [`@RequestMapping`][RequestMapping]
with `value = { "/dictionary/" }` indicating request paths will be prefixed
with `/dictionary/` and `produces = "application/json"` indicating that
`HTTP` responses should be encoded in `JSON`.

``` java
@RestController
@RequestMapping(value = { "/dictionary/" }, produces = MediaType.APPLICATION_JSON_VALUE)
@NoArgsConstructor @ToString @Log4j2
public class DictionaryRestController {
    @Autowired private Map<String,String> dictionary = null;
    ...
}
```

The dictionary map is [`@Autowired`][Autowired] as described in the previous
section.

The implementation of the `/dictionary/put` method is:

``` java
    @RequestMapping(method = { RequestMethod.GET }, value = { "put" })
    public Optional<String> put(@RequestParam Map<String,String> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException();
        }

        Map.Entry<String,String> entry = parameters.entrySet().iterator().next();
        String result = dictionary.put(entry.getKey(), entry.getValue());

        return Optional.ofNullable(result);
    }
```

Spring will inject the request's query parameters in the method call as
`parameters`.  The method verifies that exactly one query parameter is
specified, puts that key-value into the dictionary, and returns the result
(the previous value for that key in the map).  Spring interprets a
[`String`][String] as literal `JSON` so the method wraps the result in an
[`Optional`][Optional] to force Spring to encode to `JSON`.

The implementation of the `/dictionary/get` method is:

``` java
    @RequestMapping(method = { RequestMethod.GET }, value = { "get" })
    public Optional<String> get(@RequestParam Map<String,String> parameters) {
        if (parameters.size() != 1) {
            throw new IllegalArgumentException();
        }

        Map.Entry<String,String> entry = parameters.entrySet().iterator().next();
        String result = dictionary.get(entry.getKey());

        return Optional.ofNullable(result);
    }
```

Again, there must be exactly one query parameter and the result is wrapped
in an [`Optional`][Optional].  The implementation of the
`/dictionary/remove` request is nearly identical.

The implementation of the `/dictionary/size` method is:

``` java
    @RequestMapping(method = { RequestMethod.GET }, value = { "size" })
    public int size(@RequestParam Map<String,String> parameters) {
        if (! parameters.isEmpty()) {
            throw new IllegalArgumentException();
        }

        return dictionary.size();
    }
```

No query parameters should be specified.  The implementation of the
`/dictionary/entrySet` is nearly identical with a method return type of
[`Set<Map.Entry<String,String>>`][Set]:

``` java
    @RequestMapping(method = { RequestMethod.GET }, value = { "entrySet" })
    public Set<Map.Entry<String,String>> entrySet(@RequestParam Map<String,String> parameters) {
        if (! parameters.isEmpty()) {
            throw new IllegalArgumentException();
        }

        return dictionary.entrySet();
    }
```

And the implementation of `/dictionary/keySet` follows the same pattern.

The [Maven](http://maven.apache.org/) project
[POM](http://maven.apache.org/guides/introduction/introduction-to-the-pom.html)
provides a `spring-boot:run` profile described in the first
[article](/article/2019-11-16-spring-boot-part-01) of this series and the
server may be started with `mvn -B -Pspring-boot:run`.  When started with
this profile, the [Spring Boot Actuator] is available.  The
[`@RestController`][RestController] handler mappings may be verified with
the following query:

``` bash
$ curl -X GET http://localhost:8081/actuator/mappings \
> | jq '.contexts.application.mappings.dispatcherServlets[][]
        | {handler: .handler, predicate: .predicate}'
{
  "handler": "application.DictionaryRestController#remove(Map)",
  "predicate": "{GET /dictionary/remove, produces [application/json]}"
}
{
  "handler": "application.DictionaryRestController#get(Map)",
  "predicate": "{GET /dictionary/get, produces [application/json]}"
}
{
  "handler": "application.DictionaryRestController#put(Map)",
  "predicate": "{GET /dictionary/put, produces [application/json]}"
}
{
  "handler": "application.DictionaryRestController#size(Map)",
  "predicate": "{GET /dictionary/size, produces [application/json]}"
}
{
  "handler": "application.DictionaryRestController#entrySet(Map)",
  "predicate": "{GET /dictionary/entrySet, produces [application/json]}"
}
{
  "handler": "application.DictionaryRestController#keySet(Map)",
  "predicate": "{GET /dictionary/keySet, produces [application/json]}"
}
{
  "handler": "org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController#errorHtml(HttpServletRequest, HttpServletResponse)",
  "predicate": "{ /error, produces [text/html]}"
}
{
  "handler": "org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController#error(HttpServletRequest)",
  "predicate": "{ /error}"
}
{
  "handler": "ResourceHttpRequestHandler [\"classpath:/META-INF/resources/webjars/\"]",
  "predicate": "/webjars/**"
}
{
  "handler": "ResourceHttpRequestHandler [\"classpath:/META-INF/resources/\", \"classpath:/resources/\", \"classpath:/static/\", \"classpath:/public/\", \"/\"]",
  "predicate": "/**"
}
```

Using `curl` to verify the put operation (note the difference in return
values from the first and second invocations):

``` bash
$ curl -X GET -i http://localhost:8080/dictionary/put?foo=bar
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 11 Dec 2019 19:56:43 GMT

null

$ curl -X GET -i http://localhost:8080/dictionary/put?foo=bar
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 11 Dec 2019 19:56:44 GMT

"bar"
```

And then verify the previous put with the get operation:

``` bash
$ curl -X GET -i http://localhost:8080/dictionary/get?foo
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 11 Dec 2019 19:59:22 GMT

"bar"
```

Retrieving the dictionary entry set demonstrates complex JSON encoding:

``` bash
$ curl -X GET -i http://localhost:8080/dictionary/entrySet
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 11 Dec 2019 20:00:24 GMT

[ {
  "foo" : "bar"
} ]
```

And supplying a query parameter to size demonstrates error handling:

``` bash
$ curl -X GET -i http://localhost:8080/dictionary/size?foo
HTTP/1.1 500
Content-Type: application/json
Transfer-Encoding: chunked
Date: Wed, 11 Dec 2019 20:03:42 GMT
Connection: close

{
  "timestamp" : "2019-12-11T20:03:42.110+0000",
  "status" : 500,
  "error" : "Internal Server Error",
  "message" : "No message available",
  "trace" : "java.lang.IllegalArgumentException\n\tat application.DictionaryRestController.size(DictionaryRestController.java:65)\n...",
  "path" : "/dictionary/size"
}
```


## Summary

This article demonstrates basic Spring dependency injection through showing
how "[`@Value`s][Value]" may be calculated and injected and
"[`@Bean`s][Bean]" may be created and "[`@Autowired`][Autowired]" in a
[`@RestController`][RestController] implementation.

[Part 4](/article/2020-01-01-spring-boot-part-04) of this
series discusses Spring MVC and implements a simple internationalized clock
application as an example.


<b id="endnote1">[1]</b>
SpEL also provides access to the properties defined in the
`application.properties` resources.
[↩](#ref1)

<b id="endnote2">[2]</b>
It is unfortunate that the `GET` HTTP method combined with the `Map` `put`
method may cause confusion and a more sophisticated API definition might
reasonably use `POST` or `PUT` methods for their semantic value.
[↩](#ref2)


[Optional]: {{ page.javadoc.javase }}/java/util/Optional.html
[Set]: {{ page.javadoc.javase }}/java/util/Set.html
[String]: {{ page.javadoc.javase }}/java/lang/String.html

[Autowired]: {{ page.javadoc.spring-framework }}/org/springframework/beans/factory/annotation/Autowired.html
[Bean]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Bean.html?is-external=true
[ComponentScan]: {{ page.javadoc.spring }}/index.html?org/springframework/context/annotation/ComponentScan.html
[Component]: {{ page.javadoc.spring-framework }}/org/springframework/stereotype/Component.html
[Configuration]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Configuration.html
[Configuration]: {{ page.javadoc.spring-framework }}/org/springframework/context/annotation/Configuration.html
[Controller]: {{ page.javadoc.spring-framework }}/org/springframework/stereotype/Controller.html
[EnableAutoConfiguration]: {{ page.javadoc.spring-boot }}/org/springframework/boot/autoconfigure/EnableAutoConfiguration.html
[RequestMapping]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RequestMapping.html
[RestController]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RestController.html
[Value]: {{ page.javadoc.spring-framework }}/org/springframework/beans/factory/annotation/Value.html

[Spring Boot]: https://spring.io/projects/spring-boot
[Spring Boot Actuator]: https://docs.spring.io/spring-boot/docs/2.4.x/reference/html/production-ready-features.html#production-ready
[SpringApplication.run]: {{ page.javadoc.spring-boot }}/org/springframework/boot/SpringApplication.html#run-java.lang.String...-
[SpringApplication]: {{ page.javadoc.spring-boot }}/org/springframework/boot/SpringApplication.html
[SpringBootApplication]: {{ page.javadoc.spring-boot }}/org/springframework/boot/autoconfigure/SpringBootApplication.html
[SpringBootApplication.init]: {{ page.javadoc.spring-boot }}/org/springframework/boot/SpringApplication.html#SpringApplication-java.lang.Class...-

[application.Launcher]: https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-03/src/main/java/application/Launcher.java
[DictionaryConfiguration]: https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-03/src/main/java/application/DictionaryConfiguration.java
[DictionaryRestController]: https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-03/src/main/java/application/DictionaryRestController.java
