'use client';

import { RecoveryFlow, UpdateRecoveryFlowBody } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ActionCard, CenterLink, MarginCard } from "@/components/ui/styled";
import { Flow } from "@/components/ui";
import { handleFlowError } from "../../utils/ory/errors";
import ory from "../../utils/ory/sdk";

export default function RecoveryClient() {
  const [flow, setFlow] = useState<RecoveryFlow>();
  const router = useRouter();
  
  const searchParams = useSearchParams();
  const flowId = searchParams?.get("flow") || undefined;
  const returnTo = searchParams?.get("return_to") || "";

  useEffect(() => {
    if (flow) return;

    if (flowId) {
      ory
        .getRecoveryFlow({ id: String(flowId) })
        .then(({ data }) => {
          setFlow(data);
        })
        .catch(handleFlowError(router, "recovery", setFlow));
      return;
    }

    ory
      .createBrowserRecoveryFlow({
        returnTo: String(returnTo),
      })
      .then(({ data }) => {
        setFlow(data);
      })
      .catch(handleFlowError(router, "recovery", setFlow))
      .catch((err: AxiosError<RecoveryFlow>) => {
        if (err.response?.status === 400) {
          setFlow(err.response?.data);
          return;
        }
        return Promise.reject(err);
      });
  }, [flowId, router, returnTo, flow]);

  const onSubmit = (values: UpdateRecoveryFlowBody) => {
    router.push(`/recovery?flow=${flow?.id}`);

    return ory
      .updateRecoveryFlow({
        flow: String(flow?.id),
        updateRecoveryFlowBody: values,
      })
      .then(({ data }) => {
        setFlow(data);
      })
      .catch(handleFlowError(router, "recovery", setFlow))
      .catch((err: AxiosError<RecoveryFlow>) => {
        switch (err.response?.status) {
          case 400:
            setFlow(err.response?.data);
            return;
        }
        throw err;
      });
  };

  return (
    <>
      <MarginCard>
        <h1 
          style={{ 
            fontSize: '1.5rem', 
            fontWeight: 600, 
            color: '#111827', 
            marginBottom: '1rem',
            textAlign: 'center' 
          }}
        >
          Recover your account
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
