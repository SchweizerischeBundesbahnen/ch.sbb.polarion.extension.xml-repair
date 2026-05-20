# Changelog

## [2.4.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/compare/v2.3.1...v2.4.0) (2026-05-20)


### Features

* new check for standard structure link role usage in a LiveDoc ([#52](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/52)) ([a4a7dbc](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/a4a7dbc3f6f4e68410511fbd9c75f2389111dd05)), closes [#41](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/41)
* show statistics about found XML Problems on the plugin report page ([#48](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/48)) ([0183069](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/018306922bbfe5a9912289f0780f757988b332dc)), closes [#43](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/43)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v14.1.4 ([#51](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/51)) ([f9eae0d](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/f9eae0d9b170286acb7489f00e4f94f47dcda2f7))

## [2.3.1](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/compare/v2.3.0...v2.3.1) (2026-05-15)


### Bug Fixes

* false-positive link role rule violation ([#37](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/37)) ([73ea355](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/73ea35533cbbc2cac037ec7eafcdd40f861d3a91)), closes [#36](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/36)

## [2.3.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/compare/v2.2.0...v2.3.0) (2026-05-14)


### Features

* ability to run a scan using a specific revision/baseline ([#32](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/32)) ([7ad4c11](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/7ad4c11faad27707a8c3aa015645c971760091b2)), closes [#24](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/24)
* repair enumeration values using names ([#27](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/27)) ([47e023b](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/47e023b82fc57adcfdb9b26ea3b273a940c5b1b2)), closes [#21](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/21)


### Bug Fixes

* NPE during 'Broken Work Item Links' scan ([#34](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/34)) ([1d49ac0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/1d49ac0dbb1b8689165485b101ccb1e651bce9ee)), closes [#33](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/33)

## [2.2.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/compare/v2.1.0...v2.2.0) (2026-04-23)


### Features

* broken linked worked items repairer ([#18](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/18)) ([7906061](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/790606179c8a53578ccfd25647848fe2d2c8a3d8)), closes [#13](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/13)

## 2.1.0 (2026-04-17)


### Features

* icon ([#14](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/14)) ([7e1b357](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/7e1b357f28fa1bcd396b643c71d4ca0edc0af418)), closes [#12](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/12)


### Bug Fixes

* cannot delete single decimal in numeric fields ([#9](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/9)) ([f11194f](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/f11194fb99e8d1664cf321ea91893f8bf3704c7a)), closes [#8](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/8)
* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v14.1.2 ([817c940](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/817c94072b822405d76d23c988264ed6c6b3cb3a))
* Missing Title Repairer gives false positive on a newly created d… ([#11](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/11)) ([103a3e4](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/103a3e4ca3bd1260638109e0989b4828b9b64226)), closes [#10](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/10)
* wrong breadcrumbs ([#16](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/16)) ([a924549](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/commit/a924549dc5e633dda46dfe04f78ce1f3b497b629)), closes [#15](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.xml-repair/issues/15)

## Changelog

## Changelog before migration to conventional commits

| Version | Changes                                                                                                                                                                                                                                                                                                                                                      |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v2.0.0  | * Extension rework: instead of widget now it is accessible from navigation menu<br/> * ToT/ToF captions repairer                                                                                                                                                                                                                                             |
| v1.8.0  | Collections scanning and repair                                                                                                                                                                                                                                                                                                                              |
| v1.7.0  | * Unresolvable Document enum field values handling<br/> * Ability to replace broken links with plain text<br/> * Scan for issues of the currently selected repairers only<br/> * Attempt to search work item globally on broken link repair<br/> * Wrong layout assignments repairer now works properly for items containing 'layout_content_type' attribute |
| v1.6.0  | * 'Wrong layout assignments' now adds missing layout declaration<br/> * Enumeration repairer attempts to fix issues even if there are no related issues found<br/> * False-positive results for user enumerations                                                                                                                                            |
| v1.5.0  | * Invalid enumeration values repairer<br/> * NPE in 'String fields: Formatting Symbols' repairer<br/> * Duplicated layout declaration isn't deleted if there is no workitems with this type                                                                                                                                                                  |
| v1.4.0  | New repairers:<br/> * Duplicate layout declarations<br/> * Missing title-heading<br/> * Wrong title-heading position                                                                                                                                                                                                                                         |
| v1.3.0  | * Show items with issues only<br/> * Scan time limit<br/> * Scan report                                                                                                                                                                                                                                                                                      |
| v1.2.0  | * User guide<br/> * Fixed false-positive 'Wrong layout assignments' error<br/> * Fixed skip unresolved workitems<br/> * REST controllers registered as resource classes instead of provider singletons                                                                                                                                                       |
| v1.1.0  | Removed required 'entityRevision' repair request parameter                                                                                                                                                                                                                                                                                                   |
| v1.0.0  | Polarion 2512 support                                                                                                                                                                                                                                                                                                                                        |
| v0.2.0  | * Ability to select specific repairers from list<br/> * Repair wrong layout assignments                                                                                                                                                                                                                                                                      |
| v0.1.0  | Initial release                                                                                                                                                                                                                                                                                                                                              |
