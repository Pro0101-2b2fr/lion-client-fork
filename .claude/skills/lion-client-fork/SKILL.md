```markdown
# lion-client-fork Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches you the core development patterns and conventions used in the `lion-client-fork` Java codebase. You'll learn about file naming, import/export styles, commit message patterns, and how to write and run tests. This guide also provides suggested commands for common workflows to streamline your development process.

## Coding Conventions

### File Naming
- Use **camelCase** for file names.
  - Example: `userProfileManager.java`, `orderServiceImpl.java`

### Import Style
- Use **relative imports** for referencing classes within the project.
  - Example:
    ```java
    import com.myproject.services.user.UserService;
    ```

### Export Style
- Use **named exports** (i.e., explicitly declare public classes).
  - Example:
    ```java
    public class UserProfileManager {
        // class implementation
    }
    ```

### Commit Patterns
- Commit messages are **freeform** with no strict prefix requirement.
- Average commit message length: **62 characters**.
  - Example:  
    ```
    Fix bug in user authentication flow when token is expired
    ```

## Workflows

### Adding a New Feature
**Trigger:** When you need to implement a new feature.
**Command:** `/add-feature`

1. Create a new Java file using camelCase naming.
2. Implement the feature using relative imports for dependencies.
3. Export the main class using `public class`.
4. Write corresponding tests in a file matching `*.test.*`.
5. Commit your changes with a descriptive message.

### Fixing a Bug
**Trigger:** When you identify and fix a bug in the codebase.
**Command:** `/fix-bug`

1. Locate the relevant Java file(s).
2. Apply the bug fix using the established code style.
3. Update or add tests as needed in `*.test.*` files.
4. Commit with a clear message describing the fix.

### Writing and Running Tests
**Trigger:** When you need to verify code correctness.
**Command:** `/run-tests`

1. Write test cases in files matching the `*.test.*` pattern.
2. Use the project's preferred (undetected) test framework.
3. Run tests using the standard Java test runner or the project's test command.

## Testing Patterns

- Test files follow the `*.test.*` naming pattern.
  - Example: `userProfileManager.test.java`
- The specific test framework is unknown; follow existing patterns in the repository.
- Place tests alongside or near the code they test for clarity.

## Commands
| Command      | Purpose                                 |
|--------------|-----------------------------------------|
| /add-feature | Start the process to add a new feature  |
| /fix-bug     | Guide for fixing a bug                  |
| /run-tests   | Instructions for writing and running tests |
```