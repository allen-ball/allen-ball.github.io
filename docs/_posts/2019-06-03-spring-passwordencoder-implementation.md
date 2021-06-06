---
title: Spring PasswordEncoder Implementation
canonical_url: https://blog.hcf.dev/article/2019-06-03-spring-passwordencoder-implementation
tags:
 - Java
 - Spring Boot
 - Spring Security
permalink: article/2019-06-03-spring-passwordencoder-implementation
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

[Spring Security] provides multiple [`PasswordEncoder`][PasswordEncoder]
implementations with [`BCRYPT`][BCRYPT] as the recommended implementation.
However, the use-case of sharing an authentication database with an external
application, [Dovecot], is examined in this article.
<!--more-->
Dovecot uses an `MD5-CRYPT` algorithm.

Complete [javadoc] is provided.


## Reference

The actual encryption algorithm is captured in the Dovecot source file
[`password-scheme-md5crypt.c`](https://github.com/dovecot/core/blob/master/src/auth/password-scheme-md5crypt.c).


## Implementation

The implementation extends
[`DelegatingPasswordEncoder`][DelegatingPasswordEncoder] to provide
decryption services for the other Spring Security supported password types.
Two inner classes, each subclasses of [`PasswordEncoder`][PasswordEncoder],
provide `MD5-CRYPT` and `PLAIN` implementations.

``` java
@Service
public class MD5CryptPasswordEncoder extends DelegatingPasswordEncoder {
    ...

    private static final String MD5_CRYPT = "MD5-CRYPT";
    private static final HashMap<String,PasswordEncoder> MAP = new HashMap<>();

    static {
        MAP.put(MD5_CRYPT, MD5Crypt.INSTANCE);
        MAP.put("CLEAR", NoCrypt.INSTANCE);
        MAP.put("CLEARTEXT", NoCrypt.INSTANCE);
        MAP.put("PLAIN", NoCrypt.INSTANCE);
        MAP.put("PLAINTEXT", NoCrypt.INSTANCE);
    }

    ...

    public MD5CryptPasswordEncoder() {
        super(MD5_CRYPT, MAP);

        setDefaultPasswordEncoderForMatches(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    private static class NoCrypt implements PasswordEncoder {
        ...
        public static final NoCrypt INSTANCE = new NoCrypt();
        ...
    }

    private static class MD5Crypt extends NoCrypt {
        ...
        public static final MD5Crypt INSTANCE = new MD5Crypt();
        ...
    }
}
```

The
[`MD5Crypt`]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/spring/MD5CryptPasswordEncoder.html#line.114)
inner class implementation is straightforward:

``` java
    private static class MD5Crypt extends NoCrypt {
        private static final String MD5 = "md5";
        private static final String MAGIC = "$1$";
        private static final int SALT_LENGTH = 8;

        public static final MD5Crypt INSTANCE = new MD5Crypt();

        public MD5Crypt() { }

        @Override
        public String encode(CharSequence raw) {
            return encode(raw.toString(), salt(SALT_LENGTH));
        }

        private String encode(String raw, String salt) {
            if (salt.length() > SALT_LENGTH) {
                salt = salt.substring(0, SALT_LENGTH);
            }

            return (MAGIC + salt + "$" + encode(raw.getBytes(UTF_8), salt.getBytes(UTF_8)));
        }

        private String encode(byte[] password, byte[] salt) {
            /*
             * See source and password-scheme-md5crypt.c.
             */
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            String salt = null;

            if (encoded.startsWith(MAGIC)) {
                salt = encoded.substring(MAGIC.length()).split("[$]")[0];
            } else {
                throw new IllegalArgumentException("Invalid format");
            }

            return encoded.equals(encode(raw.toString(), salt));
        }
    }
```

The
[`NoCrypt`]({{ site.javadoc.url }}/{{ page.permalink }}/src-html/ball/spring/MD5CryptPasswordEncoder.html#line.70)
implementation provides the methods for calculating salt and `itoa64`
conversion.


## Spring Boot Application Integration

The [`PasswordEncoder`][PasswordEncoder] may be integrated with the
following [`@Configuration`][Configuration]:

``` java
package some.application;

import ball.spring.MD5CryptPasswordEncoder;
import ...

@Configuration
public class PasswordEncoderConfiguration {
    ...

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new MD5CryptPasswordEncoder();
    }
}
```

and must be integrated with a [`UserDetailsService`][UserDetailsService] in
a [`WebSecurityConfigurer`][WebSecurityConfigurer]:

``` java
package some.application;

import ...

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfigurerImpl extends WebSecurityConfigurerAdapter {
    ...

    @Autowired private UserDetailsService userDetailsService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService)
            .passwordEncoder(passwordEncoder);
    }

    ...
}
```

[Dovecot]: https://www.dovecot.org/

[Configuration]: {{ page.javadoc.spring }}/org/springframework/context/annotation/Configuration.html?is-external=true

[Spring Security]: https://spring.io/projects/spring-security
[BCRYPT]: {{ page.javadoc.spring-security }}/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html
[DelegatingPasswordEncoder]: {{ page.javadoc.spring-security }}/org/springframework/security/crypto/password/DelegatingPasswordEncoder.html?is-external=true
[PasswordEncoder]: {{ page.javadoc.spring-security }}/org/springframework/security/crypto/password/PasswordEncoder.html?is-external=true
[UserDetailsService]: {{ page.javadoc.spring-security }}/org/springframework/security/core/userdetails/UserDetailsService.html
[WebSecurityConfigurer]: {{ page.javadoc.spring-security }}/org/springframework/security/config/annotation/web/WebSecurityConfigurer.html

[javadoc]: {{ site.javadoc.url }}/{{ page.permalink }}/allclasses-noframe.html
