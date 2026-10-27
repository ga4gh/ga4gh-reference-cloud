import { AxiosError } from "axios"
import { MouseEvent, useState, useEffect, DependencyList } from "react"
import { useRouter as useAppRouter } from "next/navigation";
import { useRouter as usePagesRouter } from "next/router";

import ory from "./sdk"

export function LogoutLink(deps?: DependencyList) {
  const [logoutToken, setLogoutToken] = useState<string>("")

  let router;
  try {
    router = useAppRouter();
  } catch {
    router = usePagesRouter();
  }

  useEffect(() => {
    ory
      .createBrowserLogoutFlow()
      .then(({ data }) => {
        setLogoutToken(data.logout_token)
      })
      .catch((err: AxiosError) => {
        switch (err.response?.status) {
          case 401:
            return
        }

        return Promise.reject(err)
      })
  }, deps)

  return (event?: MouseEvent) => {
    if (logoutToken) {
      event?.preventDefault();

      ory
        .updateLogoutFlow({ token: logoutToken })
        .then(() => {
          if (!router) return;
          router.push("/");
        })
        .then(() => {
          if (!router) return;
          
          if ('refresh' in router) {
            router.refresh();
          } else if ('reload' in router) {
            router.reload();
          }
        })
        .catch((err) => console.error("Logout failed", err));
    }
  }
}
