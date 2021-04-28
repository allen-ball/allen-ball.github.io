---
title: java.util.concurrent.ForkJoinPool Example
canonical_url: https://blog.hcf.dev/article/2020-08-11-java-util-concurrent-forkjoinpool/
tags:
 - Java
 - Concurrency
permalink: article/2020-08-11-java-util-concurrent-forkjoinpool
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
excerpt_separator: <!--more-->
---

The combination of [`RecursiveTask`][RecursiveTask] implementations running
in a [`ForkJoinPool`][ForkJoinPool] allows tasks to be defined that may
spawn subtasks that in turn may run asynchronously.  The `ForkJoinPool`
manages efficient processing of those tasks.

This article presents a simple calculator application to evaluate a formula
defined as a [`List`][List] presents a single-threaded recursive solution,
and then converts that solution to use [`RecursiveTasks`][RecursiveTask]
executed in a [`ForkJoinPool`][ForkJoinPool].

<!--more-->


## Recursive Solution

A formula to be computed is defined as follows:

```java
    public enum Operator { ADD, MULTIPLY; }

    public static List<?> FORMULA =
        List.of(Operator.ADD,
                List.of(Operator.MULTIPLY, 1, 2, 3), 4, 5,
                List.of(Operator.ADD, 6, 7, 8,
                        List.of(Operator.MULTIPLY, 9, 10, 11)));
```

A formula is either a [`Number`][Number] or a [`List`][List] consisting of
an `Operator` followed by other formulae.  For illustration, the Lisp
equivalent of `FORMULA` would be:

```scheme
(+ (* 1 2 3) 4 5
   (+ 6 7 8
      (* 9 10 11)))
```

A `Task` class is defined to solve formulae:

```java
    public static class Task {
        private final Object formula;

        public Task(Object formula) { this.formula = formula; }

        public Integer compute() {
            Integer result = null;

            if (formula instanceof Number) {
                result = ((Number) formula).intValue();
            } else {
                List<?> list = (List<?>) formula;
                Operator operator = (Operator) list.get(0);
                List<Task> subtasks =
                    list.subList(1, list.size())
                    .stream()
                    .map(Task::new)
                    .collect(toList());
                IntStream operands =
                    subtasks.stream()
                    .map(Task::compute)
                    .mapToInt(Integer::intValue);

                switch (operator) {
                case ADD:
                    result = operands.sum();
                    break;

                case MULTIPLY:
                    result = operands.reduce(1, (x, y) -> x * y);
                    break;
                }
            }

            System.out.println(formula + " -> " + result);

            return result;
        }
    }
```

The `compute()` method evaluates the formula by creating another `Task`
instance to evaluate each operand recursively by calling `compute()`.  The
actual mechanics are to create a `List` of `Task`s and then map the
[`Stream`][Stream] of `Task`s to an [`IntStream`][IntStream] by calling
`Task.compute()` which is evaluated based on the operator.

The static `main(String[])` function simply instantiates a `Task` and calls
`compute()`.

```java
    public static void main(String[] argv) {
        Task task = new Task(FORMULA);

        System.out.println("Result: " + task.compute());
    }
```

Which generates the following output:

```javastacktrace
1 -> 1
2 -> 2
3 -> 3
[MULTIPLY, 1, 2, 3] -> 6
4 -> 4
5 -> 5
6 -> 6
7 -> 7
8 -> 8
9 -> 9
10 -> 10
11 -> 11
[MULTIPLY, 9, 10, 11] -> 990
[ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]] -> 1011
[ADD, [MULTIPLY, 1, 2, 3], 4, 5, [ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]]] -> 1026
Result: 1026
```

The next chapter details how to convert this solution to use
[`ForkJoinPool`][ForkJoinPool] to enable some level of parallel processing.


## ForkJoinPool Solution

