import { Configuration, FrontendApi } from "@ory/client"

const basePath = typeof window === "undefined"
  ? process.env.KRATOS_PUBLIC_API_BASE_URL
  : "/api/.ory";

const localConfig = new Configuration({
  basePath: basePath,
  baseOptions: {
    withCredentials: true,
  },
});

const oryClient = new FrontendApi(localConfig);

export default oryClient;
