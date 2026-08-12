import type { SidebarsConfig } from "@docusaurus/plugin-content-docs";

const sidebar: SidebarsConfig = {
  apisidebar: [
    {
      type: "doc",
      id: "api-reference/drs/reference-cloud-data-repository-service-drs",
    },
    {
      type: "category",
      label: "Objects",
      items: [
        {
          type: "doc",
          id: "api-reference/drs/options-object",
          label: "Get Authorization info about a DrsObject.",
          className: "api-method options",
        },
        {
          type: "doc",
          id: "api-reference/drs/get-object",
          label: "Get info about a DrsObject.",
          className: "api-method get",
        },
        {
          type: "doc",
          id: "api-reference/drs/post-object",
          label: "Get info about a DrsObject through POST'ing a Passport.",
          className: "api-method post",
        },
        {
          type: "doc",
          id: "api-reference/drs/options-bulk-object",
          label: "Get Authorization info about multiple DrsObjects.",
          className: "api-method options",
        },
        {
          type: "doc",
          id: "api-reference/drs/get-bulk-objects",
          label: "Get info about multiple DrsObjects with an optional Passport(s).",
          className: "api-method post",
        },
      ],
    },
    {
      type: "category",
      label: "Service Info",
      items: [
        {
          type: "doc",
          id: "api-reference/drs/get-service-info",
          label: "Retrieve information about this service",
          className: "api-method get",
        },
      ],
    },
  ],
};

export default sidebar.apisidebar;