The `Task` class is modified to extend
[`RecursiveTask<Integer>`][RecursiveTask].  When a subtask is instantiated,
[`RecursiveTask.fork()`][RecursiveTask.fork] is called to asynchronously
execute this task in the pool the current task is running in.  In the
`IntStream`, [`RecursiveTask.join()`][RecursiveTask.join] is called to wait
for the subtask to complete (if it hasn't already) and return the result of
the `compute()` method.

```java
    public static class Task extends RecursiveTask<Integer> {
        ...
        @Override
        public Integer compute() {
            Integer result = null;

            if (formula instanceof Number) {
                ...
            } else {
                ...
                List<Task> subtasks =
                    list.subList(1, list.size())
                    .stream()
                    .map(Task::new)
                    .peek(RecursiveTask::fork)
                    .collect(toList());
                IntStream operands =
                    subtasks.stream()
                    .map(RecursiveTask::join)
                    .mapToInt(Integer::intValue);
                ...
            }

            System.out.println(Thread.currentThread() + "\t"
                               + formula + " -> " + result);

            return result;
        }
    }
```

The executing [`Thread`][Thread] is included in the output to demonstrate
the behavior.  The `main(String[])` function creates a
[`ForkJoinPool`][ForkJoinPool], the `Task` to compute `FORMULA`, and uses
the pool to invoke the `Task`.  The result is obtained through `Task.join()`
to wait for the computation to complete.

```java
    public static int N = 10;

    public static void main(String[] argv) {
        ForkJoinPool pool = new ForkJoinPool(N);
        RecursiveTask<Integer> task = new Task(FORMULA);

        pool.invoke(task);

        System.out.println("Result: " + task.join());
    }
```

Output using 10 threads (`N = 10`):

```javastacktrace
Thread[ForkJoinPool-1-worker-31,5,main]	2 -> 2
Thread[ForkJoinPool-1-worker-19,5,main]	8 -> 8
Thread[ForkJoinPool-1-worker-23,5,main]	4 -> 4
Thread[ForkJoinPool-1-worker-17,5,main]	3 -> 3
Thread[ForkJoinPool-1-worker-9,5,main]	1 -> 1
Thread[ForkJoinPool-1-worker-27,5,main]	5 -> 5
Thread[ForkJoinPool-1-worker-3,5,main]	7 -> 7
Thread[ForkJoinPool-1-worker-21,5,main]	6 -> 6
Thread[ForkJoinPool-1-worker-17,5,main]	11 -> 11
Thread[ForkJoinPool-1-worker-23,5,main]	10 -> 10
Thread[ForkJoinPool-1-worker-31,5,main]	9 -> 9
Thread[ForkJoinPool-1-worker-5,5,main]	[MULTIPLY, 1, 2, 3] -> 6
Thread[ForkJoinPool-1-worker-31,5,main]	[MULTIPLY, 9, 10, 11] -> 990
Thread[ForkJoinPool-1-worker-13,5,main]	[ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]] -> 1011
Thread[ForkJoinPool-1-worker-19,5,main]	[ADD, [MULTIPLY, 1, 2, 3], 4, 5, [ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]]] -> 1026
Result: 1026
```

And with 1 pool thread (`N = 1`):

```javastacktrace
Thread[ForkJoinPool-1-worker-3,5,main]	1 -> 1
Thread[ForkJoinPool-1-worker-3,5,main]	2 -> 2
Thread[ForkJoinPool-1-worker-3,5,main]	3 -> 3
Thread[ForkJoinPool-1-worker-3,5,main]	[MULTIPLY, 1, 2, 3] -> 6
Thread[ForkJoinPool-1-worker-3,5,main]	4 -> 4
Thread[ForkJoinPool-1-worker-3,5,main]	5 -> 5
Thread[ForkJoinPool-1-worker-3,5,main]	6 -> 6
Thread[ForkJoinPool-1-worker-3,5,main]	7 -> 7
Thread[ForkJoinPool-1-worker-3,5,main]	8 -> 8
Thread[ForkJoinPool-1-worker-3,5,main]	9 -> 9
Thread[ForkJoinPool-1-worker-3,5,main]	10 -> 10
Thread[ForkJoinPool-1-worker-3,5,main]	11 -> 11
Thread[ForkJoinPool-1-worker-3,5,main]	[MULTIPLY, 9, 10, 11] -> 990
Thread[ForkJoinPool-1-worker-3,5,main]	[ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]] -> 1011
Thread[ForkJoinPool-1-worker-3,5,main]	[ADD, [MULTIPLY, 1, 2, 3], 4, 5, [ADD, 6, 7, 8, [MULTIPLY, 9, 10, 11]]] -> 1026
Result: 1026
```

Which (unsurprisingly) calculates the formulae in the same order as the
recursive solution.


## Summary

Single threaded recursive solutions may be converted to
[`RecursiveTask`][RecursiveTask]<sup id="ref1">[1](#endnote1)</sup>
implementations and invoked through a [`ForkJoinPool`][ForkJoinPool] to
enable efficient processing of subtasks.


<b id="endnote1">[1]</b>
Implementation class of [`ForkJoinTask`][ForkJoinTask].
[↩](#ref1)


[ForkJoinPool]: {{ page.javadoc.javase }}/java/util/concurrent/ForkJoinPool.html
[ForkJoinTask]: {{ page.javadoc.javase }}/java/util/concurrent/ForkJoinTask.html
[IntStream]: {{ page.javadoc.javase }}/java/util/stream/IntStream.html
[List]: {{ page.javadoc.javase }}/java/util/List.html
[Number]: {{ page.javadoc.javase }}/java/lang/Number.html
[RecursiveTask.fork]: {{ page.javadoc.javase }}/java/util/concurrent/ForkJoinTask.html#fork--
[RecursiveTask.join]: {{ page.javadoc.javase }}/java/util/concurrent/ForkJoinTask.html#join--
[RecursiveTask]: {{ page.javadoc.javase }}/java/util/concurrent/RecursiveTask.html
[Stream]: {{ page.javadoc.javase }}/java/util/stream/Stream.html
[Thread]: {{ page.javadoc.javase }}/java/lang/Thread.html
