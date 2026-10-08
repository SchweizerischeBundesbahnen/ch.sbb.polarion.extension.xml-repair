import { useCallback, useRef, useState } from 'react';
import type { RepairResult, ScanResult } from '../types';
import type useRemote from './useRemote';

type SendRequest = ReturnType<typeof useRemote>['sendRequest'];

/** Mutable so the tests can poll without waiting a second per round. */
export const jobTiming = { pollIntervalMs: 1000 };

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
 * @param jobsUrl where the jobs of this kind are started, e.g. `/scan/jobs`
 * @param stoppable whether the job kind has a stop endpoint
 * @returns the job result, or null when the job was superseded
 * @throws Error with the message to show when the job cannot start or fails
 */
async function runJob<T>(
  sendRequest: SendRequest,
  jobsUrl: string,
  body: string,
  callbacks: JobCallbacks,
  stoppable: boolean,
): Promise<T | null> {
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

  for (;;) {
    if (callbacks.isSuperseded()) {
      if (stoppable) {
        stop();
      }
      return null;
    }
    const status = await sendRequest({ method: 'GET', url: jobUrl, redirect: 'manual' });
    if (status.status === 202) {
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
    if (!result.ok) {
      throw new Error(await errorMessage(result));
    }
    const jobResult: T = await result.json();
    return callbacks.isSuperseded() ? null : jobResult;
  }
}

export const runScanJob = (sendRequest: SendRequest, body: string, callbacks: JobCallbacks) =>
  runJob<ScanResult>(sendRequest, '/scan/jobs', body, callbacks, true);

/** A repair has no stop: once started it runs to its single commit, even when the page goes away. */
export const runRepairJob = (sendRequest: SendRequest, body: string, callbacks: JobCallbacks) =>
  runJob<RepairResult[]>(sendRequest, '/repair/jobs', body, callbacks, false);

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
