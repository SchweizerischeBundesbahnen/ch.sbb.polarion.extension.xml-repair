import { vi } from 'vitest';

// Mocks the extension's REST layer at the global `fetch` boundary (useRemote -> fetch). Each route
// matches on HTTP method + a URL regex; the first match wins. Unmatched requests resolve to a 404 with
// an errorMessage so a missing mock is obvious in a failing assertion rather than hanging.
//
// A scan or a repair runs as a server job (start, poll, fetch the result). The mock emulates that protocol, so
// a test keeps describing only the outcome: the route matching `POST .../scan/jobs` or `POST .../repair/jobs`
// gives the response the finished job holds. A 2xx outcome finishes the job, any other one fails it with the message of its body.
// While the route's response is pending, the job stays in progress.

export interface Route {
  method?: string;
  match: RegExp;
  /** Static JSON body (200 unless `status` given). */
  json?: unknown;
  status?: number;
  /** Full control: build the Response from the request. May be async, to hold a request open. */
  respond?: (url: string, init?: RequestInit) => Response | Promise<Response>;
  /** For a scan job route: the progress message the job reports while its response is pending. */
  progress?: string;
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const JOB_START = /\/(scan|repair)\/jobs$/;
const JOB = /\/(?:scan|repair)\/jobs\/([^/]+)(\/result|\/stop)?$/;

interface EmulatedJob {
  outcome?: Response;
  progress?: string;
  stopped: boolean;
}

function startJob(
  jobs: Map<string, EmulatedJob>,
  kind: string,
  outcome: Response | Promise<Response>,
  progress: string | undefined,
): Response {
  const id = `job-${jobs.size + 1}`;
  const job: EmulatedJob = { progress, stopped: false };
  jobs.set(id, job);
  void Promise.resolve(outcome).then((response) => {
    job.outcome = response;
  });
  return new Response(null, {
    status: 202,
    headers: { Location: `/polarion/xml-repair/rest/internal/${kind}/jobs/${id}` },
  });
}

async function answerJob(job: EmulatedJob, action: string | undefined): Promise<Response> {
  if (action === '/stop') {
    job.stopped = true;
    return new Response(null, { status: 204 });
  }
  if (!job.outcome) {
    return jsonResponse({ status: 'IN_PROGRESS', progressMessage: job.progress }, 202);
  }
  if (action === '/result') {
    return job.outcome.clone();
  }
  if (job.outcome.ok) {
    return new Response(null, { status: 303 });
  }
  const data = await job.outcome
    .clone()
    .json()
    .catch(() => null);
  return jsonResponse({ status: 'FAILED', errorMessage: data?.errorMessage ?? data?.message }, 409);
}

export type FetchMock = ReturnType<typeof vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>>;

/** Install a fetch mock for the given routes; returns the spy so tests can assert calls. */
export function installFetchMock(routes: Route[]): FetchMock {
  const jobs = new Map<string, EmulatedJob>();
  const fn = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>((input, init) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init?.method ?? 'GET').toUpperCase();
    const jobMatch = JOB.exec(url);
    const job = jobMatch && jobs.get(decodeURIComponent(jobMatch[1]));
    if (jobMatch && job) {
      return answerJob(job, jobMatch[2]);
    }
    for (const route of routes) {
      if ((route.method ?? 'GET').toUpperCase() === method && route.match.test(url)) {
        const response = route.respond ? route.respond(url, init) : jsonResponse(route.json ?? {}, route.status ?? 200);
        const start = method === 'POST' ? JOB_START.exec(url) : null;
        return Promise.resolve(start ? startJob(jobs, start[1], response, route.progress) : response);
      }
    }
    return Promise.resolve(jsonResponse({ errorMessage: `unmocked ${method} ${url}` }, 404));
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}
