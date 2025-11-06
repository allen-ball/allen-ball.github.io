/* $Id: walker.java 6208 2020-06-17 22:14:29Z ball $ */
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import java.io.File;
import java.util.stream.IntStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {
    public static void main(String[] argv) {
        Walker.<Class<?>>walk(java.util.Collections.class,
                              t -> Stream.of(t.getDeclaredClasses()))
            .limit(10)
            .forEach(System.out::println);

        Walker.walk(new File("."),
                    t -> t.isDirectory() ? Stream.of(t.listFiles()) : Stream.empty())
            .filter(File::isDirectory)
            .sorted()
            .forEach(System.out::println);
    }

    public static Stream<Node> childrenOf(Node node) {
        NodeList list = node.getChildNodes();

        return IntStream.range(0, list.getLength()).mapToObj(list::item);
    }
}

public class Walker<T> extends Spliterators.AbstractSpliterator<T> {
    private final Stream<Supplier<Spliterator<T>>> stream;
    private Iterator<Supplier<Spliterator<T>>> iterator = null;
    private Spliterator<? extends T> spliterator = null;

    private Walker(T node,
                   Function<? super T,Stream<? extends T>> childrenOf) {
        super(Long.MAX_VALUE, IMMUTABLE | NONNULL);

        stream =
            Stream.of(node)
            .filter(Objects::nonNull)
            .flatMap(childrenOf)
            .filter(Objects::nonNull)
            .map(t -> (() -> new Walker<T>(t, childrenOf)));
        spliterator = Stream.of(node).spliterator();
    }

    private Walker(Stream<T> nodes,
                   Function<? super T,Stream<? extends T>> childrenOf) {
        super(Long.MAX_VALUE, IMMUTABLE | NONNULL);

        stream = nodes.map(t -> (() -> new Walker<T>(t, childrenOf)));
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

    public static <T> Stream<T> walk(T root,
                                     Function<? super T,Stream<? extends T>> childrenOf) {
        return StreamSupport.stream(new Walker<>(root, childrenOf), false);
    }

    public static <T> Stream<T> walk(Stream<T> roots,
                                     Function<? super T,Stream<? extends T>> childrenOf) {
        return StreamSupport.stream(new Walker<>(roots, childrenOf), false);
    }
}
/*
 * Local Variables:
 * compile-command: "$(/usr/libexec/java_home -v 13)/bin/java walker.java"
 * End:
 */
