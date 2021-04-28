---
title: >-
    Adding Support to Java InvocationHandler Implementations for
    Interface Default Methods
canonical_url: https://blog.hcf.dev/article/2019-01-31-java-invocationhandler-interface-default-methods/
tags:
 - Java
permalink: article/2019-01-31-java-invocationhandler-interface-default-methods
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
excerpt_separator: <!--more-->
---

[Java 8] introduced default methods to interfaces.  Existing
[`InvocationHandler`][InvocationHandler] implementations will not invoke
default interface methods.  This short article documents the necessary
changes.
<!--more-->
Note: It first describes the implementation based on a reading of the
documents and then provides a working implementation for Java 8.

Given the [`InvocationHandler`][InvocationHandler] implementation:

``` java
    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;
        /*
         * Logic to calculate result.
         */
        return result;
    }
```

the [Java 8] solution appears to be to extend the implementation to invoke any
interface default methods through a [`MethodHandle`][MethodHandle]:

``` java
    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;
        Class<?> declaringClass = method.getDeclaringClass();

        if (method.isDefault()) {
            result =
                MethodHandles.lookup()
                .in(declaringClass)
                .unreflectSpecial(method, declaringClass)
                .bindTo(proxy)
                .invokeWithArguments(argv);
        } else {
            /*
             * Logic to calculate result.
             */
        }

        return result;
    }
```

If the [`Method`][Method] is "default" then the target interface method is
invoked.  Otherwise, the [`InvocationHandler`][InvocationHandler]
implementation processes as before.  Any interface default method *should*
be invoked by:

1. Finding the
   [`MethodHandles.Lookup`][MethodHandles.Lookup] through
   `MethodHandles.lookup().in(declaringClass)`,
2. Get a [`MethodHandle`][MethodHandle] bypassing any overriding methods
   through `.unreflectSpecial(method, declaringClass)`, and,
3. Invoke the method on the [`proxy`][Proxy] with
   `.bindTo(proxy).invokeWithArguments(argv)`

Unfortunately, this does not work if the `declaringClass` is not
"private-accessible" to the caller (which is most of the time) resulting in:

``` bash
Caused by: java.lang.IllegalAccessException: no private access for invokespecial: interface package1.SomeInterface, from package1.SomeInterface/public
	at java.lang.invoke.MemberName.makeAccessException(MemberName.java:850)
	at java.lang.invoke.MethodHandles$Lookup.checkSpecialCaller(MethodHandles.java:1572)
	at java.lang.invoke.MethodHandles$Lookup.unreflectSpecial(MethodHandles.java:1231)
	at package2.InvocationHandlerImpl.invoke(InvocationHandlerImpl.java:59)
```

The actual [Java 8] solution is:

``` java
    @Override
    public Object invoke(Object proxy,
                         Method method, Object[] argv) throws Throwable {
        Object result = null;
        Class<?> declaringClass = method.getDeclaringClass();

        if (method.isDefault()) {
            Constructor<MethodHandles.Lookup> constructor =
                MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);

            constructor.setAccessible(true);

            result =
                constructor.newInstance(declaringClass)
                .in(declaringClass)
                .unreflectSpecial(method, declaringClass)
                .bindTo(proxy)
                .invokeWithArguments(argv);
        } else {
            /*
             * Logic to calculate result.
             */
        }

        return result;
    }
```

This will not work in [Java 9]+.  In Java 9 and subsequent releases, the
solution should be based on `MethodHandles.Lookup.findSpecial()` and/or
`MethodHandles.privateLookupIn()`.

[Java 8]: https://www.java.com/en/download/help/java8.html
[Java 9]: https://www.oracle.com/java/java9.html

[InvocationHandler]: {{ page.javadoc.javase }}/java/lang/reflect/InvocationHandler.html?is-external=true
[MethodHandle]: {{ page.javadoc.javase }}/java/lang/invoke/MethodHandle.html
[MethodHandles.Lookup]: {{ page.javadoc.javase }}/java/lang/invoke/MethodHandles.Lookup.html
[Method]: {{ page.javadoc.javase }}/java/lang/reflect/Method.html
[Proxy]: {{ page.javadoc.javase }}/java/lang/reflect/Proxy.html
