import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import docusaurus from "@docusaurus/eslint-plugin";
import prettier from "eslint-config-prettier/flat";

const eslintConfig = defineConfig([
  // build output, docusaurus' generated files, and api docs generated from openapi/ by docusaurus-plugin-openapi-docs
  globalIgnores(["build/**", ".docusaurus/**", "docs/api-reference/**"]),
  ...tseslint.configs.recommended,
  { ...react.configs.flat.recommended, settings: { react: { version: "detect" } } },
  // react 17+ jsx transform: no need to import React in scope
  react.configs.flat["jsx-runtime"],
  reactHooks.configs.flat["recommended-latest"],
  { plugins: { "@docusaurus": docusaurus }, rules: docusaurus.configs.recommended.rules },
  // turns off stylistic rules that conflict with prettier, which owns formatting
  prettier,
]);

export default eslintConfig;
