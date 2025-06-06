# Fern JUnit Gradle Plugin

A Gradle plugin for publishing JUnit test results to a Fern test reporting instance.

![example workflow](https://github.com/guidewire-oss/fern-junit-gradle-plugin/actions/workflows/gradle.yml/badge.svg?event=push)

[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.guidewire-oss.fern-publisher)

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

1. To register your project send a `POST` request to `<yourFernUrl>/project` with JSON body of 
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