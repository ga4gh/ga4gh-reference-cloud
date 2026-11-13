"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";

interface CopyableValueProps {
  value: string;
  label: string;
}

// a value researchers paste into a WES client (a base URL or a workflow_url), shown in monospace with a copy button
const CopyableValue = ({ value, label }: CopyableValueProps) => {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error(`Failed to copy ${label}`, error);
    }
  };

  return (
    <div className="flex items-start gap-2">
      <code className="flex-1 break-all text-sm">{value}</code>
      <button
        type="button"
        className="btn btn-ghost btn-xs"
        onClick={copy}
        aria-label={`Copy ${label}`}
        title={`Copy ${label}`}
      >
        {copied ? <Check size={16} /> : <Copy size={16} />}
      </button>
    </div>
  );
};

export default CopyableValue;
