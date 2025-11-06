---
title: Java Multi-Release JARs
canonical_url: https://blog.hcf.dev/article/2020-07-19-java-multi-release-jars
tags:
 - Java
permalink: article/2020-07-19-java-multi-release-jars
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
excerpt_separator: <!--more-->
---

["Adding Support to Java InvocationHandler Implementations for Interface Default Methods"](/article/2019-01-31-java-invocationhandler-interface-default-methods)
describes how to implement an [`InvocationHandler`][InvocationHandler] to
invoke `default` interface methods.  This mechanism is critical to the
[`FluentNode`][FluentNode] implementation described in
["Java Interface Facades"](/article/2019-03-30-java-interface-facades).
The first article also notes that the
[`MethodHandles.Lookup`][MethodHandles.Lookup] method used with [Java 8]
would not work with [Java 9] *which means the whole API will not work on
Java 9 and subsequent JVMs*.

This article describes the [Java 9]-specific solution, refactoring the
[`InvocationHandler`][InvocationHandler] implementation to separate and
compartmentalize the Java 8 and Java 9-specific solution logic, and
introduces ["JEP 238: Multi-Release JAR Files"][JEP 238] to deliver a
[Java 8] and Java 9 (and later) solutions simultaneously in the same JAR.

<!--more-->


## Theory of Operation

As described in [JEP 238], multi-release JARs provide a means to provide
alternate versions of classes that can take advantage of specific platform
features.  Alternate classes are stored in the JAR within the hierarchy
described by `/META-INF/versions/${java.specification.version}/`.  (The
implementation hierarchy is shown in detail at the end of this article.)
For archivers and class-loaders that are not multi-release aware (e.g.,
[Java 8]), these additional classes are ignore.  However, for [Java 9] and
subsequent environments, these additional classes are loaded if the
`version` is less than or equal to the JVM's `${java.specification.version}`
(with the latest taking precedence).

The class with the [Java 8]-specific code will be refactored to implement a
super-interface with a single method embodying that code.  That existing
code base will be compiled as-is to create a JAR suitable for Java 8 JVMs.
In addition, a [Java 9] version of the super-interface will be compiled for
a Java 9 environment and saved to the JAR beneath the
`/META-INF/versions/9/` hierarchy.


## Implementation

[`DefaultInvocationHandler.invoke(Object,Method,Object[])`][DefaultInvocationHandler.invoke]
is re-factored to implement
[`DefaultInterfaceMethodInvocationHandler`][DefaultInterfaceMethodInvocationHandler]:

<figcaption style="text-align: center">DefaultInvocationHandler</figcaption>
```java
@NoArgsConstructor @ToString
public class DefaultInvocationHandler implements DefaultInterfaceMethodInvocationHandler {
    ...
    @Override
    public Object invoke(Object proxy, Method method, Object[] argv) throws Throwable {
        Object result = null;
        Class<?> declarer = method.getDeclaringClass();

        if (method.isDefault()) {
            result = DefaultInterfaceMethodInvocationHandler.super.invoke(proxy, method, argv);
        } else if (declarer.equals(Object.class)) {
            result = method.invoke(this, argv);
        } else {
            result = invokeMethod(this, true, method.getName(), argv, method.getParameterTypes());
        }

        return result;
    }
    ...
}
```

With the [Java 8] implementation:

<figcaption style="text-align: center">
    DefaultInterfaceMethodInvocationHandler (Java 8)
</figcaption>
```java
public interface DefaultInterfaceMethodInvocationHandler extends InvocationHandler {
    @Override
    default Object invoke(Object proxy, Method method, Object[] argv) throws Throwable {
        Constructor<MethodHandles.Lookup> constructor =
            MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);

        constructor.setAccessible(true);

        Class<?> declarer = method.getDeclaringClass();
        Object result =
            constructor.newInstance(declarer)
            .in(declarer)
            .unreflectSpecial(method, declarer)
            .bindTo(proxy)
            .invokeWithArguments(argv);

        return result;
    }
}
```

While the [Java 9] implementation is:

<figcaption style="text-align: center">
    DefaultInterfaceMethodInvocationHandler (Java 9)
