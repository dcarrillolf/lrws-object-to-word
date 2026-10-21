## Introduction

- Sample OSGi modules that generate a MS Word document (.docx) from a Liferay Object Entry, filling a Word template with `{{placeholder}}` values.
- Uses [Apache POI](https://poi.apache.org/) (`poi-ooxml`), embedded in the module.
- The generation is triggered with a Gogo shell command. The document is saved in the app server temp folder.

## Modules

- `object-to-word`: `ObjectEntryWordExporter` OSGi service, the template filling logic and the sample template `templates/ORDER.docx`.
- `object-to-word-gogo`: Gogo shell command `objectToWord:export`.

## Steps to test

1. Build and deploy the modules: `blade gw deploy`. Check the logs to confirm they are `STARTED`.
2. Import the sample Object Definition `object-definitions/Object_Definition_SampleOrder_ORDER.json` (Control Panel > Objects > Import Object Definition), keeping the External Reference Code `ORDER`, and publish it.
   - The Object Definition labels are in `en_US` (the default language of a new Liferay instance). If the instance default language is different, replace `en_US` with it (e.g. `es_ES`) in the JSON before importing it.
3. Add a Sample Order entry and get its ID.
4. Get the Company ID (Control Panel > Virtual Instances).
5. Open the Gogo shell (Control Panel > Gogo Shell) and run:

```
objectToWord:export <companyId> ORDER <objectEntryId>
```

6. The command prints the path of the generated file, e.g. `tomcat/temp/object-to-word/ORDER_1234_567890.docx`.

## Template

- The template is `modules/object-to-word/src/main/resources/templates/{ERC}.docx`.
- Each Object Field is a placeholder with the field name, e.g. `{{shipmentDate}}`. `{{id}}`, `{{externalReferenceCode}}`, `{{creator}}`, `{{createDate}}`, `{{modifiedDate}}` and `{{generatedDate}}` are also available.
- Dates are formatted as `dd/MM/yyyy`, picklists use the entry name and line breaks are kept. Unknown placeholders are removed.
- Placeholders are replaced in the body, tables, headers and footers.

## Environment

- Liferay DXP 2024.Q1.10 (`liferay.workspace.product=dxp-2024.q1.10`).
- JDK 11.
- Apache POI 5.4.1.

## Notes
- This is a 'proof of concept' that is being provided 'as is' without any support coverage or warranty.
