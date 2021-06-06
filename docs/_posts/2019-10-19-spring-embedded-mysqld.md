---
title: Spring Embedded MySQL Server
canonical_url: https://blog.hcf.dev/article/2019-10-19-spring-embedded-mysqld
tags:
 - Java
 - Spring
 - MySQL
permalink: article/2019-10-19-spring-embedded-mysqld
javadoc:
  javase: >-
    https://docs.oracle.com/javase/8/docs/api
  spring: >-
    https://docs.spring.io/spring/docs/5.3.6/javadoc-api
  spring-boot: >-
    https://docs.spring.io/spring-boot/docs/2.4.5/api
  spring-security: >-
    https://docs.spring.io/spring-security/site/docs/5.4.6/api
excerpt_separator: <!--more-->
---

This article describes a method to create a `mysqld` [`Process`][Process]
managed by the Spring Boot Application conditioned on the definition of an
`application.properties` property, `${mysqld.home}`.
<!--more-->
If the property is defined, the corresponding bean
[`@Configuration`][Configuration] with invoke `mysqld` with the
`--initialize-insecure` option to create the database and then create and
manage the `mysqld` `Process` for the life of the Spring Boot application
including graceful shutdown at application shutdown.

Complete [javadoc] is provided.


## Theory of Operation

The [`MysqldConfiguration`][MysqldConfiguration] is
[`@ConditionalOnProperty`][ConditionalOnProperty] annotation for
`${mysqld.home}`; if the property is defined, the [`Process`][Process]
[`@Bean`][Bean] is created running the MySQL server.  If the
`${mysqld.datadir}` does not exist, `mysqld` is invoked with the
`--initialize-insecure` option to create the database first.  A
[`@PreDestroy`][PreDestroy] method is defined to destroy the `mysqld`
`Process` at application shutdown.

``` java
@Configuration
@ConditionalOnProperty(name = "mysqld.home", havingValue = "")
@NoArgsConstructor @ToString @Log4j2
public class MysqldConfiguration {
    @Value("${mysqld.home}")
    private File home;

    @Value("${mysqld.defaults.file:${mysqld.home}/my.cnf}")
    private File defaults;

    @Value("${mysqld.datadir:${mysqld.home}/data}")
    private File datadir;

    @Value("${mysqld.port}")
    private Integer port;

    @Value("${mysqld.socket:${mysqld.home}/socket}")
    private File socket;

    @Value("${logging.path}/mysqld.log")
    private File console;

    private volatile Process mysqld = null;

    ...

    @Bean
    public Process mysqld() throws IOException {
        if (mysqld == null) {
            synchronized (this) {
                if (mysqld == null) {
                    Files.createDirectories(home.toPath());
                    Files.createDirectories(datadir.toPath().getParent());
                    Files.createDirectories(console.toPath().getParent());

                    String defaultsArg = "--no-defaults";

                    if (defaults.exists()) {
                        defaultsArg = "--defaults-file=" + defaults.getAbsolutePath();
                    }

                    String datadirArg = "--datadir=" + datadir.getAbsolutePath();
                    String socketArg = "--socket=" + socket.getAbsolutePath();
                    String portArg = "--port=" + port;

                    if (! datadir.exists()) {
                        try {
                            new ProcessBuilder("mysqld", defaultsArg, datadirArg, "--initialize-insecure")
                                .directory(home)
                                .inheritIO()
                                .redirectOutput(Redirect.to(console))
                                .redirectErrorStream(true)
                                .start()
                                .waitFor();
                        } catch (InterruptedException exception) {
                        }
                    }

                    if (datadir.exists()) {
                        socket.delete();

                        mysqld =
                            new ProcessBuilder("mysqld", defaultsArg, datadirArg, socketArg, portArg)
                            .directory(home)
                            .inheritIO()
                            .redirectOutput(Redirect.appendTo(console))
                            .redirectErrorStream(true)
                            .start();

                        while (! socket.exists()) {
                            try {
                                mysqld.waitFor(15, SECONDS);
                            } catch (InterruptedException exception) {
                            }

                            if (mysqld.isAlive()) {
                                continue;
                            } else {
                                throw new IllegalStateException("mysqld not started");
                            }
                        }
                    } else {
                        throw new IllegalStateException("mysqld datadir does not exist");
                    }
                }
            }
        }

        return mysqld;
    }

    @PreDestroy
    public void destroy() {
        if (mysqld != null) {
            try {
                for (int i = 0; i < 8; i+= 1) {
                    if (mysqld.isAlive()) {
                        mysqld.destroy();
                        mysqld.waitFor(15, SECONDS);
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException exception) {
            }

            try {
                if (mysqld.isAlive()) {
                    mysqld.destroyForcibly().waitFor(60, SECONDS);
                }
            } catch (InterruptedException exception) {
            }
        }
    }
}
```

