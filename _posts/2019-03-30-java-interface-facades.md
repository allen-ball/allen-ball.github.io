---
title: Java Interface Facades
canonical_url: https://blog.hcf.dev/article/2019-03-30-java-interface-facades
tags:
 - Java
 - XML
 - HTML
 - DOM
 - Proxy
 - Javadoc
permalink: article/2019-03-30-java-interface-facades
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
excerpt_separator: <!--more-->
---

This article discusses extending final implementation classes through the
use of [`Proxy`][Proxy] [`InvocationHandler`s][InvocationHandler] and
[Default Interface Methods] introduced in [Java 8].  The specific use case
described here is to add fluent methods to [Document Object Model (DOM)] to
enable [Javadoc] [Taglet] implementations to provide snippets of well-formed
HTML/XML.
<!--more-->
The various fluent `add()` methods implemented in [`FluentNode`][FluentNode]
(the "facade"):

```java
    default FluentNode add(Stream<Node> stream) {
        return add(stream.toArray(Node[]::new));
    }

    default FluentNode add(Iterable<Node> iterable) {
        return add(toArray(iterable));
    }

    default FluentNode add(Node... nodes) {
        for (Node node : nodes) {
            switch (node.getNodeType()) {
            case ATTRIBUTE_NODE:
                getAttributes().setNamedItem(node);
                break;

            default:
                appendChild(node);
                break;
            }
        }

        return this;
    }
```

which allows the creation of fluent methods to create [`Element`s][Element]:

```java
    default FluentNode element(String name, Stream<Node> stream) {
        return element(name, stream.toArray(Node[]::new));
    }

    default FluentNode element(String name, Iterable<Node> iterable) {
        return element(name, toArray(iterable));
    }

    default FluentNode element(String name, Node... nodes) {
        return ((FluentNode) owner().createElement(name)).add(nodes);
    }
```

that can be built up in to templates (e.g.,
[`HTMLTemplates`][HTMLTemplates]):

```java
    default FluentNode pre(String content) {
        return element("pre").content(content);
    }
```

Allowing the creation of methods to be trivially invoked to return XML
snippets:


```java
        return a(href, text)
                   .add(map.entrySet()
                        .stream()
                        .map(t -> attr(t.getKey(), t.getValue())));
```

to produce something like

```xml
<a href="https://www.rfc-editor.org/rfc/rfc2045.txt" target="newtab">RFC2045</a>
```

Complete [javadoc] is provided.


## Theory of Operation

