---
title: >-
    Spring Boot Part 5: voyeur, A Non-Trivial Application
canonical_url: https://blog.hcf.dev/article/2020-06-26-spring-boot-part-05/
tags:
 - Java
 - Spring
 - Thymeleaf
permalink: article/2020-06-26-spring-boot-part-05
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
  ball-api: >-
    https://repo.hcf.dev/javadoc/ball-api/20200622.0
---

[This](/article/2019-11-16-spring-boot-part-01/)
[series](/article/2019-11-17-spring-boot-part-02/)
[of](/article/2019-12-15-spring-boot-part-03/)
[articles](/article/2020-01-01-spring-boot-part-04/) examines [Spring Boot]
features.  This fifth article in the series presents a non-trivial
application which probes local hosts (with the help of the [`nmap`][nmap]
command) to assist in developing [UPNP] and [SSDP] applications.

![](/assets/{{ page.permalink }}/screen-shot-nmap.png)

Complete [source](https://github.com/allen-ball/voyeur) and
[javadoc](https://allen-ball.github.io/voyeur/) are available
on [GitHub](https://github.com/allen-ball).  Additional
artifacts (including their respective source and javadoc JARs) are available
from the Maven repository at:

```xml
    <repository>
      <id>repo.hcf.dev-RELEASE</id>
      <name>hcf.dev RELEASE Repository</name>
      <url>https://repo.hcf.dev/maven/release/</url>
      <layout>default</layout>
    </repository>
```

Specific topics covered herein:

* [`@Service`][Service] implementations with [`@Scheduled`][Scheduled]
  updates
    - [`@Autowired`][Autowired]

* UI [`@Controller`][Controller]
    - Populates [`Model`][Model]
    - Thymeleaf temlates and decoupled logic

* [`@RestController`][RestController] implementation


## Theory of Operation

The following subsections describe the components.


### @Service Implementations

The [`voyeur`][voyeur] package defines a number of (annotated)
[`@Service`s][Service]:

| [`@Service`][Service]                           | Description                                                                                                                                         |
| ---                                             | ---                                                                                                                                                 |
| [`ArpCache`][voyeur.ArpCache]                   | [`Map`][Map] of [`InetAddress`][InetAddress] to hardware address periodically updated by reading `/proc/net/arp` or parsing the output of `arp -an` |
| [`NetworkInterfaces`][voyeur.NetworkInterfaces] | [`Set`][Set] of [`NetworkInterface`s][NetworkInterface]                                                                                             |
| [`Nmap`][voyeur.Nmap]                           | `Map` of XML output of the `nmap` command for each `InetAddress` discovered via `ARPCache`, `NetworkInterfaces`, and/or `SSDP`                      |
| [`SSDP`][voyeur.SSDP]                           | SSDP hosts discovered via [`SSDPDiscoveryCache`][SSDPDiscoveryCache]                                                                                |

Each of these services implement a [`Set`][Set] or [`Map`][Map], which may
be [`@Autowire`d][Autowired] into other components, and periodically update
themselves with a [`@Scheduled`][Scheduled] method.  The
[`Nmap`][voyeur.Nmap] service is examined in detail.

First, the [`@PostConstruct`][PostConstruct] method (in addition to
performing other initialization chores) tests to determine if the
[`nmap`][nmap] command is available:

```java
...
@Service
@NoArgsConstructor @Log4j2
public class Nmap extends InetAddressMap<Document> ... {
    ...
    private static final String NMAP = "nmap";
    ...
    private boolean disabled = true;
    ...
    @PostConstruct
    public void init() throws Exception {
        ...
        try {
            List<String> argv = Stream.of(NMAP, "-version").collect(toList());

            log.info(String.valueOf(argv));

            Process process =
                new ProcessBuilder(argv)
                .inheritIO()
                .redirectOutput(PIPE)
                .start();

            try (InputStream in = process.getInputStream()) {
                new BufferedReader(new InputStreamReader(in, UTF_8))
                    .lines()
                    .forEach(t -> log.info(t));
            }

            disabled = (process.waitFor() != 0);
        } catch (Exception exception) {
            disabled = true;
        }

        if (disabled) {
            log.warn("nmap command is not available");
        }
    }
    ...
    public boolean isDisabled() { return disabled; }
    ...
}
```

If the [`nmap`][nmap] command is successful, its version is logged.
Otherwise, `disabled` is set to `true` and no further attempt is made to run
the `nmap` command in other methods.

The [`@Scheduled`][Scheduled] [`update()`][voyeur.Nmap.update] method is
invoked every 30 seconds and ensures a map entry exists for every
[`InetAddress`][InetAddress] previously discovered by the
[`NetworkInterfaces`][voyeur.NetworkInterfaces],
[`ARPCache`][voyeur.ARPCache], and [`SSDP`][voyeur.SSDP] components and then
queues a `Worker` `Runnable` for any value whose output is more than
`INTERVAL` (60 minutes) old.  The [`@EventListener`][EventListener] (with
[`ApplicationReadyEvent`][ApplicationReadyEvent] guarantees the method won't
be called before the application is ready (to serve requests).

```java
public class Nmap extends InetAddressMap<Document> ... {
    ...
    private static final Duration INTERVAL = Duration.ofMinutes(60);
    ...
    @Autowired private NetworkInterfaces interfaces = null;
    @Autowired private ARPCache arp = null;
    @Autowired private SSDP ssdp = null;
    @Autowired private ThreadPoolTaskExecutor executor = null;
    ...
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 30 * 1000)
    public void update() {
        if (! isDisabled()) {
            try {
                Document empty = factory.newDocumentBuilder().newDocument();

                empty.appendChild(empty.createElement("nmaprun"));

                interfaces
                    .stream()
                    .map(NetworkInterface::getInterfaceAddresses)
                    .flatMap(List::stream)
                    .map(InterfaceAddress::getAddress)
                    .filter(t -> (! t.isMulticastAddress()))
                    .forEach(t -> putIfAbsent(t, empty));

                arp.keySet()
                    .stream()
                    .filter(t -> (! t.isMulticastAddress()))
                    .forEach(t -> putIfAbsent(t, empty));

                ssdp.values()
                    .stream()
                    .map(SSDP.Value::getSSDPMessage)
                    .filter(t -> t instanceof SSDPResponse)
                    .map(t -> ((SSDPResponse) t).getInetAddress())
                    .forEach(t -> putIfAbsent(t, empty));

                keySet()
                    .stream()
                    .filter(t -> INTERVAL.compareTo(getOutputAge(t)) < 0)
                    .map(Worker::new)
                    .forEach(t -> executor.execute(t));
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
        }
    }
    ...
    private Duration getOutputAge(InetAddress key) {
        long start = 0;
        Number number = (Number) get(key, "/nmaprun/runstats/finished/@time", NUMBER);

        if (number != null) {
            start = number.longValue();
        }

        return Duration.between(Instant.ofEpochSecond(start), Instant.now());
    }

    private Object get(InetAddress key, String expression, QName qname) {
        Object object = null;
        Document document = get(key);

        if (document != null) {
            try {
                object = xpath.compile(expression).evaluate(document, qname);
            } catch (Exception exception) {
                log.error(exception.getMessage(), exception);
            }
        }

        return object;
    }
    ...
}
```

[Spring Boot]'s [`ThreadPoolTaskExecutor`][ThreadPoolTaskExecutor] is
injected.  To guarantee more than one thread is allocated the
[`application.properties`](https://github.com/allen-ball/voyeur/blob/master/src/main/resources/application.properties)
contains the following property:

```properties
spring.task.scheduling.pool.size: 4
```

The `Worker` implementation is given below.

```java
    ...
    private static final List<String> NMAP_ARGV =
        Stream.of(NMAP, "--no-stylesheet", "-oX", "-", "-n", "-PS", "-A")
        .collect(toList());
    ...
    @RequiredArgsConstructor @EqualsAndHashCode @ToString
    private class Worker implements Runnable {
        private final InetAddress key;

        @Override
        public void run() {
            try {
                List<String> argv = NMAP_ARGV.stream().collect(toList());

                if (key instanceof Inet4Address) {
                    argv.add("-4");
                } else if (key instanceof Inet6Address) {
                    argv.add("-6");
                }

                argv.add(key.getHostAddress());

                DocumentBuilder builder = factory.newDocumentBuilder();
                Process process =
                    new ProcessBuilder(argv)
                    .inheritIO()
                    .redirectOutput(PIPE)
                    .start();

                try (InputStream in = process.getInputStream()) {
                    put(key, builder.parse(in));

                    int status = process.waitFor();

                    if (status != 0) {
                        throw new IOException(argv + " returned exit status " + status);
                    }
                }
            } catch (Exception exception) {
                remove(key);
                log.error(exception.getMessage(), exception);
            }
        }
    }
    ...
```

Note that the [`InetAddress`][InetAddress] will be removed from the
[`Map`][Map] if the [`Process`][Process] fails.


### UI @Controller, Model, and Thymeleaf Template

The complete [`UIController`][voyeur.UIController] implementation is given
below.

```java
@Controller
@NoArgsConstructor @ToString @Log4j2
public class UIController extends AbstractController {
    @Autowired private SSDP ssdp = null;
    @Autowired private NetworkInterfaces interfaces = null;
    @Autowired private ARPCache arp = null;
    @Autowired private Nmap nmap = null;

    @ModelAttribute("upnp")
    public Map<URI,List<URI>> upnp() {
        Map<URI,List<URI>> map =
            ssdp().values()
            .stream()
            .map(SSDP.Value::getSSDPMessage)
            .collect(groupingBy(SSDPMessage::getLocation,
                                ConcurrentSkipListMap::new,
                                mapping(SSDPMessage::getUSN, toList())));

        return map;
    }

    @ModelAttribute("ssdp")
    public SSDP ssdp() { return ssdp; }

    @ModelAttribute("interfaces")
    public NetworkInterfaces interfaces() { return interfaces; }

    @ModelAttribute("arp")
    public ARPCache arp() { return arp; }

    @ModelAttribute("nmap")
    public Nmap nmap() { return nmap; }

    @RequestMapping(value = {
                        "/",
                        "/upnp/devices", "/upnp/ssdp",
                        "/network/interfaces", "/network/arp", "/network/nmap"
                    })
    public String root(Model model) { return getViewName(); }

    @RequestMapping(value = { "/index", "/index.htm", "/index.html" })
    public String index() { return "redirect:/"; }
}
```

The [`@Controller`][Controller] populates the [`Model`][Model] with five
attributes and implements the
[`root`](https://allen-ball.github.io/voyeur/voyeur/UIController.html#root-org.springframework.ui.Model-)
method<sup id="ref1">[1](#endnote1)</sup> to serve the UI request paths.
The
[superclass]({{ page.javadoc.ball-api }}/ball/spring/AbstractController.html?is-external=true)
implements
[`getViewName()`]({{ page.javadoc.ball-api }}/ball/spring/AbstractController.html#getViewName--)
which creates a view name based on the implementing class's package which
translates to
[classpath:/templates/voyeur.html](https://github.com/allen-ball/voyeur/blob/master/src/main/resources/templates/voyeur.html),
a [Thymeleaf] template to generate a pure HTML5 document.  Its outline is
shown below.

```xml
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:xmlns="@{http://www.w3.org/1999/xhtml}">
  <head>
    ...
  </head>
  <body>
    ...
    <header>
      <nav th:ref="navbar">
        ...
      </nav>
    </header>
    <main th:unless="${#ctx.containsVariable('exception')}"
          th:switch="${#request.servletPath}">
      <section th:case="'/error'">
        ...
      </section>
      <section th:case="'/upnp/devices'">
        ...
      </section>
      <section th:case="'/upnp/ssdp'">
        ...
      </section>
      ...
    </main>
    <main th:if="${#ctx.containsVariable('exception')}">
      ...
    </main>
    <footer>
      <nav th:ref="navbar">
        ...
      </nav>
    </footer>
    <script/>
  </body>
</html>
```

The template's `<header/>` `<nav/>` implements the menu and references the
paths specified in the `UIController.root()`.

```xml
      <nav th:ref="navbar">
        <th:block th:ref="container">
          <div th:ref="navbar-brand">
            <a th:text="${#strings.defaultString(brand, 'Home')}" th:href="@{/}"/>
          </div>
          <div th:ref="navbar-menu">
            <ul th:ref="navbar-start"></ul>
            <ul th:ref="navbar-end">
              <li th:ref="navbar-item">
                <button th:text="'UPNP'"/>
                <ul th:ref="navbar-dropdown">
                  <li><a th:text="'Devices'" th:href="@{/upnp/devices}"/></li>
                  <li><a th:text="'SSDP'" th:href="@{/upnp/ssdp}"/></li>
                </ul>
              </li>
              <li th:ref="navbar-item">
                <button th:text="'Network'"/>
                <ul th:ref="navbar-dropdown">
                  <li><a th:text="'Interfaces'" th:href="@{/network/interfaces}"/></li>
                  <li><a th:text="'ARP'" th:href="@{/network/arp}"/></li>
                  <li><a th:text="'Nmap'" th:href="@{/network/nmap}"/></li>
                </ul>
              </li>
            </ul>
          </div>
        </th:block>
      </nav>
```

The template is also structured to produce a `<main/>` node with a
`<section/>` node corresponding to the request path if there is no
`exception` variable in the context (normal operation).  The `th:switch` and
`th:case` attributes are used to create a `<section/>` corresponding to each
`${#request.servletPath}`.  The `<section/>` specific to the `/network/nmap`
path is shown below:

```xml
    <main th:unless="${#ctx.containsVariable('exception')}"
          th:switch="${#request.servletPath}">
      ...
      <section th:case="'/network/nmap'">
        <table>
          <tbody>
            <tr th:each="key : ${nmap.keySet()}">
              <td>
                <a th:href="@{/network/nmap/{ip}.xml(ip=${key.hostAddress})}" th:target="_newtab">
                  <code th:text="${key.hostAddress}"/>
                </a>
                <p><code th:text="${nmap.getPorts(key)}"/></p>
              </td>
              <td>
                <p th:each="product : ${nmap.getProducts(key)}" th:text="${product}"/>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
      ...
    </main>
```

The template generates a `<table/>` with a row (`<tr/>`) for each key in the
`Nmap`.  Each row consists of two columns (`<td/>`):

1. The [`InetAddress`][InetAddress] of the host with a link to the `nmap`
command output<sup id="ref2">[2](#endnote2)</sup> and a list of open TCP
ports

2. The services/products detected

The [`getPorts(InetAddress)`][voyeur.Nmap.getPorts] and
[`getProducts(InetAddress)`][voyeur.Nmap.getProducts] methods are provided
to avoid [`XPath`][XPath] calculations within the [Thymeleaf] template.

```java
...
@Service
@NoArgsConstructor @Log4j2
public class Nmap extends InetAddressMap<Document> ... {
    ...
    public Set<Integer> getPorts(InetAddress key) {
        Set<Integer> ports = new TreeSet<>();
        NodeList list = (NodeList) get(key, "/nmaprun/host/ports/port/@portid", NODESET);

        if (list != null) {
            for (int i = 0; i < list.getLength(); i += 1) {
                ports.add(Integer.parseInt(list.item(i).getNodeValue()));
            }
        }

        return ports;
    }
    ...
}
```

The [`getProducts(InetAddress)`][voyeur.Nmap.getProducts] implementation is
similar with an [`XPathExression`][XPathExression] of
`/nmaprun/host/ports/port/service/@product`.

The [`UIController`][voyeur.UIController] instance combined with the
[Thymeleaf] template described so far will only generate pure HTML5 with no
style markup.  This implementation uses Thymeleaf's [Decoupled Template
Logic] feature and can be found at
[classpath:/templates/voyeur.th.xml](https://github.com/allen-ball/voyeur/blob/master/src/main/resources/templates/voyeur.th.xml).<sup id="ref3">[3](#endnote3)</sup>
The decoupled logic for the table described
in this section is shown below.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<thlogic>
  ...
  <attr sel="body">
    ...
    <attr sel="main" th:class="'container'">
      <attr sel="table" th:class="'table table-striped'">
        <attr sel="tbody">
          <attr sel="tr" th:class="'row'"/>
          <attr sel="tr/td" th:class="'col'"/>
        </attr>
      </attr>
    </attr>
    ...
  </attr>
</thlogic>
```

The [`UIController`][voyeur.UIController] superclass provides one more
feature: To inject the proprties defined in
[classpath:/templates/voyeur.model.properties](https://github.com/allen-ball/voyeur/blob/master/src/main/resources/templates/voyeur.model.properties)
into the [`Model`][Model].

```properties
brand = ${application.brand:}

stylesheets: /webjars/bootstrap/css/bootstrap.css
style:\
body { padding-top: 60px; margin-bottom: 60px; }\n\
@media (max-width: 979px) { body { padding-top: 0px; } }
scripts: /webjars/jquery/jquery.js, /webjars/bootstrap/js/bootstrap.js
```

The design goal of this implementation was to commit all markup logic to the
`*.th.xml` resource allowing only the necessity to modify the decoupled
logic and the model properties to use an alternate framework.  This goal was
defeated in this implementation because different frameworks support to
different degrees HTML5 elements.  A partial Bulma implementation is
available in
https://github.com/allen-ball/voyeur/tree/master/src/main/resources/templates-bulma
which demonstrates the HTML5 differences.

The `/network/nmap` request is shown rendered in the image in the
[Introduction](#introduction) of this article.


### nmap Output @RestController

`nmap` XML output can be served by implementing a
[`@RestController`][RestController].  The `Nmap` class is annotated with
`@RestController` and [`@RequestMapping`][RequestMapping] to requests for
`/network/nmap/` and the
[`nmap(String)`](https://allen-ball.github.io/voyeur/voyeur/Nmap.html#nmap-java.lang.String-)
method provides the XML serialized to a String.

```java
@RestController
@RequestMapping(value = { "/network/nmap/" }, produces = MediaType.APPLICATION_XML_VALUE)
...
public class Nmap ... {
    ...
    @RequestMapping(value = { "{ip}.xml" })
    public String nmap(@PathVariable String ip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        transformer.transform(new DOMSource(get(InetAddress.getByName(ip))),
                              new StreamResult(out));

        return out.toString("UTF-8");
    }
    ...
}
```


### Packaging

The [`spring-boot-maven-plugin`][spring-boot-maven-plugin] has a `repackage`
goal which may be used to create a self-contained JAR with an embedded
launch script.  That goal is used in the project
[`pom`](https://github.com/allen-ball/voyeur/blob/master/pom.xml) to create
and attach a self-contained JAR atifact.

```xml
<project ...>
  ...
  <build>
    <pluginManagement>
      <plugins>
        ...
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <executions>
            <execution>
              <goals>
                <goal>build-info</goal>
                <goal>repackage</goal>
              </goals>
            </execution>
          </executions>
          <configuration>
            <attach>true</attach>
            <classifier>bin</classifier>
            <executable>true</executable>
            <mainClass>${start-class}</mainClass>
            <embeddedLaunchScriptProperties>
              <inlinedConfScript>${basedir}/src/bin/inline.conf</inlinedConfScript>
            </embeddedLaunchScriptProperties>
          </configuration>
        </plugin>
        ...
      </plugins>
    </pluginManagement>
    ...
  </build>
  ...
</project>
```

Please see the project GitHub
[page](https://github.com/allen-ball/voyeur) for instructions
on how to run the JAR.


## Summary

This article discusses aspects of the
[`voyeur`](https://github.com/allen-ball/voyeur) application
and provides specific examples of:

* `@Service` implementation and `@Autowired` components with `@Scheduled`
  methods

* `@Controller` implementation, [`Model`][Model] population, and [Thymeleaf]
  templates and decoupled logic

* `@RestController` implementation


<b id="endnote1">[1]</b>
A misleading method name at best.
[↩](#ref1)

<b id="endnote2">[2]</b>
The `@RestController` is described in the next subsection.
[↩](#ref2)

<b id="endnote3">[3]</b>
The [common application properties] do not provide an option to enable this
functionality.  It is enabled in the `UIController` suprclass by configuring
the injected `SpringResourceTemplateResolver`.
[↩](#ref3)


[InetAddress]: {{ page.javadoc.javase }}/java/net/InetAddress.html?is-external=true
[Map]: {{ page.javadoc.javase }}/java/util/Map.html
[NetworkInterface]: {{ page.javadoc.javase }}/java/net/NetworkInterface.html?is-external=true
[Process]: {{ page.javadoc.javase }}/java/lang/Process.html
[Set]: {{ page.javadoc.javase }}/java/util/Set.html
[ThreadPoolTaskExecutor]: {{ page.javadoc.spring-framework }}/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
[XPath]: {{ page.javadoc.javase }}/javax/xml/xpath/XPath.html
[XPathExression]: {{ page.javadoc.javase }}/javax/xml/xpath/XPathExpression.html

[PostConstruct]: https://javaee.github.io/javaee-spec/javadocs/javax/annotation/PostConstruct.html?is-external=true
[PreDestroy]: https://javaee.github.io/javaee-spec/javadocs/javax/annotation/PreDestroy.html?is-external=true

[Spring Boot]: https://spring.io/projects/spring-boot
[spring-boot-maven-plugin]: https://docs.spring.io/spring-boot/docs/2.4.5/maven-plugin/reference/html/
[common application properties]: https://docs.spring.io/spring-boot/docs/2.4.5/reference/html/appendix-application-properties.html

[Autowired]: {{ page.javadoc.spring-framework }}/org/springframework/beans/factory/annotation/Autowired.html
[Controller]: {{ page.javadoc.spring-framework }}/org/springframework/stereotype/Controller.html
[EventListener]: {{ page.javadoc.spring }}/org/springframework/context/event/EventListener.html?is-external=true
[Model]: {{ page.javadoc.spring }}/org/springframework/ui/Model.html
[RequestMapping]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RequestMapping.html
[RestController]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RestController.html
[Scheduled]: {{ page.javadoc.spring }}/org/springframework/scheduling/annotation/Scheduled.html?is-external=true
[Scheduled]: {{ page.javadoc.spring }}/org/springframework/scheduling/annotation/Scheduled.html?is-external=true
[Service]: {{ page.javadoc.spring }}/org/springframework/stereotype/Service.html?is-external=true

[ApplicationReadyEvent]: {{ page.javadoc.spring-boot }}/org/springframework/boot/context/event/ApplicationReadyEvent.html?is-external=true)

[Thymeleaf]: https://www.thymeleaf.org
[Decoupled Template Logic]: https://www.thymeleaf.org/doc/tutorials/3.0/usingthymeleaf.html#decoupled-template-logic

[nmap]: https://nmap.org/

[SSDP]: https://tools.ietf.org/id/draft-cai-ssdp-v1-03.txt

[UPNP]: https://openconnectivity.org/developer/specifications/upnp-resources/upnp-developer-resources

[SSDPDiscoveryCache]: {{ page.javadoc.ball-api }}/ball/upnp/ssdp/SSDPDiscoveryCache.html?is-external=true

[voyeur]: https://allen-ball.github.io/voyeur/voyeur/package-summary.html
[voyeur.ArpCache]: https://allen-ball.github.io/voyeur/voyeur/ARPCache.html
[voyeur.NetworkInterfaces]: https://allen-ball.github.io/voyeur/voyeur/NetworkInterfaces.html
[voyeur.Nmap]: https://allen-ball.github.io/voyeur/voyeur/Nmap.html
[voyeur.Nmap.getPorts]: https://allen-ball.github.io/voyeur/voyeur/Nmap.html#getPorts-java.net.InetAddress-
[voyeur.Nmap.getProducts]: https://allen-ball.github.io/voyeur/voyeur/Nmap.html#getProducts-java.net.InetAddress-
[voyeur.Nmap.update]: https://allen-ball.github.io/voyeur/voyeur/Nmap.html#update--
[voyeur.SSDP]: https://allen-ball.github.io/voyeur/voyeur/SSDP.html
[voyeur.UIController]: https://allen-ball.github.io/voyeur/voyeur/UIController.html
