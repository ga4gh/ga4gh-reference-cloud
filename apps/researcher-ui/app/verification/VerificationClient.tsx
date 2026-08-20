'use client';

import { VerificationFlow, UpdateVerificationFlowBody } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { Flow, ActionCard, CenterLink, MarginCard } from "../../pkg";
import ory from "../../utils/ory/sdk";

export default function VerificationClient() {
  const [flow, setFlow] = useState<VerificationFlow>();
  const router = useRouter();

  const searchParams = useSearchParams();
  const flowId = searchParams?.get("flow") || undefined;
  const returnTo = searchParams?.get("return_to") || undefined;

  useEffect(() => {
    if (flowId) {
      if (flow?.id === flowId) {
        return;
      }

      ory
        .getVerificationFlow({ id: String(flowId) })
        .then(({ data }) => {
          setFlow(data);
        })
        .catch((err: AxiosError) => {
          switch (err.response?.status) {
            case 410:
            case 403:
              router.push("/verification");
              return;
          }
          throw err;
        });
      return;
    }

    if (!flow) {
      ory
        .createBrowserVerificationFlow({
          returnTo: returnTo ? String(returnTo) : undefined,
        })
        .then(({ data }) => {
          setFlow(data);
        })
        .catch((err: AxiosError) => {
          switch (err.response?.status) {
            case 400:
              router.push("/");
              return;
          }
          throw err;
        });
    }
  }, [flowId, router, returnTo, flow]);

  const onSubmit = async (values: UpdateVerificationFlowBody) => {
    router.push(`/verification?flow=${flow?.id}`);

    ory
      .updateVerificationFlow({
        flow: String(flow?.id),
        updateVerificationFlowBody: values,
      })
      .then(({ data }) => {
        setFlow(data);
      })
      .catch((err: AxiosError<VerificationFlow & { use_flow_id?: string }>) => {
        switch (err.response?.status) {
          case 400:
            setFlow(err.response?.data);
            return;
          case 410:
            const newFlowID = String(err.response?.data?.use_flow_id);
            router.push(`/verification?flow=${newFlowID}`);

            ory
              .getVerificationFlow({ id: newFlowID })
              .then(({ data }) => setFlow(data));
            return;
        }
        throw err;
      });
  };

  return (
    <>
      <MarginCard>
        <h1 className="text-xl font-semibold text-gray-900 mb-4">
          Verify your account
        </h1>
        <Flow onSubmit={onSubmit} flow={flow} />
      </MarginCard>
      <ActionCard>
        <Link href="/">
          <CenterLink>Go back</CenterLink>
        </Link>
      </ActionCard>
    </>
  );
}
