---
title: Java Streams and Spliterators
canonical_url: https://blog.hcf.dev/article/2019-03-28-java-streams-and-spliterators/
tags:
 - Java
 - Stream
 - Spliterator
permalink: article/2019-03-28-java-streams-and-spliterators
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
---

This article discusses implementing [Java 8][Java 8] [`Stream`s][Stream] and
the underlying [`Spliterator`][Spliterator] implementation.  The nontrivial
implementations described here are [`Permutations`][Permutations] and
[`Combinations`][Combinations] streams, both of which provide a stream of
[`List<T>`][List] instances representing the combinations of the argument
[`Collection<T>`][Collection].

For example, the first [`Combinations`][Combinations] of `5` of a 52-card
`Deck` are:

```bash
[2-♧, 3-♧, 4-♧, 5-♧, 6-♧]
[2-♧, 3-♧, 4-♧, 5-♧, 7-♧]
[2-♧, 3-♧, 4-♧, 5-♧, 8-♧]
[2-♧, 3-♧, 4-♧, 5-♧, 9-♧]
[2-♧, 3-♧, 4-♧, 5-♧, 10-♧]
[2-♧, 3-♧, 4-♧, 5-♧, J-♧]
[2-♧, 3-♧, 4-♧, 5-♧, Q-♧]
[2-♧, 3-♧, 4-♧, 5-♧, K-♧]
[2-♧, 3-♧, 4-♧, 5-♧, A-♧]
[2-♧, 3-♧, 4-♧, 5-♧, 2-♢]
[2-♧, 3-♧, 4-♧, 5-♧, 3-♢]
...
```

Complete [javadoc] is provided.


## Stream Implementation

The [`Permutations`][Permutations] stream is implemented in terms of
[`Combinations`][Combinations]:

```java
    public static <T> Stream<List<T>> of(Predicate<List<T>> predicate,
                                         Collection<T> collection) {
        int size = collection.size();

        return Combinations.of(size, size, predicate, collection);
    }
```

and the [`Combinations`][Combinations] stream relies on a
[`Spliterator`][Spliterator] implementation provided through a
[`Supplier`][Supplier]:

```java
    public static <T> Stream<List<T>> of(int size0, int sizeN,
                                         Predicate<List<T>> predicate,
                                         Collection<T> collection) {
        SpliteratorSupplier<T> supplier =
            new SpliteratorSupplier<T>()
            .collection(collection)
            .size0(size0).sizeN(sizeN)
            .predicate(predicate);

        return supplier.stream();
    }
```

The `supplier.stream()` method relies on [`StreamSupport`][StreamSupport]:

```java
        public Stream<List<T>> stream() {
            return StreamSupport.<List<T>>stream(get(), false);
        }
```

The [`Spliterator`][Spliterator] implementation is the subject of the next
section.


## Spliterator Implementation

