---
title: Google Cloud Platform App Engine Deployment Experience
canonical_url: https://blog.hcf.dev/article/2018-12-18-gae-deployment-experience/
tags:
  - Java
  - Spring
  - GCP
  - GAE
permalink: article/2018-12-18-gae-deployment-experience
---

This article describes some of the challenges and pecularities of deploying
an annotated
[`@SpringBootApplication`](https://docs.spring.io/spring-boot/docs/2.4.5/reference/html/using-spring-boot.html#using-boot-using-springbootapplication-annotation)
application to
[Google Cloud Platform](https://cloud.google.com/)'s
[Google App Engine](https://cloud.google.com/appengine/).

## Set-Up

This article assumes that the
[Google Cloud SDK](https://cloud.google.com/sdk/) has been
installed and has been configured.  Further, this article assumes that a
project has been set-up within the
[Google Cloud Platform Console](https://console.cloud.google.com/)
(a fictitious `www-example-com` within this article).

The `default` Google App Engine service must be created.  It may be created
from the command line with `gcloud app create`.

## WAR Maven Project

The Maven Project to create the WAR for deployment consists of the following
artifacts:

```bash
www-example-com-service-default
├── pom.xml
└── src
    └── main
        ├── resources
        │   └── application-gcp.properties
        └── webapp
            └── WEB-INF
                ├── logging.properties
                └── web.xml
```

For simplicity, this example assumes a Spring Boot application is available
as a single Maven dependency.  The project POM to deploy the application is
identified with `groupId` corresponding to the Google Cloud `PROJECT_ID`,
`artifactId` corresponding to the App Engine service name, and `version`
corresponds to the App Engine service version.

```xml
<project ...>
  <groupId>www-example-com</groupId>
  <artifactId>default</artifactId>
  <version>2018121801</version>
  <packaging>war</packaging>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>2.1.1.RELEASE</version>
    <relativePath/>
  </parent>
  ...
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>spring-boot-application</artifactId>
      <version>1.0.0</version>
    </dependency>
    ...
  </dependencies>
  ...
</project>
```

Runtime dependencies should be added, also.  For example, the necessary
dependencies to connect to a Google `mysql` SQL instance would include:

```xml
  <dependencies>
    ...
    <dependency>
      <groupId>com.google.cloud.sql</groupId>
      <artifactId>mysql-socket-factory</artifactId>
      <version>1.0.11</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-java</artifactId>
      <version>8.0.13</version>
      <scope>runtime</scope>
    </dependency>
    ...
  </dependencies>
```

The necessary plugins to build the WAR and repackage the WAR as a Spring
Boot application are:

```xml
  ...
  <properties>
    ...
    <start-class>com.example.Launcher</start-class>
    ...
  </properties>
  ...
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-war-plugin</artifactId>
          <configuration>
            <delimiters>
              <delimiter>@</delimiter>
            </delimiters>
            <useDefaultDelimiters>false</useDefaultDelimiters>
            <webResources>
              <resource>
                <filtering>true</filtering>
                <directory>src/main/webapp</directory>
              </resource>
            </webResources>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <executions>
            <execution>
              <goals>
                <goal>build-info</goal>
                <goal>repackage</goal>
              </goals>
            </execution>
          </executions>
          <configuration>
            <mainClass>${start-class}</mainClass>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
    ...
  </build>
  ...
```

The additional artifacts used in constructing the WAR include
`src/main/resources/application-gcp.properties`,

<pre data-src="www-example-com-service-default/src/main/resources/application-gcp.properties"></pre>

`src/main/webapp/WEB-INF/web.xml`,

<pre data-src="www-example-com-service-default/src/main/webapp/WEB-INF/web.xml"></pre>

and `src/main/webapp/WEB-INF/logging.properties`:

<pre data-src="www-example-com-service-default/src/main/webapp/WEB-INF/logging.properties"></pre>

The resulting WAR is expected to be executed with the Spring Profile "`gcp`"
enabled.  The secrets in the template may be populated by setting the
corresponding Maven properties in the build.[^1]  While the `web.xml` is not
needed by the annotated Spring Boot Application, it is configured to
redirect `http` traffic to `https` and provide session management.

[^1]: Exercise left to the reader.

The WAR may be created by executing `mvn clean package`.

## Adjustments to Deploy to Google App Engine

The "standard" App Engine environment uses `Jetty` instead of `Tomcat` so
the application dependencies need to be adjusted by adding exclusions:

```xml
  <dependencies verbose="true">
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>spring-boot-application</artifactId>
      <version>1.0.0</version>
      <exclusions>
        <exclusion>
          <artifactId>commons-logging</artifactId>
          <groupId>commons-logging</groupId>
        </exclusion>
        <exclusion>
          <groupId>javax.transaction</groupId>
          <artifactId>javax.transaction-api</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    ...
  </dependencies>
```

Google offers at least two [Maven](http://maven.apache.org/)
plugins.  As of this writing, the two available plugins are:

```xml
  <build>
    <pluginManagement>
      ...
      <plugins>
        <plugin>
          <groupId>com.google.appengine</groupId>
          <artifactId>appengine-maven-plugin</artifactId>
          <version>1.9.70</version>
        </plugin>
        <plugin>
          <groupId>com.google.cloud.tools</groupId>
          <artifactId>appengine-maven-plugin</artifactId>
          <version>1.3.2</version>
        </plugin>
      </plugins>
      ...
    </pluginManagement>
    ...
  </build>
```

Google describes the first as "App Engine SDK-based" and provides a document
on this plugin
[here](https://cloud.google.com/appengine/docs/standard/java/tools/maven)
and the second as "Cloud SDK-based" with a document on this plugin
[here](https://cloud.google.com/appengine/docs/standard/java/tools/using-maven).
The Cloud SDK-based plugin also requires the `app-engine-java` component be
installed with `gcloud components install app-engine-java`.

Both plugins (as with any deployment to App Engine) require a
[`src/main/webapp/WEB-INF/appengine-web.xml`](https://cloud.google.com/appengine/docs/standard/java/config/appref)
which is either specified and/or generated.  This application includes the
following:

<pre data-src="www-example-com-service-default/src/main/webapp/WEB-INF/appengine-web.xml"></pre>

which has the following features (in addition to specifying scaling):

* Sets the `SPRING_PROFILES_ACTIVE` environment variable to `gcp` to enable
  the Spring profile
* Configures `java.util.logging`

For this demonstration, the `com.google.appengine:appengine-maven-plugin` is
configured in a Maven profile:

```xml
  <profiles>
    ...
    <profile>
      <id>com.google.appengine:appengine-maven-plugin</id>
      <activation>
        <file><missing>${basedir}/src/main/appengine/app.yaml</missing></file>
      </activation>
      <build>
        <plugins>
          <plugin>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-maven-plugin</artifactId>
          </plugin>
        </plugins>
      </build>
    </profile>
    ...
  </profiles>
```

The application may be built and deployed with
`mvn clean package appengine:deploy`.  Example output:

```bash
mvn -B clean package appengine:deploy
[INFO] Scanning for projects...
[INFO]
[INFO] ---------------------< www-example-com:default >----------------------
[INFO] Building www-example-com default 1
[INFO] --------------------------------[ war ]---------------------------------
...
[INFO] --- appengine-maven-plugin:1.9.70:deploy (default-cli) @ default ---
[INFO]
[INFO] Google App Engine Java SDK - Updating Application
[INFO]
[INFO] Retrieving Google App Engine Java SDK from Maven
[INFO] Downloading from central: https://repo1.maven.org/maven2/com/google/appengine/appengine-java-sdk/1.9.70/appengine-java-sdk-1.9.70.zip
[INFO] Downloaded from central: https://repo1.maven.org/maven2/com/google/appengine/appengine-java-sdk/1.9.70/appengine-java-sdk-1.9.70.zip (183 MB at 15 MB/s)
[INFO] Updating Google App Engine Application
[INFO] Running -A www-example-com -V 1 --oauth2 update /Users/ball/www-example-com-service-default/target/default-1
Reading application configuration data...


Beginning interaction for module default...
0% Created staging directory at: '/var/folders/c5/pzywv1k91gqgvkklp5r2twx00000gn/T/appcfg6118757389943856883.tmp'
5% Scanning for jsp files.
20% Scanning files on local disk.
25% Initiating update.
28% Cloning 162 application files.
40% Uploading 6 files.
52% Uploaded 1 files.
61% Uploaded 2 files.
68% Uploaded 3 files.
73% Uploaded 4 files.
77% Uploaded 5 files.
80% Uploaded 6 files.
82% Sending batch containing 6 file(s) totaling 231KB.
84% Initializing precompilation...
90% Deploying new version.
95% Will check again in 1 seconds.
98% Will check again in 2 seconds.
99% Will check again in 4 seconds.
99% Will check again in 8 seconds.
99% Closing update: new version is ready to start serving.
99% Uploading index definitions.

Update for module default completed successfully.
Success.
Cleaning up temporary files for module default...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  48.730 s
[INFO] Finished at: 2018-12-18T19:34:33-08:00
[INFO] ------------------------------------------------------------------------
```

This deploys the service but does not switch the load balancer to the new
service.

The `com.google.cloud.tools:appengine-maven-plugin` may be configured with
an `src/main/appengine/app.yaml`:

```xml
  <profiles>
    ...
    <profile>
      <id>com.google.cloud.tools:appengine-maven-plugin</id>
      <activation>
        <file><exists>${basedir}/src/main/appengine/app.yaml</exists></file>
      </activation>
      <build>
        <plugins>
          <plugin>
            <groupId>com.google.cloud.tools</groupId>
            <artifactId>appengine-maven-plugin</artifactId>
            <configuration>
              <project>${project.groupId}</project>
              <service>${project.artifactId}</service>
              <version>${project.version}</version>
            </configuration>
          </plugin>
        </plugins>
      </build>
    </profile>
    ...
  </profiles>
```

Various articles suggest that a minimal `app.yaml` can replace the
`appengine-web.xml`:

```yaml
runtime: java8
env: standard
```

However, it fails with a cryptic error message:

```bash
mvn -B clean package appengine:deploy
[INFO] Scanning for projects...
[INFO]
[INFO] ---------------------< www-example-com:default >----------------------
[INFO] Building www-example-com default 2
[INFO] --------------------------------[ war ]---------------------------------
...
[INFO] --- appengine-maven-plugin:1.3.2:deploy (default-cli) @ default ---
[INFO] Staging the application to: /Users/ball/www-example-com-service-default/target/appengine-staging
[INFO] Detected App Engine flexible environment application.
Dec 18, 2018 8:16:31 PM com.google.cloud.tools.appengine.cloudsdk.CloudSdk logCommand
INFO: submitting command: /usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/gcloud app deploy --version 2 --project www-example-com
[INFO] GCLOUD: Services to deploy:
[INFO] GCLOUD:
[INFO] GCLOUD: descriptor:      [/Users/ball/www-example-com-service-default/target/appengine-staging/app.yaml]
[INFO] GCLOUD: source:          [/Users/ball/www-example-com-service-default/target/appengine-staging]
[INFO] GCLOUD: target project:  [www-example-com]
[INFO] GCLOUD: target service:  [default]
[INFO] GCLOUD: target version:  [2]
[INFO] GCLOUD: target url:      [http://www-example-com.appspot.com]
[INFO] GCLOUD:
[INFO] GCLOUD:
[INFO] GCLOUD: Beginning deployment of service [default]...
[INFO] GCLOUD: ERROR: (gcloud.app.deploy) Cannot upload file [/Users/ball/www-example-com-service-default/target/appengine-staging/default-2.war], which has size [74408302] (greater than maximum allowed size of [33554432]). Please delete the file or add to the skip_files entry in your application .yaml file and try again.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.260 s
[INFO] Finished at: 2018-12-18T20:16:34-08:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal com.google.cloud.tools:appengine-maven-plugin:1.3.2:deploy (default-cli) on project default: Execution default-cli of goal com.google.cloud.tools:appengine-maven-plugin:1.3.2:deploy failed: Non zero exit: 1 -> [Help 1]
[ERROR]
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR]
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/PluginExecutionException
```

This author recommends using `com.google.cloud.tools:appengine-maven-plugin`
with a fully defined `appengine-web.xml` and a trivial `app.yaml`:

<pre data-src="www-example-com-service-default/src/main/appengine/app.yaml"></pre>

With the corresponding output:

```bash
mvn -B clean package appengine:deploy
[INFO] Scanning for projects...
[INFO]
[INFO] ----------------------< www-example-com:default >-----------------------
[INFO] Building www-example-com default 3
[INFO] --------------------------------[ war ]---------------------------------
...
[INFO] --- appengine-maven-plugin:1.3.2:deploy (default-cli) @ default ---
[INFO] Staging the application to: /Users/ball/www-example-com-service-default/target/appengine-staging
[INFO] Detected App Engine standard environment application.
Dec 18, 2018 8:30:22 PM com.google.cloud.tools.appengine.cloudsdk.CloudSdk logCommand
INFO: submitting command: /Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/bin/java -cp /usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/platform/google_appengine/google/appengine/tools/java/lib/appengine-tools-api.jar com.google.appengine.tools.admin.AppCfg --disable_update_check stage /Users/ball/www-example-com-service-default/target/default-3 /Users/ball/www-example-com-service-default/target/appengine-staging
[INFO] GCLOUD: Reading application configuration data...
[INFO] GCLOUD:
[INFO] GCLOUD:
[INFO] GCLOUD: Beginning interaction for module default...
[INFO] GCLOUD: 0% Scanning for jsp files.
[INFO] GCLOUD: 2018-12-18 20:30:24.932:INFO::main: Logging initialized @234ms to org.eclipse.jetty.util.log.StdErrLog
[INFO] GCLOUD: 2018-12-18 20:30:25.048:INFO:oejs.Server:main: jetty-9.4.14.v20181114; built: 2018-11-14T21:20:31.478Z; git: c4550056e785fb5665914545889f21dc136ad9e6; jvm 1.8.0_192-b12
[INFO] GCLOUD: 2018-12-18 20:30:26.627:INFO:oeja.AnnotationConfiguration:main: Scanning elapsed time=989ms
[INFO] GCLOUD: 2018-12-18 20:30:26.643:INFO:oejq.QuickStartDescriptorGenerator:main: Quickstart generating
[INFO] GCLOUD: 2018-12-18 20:30:26.658:INFO:oejsh.ContextHandler:main: Started o.e.j.q.QuickStartWebApp@685cb137{/,file:///Users/ball/www-example-com-service-default/target/appengine-staging/,AVAILABLE}
[INFO] GCLOUD: 2018-12-18 20:30:26.661:INFO:oejs.Server:main: Started @1964ms
[INFO] GCLOUD: 2018-12-18 20:30:26.666:INFO:oejsh.ContextHandler:main: Stopped o.e.j.q.QuickStartWebApp@685cb137{/,file:///Users/ball/www-example-com-service-default/target/appengine-staging/,UNAVAILABLE}
[INFO] GCLOUD: Success.
[INFO] GCLOUD: Temporary staging for module default directory left in /Users/ball/www-example-com-service-default/target/appengine-staging
Dec 18, 2018 8:30:26 PM com.google.cloud.tools.appengine.cloudsdk.CloudSdk logCommand
INFO: submitting command: /usr/local/Caskroom/google-cloud-sdk/latest/google-cloud-sdk/bin/gcloud app deploy --version 3 --project www-example-com
[INFO] GCLOUD: Services to deploy:
[INFO] GCLOUD:
[INFO] GCLOUD: descriptor:      [/Users/ball/www-example-com-service-default/target/appengine-staging/app.yaml]
[INFO] GCLOUD: source:          [/Users/ball/www-example-com-service-default/target/appengine-staging]
[INFO] GCLOUD: target project:  [www-example-com]
[INFO] GCLOUD: target service:  [default]
[INFO] GCLOUD: target version:  [3]
[INFO] GCLOUD: target url:      [https://www-example-com.appspot.com]
[INFO] GCLOUD:
[INFO] GCLOUD:
[INFO] GCLOUD: Beginning deployment of service [default]...
[INFO] GCLOUD: #============================================================#
[INFO] GCLOUD: #= Uploading 3 files to Google Cloud Storage                =#
[INFO] GCLOUD: #============================================================#
[INFO] GCLOUD: File upload done.
[INFO] GCLOUD: Updating service [default]...
[INFO] GCLOUD: ..............done.
[INFO] GCLOUD: Setting traffic split for service [default]...
[INFO] GCLOUD: .......done.
[INFO] GCLOUD: Stopping version [www-example-com/default/2018121803].
[INFO] GCLOUD: Sent request to stop version [www-example-com/default/2018121803]. This operation may take some time to complete. If you would like to verify that it succeeded, run:
[INFO] GCLOUD:   $ gcloud app versions describe -s default 2018121803
[INFO] GCLOUD: until it shows that the version has stopped.
[INFO] GCLOUD: Deployed service [default] to [https://www-example-com.appspot.com]
[INFO] GCLOUD:
[INFO] GCLOUD: You can stream logs from the command line by running:
[INFO] GCLOUD:   $ gcloud app logs tail -s default
[INFO] GCLOUD:
[INFO] GCLOUD: To view your application in the web browser run:
[INFO] GCLOUD:   $ gcloud app browse
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  37.537 s
[INFO] Finished at: 2018-12-18T20:30:51-08:00
[INFO] ------------------------------------------------------------------------
```

## `pom.xml`

The complete `pom.xml` used in this example:

<pre data-src="www-example-com-service-default/pom.xml"></pre>

## References

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Google Cloud Platform](https://cloud.google.com/)
- [Google App Engine](https://cloud.google.com/appengine/)
- [Google Cloud Platform Console](https://console.cloud.google.com/home/dashboard)
