---
title: Data Repository Service
sidebar_label: DRS
sidebar_position: 2
---

## Getting Started

To run the DRS API Server, run the following command from the project's working directory: 
```jsx
docker run --rm -d --name ga4gh-drs-test -p 4500:4500 -p 8080:8080 -v `pwd`/src/test/resources:/config ga4gh/ga4gh-starter-kit-drs:0.1.8 java -jar ga4gh-starter-kit-drs.jar -c /config/config.yml
```

## Configuration

## Index
`localhost:3000/drs`

From the [Home](starter_kit_ui.md) page, select the `DRS Starter Kit` option. This will render the DRS Starter Kit Index page and display all of the DRS Objects that you have access to through your current server configuration.

## View DRS Object
`localhost:3000/drs/{DRS Object Id}`

## Edit DRS Object
`localhost:3000/drs/{DRS Object Id}/edit`

## New DRS Object
`localhost:3000/drs/new`

Navigate to the New DRS Object form by clicking the `New DRS Object` button located on the [Index](drs_starter_kit_ui.md#index) page. 
<br />
To create a new DRS Object, populate the relevant fields displayed on the DRS Object form and click the Submit button. Note that all DRS Objects must have a unique ID and must be identified as either a Blob or a Bundle. All other fields are optional. See below for more details about some of the New DRS Object form fields. 

#### Id
All DRS Object must have a unique ID which can be entered in the Id field manually or by selecting the Generate ID button to generate a random UUID. This value cannot be updated after the new DRS Object has been submitted.

#### Created Time and Updated Time
By default, Created Time and Updated Time are populated with the current time, as of when the New DRS Object page was rendered. To update either of these values, click on the field to display a date-time picker. Any date or time can be selected, however, note that the seconds cannot be selected and will therefore will automatically be set to zero.
`!TODO: Insert screenshot of date time picker!`

#### Blob and Bundle Selector
Use these radio buttons to identify whether the new DRS Object is a bundle or a blob. Note that "Blob" is selected by default. Bundles can have child DRS Objects, while Blobs are single DRS Objects and do not have any children. 
<br/>

The following property fields are common to both Blobs and Bundles:
- Id
- Name
- Description
- Created Time
- Updated Time
- Version
- Aliases
- Parent Bundles

The following property fields are displayed when "Blob" is selected:
- MIME Type
- Size
- Checksums
- Local File Access Points
- AWS S3 Access Points 

The following property fields are displayed when "Bundle" is selected: 
- Bundle Children

#### Checksums

#### Parent Bundles and Bundle Children

#### Aliases, Local File Access Points, and AWS S3 Access Points