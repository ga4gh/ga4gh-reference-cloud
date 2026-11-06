declare module "@docusaurus/theme-openapi-docs/lib/theme/store";

// docusaurus' bundler turns image imports into a url, but @docusaurus/module-type-aliases only declares *.svg
declare module "*.png" {
  const src: string;
  export default src;
}
