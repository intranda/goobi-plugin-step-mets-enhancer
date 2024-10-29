---
title: METS Enhancer
identifier: intranda_step_mets_enhancer
description: Step Plugin for the automatic enhancement of METS files
published: true
---

## Introduction
This documentation explains the automatic enrichment of data in the METS file with configurable pagination.

## Installation
To be able to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/plugin-step-mets-enhancer-base-24.03-SNAPSHOT.jar
/opt/digiverso/goobi/config/plugin_intranda_step_mets_enhancer.xml
```

After installing the plugin, it can be selected within the workflow for the respective steps and thus executed automatically.

To use the plugin, it must be selected in a workflow step:

![Configuration of the workflow step for using the plugin](screen1_en.png)


## Overview and functionality
The plugin opens the METS file and enriches it with metadata from the images contained in the media folder. Additionally, metadata from the rule set can be added automatically. Pagination and further metadata can also be included through configuration.

## Configuration
The plugin is configured in the file `plugin_intranda_step_mets_enhancer.xml` as shown here:

{{CONFIG_CONTENT}}

{{CONFIG_DESCRIPTION_PROJECT_STEP}}

Parameter               | Explanation
------------------------|------------------------------------
| `<createPagination>`    | Here, a pagination can be created by setting the value to `true`. This can also be configured through the `type`. Pagination can be `uncounted`, `roman`, `ROMAN`, or `arabic`. |
| `<addMetadata>`         | Additional values can be added to the configuration here. The type must match a metadata type from the rule set, and value can be set freely. |