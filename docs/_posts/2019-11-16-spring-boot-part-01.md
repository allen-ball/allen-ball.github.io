---
title: >-
    Spring Boot Part 1: Minimum Web Server, Devtools, and Actuator
canonical_url: https://blog.hcf.dev/article/2019-11-16-spring-boot-part-01
tags:
 - Java
 - Spring
permalink: article/2019-11-16-spring-boot-part-01
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
  spring: >-
    https://docs.spring.io/spring/docs/5.3.9/javadoc-api
  spring-boot: >-
    https://docs.spring.io/spring-boot/docs/2.5.3/api
  spring-security: >-
    https://docs.spring.io/spring-security/site/docs/5.5.1/api
---

This series of articles will examine [Spring Boot] features.  This first
article will look at the minimum Spring Boot application, Spring Boot
Devtools, and the Spring Boot Actuator.  There are already a wealth of
resources for Spring Boot including the [Spring Initializr]; these articles
do not intend to replace these resources but instead provide a collection of
skeletal projects which may be quickly and easily used to experiment with
specific Spring Boot features.

Complete source code for the
[series](https://github.com/allen-ball/spring-boot-web-server) and for this
[part](https://github.com/allen-ball/spring-boot-web-server/tree/trunk/part-01)
are available on [Github](https://github.com/allen-ball).

Note that this post's details and the example source code has been updated
for Spring Boot version 2.5.3 so some output may show older Spring Boot
versions.


## Minimum Web Server Project

The minimum web server project consists of a [Maven][Apache Maven] [POM], a
"Launcher" class with static `main` function, and an
`applications.properties` file.  The web server will be launched from Maven
by invoking the `spring-boot:run` plug-in.

The minimum POM for the server is:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>ball</groupId>
  <artifactId>spring-boot-web-server</artifactId>
  <packaging>jar</packaging>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>2.5.3.RELEASE</version>
  </parent>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies verbose="true">
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
  </dependencies>
</project>
```

This POM:

1. Specifies the parent POM as
   `org.springframework.boot:spring-boot-dependencies`,<sup id="ref1">[1](#endnote1)</sup>

2. Sets the `${project.build.sourceEncoding}` property to `UTF-8` avoid
   Maven warnings,

3. Specifies the one required Spring Boot dependency,
   `org.springframework.boot:spring-boot-starter-web`, and,

4. Provides the [`org.projectlombok:lombok`][Project Lombok] artifact

The last is not strictly required but it saves on writing the Java
"boilerplate" required by the implementation beans.

A static, annotated `main` function must be provided:

<pre data-src="https://raw.githubusercontent.com/allen-ball/spring-boot-web-server/trunk/part-01/src/main/java/application/Launcher.java"></pre>

And an empty
`${project.basedir}/src/main/resources/application.properties`:

<pre data-src="https://raw.githubusercontent.com/allen-ball/spring-boot-web-server/trunk/part-01/src/main/resources/application.properties"></pre>

Executing `mvn -B spring-boot:run` in the project directory gives the log
output:

![](/assets/{{ page.permalink }}/spring-boot-run.png)

which indicates Tomcat has been started listening on port 8080.  (Note the
ASCII-art banner and color in the log lines.)  However, browsing to
<http://localhost:8080/> shows:

![](/assets/{{ page.permalink }}/whitelabel-error-page.png)

This is to be expected!  This project has not yet defined any content
(static or dynamic) to be served.<sup id="ref2">[2](#endnote2)</sup>


## Spring Boot Devtools

The `org.springframework.boot:spring-boot-devtools` artifact may be added to
the classpath to provide developer tools.  There are a number of features
documented at
[docs.spring.io](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools)
including automatic restart and live reload but this article will examine
features for setting application properties in a development environment.

The changes to the POM are encapsulated in the `spring-boot:run`
`<profile/>`:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  ...
  <profiles>
    <profile>
      <id>spring-boot:run</id>
      <properties>
        <maven.source.skip>true</maven.source.skip>
        <maven.javadoc.skip>true</maven.javadoc.skip>
        <maven.test.skip>true</maven.test.skip>
      </properties>
      <dependencies>
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-devtools</artifactId>
          <scope>runtime</scope>
        </dependency>
      </dependencies>
      <build>
        <defaultGoal>clean spring-boot:run</defaultGoal>
      </build>
    </profile>
  </profiles>
  ...
</project>
```

This profile:

1. Sets Maven properties to skip source generation, javadoc generation, and
running test.

2. Sets the Maven default goal(s) to "`clean`" and "`spring-boot:run`".

The
[Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/index.html)
[Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
section discusses in detail how, where, and when property values may be
overridden.  Notably
`${user.home}/.config/spring-boot/spring-boot-devtools.properties`
may be used to store a developer's default settings for all projects and
`${project.basedir}/application.properties` (typically outside
source control) may be used to store properties used for development such as
database authentication details.

This author includes the following in his
`${user.home}/.config/spring-boot/spring-boot-devtools.properties`
to reduce log output during testing:

``` properties
spring.main.banner-mode: OFF
spring.main.headless: false
spring.main.log-startup-info: false

spring.output.ansi.enabled: NEVER
```

and executing the application with devtools through the new profile
(`mvn -B -Pspring-boot:run`) gives the following shorter and less colorful
output.

![](/assets/{{ page.permalink }}/spring-boot-run+devtools.png)


## Spring Boot Actuator

The
[Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator)
provides monitoring and management features.  All that is required to enable
it is to include its "starter" as a dependency which has been added to the
`spring-boot:run` `<profile/>`:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  ...
  <profiles>
    <profile>
      <id>spring-boot:run</id>
      ...
      <dependencies>
        ...
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-actuator</artifactId>
          <scope>runtime</scope>
        </dependency>
        ...
      </dependencies>
      ...
    </profile>
  </profiles>
  ...
</project>
```

The Spring Boot Actuator also needs to be configured by setting application
properties (e.g., in
`${user.home}/.config/spring-boot/spring-boot-devtools.properties`).

``` properties
management.server.address: 127.0.0.1
management.server.port: 8081
management.endpoints.web.exposure.include: *
management.endpoints.enabled-by-default: true
management.endpoint.shutdown.enabled: true
```

Executing `mvn -B -Pspring-boot:run` gives the following log output:

![](/assets/{{ page.permalink }}/spring-boot-run+actuator.png)

Which indicates Tomcat is listening on ports 8080 and 8081.
<http://localhost:8081/actuator> returns the following JSON which lists the
available endpoints:

``` json
{
  "_links": {
    "self": {
      "href": "http://localhost:8081/actuator",
      "templated": false
    },
    "beans": {
      "href": "http://localhost:8081/actuator/beans",
      "templated": false
    },
    "caches": {
      "href": "http://localhost:8081/actuator/caches",
      "templated": false
    },
    "caches-cache": {
      "href": "http://localhost:8081/actuator/caches/{cache}",
      "templated": true
    },
    "health": {
      "href": "http://localhost:8081/actuator/health",
      "templated": false
    },
    "health-path": {
      "href": "http://localhost:8081/actuator/health/{*path}",
      "templated": true
    },
    "info": {
      "href": "http://localhost:8081/actuator/info",
      "templated": false
    },
    "conditions": {
      "href": "http://localhost:8081/actuator/conditions",
      "templated": false
    },
    "shutdown": {
      "href": "http://localhost:8081/actuator/shutdown",
      "templated": false
    },
    "configprops": {
      "href": "http://localhost:8081/actuator/configprops",
      "templated": false
    },
    "env": {
      "href": "http://localhost:8081/actuator/env",
      "templated": false
    },
    "env-toMatch": {
      "href": "http://localhost:8081/actuator/env/{toMatch}",
      "templated": true
    },
    "loggers": {
      "href": "http://localhost:8081/actuator/loggers",
      "templated": false
    },
    "loggers-name": {
      "href": "http://localhost:8081/actuator/loggers/{name}",
      "templated": true
    },
    "heapdump": {
      "href": "http://localhost:8081/actuator/heapdump",
      "templated": false
    },
    "threaddump": {
      "href": "http://localhost:8081/actuator/threaddump",
      "templated": false
    },
    "metrics": {
      "href": "http://localhost:8081/actuator/metrics",
      "templated": false
    },
    "metrics-requiredMetricName": {
      "href": "http://localhost:8081/actuator/metrics/{requiredMetricName}",
      "templated": true
    },
    "scheduledtasks": {
      "href": "http://localhost:8081/actuator/scheduledtasks",
      "templated": false
    },
    "mappings": {
      "href": "http://localhost:8081/actuator/mappings",
      "templated": false
    }
  }
}
```

For example, <http://localhost:8081/actuator/metrics> returns:

``` json
{
  "names": [
    "jvm.memory.max",
    "jvm.threads.states",
    "process.files.max",
    "jvm.gc.memory.promoted",
    "system.load.average.1m",
    "jvm.memory.used",
    "jvm.gc.max.data.size",
    "jvm.memory.committed",
    "system.cpu.count",
    "logback.events",
    "jvm.buffer.memory.used",
    "tomcat.sessions.created",
    "jvm.threads.daemon",
    "system.cpu.usage",
    "jvm.gc.memory.allocated",
    "tomcat.sessions.expired",
    "jvm.threads.live",
    "jvm.threads.peak",
    "process.uptime",
    "tomcat.sessions.rejected",
    "process.cpu.usage",
    "jvm.classes.loaded",
    "jvm.classes.unloaded",
    "tomcat.sessions.active.current",
    "tomcat.sessions.alive.max",
    "jvm.gc.live.data.size",
    "process.files.open",
    "jvm.buffer.count",
    "jvm.buffer.total.capacity",
    "tomcat.sessions.active.max",
    "process.start.time"
  ]
}
```

And "process start time" can be retrieved through
<http://localhost:8081/actuator/metrics/process.start.time> which returns
something like:

``` json
{
  "name": "process.start.time",
  "description": "Start time of the process since unix epoch.",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 1573860137.896
    }
  ],
  "availableTags": []
}
```


## Spring Boot Properties Migrator

Spring Boot relies on numerous properties whose names may change from
release to release.  The `spring-boot-properties-migrator` is configured in
the `spring-boot:run` `<profile/>`:

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
  ...
  <profiles>
    <profile>
      <id>spring-boot:run</id>
      ...
      <dependencies>
        ...
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-properties-migrator</artifactId>
          <scope>runtime</scope>
        </dependency>
        ...
      </dependencies>
      ...
    </profile>
  </profiles>
  ...
</project>
```

In the event that a property name changes it will be noted in the log
output:

![](/assets/{{ page.permalink }}/spring-boot-run+properties-migrator.png)


## Summary

This article demonstrates the use of the `spring-boot-starter-web`, the use
of `spring-boot-devtools`,<sup id="ref3">[3](#endnote3)</sup> and the
integration of `spring-boot-starter-actuator`.  The POM defines a
`spring-boot:run` `<profile/>` which can be used for testing the
application.  The projects described in subsequent articles in this series
will benefit by having both Spring Boot Devtools and the Spring Boot
Actuator activated during development runs of the application.

[Part 2](/article/2019-11-17-spring-boot-part-02) of this
series demonstrates how static resources may be added to be served by the
web server.


<b id="endnote1">[1]</b>
This author prefers this specific method if only because versions of
provided dependencies may be updated simply by updating the corresponding
property value in the project POM.
[↩](#ref1)

<b id="endnote2">[2]</b>
In fact, serving content is not covered here and will be the subject of
subsequent articles in this series.
[↩](#ref2)

<b id="endnote3">[3]</b>
With `spring-boot-properties-migrator`.
[↩](#ref3)


[Apache Maven]: https://maven.apache.org/
[POM]: http://maven.apache.org/guides/introduction/introduction-to-the-pom.html

[Project Lombok]: https://projectlombok.org/

[Spring Boot]: https://spring.io/projects/spring-boot
[Spring Initializr]: https://start.spring.io/
