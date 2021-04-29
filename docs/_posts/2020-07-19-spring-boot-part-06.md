---
title: >-
    Spring Boot Part 6: Auto Configuration and Starters
canonical_url: https://blog.hcf.dev/article/2020-07-19-spring-boot-part-06/
tags:
 - Java
 - Spring
permalink: article/2020-07-19-spring-boot-part-06
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
  spring: >-
    https://docs.spring.io/spring/docs/5.3.6/javadoc-api
  spring-boot: >-
    https://docs.spring.io/spring-boot/docs/2.4.5/api
  spring-framework: >-
    https://docs.spring.io/spring-framework/docs/5.3.6/javadoc-api
  spring-security: >-
    https://docs.spring.io/spring-security/site/docs/5.4.6/api
excerpt_separator: <!--more-->
---

[Spring Boot] allows for the creation of "starters:" Convenient dependency
descriptors that often provide some specific but complex functionality.
Examples from Spring Boot discussed in this series of articles include
[`spring-boot-starter-web`][spring-boot-starter-web],
`spring-boot-starter-thymeleaf`, and `spring-boot-starter-actuator`.

This article describes some reusable code targeted for development and
testing (a REST controller @ `/jig/bean/{name}.json` and
`/jig/bean/{name}.xml`) and the steps necessary to create a Spring Boot
starter.  It also describes a starter for the embedded MySQL server process
described in
["Spring Embedded MySQL Server"](/article/2019-10-19-spring-embedded-mysqld/).

<!--more-->

Complete [javadoc] is provided.


## Theory of Operation

As discussed in
[part 1](/article/2019-11-16-spring-boot-part-01/), the
`spring-boot-starter-actuator` may be configured.  One of its sevices may be
used to get the list (with attributes) of the configured beans.  Sample
partial output below:

```bash
$ curl -is -X GET http://localhost:5001/actuator/beans/
HTTP/1.1 200
Content-Type: application/vnd.spring-boot.actuator.v3+json
Transfer-Encoding: chunked
Date: Sun, 19 Jul 2020 20:22:10 GMT

{
  "contexts" : {
    "application" : {
      "beans" : {
        ...
        "discoveryService" : {
          "scope" : "singleton",
          "type" : "upnp.DiscoveryService",
          "resource" : "file [/Users/ball/upnp-media-server/target/classes/upnp/DiscoveryService.class]",
          "dependencies" : [ "mediaServer" ]
        },
        ...
      },
      "parentId" : null
    }
  }
}
```

The `ball-spring-jig-starter` will provide the following two REST APIs to
retrieve a specific bean's value and demonstrate serialization to JSON or
XML as appropriate.  JSON partial output:

```bash
$ curl -is -X GET http://localhost:5000/jig/bean/discoveryService.json
HTTP/1.1 200
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sun, 19 Jul 2020 20:23:38 GMT

{
  "uuid:00000000-0000-1010-8000-0024BEF18BCC" : {
    "expiration" : 1595191949391,
    "ssdpmessage" : {
      "params" : { },
      "entity" : null,
      "locale" : null,
      "inetAddress" : "10.0.1.9",
      "st" : "uuid:00000000-0000-1010-8000-0024BEF18BCC",
      "location" : "http://10.0.1.9:52323/dmr.xml",
      "usn" : "uuid:00000000-0000-1010-8000-0024BEF18BCC",
      "protocolVersion" : {
        "protocol" : "HTTP",
        "major" : 1,
        "minor" : 1
      },
      ...
    },
    ...
  },
  ...
}
```

And corresponding XML output:

```bash
$ curl -is -X GET http://localhost:5000/jig/bean/discoveryService.xml
HTTP/1.1 200
Content-Type: application/xml
Transfer-Encoding: chunked
Date: Sun, 19 Jul 2020 20:29:47 GMT

<DiscoveryService>
  <uuid:00000000-0000-1010-8000-0024BEF18BCC>
    <expiration>1595192286661</expiration>
    <ssdpmessage>
      <params/>
      <entity/>
      <locale/>
      <inetAddress>10.0.1.9</inetAddress>
      <st>uuid:00000000-0000-1010-8000-0024BEF18BCC</st>
      <location>http://10.0.1.9:52323/dmr.xml</location>
      <usn>uuid:00000000-0000-1010-8000-0024BEF18BCC</usn>
      <protocolVersion>
        <protocol>HTTP</protocol>
        <major>1</major>
        <minor>1</minor>
      </protocolVersion>
      ...
    </ssdpmessage>
  </uuid:00000000-0000-1010-8000-0024BEF18BCC>
  ...
</DiscoveryService>
```

The implementation is described in the next section.


## Implementation

The steps to create the "jig" starter:

1. Implement the REST controller

2. Create a project and POM for the starter artifact

3. Create the auto-configuration class(es)

