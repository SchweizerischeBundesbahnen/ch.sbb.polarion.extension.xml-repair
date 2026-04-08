import { useCallback } from 'react';

const REST_PATH = '/polarion/xml-repair/rest';

interface RequestParams {
  method: string;
  url: string;
  body?: string;
  contentType?: string;
}

export default function useRemote() {
  const sendRequest = useCallback(({ method, url, body, contentType }: RequestParams): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (contentType) {
      headers['Content-Type'] = contentType;
    }
    if (import.meta.env.VITE_BEARER_TOKEN) {
      headers['Authorization'] = `Bearer ${import.meta.env.VITE_BEARER_TOKEN}`;
    }

    const apiPath = import.meta.env.VITE_BEARER_TOKEN ? '/api' : '/internal';

    return fetch(`${REST_PATH}${apiPath}${url}`, {
      method,
      mode: 'cors',
      cache: 'no-cache',
      headers,
      body,
    }).catch(() => {
      const errorHeaders = {
        status: 503,
        'Content-Type': 'application/json',
      };
      const errorResponse = new Response(
        JSON.stringify({ message: 'Network error occurred. Be sure Polarion is started and accessible.' }),
        errorHeaders,
      );
      return Promise.resolve(errorResponse);
    });
  }, []);

  return { sendRequest };
}
