---
title: A java.net.ResponseCache Implementation
canonical_url: https://blog.hcf.dev/article/2019-01-30-java-responsecache-implementation/
tags:
 - Java
permalink: article/2019-01-30-java-responsecache-implementation
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
---

The Java [`URLConnection`][URLConnection] mechanism may be configured to use
a [`ResponseCache`][ResponseCache].  This article describes a
`ResponseCache` implementation.


## Implementation Outline

The implementation requires subclassing `ResponseCache`, providing
implementations of [`get(URI,String,Map<String,List<String>>)`][URI.get] and
[`put(URI,URLConnection)`][URI.put].  Non-trivial implementations of each
method require providing concrete implementations of
[`CacheResponse`][CacheResponse] and [`CacheRequest`][CacheRequest].  The
outline of the implementation is:

```java
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;

public class ResponseCacheImpl extends ResponseCache {

    /**
     * Default {@link ResponseCacheImpl}.
     */
    public static final ResponseCacheImpl DEFAULT = new ResponseCacheImpl();

    private ResponseCacheImpl() {
        super();
        /*
         * ...
         */
    }

    @Override
    public CacheResponse get(URI uri, String method,
                             Map<String,List<String>> headers) {
        CacheResponseImpl response = null;
        /*
         * ...
         */
        return response;
    }

    @Override
    public CacheRequest put(URI uri, URLConnection connection) {
        CacheRequestImpl request = null;
        /*
         * ...
         */
        return request;
    }

    public class CacheRequestImpl extends CacheRequest {
        /*
         * ...
         */
    }

    public class CacheResponseImpl extends CacheResponse {
        /*
         * ...
         */
    }
}
```

Note: The `get()` and `put()` methods may return `null` indicating that no
caching facility is available for that [`URI`][URI].


## Cache Design

The cache will be a simple file system hierarchy residing under
`${user.home}/.config/java/cache/`.  A cached [`URI`][URI] will map to a
directory which will exist and contain two files if the object is cached:
`BODY` and `HEADERS`.

```java
    private Path cache(URI uri) {
        Path path = cache.resolve(uri.getScheme().toLowerCase());
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();

        if (port > 0) {
            host += ":" + String.valueOf(port);
        }

        path = path.resolve(host);

        String string = uri.getPath();

        if (string != null) {
            for (String substring : string.split("[/]+")) {
                if (isNotEmpty(substring)) {
                    path = path.resolve(substring);
                }
            }
        }

        return path.normalize();
    }
```

No attempt will be made to cache "complex" [`URI`][URI]s. `isCacheable` is
defined as:

```java
    private boolean isCacheable(URI uri) {
        return (uri.isAbsolute()
                && (! uri.isOpaque())
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null);
    }
```

A [`URI`][URI] is cached if it `isCacheable()` and its body exists in the
cache:

```java
    private boolean isCached(URI uri) {
        return isCacheable(uri) && Files.exists(cache(uri).resolve(BODY));
    }
```


## ResponseCache.put(URI,URLConnection) Implementation

This method allows the caller to put an object in the cache.
[`URLConnection.getHeaderFields()`][URLConnection.getHeaderFields] are saved
along with the "body" using an [`XMLEncoder`][XMLEncoder].

```java
    @Override
    public CacheRequest put(URI uri, URLConnection connection) {
        CacheRequestImpl request = null;

        if (isCacheable(uri)) {
            if (! connection.getAllowUserInteraction()) {
                request =
                    new CacheRequestImpl(cache(uri),
                                         connection.getHeaderFields());
            }
        }

        return request;
    }

    public class CacheRequestImpl extends CacheRequest {
        private final Path path;
        private final Map<String,List<String>> headers;

        private CacheRequestImpl(Path path, Map<String,List<String>> headers) {
            super();

            this.path = Objects.requireNonNull(path);
            this.headers = Objects.requireNonNull(headers);
        }

        @Override
        public OutputStream getBody() throws IOException {
            Files.createDirectories(path);

            XMLEncoder encoder =
                new XMLEncoder(Files.newOutputStream(path.resolve(HEADERS)));

            encoder.writeObject(headers);
            encoder.close();

            return Files.newOutputStream(path.resolve(BODY));
        }
```


