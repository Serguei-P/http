Simple HTTP client and a simple HTTP server. Easy to use and small.

Demonstrates that if one needs to write an application communicating via HTTP one does not need large libraries or frameworks.

This only uses what is included in Java JDK. The only external dependancy - JUnit (testing).
Java 8 is used for development, but the code is mostly classic Java (no Stream API or Lambdas).

To include the latest stable version from Maven Repository add the following to your pom.xml:

```xml
<!-- https://mvnrepository.com/artifact/com.github.serguei-p/http -->
<dependency>
    <groupId>com.github.serguei-p</groupId>
    <artifactId>http</artifactId>
    <version>1.0.18</version>
</dependency>
```

