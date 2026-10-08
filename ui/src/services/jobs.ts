import { useCallback, useRef, useState } from 'react';
import type { RepairResult, ScanResult } from '../types';
import type useRemote from './useRemote';

type SendRequest = ReturnType<typeof useRemote>['sendRequest'];

/** Mutable so the tests can poll without waiting a second per round. */
export const jobTiming = { pollIntervalMs: 1000, unreachableTimeoutMs: 2 * 60 * 1000 };

// What a gateway or a lost connection answers (useRemote turns a network error into 503): the job runs on regardless
const TEMPORARY_STATUSES = new Set([502, 503, 504]);

type JobKind = 'scan' | 'repair';

export interface JobCallbacks {
  /** True once the page no longer waits for this job. A stoppable job is then stopped; the result is dropped. */
  isSuperseded: () => boolean;
  /** Receives the function that asks a stoppable job to stop and return what it found so far. */
  onStarted?: (stop: () => void) => void;
  onProgress?: (message: string) => void;
}

async function errorMessage(response: Response): Promise<string> {
  const data = await response.json().catch(() => null);
  // A job reports `errorMessage`, the exception mappers of the start request report `message`.
  return data?.errorMessage || data?.message || `Request failed with status ${response.status}`;
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Runs a server job: starts it, polls it until it is over and fetches its result. A single request would outlive
 * the gateway timeout in front of Polarion on a long scan or repair.
 *
 * A poll that fails temporarily is repeated: the job goes on on the server, and the page must not offer to start
 * another one while it does. Only a definite answer ends the wait, or no answer for `unreachableTimeoutMs`.
 *
 * @returns the job result, or null when the job was superseded
 * @throws Error with the message to show when the job cannot start or fails
 */
async function runJob<T>(
  sendRequest: SendRequest,
  kind: JobKind,
  body: string,
  callbacks: JobCallbacks,
): Promise<T | null> {
  const jobsUrl = `/${kind}/jobs`;
  // only a scan has a stop endpoint
  const stoppable = kind === 'scan';
  const start = await sendRequest({ method: 'POST', url: jobsUrl, body, contentType: 'application/json' });
  if (start.status !== 202) {
    throw new Error(await errorMessage(start));
  }
  // Only the ID is taken from the Location: it is absolute, and the requests below keep to sendRequest's base.
  const jobId = start.headers.get('Location')?.split('/').filter(Boolean).pop();
  if (!jobId) {
    throw new Error('Job did not return its location.');
  }
  const jobUrl = `${jobsUrl}/${encodeURIComponent(jobId)}`;
  const stop = () => {
    void sendRequest({ method: 'POST', url: `${jobUrl}/stop` });
  };
  if (stoppable) {
    callbacks.onStarted?.(stop);
  }

  let unreachableSince: number | null = null;
  const waitWhileUnreachable = async () => {
    unreachableSince ??= Date.now();
    if (Date.now() - unreachableSince >= jobTiming.unreachableTimeoutMs) {
      const minutes = Math.round(jobTiming.unreachableTimeoutMs / 60000);
      throw new Error(
        `Polarion did not answer for ${minutes} minutes. The ${kind} may still be running on the server.`,
      );
    }
    await delay(jobTiming.pollIntervalMs);
  };

  for (;;) {
    if (callbacks.isSuperseded()) {
      if (stoppable) {
        stop();
      }
      return null;
    }
    const status = await sendRequest({ method: 'GET', url: jobUrl, redirect: 'manual' });
    if (TEMPORARY_STATUSES.has(status.status)) {
      await waitWhileUnreachable();
      continue;
    }
    if (status.status === 202) {
      // only a running job resets the clock: after a 303, the result request must answer too
      unreachableSince = null;
      const details = await status.json().catch(() => null);
      if (details?.progressMessage) {
        callbacks.onProgress?.(details.progressMessage);
      }
      await delay(jobTiming.pollIntervalMs);
      continue;
    }
    if (status.type !== 'opaqueredirect' && status.status !== 303) {
      throw new Error(await errorMessage(status));
    }
    const result = await sendRequest({ method: 'GET', url: `${jobUrl}/result` });
    if (TEMPORARY_STATUSES.has(result.status)) {
      await waitWhileUnreachable();
      continue;
    }
    if (!result.ok) {
      throw new Error(await errorMessage(result));
    }
    const jobResult: T = await result.json();
    return callbacks.isSuperseded() ? null : jobResult;
  }
}

export const runScanJob = (sendRequest: SendRequest, body: string, callbacks: JobCallbacks) =>
  runJob<ScanResult>(sendRequest, 'scan', body, callbacks);

/** A repair has no stop: once started it runs to its single commit, even when the page goes away. */
export const runRepairJob = (sendRequest: SendRequest, body: string, callbacks: JobCallbacks) =>
  runJob<RepairResult[]>(sendRequest, 'repair', body, callbacks);

/** The progress and the stop control of the scan job a page runs, shown by ScanningIndicator. */
export function useScanJobState() {
  const [progress, setProgress] = useState<string | null>(null);
  const [stopping, setStopping] = useState(false);
  const [canStop, setCanStop] = useState(false);
  const stopRef = useRef<(() => void) | null>(null);

  const callbacks = useCallback(
    (isSuperseded: () => boolean): JobCallbacks => ({
      isSuperseded,
      onStarted: (stop) => {
        stopRef.current = stop;
        setCanStop(true);
      },
      onProgress: (message) => {
        if (!isSuperseded()) setProgress(message);
      },
    }),
    [],
  );

  const stop = useCallback(() => {
    if (stopRef.current) {
      setStopping(true);
      stopRef.current();
    }
  }, []);

  const reset = useCallback(() => {
    stopRef.current = null;
    setProgress(null);
    setStopping(false);
    setCanStop(false);
  }, []);

  return { progress, stopping, canStop, callbacks, stop, reset };
}
