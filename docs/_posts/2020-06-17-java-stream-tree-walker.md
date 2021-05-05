---
title: Java Stream Tree Walker
canonical_url: https://blog.hcf.dev/article/2020-06-17-java-stream-tree-walker
tags:
 - Java
 - Stream
 - Spliterator
permalink: article/2020-06-17-java-stream-tree-walker
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
---

This article discusses walking a tree or graph of nodes with a
[Java 8][Java 8] [`Stream`][Stream] implementation.  The implementation is
codified in a [`Spliterator`][Spliterator] The strategy described herein can
be a useful alternative to implementing a class-hierarchy specific visitor
because only a single per-type method need be defined (often as a Java
lambda).

Please refer to this
[article](/article/2019-03-28-java-streams-and-spliterators) for an
in-depth discussion of creating [`Spliterator`s][Spliterator].


## API Definition

The implementaion provides the following API:

```java
public class Walker<T> ... {
    ...
    public static <T> Stream<T> walk(T root, Function<? super T,Stream<? extends T>> childrenOf)
    ...
}
```

The [`Function`][Function] provides the subordinate ("children") nodes of
the argument node.  For example, for Java [`Class`es][Class] to obtain inner
(declared) `Class`es:

```java
    t -> Stream.of(t.getDeclaredClasses()
```

or for [`File`s][File]:

```java
    t -> t.isDirectory() ? Stream.of(t.listFiles()) : Stream.empty()
```


## Implementation

The API provides a static method to create a [`Stream`][Stream] from the
implemented [`Spliterator`][Spliterator]:

```java
    public static <T> Stream<T> walk(T root, Function<? super T,Stream<? extends T>> childrenOf) {
        return StreamSupport.stream(new Walker<>(root, childrenOf), false);
    }
```

The complete [`Spliterator`][Spliterator] implementation is:

```java
public class Walker<T> extends Spliterators.AbstractSpliterator<T> {
    private final Stream<Supplier<Spliterator<T>>> stream;
    private Iterator<Supplier<Spliterator<T>>> iterator = null;
    private Spliterator<? extends T> spliterator = null;

    private Walker(T node, Function<? super T,Stream<? extends T>> childrenOf) {
        super(Long.MAX_VALUE, IMMUTABLE | NONNULL);

        stream =
            Stream.of(node)
            .filter(Objects::nonNull)
            .flatMap(childrenOf)
            .filter(Objects::nonNull)
            .map(t -> (() -> new Walker<T>(t, childrenOf)));
        spliterator = Stream.of(node).spliterator();
    }

    @Override
    public Spliterator<T> trySplit() {
        if (iterator == null) {
            iterator = stream.iterator();
        }

        return iterator.hasNext() ? iterator.next().get() : null;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> consumer) {
        boolean accepted = false;

        while (! accepted) {
            if (spliterator == null) {
                spliterator = trySplit();
            }

            if (spliterator != null) {
                accepted = spliterator.tryAdvance(consumer);

                if (! accepted) {
                    spliterator = null;
                }
            } else {
                break;
            }
        }

        return accepted;
    }
    ...
}
```

A [`Stream`][Stream] of [`Spliterator`][Spliterator] [`Supplier`s][Supplier]
is created at instantiation (and the `Supplier` will supply another
`Walker`).  The first time [`trySplit()`][Spliterator.trySplit] is called
the `Stream` is converted to an [`Iterator`][Iterator] and the next
`Spliterator` is generated.  The
[`tryAdvance(Consumer)`][Spliterator.tryAdvance] will exhaust the last
created `Spliterator` before calling `tryAdvance()` to obtain another.  The
first `Spliterator` consists solely of the root node.


## Examples

To print the inner defined classes of
[`Collections`]({{ page.javadoc.javase }}/java/util/Collections.html):

```java
        Walker.<Class<?>>walk(java.util.Collections.class,
                              t -> Stream.of(t.getDeclaredClasses()))
            .limit(10)
            .forEach(System.out::println);
```

Yields:

```bash
class java.util.Collections
class java.util.Collections$UnmodifiableCollection
class java.util.Collections$UnmodifiableSet
class java.util.Collections$UnmodifiableSortedSet
class java.util.Collections$UnmodifiableNavigableSet
class java.util.Collections$UnmodifiableNavigableSet$EmptyNavigableSet
class java.util.Collections$UnmodifiableRandomAccessList
class java.util.Collections$UnmodifiableList
class java.util.Collections$UnmodifiableMap
class java.util.Collections$UnmodifiableMap$UnmodifiableEntrySet
```

And to print the directory structure (sorted):

```java
        Walker.walk(new File("."),
                    t -> t.isDirectory() ? Stream.of(t.listFiles()) : Stream.empty())
            .filter(File::isDirectory)
            .sorted()
            .forEach(System.out::println);
```

Yields:

```bash
.
./src
./src/main
./src/main/resources
./target
```

This presents a flexible solution and can be extended with disparate
objects.  For example, a method to walk XML [`Node`s][Node] might be:

```java
    public static Stream<Node> childrenOf(Node node) {
        NodeList list = node.getChildNodes();

        return IntStream.range(0, list.getLength()).mapToObj(list::item);
    }
```


## Alternate Entry Point

It is straightforward to offer an alternate entry point where multiple root
nodes are supplied by defining a corresponding constructor:

```java
public class Walker<T> extends Spliterators.AbstractSpliterator<T> {
    ...
    private Walker(Stream<T> nodes, Function<? super T,Stream<? extends T>> childrenOf) {
        super(Long.MAX_VALUE, IMMUTABLE | NONNULL);

        stream = nodes.map(t -> (() -> new Walker<T>(t, childrenOf)));
    }
    ...
    public static <T> Stream<T> walk(Stream<T> roots, Function<? super T,Stream<? extends T>> childrenOf) {
        return StreamSupport.stream(new Walker<>(roots, childrenOf), false);
    }
    ...
}
```


## Summary

[`Stream`s][Stream] created from the [`Spliterator`][Spliterator]
implementation described herein provide a versatile means for walking any
tree with a minimum per-type implementation while offering all the filtering
and processing power of the `Stream` API.


[Java 8]: https://www.java.com/en/download/help/java8.html
[Class]: {{ page.javadoc.javase }}/java/lang/Class.html
[File]: {{ page.javadoc.javase }}/java/io/File.html
[Function]: {{ page.javadoc.javase }}/java/util/function/Function.html
[Iterator]: {{ page.javadoc.javase }}/java/util/Iterator.html
[Node]: {{ page.javadoc.javase }}/org/w3c/dom/Node.html
[Spliterator.tryAdvance]: {{ page.javadoc.javase }}/java/util/Spliterator.html#tryAdvance-java.util.function.Consumer-
[Spliterator.trySplit]: {{ page.javadoc.javase }}/java/util/Spliterator.html#trySplit--
[Spliterator]: {{ page.javadoc.javase }}/java/util/Spliterators.html
[Stream]: {{ page.javadoc.javase }}/java/util/stream/Stream.html
[Supplier]: {{ page.javadoc.javase }}/java/util/function/Supplier.html