The abstract [`DispatchSpliterator`][DispatchSpliterator] base class
provides the implementation of
[`Spliterator.tryAdvance(Consumer)`][Spliterator.tryAdvance].  The key logic
is the current `Spliterator`'s `tryAdvance(Consumer)` method is tried and if
it returns false, the next `Spliterator`<sup id="ref1">[1](#endnote1)</sup>
is tried until there are no more `Spliterator`s to be supplied.

```java
    private Iterator<Supplier<Spliterator<T>>> spliterators = null;
    private Spliterator<T> spliterator = Spliterators.emptySpliterator();
    ...
    protected abstract Iterator<Supplier<Spliterator<T>>> spliterators();
    ...
    @Override
    public Spliterator<T> trySplit() {
        if (spliterators == null) {
            spliterators = Spliterators.iterator(spliterators());
        }

        return spliterators.hasNext() ? spliterators.next().get() : null;
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
```

Subclass implementors must supply an implementation of
[`Iterator<Supplier<Spliterator<T>>> spliterators()`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/ball/util/DispatchSpliterator.html#spliterators--).
In the [`Combinations`][Combinations] implementation, the key
[`Spliterator`][Spliterator], [`ForPrefix`][ForPrefix], iterates over every
(sorted) prefix and either supplies more `ForPrefix` `Spliterator`s or a
single [`ForCombination`][ForCombination] `Spliterator`:

```java
        private class ForPrefix extends DispatchSpliterator<List<T>> {
            private final int size;
            private final List<T> prefix;
            private final List<T> remaining;

            public ForPrefix(int size, List<T> prefix, List<T> remaining) {
                super(binomial(remaining.size(), size),
                      SpliteratorSupplier.this.characteristics());

                this.size = size;
                this.prefix = requireNonNull(prefix);
                this.remaining = requireNonNull(remaining);
            }

            @Override
            protected Iterator<Supplier<Spliterator<List<T>>>> spliterators() {
                List<Supplier<Spliterator<List<T>>>> list = new LinkedList<>();

                if (prefix.size() < size) {
                    for (int i = 0, n = remaining.size(); i < n; i += 1) {
                        List<T> prefix = new LinkedList<>(this.prefix);
                        List<T> remaining = new LinkedList<>(this.remaining);

                        prefix.add(remaining.remove(i));

                        list.add(() -> new ForPrefix(size, prefix, remaining));
                    }
                } else if (prefix.size() == size) {
                    list.add(() -> new ForCombination(prefix));
                } else {
                    throw new IllegalStateException();
                }

                return list.iterator();
            }
        }
```

Size, supplied as a superclass constructor parameter, is calculated with the
[`binomial()`][binomial] method.  For an individual combination, the size is
`1`.

```java
        private class ForCombination extends DispatchSpliterator<List<T>> {
            private final List<T> combination;

            public ForCombination(List<T> combination) {
                super(1, SpliteratorSupplier.this.characteristics());

                this.combination = requireNonNull(combination);
            }

            @Override
            protected Iterator<Supplier<Spliterator<List<T>>>> spliterators() {
                Supplier<Spliterator<List<T>>> supplier =
                    () -> Collections.singleton(combination).spliterator();

                return Collections.singleton(supplier).iterator();
            }
        }
```

Implementations should delay as much computation as possible until required
in [`Spliterator.tryAdvance(Consumer)`][Spliterator.tryAdvance] allowing
callers (including [`Stream`][Stream] thorough
[`StreamSupport`][StreamSupport]) to optimize and avoid computation.

The complete implementation provides a [`Start`][Start]
[`Spliterator`][Spliterator] returned by the
[`SpliteratorSupplier`][SpliteratorSupplier] and a [`ForSize`][ForSize]
spliterator to iterate over combination sizes.

```java
        private class Start extends DispatchSpliterator<List<T>> {
            public Start() {
                super(binomial(collection().size(), size0(), sizeN()),
                      SpliteratorSupplier.this.characteristics());
            }

            @Override
            protected Iterator<Supplier<Spliterator<List<T>>>> spliterators() {
                List<Supplier<Spliterator<List<T>>>> list = new LinkedList<>();

                IntStream.rangeClosed(Math.min(size0(), sizeN()),
                                      Math.max(size0(), sizeN()))
                    .filter(t -> ! (collection.size() < t))
                    .forEach(t -> list.add(() -> new ForSize(t)));

                if (size0() > sizeN()) {
                    Collections.reverse(list);
                }

                return list.iterator();
            }
            ...
        }
```

```java
        private class ForSize extends DispatchSpliterator<List<T>> {
            private final int size;

            public ForSize(int size) {
                super(binomial(collection().size(), size),
                      SpliteratorSupplier.this.characteristics());

                this.size = size;
            }

            @Override
            protected Iterator<Supplier<Spliterator<List<T>>>> spliterators() {
                Supplier<Spliterator<List<T>>> supplier =
                    () -> new ForPrefix(size,
                                        Collections.emptyList(),
                                        new LinkedList<>(collection()));

                return Collections.singleton(supplier).iterator();
            }
            ...
        }
```


## Honoring the API Predicate Parameter

The API defines a [`Predicate`][Predicate] parameter which provides a way
for callers to dynamically short-circuit all or part of the iteration.  The
[`ForPrefix`][ForPrefix] and [`ForCombination`][ForCombination]
`tryAdvance(Consumer)` methods are overridden as follows:

```java
        private class ForPrefix extends DispatchSpliterator<List<T>> {
            ...
            @Override
            public boolean tryAdvance(Consumer<? super List<T>> consumer) {
                Predicate<List<T>> predicate =
                    SpliteratorSupplier.this.predicate();

                return ((prefix.isEmpty()
                         || (predicate == null || predicate.test(prefix)))
                        && super.tryAdvance(consumer));
            }
            ...
        }

        private class ForCombination extends DispatchSpliterator<List<T>> {
            ...
            public boolean tryAdvance(Consumer<? super List<T>> consumer) {
                Predicate<List<T>> predicate =
                    SpliteratorSupplier.this.predicate();

                return ((combination.isEmpty()
                         || (predicate == null || predicate.test(combination)))
                        && super.tryAdvance(consumer));
            }
            ...
        }
```

If a [`Predicate`][Predicate] is supplied and the current combination does
not satisfy the `Predicate`, that *path* is pruned immediately.
A [future blog post](/article/2019-10-29-java-enums-as-predicates/) will
discuss using this feature to quickly evaluate Poker hands.


<b id="endnote1">[1]</b>
Obtained by calling the implementatioon of
[`Spliterator.trySplit()`][Spliterator.trySplit].
[↩](#ref1)


[Java 8]: https://www.java.com/en/download/help/java8.html

[Collection]: {{ page.javadoc.javase }}/java/util/Collection.html
[List]: {{ page.javadoc.javase }}/java/util/List.html
[Predicate]: {{ page.javadoc.javase }}/java/util/function/Predicate.html
[Spliterator.tryAdvance]: {{ page.javadoc.javase }}/java/util/Spliterator.html#tryAdvance-java.util.function.Consumer-
[Spliterator.trySplit]: {{ page.javadoc.javase }}/java/util/Spliterator.html#trySplit--
[Spliterator]: {{ page.javadoc.javase }}/java/util/Spliterators.html
[StreamSupport]: {{ page.javadoc.javase }}/java/util/stream/StreamSupport.html
[Stream]: {{ page.javadoc.javase }}/java/util/stream/Stream.html
[Supplier]: {{ page.javadoc.javase }}/java/util/function/Supplier.html

[javadoc]: {{ site.javadoc.url }}/{{ page.permalink }}/overview-summary.html
[Combinations]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/util/stream/Combinations.html
[DispatchSpliterator]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/util/DispatchSpliterator.html
[ForSize]: {{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.177
[Permutations]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/util/stream/Permutations.html
[Start]: {{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.144
[binomial]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/util/DispatchSpliterator.html#binomial-long-long-
[ForPrefix]: {{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.208
[ForCombination]: {{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.265
[SpliteratorSupplier]: {{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/util/stream/Combinations.html#line.106
