# Spring REST Docs Generator

![Build](https://github.com/sweet-mustard/spring-rest-docs-generator/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
The Spring REST Docs Generator IntelliJ-based plugin allows generating Spring REST Documentation tests. 

After installing the plugin, you can generate a Spring REST Documentation test via the Tools > Generate Spring REST Docs menu item.
Based on the selected endpoint, the plugin will generate a skeleton for the corresponding
documentation test.

The development of this plugin is proudly sponsored by [Sweet Mustard](https://www.sweetmustard.be/).
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "spring-rest-docs-generator"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/sweet-mustard/spring-rest-docs-generator/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Functionality

### Generated code

Given an endpoint in a rest controller, the plugin will generate a skeleton for the corresponding
documentation test. This skeleton includes:

- Test method with `@Test` annotation
- `mockMvc.perform()` containing
  - the HTTP-method
  - the URI, with empty places for the values of the path variables (if any)
  - one `.param(<query parameter>,)` for each query parameter (if any)
  - `.contentType()` and `.content()` if applicable. The `.content()` contains a skeleton of the
    JSON-body
- `.andExpect(status())` with the expected status
- `.andDo(document)` containing
  - Snippet identifier based on selected method
  - Documentation (without description) for all
    - Path variables (if any)
    - Query parameters (if any)
    - Request fields (if any)
    - Response fields (if any)

### Nesting

When creating documentation, the plugin will create a tree representing the structure of the request
and/or response object,
based on the fields and subfields present.
It will stop along a branch when it encounters one of the following types:

- A primitive type: `byte`, `short`, `int`, `long`, `boolean`, `char`, `float`, `double`
- A wrapper classes of a primitive type: `Byte`, `Short`, `Integer`, `Long`, `Boolean`, `Character`,
  `Float`, `Double`
- `java.lang.String`
- `java.math.BigDecimal`,
  `java.math.BigInteger`,
  `java.time.LocalDate`,
  `java.time.LocalDateTime`,
  `java.time.ZonedDateTime`,
  `java.time.Instant`,
  `java.time.Duration`,
  `java.util.UUID`
- `java.util.Map`
- An enum type
- The void type
- A type for which the project contains a custom JSON-converter;
  that is, a type `T` for which a class implementing `JsonSerializer<T>` exists.

For all other types, it will continue until a nesting depth of 10 is reached.

## Customization

### Annotations

A RestControllerDocumentationTest class is usually annotated with

- `@ExtendWith({RestDocumentationExtension.class})`,
- `@AutoConfigureRestDocs`, and
- `@WebMvcTest({rest-controller-name}.class)`.

It is possible to use a custom annotation instead of those three, and to add other annotations to
all generated RestControllerDocumentationTest classes and methods.

### MockMvc code

If the documentation tests need extra code inside `mockMvc.perform()` (e.g. due to authentication
integration), you can supply the necessary code to be automatically added.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
