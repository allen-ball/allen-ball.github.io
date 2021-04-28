---
title: Maven toolchains.xml Script
canonical_url: https://blog.hcf.dev/article/2019-09-15-maven-toolchains-xml-script/
tags:
  - Maven
  - Java
  - bash
permalink: article/2019-09-15-maven-toolchains-xml-script
---

Apache Maven has introduced Maven Toolchains to ease configuring plug-ins
and to avoid specifying any JDK location in the project POMs.  Available
JDKs are configured in `${HOME}/.m2/toolchains.xml`.  This article
introduces a script to automate the generation of the `toolchains.xml`
file, `toolchains.xml.bash`.


## Theory of Operation

The `toolchains.xml` file contains a `<toolchain/>` element for each
configured JDK.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  ...
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>VERSION</version>
      <vendor>VENDOR</vendor>
    </provides>
    <configuration>
      <jdkHome>JDKHOMEDIR</jdkHome>
    </configuration>
  </toolchain>
  ...
</toolchains>
```

For each `JDKHOMEDIR` candidate, the `VERSION` is specified by the JVM
system property `java.specification.version` and a consistent vendor name is
specified by `java.vendor`.  This simple Java program (compatible with Java
1.5 and later) will provide the template.  (The JDK directory must be
supplied as a command line argument.)

``` java
public class ToolchainEntry {
    public static void main(String[] argv) {
        System.out
            .format("  <toolchain>\n")
            .format("    <type>jdk</type>\n")
            .format("    <provides>\n")
            .format("      <version>%s</version>\n",
                    System.getProperty("java.specification.version"))
            .format("      <vendor>%s</vendor>\n",
                    System.getProperty("java.vendor"))
            .format("    </provides>\n")
            .format("    <configuration>\n")
            .format("      <jdkHome>%s</jdkHome>\n", argv[0])
            .format("    </configuration>\n")
            .format("  </toolchain>\n");
    }
}
```

This program is combined with a `bash` script in the next section.


## Script

A `bash` script is used to drive the execution of the Java program described
in the previous section with responsibilities to:

1. Identify the candidate JVM directories:
    * Mac OS X (Darwin): `/Library/Java/JavaVirtualMachines/*.jdk/Contents/Home`
    * Linux: `/usr/lib/jvm/*jdk*`
2. Create `Toolchain.java` and compile to
   `Toolchain.class`<sup id="ref1">[1](#endnote1)</sup>
3. Create a new `<toolchains/>` document and iterate therough each candidate
   JDK directory executing `Toolchain.class` with the candidate JVM

``` bash
#!/bin/bash

shopt -s nullglob
JDKS+=({/Library/Java/JavaVirtualMachines/*.jdk/Contents/Home,/usr/lib/jvm/*jdk*})

cat > ToolchainEntry.java <<EOF
public class ToolchainEntry {
    public static void main(String[] argv) {
        System.out
            .format("  <toolchain>\n")
            .format("    <type>jdk</type>\n")
            .format("    <provides>\n")
            .format("      <version>%s</version>\n",
                    System.getProperty("java.specification.version"))
            .format("      <vendor>%s</vendor>\n",
                    System.getProperty("java.vendor"))
            .format("    </provides>\n")
            .format("    <configuration>\n")
            .format("      <jdkHome>%s</jdkHome>\n", argv[0])
            .format("    </configuration>\n")
            .format("  </toolchain>\n");
    }
}
EOF

javac -Xlint:none -source 1.7 -target 1.7 ToolchainEntry.java

echo '<?xml version="1.0" encoding="UTF-8"?>' > toolchains.xml
echo '<toolchains>' >> toolchains.xml

for jdk in ${JDKS[@]}; do
    if [ -d ${jdk} ]; then
        java=${jdk}/bin/java

        if [ -x ${java} ]; then
            ${java} ToolchainEntry ${jdk} >> toolchains.xml
        fi
    fi
done

echo '</toolchains>' >> toolchains.xml

rm -rf ToolchainEntry.*
```


## Output

Example generated `toolchains.xml` files for `Mac OS X` and `Linux` (Google
Cloud Shell) are provided below.

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>10</version>
      <vendor>AdoptOpenJDK</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/adoptopenjdk-10.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>11</version>
      <vendor>AdoptOpenJDK</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>12</version>
      <vendor>AdoptOpenJDK</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/adoptopenjdk-12.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>AdoptOpenJDK</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>9</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/adoptopenjdk-9.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>11</version>
      <vendor>Amazon.com Inc.</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/amazon-corretto-11.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>12</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/jdk-12.0.2.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.7</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/jdk1.7.0_80.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

``` xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>11</version>
      <vendor>Debian</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-1.11.0-openjdk-amd64</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>11</version>
      <vendor>Debian</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-11-openjdk-amd64</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-1.8.0-openjdk-amd64</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>Oracle Corporation</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-8-openjdk-amd64</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```


<b id="endnote1">[1]</b>
Even though the earliest Java version supported by the java source is 1.5,
the earliest code Oracle's JDK 12 can produce is for 1.7.
[↩](#ref1)