## ResponseCache.get(URI,String,Map<String,List<String>>) Implementation

This method attempts to retrieve the object from cache.  If it is cached,
`CacheResponseImpl` provides the previously saved headers and
[`InputStream`][InputStream] from the cached file.  Headers are deserialized
with an [`XMLDecoder`][XMLDecoder].

```java
    @Override
    public CacheResponse get(URI uri, String method,
                             Map<String,List<String>> headers) {
        CacheResponseImpl response = null;

        if (isCached(uri)) {
            response = new CacheResponseImpl(cache(uri));
        }

        return response;
    }

    public class CacheResponseImpl extends CacheResponse {
        private final Path path;

        private CacheResponseImpl(Path path) {
            super();

            this.path = Objects.requireNonNull(path);
        }

        @Override
        public Map<String,List<String>> getHeaders() throws IOException {
            XMLDecoder decoder =
                new XMLDecoder(Files.newInputStream(path.resolve(HEADERS)));
            @SuppressWarnings("unchecked")
            Map<String,List<String>> headers =
                (Map<String,List<String>>) decoder.readObject();

            decoder.close();

            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return Files.newInputStream(path.resolve(BODY));
        }
    }
```


## Installation

The [`ResponseCache`][ResponseCache] implementation must be configured with
the [`ResponseCache.setDefault(ResponseCache)`][ResponseCache.setDefault]
static method.  The following code fragment checks to see no other
`ResponseCache` is installed before installing the target.

```java
        if (ResponseCache.getDefault() == null) {
            ResponseCache.setDefault(ResponseCacheImpl.DEFAULT);
        }
```


## Ant Task

The critical portions of an Ant Task that uses
[`URLConnection`][URLConnection] configured with `ResponseCacheImpl` for
download is shown below.

```java
@AntTask("download")
@NoArgsConstructor @ToString
public class DownloadTask extends Task {
    static {
        if (ResponseCache.getDefault() == null) {
            ResponseCache.setDefault(ResponseCacheImpl.DEFAULT);
        }
    }

    @NotNull @Getter @Setter
    private URI uri = null;
    @NotNull @Getter @Setter
    private File toFile = null;

    @Override
    public void execute() throws BuildException {
        try {
            URLConnection connection = getUri().toURL().openConnection();

            IOUtils.copy(connection.getInputStream(),
                         new FileOutputStream(getToFile()));

            log(getUri() + " --> " + getToFile());
        } catch (BuildException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BuildException(exception);
        }
    }
}
```


## ResponseCacheImpl.java

The complete implementation:

