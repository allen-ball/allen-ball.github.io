---
title: Ganymede Kernel 1.1.0.20210614 Released
tags:
 - Java
 - Jupyter
release:
  tag: >-
    https://github.com/allen-ball/ganymede/releases/tag/v1.1.0.20210614
  download: >-
    https://github.com/allen-ball/ganymede/releases/download/v1.1.0.20210614
excerpt_separator: <!--more-->
---

[Ganymede Kernel] 1.1.0.20210614 is released.

The [Ganymede Kernel] is a [Jupyter Notebook] Java [kernel][Jupyter Kernel] based on [JShell] combined with an integrated [Apache Maven]-like POM, support for JVM languages such as [Groovy], [Javascript], and [Kotlin], and support for [Apache Spark] and [Scala] binary distributions.

<!--more-->

This release adds support for:

* [Markdown] ([CommonMark]) with [Handlebars][Handlebars.java], [FreeMarker][Apache FreeMarker], and [Velocity][Apache Velocity]) templating languages in addition to [Thymeleaf] templates
* [Apache Spark] 3.1.2
* [Rendering][Renderer] [TableModel]s

Binary and source downloads are available from <https://github.com/allen-ball/ganymede/releases/tag/v1.1.0.20210614>.

Quickstart if Java 11 (or later) and [Jupyter][Jupyter Notebook] are already installed:

```bash
$ curl -sL https://github.com/allen-ball/ganymede/releases/download/v1.1.0.20210614/ganymede-kernel-1.1.0.20210614.jar -o ganymede-kernel.jar
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
[Handlebars.java]: https://github.com/jknack/handlebars.java
[JShell]: https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jshell/jdk/jshell/JShell.html?is-external=true
[Javascript]: https://www.oracle.com/technical-resources/articles/java/jf14-nashorn.html
[Jupyter Kernel]: https://jupyter-client.readthedocs.io/en/stable/kernels.html
[Jupyter Notebook]: https://jupyter-notebook.readthedocs.io/en/stable/index.html
[Kotlin]: https://kotlinlang.org/
[Markdown]: https://en.wikipedia.org/wiki/Markdown
[Renderer]: https://allen-ball.github.io/ganymede/ganymede/server/Renderer.html
[Scala]: https://www.scala-lang.org/
[TableModel]: https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/table/TableModel.html?is-external=true
[Thymeleaf]: https://www.thymeleaf.org/index.html
