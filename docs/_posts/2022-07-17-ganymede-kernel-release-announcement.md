---
title: Ganymede Kernel 2.0.0.20220717 Released
tags:
 - Java
 - Jupyter
release:
  tag: >-
    https://github.com/allen-ball/ganymede/releases/tag/v2.0.0.20220717
  download: >-
    https://github.com/allen-ball/ganymede/releases/download/v2.0.0.20220717
excerpt_separator: <!--more-->
---

[Ganymede Kernel] 2.0.0.20220717 is released.

The [Ganymede Kernel] is a [Jupyter Notebook] Java [kernel][Jupyter Kernel] based on [JShell] combined with an integrated [Apache Maven]-like POM, support for JVM languages such as [Groovy], [Javascript], and [Kotlin], and support for [Apache Spark] and [Scala] binary distributions.

<!--more-->

This release improves the [Ganymede Kernel]'s robustness by introducing the Ganymede Kernel REST protocol to communicate between the kernel and the [JShell] instance.

This release adds support for:

* The `%%java` magic with no code will display the context with pointers to the respective type's javadoc

* New magics: `mustache` (`handlebars`), `spark-session`, and `sql`

* New renderers for [jOOQ] Formattable and [Tablesaw] Table

* Mustache scripting with [JMustache]

* Updated the Apache Maven Resolver to 1.8.1 and support the use of "LATEST" in the POM artifact version specification

* Added Ganymede and JDK javadoc to help links

* Support Apache Spark and Hive 3.1.3 with runtime support for Hive

* Use PicoCLI for magic command line parsing and improved usage messages

Binary and source downloads are available from <https://github.com/allen-ball/ganymede/releases/tag/v2.0.0.20220717>.

Quickstart if Java 11 (or later) and [Jupyter][Jupyter Notebook] are already installed:

```bash
$ curl -sL https://github.com/allen-ball/ganymede/releases/download/v2.0.0.20220717/ganymede-2.0.0.20220717.jar -o ganymede-kernel.jar
$ java -jar ganymede-kernel.jar --install
```

Please see the project [page][Ganymede Kernel] for detailed [installation][Ganymede Kernel installation] instructions and its [features and usage][Ganymede Kernel usage].


[Apache FreeMarker]: https://freemarker.apache.org/
[Apache Maven]: https://maven.apache.org/
[Apache Spark]: http://spark.apache.org/
[Apache Velocity]: https://velocity.apache.org/
[CommonMark]: https://commonmark.org/
[Ganymede Kernel installation]: https://github.com/allen-ball/ganymede#installation
[Ganymede Kernel usage]: https://github.com/allen-ball/ganymede#features-and-usage
[Ganymede Kernel]: https://github.com/allen-ball/ganymede
[Groovy]: https://groovy-lang.org/
[JMustache]: https://github.com/samskivert/jmustache
[JShell]: https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jshell/jdk/jshell/JShell.html?is-external=true
[Javascript]: https://www.oracle.com/technical-resources/articles/java/jf14-nashorn.html
[Jupyter Kernel]: https://jupyter-client.readthedocs.io/en/stable/kernels.html
[Jupyter Notebook]: https://jupyter-notebook.readthedocs.io/en/stable/index.html
[Kotlin]: https://kotlinlang.org/
[Markdown]: https://en.wikipedia.org/wiki/Markdown
[Scala]: https://www.scala-lang.org/
[Tablesaw]: https://github.com/jfree/jfreechart
[Thymeleaf]: https://www.thymeleaf.org/index.html
[jOOQ]: https://www.jooq.org/doc/latest/manual