An application can add a facade to a class hierarchy by extending
[`FacadeProxyInvocationHandler`][FacadeProxyInvocationHandler]<sup id="ref1">[1](#endnote1)</sup>
and implementing
[`getProxyClassFor(Object)`][FacadeProxyInvocationHandler.getProxyClassFor]
where the
[`invoke(Object,Method,Object[])`][FacadeProxyInvocationHandler.invoke]
"enhances" any eligible return types.  Conceptually:

```java
    public Object enhance(Object in) {
        Object out = null;
        Class<?> type = getProxyClassFor(in);

        if (type != null) {
            try {
                out =
                    type.getConstructor(InvocationHandler.class)
                    .newInstance(this);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        return (out != null) ? out : in;
    }

    protected abstract Class<?> getProxyClassFor(Object object);

    @Override
    public Object invoke(Object proxy, Method method, Object[] argv) throws Throwable {
        Object result = super.invoke(proxy, method, argv);

        return enhance(result);
    }
```

There are additional details that are discussed in the next section.  The
implementor must return a interface [`Class`][Class] to [`Proxy`][Proxy]
from `getProxyClassFor(Object)` for any `Class` to be enhanced.


## Implementation

[`Node`][Node] will be enhanced by [`FluentNode`][FluentNode] and
[`Document`][Document] will be enhanced by
[`FluentDocument`][FluentDocument].  Note: A `Node` does not necessarily
have to implement the sub-interface that corresponds to
[Node.getNodeType()][Node.getNodeType] so both the [`Object`][Object]'s
class hierarchy and node type are analyzed and the results are cached in the
[`getProxyClassFor(Object)`]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/xml/FluentNode.InvocationHandler.html#line.358) implementation.

```java
        private final HashMap<List<Class<?>>,Class<?>> map = new HashMap<>();
        ...
        @Override
        protected Class<?> getProxyClassFor(Object object) {
            Class<?> type = null;

            if (object instanceof Node && (! (object instanceof FluentNode))) {
                Node node = (Node) object;
                List<Class<?>> key =
                    Arrays.asList(NODE_TYPE_MAP.getOrDefault(node.getNodeType(), Node.class),
                                  node.getClass());

                type = map.computeIfAbsent(key, k -> compute(k));
            }

            return type;
        }

        private Class<?> compute(List<Class<?>> key) {
            LinkedHashSet<Class<?>> implemented =
                key.stream()
                .flatMap(t -> getImplementedInterfacesOf(t).stream())
                .filter(t -> Node.class.isAssignableFrom(t))
                .filter(t -> Node.class.getPackage().equals(t.getPackage()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            LinkedHashSet<Class<?>> interfaces =
                implemented.stream()
                .map(t -> fluent(t))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            interfaces.addAll(implemented);

            new ArrayList<>(interfaces)
                .stream()
                .forEach(t -> interfaces.removeAll(Arrays.asList(t.getInterfaces())));

            return getProxyClass(interfaces.toArray(new Class<?>[] { }));
        }
```

The corresponding
["fluent"]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/xml/FluentNode.InvocationHandler.html#line.398)
interface is found through reflection:

```java
        private Class<?> fluent(Class<?> type) {
            Class<?> fluent = null;

            if (Node.class.isAssignableFrom(type) && Node.class.getPackage().equals(type.getPackage())) {
                try {
                    String name =
                        String.format("%s.Fluent%s",
                                      FluentNode.class.getPackage().getName(),
                                      type.getSimpleName());

                    fluent = Class.forName(name).asSubclass(FluentNode.class);
                } catch (Exception exception) {
                }
            }

            return fluent;
        }
```

The [`DocumentBuilderFactory`][DocumentBuilderFactory],
[`FluentDocumentBuilderFactory`][FluentDocumentBuilderFactory], and
[`DocumentBuilder`][DocumentBuilder],
[`FluentDocument.Builder`][FluentDocument.Builder], implementations are both
straightforward.  The two `DocumentBuilder`
[methods]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/xml/FluentDocument.Builder.html#line.75)
that create new [`Document`s][Document] are implemented by creating a new
[`FluentNode.InvocationHandler`][FluentNode.InvocationHandler]:

```java
        @Override
        public FluentDocument newDocument() {
            Document document = builder.newDocument();

            return (FluentDocument) new FluentNode.InvocationHandler().enhance(document);
        }

        @Override
        public Document parse(InputSource in) throws SAXException, IOException {
            Document document = builder.parse(in);

            return (FluentDocument) new FluentNode.InvocationHandler().enhance(document);
        }
```

Creating a new [`FluentDocument`][FluentDocument] is as simple as:

```java
            document =
                FluentDocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
```

Unfortunately, the implementation as described so far will fail with an
error similar to:

```javastacktrace
...
[ERROR] Caused by: org.w3c.dom.DOMException: WRONG_DOCUMENT_ERR: A node is used in a different document than the one that created it.
[ERROR] 	at com.sun.org.apache.xerces.internal.dom.AttributeMap.setNamedItem(AttributeMap.java:86)
[ERROR] 	at ball.xml.FluentNode.add(FluentNode.java:180)
...
[ERROR] 	... 35 more
...
```

The `com.sun.org.apache.xerces.internal.dom` implementation classes expect
to have package access to other package classes.  This requires adjusting
the
[`invoke(Object,Method,Object[])`]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/lang/reflect/FacadeProxyInvocationHandler.html#line.164)
implementation to choose the wider of the [`Proxy`][Proxy] facade or the
*reverse* depending on the required context:

```java
    @Override
    public Object invoke(Object proxy, Method method, Object[] argv) throws Throwable {
        Object result = null;
        Class<?> declarer = method.getDeclaringClass();
        Object that = map.reverse.get(proxy);

        if (declarer.isAssignableFrom(Object.class)) {
            result = method.invoke(that, argv);
        } else {
            argv = reverseFor(method.getParameterTypes(), argv);

            if (declarer.isAssignableFrom(that.getClass())) {
                result = method.invoke(that, argv);
            } else {
                result = super.invoke(proxy, method, argv);
            }
        }

        return enhance(result);
    }
```

This requires keeping an [`IdentityHashMap`][IdentityHashMap] of enhanced
[`Object`][Object] to [`Proxy`][Proxy] and reverse:

```java
    private final ProxyMap map = new ProxyMap();

    public Object enhance(Object in) {
        Object out = null;

        if (! hasFacade(in)) {
            Class<?> type = getProxyClassFor(in);

            if (type != null) {
                out = map.computeIfAbsent(in, k -> compute(type));
            }
        }

        return (out != null) ? out : in;
    }

    private <T> T compute(Class<T> type) {
        T proxy = null;

        try {
            proxy =
                type.getConstructor(InvocationHandler.class)
                .newInstance(this);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        return proxy;
    }

    private class ProxyMap extends IdentityHashMap<Object,Object> {
        private final IdentityHashMap<Object,Object> reverse = new IdentityHashMap<>();

        public IdentityHashMap<Object,Object> reverse() { return reverse; }

        @Override
        public Object put(Object key, Object value) {
            reverse().put(value, key);

            return super.put(key, value);
        }
    }
```

and providing the necessary "reverse" methods contained in the
[source]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/lang/reflect/FacadeProxyInvocationHandler.html#line.104).


## Integration

[`AbstractTaglet`][AbstractTaglet] demonstrates the integration.  The class
must implement [`XMLServices`][XMLServices] and provide an implementation of
[`document()`][XMLServices.document].

```java
    private final FluentDocument document;
    ...
    protected AbstractTaglet(boolean isInlineTag, boolean inPackage,
                             boolean inOverview, boolean inField,
                             boolean inConstructor, boolean inMethod,
                             boolean inType) {
        ...
        try {
            ...
            document =
                FluentDocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();
            document
                .add(element("html",
                             element("head",
                                     element("meta",
                                             attr("charset", "utf-8"))),
                             element("body")));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
    ...
    @Override
    public FluentDocument document() { return document; }
```

[`AbstractTaglet`][AbstractTaglet] also implements
[`HTMLTemplates`][HTMLTemplates] which provides `default` methods for HTML
elements/nodes.  `HTMLTemplates` is further extended by
[`JavadocHTMLTemplates`][JavadocHTMLTemplates] to provide common HTML/XML
fragments required to generate Javadoc.


## Summary

The [`FacadeProxyInvocationHandler`][FacadeProxyInvocationHandler] combined
with specialized interfaces implementing `default` methods provides a
mechanism for extending an otherwise `final` class hierarchy.


<b id="endnote1">[1]</b>
[`FacadeProxyInvocationHandler`][FacadeProxyInvocationHandler] is a subclass
of [`DefaultInvocationHandler`][DefaultInvocationHandler] whose
[`invoke(Object,Method,Object[])`][DefaultInvocationHandler.invoke]
implementation is discussed in
["Adding Support to Java InvocationHandler Implementations for Interface
Default Methods"]).
[↩](#ref1)


[Java 8]: https://www.java.com/en/download/help/java8.html
[Default Interface Methods]: https://docs.oracle.com/javase/tutorial/java/IandI/defaultmethods.html
[Document Object Model (DOM)]: {{ page.javadoc.javase }}/org/w3c/dom/package-summary.html

[Javadoc]: https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html
[Taglet]: https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/taglet/overview.html

[Class]: {{ page.javadoc.javase }}/java/lang/Class.html?is-external=true
[DocumentBuilderFactory]: {{ page.javadoc.javase }}/javax/xml/parsers/DocumentBuilderFactory.html
[DocumentBuilder]: {{ page.javadoc.javase }}/javax/xml/parsers/DocumentBuilder.html
[Document]: {{ page.javadoc.javase }}/org/w3c/dom/Document.html
[Element]: {{ page.javadoc.javase }}/org/w3c/dom/Element.html
[IdentityHashMap]: {{ page.javadoc.javase }}/java/util/IdentityHashMap.html
[InvocationHandler]: {{ page.javadoc.javase }}/java/lang/reflect/InvocationHandler.html?is-external=true
[Node.getNodeType]: {{ page.javadoc.javase }}/org/w3c/dom/Node.html#getNodeType--
[Node]: {{ page.javadoc.javase }}/org/w3c/dom/Node.html
[Object]: {{ page.javadoc.javase }}/java/lang/Object.html
[Proxy]: {{ page.javadoc.javase }}/java/lang/reflect/Proxy.html

[javadoc]: {{ site.javadoc.url }}/{{ page.permalink }}/overview-summary.html
[AbstractTaglet]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/tools/javadoc/AbstractTaglet.html
[DefaultInvocationHandler.invoke]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/DefaultInvocationHandler.html#invoke-java.lang.Object-java.lang.reflect.Method-java.lang.Object:A-
[DefaultInvocationHandler]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/DefaultInvocationHandler.html
[FacadeProxyInvocationHandler.getProxyClassFor]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/FacadeProxyInvocationHandler.html#getProxyClassFor-java.lang.Object-
[FacadeProxyInvocationHandler.invoke]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/FacadeProxyInvocationHandler.html#invoke-java.lang.Object-java.lang.reflect.Method-java.lang.Object:A-
[FacadeProxyInvocationHandler]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/lang/reflect/FacadeProxyInvocationHandler.html
[FluentDocument.Builder]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentDocument.Builder.html
[FluentDocumentBuilderFactory]:{{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentDocumentBuilderFactory.html
[FluentDocument]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentDocument.html
[FluentNode.InvocationHandler]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentNode.InvocationHandler.html
[FluentNode]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/FluentNode.html
[HTMLTemplates]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/HTMLTemplates.html
[JavadocHTMLTemplates]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/tools/javadoc/JavadocHTMLTemplates.html
[XMLServices.document]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/XMLServices.html#document--
[XMLServices]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/xml/XMLServices.html

["Adding Support to Java InvocationHandler Implementations for Interface Default Methods"]: /article/2019-01-31-java-invocationhandler-interface-default-methods
