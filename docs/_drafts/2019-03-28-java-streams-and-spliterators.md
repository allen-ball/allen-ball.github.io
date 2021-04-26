---
title: Java Streams and Spliterators
canonical_url: https://blog.hcf.dev/article/2019-03-28-java-streams-and-spliterators/
tags:
 - Java
 - Stream
 - Spliterator
permalink: article/2019-03-28-java-streams-and-spliterators
---

## Introduction

This article discusses implementing
[Java 8](https://docs.oracle.com/javase/8/docs/api/index.html)
[`Stream`s](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html)
and the underlying
[`Spliterator`](https://docs.oracle.com/javase/8/docs/api/java/util/Spliterators.html)
implementation.  The nontrivial implementations described here are
[`Permutations`](javadoc/ball/util/stream/Permutations.html)
and
[`Combinations`](javadoc/ball/util/stream/Combinations.html)
`Stream`s, both of which provide a stream of
[`List<T>`](https://docs.oracle.com/javase/8/docs/api/java/util/List.html)
instances representing the combinations of the argument
[`Collection<T>`](https://docs.oracle.com/javase/8/docs/api/java/util/Collection.html).

For example, the first `Combinations` of `5` of a 52-card `Deck` are:

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

Complete [javadoc](javadoc/overview-summary.html) is
provided.

## `Stream` Implementation

The `Permutations` stream is implemented in terms of `Combinations`:

```java
    public static <T> Stream<List<T>> of(Predicate<List<T>> predicate,
                                         Collection<T> collection) {
        int size = collection.size();

        return Combinations.of(size, size, predicate, collection);
    }
```

and the `Combinations` stream relies on a `Spliterator` implementation
provided through a
[`Supplier`](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html):

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

The `supplier.stream()` method relies on
[`StreamSupport`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/StreamSupport.html):

```java
        public Stream<List<T>> stream() {
            return StreamSupport.<List<T>>stream(get(), false);
        }
```

The `Spliterator` implementation is the subject of the next section.

## `Spliterator` Implementation

The abstract
[`DispatchSpliterator`](javadoc/ball/util/DispatchSpliterator.html)
base class provides the implementation of
[`Spliterator.tryAdvance(Consumer)`](https://docs.oracle.com/javase/8/docs/api/java/util/Spliterator.html#tryAdvance-java.util.function.Consumer-).
The key logic is the current `Spliterator`'s `tryAdvance(Consumer)` method
is tried and if it returns false, the next `Spliterator`[^1] is tried until
there are no more `Spliterator`s to be supplied.

[^1]: Obtained by calling the implementatioon of
[`Spliterator.trySplit()`](https://docs.oracle.com/javase/8/docs/api/java/util/Spliterator.html#trySplit--).

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
[`Iterator<Supplier<Spliterator<T>>>
spliterators()`](javadoc/ball/util/DispatchSpliterator.html#spliterators--).
In the `Combinations` implementation, the key `Spliterator`,
[`ForPrefix`](javadoc/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.208),
iterates over every (sorted) prefix and either supplies more `ForPrefix`
`Spliterator`s or a single
[`ForCombination`](javadoc/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.265)
`Spliterator`:

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
[`binomial()`](javadoc/ball/util/DispatchSpliterator.html#binomial-long-long-)
method.  For an individual combination, the size is `1`.

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
in `Spliterator.tryAdvance(Consumer)` allowing callers (including
`Stream` thorough `StreamSupport`) to optimize and avoid computation.

The complete implementation provides a
[`Start`](javadoc/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.144)
`Spliterator` returned by the `SpliteratorSupplier` and a
[`ForSize`](javadoc/src-html/ball/util/stream/Combinations.SpliteratorSupplier.html#line.177)
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

The API defines a
[`Predicate`](https://docs.oracle.com/javase/8/docs/api/java/util/function/Predicate.html)
parameter which provides a way for callers to dynamically short-circuit all
or part of the iteration.  The `ForPrefix` and `ForCombination`
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

If a `Predicate` is supplied and the current combination does not satisfy
the `Predicate`, that *path* is pruned immediately.  A
[future blog post](/article/2019-10-29-java-enums-as-predicates/)
will discuss using this feature to quickly evaluate Poker hands.
