'use client';

import { SettingsFlow, UpdateSettingsFlowBody } from "@ory/client";
import { AxiosError } from "axios";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";

import { ActionCard, CenterLink } from "@/components/ui/styled";
import { Flow, Messages, Methods } from "@/components/ui";
import { handleFlowError } from "@/utils/ory/errors";
import ory from "@/utils/ory/sdk";

const pageTitleStyle = { marginTop: 80, fontSize: "1.75rem", fontWeight: 700, color: "#111827", marginBottom: "2rem" };
const heading3Style = { fontSize: "1.25rem", fontWeight: 600, color: "#1a202c", marginBottom: "1rem" };
const paragraphStyle = { fontSize: "0.95rem", color: "#4a5568", lineHeight: "1.5", marginBottom: "1rem" };
const linkStyle = { color: "#3182ce", textDecoration: "underline" };

interface Props {
  flow?: SettingsFlow;
  only?: Methods;
}

function SettingsCard({ flow, only, children }: Props & { children: ReactNode }) {
  if (!flow) return null;
  const nodes = only ? flow.ui.nodes.filter(({ group }) => group === only) : flow.ui.nodes;
  if (nodes.length === 0) return null;
  return <ActionCard>{children}</ActionCard>;
}

export default function SettingsClient() {
  const [flow, setFlow] = useState<SettingsFlow>();
  const router = useRouter();
  
  const searchParams = useSearchParams();
  const flowId = searchParams?.get("flow") || undefined;
  const returnTo = searchParams?.get("return_to") || "";

  useEffect(() => {
    if (flow) return;

    if (flowId) {
      ory.getSettingsFlow({ id: String(flowId) })
        .then(({ data }) => setFlow(data))
        .catch(handleFlowError(router, "settings", setFlow));
      return;
    }

    ory.createBrowserSettingsFlow({ returnTo: String(returnTo) })
      .then(({ data }) => setFlow(data))
      .catch(handleFlowError(router, "settings", setFlow));
  }, [flowId, router, returnTo, flow]);

  const onSubmit = (values: UpdateSettingsFlowBody) => {
    router.push(`/settings?flow=${flow?.id}`);

    return ory
      .updateSettingsFlow({ 
        flow: String(flow?.id), 
        updateSettingsFlowBody: values 
      })
      .then(({ data }) => {
        setFlow(data);
        if (data.continue_with) {
          for (const item of data.continue_with) {
            if (item.action === "show_verification_ui") {
              router.push("/verification?flow=" + item.flow.id);
            }
          }
        }
        if (data.return_to) window.location.href = data.return_to;
      })
      .catch(handleFlowError(router, "settings", setFlow))
      .catch(async (err: AxiosError<SettingsFlow>) => {
        if (err.response?.status === 400) {
          setFlow(err.response?.data);
        } else {
          return Promise.reject(err);
        }
      });
  };

  return (
    <>
      <h1 style={pageTitleStyle}>Profile Management and Security</h1>

      {/* Profile Section */}
      <SettingsCard only="profile" flow={flow}>
        <h3 style={heading3Style}>Profile Settings</h3>
        <Messages messages={flow?.ui.messages} />
        <Flow hideGlobalMessages onSubmit={onSubmit} only="profile" flow={flow} />
      </SettingsCard>

      {/* Password Section */}
      <SettingsCard only="password" flow={flow}>
        <h3 style={heading3Style}>Change Password</h3>
        <Messages messages={flow?.ui.messages} />
        <Flow hideGlobalMessages onSubmit={onSubmit} only="password" flow={flow} />
      </SettingsCard>

      {/* OIDC Section */}
      <SettingsCard only="oidc" flow={flow}>
        <h3 style={heading3Style}>Manage Social Sign In</h3>
        <Messages messages={flow?.ui.messages} />
        <Flow hideGlobalMessages onSubmit={onSubmit} only="oidc" flow={flow} />
      </SettingsCard>

      {/* 2FA Sections */}
      <SettingsCard only="lookup_secret" flow={flow}>
        <h3 style={heading3Style}>Recovery Codes</h3>
        <p style={paragraphStyle}>Use recovery codes if you lose your 2FA device.</p>
        <Flow hideGlobalMessages onSubmit={onSubmit} only="lookup_secret" flow={flow} />
      </SettingsCard>

      <SettingsCard only="totp" flow={flow}>
        <h3 style={heading3Style}>Authenticator App</h3>
        <p style={paragraphStyle}>
          Add a <a href="https://lastpass.com" style={linkStyle}>TOTP App</a> for better security.
        </p>
        <Flow hideGlobalMessages onSubmit={onSubmit} only="totp" flow={flow} />
      </SettingsCard>

      <SettingsCard only="webauthn" flow={flow}>
        <h3 style={heading3Style}>Hardware Tokens</h3>
        <p style={paragraphStyle}>Use YubiKey or Biometrics (FaceID/TouchID).</p>
        <Flow hideGlobalMessages onSubmit={onSubmit} only="webauthn" flow={flow} />
      </SettingsCard>

      <SettingsCard only="passkey" flow={flow}>
        <h3 style={heading3Style}>Manage Passkeys</h3>
        <Flow hideGlobalMessages onSubmit={onSubmit} only="passkey" flow={flow} />
      </SettingsCard>

      <ActionCard>
        <Link href="/">
          <CenterLink>Go back</CenterLink>
        </Link>
      </ActionCard>
    </>
  );
}
