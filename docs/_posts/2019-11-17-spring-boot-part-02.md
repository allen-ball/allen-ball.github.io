---
title: >-
    Spring Boot Part 2: Static Resources
canonical_url: https://blog.hcf.dev/article/2019-11-17-spring-boot-part-02
tags:
 - Java
 - Spring
permalink: article/2019-11-17-spring-boot-part-02
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
---

This series of articles will examine
[Spring Boot](https://spring.io/projects/spring-boot)
features.  This second article builds on the
[first article](/article/2019-11-16-spring-boot-part-01)
by demonstrating how to serve static resources from the `classpath` and
WebJars.

Complete source code for the
[series](https://github.com/allen-ball/spring-boot-web-server)
and for this
[part](https://github.com/allen-ball/spring-boot-web-server/tree/master/part-02)
are available on [Github](https://github.com/allen-ball).


## Static Resources

By default, resources found on the `classpath` under `/static` are served.
This is demonstrated by creating
`${project.basedir}/src/main/resources/static/index.html`:

``` xml
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  </head>
  <body>
    <h1>Hello, World!</h1>
  </body>
</html>
```

Starting the application as described in
[part 1](/article/2019-11-16-spring-boot-part-01) and
browsing `http://localhost:8080/` produces something similar to:

![](/assets/{{ page.permalink }}/hello-world.png)

Note: No additional Java code is required to serve static resources.


## WebJars

[WebJars](https://www.webjars.org/) are client-side web
libraries packaged into JAR (Java Archive) files.  Developers can use JVM
build tools (e.g., Maven and Gradle) may be used to download and manage
client-side dependencies.

To use this feature:

1. Add `org.webjars:webjars-locator-core` (version specified in the parent
POM) so Spring Boot finds it on the runtime `classpath` and enables the
feature

2. Add the WebJars as dependencies

The POM has been so modified to provide
[Bulma](https://bulma.io/).

``` xml
<project>
  ...
  <dependencies>
    ...
    <dependency>
      <groupId>org.webjars</groupId>
      <artifactId>webjars-locator-core</artifactId>
    </dependency>
    ...
    <dependency>
      <groupId>org.webjars.npm</groupId>
      <artifactId>bulma</artifactId>
      <version>0.8.0</version>
    </dependency>
    ...
  </dependencies>
  ...
</project>
```

After adding the corresponding stylesheet link to
`${project.basedir}/src/main/resources/static/index.html`:

``` xml
  ...
  <head>
    <link rel="stylesheet" href="/webjars/bulma/css/bulma.css"/>
  </head>
  ...
```

And browsing `http://localhost:8080/` shows the integration of the Bulma
styles.

![](/assets/{{ page.permalink }}/hello-world-bulma.png)

One additional feature is the developer does not need to be concerned with
the frontend versions when linking into HTML.  The
`org.webjars:webjars-locator-core` serves `bulma.css` at both
`http://localhost:8080/webjars/bulma/0.8.0/css/bulma.css` and
`http://localhost:8080/webjars/bulma/css/bulma.css`.

Note: Again, no additional Java code is required to serve static resources.


## Summary

This article demonstrates how static resources may be provided to be served
by the Spring Web Server.  These resources may be included on the
`classpath` under the `/static` folder or within WebJars.

[Part 3](/article/2019-12-15-spring-boot-part-03) of this series discusses
dependency injection and implements a simple
[`@RestController`][RestController].


[RestController]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RestController.html