</figcaption>
```java
public interface DefaultInterfaceMethodInvocationHandler extends InvocationHandler {
    @Override
    default Object invoke(Object proxy, Method method, Object[] argv) throws Throwable {
        Class<?> declarer = method.getDeclaringClass();
        Object result =
            MethodHandles.lookup()
            .findSpecial(declarer, method.getName(),
                         methodType(method.getReturnType(), method.getParameterTypes()), declarer)
            .bindTo(proxy)
            .invokeWithArguments(argv);

        return result;
    }
}
```

The Java 9 alternative is saved to
`${basedir}/src/main/java9/ball/lang/reflect/DefaultInterfaceMethodInvocationHandler.java`
within the Maven project.  Compiling with the `maven-compiler-plugin` is
straightforward as the following profile demonstrates the incremental
configuration.<sup id="ref1">[1](#endnote1)</sup>

<figcaption style="text-align: center">Parent POM: Java 9 Profile</figcaption>
```xml
<project ...>
  ...
  <profiles>
  ...
    <profile>
      <id>[+] src/main/java9</id>
      <activation>
        <file><exists>${basedir}/src/main/java9</exists></file>
      </activation>
      <build>
        <pluginManagement>
          <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <executions>
                <execution>
                  <id>jdk9</id>
                  <goals>
                    <goal>compile</goal>
                  </goals>
                  <configuration>
                    <release>9</release>
                    <compileSourceRoots>
                      <compileSourceRoot>${project.basedir}/src/main/java9</compileSourceRoot>
                    </compileSourceRoots>
                    <multiReleaseOutput>true</multiReleaseOutput>
                  </configuration>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </pluginManagement>
      </build>
    </profile>
  ...
  </profiles>
  ...
</project>
```

To complete the JAR's configuration, the `Multi-Release` flag must be set in
the JAR's manifest.

<figcaption style="text-align: center">META-INF/MANIFEST.MF</figcaption>
```properties
Manifest-Version: 1.0
...
Multi-Release: true
...
```

The resulting JAR hierarchy is depicted below.

<figcaption style="text-align: center">Muti-Release JAR Hierarchy</figcaption>
```log
ball-util.jar
├── META-INF
│   ├── MANIFEST.MF
│   ├── ...
│   └── versions
│       └── 9
│           └── ball
│               └── lang
│                   └── reflect
│                       └── DefaultInterfaceMethodInvocationHandler.class
└── ball
    ├── ...
    ├── lang
    │   ├── ...
    │   └── reflect
    │       ├── DefaultInterfaceMethodInvocationHandler.class
    │       ├── DefaultInvocationHandler.class
    │       └── ...
    ├── ...
    ...
```


## Summary

Multi-release JARs provide a solution for a single JAR to support a range of
Java platform versions.  Developers should be aware that the solution does
present some challenges: The compiled *versioned* class files cannot be run
outside the JAR (making testing difficult) and class-loader implementation
details will effect resource discovery and
loading<sup id="ref2">[2](#endnote2)</sup> (to name a few).  However, as
illustrated with this use-case, it is a powerful tool for creating JARs that
support a wide range of Java platforms.


<b id="endnote1">[1]</b>
The Parent POM has similar profiles for Java 10 through 14.
[↩](#ref1)

<b id="endnote2">[2]</b>
Please see the discussion in ["JEP 238: Multi-Release JAR Files"][JEP 238].
[↩](#ref2)


[Java 8]: https://www.java.com/en/download/help/java8.html
[Java 9]: https://www.oracle.com/java/java9.html
[JEP 238]: https://openjdk.java.net/jeps/238

[InvocationHandler]: {{ page.javadoc.javase }}/java/lang/reflect/InvocationHandler.html?is-external=true
[MethodHandles.Lookup]: {{ page.javadoc.javase }}/java/lang/invoke/MethodHandles.Lookup.html

[DefaultInterfaceMethodInvocationHandler]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/DefaultInterfaceMethodInvocationHandler.html
[DefaultInvocationHandler.invoke]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/DefaultInvocationHandler.html#invoke-java.lang.Object-java.lang.reflect.Method-java.lang.Object:A-
[FluentNode]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentNode.html
