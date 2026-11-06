import { AxiosError } from "axios";
import { MouseEvent, useState, useEffect } from "react";
import { useRouter } from "next/navigation";

import ory from "./sdk";

// creates a logout flow on mount, and again whenever refreshKey changes
export function LogoutLink(refreshKey?: string) {
  const [logoutToken, setLogoutToken] = useState<string>("");
  const router = useRouter();

  useEffect(() => {
    ory
      .createBrowserLogoutFlow()
      .then(({ data }) => {
        setLogoutToken(data.logout_token);
      })
      .catch((err: AxiosError) => {
        switch (err.response?.status) {
          case 401:
            return;
        }

        return Promise.reject(err);
      });
  }, [refreshKey]);

  return (event?: MouseEvent) => {
    if (logoutToken) {
      event?.preventDefault();

      ory
        .updateLogoutFlow({ token: logoutToken })
        .then(() => {
          router.push("/");
          router.refresh();
        })
        .catch((err) => console.error("Logout failed", err));
    }
  };
}
