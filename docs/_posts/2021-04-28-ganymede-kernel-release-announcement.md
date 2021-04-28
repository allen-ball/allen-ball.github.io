---
title: Ganymede Kernel 1.0.0.20210422 Released
tags:
 - Java
 - Jupyter
release:
  tag: >-
    https://github.com/allen-ball/ganymede/releases/tag/v1.0.0.20210422
  download: >-
    https://github.com/allen-ball/ganymede/releases/download/v1.0.0.20210422
excerpt_separator: <!--more-->
---

[Ganymede Kernel] 1.0.0.20210422 is released.

The [Ganymede Kernel] is a [Jupyter Notebook] Java [kernel][Jupyter Kernel]
based on [JShell] combined with an integrated [Apache Maven]-like POM,
support for JVM languages such as [Groovy], [Javascript], and [Kotlin], and
support for [Apache Spark] and [Scala] binary distributions.

<!--more-->

Binary and source downloads are available from <{{ page.release.tag }}>.

Quickstart if Java 11 (or later) and [Jupyter][Jupyter Notebook] are already
installed:

```bash
$ curl -L {{ page.release.download }}/ganymede-kernel-1.0.0.20210422.jar > ganymede-kernel.jar
$ java -jar ganymede-kernel.jar --install
```

Please see the project [page][Ganymede Kernel] for detailed
[installation][Ganymede Kernel installation] instructions and its
[features and usage][Ganymede Kernel usage].


[Apache Maven]: https://maven.apache.org/
[Apache Spark]: http://spark.apache.org/
[Ganymede Kernel]: https://github.com/allen-ball/ganymede
[Ganymede Kernel installation]: https://github.com/allen-ball/ganymede#installation
[Ganymede Kernel usage]: https://github.com/allen-ball/ganymede#features-and-usage
[Groovy]: https://groovy-lang.org/
[JShell]: https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jshell/jdk/jshell/JShell.html?is-external=true
[Javascript]: https://www.oracle.com/technical-resources/articles/java/jf14-nashorn.html
[Jupyter Kernel]: https://jupyter-client.readthedocs.io/en/stable/kernels.html
[Jupyter Notebook]: https://jupyter-notebook.readthedocs.io/en/stable/index.html
[Kotlin]: https://kotlinlang.org/
[Scala]: https://www.scala-lang.org/
