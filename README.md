# Fern JUnit Gradle Plugin

A Gradle plugin and CLI for publishing JUnit test results to a Fern test reporting instance.

![example workflow](https://github.com/guidewire-oss/fern-junit-gradle-plugin/actions/workflows/gradle.yml/badge.svg?event=push)
![plugin](https://img.shields.io/gradle-plugin-portal/v/io.github.guidewire-oss.fern-publisher?label=Gradle%20Plugins%20Portal&color=blue)

## Overview

This plugin simplifies the process of collecting JUnit XML test reports and publishing them to a Fern test reporting
service. It parses JUnit XML reports, converts them to Fern's data model, and sends them to your Fern instance through
its API.

To learn more about Fern, check out [its repository](https://github.com/guidewire-oss/fern-reporter)

## Features

- Parse JUnit XML test reports in various formats
- Group test suites and test cases into a cohesive test run
- Track test execution time and status
- Support for test tags
- Configurable verbosity for debugging

## Installation

Add the plugin to your `build.gradle.kts` file:

```kotlin
plugins {
  id("io.github.guidewire-oss.fern-publisher") version "1.0.0"
}
```

Or in `build.gradle`:

```groovy
plugins {
  id 'io.github.guidewire-oss.fern-publisher' version '1.0.0'
}
```

## Configuration

### Register your application with fern

Newer versions of the Fern Reporter server require you to pre-register your application to receive a UUID (your project id)

1. To register your project send a `POST` request to `<yourFernUrl>/api/project` with JSON body of

```json
{
  "name": "my-project",
  "team_name": "Dev Team",
  "comment": "Initial project registration"
}
```

Here is an example curl command for ease of use:

```shell
curl -X POST "https://yourFernUrl.com/api/project" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-project",
    "team_name": "Dev Team",
    "comment": "Initial project registration"
  }'
```

2. You will receive a successful response that looks like:

```json
{
  "uuid": "59e06cf8-f390-5093-af2e-3685be593a25",
  "name": "my-project",
  "team_name": "Dev Team",
  "comment": "Initial project registration",
  "created_at": "2025-06-06T15:00:28.403029Z",
  "updated_at": "2025-06-06T15:00:28.403682Z"
}
```

You will need to take note of the returned `ProjectID` UUID for use in configuring the plugin, as described below

### Plugin Setup

Configure the plugin in your build script:

```kotlin
fernPublisher {
  fernUrl.set("https://your-fern-instance.example.com")
  projectId.set("6ba7b812-9dad-11d1-80b4-00c04fd430c8")
  projectName.set("my-project")
  reportPaths.set(listOf("build/test-results/test/*.xml"))
  fernTags.set(listOf("automated", "integration"))
  verbose.set(false)
  failOnError.set(false)
}
```

### Parameters

| Property    | Description                                                                                                                                                               | Required | Default    |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|------------|
| fernUrl     | URL of your Fern instance                                                                                                                                                 | Yes      | -          |
| projectId   | The id of your project supplied by registering a project in Fern.                                                                                                         | No*      | ""         |
| projectName | Name of your project in Fern                                                                                                                                              | No*      | ""         |
|             | * **NOTE:** you must include a projectName or a projectID (or both). Newer versions of the Fern Reporting server require you to pre-register your project to obtain an ID |          |            |
| reportPaths | Glob patterns for locating JUnit XML reports                                                                                                                              | Yes      | -          |
| fernTags    | Tags to apply to all tests                                                                                                                                                | No       | empty list |
| verbose     | Enable verbose logging                                                                                                                                                    | No       | false      |
| failOnError | When true, will fail when error is thrown. Errors will always log.                                                                                                        | No       | false      |

## Usage

Run the task to publish test results:

```
./gradlew publishToFern
```

You can also make it run automatically after your tests:

```kotlin
tasks.named("test") {
  finalizedBy("publishToFern")
}
```

## Examples

### Basic Configuration

```kotlin
fernPublisher {
  fernUrl.set("https://fern.example.com")
  projectName.set("backend-api")
  reportPaths.set(listOf("build/test-results/test/*.xml"))
}
```

### Multiple Report Sources

```kotlin
fernPublisher {
  fernUrl.set("https://fern.example.com")
  projectName.set("full-stack")
  reportPaths.set(
    listOf(
      "${project.buildDir}/test-results/test/*.xml",
      "frontend/build/test-results/test/*.xml"
    )
  )
  fernTags.set(listOf("ci", "nightly"))
}
```

## Compatibility

- Gradle 6.1 or later
- Kotlin 1.4 or later
- JUnit 4 and JUnit 5 XML report formats

## Command-Line Interface (CLI)

This project also provides a CLI tool, `fern-junit-client`, for collecting and publishing JUnit XML test reports to a Fern Reporter instance. It is designed to work the same way as the Gradle plugin, but can be used independently of Gradle and Java.

Builds of the CLI binary are available for Linux, macOS, and Windows on the [Releases page](https://github.com/guidewire-oss/fern-junit-gradle-plugin/releases).

### Usage

```sh
fern-junit-client send \
  --fern-url <FERN_URL> \
  --project-name <PROJECT_NAME> \
  --project-id <PROJECT_ID> \
  --file-pattern <REPORT_PATTERN> \
  [--tags <TAGS>] \
  [--verbose]
```

#### Options

- `--fern-url` (`-u`): Base URL of the Fern Reporter instance to send test reports to (required)
- `--project-name` (`-n`): Name of the project to associate test reports with (required)
- `--project-id` (`-i`): Project ID to associate test reports with (required)
- `--file-pattern` (`-f`): Glob pattern(s) for JUnit XML reports (can be repeated, required)
- `--tags` (`-t`): (Optional) Comma-separated tags to include on the test run
- `--verbose` (`-v`): (Optional) Enable verbose output for debugging

### Example

```sh
fern-junit-client send \
  --fern-url https://fern.example.com \
  --project-name my-service \
  --project-id 1234 \
  --file-pattern "build/test-results/**/*.xml" \
  --tags "ci,nightly" \
  --verbose
```

## Building from Source

```bash
git clone https://github.com/your-org/fern-junit-publisher.git
cd fern-junit-publisher
./gradlew build
```

## Development

You can build and publish this plugin for use locally with the command

```bash
./gradlew publishToMavenLocal
```

You should see the plugin in your local `.m2` repository.

From there you can set up a test project to use the plugin. Make sure to edit your `settings.gradle` file to use plugins
found in your local m2 repository.

```gradle
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
```
### Building the CLI
Native CLI binaries are built using GraalVM's native-image tool. This allows the CLI to run without requiring a Java runtime, making it lightweight and portable.

For building the CLI, you will need to have GraalVM installed and set the environment variable `GRAALVM_HOME` as your GraalVM install location.

Once that is set up, run the following command in the project root:

```bash
./gradlew nativeImage -Pversion="1.0.0-SNAPSHOT" 
```