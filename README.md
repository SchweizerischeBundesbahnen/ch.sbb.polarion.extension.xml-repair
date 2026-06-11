[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=bugs)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=coverage)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.xml-repair)

# Polarion ALM extension to validate and repair XML structure of different Polarion objects, at the moment - WorkItems, Documents and Collections.

> [!IMPORTANT]
> Starting from version 1.0.0 only latest version of Polarion is supported.
> Right now it is Polarion 2606.

## Quick start

The latest version of the extension can be downloaded from the [releases page](../../releases/latest) and installed to Polarion instance without necessity to be compiled from the sources.
The extension should be copied to `<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.xml-repair/eclipse/plugins` and changes will take effect after Polarion restart.
> [!IMPORTANT]
> Don't forget to clear `<polarion_home>/data/workspace/.config` folder after extension installation/update to make it work properly.

## Documentation

- [Development Guide](./DEVELOPMENT.md) - Comprehensive guide for setting up development environment and contributing to this project
- [Contributing Guidelines](./CONTRIBUTING.md) - Guidelines for contributing to this project
- [Coding Standards](./CODING_STANDARDS.md) - Coding standards and best practices
- [Release Process](./RELEASE.md) - Information about the release process

## Installation

To install this extension, copy the JAR file `ch.sbb.polarion.extension.xml-repair-<version>.jar` to your Polarion installation directory at:
```
<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.xml-repair/eclipse/plugins
```

Restart Polarion for the changes to take effect.

> **Note:** For detailed build and installation instructions, including automated installation options, see the [Development Guide](./DEVELOPMENT.md#building-the-project).

## Enabling "XML-Repair" in the Navigation Tree

1. Open the Polarion project where you want to activate the extension.
2. On the top of the project's navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Project's administration page will be opened.
3. On the administration's navigation pane select Portal ➙ Topics.
4. Depending on which view type you are using choose to edit either Default or Admin view.
5. In the Topics Configuration editor, insert the following inside the `<topics>` element:
   ```xml
   …
   <topic id="xml-repair"/>
   …
   ```
6. Save changes by clicking 💾 Save.

After Polarion restart, the "XML-Repair" item will appear in the project's left navigation panel. Clicking it opens the XML Repair UI where you can scan and repair entities.

## Extension Configuration

1. On the top of the project's navigation pane click ⚙ (Actions) ➙ 🔧 Administration. Project's administration page will be opened.
2. On the administration's navigation pane select `XML Repair`. There is `Repair Authorization` sub-menu where you can restrict which users should have access to repair functionality. `Quick Help` section of this page contains short description about restriction logic.
3. To change configuration of XML Repair extension just edit corresponding section and press `Save` button.

## REST API

This extension provides REST API. OpenAPI Specification can be obtained [here](docs/openapi.json).
