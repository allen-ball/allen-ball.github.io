---
title: Java Snippets
tags:
 - Java
---

## Introduction

## I/O

### InputStream Contents to String

```java
InputStream in = ...;
Scanner scanner = new Scanner(in, "UTF-8");
String string = scanner.useDelimiter("\\A").next();
```

## Lambdas

## Collections and Iterables

### Iterator as Iterable, Stream, or Collection

1. Construct the
[Iterable](https://docs.oracle.com/javase/8/docs/api/java/lang/Iterable.html)
with a lambda expression

2. Use
[StreamSupport.stream(Spliterator,boolean)](https://docs.oracle.com/javase/8/docs/api/java/util/stream/StreamSupport.html#stream-java.util.Spliterator-boolean-)
to create a
[Stream](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html)

3. Use
[Stream.collect(Collector)](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html#collect-java.util.stream.Collector-)
(or variant) to create the Collection

```java
Iterator<T> iterator = ...;
Iterable<T> iterable = () -> iterator;
Stream<T> stream = StreamSupport.stream(iterable.spliterator(), false);
Collection<T> collection = stream.collect(Collectors.toCollection(() -> new ArrayList<>()));
```

Example(s):

```java
    private <T> Iterable<T> asIterable(Iterator<T> iterator) {
        return () -> iterator;
    }

    private <T> Stream<T> asStream(Iterator<T> iterator) {
        return StreamSupport.stream(asIterable(iterator).spliterator(), false);
    }
```

```java
import com.fasterxml.jackson.databind.node.ObjectNode;

    private List<String> getFieldNames(ObjectNode node) {
        Iterable<String> iterable = () -> node.fieldNames();

        return StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
    }
```

## java.util.concurrent

### CountDownLatch

```java
import java.util.concurrent.*;

public class CountDownLatchExample {
    public static int N = 10;

    public static void main(String[] argv) {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N);

        for (int i = 0; i < N; i += 1) {
            new Thread(new Worker(start, done)).start();
        }

        start.countDown();

        try {
            done.await();
        } catch (InterruptedException exception) {
        }
    }
}

public class Worker implements Runnable {
    private CountDownLatch start;
    private CountDownLatch done;

    public Worker(CountDownLatch start, CountDownLatch done) {
        this.start = start;
        this.done = done;
    }

    public void run() {
        try {
            start.await();

            for (int i = 0; i < 1000000; i += 1) {
            }

            System.out.println(this + " completed");

            done.countDown();
        } catch (InterruptedException exception) {
        }
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java CountDownLatchExample.java"
 * End:
 */
```

### ForkJoinPool

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static java.util.stream.Collectors.toList;

public class ForkJoinPoolExample {
    public enum Operator { ADD, MULTIPLY; }

    public static List<?> FORMULA =
        List.of(Operator.ADD,
                List.of(Operator.MULTIPLY, 1, 2, 3), 4, 5,
                List.of(Operator.ADD, 6, 7, 8,
                        List.of(Operator.MULTIPLY, 9, 10, 11)));

    public static int N = 20;

    public static void main(String[] argv) {
        ForkJoinPool pool = new ForkJoinPool(N);
        RecursiveTask<Integer> task = new Task(FORMULA);

        pool.invoke(task);

        System.out.println("Result: " + task.join());
    }

    public static class Task extends RecursiveTask<Integer> {
        private final Object formula;

        public Task(Object formula) { this.formula = formula; }

        @Override
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
                    .peek(RecursiveTask::fork)
                    .collect(toList());
                IntStream operands =
                    subtasks.stream()
                    .map(RecursiveTask::join)
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

            System.out.println(Thread.currentThread() + "\t"
                               + formula + " -> " + result);

            return result;
        }
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java ForkJoinPoolExample.java"
 * End:
 */
```

### ThreadPoolExecutor

```java
import java.util.*;
import java.util.concurrent.*;

public class ThreadPoolExecutorExample {
    public static int N = 10;

    public static void main(String[] argv) {
        try {
            ThreadPoolExecutor executor =
                (ThreadPoolExecutor) Executors.newFixedThreadPool(4);

            for (int i = 0; i < N; i += 1) {
                executor.submit(new Worker());
            }

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
}

public class Worker implements Runnable {
    public void run() {
        int count = Math.abs(new Random().nextInt() % 2000000);

        for (int i = 0; i < count; i += 1) {
        }

        System.out.println(Thread.currentThread() + "\t"
                           + this + " completed (" + count + ")");
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java ThreadPoolExecutorExample.java"
 * End:
 */
```

## Spring

### Spring Boot Web Server Launcher

```java
package application.root;

import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * {@link SpringApplication} {@link Launcher}.
 */
@SpringBootApplication
@NoArgsConstructor @ToString @Log4j2
public class Launcher extends SpringBootServletInitializer {

    /**
     * Standard {@link SpringApplication} {@code main(String[])}
     * entry point.
     *
     * @param   argv            The command line argument vector.
     *
     * @throws  Exception       If the function does not catch
     *                          {@link Exception}.
     */
    public static void main(String[] argv) throws Exception {
        SpringApplication application = new SpringApplication(Launcher.class);

        application.run(argv);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Launcher.class);
    }
}
```

### Add Header to Every HTTP Response

Implement a
[Filter](https://docs.oracle.com/javaee/7/api/javax/servlet/Filter.html)
as a
[`@Service`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/stereotype/Service.html).

```java
@Service
@NoArgsConstructor @Log4j2
public class FilterImpl implements Filter {
    @Override
    public void init(FilterConfig config) throws ServletException { }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException,
                                                   ServletException {
        response.setHeader("NAME", "VALUE");

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() { }
}
```

## Interview Questions

### Bubble Sort

```java
import java.util.*;

public class BubbleSort {
    public static void main(String[] argv) {
        sort(new int[] { 19, 2, 1, 8, 6, 20, 11, 9, 14, 12 });
        System.out.println("----------------------------------------");
        sort(new int[] { 20 });
        System.out.println("----------------------------------------");
        sort(new int[] { });
    }

    private static int[] sort(int[] array) {
        System.out.println(Arrays.toString(array));

        for (int j = array.length - 1; j > 0; j -= 1) {
            boolean changed = false;

            for (int i = 0; i < j; i += 1) {
                if (array[i] > array[i + 1]) {
                    changed |= true;

                    int temp = array[i];

                    array[i] = array[i + 1];
                    array[i + 1] = temp;

                    System.out.println(Arrays.toString(new int[] { i, j })
                                       + " " + Arrays.toString(array));
                }
            }

            if (! changed) {
                break;
            }
        }

        return array;
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java BubbleSort.java"
 * End:
 */
```

### Binary Search

#### Iterative

```java
import java.util.function.*;

public class BinarySearchIterative {
    public static int[] array = new int[] { 1, 2, 6, 8, 9, 11, 12, 14 };

    public static void main(String[] argv) {
        System.out.println(time(() -> findValueIn(8, array)));
        System.out.println(time(() -> findValueIn(0, array)));
        System.out.println(time(() -> findValueIn(20, array)));
        System.out.println(time(() -> findValueIn(10, array)));
        System.out.println(time(() -> findValueIn(10, new int[] { })));
    }

    private static int findValueIn(int value, int[] array) {
        int lower = 0;
        int upper = array.length - 1;

        while (lower < upper) {
            int mid = (lower + upper) / 2;

            if (array[mid] == value) {
                return mid;
            } else {
                if (value < array[mid]) {
                    upper = mid - 1;
                } else {
                    lower = mid + 1;
                }
            }
        }

        return -1;
    }

    private static void time(Runnable runnable) {
        long start = System.nanoTime();

        try {
            runnable.run();
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
        } finally {
            long end = System.nanoTime();

            System.out.println("Elapsed time: "
                               + (((double) end - start) / (1000 * 1000000))
                               + " seconds");
        }
    }

    private static <T> T time(Supplier<T> supplier) {
        T result = null;
        long start = System.nanoTime();

        try {
            result = supplier.get();
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
        } finally {
            long end = System.nanoTime();

            System.out.println("Elapsed time: "
                               + (((double) end - start) / (1000 * 1000000))
                               + " seconds");
        }

        return result;
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java BinarySearchIterative.java"
 * End:
 */
```

#### Recursive

```java
import java.util.function.*;

public class BinarySearchRecursive {
    public static int[] array = new int[] { 1, 2, 6, 8, 9, 11, 12, 14 };

    public static void main(String[] argv) {
        System.out.println(time(() -> findValueIn(8, array)));
        System.out.println(time(() -> findValueIn(0, array)));
        System.out.println(time(() -> findValueIn(20, array)));
        System.out.println(time(() -> findValueIn(10, array)));
        System.out.println(time(() -> findValueIn(10, new int[] { })));
    }

    private static int findValueIn(int value, int[] array) {
        return findValueIn(value, array, 0, array.length - 1);
    }

    private static int findValueIn(int value,
                                   int[] array, int lower, int upper) {
        if (upper < lower) {
            return -1;
        }

        int mid = (lower + upper) / 2;

        if (array[mid] == value) {
            return mid;
        } else if (value < array[mid]) {
            return findValueIn(value, array, lower, mid - 1);
        } else {
            return findValueIn(value, array, mid + 1, upper);
        }
    }

    private static void time(Runnable runnable) {
        long start = System.nanoTime();

        try {
            runnable.run();
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
        } finally {
            long end = System.nanoTime();

            System.out.println("Elapsed time: "
                               + (((double) end - start) / (1000 * 1000000))
                               + " seconds");
        }
    }

    private static <T> T time(Supplier<T> supplier) {
        T result = null;
        long start = System.nanoTime();

        try {
            result = supplier.get();
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
        } finally {
            long end = System.nanoTime();

            System.out.println("Elapsed time: "
                               + (((double) end - start) / (1000 * 1000000))
                               + " seconds");
        }

        return result;
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java BinarySearchRecursive.java"
 * End:
 */
```

## Java Bugs

```java
public class StringValueOfNull {
    public static void main(String[] argv) {
        long start = System.nanoTime();

        try {
            System.out.println(String.valueOf(null));
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
        } finally {
            long end = System.nanoTime();

            System.out.println("Elapsed time: "
                               + (((double) end - start) / (1000 * 1000000))
                               + " seconds");
        }
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 11)/bin/java StringValueOfNull.java"
 * End:
 */
```

## Spring Boot + Thymeleaf

### Context Variables

```xml
        <!-- https://stackoverflow.com/questions/31387526/list-all-available-model-attributes-in-thymeleaf -->
        <table>
          <th th:text="${#ctx.getClass()}"/>
          <tbody>
            <tr>
              <td>param</td>
              <td th:text="${#ctx.getVariable('param')}"></td>
            </tr>
            <tr>
              <td>session</td>
              <td th:text="${#ctx.getVariable('session')}"></td>
            </tr>
            <tr>
              <td>application</td>
              <td th:text="${#ctx.getVariable('application')}"></td>
            </tr>
            <tr th:each="var : ${#ctx.getVariableNames()}">
              <td th:text="${var}"></td>
              <td th:text="${#ctx.getVariable(var)}"></td>
            </tr>
          </tbody>
        </table>
```
