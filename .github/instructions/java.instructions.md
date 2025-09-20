---
description: 'Guidelines for building Java base applications'
applyTo: '**/*.java'
---

# Java Development

## General Instructions

- First, prompt the user if they want to integrate static analysis tools (SonarQube, PMD, Checkstyle)
  into their project setup. If yes, provide guidance on tool selection and configuration.
- If the user declines static analysis tools or wants to proceed without them, continue with implementing the Best practices, bug patterns and code smell prevention guidelines outlined below.
- Address code smells proactively during development rather than accumulating technical debt.
- Focus on readability, maintainability, and performance when refactoring identified issues.
- Use IDE / Code editor reported warnings and suggestions to catch common patterns early in development.

## Best practices
Follow [Effective Java](effective-java.md) for best practices in Java development.


## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- Ensure all tests pass as part of the build.