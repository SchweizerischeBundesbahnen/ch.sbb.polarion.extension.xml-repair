import { useCallback, useMemo } from 'react';
import type { Revision } from '@grigoriev/react-sbb-polarion';
import useRemote from './useRemote';

/** The named-settings feature id (matches the JSP's `setting` and the backend settings path). */
const FEATURE = 'authorization';
/** The single, always-present setting name the generic framework uses when there are no named configs. */
const DEFAULT_NAME = 'Default';

/** Content of the repair-authorization setting: the roles allowed to run repairs. */
export interface AuthorizationSettings {
  globalRoles: string[];
  projectRoles: string[];
}

/** All roles available to grant in the current scope (from the extension's /roles endpoint). */
export interface RolesInfo {
  globalRoles: string[];
  projectRoles: string[];
}

/** Extract a human-readable error message from a failed Response (mirrors ExtensionContext.callAsync). */
async function errorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '');
  if (text) {
    try {
      const parsed = JSON.parse(text);
      if (parsed?.message) return parsed.message;
      if (parsed?.errorMessage) return parsed.errorMessage;
    } catch {
      return text;
    }
  }
  return `HTTP ${response.status}`;
}

async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return (await response.json()) as T;
}

async function okOrThrow(response: Response): Promise<void> {
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
}

/**
 * REST helpers for the Repair Authorization page: this extension's `/roles` endpoint plus the generic
 * single-setting endpoints (content / default-content / revisions) for the always-present `Default`
 * setting. Built on `useRemote`, so it uses the session `/internal` endpoints in Polarion and the
 * token `/api` endpoints in `vite dev`.
 */
export default function useAuthorization() {
  const { sendRequest } = useRemote();

  const settingsPath = useCallback((suffix: string): string => `/settings/${FEATURE}${suffix}`, []);

  const loadRoles = useCallback(
    (scope: string): Promise<RolesInfo> =>
      sendRequest({ method: 'GET', url: `/roles?scope=${encodeURIComponent(scope)}` }).then((r) =>
        jsonOrThrow<RolesInfo>(r),
      ),
    [sendRequest],
  );

  const loadContent = useCallback(
    (scope: string, revision?: string): Promise<AuthorizationSettings> => {
      let url = settingsPath(`/names/${DEFAULT_NAME}/content?scope=${encodeURIComponent(scope)}`);
      if (revision) {
        url += `&revision=${encodeURIComponent(revision)}`;
      }
      return sendRequest({ method: 'GET', url }).then((r) => jsonOrThrow<AuthorizationSettings>(r));
    },
    [sendRequest, settingsPath],
  );

  const saveContent = useCallback(
    (scope: string, content: AuthorizationSettings): Promise<void> =>
      sendRequest({
        method: 'PUT',
        url: settingsPath(`/names/${DEFAULT_NAME}/content?scope=${encodeURIComponent(scope)}`),
        contentType: 'application/json',
        body: JSON.stringify(content),
      }).then(okOrThrow),
    [sendRequest, settingsPath],
  );

  const loadDefaultContent = useCallback(
    (): Promise<AuthorizationSettings> =>
      sendRequest({ method: 'GET', url: settingsPath('/default-content') }).then((r) =>
        jsonOrThrow<AuthorizationSettings>(r),
      ),
    [sendRequest, settingsPath],
  );

  const loadRevisions = useCallback(
    (name: string, scope: string): Promise<Revision[]> =>
      sendRequest({
        method: 'GET',
        url: settingsPath(`/names/${encodeURIComponent(name)}/revisions?scope=${encodeURIComponent(scope)}`),
      }).then((r) => jsonOrThrow<Revision[]>(r)),
    [sendRequest, settingsPath],
  );

  return useMemo(
    () => ({ loadRoles, loadContent, saveContent, loadDefaultContent, loadRevisions, defaultName: DEFAULT_NAME }),
    [loadRoles, loadContent, saveContent, loadDefaultContent, loadRevisions],
  );
}
