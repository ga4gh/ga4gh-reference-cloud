'use client';

import { FlowError } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";

import { ActionCard, CenterLink, MarginCard } from "@/components/ui/styled";
import ory from "../../utils/ory/sdk";

export default function AuthErrorClient() {
  const [error, setError] = useState<FlowError | string>();
  const router = useRouter();

  const searchParams = useSearchParams();
  const id = searchParams?.get("id") || undefined;

  useEffect(() => {
    if (error) return;

    if (id) {
      ory
        .getFlowError({ id: String(id) })
        .then(({ data }) => {
          setError(data);
        })
        .catch((err: AxiosError) => {
          switch (err.response?.status) {
            case 404:
            case 403:
            case 410:
              router.push("/");
              return;
          }
          return Promise.reject(err);
        });
    }
  }, [id, router, error]);

  if (!error) {
    return null;
  }

  return (
    <>
      <MarginCard>
        <div className="w-full max-w-4xl mx-auto">
          <h2 className="text-2xl font-bold font-heading text-error mb-4">
          An error occurred
          </h2>
        
          <div className="bg-brand-dark text-emerald-400 p-4 rounded-none font-mono text-xs overflow-x-auto shadow-inner border border-brand-light">
            <pre>{JSON.stringify(error, null, 2)}</pre>
          </div>
        </div>
      </MarginCard>
      
      <ActionCard>
        <Link href="/">
          <CenterLink>Go back</CenterLink>
        </Link>
      </ActionCard>
    </>
  );
}
