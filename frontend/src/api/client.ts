// Thin fetch wrapper: JSON in/out, bearer token, and readable errors.
// The backend returns errors either as plain text ("Room is already booked…")
// or, for validation failures, as a JSON map {field: message}.

const TOKEN_KEY = 'stayline.token';

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable (private mode) - the session just won't persist */
  }
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Called on 401 so the app can drop an expired session. */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Please check the details and try again.',
  401: 'Please sign in to continue.',
  403: "You don't have access to that.",
  404: "We couldn't find that.",
  409: 'That conflicts with the current state. Refresh and try again.',
  429: "You're going a bit fast. Wait a minute and try again.",
  503: 'This service is temporarily unavailable.',
};

type Query = Record<string, string | number | undefined | null>;

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Query;
  headers?: Record<string, string>;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  }

  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, "Can't reach the server. Is the backend running?");
  }

  const text = await response.text();

  if (!response.ok) {
    if (response.status === 401 && token && onUnauthorized) onUnauthorized();
    let message = FALLBACK_MESSAGES[response.status] ?? `Something went wrong (${response.status}).`;
    let fieldErrors: Record<string, string> = {};
    if (text) {
      try {
        const parsed: unknown = JSON.parse(text);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          const values = Object.values(parsed as Record<string, unknown>);
          if (values.every((v) => typeof v === 'string')) {
            fieldErrors = parsed as Record<string, string>;
            message = values.join(' ');
          }
        }
      } catch {
        // Plain-text message from the API; short ones are safe to show as-is
        if (text.length < 300 && !text.trimStart().startsWith('<')) message = text;
      }
    }
    throw new ApiError(response.status, message, fieldErrors);
  }

  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}
