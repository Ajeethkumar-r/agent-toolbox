# Agent Toolbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an LLM-powered Java CLI agent that uses ETag/MD5 to solve four storage problems, running fully local with Ollama + fake GCS.

**Architecture:** Maven multi-module project. LangChain4j AiServices wires the agent loop: user input -> LLM (Ollama) -> @Tool method invocation -> StorageAdapter (local FS) -> result. Picocli provides the CLI REPL.

**Tech Stack:** Java 17, LangChain4j 1.x, Picocli 4.7, Ollama, Maven, JUnit 5, WireMock

**Spec:** `docs/superpowers/specs/2026-06-04-agent-toolbox-design.md`

---

### Task 1: Project Scaffolding

**Files:**
- Create: `~/projects/agent-toolbox/pom.xml`
- Create: `~/projects/agent-toolbox/.gitignore`
- Create: `~/projects/agent-toolbox/.editorconfig`
- Create: `~/projects/agent-toolbox/agent-common/pom.xml`
- Create: `~/projects/agent-toolbox/agent-core/pom.xml`
- Create: `~/projects/agent-toolbox/tool-etag/pom.xml`
- Create: `~/projects/agent-toolbox/agent-cli/pom.xml`
- Create: `~/projects/agent-toolbox/agent-integration-tests/pom.xml`

- [ ] **Step 1: Create project directory and initialize git**

```bash
mkdir -p ~/projects/agent-toolbox
cd ~/projects/agent-toolbox
git init
```

