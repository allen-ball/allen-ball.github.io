---
title: >-
    Spring Boot Part 4: Spring MVC with Thymeleaf
canonical_url: https://blog.hcf.dev/article/2020-01-01-spring-boot-part-04
tags:
 - Java
 - Spring
 - MVC
 - Thymeleaf
permalink: article/2020-01-01-spring-boot-part-04
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
  spring: >-
    https://docs.spring.io/spring/docs/5.3.6/javadoc-api
  spring-boot: >-
    https://docs.spring.io/spring-boot/docs/2.4.5/api
  spring-security: >-
    https://docs.spring.io/spring-security/site/docs/5.4.6/api
---

This series of articles will examine [Spring Boot] features.  This fourth
installment discusses [Spring MVC], templating in Spring, and creates a
simple internationalized clock application as an example.  The clock
application will allow the user to select [`Locale`][Locale] and
[`TimeZone`][TimeZone].

Complete source code for the
[series](https://github.com/allen-ball/spring-boot-web-server)
and for this
[part](https://github.com/allen-ball/spring-boot-web-server/tree/trunk/part-04)
are available on [Github](https://github.com/allen-ball).


## Theory of Operation

The *Controller* will provide methods to service `GET` and `POST` requests
at `/clock/time` and update the
[`Model`]({{ page.javadoc.spring }}/org/springframework/ui/Model.html)
with:

| Attribute Name | Type                                                                          |
| ---            | ---                                                                           |
| locale         | User-selected [`Locale`][Locale]                                              |
| zone           | User-selected [`TimeZone`][TimeZone]                                          |
| timestamp      | Current [`Date`][Date]                                                        |
| date           | [`DateFormat`][DateFormat] to display date (based on `Locale` and `TimeZone`) |
| time           | `DateFormat` to display time (based on `Locale` and `TimeZone`                |
| locales        | A sorted [`List`][List] of `Locale`s the user may select                      |
| zones          | A sorted `List` of `TimeZone`s the user may select                            |

The *View* will use [Thymeleaf] technology which may be included with a
reasonable configuration simply by including the corresponding "starter" in
the POM.  The developer's primary responsibility is write the corresponding
Thymeleaf template.

For this project, [Bootstrap] is used as the CSS
Framework.<sup id="ref1">[1](#endnote1)</sup>

The required dependencies are included in the
[`pom.xml`](https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-04/pom.xml):

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/maven-v4_0_0.xsd">
  ...
  <dependencies verbose="true">
    ...
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    ...
    <dependency>
      <groupId>org.webjars</groupId>
      <artifactId>webjars-locator-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.webjars</groupId>
      <artifactId>bootstrap</artifactId>
      <version>4.4.1</version>
    </dependency>
    <dependency>
      <groupId>org.webjars</groupId>
      <artifactId>jquery</artifactId>
      <version>3.4.1</version>
    </dependency>
    <dependency>
      <groupId>org.webjars</groupId>
      <artifactId>popper.js</artifactId>
      <version>1.15.0</version>
    </dependency>
  </dependencies>
  ...
</project>
```

The following subsections describe the *View*, *Controller*, and `Model`.


### View

The *Controller* will serve requests at `/clock/time` and the Thymeleaf
`ViewResolver` (with the default configuration) will look for the
corresponding template at
[`classpath:/templates/clock/time.html`](https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-04/src/main/resources/templates/clock/time.html)
(note the `/templates/` superdirectory and the `.html` suffix).  The
template with the `<main/>` element is shown below.  The XML Namespace "th"
is defined for Thymeleaf and a number of "`th:*`" attributes are used.  For
example, the Bootstrap artifact paths are wrapped in "`th:href`" and
"`th:src`" attributes with values expressed in Thymeleaf standard expression
syntax.

``` html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:xmlns="@{http://www.w3.org/1999/xhtml}">
  <head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width,initial-scale=1,shrink-to-fit=no"/>
    <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.css}"/>
  </head>
  <body>
    <!--[if lte IE 9]>
      <p class="browserupgrade" th:utext="#{browserupgrade}"/>
    <![endif]-->
    <main class="container">
      ...
    </main>
    <script th:src="@{/webjars/jquery/jquery.js}" th:text="''"/>
    <script th:src="@{/webjars/bootstrap/js/bootstrap.js}" th:text="''"/>
  </body>
</html>
```

The following shows the template's rendered `HTML`.  The Bootstrap
artifacts' paths have been rendered "`href`" and "`src`" attributes (with
version handling in their paths as decribed in
[part 2](/article/2019-11-17-spring-boot-part-02)) and with
the Thymeleaf expressions evaluated.

``` html
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width,initial-scale=1,shrink-to-fit=no"/>
    <link rel="stylesheet" href="/webjars/bootstrap/4.4.1/css/bootstrap.css"/>
  </head>
  <body>
    <!--[if lte IE 9]>
      <p class="browserupgrade">You are using an <strong>outdated</strong> browser.  Please <a href="https://browsehappy.com/">upgrade your browser</a> to improve your experience and security.</p>
    <![endif]-->
    <main class="container">
      ...
    </main>
    <script src="/webjars/jquery/3.4.1/jquery.js"></script>
    <script src="/webjars/bootstrap/4.4.1/js/bootstrap.js"></script>
  </body>
</html>
```

Spring provides a message catalog facility which allows `<p
class="browserupgrade" th:utext="#{browserupgrade}"/>` to be evaluated from
the
[`message.properties`](https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-04/src/main/resources/messages.properties).
The [Tutorial: Thymeleaf + Spring] provides a reference for these and other
features.  [Tutorial: Using Thymeleaf] provides the reference for standard
expression syntax, the available "`th:*`" attributes and elements, and
available expression objects.

The initial implementation of the clock *View* is:

``` html
    ...
    <main class="container">
      <div class="jumbotron">
        <h1 class="text-center" th:text="${time.format(timestamp)}"/>
        <h1 class="text-center" th:text="${date.format(timestamp)}"/>
      </div>
    </main>
    ...
```

It output will be discussed in detail in the next section after discussing
the *Controller* implementation and the population of the `Model` but note
that the *View* requires the `Model` provide the `time`, `date`, and
`timestamp` attributes as laid out above.


### Controller and Model

The *Controller* is implemented by a class annotated with
[`@Controller`][Controller],
[`ClockController`](https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-04/src/main/java/application/ClockController.java).
The implementation of the `GET` `/clock/time` is outlined below:

``` java
@Controller
@RequestMapping(value = { "/clock/" })
@NoArgsConstructor @ToString @Log4j2
public class ClockController {
    ...
    @RequestMapping(method = { RequestMethod.GET }, value = { "time" })
    public void get(Model model, Locale locale, TimeZone zone) {
        model.addAttribute("locale", locale);
        model.addAttribute("zone", zone);

        DateFormat date = DateFormat.getDateInstance(DateFormat.LONG, locale);
        DateFormat time = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale);

        model.addAttribute("date", date);
        model.addAttribute("time", time);

        for (Object object : model.asMap().values()) {
            if (object instanceof DateFormat) {
                ((DateFormat) object).setTimeZone(zone);
            }
        }

        Date timestamp = new Date();

        model.addAttribute("timestamp", timestamp);
    }
    ...
}
```

The parameters are `Model`, `Locale`, and `TimeZone`, all injected by
Spring.  A complete list of available method parameters and return types
with their respective semantics, may be found at [Handler Methods].

The method updates the `Model` with the user `Locale` and `TimeZone`, the
current timestamp, and the time and date `DateFormat` to render the clock
display.  Since the method returns `void`, the view resolves to the
Thymeleaf template at `classpath:/templates/clock/time.html` (as described
above).  Alternatively, the method may return a [`String`][String] with a
name (path) of a template.  Spring then evaluates the template with the
`Model` for the output which results in:

``` html
    ...
    <main class="container">
      <div class="jumbotron">
        <h1 class="text-center">11:59:59 AM</h1>
        <h1 class="text-center">December 24, 2019</h1>
      </div>
    </main>
    ...
```

which renders to:

![](/assets/{{ page.permalink }}/screen-shot-1.png)

Of course, this implementation does not yet allow the user to customize
their `Locale` or `TimeZone`.  The next section adds this functionality.


### Adding User Customization

To allow user customization, first a form allowing the user to select
`Locale` and `TimeZone` must be added.

``` html
    ...
    <main class="container">
      ...
      <form class="row" method="post" th:action="${#request.servletPath}">
        <select class="col-lg" name="languageTag">
          <option th:each="option : ${locales}"
                  th:with="selected = ${option.equals(locale)},
                           value = ${option.toLanguageTag()},
                           display = ${option.getDisplayName(locale)},
                           text = ${value + ' - ' + display}"
                  th:selected="${selected}" th:value="${value}" th:text="${text}"/>
        </select>
        <select class="col-lg" name="zoneID">
          <option th:each="option : ${zones}"
                  th:with="selected = ${option.equals(zone)},
                           value = ${option.ID},
                           display = ${option.getDisplayName(locale)},
                           text = ${value + ' - ' + display}"
                  th:selected="${selected}" th:value="${value}" th:text="${text}"/>
        </select>
        <button class="col-sm-1" type="submit" th:text="'&#8635;'"/>
      </form>
    </main>
    ...
```

Two attributes must be added to the `Model` by the `GET` `/clock/time`
method, the `List`s of `Locale`s and `TimeZone`s from which the user may
select.<sup id="ref2">[2](#endnote2)</sup>

``` java
    private static final List<Locale> LOCALES =
        Stream.of(Locale.getAvailableLocales())
        .filter(t -> (! t.toString().equals("")))
        .collect(Collectors.toList());
    private static final List<TimeZone> ZONES =
        Stream.of(TimeZone.getAvailableIDs())
        .map(t -> TimeZone.getTimeZone(t))
        .collect(Collectors.toList());

    @RequestMapping(method = { RequestMethod.GET }, value = { "time" })
    public void get(Model model, Locale locale, TimeZone zone, ...) {
        ...
        Collator collator = Collator.getInstance(locale);
        List<Locale> locales =
            LOCALES.stream()
            .sorted(Comparator.comparing(Locale::toLanguageTag, collator))
            .collect(Collectors.toList());
        List<TimeZone> zones =
            ZONES.stream()
            .sorted(Comparator
                    .comparingInt(TimeZone::getRawOffset)
                    .thenComparingInt(TimeZone::getDSTSavings)
                    .thenComparing(TimeZone::getID, collator))
            .collect(Collectors.toList());

        model.addAttribute("locales", locales);
        model.addAttribute("zones", zones);
        ...
    }
```

Key to the implementation is the use of the "`th:each`" attribute where the
node is evaluated each member of the [`List`][List].  The "`th:with`"
attribute allows variables to be defined and referenced within the scope of
the corresponding node.  Partial output is shown below.

``` html
    ...
    <main class="container">
      ...
      <form class="row" method="post" action="/clock/time">
        <select class="col-lg" name="languageTag">
          <option value="ar">ar - Arabic</option>
          <option value="ar-AE">ar-AE - Arabic (United Arab Emirates)</option>
          ...
          <option value="en-US" selected="selected">en-US - English (United States)</option>
          ...
          <option value="zh-TW">zh-TW - Chinese (Taiwan)</option>
        </select>
        <select class="col-lg" name="zoneID">
          <option value="Etc/GMT+12">Etc/GMT+12 - GMT-12:00</option>
          <option value="Etc/GMT+11">Etc/GMT+11 - GMT-11:00</option>
          ...
          <option value="PST8PDT" selected="selected">PST8PDT - Pacific Standard Time</option>
          ...
          <option value="Pacific/Kiritimati">Pacific/Kiritimati - Line Is. Time</option>
        </select>
        <button class="col-sm-1" type="submit">↻</button>
      </form>
    </main>
    ...
```

The updated *View* provides to selection lists and a form `POST` button:

![](/assets/{{ page.permalink }}/screen-shot-2.png)

![](/assets/{{ page.permalink }}/screen-shot-3.png)

![](/assets/{{ page.permalink }}/screen-shot-4.png)

A new method is added to the *Controller* to handle the `POST` `/clock/time`
request.  Note the [`HttpServletRequest`][HttpServletRequest] and
[`HttpSession`][HttpSession] parameters.

``` java
    @RequestMapping(method = { RequestMethod.POST }, value = { "time" })
    public String post(HttpServletRequest request, HttpSession session,
                       @RequestParam Map<String,String> form) {
        for (Map.Entry<String,String> entry : form.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }

        return "redirect:" + request.getServletPath();
    }
```

The selected `Locale` `languageTag` and `TimeZone` `zoneID` are written to
the [`@RequestParam`][RequestParam]-annotated [`Map`][Map] with keys
`languageTag` and `zoneID`, respectively.  The `Map` key-value pairs are
written into the `HttpSession` (automatically managed by Spring)
attributes.<sup id="ref3">[3](#endnote3)</sup> Adding the prefix
"`redirect:`" instructs Spring to respond with a `302` to cause the browser
to make a request to the new URL: `GET` `/clock/time`.  That method must be
modified to set `Locale` based on the session `languageTag` and/or
`TimeZone` based on `zoneID` if specified (accessed via
[`@SessionAttribute`][SessionAttribute]).<sup id="ref4">[4](#endnote4)</sup>

``` java
    @RequestMapping(method = { RequestMethod.GET }, value = { "time" })
    public void get(Model model, Locale locale, TimeZone zone,
                    @SessionAttribute Optional<String> languageTag,
                    @SessionAttribute Optional<String> zoneID) {
        if (languageTag.isPresent()) {
            locale = Locale.forLanguageTag(languageTag.get());
        }

        if (zoneID.isPresent()) {
            zone = TimeZone.getTimeZone(zoneID.get());
        }

        model.addAttribute("locale", locale);
        model.addAttribute("zone", zone);
        ...
    }
```

A couple of alternative `Locale`s and `TimeZone`s:

![](/assets/{{ page.permalink }}/screen-shot-5.png)

![](/assets/{{ page.permalink }}/screen-shot-6.png)


## Summary

This article demonstrates Spring MVC with Thymeleaf templates by
implementing a simple, but internationalized, clock web application.


<b id="endnote1">[1]</b>
[Part 2](/article/2019-11-17-spring-boot-part-02) of
this series demonstrated the inclusion of Bulma artifacts and the use of
Bootstrap here is to provide contrast.
[↩](#ref1)

<b id="endnote2">[2]</b>
Arguably, the sorting of the lists should be part of the *View* and
included in the template but that would overly complicate the
implementation.
[↩](#ref2)

<b id="endnote3">[3]</b>
An alternative strategy would be to include the `POST` `@RequestParam`s as
query parameters in the redirected `URL`.
[↩](#ref3)

<b id="endnote4">[4]</b>
To avoid specifying the `@SessionAttribute` `name` attribute, the Java code
must be compiled with the `javac` `-parameters` option so the method
parameter names are available through reflection.  Please see the
configuration of the `maven-compiler-plugin` plug-in in the
[`pom.xml`](https://github.com/allen-ball/spring-boot-web-server/blob/trunk/part-04/pom.xml).
[↩](#ref4)


[DateFormat]: {{ page.javadoc.javase }}/java/text/DateFormat.html
[Date]: {{ page.javadoc.javase }}/java/util/Date.html
[List]: {{ page.javadoc.javase }}/java/util/List.html
[Map]: {{ page.javadoc.javase }}/java/util/Map.html
[Locale]: {{ page.javadoc.javase }}/java/util/Locale.html
[String]: {{ page.javadoc.javase }}/java/lang/String.html
[TimeZone]: {{ page.javadoc.javase }}/java/util/TimeZone.html

[HttpServletRequest]: https://docs.oracle.com/javaee/7/api/javax/servlet/http/HttpServletRequest.html
[HttpSession]: https://docs.oracle.com/javaee/7/api/javax/servlet/http/HttpSession.html

[Spring Boot]: https://spring.io/projects/spring-boot
[Spring MVC]: https://docs.spring.io/spring/docs/5.3.6/spring-framework-reference/web.html
[Handler Methods]: https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/web.html#mvc-ann-methods

[Controller]: {{ page.javadoc.spring }}/org/springframework/stereotype/Controller.html
[RequestParam]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/RequestParam.html
[SessionAttribute]: {{ page.javadoc.spring }}/org/springframework/web/bind/annotation/SessionAttribute.html

[Thymeleaf]: https://www.thymeleaf.org/
[Tutorial: Thymeleaf + Spring]: https://www.thymeleaf.org/doc/tutorials/3.0/thymeleafspring.html
[Tutorial: Using Thymeleaf]: https://www.thymeleaf.org/doc/tutorials/3.0/usingthymeleaf.html

[Bootstrap]: https://getbootstrap.com/
