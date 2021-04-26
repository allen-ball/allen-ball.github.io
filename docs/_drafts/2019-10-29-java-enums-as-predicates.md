---
title: Java Enums as Predicates
canonical_url: https://blog.hcf.dev/article/2019-10-29-java-enums-as-predicates/
tags:
 - Java
 - fluent
permalink: article/2019-10-29-java-enums-as-predicates
---

## Introduction

This article examines extending Java
[`Enum`s](https://docs.oracle.com/javase/8/docs/api/java/lang/Enum.html)
used as property values within
[JavaBeans](https://docs.oracle.com/javase/tutorial/javabeans/)
combined with the Java
[Stream API](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html) 
to create and extend *fluent interfaces*.  From
[Wikipedia](https://en.wikipedia.org/wiki/Main_Page):

> [In software engineering, a fluent interface ... is a method for designing object oriented APIs based extensively on method chaining with the goal of making the readability of the source code close to that of ordinary written prose, essentially creating a domain-specific language within the interface.](https://en.wikipedia.org/wiki/Fluent_interface)

The Java Stream API provides the method chaining; this article will examine
how Java `Enum`s may be extended (specifically to implement
[`Predicate`](https://docs.oracle.com/javase/8/docs/api/java/util/function/Predicate.html))
to contribute to a fluent interface's quality of being similar to "written
prose."  For example:

``` java
public enum Rank implements Predicate<Card> {
    JOKER, ACE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING;
    ...
}

public enum Suit implements Predicate<Card> {
    CLUBS, DIAMONDS, HEARTS, SPADES;
    ...
}

public class Card {
    private final Suit suit;
    private final Rank rank;
    ...
}
```

will allow support for fluent expressions like:

``` java
    List<Card> hand = ...;

    boolean areAllHearts =
        hand.stream()
        .filter(Suit.HEARTS)
        .allMatch();

    boolean hasKingOfSpades =
        hand.stream()
        .filter(Rank.KING.and(Suit.SPADES))
        .anyMatch();
```

Complete [javadoc](javadoc/allclasses-noframe.html) is
provided.

## Extending Enums

`Enum` values are constants but they are also subclasses of `Enum` and those
subclass implementations may have custom fields and methods.  For example,
[`java.time.DayOfWeek`](https://docs.oracle.com/javase/8/docs/api/java/time/DayOfWeek.html)
implements the
[`TemporalAccessor`](https://docs.oracle.com/javase/8/docs/api/java/time/temporal/TemporalAccessor.html)
and
[`TemporalAdjuster`](https://docs.oracle.com/javase/8/docs/api/java/time/temporal/TemporalAdjuster.html)
interfaces so `DayOfWeek` provides implementation methods for those
interface methods.  The
[`Suit`](javadoc/ball/game/card/Card.Suit.html)
implementation demonstrates how subclass fields may be defined and set by
defining a custom constructor.

``` java
    public enum Suit {
        CLUBS(Color.BLACK, "\u2667"),
        DIAMONDS(Color.RED, "\u2662"),
        HEARTS(Color.RED, "\u2661"),
        SPADES(Color.BLACK, "\u2664");
        ...
        private final Color color;
        private final String string;

        @ConstructorProperties({ "color", EMPTY })
        private Suit(Color color, String string) {
            this.color = color;
            this.string = string;
        }

        public Color getColor() { return color; }

        @Override
        public String toString() { return string; }
        ...
    }
```

## Implementing Predicate

The key to contributing to the fluent interface provided by
[`Stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html)
is for the `Enum` subclass to implement `Predicate`.  Of course, that
`Predicate` must test the bean that the `Enum` is a property for.  For
example, [`Rank`](javadoc/ball/game/card/Card.Rank.html) and
`Suit` must test [`Card`](javadoc/ball/game/card/Card.html):

``` java
    public enum Rank implements Predicate<Card> {
        ...
        @Override
        public boolean test(Card card) {
            return is(this).test(card.getRank());
        }
        ...
        public static Predicate<Rank> is(Rank rank) {
            return t -> Objects.equals(rank, t);
        }
        ...
    }

    public enum Suit implements Predicate<Card> {
        ...
        @Override
        public boolean test(Card card) {
            return is(this).test(card.getSuit());
        }
        ...
        public static Predicate<Suit> is(Suit rank) {
            return t -> Objects.equals(rank, t);
        }
        ...
    }
```

Note: Both implementations provide static `is` methods to further contribute
to the "fluency" of the API.

To re-inforce the fact that `Rank` and `Suit` are bean properties of `Card`,
`Rank` and `Suit` are implemented as inner classes of `Card`.

## Fluent Implementation - Poker Hand Ranking

To demonstrate the "fluency" of the API, a Poker
[`Ranking`](javadoc/ball/game/card/poker/Ranking.html) `Enum`
may be defined.

``` java
public enum Ranking implements Predicate<List<Card>> {
    Empty(0, Collection::isEmpty),
        HighCard(1, t -> true),
        Pair(2, Rank.SAME),
        TwoPair(4, Pair.with(Pair)),
        ThreeOfAKind(3, Rank.SAME),
        Straight(5, Rank.SEQUENCE),
        Flush(5, Suit.SAME),
        FullHouse(5, ThreeOfAKind.with(Pair)),
        FourOfAKind(4, Rank.SAME),
        StraightFlush(5, holding(ACE, KING).negate().and(Straight).and(Flush)),
        RoyalFlush(5, holding(ACE, KING).and(Straight).and(Flush)),
        FiveOfAKind(5, Rank.SAME);

    private final int required;
    private final Predicate<List<Card>> is;

    private Ranking(int required, Predicate<List<Card>> is) {
        this.required = required;
        this.is = Objects.requireNonNull(is);
    }
    ...
    public int required() { return required; }
    ...
    @Override
    public boolean test(List<Card> list) {
        return (list.size() >= required()
                && is.test(subListTo(list, required())));
    }
    ...
}
```

To complete the Poker "domain specific language" the `Rank` and `Suit` types
must provide static `Predicate` `SAME` fields,

``` java
    ...
    private static <T> Predicate<List<T>> same(Function<T,Predicate<T>> mapper) {
        return t -> ((! t.isEmpty()) && t.stream().allMatch(mapper.apply(t.get(0))));
    }
    ...
    public enum Rank implements Predicate<Card> {
        ...
        public static final Predicate<List<Card>> SAME = same(Card::getRank);
        ...
    }
    ...
    public enum Suit implements Predicate<Card> {
        ...
        public static final Predicate<List<Card>> SAME = same(Card::getSuit);
        ...
    }
    ...
```

`Rank` must provide a static `SEQUENCE` `Predicate`,

``` java
    ...
    private static <T,R> List<R> listOf(Collection<T> collection,
                                        Function<T,R> mapper) {
        return collection.stream().map(mapper).collect(Collectors.toList());
    }
    ...
    public enum Rank implements Predicate<Card> {
        ...
        public static final List<Rank> ACE_HIGH  =
            unmodifiableList(asList(JOKER,
                                    TWO, THREE, FOUR, FIVE,
                                    SIX, SEVEN, EIGHT, NINE,
                                    TEN, JACK, QUEEN, KING, ACE));
        public static final List<Rank> ACE_LOW =
            unmodifiableList(asList(values()));

        private static final Map<String,Rank> MAP;
        private static final List<List<Rank>> SEQUENCES;

        static {
            TreeMap<String,Rank> map =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            for (Rank rank : values()) {
                map.put(rank.name(), rank);
                map.put(rank.toString(), rank);
            }

            MAP = unmodifiableMap(map);

            List<Rank> high = new ArrayList<>(Rank.ACE_HIGH);
            List<Rank> low = new ArrayList<>(Rank.ACE_LOW);

            reverse(high);
            reverse(low);

            SEQUENCES =
                unmodifiableList(asList(unmodifiableList(high),
                                        unmodifiableList(low)));
        }
        ...
        public static final Predicate<List<Card>> SEQUENCE =
            t -> ((! t.isEmpty()) && sequence(listOf(t, Card::getRank)));

        private static boolean sequence(List<Rank> list) {
            return (SEQUENCES.stream().anyMatch(t -> indexOfSubList(t, list) >= 0));
        }
        ...
    }
    ...
```

and `Ranking` must provide static `with` and `holding` methods:

``` java
public enum Ranking implements Predicate<List<Card>> {
    ...
    private Predicate<List<Card>> with(Predicate<List<Card>> that) {
        return t -> test(t) && that.test(subListFrom(t, required()));
    }

    private static <T> Predicate<List<T>> holding(int count, Predicate<List<T>> predicate) {
        return t -> (t.isEmpty() || predicate.test(subListTo(t, count)));
    }

    @SafeVarargs
    @SuppressWarnings({ "varargs" })
    private static <T> Predicate<List<T>> holding(Predicate<T>... array) {
        return holding(Stream.of(array).collect(Collectors.toList()));
    }

    private static <T> Predicate<List<T>> holding(List<Predicate<T>> list) {
        return t -> ((list.isEmpty() || t.isEmpty())
                     || (list.get(0).test(t.get(0))
                         && (holding(subListFrom(list, 1)).test(subListFrom(t, 1)))));
    }

    private static <T> List<T> subListTo(List<T> list, int to) {
        return list.subList(0, Math.min(to, list.size()));
    }

    private static <T> List<T> subListFrom(List<T> list, int from) {
        return list.subList(from, list.size());
    }
    ...
}
```

The `Ranking` `Predicate` `Enum` combined with the `Combinations` `Stream`
introduced in
[this article](/article/2019-03-28-java-streams-and-spliterators/)
may be used to test for a specific Poker hand.

``` java
    List<Card> hand = ...;
    int size = Math.min(5, hand.size());

    boolean isStraight =
        Combinations.of(size, hand)
        .filter(Ranking.STRAIGHT)
        .anyMatch();
```

While this implementation is complete, the `Combinations` `Stream` provides
an `of(int,int,Predicate<List<T>>,Collection<T>)` method that allows the
specification of a `Predicate` that when it evaluates to `false` will stop
iterating over that branch.  The `Ranking` `Enum` may be extended to provide
that `Predicate` by providing a `possible()` method:

``` java
public enum Ranking implements Predicate<List<Card>> {
    Empty(0, null, Collection::isEmpty),
        HighCard(1, t -> true, t -> true),
        Pair(2, Rank.SAME, Rank.SAME),
        TwoPair(4, holding(2, Rank.SAME), Pair.with(Pair)),
        ThreeOfAKind(3, Rank.SAME, Rank.SAME),
        Straight(5, Rank.SEQUENCE, Rank.SEQUENCE),
        Flush(5, Suit.SAME, Suit.SAME),
        FullHouse(5, holding(3, Rank.SAME), ThreeOfAKind.with(Pair)),
        FourOfAKind(4, Rank.SAME, Rank.SAME),
        StraightFlush(5,
                      holding(ACE, KING).negate().and(Rank.SEQUENCE).and(Suit.SAME),
                      holding(ACE, KING).negate().and(Straight).and(Flush)),
        RoyalFlush(5,
                   holding(ACE, KING).and(Rank.SEQUENCE).and(Suit.SAME),
                   holding(ACE, KING).and(Straight).and(Flush)),
        FiveOfAKind(5, Rank.SAME, Rank.SAME);

    private final int required;
    private final Predicate<List<Card>> possible;
    private final Predicate<List<Card>> is;

    private Ranking(int required, Predicate<List<Card>> possible, Predicate<List<Card>> is) {
        this.required = required;
        this.possible = possible;
        this.is = Objects.requireNonNull(is);
    }
    ...
    public Predicate<List<Card>> possible() {
        return t -> (possible == null || possible.test(subListTo(t, required())));
    }
    ...
}
```

which in combination with the
`Combinations.of(int,int,Predicate<List<T>>,Collection<T>)` method will
optimize the search for `ThreeOfAKind` by escaping a branch if the first
`Card`s are not the same `Rank`, `Straight` if the first `Card`s are not a
sequence, etc.

``` java
    List<Card> hand = ...;
    int size = Math.min(5, hand.size());

    boolean isStraight =
        Combinations.of(size, size, STRAIGHT.possible(), hand)
        .filter(Ranking.STRAIGHT)
        .anyMatch();
```

The logic in `Ranking.find(Collection<Card>)` and
[`Evaluator`](javadoc/ball/game/card/poker/Evaluator.html)
demonstrate more sophisticated logic.

## Summary

Implementing `Predicate(BEAN)` for `BEAN` property types (including `Enum`)
will contribute to making an API "fluent" when used in combination with
`Stream`.
