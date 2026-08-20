'use client';

import { RegistrationFlow, UpdateRegistrationFlowBody } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { Flow } from "../../pkg/ui-ga4gh";
import { handleFlowError } from "../../pkg/errors";
import ory from "../../pkg/sdk";

export default function RegistrationClient() {
  const router = useRouter();
  const [flow, setFlow] = useState<RegistrationFlow>();

  const searchParams = useSearchParams();
  const flowId = searchParams?.get("flow") || undefined;
  const returnTo = searchParams?.get("return_to") || undefined;

  useEffect(() => {
    if (flow) return;

    if (flowId) {
      ory
        .getRegistrationFlow({ id: String(flowId) })
        .then(({ data }) => {
          setFlow(data);
        })
        .catch(handleFlowError(router, "registration", setFlow));
      return;
    }

    ory
      .createBrowserRegistrationFlow({
        returnTo: returnTo ? String(returnTo) : undefined,
      })
      .then(({ data }) => {
        setFlow(data);
      })
      .catch(handleFlowError(router, "registration", setFlow));
  }, [flowId, router, returnTo, flow]);

  const onSubmit = async (values: UpdateRegistrationFlowBody) => {
    router.push(`/registration?flow=${flow?.id}`);

    ory
      .updateRegistrationFlow({
        flow: String(flow?.id),
        updateRegistrationFlowBody: values,
      })
      .then(({ data }) => {
        console.log("This is the user session: ", data, data.identity);

        if (data.continue_with) {
          for (const item of data.continue_with) {
            switch (item.action) {
              case "show_verification_ui":
                router.push("/verification?flow=" + item.flow.id);
                return;
            }
          }
        }

        router.push(flow?.return_to || "/");
      })
      .catch(handleFlowError(router, "registration", setFlow))
      .catch((err: AxiosError<RegistrationFlow>) => {
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
            <h2 className="card-title">Sign up</h2>
            <Flow onSubmit={onSubmit} flow={flow} />
            <div className="flex w-full flex-col">
              <div className="divider" />
            </div>
            <p>Already registered? <Link href="/login"><span className="ga4gh-link">Log in</span></Link></p>
          </div>
        </div>
      </div>
    </div>
  );
}
