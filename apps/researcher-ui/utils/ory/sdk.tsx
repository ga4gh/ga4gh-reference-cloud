import { Configuration, FrontendApi } from "@ory/client"

const basePath = process.env.NEXT_PUBLIC_KRATOS_PUBLIC_URL;

const localConfig = new Configuration({
  basePath: basePath,
  baseOptions: {
    withCredentials: true,
  },
});

const oryClient = new FrontendApi(localConfig);

export default oryClient;