```java
/*
 * $Id: README.md 6485 2020-07-18 03:45:11Z ball $
 *
 * Copyright 2019 Allen D. Ball.  All rights reserved.
 */
package ball.net;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermissions.asFileAttribute;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * {@link ResponseCache} implementation.
 *
 * @author {@link.uri mailto:ball@iprotium.com Allen D. Ball}
 * @version $Revision: 6485 $
 */
public class ResponseCacheImpl extends ResponseCache {

    /**
     * Default {@link ResponseCacheImpl}.
     */
    public static final ResponseCacheImpl DEFAULT = new ResponseCacheImpl();

    private static final String BODY = "BODY";
    private static final String HEADERS = "HEADERS";

    private final Path cache;

    private ResponseCacheImpl() {
        super();

        try {
            cache =
                Paths.get(System.getProperty("user.home"),
                          ".config", "java", "cache");

            Files.createDirectories(cache,
                                    asFileAttribute(fromString("rwx------")));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Override
    public CacheResponse get(URI uri, String method,
                             Map<String,List<String>> headers) {
        CacheResponseImpl response = null;

        if (isCached(uri)) {
            response = new CacheResponseImpl(cache(uri));
        }

        return response;
    }

    @Override
    public CacheRequest put(URI uri, URLConnection connection) {
        CacheRequestImpl request = null;

        if (isCacheable(uri)) {
            if (! connection.getAllowUserInteraction()) {
                request =
                    new CacheRequestImpl(cache(uri),
                                         connection.getHeaderFields());
            }
        }

        return request;
    }

    private Path cache(URI uri) {
        Path path = cache.resolve(uri.getScheme().toLowerCase());
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();

        if (port > 0) {
            host += ":" + String.valueOf(port);
        }

        path = path.resolve(host);

        String string = uri.getPath();

        if (string != null) {
            for (String substring : string.split("[/]+")) {
                if (isNotEmpty(substring)) {
                    path = path.resolve(substring);
                }
            }
        }

        return path.normalize();
    }

    private boolean isCached(URI uri) {
        return isCacheable(uri) && Files.exists(cache(uri).resolve(BODY));
    }

    private boolean isCacheable(URI uri) {
        return (uri.isAbsolute()
                && (! uri.isOpaque())
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null);
    }

    private void delete(Path path) throws IOException {
        Files.deleteIfExists(path.resolve(HEADERS));
        Files.deleteIfExists(path.resolve(BODY));
        Files.deleteIfExists(path);
    }

    public class CacheRequestImpl extends CacheRequest {
        private final Path path;
        private final Map<String,List<String>> headers;

        private CacheRequestImpl(Path path, Map<String,List<String>> headers) {
            super();

            this.path = Objects.requireNonNull(path);
            this.headers = Objects.requireNonNull(headers);
        }

        @Override
        public OutputStream getBody() throws IOException {
            Files.createDirectories(path);

            XMLEncoder encoder =
                new XMLEncoder(Files.newOutputStream(path.resolve(HEADERS)));

            encoder.writeObject(headers);
            encoder.close();

            return Files.newOutputStream(path.resolve(BODY));
        }

        @Override
        public void abort() {
            try {
                delete(path);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    public class CacheResponseImpl extends CacheResponse {
        private final Path path;

        private CacheResponseImpl(Path path) {
            super();

            this.path = Objects.requireNonNull(path);
        }

        @Override
        public Map<String,List<String>> getHeaders() throws IOException {
            XMLDecoder decoder =
                new XMLDecoder(Files.newInputStream(path.resolve(HEADERS)));
            @SuppressWarnings("unchecked")
            Map<String,List<String>> headers =
                (Map<String,List<String>>) decoder.readObject();

            decoder.close();

            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return Files.newInputStream(path.resolve(BODY));
        }
    }
}
```

[CacheRequest]: {{ page.javadoc.javase }}/java/net/CacheRequest.html
[CacheResponse]: {{ page.javadoc.javase }}/java/net/CacheResponse.html
[InputStream]: {{ page.javadoc.javase }}/java/io/InputStream.html
[ResponseCache]: {{ page.javadoc.javase }}/java/net/ResponseCache.html?is-external=true
[URI.get]: {{ page.javadoc.javase }}/java/net/ResponseCache.html#get-java.net.URI-java.lang.String-java.util.Map-
[URI.put]: {{ page.javadoc.javase }}/java/net/ResponseCache.html#put-java.net.URI-java.net.URLConnection-
[URI]: {{ page.javadoc.javase }}/java/net/URI.html
[URLConnection.getHeaderFields]: {{ page.javadoc.javase }}/java/net/URLConnection.html#getHeaderFields--
[URLConnection]: {{ page.javadoc.javase }}/java/net/URLConnection.html?is-external=true
[XMLDecoder]: {{ page.javadoc.javase }}/java/beans/XMLDecoder.html
[XMLEncoder]: {{ page.javadoc.javase }}/java/beans/XMLEncoder.html
[ResponseCache.setDefault]: {{ page.javadoc.javase }}/java/net/ResponseCache.html#setDefault-java.net.ResponseCache-
