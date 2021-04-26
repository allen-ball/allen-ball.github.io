---
title: >-
    Spring Boot Part 7: Spring Security, Basic Authentication
    and Form Login, and Oauth2
canonical_url: https://blog.hcf.dev/article/2020-10-31-spring-boot-part-07/
tags:
 - Java
 - Spring Boot
 - Spring Security
 - oauth
 - oidc
permalink: article/2020-10-31-spring-boot-part-07
---

## Introduction

This article explores integrating
[Spring Security](https://docs.spring.io/spring-security/site/docs/5.4.6/reference/html5/)
into a
[Spring Boot](https://docs.spring.io/spring-boot/docs/2.4.5/reference/html/index.html)
application.  Specifically, it will examine:

1. Managing users' credentials (IDs and passwords) and granted authorities

2. Creating a Spring MVC Controller with Spring Method Security and
[Thymeleaf](https://www.thymeleaf.org/)
[Spring Security Integration Modules](https://github.com/thymeleaf/thymeleaf-extras-springsecurity)
(to provide features such as customized menus corresponding to a user's
grants)

3. Creating a REST controller with Basic Authentication and Spring Method
Security

The MVC application and REST controller will each have functions requiring
various granted authorities.  E.g., a "who-am-i" function may be executed by
a "USER" but the "who" function will require 'ADMINISTRATOR" authority while
"logout" and "change password" will simply require the user is
authenticated.  The MVC application will also use the Spring Security
Thymeleaf Dialect to provide menus in the context of the authorities granted
to the user.

After creating the baseline application, this article will then explore
integrating OAuth authentication.

Source code for the
[series](https://github.com/allen-ball/spring-boot-web-server)
and for this
[part](https://github.com/allen-ball/spring-boot-web-server/tree/master/part-07)
are available on [Github](https://github.com/allen-ball).

## Application

The following subsections outline creating and running the baseline
application.

### Prerequisites

A
[`PasswordEncoder`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/crypto/password/PasswordEncoder.html?is-external=true)
must be configured.  This is straightforward:[^1]

[^1]: Implementing a `PasswordEncoder` is discussed in detail in
[Spring PasswordEncoder Implementation](/article/2019-06-03-spring-passwordencoder-implementation/).

<figcaption style="text-align: center">
  PasswordEncoderConfiguration
</figcaption>
```java
@Configuration
@NoArgsConstructor @ToString @Log4j2
public class PasswordEncoderConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

The returned
[`DelegatingPasswordEncoder`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/crypto/password/DelegatingPasswordEncoder.html?is-external=true)
will decrypt most formats known to Spring and will encrypt using a
[`BCryptPasswordEncoder`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html)
for storage.

Users' credentials and granted authorities are stored in a database
(configured at runtime) and accessed through
[JPA](https://docs.oracle.com/javaee/7/api/javax/persistence/package-summary.html).
The `Credential`
[`@Entity`](https://docs.oracle.com/javaee/7/api/javax/persistence/Entity.html)
and
[`JpaRepository`](https://docs.spring.io/spring-data/jpa/docs/2.4.5/api/org/springframework/data/jpa/repository/JpaRepository.html?is-external=true)
are shown below.

<figcaption style="text-align: center">Credential</figcaption>
```java
@Entity
@Table(catalog = "application", name = "credentials")
@Data @NoArgsConstructor
public class Credential {
    @Id @Column(length = 64, nullable = false, unique = true)
    @NotBlank @Email
    private String email = null;

    @Lob @Column(nullable = false)
    @NotBlank
    private String password = null;
}
```

<figcaption style="text-align: center">CredentialRepository</figcaption>
```java
@Repository
@Transactional(readOnly = true)
public interface CredentialRepository extends JpaRepository<Credential,String> {
}
```

The implementations of `Authority` and `AuthorityRepository` are nearly
identical with the `password` property/column replaced with `grants`, a
`AuthoritiesSet` (`Set<Authorities>`) with a
[`@Converter`](https://docs.oracle.com/javaee/7/api/javax/persistence/Converter.html)-annotated
[`AttributeConverter`](https://docs.oracle.com/javaee/7/api/javax/persistence/AttributeConverter.html)
to convert to and from a comma-separated string of `Authorities`
([`Enum`](https://docs.oracle.com/javase/8/docs/api/java/lang/Enum.html))
names for storing in the database.

<figcaption style="text-align: center">Authorities</figcaption>
```java
public enum Authorities { USER, ADMINISTRATOR };
```

The generated tables are:

```sql
mysql> DESCRIBE credentials;
+----------+-------------+------+-----+---------+-------+
| Field    | Type        | Null | Key | Default | Extra |
+----------+-------------+------+-----+---------+-------+
| email    | varchar(64) | NO   | PRI | NULL    |       |
| password | longtext    | NO   |     | NULL    |       |
+----------+-------------+------+-----+---------+-------+
2 rows in set (0.00 sec)

mysql> DESCRIBE authorities;
+--------+--------------+------+-----+---------+-------+
| Field  | Type         | Null | Key | Default | Extra |
+--------+--------------+------+-----+---------+-------+
| email  | varchar(64)  | NO   | PRI | NULL    |       |
| grants | varchar(255) | NO   |     | NULL    |       |
+--------+--------------+------+-----+---------+-------+
2 rows in set (0.00 sec)
```

The `CredentialRepository` and `AuthorityRepository` are injected into a
[`UserDetailsService`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/core/userdetails/UserDetailsService.html)
implementation to provide
[`UserDetails`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/core/userdetails/UserDetails.html).

<figcaption style="text-align: center">
  UserServicesConfiguration - UserDetailsService @Bean
</figcaption>
```java
@Configuration
@NoArgsConstructor @ToString @Log4j2
public class UserServicesConfiguration {
    @Autowired private CredentialRepository credentialRepository = null;
    @Autowired private AuthorityRepository authorityRepository = null;

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl();
    }
    ...
    @NoArgsConstructor @ToString
    private class UserDetailsServiceImpl implements UserDetailsService {
        @Override
        @Transactional(readOnly = true)
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            User user = null;

            try {
                Optional<Credential> credential = credentialRepository.findById(username);
                Optional<Authority> authority = authorityRepository.findById(username);

                user =
                    new User(username,
                             credential.get().getPassword(),
                             authority.map(t -> t.getGrants().asGrantedAuthorityList())
                             .orElse(AuthorityUtils.createAuthorityList()));
            } catch (UsernameNotFoundException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new UsernameNotFoundException(username);
            }

            return user;
        }
    }
    ...
}
```

Separate
[`WebSecurityConfigurer`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/config/annotation/web/WebSecurityConfigurer.html)
instances will be configured for the `RestControllerImpl` (`/api/**`) and
`ControllerImpl` (`/**`) but each will share the same super-class where the
`PasswordEncoder` and `UserDetailsService` configured above will be injected
and configured.

<figcaption style="text-align: center">WebSecurityConfigurerImpl</figcaption>
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@NoArgsConstructor(access = PRIVATE) @Log4j2
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    @Autowired private UserDetailsService userDetailsService = null;
    @Autowired private PasswordEncoder passwordEncoder = null;

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder);
    }
    ...
}
```

The `WebSecurityConfigurer` for the REST controller
(`WebSecurityConfigurerImpl.API`) must be ordered before the configurer for
the MVC controller (`@Order(1)`) because otherwise its path-space,
`/api/**`, would be included in that of the MVC controller, `/**`.

The configuration:

* Requires requests are authenticated
* Disables Cross-Site Request Forgery checks
* Configures Basic Authentication

<a name="authenticationEntryPoint">
<figcaption style="text-align: center">
  WebSecurityConfigurerImpl.API
</figcaption>
```java
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...
    @Configuration
    @Order(1)
    @NoArgsConstructor @ToString
    public static class API extends WebSecurityConfigurerImpl {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/api/**")
                .authorizeRequests(t -> t.anyRequest().authenticated())
                .csrf(t -> t.disable())
                .httpBasic(t -> t.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
        }
    }
    ...
}
```

The
[`HttpStatusEntryPoint`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/web/authentication/HttpStatusEntryPoint.html)
is configured to prevent authentication failures from redirecting to the
`/error` page configured for the MVC controller.
</a>

The `WebSecurityConfigurerImpl.UI` configuration:

* Ignores security checks on static assets
* Requires requests are authenticated
* Configures Form Login
* Configures a Logout Handler (alleviating the need to implement a
corresponding MVC controller method)

<a name="logoutRequestMatcher">
<figcaption style="text-align: center">
  WebSecurityConfigurerImpl.UI
</figcaption>
```java
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...
    @Configuration
    @Order(2)
    @NoArgsConstructor @ToString
    public static class UI extends WebSecurityConfigurerImpl {
        private static final String[] IGNORE = {
            "/css/**", "/js/**", "/images/**", "/webjars/**", "/webjarsjs"
        };

        @Override
        public void configure(WebSecurity web) {
            web.ignoring().antMatchers(IGNORE);
        }
        ...
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/**")
                .authorizeRequests(t -> t.anyRequest().authenticated())
                .formLogin(t -> t.loginPage("/login").permitAll())
                .logout(t -> t.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                              .logoutSuccessUrl("/").permitAll());
            ...
        }
    }
    ...
}
```

The outline of the MVC is shown below.  (The individual methods will be
described in detail in a subsequent chapter.)  There are two things to note:

1. The template resolver is configured to use
["decoupled template logic"](https://www.thymeleaf.org/doc/tutorials/3.0/usingthymeleaf.html#decoupled-template-logic)

2. All methods (including a custom `/error` mapping) return the same view
(Thymeleaf template)
</a>

<figcaption style="text-align: center">ControllerImpl</figcaption>
```java
@Controller
@RequestMapping(value = { "/" })
@NoArgsConstructor @ToString @Log4j2
public class ControllerImpl implements ErrorController {
    private static final String VIEW = ControllerImpl.class.getPackage().getName();
    ...
    @Autowired private SpringResourceTemplateResolver resolver = null;
    ...
    @PostConstruct
    public void init() { resolver.setUseDecoupledLogic(true); }

    @PreDestroy
    public void destroy() { }
    ...
    @RequestMapping(value = { "/" })
    public String root() {
        return VIEW;
    }
    ...
    @RequestMapping(value = "${server.error.path:${error.path:/error}}")
    public String error() { return VIEW; }

    @ExceptionHandler
    @ResponseStatus(value = INTERNAL_SERVER_ERROR)
    public String handle(Model model, Exception exception) {
        model.addAttribute("exception", exception);

        return VIEW;
    }
    ...
}
```

The common Thymeleaf template is outlined below.  `<li/>` elements provide
drop-down menus which are activated by security dialect `sec:authorize`
attributes.  A `th:switch` attribute provides a `<section/>` "case" element
for each supported path.  A form is displayed if the "form" attribute is set
in the
[`Model`](https://docs.spring.io/spring/docs/5.3.6/javadoc-api/org/springframework/ui/Model.html).
And, if the user is authenticated, their granted authorities are displayed
in the right of the footer with the `sec:authentication` attribute.

<figcaption style="text-align: center">application.html</figcaption>
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:xmlns="@{http://www.w3.org/1999/xhtml}">
  <head>...</head>
  <body>
    <header>
      <nav th:ref="navbar">
        <th:block th:ref="container">
          ...
          <div th:ref="navbar-menu">
            ...
            <ul th:ref="navbar-end">
              <li th:ref="navbar-item" sec:authorize="hasAuthority('ADMINISTRATOR')">
                <button th:text="'Administrator'"/>
                <ul th:ref="navbar-dropdown">...</ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="hasAuthority('USER')">
                <button th:text="'User'"/>
                <ul th:ref="navbar-dropdown">...</ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="isAuthenticated()">
                <button sec:authentication="name"/>
                <ul th:ref="navbar-dropdown">...</ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="!isAuthenticated()">
                <a th:text="'Login'" th:href="@{/login}"/>
              </li>
            </ul>
          </div>
        </th:block>
      </nav>
    </header>
    <main th:unless="${#ctx.containsVariable('exception')}"
          th:switch="${#request.servletPath}">
      <section th:case="'/who'">...</section>
      <section th:case="'/who-am-i'">...</section>
      <section th:case="'/error'">...</section>
      <section th:case="*">
        <th:block th:if="${#ctx.containsVariable('form')}">
          <th:block th:insert="~{${#execInfo.templateName + '/' + form.class.simpleName}}"/>
        </th:block>
        <p th:if="${#ctx.containsVariable('exception')}" th:text="${exception}"/>
      </section>
    </main>
    <main th:if="${#ctx.containsVariable('exception')}">
      <section>...</section>
    </main>
    <footer>
      <nav th:ref="navbar">
        <div th:ref="container">
          ...
          <span th:ref="right">
            <th:block sec:authorize="isAuthenticated()">
              <span sec:authentication="authorities"/>
            </th:block>
          </span>
        </div>
      </nav>
    </footer>
    ...
  </body>
</html>
```

[Bootstrap](https://getbootstrap.com/) attributes are added
through the "decoupled template logic" expressed in
`src/main/resources/templates/application.th.xml`.  (The mechanics of
decoupled template logic are not discussed further in this article.)

<a name="ExceptionHandler">
The outline of the REST controller is shown below.  The common exception
handler returns an HTTP 403 code for security-related exceptions.  This
combined with the Basic Authentication entry point setting in the
`WebSecurityConfigurer` prevents the REST controller from redirecting in the
event of an Exception.

<figcaption style="text-align: center">RestControllerImpl</figcaption>
```java
@RestController
@RequestMapping(value = { "/api/" }, produces = APPLICATION_JSON_VALUE)
@NoArgsConstructor @ToString @Log4j2
public class RestControllerImpl {
    ...
    @ExceptionHandler({ AccessDeniedException.class, SecurityException.class })
    public ResponseEntity<Object> handleFORBIDDEN() {
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }
}
```
</a>

### Runtime Environment

The POM (`pom.xml`) has a similar `spring-boot:run` profile to that
described in
[part 1](/article/2019-11-16-spring-boot-part-01/) of this
series.  The relevant parts of the
[`application.properties`](https://github.com/allen-ball/spring-boot-web-server/blob/master/part-07/application.properties)
file are shown below.

<figcaption style="text-align: center">application.properties</figcaption>
```properties
spring.jpa.format-sql: true
spring.jpa.hibernate.ddl-auto: create
spring.jpa.open-in-view: true
spring.jpa.show-sql: false

spring.datasource.initialization-mode: always
spring.datasource.data: file:data.sql
...
```

While the above specifies the contents of `data.sql` is to be loaded to the
Spring data source, it does not configure the data source.  Two additional
profiles are provide to configure an `hsqldb` or `mysql` data source.  The
`hsqldb` profile and application properties are shown below.

```xml
    <profile>
      <id>hsqldb</id>
      <dependencies>
        <dependency>
          <groupId>org.hsqldb</groupId>
          <artifactId>hsqldb</artifactId>
          <scope>runtime</scope>
        </dependency>
      </dependencies>
      <build>
        <plugins>
          <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
              <profiles combine.children="append">
                <profile>hsqldb</profile>
              </profiles>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
```

<figcaption style="text-align: center">
  application-hsqldb.properties
</figcaption>
```properties
spring.datasource.driver-class-name: org.hsqldb.jdbc.JDBCDriver
spring.datasource.url: jdbc:hsqldb:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.username: sa
spring.datasource.password:
```

The `mysql` profile requires the embedded MySQL server described in
[Spring Embedded MySQL Server](/article/2019-10-19-spring-embedded-mysqld/)
and packaged in the starter described in
[part 6](/article/2020-07-19-spring-boot-part-06/)
of this series.

```xml
    <profile>
      <id>mysql</id>
      <repositories>...</repositories>
      <dependencies>
        <dependency>
          <groupId>ball</groupId>
          <artifactId>ball-spring-mysqld-starter</artifactId>
          <version>2.1.0</version>
        </dependency>
      </dependencies>
      <build>
        ...
      </build>
    </profile>
```

<figcaption style="text-align: center">
  application-mysql.properties
</figcaption>
```properties
spring.jpa.hibernate.naming.implicit-strategy: default
spring.jpa.hibernate.naming.physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

spring.datasource.driver-class-name: com.mysql.cj.jdbc.Driver
spring.datasource.url: jdbc:mysql://localhost:${mysqld.port}/application?serverTimezone=UTC&createDatabaseIfNotExist=true
spring.datasource.username: root

mysqld.home: target/mysql
mysqld.port: 3306
```

Depending on the desired database selection, run either
`mvn -Pspring-boot:run,hsqldb` or `mvn -Pspring-boot:run,mysql` to start the
application server.

[`data.sql`](https://github.com/allen-ball/spring-boot-web-server/blob/master/part-07/data.sql)
defines two users: `user@example.com` who is granted "USER" authority and
`admin@example.com` who is granted "USER" and "ADMINISTRATOR" authorities.

<figcaption style="text-align: center">data.sql</figcaption>
```sql
INSERT INTO credentials (email, password)
       VALUES ('admin@example.com', '{noop}abcdef'),
              ('user@example.com', '{noop}123456');
INSERT INTO authorities (email, grants)
       VALUES ('admin@example.com', 'ADMINISTRATOR,USER'),
              ('user@example.com', 'USER');
```

The result of executing the above SQL is shown below.

```command-line
mysql> SELECT * FROM credentials;
+-------------------+--------------+
| email             | password     |
+-------------------+--------------+
| admin@example.com | {noop}abcdef |
| user@example.com  | {noop}123456 |
+-------------------+--------------+
2 rows in set (0.00 sec)

mysql> SELECT * FROM authorities;
+-------------------+--------------------+
| email             | grants             |
+-------------------+--------------------+
| admin@example.com | ADMINISTRATOR,USER |
| user@example.com  | USER               |
+-------------------+--------------------+
2 rows in set (0.00 sec)
```

For purposes of demonstration, the above passwords are exceedingly weak and
unencrypted.  Passwords may be encrypted outside the application with the
`htpasswd` command:

```command-line
$ htpasswd -bnBC 10 "" 123456 | tr -d ':\n' | sed 's/$2y/$2a/'
$2a$10$PJO7Bxx9u9JHnZ0lhHJ2dO5WwWwGrDvBdy82mV/KHUw/b1Us1yZS6
```

Whose output may be used to set (UPDATE) `user@example.com`'s password to
`{BCRYPT}$2a$10$PJO7Bxx9u9JHnZ0lhHJ2dO5WwWwGrDvBdy82mV/KHUw/b1Us1yZS6`.

The next section discusses the MVC controller.

### MVC Controller

Navigating to <http://localhost:8080/login/> will present:

![](/assets/{{ page.permalink }}/application-01-form-login.png)

The portion of the Thymeleaf template that generates the navbar buttons and
drop-down menus is shown below.  The `sec:authorize` expressions are
evaluated to determine if Thymeleaf renders the corresponding HTML.

```xml
            <ul th:ref="navbar-end">
              <li th:ref="navbar-item" sec:authorize="hasAuthority('ADMINISTRATOR')">
                <button th:text="'Administrator'"/>
                <ul th:ref="navbar-dropdown">
                  <li><a th:text="'Who'" th:href="@{/who}"/></li>
                </ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="hasAuthority('USER')">
                <button th:text="'User'"/>
                <ul th:ref="navbar-dropdown">
                  <li><a th:text="'Who Am I?'" th:href="@{/who-am-i}"/></li>
                </ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="isAuthenticated()">
                <button sec:authentication="name"/>
                <ul th:ref="navbar-dropdown">
                  <li><a th:text="'Change Password'" th:href="@{/password}"/></li>
                  <li><a th:text="'Logout'" th:href="@{/logout}"/></li>
                </ul>
              </li>
              <li th:ref="navbar-item" sec:authorize="!isAuthenticated()">
                <a th:text="'Login'" th:href="@{/login}"/>
              </li>
            </ul>
```

In the case of an unauthenticated client, only the "Login" button is
rendered (as shown in the image above).  The resulting HTML (with the
decoupled template logic applied) is shown below.

```xml
            <ul class="navbar-nav text-white bg-dark">



              <li class="navbar-item dropdown">
                <a href="/login" class="btn navbar-link text-white bg-dark">Login</a>
              </li>
            </ul>
```

The controller methods to present the Login form, present the Change
Password form, and handle the change password POST method are shown below.
The default Spring Security login POST method is used and does not have to
be implemented here.  The
[`@PreAuthorize`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/access/prepost/PreAuthorize.html)
annotations on the change password methods enforce that the client must be
authenticated to use those functions.  No logout method needs to be
implemented because a logout handler was configured in the
[`WebSecurityConfigurer`](#logoutRequestMatcher).

<figcaption style="text-align: center">
    ControllerImpl - Login and Change Password Methods
</figcaption>
```java
public class ControllerImpl implements ErrorController {
    ...
    @Autowired private CredentialRepository credentialRepository = null;
    @Autowired private PasswordEncoder encoder = null;
    ...
    @RequestMapping(method = { GET }, value = { "login" })
    public String login(Model model, HttpSession session) {
        model.addAttribute("form", new LoginForm());

        return VIEW;
    }

    @RequestMapping(method = { GET }, value = { "password" })
    @PreAuthorize("isAuthenticated()")
    public String password(Model model, Principal principal) {
        Credential credential =
            credentialRepository.findById(principal.getName())
            .orElseThrow(() -> new AuthorizationServiceException("Unauthorized"));

        model.addAttribute("form", new ChangePasswordForm());

        return VIEW;
    }

    @RequestMapping(method = { POST }, value = { "password" })
    @PreAuthorize("isAuthenticated()")
    public String passwordPOST(Model model, Principal principal, @Valid ChangePasswordForm form, BindingResult result) {
        Credential credential =
            credentialRepository.findById(principal.getName())
            .orElseThrow(() -> new AuthorizationServiceException("Unauthorized"));

        try {
            if (result.hasErrors()) {
                throw new RuntimeException(String.valueOf(result.getAllErrors()));
            }

            if (! (Objects.equals(form.getUsername(), principal.getName())
                   && encoder.matches(form.getPassword(), credential.getPassword()))) {
                throw new AccessDeniedException("Invalid user name and password");
            }

            if (! (form.getNewPassword() != null
                   && Objects.equals(form.getNewPassword(), form.getRepeatPassword()))) {
                throw new RuntimeException("Repeated password does not match new password");
            }

            if (encoder.matches(form.getNewPassword(), credential.getPassword())) {
                throw new RuntimeException("New password must be different than old");
            }

            credential.setPassword(encoder.encode(form.getNewPassword()));
            credentialRepository.save(credential);
        } catch (Exception exception) {
            model.addAttribute("form", form);
            model.addAttribute("errors", exception.getMessage());
        }

        return VIEW;
    }
    ...
}
```

Once authenticated, the user management drop-down is rendered and the Login
button is not.  In addition, because `user@example.com` has been granted
"USER" authority, the User drop-down is rendered to HTML, also.

![](/assets/{{ page.permalink }}/application-02-authenticated.png)

The change password form is straightforward.

![](/assets/{{ page.permalink }}/application-03-change-password.png)

And, as an aside, changed passwords are stored encrypted (as expected).

```command-line
mysql> SELECT * FROM credentials;
+-------------------+----------------------------------------------------------------------+
| email             | password                                                             |
+-------------------+----------------------------------------------------------------------+
| admin@example.com | {noop}abcdef                                                         |
| user@example.com  | {bcrypt}$2a$10$UXRB6BbmcbHfXkWDTk755ewgWsENMgFZoJ.JcIoiIjuRyGhOpEaNS |
+-------------------+----------------------------------------------------------------------+
2 rows in set (0.00 sec)
```

The user dropdown expanded below:

![](/assets/{{ page.permalink }}/application-04-user-dropdown.png)

The `/who-am-i` method adds the client's
[`Principal`](https://docs.oracle.com/javase/8/docs/api/java/security/Principal.html)
(injected as a parameter by Spring) to the `Model` so it may be presented in
the Thymeleaf template (as long as the client has the "USER" authority).

<figcaption style="text-align: center">ControllerImpl - /who-am-i</figcaption>
```java
public class ControllerImpl implements ErrorController {
    ...
    @RequestMapping(value = { "who-am-i" })
    @PreAuthorize("hasAuthority('USER')")
    public String whoAmI(Model model, Principal principal) {
        model.addAttribute("principal", principal);

        return VIEW;
    }
    ...
}
```

When selecting "User->Who Am I?" the application shows something similar to:

![](/assets/{{ page.permalink }}/application-05-who-am-i.png)

The application Thymeleaf template contains to display the method parameter
`Principal`:[^2]

[^2]: It's important to note that the equivalent value for `Principal` is
available in the security dialect as `${#authentication}` which references
an implementation of
[`Authentication`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/index.html?org/springframework/security/core/Authentication.html).
The use of `${#authentication}` will be explored further in the OAuth
discussion in the next chapter.

```xml
      <section th:case="'/who-am-i'">
        <p th:text="${principal}"/>
      </section>
```

Clients that have been granted "ADMINISTRATOR" authority will be presented
the Administrator drop-down menu.

![](/assets/{{ page.permalink }}/application-06-administrator-dropdown.png)

The `/who` method will list the `Principal`s of currently registered
sessions and is only available to clients granted "ADMINISTRATOR" authority.

<figcaption style="text-align: center">ControllerImpl - /who</figcaption>
```java
public class ControllerImpl implements ErrorController {
    ...
    @Autowired private SessionRegistry registry = null;
    ...
    @RequestMapping(value = { "who" })
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public String who(Model model) {
        model.addAttribute("principals", registry.getAllPrincipals());

        return VIEW;
    }
    ...
}
```

The method implementation is straightforward with the injected
[`SessionRegistry`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/core/session/SessionRegistry.html).
The `SessionRegistry`
[implementation](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/core/session/SessionRegistryImpl.html)
bean must be configured as part of the `WebSecurityConfigurer`.

<figcaption style="text-align: center">
  WebSecurityConfigurerImpl.UI - SessionRegistry
</figcaption>
```java
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...
    public static class UI extends WebSecurityConfigurerImpl {
        ...
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            ...
            http.sessionManagement(t -> t.maximumSessions(-1).sessionRegistry(sessionRegistry()));
        }
        ...
        @Bean
        public SessionRegistry sessionRegistry() {
            return new SessionRegistryImpl();
        }
        ...
    }
    ...
}
```

A client with ADMINISTRATION authority may navigate to `/who`:

![](/assets/{{ page.permalink }}/application-07-who.png)

While a client without (even if authenticated) will be denied:

![](/assets/{{ page.permalink }}/application-08-who-denied.png)

The next section discusses the REST controller.

### REST Controller

Similar to the corresponding `@Controller` method described in the previous
section, the `/api/who-am-i` method returns the client's `Principal`
(injected as a parameter by Spring) if the client has the "USER" authority.

<figcaption style="text-align: center">
  RestControllerImpl - /who-am-i
</figcaption>
```java
public class RestControllerImpl {
    ...
    @RequestMapping(method = { GET }, value = { "who-am-i" })
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<Principal> whoAmI(Principal principal) throws Exception {
        return new ResponseEntity<>(principal, HttpStatus.OK);
    }
    ...
}
```

Invoking without authentication returns `HTTP/1.1 403`.[^3]

[^3]: Recall the discussion of setting
[`HttpSecurity`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/config/annotation/web/builders/HttpSecurity.html)
[basic authentication entry point](#authenticationEntryPoint).

```command-line
$ curl -is http://localhost:8080/api/who-am-i
HTTP/1.1 403
Set-Cookie: JSESSIONID=6EBD3FEED11F2499F6915E98E02D1C26; Path=/; HttpOnly
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Sat, 17 Oct 2020 05:25:04 GMT
```

While supplying credentials for a user that is granted "USER" is successful.

```command-line
$ curl -is --basic -u user@example.com:123456 http://localhost:8080/api/who-am-i
HTTP/1.1 200
Set-Cookie: JSESSIONID=C0F5D3A67AA59521A223B5F87B2915FC; Path=/; HttpOnly
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 17 Oct 2020 05:25:44 GMT

{
  "authorities" : [ {
    "authority" : "USER"
  } ],
  "details" : {
    "remoteAddress" : "0:0:0:0:0:0:0:1",
    "sessionId" : null
  },
  "authenticated" : true,
  "principal" : {
    "password" : null,
    "username" : "user@example.com",
    "authorities" : [ {
      "authority" : "USER"
    } ],
    "accountNonExpired" : true,
    "accountNonLocked" : true,
    "credentialsNonExpired" : true,
    "enabled" : true
  },
  "credentials" : null,
  "name" : "user@example.com"
}
```

The `/api/who` method returns the list of all `Principal`s logged in
(defined as having active sessions in the UI) if the client has
"ADMINISTRATOR" authority.

<figcaption style="text-align: center">RestControllerImpl - /who</figcaption>
```java
public class RestControllerImpl {
    @Autowired private SessionRegistry registry = null;
    ...
    @RequestMapping(method = { GET }, value = { "who" })
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<List<Object>> who() throws Exception {
        return new ResponseEntity<>(registry.getAllPrincipals(), HttpStatus.OK);
    }
    ...
}
```

Invoking with an authenticated client *without* "ADMINISTRATOR" authority
granted returns `HTTP/1.1 403` (as expected).[^4]

[^4]: Recall the discussion `RestControllerImpl`'s
[`@ExceptionHandler` method](#ExceptionHandler).

```command-line
$ curl -is --basic -u user@example.com:123456 http://localhost:8080/api/who
HTTP/1.1 403
...
```

While supplying credentials for a user that is granted "ADMINISTRATOR" is
successful.

```command-line
$ curl -is --basic -u admin@example.com:abcdef http://localhost:8080/api/who
HTTP/1.1 200
Set-Cookie: JSESSIONID=8E283763FD321382417C89B609DE9EDC; Path=/; HttpOnly
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 17 Oct 2020 05:27:39 GMT

[ {
  "password" : null,
  "username" : "user@example.com",
  "authorities" : [ {
    "authority" : "USER"
  } ],
  "accountNonExpired" : true,
  "accountNonLocked" : true,
  "credentialsNonExpired" : true,
  "enabled" : true
}, {
  "password" : null,
  "username" : "admin@example.com",
  "authorities" : [ {
    "authority" : "ADMINISTRATOR"
  }, {
    "authority" : "USER"
  } ],
  "accountNonExpired" : true,
  "accountNonLocked" : true,
  "credentialsNonExpired" : true,
  "enabled" : true
} ]
```

The next chapter will examine OAuth integration.

## OAuth

The following subsections will:

1. Run an experiment by configuring the application as described in the
first chapter for OAuth authentication

2. Change the application implementation to allow Form Login and OAuth
authenitcation

### Experiment

This section will examine the behavior Spring Security's default settings
for authentication.  An OAuth provider must be configured.  This can easily
be done on [GitHub](https://github.com/):

1. Navigate to [GitHub](https://github.com/) and login

2. Select ["Settings" from the right-most profile drop-down menu](https://github.com/settings/profile)

3. Click ["Developer Settings" from the left column](https://github.com/settings/apps)

4. Click ["OAuth Apps" from the left column](https://github.com/settings/developers)

    ![](github-01.png)

5. Click "Register a new application."  Fill in the form with the following values:
    * Homepage URL: <http://localhost:8080/>
    * Authorization callback URL: <http://localhost:8080/login/oauth2/code/github>

    ![](github-02.png)

6. Note the Client ID and Secret as it will be required in the application [configuration](#application-oauth.yml)

    ![](github-03.png)

The POM `oauth` profile enables the Spring Boot `oauth` profile.  In
addition, the required Spring Security dependencies for OAuth are added: An
OAuth 2.0 client and support for Javascript Object Signing and Encryption
(JOSE).

```xml
  <profiles>
    ...
    <profile>
      <id>oauth</id>
      <build>
        ...
      </build>
    </profile>
    ...
  </profiles>
  <dependencies verbose="true">
    ...
    <dependency>
      <groupId>com.okta.spring</groupId>
      <artifactId>okta-spring-boot-starter</artifactId>
      <version>1.4.0</version>
    </dependency>
    ...
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-oauth2-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-oauth2-jose</artifactId>
    </dependency>
    ...
  </dependencies>
```

Allowing the application to be run from Maven with either
`mvn -Pspring-boot:run,hsqldb,oauth` or
`mvn -Pspring-boot:run,mysql,oauth`.

The `WebSecurityConfigurer` is changed to use OAuth Login instead of Form
Login (with default configuration):

<figcaption style="text-align: center">
  WebSecurityConfigurerImpl.UI (Default OAuth2 Customizer)
</figcaption>
```java
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...
    public static class UI extends WebSecurityConfigurerImpl {
        ...
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/**")
                .authorizeRequests(t -> t.anyRequest().authenticated())
                /* .formLogin(t -> t.loginPage("/login").permitAll()) */
                .oauth2Login(Customizer.withDefaults())
                .logout(t -> t.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                              .logoutSuccessUrl("/").permitAll());
            ...
        }
        ...
    }
    ...
}
```

Finally, the OAuth client prperties must be configured in the
profile-specific application properties YAML file:[^5]

[^5]: YAML is not required but YAML lends itself to expressing the
configuration compactly.

<a name="application-oauth.yml">
<figcaption style="text-align: center">application-oauth.yml</figcaption>
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: dad3306da38eb7be68a1
            client-secret: 8a5394b2e29037b9bdf17e51af472020f85bfca6
```
</a>

Running the application now offers OAuth
[login](http://localhost:8080/password):

![](/assets/{{ page.permalink }}/application-09-oauth-login.png)

Clicking [GitHub]() will redirect for authorization:

![](/assets/{{ page.permalink }}/application-10-redirect.png)

If granted, the application will successfully login.  However, the
`Principal` name will be unrecognizable as well as the granted authorities:

![](/assets/{{ page.permalink }}/application-11-oauth-authenticated.png)

If invoked, the Change Password function fails with:

![](/assets/{{ page.permalink }}/application-12-change-password.png)

Because the authenticated `Principal` has no corresponding `Credential`
database record.

A naive implementation to integrate Form Login and OAuth2 Login is
configured by simply enabling both:

```java
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/**")
                .authorizeRequests(t -> t.anyRequest().authenticated())
                .formLogin(t -> t.loginPage("/login").permitAll())
                .oauth2Login(Customizer.withDefaults())
                .logout(t -> t.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                              .logoutSuccessUrl("/").permitAll());
            ...
        }
```

However, only the OAuth2 Login page is available (illustrated previously).
A successful integration will need to create a custom OAuth2 Login page
compatible with (and the same as) the Form Login Page.

The next subsection will adjust the implementation to:

1. Use user e'mail as `Principal` name

2. Integrate Form Login and OAuth2 Login into a single custom login page

3. Manage granted authorities for OAuth2-authenticated users

4. Not offer the Change Password function to users logged in through OAuth2

### Implementation

This section will adjust the implementation as outlined at the end of the
previous section.  The first step is to provide the OAuth 2.0 security client
registrations and configured providers.  The processes to configure Google
and Okta Client IDs is very similar to the one for GitHub and must be
configured on their respective sites.  The authorization callback URI must
be <http://localhost:8080/login/oauth2/code/google> and
<http://localhost:8080/login/oauth2/code/okta>, respectively.  A redacted
example is shown below.

```yaml
---
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: XXXXXXXXXXXXXXXXXXXX
            client-secret: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
            scope: user:email
          google:
            client-id: XXXXXXXXXXXXXXXXXXXX
            client-secret: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
          okta:
            client-id: XXXXXXXXXXXXXXXXXXXX
            client-secret: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
            client-name: Okta
        provider:
          github:
            user-name-attribute: email
          google:
            user-name-attribute: email
          okta:
            issuer-uri: https://DOMAIN.okta.com/oauth2/default
            user-name-attribute: email
```

Note that the providers are configured to use the user's e'mail address as
the `Principal` name (`user-name-attribute: email`).

The next step is to integrate the OAuth Login page with the custom Form
Login page.  Simply calling
[`HttpSecurity.oauth2Login(Customizer.withDefaults())`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/config/annotation/web/builders/HttpSecurity.html#oauth2Login-org.springframework.security.config.Customizer-)
will attempt to configure a
[`ClientRegistrationRepository`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/oauth2/client/registration/ClientRegistrationRepository.html)
bean but that will fail if no `spring.security.oauth2.client.registration.*`
properties are configured.  The implementation tests if the bean is
configured before attempting the `HttpSecurity` method call.

<figcaption style="text-align: center">
  WebSecurityConfigurerImpl.UI (Custom Login Page)
</figcaption>
```java
public abstract class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...
    public static class UI extends WebSecurityConfigurerImpl {
        ...
        @Autowired private OidcUserService oidcUserService = null;
        ...
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            ...
            try {
                ClientRegistrationRepository repository =
                    getApplicationContext().getBean(ClientRegistrationRepository.class);

                if (repository != null) {
                    http.oauth2Login(t -> t.clientRegistrationRepository(repository)
                                           .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                                           .loginPage("/login").permitAll());
                }
            } catch (Exception exception) {
            }
            ...
        }
        ...
    }
    ...
}
```

Both the
[`OAuth2UserService`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/oauth2/client/userinfo/OAuth2UserService.html)
and
[`OidcUserService`](https://docs.spring.io/spring-security/site/docs/5.4.6/api/org/springframework/security/oauth2/client/oidc/userinfo/OidcUserService.html)
beans are configured by configuring the Open ID Connect (OIDC)
`OidcUserService` -- OIDC is built on top of OAuth 2.0 to provide identity
services.  The `OidcUserService` delegates to the `OAuth2UserService` for
retrieving Oauth 2.0-specific information.

<figcaption style="text-align: center">
  UserServicesConfiguration - OAuth2UserService and OidcUserService Beans
</figcaption>
```java
@Configuration
@NoArgsConstructor @ToString @Log4j2
public class UserServicesConfiguration {
    ...
    @Bean
    public OAuth2UserService<OAuth2UserRequest,OAuth2User> oAuth2UserService() {
        return new OAuth2UserServiceImpl();
    }

    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserServiceImpl();
    }
    ...
    private static final List<GrantedAuthority> DEFAULT_AUTHORITIES =
        AuthorityUtils.createAuthorityList(Authorities.USER.name());

    @NoArgsConstructor @ToString
    private class OAuth2UserServiceImpl extends DefaultOAuth2UserService {
        private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

        @Override
        public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
            String attribute =
                request.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
            OAuth2User user = delegate.loadUser(request);

            try {
                Optional<Authority> authority = authorityRepository.findById(user.getName());

                user =
                    new DefaultOAuth2User(authority.map(t -> t.getGrants().asGrantedAuthorityList())
                                          .orElse(DEFAULT_AUTHORITIES),
                                          user.getAttributes(), attribute);
            } catch (OAuth2AuthenticationException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("{}", request, exception);
            }

            return user;
        }
    }

    @NoArgsConstructor @ToString
    private class OidcUserServiceImpl extends OidcUserService {
        { setOauth2UserService(oAuth2UserService()); }

        @Override
        public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
            String attribute =
                request.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
            OidcUser user = super.loadUser(request);

            try {
                Optional<Authority> authority = authorityRepository.findById(user.getName());

                user =
                    new DefaultOidcUser(authority.map(t -> t.getGrants().asGrantedAuthorityList())
                                        .orElse(DEFAULT_AUTHORITIES),
                                        user.getIdToken(), user.getUserInfo(), attribute);
            } catch (OAuth2AuthenticationException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("{}", request, exception);
            }

            return user;
        }
    }
}
```

Both the `OAuth2UserService` and `OidcUserService` map granted authorities
for *this* application.

A `@ControllerAdvice` is implemented to add two attributes to the `Model`:

1. `oauth2`, a `List` of configured `ClientRegistration`s

2. `isPasswordAuthenticated`, indicating if the `Principal` was
authenticated with a password

<figcaption style="text-align: center">ControllerAdviceImpl</figcaption>
```java
@ControllerAdvice
@NoArgsConstructor @ToString @Log4j2
public class ControllerAdviceImpl {
    @Autowired private ApplicationContext context = null;
    private List<ClientRegistration> oauth2 = null;
    ...
    @ModelAttribute("oauth2")
    public List<ClientRegistration> oauth2() {
        if (oauth2 == null) {
            oauth2 = new ArrayList<>();

            try {
                ClientRegistrationRepository repository =
                    context.getBean(ClientRegistrationRepository.class);

                if (repository != null) {
                    ResolvableType type =
                        ResolvableType.forInstance(repository)
                        .as(Iterable.class);

                    if (type != ResolvableType.NONE
                        && ClientRegistration.class.isAssignableFrom(type.resolveGenerics()[0])) {
                        ((Iterable<?>) repository)
                            .forEach(t -> oauth2.add((ClientRegistration) t));
                    }
                }
            } catch (Exception exception) {
            }
        }

        return oauth2;
    }

    @ModelAttribute("isPasswordAuthenticated")
    public boolean isPasswordAuthenticated(Principal principal) {
        return principal instanceof UsernamePasswordAuthenticationToken;
    }
}
```

The `LoginForm` is modified to include configured OAuth 2.0 authentication
options (if configured):

<figcaption style="text-align: center">LoginForm.html</figcaption>
```html
<div>
  <div>
    <div>
      <form th:object="${form}">
        <input type="email" th:name="username" th:placeholder="'E\'mail Address'"/>
        <label th:text="'E\'mail Address'"/>
        <input type="password" th:name="password" th:placeholder="'Password'"/>
        <label th:text="'Password'"/>
        <button type="submit" th:text="'Login'"/>
        <th:block th:if="${! oauth2.isEmpty()}">
          <hr/>
          <a th:each="client : ${oauth2}" th:href="@{/oauth2/authorization/{id}(id=${client.registrationId})}" th:text="${client.clientName}"/>
        </th:block>
      </form>
    </div>
  </div>
  <div>
    <div>
      <p th:if="${param.error}">Invalid username and password.</p>
      <p th:if="${param.logout}">You have been logged out.</p>
    </div>
  </div>
</div>
```

And the Change Password menu option is only offered if the client was
authenticated with a password (by testing the `isPasswordAuthenticated`
`Model` attribute:

<figcaption style="text-align: center">
  application.html - Change Password
</figcaption>
```html
              ...
              <li th:ref="navbar-item" sec:authorize="isAuthenticated()">
                <button sec:authentication="name"/>
                <ul th:ref="navbar-dropdown">
                  <li th:if="${isPasswordAuthenticated}">
                    <a th:text="'Change Password'" th:href="@{/password}"/>
                  </li>
                  <li><a th:text="'Logout'" th:href="@{/logout}"/></li>
                </ul>
              </li>
              ...
```

The end result for the login page is shown below:

![](/assets/{{ page.permalink }}/application-13-integrated-login.png)

With a successful (Google) login:

![](/assets/{{ page.permalink }}/application-14-authenticated.png)