The `mysqld` server is configured with the `--socket=${mysqld.socket}`
option for the purpose of notifying the Spring Boot Application that the
server has started: While the [MySQL Connector/J] does not support UNIX
domain sockets, the above code waits for the `mysqld` server to create the
socket to be sure the server is running before continuing.  The
[`MysqldComponent`][MysqldComponent] will simply monitor that the `Process`
is still alive.  This [`@Component`][Component] is dependent on the `mysqld`
[`@Bean`][Bean] which in turn is dependent on the `${mysqld.home}` property.

``` java
@Component
@ConditionalOnBean(name = { "mysqld" })
@NoArgsConstructor @ToString @Log4j2
public class MysqldComponent {
    @Autowired private Process mysqld;

    @Scheduled(fixedRate = 15 * 1000)
    public void run() {
        if (mysqld != null) {
            if (mysqld.isAlive()) {
                try {
                    mysqld.waitFor(15, SECONDS);
                } catch (InterruptedException exception) {
                }
            } else {
                throw new IllegalStateException("mysqld is not running");
            }
        }
    }
}
```

Per the direction of the
[Spring Boot Reference Guide](https://docs.spring.io/spring-boot/docs/2.4.5/reference/html/howto.html#howto-configure-a-component-that-is-used-by-JPA),
[`EntityManagerFactoryComponent`][EntityManagerFactoryComponent] is provided
to indicate the `mysqld` [`Process`][Process] is required by JPA.

``` java
@Component
@ConditionalOnProperty(name = "mysqld.home", havingValue = "")
@ToString @Log4j2
public class EntityManagerFactoryComponent extends EntityManagerFactoryDependsOnPostProcessor {
    @Autowired private Process mysqld;

    public EntityManagerFactoryComponent() { super("mysqld"); }
}
```

An application may integrate this functionality by annotating some component
(presumably one that depends on a
[`Repository`](https://docs.spring.io/spring-data/commons/docs/2.4.5/api/org/springframework/data/repository/Repository.html?is-external=true)
or
[`JpaRepository`](https://docs.spring.io/spring-data/jpa/docs/2.4.5/api/org/springframework/data/jpa/repository/JpaRepository.html?is-external=true))
with:

``` java
@Component
@ComponentScan(basePackageClasses = { ball.spring.mysqld.MysqldComponent.class })
public class SomeComponent {
    ...
}
```


## Shell Script

The following shell script may be dropped into the `${mysqld.home}`
directory to conveniently start a MySQL server from the shell.

``` bash
#!/bin/bash

PRG="$0"

while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done

cd $(dirname "$PRG")

MYCNF=$(pwd)/my.cnf
DATADIR=$(pwd)/data
SOCKET=$(pwd)/socket

if [ ! -f "${MYCNF}" ]; then
    cat > "${MYCNF}" <<EOF
[mysqld]
general_log = ON
log_output = TABLE
EOF
fi

DEFAULTS_OPT=--no-defaults
DATADIR_OPT=--datadir="${DATADIR}"

if [ -f "${MYCNF}" ]; then
    DEFAULTS_OPT=--defaults-file="${MYCNF}"
fi

if [ ! -d "${DATADIR}" ]; then
    mysqld "${DEFAULTS_OPT}" "${DATADIR_OPT}" --initialize-insecure
fi

exec mysqld "${DEFAULTS_OPT}" "${DATADIR_OPT}" --socket="${SOCKET}"
```


## Summary

The technique described here may be used with with other database
applications (e.g., PostgreSQL).


[MySQL Connector/J]: https://dev.mysql.com/doc/connector-j/8.0/en/

[Process]: {{ page.javadoc.javase }}/java/lang/Process.html

[PreDestroy]: https://javaee.github.io/javaee-spec/javadocs/javax/annotation/PreDestroy.html?is-external=true

[Bean]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Bean.html?is-external=true
[Component]: {{ page.javadoc.spring }}/org/springframework/stereotype/Component.html?is-external=true
[ConditionalOnProperty]: https://docs.spring.io/spring-boot/docs/2.4.5/api/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html?is-external=true
[Configuration]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Configuration.html?is-external=true

[javadoc]: {{ site.javadoc.url }}/{{ page.permalink }}/allclasses-noframe.html
[EntityManagerFactoryComponent]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/mysqld/EntityManagerFactoryComponent.html
[MysqldComponent]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/mysqld/MysqldComponent.html
[MysqldConfiguration]: {{ site.javadoc.url }}/{{ page.permalink }}/ball/spring/mysqld/MysqldConfiguration.html
