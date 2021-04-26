---
title: FiveThirtyEight "What’s Your Best Scrabble String?" Solution
canonical_url: https://blog.hcf.dev/article/2019-07-08-fivethirtyeight-best-scrabble-string/
tags:
 - Java
 - Scrabble
permalink: article/2019-07-08-fivethirtyeight-best-scrabble-string
---

## Introduction

[FiveThirtyEight](https://fivethirtyeight.com/) presented a
[challenge](https://fivethirtyeight.com/features/whats-your-best-scrabble-string/)
to order Scrabble tiles to generate the largest score.

Complete [javadoc]({{ site.blog_javadoc_url }}/{{ page.permalink }}/overview-summary.html) is
provided with the solution implemented in
[`SolveRiddle20190628Task`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html).

## Solution

<pre>
CARBOXYMETHYLCELLULOSEHAND_RAFTSMANS_IPWIREDRAWERDINITROBENZENEPETTIFOGGINGJUDOKAEQUATEVIVAAIOESOOIU 912
                          C         H
----------------------------------------------------------------------------------------------------
CARBOXYMETHYLCELLULOSE                                                                               46
CARBO                                                                                                9
CARB                                                                                                 8
CAR                                                                                                  5
 ARB                                                                                                 5
 AR                                                                                                  2
   BOXY                                                                                              16
   BOX                                                                                               12
   BO                                                                                                4
    OXY                                                                                              13
    OX                                                                                               9
       METHYLCELLULOSE                                                                               25
       METHYL                                                                                        14
       METH                                                                                          9
       MET                                                                                           5
       ME                                                                                            4
        ETHYL                                                                                        11
        ETH                                                                                          6
        ET                                                                                           2
         THY                                                                                         9
             CELLULOSE                                                                               11
             CELL                                                                                    6
             CEL                                                                                     5
              ELL                                                                                    3
              EL                                                                                     2
                  LOSE                                                                               4
                  LO                                                                                 2
                   OSE                                                                               3
                   OS                                                                                2
                     EH                                                                              5
                      HANDCRAFTSMANSHIP                                                              26
                      HANDCRAFTSMAN                                                                  21
                      HANDCRAFTS                                                                     16
                      HANDCRAFT                                                                      15
                      HAND                                                                           8
                      HA                                                                             5
                       AND                                                                           4
                       AN                                                                            2
                          CRAFTSMANSHIP                                                              18
                          CRAFTSMAN                                                                  13
                          CRAFTS                                                                     8
                          CRAFT                                                                      7
                           RAFTSMAN                                                                  13
                           RAFTS                                                                     8
                           RAFT                                                                      7
                            AFT                                                                      6
                                MANS                                                                 6
                                MAN                                                                  5
                                MA                                                                   4
                                   SHIP                                                              5
                                   SH                                                                1
                                    HIP                                                              4
                                    HI                                                               1
                                       WIREDRAWER                                                    17
                                       WIREDRAW                                                      15
                                       WIRED                                                         9
                                       WIRE                                                          7
                                        IRED                                                         5
                                        IRE                                                          3
                                         REDRAWER                                                    12
                                         REDRAW                                                      10
                                         RED                                                         4
                                         RE                                                          2
                                          ED                                                         3
                                           DRAWER                                                    10
                                           DRAW                                                      8
                                            RAWER                                                    8
                                            RAW                                                      6
                                             AWE                                                     6
                                             AW                                                      5
                                              WE                                                     5
                                               ER                                                    2
                                                 DINITROBENZENE                                      26
                                                 DINITRO                                             8
                                                 DIN                                                 4
                                                  IN                                                 2
                                                   NITROBENZENE                                      23
                                                   NITRO                                             5
                                                   NIT                                               3
                                                    IT                                               2
                                                      ROBE                                           6
                                                      ROB                                            5
                                                       OBE                                           5
                                                        BENZENE                                      18
                                                        BEN                                          5
                                                        BE                                           4
                                                         EN                                          2
                                                             NE                                      2
                                                               PETTIFOGGING                          20
                                                               PETTIFOG                              14
                                                               PETTI                                 7
                                                               PET                                   5
                                                               PE                                    4
                                                                  TI                                 2
                                                                   IF                                5
                                                                    FOGGING                          13
                                                                    FOG                              7
                                                                       GIN                           4
                                                                           JUDOKA                    18
                                                                           JUDO                      12
                                                                            UDO                      4
                                                                             DO                      3
                                                                              OKA                    7
                                                                               KA                    6
                                                                                AE                   2
                                                                                 EQUATE              15
                                                                                  QUATE              14
                                                                                  QUA                12
                                                                                    ATE              3
                                                                                    AT               2
                                                                                       VIVA          10
                                                                                           AI        2
                                                                                             OE      2
                                                                                               SO    2
</pre>

## Theory of Operation

A number of
[`Map`s](https://docs.oracle.com/javase/8/docs/api/java/util/Map.html)
are generated from the
[word list](https://norvig.com/ngrams/enable1.txt).

``` java
    private final Wordlist wordlist = new Wordlist();
    private final WordMap wordmap = new WordMap(wordlist.keySet());
    private final StartsWithMap starts = new StartsWithMap(wordmap.keySet());
```

`wordlist` is a
[`Map<CharSequence,Integer>`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/src-html/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html#line.441)
mapping a valid "word" to its potential point value, `wordmap` is a
[`Map<CharSequence,SortedSet<CharSequence>>`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/src-html/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html#line.536)
mapping a word to all its "included" words, and `starts` is also a
[`Map<CharSequence,SortedSet<CharSequence>>`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/src-html/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html#line.561)
mapping all words to its respective prefix.  After each iteration of
selecting a subsequence to
[play]({{ site.blog_javadoc_url }}/{{ page.permalink }}/src-html/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html#line.365),
the `wordmap` `keySet()` is updated by removing any sequences that can no
longer be played for points:

``` java
            wordmap.keySet().removeIf(t -> (! isPlayable(t, bag)));

            wordmap.values().forEach(t -> t.retainAll(wordmap.keySet()));
            wordmap.values().removeIf(Collection::isEmpty);

            starts.values().forEach(t -> t.retainAll(wordmap.keySet()));
            starts.values().removeIf(Collection::isEmpty);
```

The
[`next()`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/src-html/ball/riddler538/ant/taskdefs/SolveRiddle20190628Task.html#line.313)
method chooses the next highest valued sequence of tiles to play:

``` java
    private Optional<CharSequence> next() {
        TreeMap<CharSequence,CharSequence> map = new TreeMap<>(COMPARATOR);

        for (int i = 1, j = sequence.length(); i <= j; i += 1) {
            String prefix = sequence.subSequence(i, j).toString();

            starts.subMap(prefix + Character.MIN_VALUE,
                          prefix + Character.MAX_VALUE)
                .keySet()
                .stream()
                .max(comparingInt(t -> potential(t)))
                .ifPresent(t -> map.put(prefix, t));
        }

        starts.keySet()
            .stream()
            .max(comparingInt(t -> potential(t)))
            .ifPresent(t -> map.put("", t));

        Optional<CharSequence> next =
            map.entrySet()
            .stream()
            .max(comparingInt(t -> potential(t.getValue())))
            .map(t -> t.getValue().toString().substring(t.getKey().length()));

        return next;
    }
```

## Previous Challenge

The `ball.game.scrabble` package was initially developed to solve a previous
Scrabble
[problem](http://fivethirtyeight.com/features/this-challenge-will-boggle-your-mind/).
The implementation is
[`SolveExpress20161021Task`]({{ site.blog_javadoc_url }}/{{ page.permalink }}/ball/riddler538/ant/taskdefs/SolveExpress20161021Task.html)
giving the solution:

<pre>
CLASSISMS [AS, AS+S, L+ASS, LASS+I, LASSI+S, C+LASSIS, CLASSIS+M, CLASSISM+S]
CLASSISTS [AS, AS+S, L+ASS, LASS+I, LASSI+S, C+LASSIS, CLASSIS+T, CLASSIST+S]
RELAPSERS [LA, LA+P, LAP+S, LAPS+E, E+LAPSE, R+ELAPSE, RELAPSE+R, RELAPSER+S]
GLASSIEST [AS, AS+S, L+ASS, LASS+I, LASSI+E, LASSIE+S, G+LASSIES, GLASSIES+T]
SCRAPINGS [PI, PI+N, PIN+G, A+PING, R+APING, C+RAPING, S+CRAPING, SCRAPING+S]
SHEATHERS [AT, E+AT, EAT+H, H+EATH, S+HEATH, SHEATH+E, SHEATHE+R, SHEATHER+S]
UPRAISERS [IS, A+IS, R+AIS, RAIS+E, P+RAISE, PRAISE+R, PRAISER+S, U+PRAISERS]
</pre>