4. Link the auto-configuration class(es) into the starter's
`META-INF/spring.factories` resource<sup id="ref1">[1](#endnote1)</sup>

The [`BeanRestController`][BeanRestController] implementation is shown
below.

<figcaption style="text-align: center">
    ball.spring.jig.BeanRestController
</figcaption>
```java
@RestController
@RequestMapping(value = { "/jig/bean/" })
@ResponseBody
@NoArgsConstructor @ToString @Log4j2
public class BeanRestController implements ApplicationContextAware {
    private ApplicationContext context = null;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }

    @RequestMapping(method = { GET }, value = { "{name}.json" }, produces = APPLICATION_JSON_VALUE)
    public Object json(@PathVariable String name) throws Exception {
        return context.getBean(name);
    }

    @RequestMapping(method = { GET }, value = { "{name}.xml" }, produces = APPLICATION_XML_VALUE)
    public Object xml(@PathVariable String name) throws Exception {
        return context.getBean(name);
    }

    @ExceptionHandler({ NoSuchBeanDefinitionException.class, NoSuchElementException.class })
    @ResponseStatus(value = NOT_FOUND, reason = "Resource not found")
    public void handleNOT_FOUND() { }
}
```

Its implementation is straightforward: A method each to look-up the
requested bean and then serialize to JSON and XML.

In the project for the starter, add the
[`AutoConfiguration`][jig.AutoConfiguration] class.

<figcaption style="text-align: center">
    ball.spring.jig.autoconfigure.AutoConfiguration
</figcaption>
```java
@Configuration
@ConditionalOnClass({ BeanRestController.class })
@Import({ BeanRestController.class })
@NoArgsConstructor @ToString @Log4j2
public class AutoConfiguration {
}
```

It is critical that [`@Configuration`][Configuration] classes are added
through [`@Import`][Import] annotations and *not*
[`@ComponentScan`][ComponentScan] Neither the starter author nor the
integrator will be able to predict what components will or will not be
included in a scan.

It is a good practice to include a "conditional-on" annotation (e.g.,
[`@ConditionalOnClass`][ConditionalOnClass] to test any required dependency
software has been configured and/or is on the class path.

Finally, `META-INF/spring.factories` must be configured in the starter JAR
to notify Spring Boot to add the
[`AutoConfiguration`]({{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/jig/autoconfigure/AutoConfiguration.html).

<figcaption style="text-align: center">META-INF/spring.factories</figcaption>
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration: ball.spring.jig.autoconfigure.AutoConfiguration
```

Creating a starter for the embedded MySQL process described in
["Spring Embedded MySQL Server"](/article/2019-10-19-spring-embedded-mysqld/)
is equally straightforward.  Its
[`AutoConfiguration`][mysql.AutoConfiguration] class is shown below:

<figcaption style="text-align: center">
    ball.spring.mysqld.autoconfigure.AutoConfiguration
</figcaption>
```java
@Configuration
@ConditionalOnClass({ MysqldConfiguration.class })
@Import({ EntityManagerFactoryComponent.class, MysqldConfiguration.class })
@NoArgsConstructor @ToString @Log4j2
public class AutoConfiguration {
}
```

With it corresponding `META-INF/spring.factories`:

<figcaption style="text-align: center">META-INF/spring.factories</figcaption>
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration: ball.spring.mysqld.autoconfigure.AutoConfiguration
```


## Summary

Creating a [Spring Boot] starter is straightfoward: Create a project/POM to
host the starter's dependencies and auto-configuration class(es), add the
annotated auto-configuration class(es), and configure the starter JAR's
`META-INF/spring.factories` with the auto-configuration class(es).  That
starter's functionality can then be added to a Spring Boot application
simply by including a single dependency in the application POM.


<b id="endnote1">[1]</b>
Many [Spring Boot] components provide separate auto-configuration and
starter artifacts to support all use cases.  The author feels these example
implementations do not benefit from separate auto-configuration artifacts.
[↩](#ref1)


[Spring Boot]: https://spring.io/projects/spring-boot
[spring-boot-starter-web]: https://spring.io/guides/gs/spring-boot/

[ComponentScan]: {{ page.javadoc.spring }}/index.html?org/springframework/context/annotation/ComponentScan.html
[ConditionalOnClass]: {{ page.javadoc.spring-boot }}/org/springframework/boot/autoconfigure/condition/ConditionalOnClass.html
[Configuration]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Configuration.html
[Import]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Import.html?is-external=true

[javadoc]: {{ site.javadoc.url }}/{{ page.permalink }}/allclasses-noframe.html
[jig.AutoConfiguration]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/jig/autoconfigure/AutoConfiguration.html
[mysql.AutoConfiguration]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/mysqld/autoconfigure/AutoConfiguration.html
[BeanRestController]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/jig/BeanRestController.html
