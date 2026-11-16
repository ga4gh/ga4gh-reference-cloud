// Passport visas as core-api returns them on datasets and compute platforms: the visa that gates access, and the
// researcher's access request for it (assertion).

export enum PassportVisaAssertionStatus {
  NotRequested = "NotRequested",
  Requested = "Requested",
  Approved = "Approved",
  Denied = "Denied",
  Revoked = "Revoked",
  Expired = "Expired",
}

export type PassportVisa = {
  id: string;
  name: string;
  description: string;
  // null until the user requests access
  assertion: {
    userId: string;
    currentStatus: PassportVisaAssertionStatus;
    currentStatusAt: string;
  } | null;
};

// the researcher's access status for a visa (NotRequested when they haven't asked yet)
export function assertionStatus(visa: PassportVisa | null): PassportVisaAssertionStatus {
  return visa?.assertion?.currentStatus ?? PassportVisaAssertionStatus.NotRequested;
}

// status pill classes (styles/globals.css) for each access status
export const ACCESS_STATUS_CLASSES: Record<PassportVisaAssertionStatus, string> = {
  NotRequested: "ga4gh-status ga4gh-status--not-requested",
  Requested: "ga4gh-status ga4gh-status--requested",
  Approved: "ga4gh-status ga4gh-status--approved",
  Denied: "ga4gh-status ga4gh-status--denied",
  Revoked: "ga4gh-status ga4gh-status--denied",
  Expired: "ga4gh-status ga4gh-status--denied",
};
