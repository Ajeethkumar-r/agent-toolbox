# Adding a New Tool

This guide walks through creating a new tool module for Agent Toolbox. The agent discovers tools automatically via Java's `ServiceLoader` (SPI), so no changes to the core or CLI modules are needed.

## Step 1: Create the Maven Module

Create a new directory under the project root:

```
tool-mytool/
  src/main/java/io/agenttoolbox/tool/mytool/
  src/main/resources/META-INF/services/
  src/test/java/io/agenttoolbox/tool/mytool/
  pom.xml
```

## Step 2: Write the `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>tool-mytool</artifactId>
    <name>Tool MyTool</name>
    <description>Description of what this tool does</description>

    <dependencies>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-common</artifactId>
        </dependency>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-core</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## Step 3: Implement `@Tool` Methods

Create a class with methods annotated with LangChain4j's `@Tool`. Each method becomes an action the LLM can invoke.

```java
package io.agenttoolbox.tool.mytool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class MyTools {

    @Tool("Short description of what this tool does — the LLM reads this")
    public String myAction(
            @P("Description of parameter") String param1,
            @P("Description of another parameter") String param2) {
        // Implementation here
        return "Result message";
    }
}
```

Guidelines for `@Tool` methods:
- The description string is sent to the LLM as the tool's purpose -- be clear and concise.
- Use `@P` to describe each parameter so the LLM knows what values to provide.
- Return a `String` describing the outcome. The LLM relays this to the user.
- Throw exceptions for errors; the agent framework catches and reports them.

## Step 4: Implement `ToolProvider`

Create a class that implements `io.agenttoolbox.core.ToolProvider`:

```java
package io.agenttoolbox.tool.mytool;

import io.agenttoolbox.core.ToolProvider;

public class MyToolProvider implements ToolProvider {

    @Override
    public String name() {
        return "mytool";
    }

    @Override
    public Object toolInstance() {
        // Wire up dependencies and return the @Tool-annotated instance
        return new MyTools();
    }
}
```

## Step 5: Register via SPI

Create the file `src/main/resources/META-INF/services/io.agenttoolbox.core.ToolProvider` with the fully qualified class name:

```
io.agenttoolbox.tool.mytool.MyToolProvider
```

This allows `ServiceLoader` to discover the tool automatically at runtime.

## Step 6: Add to Parent POM

Add the new module to the root `pom.xml`:

```xml
<modules>
    <module>agent-common</module>
    <module>agent-core</module>
    <module>tool-etag</module>
    <module>tool-mytool</module>          <!-- add this -->
    <module>agent-cli</module>
    <module>agent-integration-tests</module>
</modules>
```

Also add it as a runtime dependency in `agent-cli/pom.xml` so the fat JAR includes it:

```xml
<dependency>
    <groupId>io.agenttoolbox</groupId>
    <artifactId>tool-mytool</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Step 7: Build and Test

```bash
# Build everything
./mvnw clean verify

# Run just your module's tests
./mvnw test -pl tool-mytool
```

## Checklist

- [ ] `pom.xml` with correct parent and dependencies
- [ ] `@Tool`-annotated methods with clear descriptions
- [ ] `ToolProvider` implementation returning the tool instance
- [ ] `META-INF/services/io.agenttoolbox.core.ToolProvider` SPI file
- [ ] Module added to root `pom.xml` `<modules>`
- [ ] Runtime dependency added to `agent-cli/pom.xml`
- [ ] Unit tests for service/business logic
- [ ] Integration test in `agent-integration-tests` (optional but recommended)
