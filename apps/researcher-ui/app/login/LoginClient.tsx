'use client';

import { LoginFlow, UpdateLoginFlowBody } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { ActionCard, CenterLink, LogoutLink } from "../../pkg";
import { Flow } from "../../components/ui";
import { handleGetFlowError, handleFlowError } from "../../utils/ory/errors";
import ory from "../../utils/ory/sdk";

export default function LoginClient() {
  const [flow, setFlow] = useState<LoginFlow>();
  const router = useRouter();

  const searchParams = useSearchParams();
  const returnTo = searchParams?.get("return_to") || undefined;
  const flowId = searchParams?.get("flow") || undefined;
  const refresh = searchParams?.get("refresh");
  const aal = searchParams?.get("aal") || undefined;

  const onLogout = LogoutLink([aal, refresh]);

  useEffect(() => {
    if (flow) return;

    if (flowId) {
      ory
        .getLoginFlow({ id: String(flowId) })
        .then(({ data }) => {
          setFlow(data);
        })
        .catch(handleGetFlowError(router, "login", setFlow));
      return;
    }

    ory
      .createBrowserLoginFlow({
        refresh: Boolean(refresh),
        aal: aal ? String(aal) : undefined,
        returnTo: returnTo ? String(returnTo) : undefined,
      })
      .then(({ data }) => {
        setFlow(data);
      })
      .catch(handleFlowError(router, "login", setFlow));
  }, [flowId, router, aal, refresh, returnTo, flow]);

  const onSubmit = (values: UpdateLoginFlowBody) => {
    // 💡 Shallow routing is default in App Router, path string is standard
    router.push(`/login?flow=${flow?.id}`);
    
    return ory
      .updateLoginFlow({
        flow: String(flow?.id),
        updateLoginFlowBody: values,
      })
      .then(() => {
        if (flow?.return_to) {
          window.location.href = flow?.return_to;
          return;
        }
        router.push("/");
      })
      .catch(handleFlowError(router, "login", setFlow))
      .catch((err: AxiosError<LoginFlow>) => {
        if (err.response?.status === 400) {
          setFlow(err.response?.data);
          return;
        }
        return Promise.reject(err);
      });
  };

  return (
    <div className="hero min-h-screen">
      <div className="ga4gh-hero-bg"></div>
      <div className="hero-overlay bg-[#363636]/60"></div>
      <div className="hero-content flex-col lg:flex-row-reverse">
        <div className="card bg-base-100 w-full max-w-sm shrink-0 box-shadow-card">
          <div className="card-body">
            <h2 className="card-title">
              {(() => {
                if (flow?.refresh) {
                  return "Confirm Action";
                } else if (flow?.requested_aal === "aal2") {
                  return "Two-Factor Authentication";
                }
                return "Log in";
              })()}
            </h2>
            <Flow onSubmit={onSubmit} flow={flow} />
            {aal || refresh ? (
              <ActionCard>
                <CenterLink data-testid="logout-link" onClick={onLogout}>
                  Log out
                </CenterLink>
              </ActionCard>
            ) : (
              <>
                <div className="flex w-full flex-col">
                  <div className="divider" />
                </div>
                <p>New user? <Link href="/registration"><span className="ga4gh-link">Sign up</span></Link></p>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