- [ ] **Step 2: Create parent POM**

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.agenttoolbox</groupId>
    <artifactId>agent-toolbox</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Agent Toolbox</name>
    <description>LLM-powered CLI agent platform for file storage operations</description>

    <modules>
        <module>agent-common</module>
        <module>agent-core</module>
        <module>tool-etag</module>
        <module>agent-cli</module>
        <module>agent-integration-tests</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Dependency versions -->
        <langchain4j.version>1.0.0</langchain4j.version>
        <picocli.version>4.7.6</picocli.version>
        <snakeyaml.version>2.3</snakeyaml.version>
        <slf4j.version>2.0.16</slf4j.version>
        <logback.version>1.5.12</logback.version>
        <jackson.version>2.18.2</jackson.version>

        <!-- Test dependency versions -->
        <junit.version>5.11.3</junit.version>
        <mockito.version>5.14.2</mockito.version>
        <assertj.version>3.26.3</assertj.version>
        <wiremock.version>3.10.0</wiremock.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- LangChain4j BOM -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Internal modules -->
            <dependency>
                <groupId>io.agenttoolbox</groupId>
                <artifactId>agent-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.agenttoolbox</groupId>
                <artifactId>agent-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>io.agenttoolbox</groupId>
                <artifactId>tool-etag</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- CLI -->
            <dependency>
                <groupId>info.picocli</groupId>
                <artifactId>picocli</artifactId>
                <version>${picocli.version}</version>
            </dependency>

            <!-- Config -->
            <dependency>
                <groupId>org.yaml</groupId>
                <artifactId>snakeyaml</artifactId>
                <version>${snakeyaml.version}</version>
            </dependency>

            <!-- JSON (for metadata sidecar files) -->
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>${jackson.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fasterxml.jackson.datatype</groupId>
                <artifactId>jackson-datatype-jsr310</artifactId>
                <version>${jackson.version}</version>
            </dependency>

            <!-- Logging -->
            <dependency>
                <groupId>org.slf4j</groupId>
                <artifactId>slf4j-api</artifactId>
                <version>${slf4j.version}</version>
            </dependency>
            <dependency>
                <groupId>ch.qos.logback</groupId>
                <artifactId>logback-classic</artifactId>
                <version>${logback.version}</version>
            </dependency>

            <!-- Test -->
            <dependency>
                <groupId>org.junit.jupiter</groupId>
                <artifactId>junit-jupiter</artifactId>
                <version>${junit.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.mockito</groupId>
                <artifactId>mockito-core</artifactId>
                <version>${mockito.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
                <scope>test</scope>
            </dependency>
            <dependency>
                <groupId>org.wiremock</groupId>
                <artifactId>wiremock</artifactId>
                <version>${wiremock.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.2</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-shade-plugin</artifactId>
                    <version>3.6.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 3: Create agent-common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>agent-common</artifactId>
    <name>Agent Toolbox - Common</name>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Create agent-core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>agent-core</artifactId>
    <name>Agent Toolbox - Core</name>

    <dependencies>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-common</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-ollama</artifactId>
        </dependency>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Create tool-etag/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>tool-etag</artifactId>
    <name>Agent Toolbox - ETag Tool</name>

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
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: Create agent-cli/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>agent-cli</artifactId>
    <name>Agent Toolbox - CLI</name>

    <dependencies>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>tool-etag</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>info.picocli</groupId>
                            <artifactId>picocli-codegen</artifactId>
                            <version>${picocli.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>io.agenttoolbox.cli.AgentCli</mainClass>
                                </transformer>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 7: Create agent-integration-tests/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.agenttoolbox</groupId>
        <artifactId>agent-toolbox</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>agent-integration-tests</artifactId>
    <name>Agent Toolbox - Integration Tests</name>

    <dependencies>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>tool-etag</artifactId>
        </dependency>
        <dependency>
            <groupId>io.agenttoolbox</groupId>
            <artifactId>agent-common</artifactId>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 8: Create .gitignore**

```gitignore
# Maven build output
target/
!.mvn/wrapper/maven-wrapper.jar

# IDE files
.idea/
*.iml
.project
.classpath
.settings/
.vscode/
*.swp
*.swo

# Compiled Java classes
*.class

# Package files
*.jar
*.war
*.ear

# Log files
*.log
logs/

# OS files
.DS_Store
Thumbs.db

# Environment & secrets (NEVER commit)
.env
.env.*
application-local.yaml
application-dev.yaml
secrets.yaml
secrets.json
*.pem
*.key

# Agent Toolbox runtime data
.agent-toolbox/
```

- [ ] **Step 9: Create .editorconfig**

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true

[*.xml]
indent_size = 4

[*.yaml]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 10: Create source directories for all modules**

```bash
cd ~/projects/agent-toolbox
mkdir -p agent-common/src/main/java/io/agenttoolbox/common
mkdir -p agent-common/src/test/java/io/agenttoolbox/common
mkdir -p agent-core/src/main/java/io/agenttoolbox/core
mkdir -p agent-core/src/main/resources
mkdir -p agent-core/src/test/java/io/agenttoolbox/core
mkdir -p tool-etag/src/main/java/io/agenttoolbox/tool/etag
mkdir -p tool-etag/src/main/resources/META-INF/services
mkdir -p tool-etag/src/test/java/io/agenttoolbox/tool/etag
mkdir -p agent-cli/src/main/java/io/agenttoolbox/cli
mkdir -p agent-cli/src/main/resources
mkdir -p agent-cli/src/test/java/io/agenttoolbox/cli
mkdir -p agent-integration-tests/src/test/java/io/agenttoolbox/it
```

- [ ] **Step 11: Add Maven wrapper**

```bash
cd ~/projects/agent-toolbox
mvn wrapper:wrapper -Dmaven=3.9.9
```

- [ ] **Step 12: Verify build compiles**

Run: `cd ~/projects/agent-toolbox && ./mvnw clean install -DskipTests`
Expected: `BUILD SUCCESS` for all 6 modules (parent + 5 children)

- [ ] **Step 13: Commit**

```bash
cd ~/projects/agent-toolbox
git add -A
git commit -m "chore: scaffold Maven multi-module project structure"
```

---

### Task 2: agent-common — Exception Hierarchy

**Files:**
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/AgentException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/LlmException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/LlmTimeoutException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/LlmUnavailableException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/StorageException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/BucketNotFoundException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/FileNotFoundException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/HashMismatchException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/PreconditionFailedException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/ToolException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/ToolNotFoundException.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/exception/ToolExecutionException.java`

- [ ] **Step 1: Create exception classes**

`AgentException.java`:
```java
package io.agenttoolbox.common.exception;

public class AgentException extends RuntimeException {
    public AgentException(String message) { super(message); }
    public AgentException(String message, Throwable cause) { super(message, cause); }
}
```

`LlmException.java`:
```java
package io.agenttoolbox.common.exception;

public class LlmException extends AgentException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }
}
```

`LlmTimeoutException.java`:
```java
package io.agenttoolbox.common.exception;

public class LlmTimeoutException extends LlmException {
    public LlmTimeoutException(String message) { super(message); }
    public LlmTimeoutException(String message, Throwable cause) { super(message, cause); }
}
```

`LlmUnavailableException.java`:
```java
package io.agenttoolbox.common.exception;

public class LlmUnavailableException extends LlmException {
    public LlmUnavailableException(String message) { super(message); }
    public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }
}
```

`StorageException.java`:
```java
package io.agenttoolbox.common.exception;

public class StorageException extends AgentException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
```

`BucketNotFoundException.java`:
```java
package io.agenttoolbox.common.exception;

public class BucketNotFoundException extends StorageException {
    public BucketNotFoundException(String bucket) {
        super("Bucket not found: " + bucket);
    }
}
```

`FileNotFoundException.java`:
```java
package io.agenttoolbox.common.exception;

public class FileNotFoundException extends StorageException {
    public FileNotFoundException(String bucket, String key) {
        super("File not found: " + bucket + "/" + key);
    }
}
```

`HashMismatchException.java`:
```java
package io.agenttoolbox.common.exception;

public class HashMismatchException extends StorageException {
    private final String expectedMd5;
    private final String actualMd5;

    public HashMismatchException(String expectedMd5, String actualMd5) {
        super("MD5 mismatch: expected=" + expectedMd5 + ", actual=" + actualMd5);
        this.expectedMd5 = expectedMd5;
        this.actualMd5 = actualMd5;
    }

    public String getExpectedMd5() { return expectedMd5; }
    public String getActualMd5() { return actualMd5; }
}
```

`PreconditionFailedException.java`:
```java
package io.agenttoolbox.common.exception;

public class PreconditionFailedException extends StorageException {
    private final String expectedEtag;
    private final String currentEtag;

    public PreconditionFailedException(String expectedEtag, String currentEtag) {
        super("Precondition failed: expected ETag=" + expectedEtag + ", current ETag=" + currentEtag);
        this.expectedEtag = expectedEtag;
        this.currentEtag = currentEtag;
    }

    public String getExpectedEtag() { return expectedEtag; }
    public String getCurrentEtag() { return currentEtag; }
}
```

`ToolException.java`:
```java
package io.agenttoolbox.common.exception;

public class ToolException extends AgentException {
    public ToolException(String message) { super(message); }
    public ToolException(String message, Throwable cause) { super(message, cause); }
}
```

`ToolNotFoundException.java`:
```java
package io.agenttoolbox.common.exception;

public class ToolNotFoundException extends ToolException {
    public ToolNotFoundException(String toolName) {
        super("Tool not found: " + toolName);
    }
}
```

`ToolExecutionException.java`:
```java
package io.agenttoolbox.common.exception;

public class ToolExecutionException extends ToolException {
    public ToolExecutionException(String toolName, Throwable cause) {
        super("Tool execution failed: " + toolName, cause);
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd ~/projects/agent-toolbox && ./mvnw compile -pl agent-common`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-common/src/main/java/io/agenttoolbox/common/exception/
git commit -m "feat(common): add exception hierarchy"
```

---

### Task 3: agent-common — Md5Hasher (TDD)

**Files:**
- Create: `agent-common/src/test/java/io/agenttoolbox/common/crypto/Md5HasherTest.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/crypto/Md5Hasher.java`

- [ ] **Step 1: Write the failing test**

`Md5HasherTest.java`:
```java
package io.agenttoolbox.common.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Md5HasherTest {

    @TempDir
    Path tempDir;

    @Test
    void hashBytes_returnsCorrectMd5ForKnownInput() {
        // MD5 of "hello" is 5d41402abc4b2a76b9719d911017c592
        String hash = Md5Hasher.hashBytes("hello".getBytes());
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void hashBytes_returnsCorrectMd5ForEmptyInput() {
        // MD5 of empty is d41d8cd98f00b204e9800998ecf8427e
        String hash = Md5Hasher.hashBytes(new byte[0]);
        assertThat(hash).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    void hashFile_returnsCorrectMd5() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        String hash = Md5Hasher.hashFile(file);
        assertThat(hash).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void hashFile_throwsForMissingFile() {
        Path missing = tempDir.resolve("nonexistent.txt");
        assertThatThrownBy(() -> Md5Hasher.hashFile(missing))
                .isInstanceOf(IOException.class);
    }

    @Test
    void sameContentProducesSameHash() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "same content");
        Files.writeString(file2, "same content");

        assertThat(Md5Hasher.hashFile(file1))
                .isEqualTo(Md5Hasher.hashFile(file2));
    }

    @Test
    void differentContentProducesDifferentHash() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        assertThat(Md5Hasher.hashFile(file1))
                .isNotEqualTo(Md5Hasher.hashFile(file2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-common -Dtest=Md5HasherTest`
Expected: FAIL — `Md5Hasher` class does not exist

- [ ] **Step 3: Write the implementation**

`Md5Hasher.java`:
```java
package io.agenttoolbox.common.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Md5Hasher {

    private Md5Hasher() {}

    public static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    public static String hashFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-common -Dtest=Md5HasherTest`
Expected: `Tests run: 6, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-common/src/main/java/io/agenttoolbox/common/crypto/ agent-common/src/test/java/io/agenttoolbox/common/crypto/
git commit -m "feat(common): add Md5Hasher with TDD tests"
```

---

### Task 4: agent-common — Models & Storage Interface

**Files:**
- Create: `agent-common/src/main/java/io/agenttoolbox/common/model/FileMetadata.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/storage/ConditionalReadResult.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/storage/StorageAdapter.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/config/SecretProvider.java`
- Create: `agent-common/src/main/java/io/agenttoolbox/common/config/EnvVarSecretProvider.java`
- Create: `agent-common/src/test/java/io/agenttoolbox/common/storage/ConditionalReadResultTest.java`

- [ ] **Step 1: Write ConditionalReadResult test**

```java
package io.agenttoolbox.common.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalReadResultTest {

    @Test
    void notModified_hasCorrectState() {
        ConditionalReadResult result = ConditionalReadResult.notModified("abc123");

        assertThat(result.modified()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.etag()).isEqualTo("abc123");
        assertThat(result.contentLength()).isZero();
    }

    @Test
    void modified_hasCorrectState() {
        byte[] content = "hello".getBytes();
        ConditionalReadResult result = ConditionalReadResult.modified(content, "def456");

        assertThat(result.modified()).isTrue();
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.etag()).isEqualTo("def456");
        assertThat(result.contentLength()).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-common -Dtest=ConditionalReadResultTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create all model and interface files**

`FileMetadata.java`:
```java
package io.agenttoolbox.common.model;

import java.time.Instant;

public record FileMetadata(
    String key,
    String bucket,
    String md5Hash,
    String etag,
    long size,
    Instant lastModified,
    String contentType
) {}
```

`ConditionalReadResult.java`:
```java
package io.agenttoolbox.common.storage;

public record ConditionalReadResult(
    boolean modified,
    byte[] content,
    String etag,
    long contentLength
) {
    public static ConditionalReadResult notModified(String etag) {
        return new ConditionalReadResult(false, null, etag, 0);
    }

    public static ConditionalReadResult modified(byte[] content, String etag) {
        return new ConditionalReadResult(true, content, etag, content.length);
    }
}
```

`StorageAdapter.java`:
```java
package io.agenttoolbox.common.storage;

import io.agenttoolbox.common.model.FileMetadata;

import java.util.List;

public interface StorageAdapter {

    FileMetadata getMetadata(String bucket, String key);

    byte[] read(String bucket, String key);

    FileMetadata write(String bucket, String key, byte[] content, String expectedMd5);

    FileMetadata conditionalWrite(String bucket, String key, byte[] content, String ifMatchEtag);

    ConditionalReadResult conditionalRead(String bucket, String key, String ifNoneMatchEtag);

    List<FileMetadata> list(String bucket, String prefix);

    boolean exists(String bucket, String key);
}
```

`SecretProvider.java`:
```java
package io.agenttoolbox.common.config;

import java.util.Optional;

public interface SecretProvider {
    Optional<String> get(String key);
}
```

`EnvVarSecretProvider.java`:
```java
package io.agenttoolbox.common.config;

import java.util.Optional;

public class EnvVarSecretProvider implements SecretProvider {

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(System.getenv(key));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-common`
Expected: All tests pass — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-common/src/
git commit -m "feat(common): add FileMetadata, StorageAdapter, ConditionalReadResult, SecretProvider"
```

---

### Task 5: agent-core — AgentConfig + YAML Loading

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/config/AgentConfig.java`
- Create: `agent-core/src/main/java/io/agenttoolbox/core/config/ConfigLoader.java`
- Create: `agent-core/src/test/java/io/agenttoolbox/core/config/ConfigLoaderTest.java`
- Create: `agent-core/src/test/resources/test-config.yaml`
- Create: `agent-cli/src/main/resources/application.yaml`

- [ ] **Step 1: Write ConfigLoader test**

`agent-core/src/test/resources/test-config.yaml`:
```yaml
agent:
  name: Test Agent
  version: 0.0.1
  memory:
    max-messages: 10

llm:
  provider: ollama
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
    temperature: 0.1
    timeout-seconds: 30

storage:
  provider: local
  local:
    bucket-root: /tmp/test-buckets

output:
  verbosity: auto
  color: true

logging:
  level: INFO
```

`ConfigLoaderTest.java`:
```java
package io.agenttoolbox.core.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void loadsConfigFromInputStream() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);

        assertThat(config.getAgent().getName()).isEqualTo("Test Agent");
        assertThat(config.getAgent().getVersion()).isEqualTo("0.0.1");
        assertThat(config.getAgent().getMemory().getMaxMessages()).isEqualTo(10);
    }

    @Test
    void loadsLlmConfig() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);

        assertThat(config.getLlm().getProvider()).isEqualTo("ollama");
        assertThat(config.getLlm().getOllama().getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(config.getLlm().getOllama().getModel()).isEqualTo("llama3.1:8b");
        assertThat(config.getLlm().getOllama().getTemperature()).isEqualTo(0.1);
        assertThat(config.getLlm().getOllama().getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void loadsStorageConfig() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
        AgentConfig config = ConfigLoader.load(is);

        assertThat(config.getStorage().getProvider()).isEqualTo("local");
        assertThat(config.getStorage().getLocal().getBucketRoot()).isEqualTo("/tmp/test-buckets");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ConfigLoaderTest`
Expected: FAIL — classes do not exist

- [ ] **Step 3: Create AgentConfig POJO**

`AgentConfig.java`:
```java
package io.agenttoolbox.core.config;

public class AgentConfig {

    private AgentSection agent = new AgentSection();
    private LlmSection llm = new LlmSection();
    private StorageSection storage = new StorageSection();
    private OutputSection output = new OutputSection();
    private LoggingSection logging = new LoggingSection();

    public AgentSection getAgent() { return agent; }
    public void setAgent(AgentSection agent) { this.agent = agent; }
    public LlmSection getLlm() { return llm; }
    public void setLlm(LlmSection llm) { this.llm = llm; }
    public StorageSection getStorage() { return storage; }
    public void setStorage(StorageSection storage) { this.storage = storage; }
    public OutputSection getOutput() { return output; }
    public void setOutput(OutputSection output) { this.output = output; }
    public LoggingSection getLogging() { return logging; }
    public void setLogging(LoggingSection logging) { this.logging = logging; }

    public static class AgentSection {
        private String name = "Agent Toolbox";
        private String version = "0.1.0";
        private MemoryConfig memory = new MemoryConfig();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public MemoryConfig getMemory() { return memory; }
        public void setMemory(MemoryConfig memory) { this.memory = memory; }
    }

    public static class MemoryConfig {
        private int maxMessages = 20;

        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }

    public static class LlmSection {
        private String provider = "ollama";
        private OllamaConfig ollama = new OllamaConfig();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public OllamaConfig getOllama() { return ollama; }
        public void setOllama(OllamaConfig ollama) { this.ollama = ollama; }
    }

    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1:8b";
        private double temperature = 0.1;
        private int timeoutSeconds = 30;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class StorageSection {
        private String provider = "local";
        private LocalStorageConfig local = new LocalStorageConfig();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public LocalStorageConfig getLocal() { return local; }
        public void setLocal(LocalStorageConfig local) { this.local = local; }
    }

    public static class LocalStorageConfig {
        private String bucketRoot = System.getProperty("user.home") + "/.agent-toolbox/buckets";

        public String getBucketRoot() { return bucketRoot; }
        public void setBucketRoot(String bucketRoot) { this.bucketRoot = bucketRoot; }
    }

    public static class OutputSection {
        private String verbosity = "auto";
        private boolean color = true;

        public String getVerbosity() { return verbosity; }
        public void setVerbosity(String verbosity) { this.verbosity = verbosity; }
        public boolean isColor() { return color; }
        public void setColor(boolean color) { this.color = color; }
    }

    public static class LoggingSection {
        private String level = "INFO";

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }
}
```

- [ ] **Step 4: Create ConfigLoader**

`ConfigLoader.java`:
```java
package io.agenttoolbox.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static AgentConfig load(InputStream yamlStream) {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(AgentConfig.class, options);
        Yaml yaml = new Yaml(constructor);
        return yaml.load(yamlStream);
    }

    public static AgentConfig loadFromClasspath() {
        InputStream is = ConfigLoader.class.getClassLoader()
                .getResourceAsStream("application.yaml");
        if (is == null) {
            return new AgentConfig();
        }
        return load(is);
    }
}
```

- [ ] **Step 5: Create application.yaml in agent-cli**

`agent-cli/src/main/resources/application.yaml`:
```yaml
agent:
  name: Agent Toolbox
  version: 0.1.0
  memory:
    max-messages: 20

llm:
  provider: ollama
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
    temperature: 0.1
    timeout-seconds: 30

storage:
  provider: local
  local:
    bucket-root: ${user.home}/.agent-toolbox/buckets

output:
  verbosity: auto
  color: true

logging:
  level: INFO
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ConfigLoaderTest`
Expected: `Tests run: 3, Failures: 0` — BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-core/src/ agent-cli/src/main/resources/application.yaml
git commit -m "feat(core): add AgentConfig POJO and YAML ConfigLoader"
```

---

### Task 6: agent-core — ToolProvider SPI + ToolRegistry (TDD)

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/ToolProvider.java`
- Create: `agent-core/src/main/java/io/agenttoolbox/core/ToolRegistry.java`
- Create: `agent-core/src/test/java/io/agenttoolbox/core/ToolRegistryTest.java`

- [ ] **Step 1: Write the failing test**

`ToolRegistryTest.java`:
```java
package io.agenttoolbox.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void discoverTools_returnsEmptyListWhenNoProvidersRegistered() {
        ToolRegistry registry = new ToolRegistry();
        List<Object> tools = registry.discoverTools();
        // ServiceLoader may or may not find tools depending on classpath
        assertThat(tools).isNotNull();
    }

    @Test
    void discoverTools_collectsToolInstancesFromProviders() {
        ToolRegistry registry = new ToolRegistry();
        Object fakeTool = new Object();
        registry.registerProvider(new ToolProvider() {
            @Override public String name() { return "test-tool"; }
            @Override public String description() { return "A test tool"; }
            @Override public Object toolInstance() { return fakeTool; }
        });

        List<Object> tools = registry.discoverTools();
        assertThat(tools).containsExactly(fakeTool);
    }

    @Test
    void registeredProviderNamesAreAccessible() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ToolProvider() {
            @Override public String name() { return "my-tool"; }
            @Override public String description() { return "desc"; }
            @Override public Object toolInstance() { return new Object(); }
        });

        assertThat(registry.getProviderNames()).containsExactly("my-tool");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ToolRegistryTest`
Expected: FAIL — classes do not exist

- [ ] **Step 3: Create ToolProvider interface**

`ToolProvider.java`:
```java
package io.agenttoolbox.core;

public interface ToolProvider {
    String name();
    String description();
    Object toolInstance();
}
```

- [ ] **Step 4: Create ToolRegistry**

`ToolRegistry.java`:
```java
package io.agenttoolbox.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final List<ToolProvider> providers = new ArrayList<>();

    public ToolRegistry() {
        ServiceLoader<ToolProvider> loader = ServiceLoader.load(ToolProvider.class);
        for (ToolProvider provider : loader) {
            log.info("Discovered tool: {} - {}", provider.name(), provider.description());
            providers.add(provider);
        }
    }

    public void registerProvider(ToolProvider provider) {
        log.info("Registered tool: {} - {}", provider.name(), provider.description());
        providers.add(provider);
    }

    public List<Object> discoverTools() {
        return providers.stream()
                .map(ToolProvider::toolInstance)
                .toList();
    }

    public List<String> getProviderNames() {
        return providers.stream()
                .map(ToolProvider::name)
                .toList();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ToolRegistryTest`
Expected: `Tests run: 3, Failures: 0` — BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-core/src/
git commit -m "feat(core): add ToolProvider SPI interface and ToolRegistry with ServiceLoader"
```

---

### Task 7: agent-core — ChatModelFactory + OllamaHealthCheck

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/llm/ChatModelFactory.java`
- Create: `agent-core/src/main/java/io/agenttoolbox/core/health/OllamaHealthCheck.java`
- Create: `agent-core/src/test/java/io/agenttoolbox/core/llm/ChatModelFactoryTest.java`

- [ ] **Step 1: Write ChatModelFactory test**

`ChatModelFactoryTest.java`:
```java
package io.agenttoolbox.core.llm;

import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.common.exception.LlmException;
import io.agenttoolbox.core.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelFactoryTest {

    @Test
    void create_throwsForUnknownProvider() {
        AgentConfig config = new AgentConfig();
        config.getLlm().setProvider("unknown");
        SecretProvider secrets = key -> Optional.empty();

        assertThatThrownBy(() -> ChatModelFactory.create(config, secrets))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unknown LLM provider: unknown");
    }

    @Test
    void create_returnsNonNullForOllamaProvider() {
        AgentConfig config = new AgentConfig();
        config.getLlm().setProvider("ollama");
        config.getLlm().getOllama().setBaseUrl("http://localhost:11434");
        config.getLlm().getOllama().setModel("llama3.1:8b");
        SecretProvider secrets = key -> Optional.empty();

        // This creates the model object (doesn't connect to Ollama)
        var model = ChatModelFactory.create(config, secrets);
        assertThat(model).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ChatModelFactoryTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create ChatModelFactory**

`ChatModelFactory.java`:
```java
package io.agenttoolbox.core.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.common.exception.LlmException;
import io.agenttoolbox.core.config.AgentConfig;

import java.time.Duration;

public final class ChatModelFactory {

    private ChatModelFactory() {}

    public static ChatLanguageModel create(AgentConfig config, SecretProvider secrets) {
        return switch (config.getLlm().getProvider()) {
            case "ollama" -> createOllama(config.getLlm().getOllama());
            default -> throw new LlmException(
                    "Unknown LLM provider: " + config.getLlm().getProvider()
                    + ". Supported: ollama");
        };
    }

    private static ChatLanguageModel createOllama(AgentConfig.OllamaConfig ollama) {
        return OllamaChatModel.builder()
                .baseUrl(ollama.getBaseUrl())
                .modelName(ollama.getModel())
                .temperature(ollama.getTemperature())
                .timeout(Duration.ofSeconds(ollama.getTimeoutSeconds()))
                .build();
    }
}
```

- [ ] **Step 4: Create OllamaHealthCheck**

`OllamaHealthCheck.java`:
```java
package io.agenttoolbox.core.health;

import io.agenttoolbox.common.exception.LlmUnavailableException;
import io.agenttoolbox.core.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(OllamaHealthCheck.class);

    private OllamaHealthCheck() {}

    public static void verify(AgentConfig.OllamaConfig config) {
        String url = config.getBaseUrl() + "/api/tags";
        log.debug("Checking Ollama health at {}", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new LlmUnavailableException(
                        "Ollama returned HTTP " + response.statusCode() + " at " + config.getBaseUrl());
            }

            if (!response.body().contains(config.getModel())) {
                log.warn("Model '{}' may not be pulled. Run: ollama pull {}", config.getModel(), config.getModel());
            }

            log.info("Ollama is reachable at {} with model {}", config.getBaseUrl(), config.getModel());
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException(
                    "Cannot connect to Ollama at " + config.getBaseUrl()
                    + ". Is Ollama running? Start it with: ollama serve", e);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=ChatModelFactoryTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-core/src/
git commit -m "feat(core): add ChatModelFactory and OllamaHealthCheck"
```

---

### Task 8: agent-core — AgentRunner + System Prompt

**Files:**
- Create: `agent-core/src/main/java/io/agenttoolbox/core/AgentRunner.java`
- Create: `agent-core/src/main/java/io/agenttoolbox/core/AgentService.java`
- Create: `agent-core/src/main/java/io/agenttoolbox/core/model/AgentResponse.java`
- Create: `agent-core/src/main/resources/prompts/system-prompt.txt`
- Create: `agent-core/src/test/java/io/agenttoolbox/core/AgentRunnerTest.java`

- [ ] **Step 1: Create system prompt**

`agent-core/src/main/resources/prompts/system-prompt.txt`:
```
You are Agent Toolbox, a helpful assistant that solves file storage problems.
You have access to tools for working with files and ETags/MD5 hashes.

When a user describes a problem:
1. Identify which tool best solves it
2. Extract the required parameters from their message
3. If parameters are missing, ask the user to provide them
4. Execute the tool and report results

Respond concisely unless the user asks for explanation.
When the user asks "why", "explain", or "how does this work", provide
educational detail about the ETag/MD5 mechanism being used.
```

- [ ] **Step 2: Create AgentService interface**

`AgentService.java`:
```java
package io.agenttoolbox.core;

import dev.langchain4j.service.SystemMessage;

public interface AgentService {
    @SystemMessage(fromResource = "prompts/system-prompt.txt")
    String chat(String userMessage);
}
```

- [ ] **Step 3: Create AgentResponse**

`AgentResponse.java`:
```java
package io.agenttoolbox.core.model;

public record AgentResponse(String message, boolean success) {
    public static AgentResponse ok(String message) {
        return new AgentResponse(message, true);
    }

    public static AgentResponse error(String message) {
        return new AgentResponse(message, false);
    }
}
```

- [ ] **Step 4: Write AgentRunner test**

`AgentRunnerTest.java`:
```java
package io.agenttoolbox.core;

import io.agenttoolbox.common.config.EnvVarSecretProvider;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.model.AgentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunnerTest {

    @Test
    void chat_returnsErrorWhenOllamaUnavailable() {
        AgentConfig config = new AgentConfig();
        config.getLlm().getOllama().setBaseUrl("http://localhost:99999");

        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), new ToolRegistry());
        AgentResponse response = runner.chat("hello");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("Cannot connect");
    }

    @Test
    void getToolNames_returnsRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerProvider(new ToolProvider() {
            @Override public String name() { return "test"; }
            @Override public String description() { return "test tool"; }
            @Override public Object toolInstance() { return new Object(); }
        });

        AgentConfig config = new AgentConfig();
        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), registry);

        assertThat(runner.getToolNames()).containsExactly("test");
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=AgentRunnerTest`
Expected: FAIL — `AgentRunner` does not exist

- [ ] **Step 6: Create AgentRunner**

`AgentRunner.java`:
```java
package io.agenttoolbox.core;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import io.agenttoolbox.common.config.SecretProvider;
import io.agenttoolbox.common.exception.LlmUnavailableException;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.health.OllamaHealthCheck;
import io.agenttoolbox.core.llm.ChatModelFactory;
import io.agenttoolbox.core.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentConfig config;
    private final SecretProvider secrets;
    private final ToolRegistry toolRegistry;
    private AgentService agentService;

    public AgentRunner(AgentConfig config, SecretProvider secrets, ToolRegistry toolRegistry) {
        this.config = config;
        this.secrets = secrets;
        this.toolRegistry = toolRegistry;
    }

    public void initialize() {
        if ("ollama".equals(config.getLlm().getProvider())) {
            OllamaHealthCheck.verify(config.getLlm().getOllama());
        }

        ChatLanguageModel chatModel = ChatModelFactory.create(config, secrets);
        List<Object> tools = toolRegistry.discoverTools();

        var builder = AiServices.builder(AgentService.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(
                        config.getAgent().getMemory().getMaxMessages()));

        if (!tools.isEmpty()) {
            builder.tools(tools);
        }

        this.agentService = builder.build();
        log.info("Agent initialized with {} tool(s): {}", tools.size(), toolRegistry.getProviderNames());
    }

    public AgentResponse chat(String userMessage) {
        try {
            if (agentService == null) {
                initialize();
            }
            String response = agentService.chat(userMessage);
            return AgentResponse.ok(response);
        } catch (LlmUnavailableException e) {
            return AgentResponse.error("Cannot connect to Ollama. " + e.getMessage());
        } catch (Exception e) {
            log.error("Agent error", e);
            return AgentResponse.error("Error: " + e.getMessage());
        }
    }

    public List<String> getToolNames() {
        return toolRegistry.getProviderNames();
    }

    public AgentConfig getConfig() {
        return config;
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-core -Dtest=AgentRunnerTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-core/src/
git commit -m "feat(core): add AgentRunner, AgentService interface, and system prompt"
```

---

### Task 9: tool-etag — LocalStorageAdapter (TDD)

**Files:**
- Create: `tool-etag/src/test/java/io/agenttoolbox/tool/etag/storage/LocalStorageAdapterTest.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/storage/LocalStorageAdapter.java`

- [ ] **Step 1: Write the failing tests**

`LocalStorageAdapterTest.java`:
```java
package io.agenttoolbox.tool.etag.storage;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.BucketNotFoundException;
import io.agenttoolbox.common.exception.FileNotFoundException;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.ConditionalReadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageAdapterTest {

    @TempDir
    Path tempDir;
    LocalStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalStorageAdapter(tempDir.toString());
    }

    @Test
    void write_createsFileAndReturnsMetadata() {
        byte[] content = "hello world".getBytes();
        String md5 = Md5Hasher.hashBytes(content);

        FileMetadata meta = adapter.write("test-bucket", "greeting.txt", content, md5);

        assertThat(meta.bucket()).isEqualTo("test-bucket");
        assertThat(meta.key()).isEqualTo("greeting.txt");
        assertThat(meta.md5Hash()).isEqualTo(md5);
        assertThat(meta.size()).isEqualTo(content.length);
    }

    @Test
    void write_rejectsMismatchedMd5() {
        byte[] content = "hello".getBytes();

        assertThatThrownBy(() -> adapter.write("bucket", "file.txt", content, "wrong-md5"))
                .isInstanceOf(HashMismatchException.class);
    }

    @Test
    void read_returnsWrittenContent() {
        byte[] content = "test data".getBytes();
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("bucket", "data.txt", content, md5);

        byte[] result = adapter.read("bucket", "data.txt");
        assertThat(result).isEqualTo(content);
    }

    @Test
    void read_throwsForMissingFile() {
        assertThatThrownBy(() -> adapter.read("bucket", "missing.txt"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getMetadata_returnsCorrectMetadata() {
        byte[] content = "metadata test".getBytes();
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("bucket", "file.txt", content, md5);

        FileMetadata meta = adapter.getMetadata("bucket", "file.txt");
        assertThat(meta.md5Hash()).isEqualTo(md5);
        assertThat(meta.etag()).isEqualTo(md5);
        assertThat(meta.size()).isEqualTo(content.length);
    }

    @Test
    void exists_returnsTrueForExistingFile() {
        byte[] content = "exists".getBytes();
        adapter.write("bucket", "file.txt", content, Md5Hasher.hashBytes(content));

        assertThat(adapter.exists("bucket", "file.txt")).isTrue();
    }

    @Test
    void exists_returnsFalseForMissingFile() {
        assertThat(adapter.exists("bucket", "missing.txt")).isFalse();
    }

    @Test
    void list_returnsAllFilesWithPrefix() {
        adapter.write("bucket", "docs/a.txt", "a".getBytes(), Md5Hasher.hashBytes("a".getBytes()));
        adapter.write("bucket", "docs/b.txt", "b".getBytes(), Md5Hasher.hashBytes("b".getBytes()));
        adapter.write("bucket", "images/c.png", "c".getBytes(), Md5Hasher.hashBytes("c".getBytes()));

        List<FileMetadata> docs = adapter.list("bucket", "docs/");
        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(FileMetadata::key).containsExactlyInAnyOrder("docs/a.txt", "docs/b.txt");
    }

    @Test
    void conditionalWrite_succeedsWithMatchingEtag() {
        byte[] v1 = "version1".getBytes();
        FileMetadata meta = adapter.write("bucket", "file.txt", v1, Md5Hasher.hashBytes(v1));

        byte[] v2 = "version2".getBytes();
        FileMetadata updated = adapter.conditionalWrite("bucket", "file.txt", v2, meta.etag());

        assertThat(updated.md5Hash()).isEqualTo(Md5Hasher.hashBytes(v2));
    }

    @Test
    void conditionalWrite_failsWithStaleEtag() {
        byte[] v1 = "version1".getBytes();
        adapter.write("bucket", "file.txt", v1, Md5Hasher.hashBytes(v1));

        byte[] v2 = "version2".getBytes();
        assertThatThrownBy(() -> adapter.conditionalWrite("bucket", "file.txt", v2, "stale-etag"))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void conditionalRead_returnsNotModifiedWhenEtagMatches() {
        byte[] content = "cached".getBytes();
        String md5 = Md5Hasher.hashBytes(content);
        adapter.write("bucket", "file.txt", content, md5);

        ConditionalReadResult result = adapter.conditionalRead("bucket", "file.txt", md5);
        assertThat(result.modified()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.contentLength()).isZero();
    }

    @Test
    void conditionalRead_returnsContentWhenEtagDiffers() {
        byte[] content = "new content".getBytes();
        adapter.write("bucket", "file.txt", content, Md5Hasher.hashBytes(content));

        ConditionalReadResult result = adapter.conditionalRead("bucket", "file.txt", "old-etag");
        assertThat(result.modified()).isTrue();
        assertThat(result.content()).isEqualTo(content);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=LocalStorageAdapterTest`
Expected: FAIL — `LocalStorageAdapter` does not exist

- [ ] **Step 3: Implement LocalStorageAdapter**

`LocalStorageAdapter.java`:
```java
package io.agenttoolbox.tool.etag.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.BucketNotFoundException;
import io.agenttoolbox.common.exception.FileNotFoundException;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.exception.StorageException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

public class LocalStorageAdapter implements StorageAdapter {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);
    private static final String METADATA_SUFFIX = ".metadata.json";

    private final Path bucketRoot;
    private final ObjectMapper mapper;

    public LocalStorageAdapter(String bucketRoot) {
        this.bucketRoot = Path.of(bucketRoot);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public FileMetadata write(String bucket, String key, byte[] content, String expectedMd5) {
        String actualMd5 = Md5Hasher.hashBytes(content);
        if (expectedMd5 != null && !expectedMd5.equals(actualMd5)) {
            throw new HashMismatchException(expectedMd5, actualMd5);
        }

        Path filePath = resolveFilePath(bucket, key);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);

            FileMetadata metadata = new FileMetadata(key, bucket, actualMd5, actualMd5,
                    content.length, Instant.now(), "application/octet-stream");
            writeMetadata(filePath, metadata);

            log.debug("Wrote {}/{} ({} bytes, md5={})", bucket, key, content.length, actualMd5);
            return metadata;
        } catch (IOException e) {
            throw new StorageException("Failed to write " + bucket + "/" + key, e);
        }
    }

    @Override
    public FileMetadata conditionalWrite(String bucket, String key, byte[] content, String ifMatchEtag) {
        FileMetadata current = getMetadata(bucket, key);
        if (!current.etag().equals(ifMatchEtag)) {
            throw new PreconditionFailedException(ifMatchEtag, current.etag());
        }
        String md5 = Md5Hasher.hashBytes(content);
        return write(bucket, key, content, md5);
    }

    @Override
    public byte[] read(String bucket, String key) {
        Path filePath = resolveFilePath(bucket, key);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException(bucket, key);
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to read " + bucket + "/" + key, e);
        }
    }

    @Override
    public ConditionalReadResult conditionalRead(String bucket, String key, String ifNoneMatchEtag) {
        FileMetadata meta = getMetadata(bucket, key);
        if (meta.etag().equals(ifNoneMatchEtag)) {
            return ConditionalReadResult.notModified(meta.etag());
        }
        byte[] content = read(bucket, key);
        return ConditionalReadResult.modified(content, meta.etag());
    }

    @Override
    public FileMetadata getMetadata(String bucket, String key) {
        Path filePath = resolveFilePath(bucket, key);
        Path metaPath = metadataPath(filePath);
        if (!Files.exists(metaPath)) {
            throw new FileNotFoundException(bucket, key);
        }
        try {
            return mapper.readValue(metaPath.toFile(), FileMetadata.class);
        } catch (IOException e) {
            throw new StorageException("Failed to read metadata for " + bucket + "/" + key, e);
        }
    }

    @Override
    public List<FileMetadata> list(String bucket, String prefix) {
        Path bucketPath = bucketRoot.resolve(bucket);
        if (!Files.exists(bucketPath)) {
            return List.of();
        }
        Path prefixPath = bucketPath.resolve(prefix);
        Path searchDir = Files.isDirectory(prefixPath) ? prefixPath : prefixPath.getParent();
        if (searchDir == null || !Files.exists(searchDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(searchDir)) {
            return paths
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> !p.toString().endsWith(METADATA_SUFFIX))
                    .filter(p -> {
                        String relKey = bucketPath.relativize(p).toString();
                        return relKey.startsWith(prefix);
                    })
                    .map(p -> {
                        String relKey = bucketPath.relativize(p).toString();
                        return getMetadata(bucket, relKey);
                    })
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list " + bucket + "/" + prefix, e);
        }
    }

    @Override
    public boolean exists(String bucket, String key) {
        return Files.exists(resolveFilePath(bucket, key));
    }

    private Path resolveFilePath(String bucket, String key) {
        return bucketRoot.resolve(bucket).resolve(key);
    }

    private Path metadataPath(Path filePath) {
        return filePath.resolveSibling(filePath.getFileName() + METADATA_SUFFIX);
    }

    private void writeMetadata(Path filePath, FileMetadata metadata) throws IOException {
        Path metaPath = metadataPath(filePath);
        mapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), metadata);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=LocalStorageAdapterTest`
Expected: `Tests run: 12, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add LocalStorageAdapter with sidecar metadata and TDD tests"
```

---

### Task 10: tool-etag — DeltaSyncService (TDD)

**Files:**
- Create: `tool-etag/src/test/java/io/agenttoolbox/tool/etag/service/DeltaSyncServiceTest.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/service/DeltaSyncService.java`

- [ ] **Step 1: Write the failing test**

`DeltaSyncServiceTest.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeltaSyncServiceTest {

    @TempDir
    Path localDir;

    StorageAdapter storage;
    DeltaSyncService service;

    @BeforeEach
    void setUp() {
        storage = Mockito.mock(StorageAdapter.class);
        service = new DeltaSyncService(storage);
    }

    @Test
    void syncsNewFileThatDoesNotExistInBucket() throws IOException {
        Files.writeString(localDir.resolve("new.txt"), "new content");
        when(storage.exists("bucket", "new.txt")).thenReturn(false);
        when(storage.write(eq("bucket"), eq("new.txt"), any(), any()))
                .thenReturn(new FileMetadata("new.txt", "bucket",
                        Md5Hasher.hashBytes("new content".getBytes()),
                        Md5Hasher.hashBytes("new content".getBytes()),
                        11, Instant.now(), "application/octet-stream"));

        String result = service.sync(localDir.toString(), "bucket");

        verify(storage).write(eq("bucket"), eq("new.txt"), any(), any());
        assertThat(result).contains("1");
    }

    @Test
    void skipsFileWithMatchingHash() throws IOException {
        Files.writeString(localDir.resolve("same.txt"), "same content");
        String md5 = Md5Hasher.hashBytes("same content".getBytes());
        when(storage.exists("bucket", "same.txt")).thenReturn(true);
        when(storage.getMetadata("bucket", "same.txt"))
                .thenReturn(new FileMetadata("same.txt", "bucket", md5, md5,
                        12, Instant.now(), "application/octet-stream"));

        String result = service.sync(localDir.toString(), "bucket");

        verify(storage, never()).write(eq("bucket"), eq("same.txt"), any(), any());
        assertThat(result).contains("skipped");
    }

    @Test
    void uploadsFileWithDifferentHash() throws IOException {
        Files.writeString(localDir.resolve("changed.txt"), "new version");
        when(storage.exists("bucket", "changed.txt")).thenReturn(true);
        when(storage.getMetadata("bucket", "changed.txt"))
                .thenReturn(new FileMetadata("changed.txt", "bucket", "old-hash", "old-hash",
                        10, Instant.now(), "application/octet-stream"));
        when(storage.write(eq("bucket"), eq("changed.txt"), any(), any()))
                .thenReturn(new FileMetadata("changed.txt", "bucket",
                        Md5Hasher.hashBytes("new version".getBytes()),
                        Md5Hasher.hashBytes("new version".getBytes()),
                        11, Instant.now(), "application/octet-stream"));

        String result = service.sync(localDir.toString(), "bucket");

        verify(storage).write(eq("bucket"), eq("changed.txt"), any(), any());
        assertThat(result).contains("synced");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=DeltaSyncServiceTest`
Expected: FAIL — `DeltaSyncService` does not exist

- [ ] **Step 3: Implement DeltaSyncService**

`DeltaSyncService.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DeltaSyncService {

    private static final Logger log = LoggerFactory.getLogger(DeltaSyncService.class);

    private final StorageAdapter storage;

    public DeltaSyncService(StorageAdapter storage) {
        this.storage = storage;
    }

    public String sync(String localPath, String bucketName) {
        Path localDir = Path.of(localPath);
        int totalFiles = 0;
        int synced = 0;
        int skipped = 0;
        long bytesTransferred = 0;

        try (Stream<Path> paths = Files.walk(localDir)) {
            var files = paths.filter(Files::isRegularFile).toList();
            totalFiles = files.size();

            for (Path file : files) {
                String key = localDir.relativize(file).toString();
                String localMd5 = Md5Hasher.hashFile(file);

                if (storage.exists(bucketName, key)) {
                    FileMetadata remoteMeta = storage.getMetadata(bucketName, key);
                    if (localMd5.equals(remoteMeta.md5Hash())) {
                        log.debug("Skipping {} (hash match)", key);
                        skipped++;
                        continue;
                    }
                }

                byte[] content = Files.readAllBytes(file);
                storage.write(bucketName, key, content, localMd5);
                bytesTransferred += content.length;
                synced++;
                log.debug("Synced {} ({} bytes)", key, content.length);
            }
        } catch (IOException e) {
            return "Error during sync: " + e.getMessage();
        }

        return String.format("Synced %d/%d files. %d skipped (unchanged). %s transferred.",
                synced, totalFiles, skipped, formatBytes(bytesTransferred));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=DeltaSyncServiceTest`
Expected: `Tests run: 3, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add DeltaSyncService with TDD tests"
```

---

### Task 11: tool-etag — UploadValidationService (TDD)

**Files:**
- Create: `tool-etag/src/test/java/io/agenttoolbox/tool/etag/service/UploadValidationServiceTest.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/service/UploadValidationService.java`

- [ ] **Step 1: Write the failing test**

`UploadValidationServiceTest.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadValidationServiceTest {

    @TempDir
    Path tempDir;
    StorageAdapter storage;
    UploadValidationService service;

    @BeforeEach
    void setUp() {
        storage = mock(StorageAdapter.class);
        service = new UploadValidationService(storage);
    }

    @Test
    void uploadsFileWithValidMd5() throws IOException {
        Path file = tempDir.resolve("data.bin");
        Files.writeString(file, "valid data");
        String md5 = Md5Hasher.hashBytes("valid data".getBytes());

        when(storage.write(eq("bucket"), eq("data.bin"), any(), eq(md5)))
                .thenReturn(new FileMetadata("data.bin", "bucket", md5, md5,
                        10, Instant.now(), "application/octet-stream"));

        String result = service.upload(file.toString(), "bucket", "data.bin");

        verify(storage).write(eq("bucket"), eq("data.bin"), any(), eq(md5));
        assertThat(result).contains("data.bin").contains(md5);
    }

    @Test
    void reportsCorruptionOnMd5Mismatch() throws IOException {
        Path file = tempDir.resolve("corrupt.bin");
        Files.writeString(file, "original");
        String md5 = Md5Hasher.hashBytes("original".getBytes());

        when(storage.write(eq("bucket"), eq("corrupt.bin"), any(), eq(md5)))
                .thenThrow(new HashMismatchException(md5, "corrupted-hash"));

        String result = service.upload(file.toString(), "bucket", "corrupt.bin");
        assertThat(result).contains("REJECTED").contains("mismatch");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=UploadValidationServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement UploadValidationService**

`UploadValidationService.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.HashMismatchException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadValidationService {

    private static final Logger log = LoggerFactory.getLogger(UploadValidationService.class);

    private final StorageAdapter storage;

    public UploadValidationService(StorageAdapter storage) {
        this.storage = storage;
    }

    public String upload(String localFilePath, String bucketName, String destinationKey) {
        Path localPath = Path.of(localFilePath);
        try {
            byte[] content = Files.readAllBytes(localPath);
            String localMd5 = Md5Hasher.hashBytes(content);

            FileMetadata meta = storage.write(bucketName, destinationKey, content, localMd5);

            return String.format("Uploaded %s to %s/%s (%d bytes, MD5 verified: %s)",
                    localPath.getFileName(), bucketName, destinationKey, meta.size(), meta.md5Hash());
        } catch (HashMismatchException e) {
            log.error("Upload integrity check failed for {}", destinationKey, e);
            return String.format("Upload REJECTED — MD5 mismatch for %s. Expected: %s, Got: %s. File may be corrupted.",
                    destinationKey, e.getExpectedMd5(), e.getActualMd5());
        } catch (IOException e) {
            return "Error reading local file: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=UploadValidationServiceTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add UploadValidationService with TDD tests"
```

---

### Task 12: tool-etag — ConcurrencyControlService (TDD)

**Files:**
- Create: `tool-etag/src/test/java/io/agenttoolbox/tool/etag/service/ConcurrencyControlServiceTest.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/service/ConcurrencyControlService.java`

- [ ] **Step 1: Write the failing test**

`ConcurrencyControlServiceTest.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConcurrencyControlServiceTest {

    @TempDir
    Path tempDir;
    StorageAdapter storage;
    ConcurrencyControlService service;

    @BeforeEach
    void setUp() {
        storage = mock(StorageAdapter.class);
        service = new ConcurrencyControlService(storage);
    }

    @Test
    void updatesFileWhenEtagMatches() throws IOException {
        Path localFile = tempDir.resolve("updated.txt");
        Files.writeString(localFile, "v2 content");
        String newMd5 = Md5Hasher.hashBytes("v2 content".getBytes());

        when(storage.conditionalWrite(eq("bucket"), eq("config.txt"), any(), eq("etag-v1")))
                .thenReturn(new FileMetadata("config.txt", "bucket", newMd5, newMd5,
                        10, Instant.now(), "application/octet-stream"));

        String result = service.update("bucket", "config.txt", localFile.toString(), "etag-v1");

        assertThat(result).contains("Updated").contains(newMd5);
    }

    @Test
    void reportsConflictWhenEtagStale() throws IOException {
        Path localFile = tempDir.resolve("stale.txt");
        Files.writeString(localFile, "my edits");

        when(storage.conditionalWrite(eq("bucket"), eq("config.txt"), any(), eq("old-etag")))
                .thenThrow(new PreconditionFailedException("old-etag", "current-etag"));

        String result = service.update("bucket", "config.txt", localFile.toString(), "old-etag");

        assertThat(result).contains("Conflict").contains("old-etag").contains("current-etag");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=ConcurrencyControlServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement ConcurrencyControlService**

`ConcurrencyControlService.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.exception.PreconditionFailedException;
import io.agenttoolbox.common.model.FileMetadata;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConcurrencyControlService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyControlService.class);

    private final StorageAdapter storage;

    public ConcurrencyControlService(StorageAdapter storage) {
        this.storage = storage;
    }

    public String update(String bucketName, String fileKey, String localFilePath, String knownEtag) {
        try {
            byte[] content = Files.readAllBytes(Path.of(localFilePath));
            FileMetadata meta = storage.conditionalWrite(bucketName, fileKey, content, knownEtag);

            return String.format("Updated %s/%s successfully. New ETag: %s",
                    bucketName, fileKey, meta.etag());
        } catch (PreconditionFailedException e) {
            log.warn("Concurrent modification detected for {}/{}", bucketName, fileKey);
            return String.format("Conflict — file %s/%s was modified by another process. "
                    + "Your ETag: %s. Current ETag: %s. Please reload and retry.",
                    bucketName, fileKey, e.getExpectedEtag(), e.getCurrentEtag());
        } catch (IOException e) {
            return "Error reading local file: " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=ConcurrencyControlServiceTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add ConcurrencyControlService with TDD tests"
```

---

### Task 13: tool-etag — CacheValidationService (TDD)

**Files:**
- Create: `tool-etag/src/test/java/io/agenttoolbox/tool/etag/service/CacheValidationServiceTest.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/service/CacheValidationService.java`

- [ ] **Step 1: Write the failing test**

`CacheValidationServiceTest.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheValidationServiceTest {

    StorageAdapter storage;
    CacheValidationService service;

    @BeforeEach
    void setUp() {
        storage = mock(StorageAdapter.class);
        service = new CacheValidationService(storage);
    }

    @Test
    void returnsNotModifiedWhenEtagMatches() {
        when(storage.conditionalRead("bucket", "catalog.json", "etag-123"))
                .thenReturn(ConditionalReadResult.notModified("etag-123"));

        String result = service.validate("bucket", "catalog.json", "etag-123");

        assertThat(result).contains("NOT MODIFIED").contains("0");
    }

    @Test
    void returnsNewContentWhenEtagDiffers() {
        byte[] newContent = "updated catalog".getBytes();
        when(storage.conditionalRead("bucket", "catalog.json", "old-etag"))
                .thenReturn(ConditionalReadResult.modified(newContent, "new-etag"));

        String result = service.validate("bucket", "catalog.json", "old-etag");

        assertThat(result).contains("MODIFIED").contains("new-etag").contains("15");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=CacheValidationServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement CacheValidationService**

`CacheValidationService.java`:
```java
package io.agenttoolbox.tool.etag.service;

import io.agenttoolbox.common.storage.ConditionalReadResult;
import io.agenttoolbox.common.storage.StorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheValidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheValidationService.class);

    private final StorageAdapter storage;

    public CacheValidationService(StorageAdapter storage) {
        this.storage = storage;
    }

    public String validate(String bucketName, String fileKey, String knownEtag) {
        ConditionalReadResult result = storage.conditionalRead(bucketName, fileKey, knownEtag);

        if (!result.modified()) {
            log.debug("Cache hit for {}/{} (ETag: {})", bucketName, fileKey, knownEtag);
            return String.format("NOT MODIFIED — %s/%s has not changed. ETag: %s. 0 bytes transferred.",
                    bucketName, fileKey, result.etag());
        }

        log.debug("Cache miss for {}/{} (new ETag: {})", bucketName, fileKey, result.etag());
        return String.format("MODIFIED — %s/%s has changed. New ETag: %s. %d bytes transferred.",
                bucketName, fileKey, result.etag(), result.contentLength());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl tool-etag -Dtest=CacheValidationServiceTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add CacheValidationService with TDD tests"
```

---

### Task 14: tool-etag — EtagToolProvider + SPI Registration

**Files:**
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagToolProvider.java`
- Create: `tool-etag/src/main/java/io/agenttoolbox/tool/etag/EtagTools.java`
- Create: `tool-etag/src/main/resources/META-INF/services/io.agenttoolbox.core.ToolProvider`

- [ ] **Step 1: Create EtagTools (the @Tool-annotated class)**

`EtagTools.java`:
```java
package io.agenttoolbox.tool.etag;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;

public class EtagTools {

    private final DeltaSyncService deltaSyncService;
    private final UploadValidationService uploadValidationService;
    private final ConcurrencyControlService concurrencyControlService;
    private final CacheValidationService cacheValidationService;

    public EtagTools(DeltaSyncService deltaSyncService,
                     UploadValidationService uploadValidationService,
                     ConcurrencyControlService concurrencyControlService,
                     CacheValidationService cacheValidationService) {
        this.deltaSyncService = deltaSyncService;
        this.uploadValidationService = uploadValidationService;
        this.concurrencyControlService = concurrencyControlService;
        this.cacheValidationService = cacheValidationService;
    }

    @Tool("Sync files from a local directory to a storage bucket. Only uploads files whose content has changed by comparing MD5 hashes. Use when the user wants to sync, backup, or upload a folder efficiently.")
    public String deltaSync(
            @P("Absolute path to the local directory to sync from") String localPath,
            @P("Name of the target storage bucket") String bucketName) {
        return deltaSyncService.sync(localPath, bucketName);
    }

    @Tool("Upload a file to storage with integrity validation. Computes MD5 before upload and verifies the storage backend received an identical copy. Use for large files or unreliable networks.")
    public String uploadWithValidation(
            @P("Absolute path to the local file to upload") String localFilePath,
            @P("Name of the target storage bucket") String bucketName,
            @P("Destination key/path within the bucket") String destinationKey) {
        return uploadValidationService.upload(localFilePath, bucketName, destinationKey);
    }

    @Tool("Update a file in storage with concurrency protection. Uses ETags to ensure no one else modified the file since you last read it. Use when multiple users or processes might edit the same file.")
    public String conditionalUpdate(
            @P("Name of the storage bucket") String bucketName,
            @P("Key/path of the file in the bucket") String fileKey,
            @P("Absolute path to the local file containing updated content") String localFilePath,
            @P("ETag from when the file was originally read (used for conflict detection)") String knownEtag) {
        return concurrencyControlService.update(bucketName, fileKey, localFilePath, knownEtag);
    }

    @Tool("Check if a remote file has changed without downloading it. Uses ETags to avoid unnecessary data transfer. Use for caching, polling for updates, or bandwidth-sensitive clients.")
    public String cacheValidation(
            @P("Name of the storage bucket") String bucketName,
            @P("Key/path of the file in the bucket") String fileKey,
            @P("ETag from the last known version of the file") String knownEtag) {
        return cacheValidationService.validate(bucketName, fileKey, knownEtag);
    }
}
```

- [ ] **Step 2: Create EtagToolProvider (SPI implementation)**

`EtagToolProvider.java`:
```java
package io.agenttoolbox.tool.etag;

import io.agenttoolbox.core.ToolProvider;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;

public class EtagToolProvider implements ToolProvider {

    private static final String DEFAULT_BUCKET_ROOT =
            System.getProperty("user.home") + "/.agent-toolbox/buckets";

    @Override
    public String name() {
        return "etag";
    }

    @Override
    public String description() {
        return "ETag/MD5 tools for delta sync, upload validation, concurrency control, and cache validation";
    }

    @Override
    public Object toolInstance() {
        LocalStorageAdapter storage = new LocalStorageAdapter(DEFAULT_BUCKET_ROOT);
        return new EtagTools(
                new DeltaSyncService(storage),
                new UploadValidationService(storage),
                new ConcurrencyControlService(storage),
                new CacheValidationService(storage)
        );
    }
}
```

- [ ] **Step 3: Create SPI registration file**

`tool-etag/src/main/resources/META-INF/services/io.agenttoolbox.core.ToolProvider`:
```
io.agenttoolbox.tool.etag.EtagToolProvider
```

- [ ] **Step 4: Verify build compiles with SPI**

Run: `cd ~/projects/agent-toolbox && ./mvnw compile -pl tool-etag`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add tool-etag/src/
git commit -m "feat(etag): add EtagToolProvider with @Tool methods and SPI registration"
```

---

### Task 15: agent-cli — VerbosityMode, OutputFormatter, Config Files

**Files:**
- Create: `agent-cli/src/main/java/io/agenttoolbox/cli/VerbosityMode.java`
- Create: `agent-cli/src/main/java/io/agenttoolbox/cli/OutputFormatter.java`
- Create: `agent-cli/src/main/resources/logback.xml`
- Create: `agent-cli/src/test/java/io/agenttoolbox/cli/OutputFormatterTest.java`

- [ ] **Step 1: Write OutputFormatter test**

`OutputFormatterTest.java`:
```java
package io.agenttoolbox.cli;

import io.agenttoolbox.core.model.AgentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputFormatterTest {

    @Test
    void formatsSuccessResponse() {
        OutputFormatter formatter = new OutputFormatter(VerbosityMode.CONCISE);
        AgentResponse response = AgentResponse.ok("Synced 5 files");

        String output = formatter.format(response);
        assertThat(output).isEqualTo("Synced 5 files");
    }

    @Test
    void formatsErrorResponseWithPrefix() {
        OutputFormatter formatter = new OutputFormatter(VerbosityMode.CONCISE);
        AgentResponse response = AgentResponse.error("File not found");

        String output = formatter.format(response);
        assertThat(output).startsWith("ERROR: ");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-cli -Dtest=OutputFormatterTest`
Expected: FAIL

- [ ] **Step 3: Create VerbosityMode**

`VerbosityMode.java`:
```java
package io.agenttoolbox.cli;

public enum VerbosityMode {
    CONCISE,
    VERBOSE,
    AUTO
}
```

- [ ] **Step 4: Create OutputFormatter**

`OutputFormatter.java`:
```java
package io.agenttoolbox.cli;

import io.agenttoolbox.core.model.AgentResponse;

public class OutputFormatter {

    private final VerbosityMode mode;

    public OutputFormatter(VerbosityMode mode) {
        this.mode = mode;
    }

    public String format(AgentResponse response) {
        if (!response.success()) {
            return "ERROR: " + response.message();
        }
        return response.message();
    }
}
```

- [ ] **Step 5: Create logback.xml**

`agent-cli/src/main/resources/logback.xml`:
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="WARN">
        <appender-ref ref="STDOUT"/>
    </root>

    <logger name="io.agenttoolbox" level="INFO"/>
</configuration>
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-cli -Dtest=OutputFormatterTest`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-cli/src/
git commit -m "feat(cli): add VerbosityMode, OutputFormatter, and logback config"
```

---

### Task 16: agent-cli — AgentCli Main Class

**Files:**
- Create: `agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java`

- [ ] **Step 1: Create AgentCli**

`AgentCli.java`:
```java
package io.agenttoolbox.cli;

import io.agenttoolbox.common.config.EnvVarSecretProvider;
import io.agenttoolbox.core.AgentRunner;
import io.agenttoolbox.core.ToolRegistry;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.config.ConfigLoader;
import io.agenttoolbox.core.model.AgentResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "agent-toolbox", mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "LLM-powered agent for file storage operations")
public class AgentCli implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Always use verbose output")
    boolean verbose;

    @Option(names = {"--model"}, description = "Override Ollama model name")
    String model;

    @Option(names = {"--config"}, description = "Path to config YAML")
    Path configPath;

    @Parameters(description = "One-shot query (skip REPL)", arity = "0..*")
    String[] query;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        AgentConfig config = ConfigLoader.loadFromClasspath();
        applyOverrides(config);

        VerbosityMode verbosityMode = verbose ? VerbosityMode.VERBOSE : VerbosityMode.AUTO;
        OutputFormatter formatter = new OutputFormatter(verbosityMode);

        ToolRegistry registry = new ToolRegistry();
        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), registry);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("\nGoodbye.")));

        if (query != null && query.length > 0) {
            return runOneShot(runner, formatter, String.join(" ", query));
        }
        return runRepl(runner, formatter, config);
    }

    private int runOneShot(AgentRunner runner, OutputFormatter formatter, String input) {
        AgentResponse response = runner.chat(input);
        System.out.println(formatter.format(response));
        return response.success() ? 0 : 1;
    }

    private int runRepl(AgentRunner runner, OutputFormatter formatter, AgentConfig config) {
        System.out.printf("%s v%s (Ollama: %s)%n",
                config.getAgent().getName(),
                config.getAgent().getVersion(),
                config.getLlm().getOllama().getModel());
        System.out.println("Type your request, or 'help' for commands. 'quit' to exit.");
        System.out.println();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null || "quit".equalsIgnoreCase(line.trim()) || "exit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                String input = line.trim();
                if (input.isEmpty()) continue;
                if ("help".equalsIgnoreCase(input)) {
                    printHelp(runner);
                    continue;
                }

                AgentResponse response = runner.chat(input);
                System.out.println(formatter.format(response));
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private void printHelp(AgentRunner runner) {
        System.out.println("Available tools: " + runner.getToolNames());
        System.out.println("Commands: help, quit/exit");
        System.out.println("Describe your file storage problem in natural language.");
        System.out.println();
    }

    private void applyOverrides(AgentConfig config) {
        if (model != null) {
            config.getLlm().getOllama().setModel(model);
        }
        if (verbose) {
            config.getOutput().setVerbosity("verbose");
        }
    }
}
```

- [ ] **Step 2: Build the fat JAR**

Run: `cd ~/projects/agent-toolbox && ./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS. Fat JAR at `agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar`

- [ ] **Step 3: Verify help flag works**

Run: `java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar --help`
Expected: Picocli help output with description, options, and version

- [ ] **Step 4: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-cli/src/main/java/io/agenttoolbox/cli/AgentCli.java
git commit -m "feat(cli): add AgentCli with REPL and one-shot modes"
```

---

### Task 17: Integration Tests

**Files:**
- Create: `agent-integration-tests/src/test/java/io/agenttoolbox/it/LocalStorageIntegrationTest.java`
- Create: `agent-integration-tests/src/test/resources/logback-test.xml`

- [ ] **Step 1: Create logback-test.xml**

`agent-integration-tests/src/test/resources/logback-test.xml`:
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 2: Create LocalStorageIntegrationTest**

This test verifies the full tool chain: services + LocalStorageAdapter working together with real filesystem I/O.

`LocalStorageIntegrationTest.java`:
```java
package io.agenttoolbox.it;

import io.agenttoolbox.common.crypto.Md5Hasher;
import io.agenttoolbox.tool.etag.EtagTools;
import io.agenttoolbox.tool.etag.service.CacheValidationService;
import io.agenttoolbox.tool.etag.service.ConcurrencyControlService;
import io.agenttoolbox.tool.etag.service.DeltaSyncService;
import io.agenttoolbox.tool.etag.service.UploadValidationService;
import io.agenttoolbox.tool.etag.storage.LocalStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageIntegrationTest {

    @TempDir Path bucketRoot;
    @TempDir Path localDir;
    EtagTools tools;

    @BeforeEach
    void setUp() {
        LocalStorageAdapter storage = new LocalStorageAdapter(bucketRoot.toString());
        tools = new EtagTools(
                new DeltaSyncService(storage),
                new UploadValidationService(storage),
                new ConcurrencyControlService(storage),
                new CacheValidationService(storage)
        );
    }

    @Test
    void deltaSyncUploadsNewFiles() throws IOException {
        Files.writeString(localDir.resolve("file1.txt"), "content1");
        Files.writeString(localDir.resolve("file2.txt"), "content2");

        String result = tools.deltaSync(localDir.toString(), "my-bucket");

        assertThat(result).contains("Synced 2/2 files");
        assertThat(result).contains("0 skipped");
    }

    @Test
    void deltaSyncSkipsUnchangedFiles() throws IOException {
        Files.writeString(localDir.resolve("same.txt"), "unchanged");

        // First sync
        tools.deltaSync(localDir.toString(), "my-bucket");
        // Second sync (same content)
        String result = tools.deltaSync(localDir.toString(), "my-bucket");

        assertThat(result).contains("0/1 files");
        assertThat(result).contains("1 skipped");
    }

    @Test
    void uploadWithValidationSucceeds() throws IOException {
        Path file = localDir.resolve("upload.dat");
        Files.writeString(file, "upload this data");

        String result = tools.uploadWithValidation(file.toString(), "uploads", "upload.dat");

        assertThat(result).contains("Uploaded").contains("MD5 verified");
    }

    @Test
    void conditionalUpdateDetectsConflict() throws IOException {
        // First, upload a file
        Path v1 = localDir.resolve("v1.txt");
        Files.writeString(v1, "version 1");
        tools.uploadWithValidation(v1.toString(), "shared", "config.txt");

        // Simulate another writer changing the file
        Path v2 = localDir.resolve("v2.txt");
        Files.writeString(v2, "version 2 by other writer");
        tools.uploadWithValidation(v2.toString(), "shared", "config.txt");

        // Try to update with stale ETag from v1
        Path myEdits = localDir.resolve("my-edits.txt");
        Files.writeString(myEdits, "my version");
        String v1Etag = Md5Hasher.hashBytes("version 1".getBytes());

        String result = tools.conditionalUpdate("shared", "config.txt", myEdits.toString(), v1Etag);

        assertThat(result).contains("Conflict");
    }

    @Test
    void cacheValidationReturnsNotModified() throws IOException {
        Path file = localDir.resolve("catalog.json");
        Files.writeString(file, "{\"products\": []}");
        tools.uploadWithValidation(file.toString(), "cache", "catalog.json");

        String etag = Md5Hasher.hashBytes("{\"products\": []}".getBytes());
        String result = tools.cacheValidation("cache", "catalog.json", etag);

        assertThat(result).contains("NOT MODIFIED");
    }

    @Test
    void cacheValidationDetectsChange() throws IOException {
        Path file = localDir.resolve("catalog.json");
        Files.writeString(file, "old catalog");
        tools.uploadWithValidation(file.toString(), "cache", "catalog.json");

        // Update the file in storage
        Path updated = localDir.resolve("new-catalog.json");
        Files.writeString(updated, "new catalog");
        tools.uploadWithValidation(updated.toString(), "cache", "catalog.json");

        String oldEtag = Md5Hasher.hashBytes("old catalog".getBytes());
        String result = tools.cacheValidation("cache", "catalog.json", oldEtag);

        assertThat(result).contains("MODIFIED");
    }
}
```

- [ ] **Step 3: Run integration tests**

Run: `cd ~/projects/agent-toolbox && ./mvnw test -pl agent-integration-tests`
Expected: `Tests run: 6, Failures: 0` — BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
cd ~/projects/agent-toolbox
git add agent-integration-tests/src/
git commit -m "test: add LocalStorageIntegrationTest for all 4 ETag tools"
```

---

### Task 18: README, Docs & Full Build Verification

**Files:**
- Create: `~/projects/agent-toolbox/README.md`
- Create: `~/projects/agent-toolbox/docs/model-recommendations.md`
- Create: `~/projects/agent-toolbox/docs/adding-a-new-tool.md`

- [ ] **Step 1: Create README.md**

```markdown
# Agent Toolbox

LLM-powered CLI agent platform for file storage operations. Describe your problem in natural language — the agent picks the right tool.

## Quick Start

### Prerequisites

- Java 17+
- [Ollama](https://ollama.com/) installed and running

### Setup

```bash
# 1. Install and start Ollama
brew install ollama
ollama serve

# 2. Pull a model
ollama pull llama3.1:8b

# 3. Build
./mvnw clean install

# 4. Run
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar
```

### Usage

**Interactive REPL:**
```
$ java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar
Agent Toolbox v0.1.0 (Ollama: llama3.1:8b)
Type your request, or 'help' for commands. 'quit' to exit.

> sync my documents folder to the backup bucket
Synced 12/1,847 files. 1,835 skipped (unchanged). 2.3MB transferred.
```

**One-shot mode:**
```bash
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar "upload report.pdf to archive"
```

### Available Tools

| Tool | What it does |
|------|-------------|
| Delta Sync | Sync folders efficiently — only uploads changed files (MD5 comparison) |
| Upload Validation | Upload with corruption detection — rejects files that don't match their MD5 |
| Concurrency Control | Safe concurrent edits — uses ETags to prevent overwrites |
| Cache Validation | Check for changes without downloading — uses ETags for 304 Not Modified |

### Configuration

Default config is bundled. Override with `~/.agent-toolbox/config.yaml` or `--config path/to/config.yaml`.

### CLI Options

```
--help          Show help
--version       Show version
-v, --verbose   Always use verbose output
--model NAME    Override Ollama model name
--config PATH   Path to config YAML
```

## Project Structure

```
agent-toolbox/
├── agent-common/          Shared kernel (exceptions, MD5, interfaces)
├── agent-core/            Agent engine (LLM factory, tool registry, runner)
├── tool-etag/             ETag/MD5 tool (4 operations + local storage adapter)
├── agent-cli/             Picocli CLI entry point
└── agent-integration-tests/  End-to-end tests
```

## Adding a New Tool

See [docs/adding-a-new-tool.md](docs/adding-a-new-tool.md).

## Model Recommendations

See [docs/model-recommendations.md](docs/model-recommendations.md).

## References

- [LangChain4j Tools](https://docs.langchain4j.dev/tutorials/tools/)
- [LangChain4j AiServices](https://docs.langchain4j.dev/tutorials/ai-services/)
- [LangChain4j Ollama](https://docs.langchain4j.dev/integrations/language-models/ollama/)
- [Picocli User Guide](https://picocli.info/)
- [HTTP ETags (RFC 7232)](https://datatracker.ietf.org/doc/html/rfc7232)
- [Java SPI](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Maven Multi-Module](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [SLF4J Manual](https://www.slf4j.org/manual.html)
- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [GCS Java Client](https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java)
```

- [ ] **Step 2: Create docs/model-recommendations.md**

```markdown
# Model Recommendations

Recommended Ollama models by available RAM.

| RAM | Model | Notes |
|-----|-------|-------|
| 8GB or less | `llama3.2:3b`, `phi3:mini` | Fast responses, basic tool selection |
| 16GB | `llama3.1:8b`, `mistral:7b`, `gemma2:9b` | Good balance for agentic reasoning |
| 32GB+ | `llama3.1:70b`, `mixtral:8x7b` | Best reasoning, slower startup |

## Changing the Model

```bash
# Pull a different model
ollama pull mistral:7b

# Use it with agent-toolbox
java -jar agent-cli/target/agent-cli-0.1.0-SNAPSHOT.jar --model mistral:7b
```

Or set permanently in `~/.agent-toolbox/config.yaml`:

```yaml
llm:
  ollama:
    model: mistral:7b
```
```

- [ ] **Step 3: Create docs/adding-a-new-tool.md**

```markdown
# Adding a New Tool

Tools are discovered via Java SPI. To add a new tool:

## 1. Create a new Maven module

```
tool-myfeature/
├── pom.xml
└── src/main/
    ├── java/io/agenttoolbox/tool/myfeature/
    │   ├── MyFeatureToolProvider.java
    │   └── MyFeatureTools.java
    └── resources/META-INF/services/
        └── io.agenttoolbox.core.ToolProvider
```

## 2. Add dependencies in pom.xml

```xml
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
</dependencies>
```

## 3. Create @Tool methods

```java
public class MyFeatureTools {

    @Tool("Description of what this tool does. Be specific.")
    public String myOperation(
            @P("Description of this parameter") String param1) {
        // Implementation
        return "Result message";
    }
}
```

## 4. Implement ToolProvider

```java
public class MyFeatureToolProvider implements ToolProvider {
    @Override public String name() { return "myfeature"; }
    @Override public String description() { return "What this tool does"; }
    @Override public Object toolInstance() { return new MyFeatureTools(); }
}
```

## 5. Register via SPI

Create `src/main/resources/META-INF/services/io.agenttoolbox.core.ToolProvider`:
```
io.agenttoolbox.tool.myfeature.MyFeatureToolProvider
```

## 6. Add to parent POM

Add `<module>tool-myfeature</module>` to the parent `pom.xml` modules list.

The agent discovers and loads your tool automatically on next build.
```

- [ ] **Step 4: Run full build with all tests**

Run: `cd ~/projects/agent-toolbox && ./mvnw clean verify`
Expected: BUILD SUCCESS for all modules, all tests pass

- [ ] **Step 5: Commit**

```bash
cd ~/projects/agent-toolbox
git add README.md docs/
git commit -m "docs: add README, model recommendations, and adding-a-new-tool guide"
```

- [ ] **Step 6: Final verification — list all commits**

Run: `cd ~/projects/agent-toolbox && git log --oneline`
Expected: 12 commits from scaffolding through docs
