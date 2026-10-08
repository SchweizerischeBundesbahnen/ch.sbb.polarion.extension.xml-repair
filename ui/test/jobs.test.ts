import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, renderHook } from 'vitest-browser-react';
import { runRepairJob, runScanJob, useScanJobState } from '../src/services/jobs';
import { SCAN_RESULT } from './fixtures';
import { jsonResponse } from './mockFetch';

// The scan job protocol as runScanJob speaks it, against a scripted sendRequest. The page tests cover the
// same protocol through mockFetch's emulation; these pin the answers that emulation never gives.

interface Call {
  method: string;
  url: string;
  redirect?: RequestRedirect;
}

const started = () =>
  new Response(null, {
    status: 202,
    headers: { Location: 'http://host/polarion/xml-repair/rest/internal/scan/jobs/abc' },
  });

function scripted(...responses: Response[]) {
  const calls: Call[] = [];
  const sendRequest = vi.fn(async (call: Call) => {
    calls.push(call);
    if (call.url.endsWith('/stop')) return new Response(null, { status: 204 });
    const next = responses.shift();
    if (!next) throw new Error(`unscripted ${call.method} ${call.url}`);
    return next;
  });
  return { sendRequest, calls };
}

describe('runScanJob', () => {
  it('polls with manual redirects, reports progress and fetches the result', async () => {
    const { sendRequest, calls } = scripted(
      started(),
      jsonResponse({ status: 'IN_PROGRESS', progressMessage: '5 items scanned, 2 with issues' }, 202),
      new Response(null, { status: 303 }),
      jsonResponse(SCAN_RESULT),
    );
    const onProgress = vi.fn();

    const result = await runScanJob(sendRequest, '{}', { isSuperseded: () => false, onProgress });

    expect(result).toEqual(SCAN_RESULT);
    expect(onProgress).toHaveBeenCalledWith('5 items scanned, 2 with issues');
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual([
      'POST /scan/jobs',
      'GET /scan/jobs/abc',
      'GET /scan/jobs/abc',
      'GET /scan/jobs/abc/result',
    ]);
    expect(calls[1].redirect).toBe('manual');
  });

  it('takes an opaque redirect for a finished job', async () => {
    const opaque = new Response(null, { status: 303 });
    Object.defineProperty(opaque, 'type', { value: 'opaqueredirect' });
    const { sendRequest } = scripted(started(), opaque, jsonResponse(SCAN_RESULT));

    expect(await runScanJob(sendRequest, '{}', { isSuperseded: () => false })).toEqual(SCAN_RESULT);
  });

  it('keeps polling when a running job answers without details', async () => {
    const { sendRequest } = scripted(
      started(),
      new Response(null, { status: 202 }),
      new Response(null, { status: 303 }),
      jsonResponse(SCAN_RESULT),
    );
    const onProgress = vi.fn();

    expect(await runScanJob(sendRequest, '{}', { isSuperseded: () => false, onProgress })).toEqual(SCAN_RESULT);
    expect(onProgress).not.toHaveBeenCalled();
  });

  it('reports the message of a result which cannot be fetched', async () => {
    const { sendRequest } = scripted(
      started(),
      new Response(null, { status: 303 }),
      jsonResponse({ message: 'Scan job is unknown: abc' }, 500),
    );

    await expect(runScanJob(sendRequest, '{}', { isSuperseded: () => false })).rejects.toThrow(
      'Scan job is unknown: abc',
    );
  });

  it('reports the message of a failed job', async () => {
    const { sendRequest } = scripted(started(), jsonResponse({ status: 'FAILED', errorMessage: 'Broken index' }, 409));

    await expect(runScanJob(sendRequest, '{}', { isSuperseded: () => false })).rejects.toThrow('Broken index');
  });

  it('reports the message of a start which is refused', async () => {
    const { sendRequest } = scripted(jsonResponse({ message: 'Unrecognized field "foo"' }, 400));

    await expect(runScanJob(sendRequest, '{}', { isSuperseded: () => false })).rejects.toThrow(
      'Unrecognized field "foo"',
    );
  });

  it('falls back to the status of a refused start without a message', async () => {
    const { sendRequest } = scripted(new Response(null, { status: 500 }));

    await expect(runScanJob(sendRequest, '{}', { isSuperseded: () => false })).rejects.toThrow(
      'Request failed with status 500',
    );
  });

  it('refuses a start without a job location', async () => {
    const { sendRequest } = scripted(new Response(null, { status: 202 }));

    await expect(runScanJob(sendRequest, '{}', { isSuperseded: () => false })).rejects.toThrow(
      'Job did not return its location.',
    );
  });

  it('stops a superseded job and drops it', async () => {
    const { sendRequest, calls } = scripted(started());
    const onStarted = vi.fn();

    const result = await runScanJob(sendRequest, '{}', {
      isSuperseded: () => onStarted.mock.calls.length > 0,
      onStarted,
    });

    expect(result).toBeNull();
    expect(calls.at(-1)).toEqual({ method: 'POST', url: '/scan/jobs/abc/stop' });
  });

  it('runs a repair job without a stop, and drops it silently when superseded', async () => {
    const { sendRequest, calls } = scripted(
      new Response(null, {
        status: 202,
        headers: { Location: 'http://host/polarion/xml-repair/rest/internal/repair/jobs/r1' },
      }),
    );
    const onStarted = vi.fn();
    let superseded = false;

    // the page goes away while the start request is still on its way
    const pending = runRepairJob(sendRequest, '{}', { isSuperseded: () => superseded, onStarted });
    superseded = true;

    expect(await pending).toBeNull();
    expect(onStarted).not.toHaveBeenCalled();
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual(['POST /repair/jobs']);
  });

  it('hands out a stop which asks the job to stop', async () => {
    const { sendRequest, calls } = scripted(started(), new Response(null, { status: 303 }), jsonResponse(SCAN_RESULT));

    await runScanJob(sendRequest, '{}', { isSuperseded: () => false, onStarted: (stop) => stop() });

    expect(calls[1]).toEqual({ method: 'POST', url: '/scan/jobs/abc/stop' });
  });
});

describe('useScanJobState', () => {
  afterEach(async () => {
    await cleanup();
  });

  it('shows progress only for the scan the page still waits for', async () => {
    const { result } = await renderHook(() => useScanJobState());

    result.current.callbacks(() => true).onProgress?.('5 items scanned, 0 with issues');
    result.current.callbacks(() => false).onProgress?.('6 items scanned, 1 with issues');

    await vi.waitFor(() => expect(result.current.progress).toBe('6 items scanned, 1 with issues'));
  });

  it('ignores a stop before the job has started', async () => {
    const { result } = await renderHook(() => useScanJobState());

    result.current.stop();

    expect(result.current.stopping).toBe(false);
    expect(result.current.canStop).toBe(false);
  });
});
