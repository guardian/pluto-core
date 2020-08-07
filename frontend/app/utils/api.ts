import Axios, { AxiosResponse } from "axios";

const API = "/api";
const API_IS_LOGGED_IN = `${API}/isLoggedIn`;

export const isLoggedIn = async (): Promise<PlutoUser> => {
  try {
    const { status, data } = await Axios.get<PlutoUser>(`${API_IS_LOGGED_IN}`);

    if (status === 200) {
      return data;
    }

    throw new Error(`Could not retrieve who is logged in. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

interface RefreshTokenResponse {
  access_token: string;
  refresh_token: string;
}

interface PlutoConfig {
  tokenUri: string;
  clientId: string;
}

/**
 * Refreshes a token e.g. an expired token and returns an active token.
 */
export const refreshToken = async (
  plutoConfig: PlutoConfig
): Promise<RefreshTokenResponse> => {
  const { tokenUri, clientId } = plutoConfig;
  const postdata: { [key: string]: string } = {
    grant_type: "refresh_token",
    client_id: clientId,
    refresh_token: sessionStorage.getItem("pluto:refresh-token") as string,
  };

  const content_elements = Object.keys(postdata).map(
    (k) => k + "=" + encodeURIComponent(postdata[k])
  );
  const bodyContent = content_elements.join("&");

  try {
    const response = await fetch(tokenUri, {
      method: "POST",
      body: bodyContent,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    if (response.status === 200) {
      const data = await response.json();
      return data;
    }

    throw new Error(`Could not fetch refresh token`);
  } catch (error) {
    throw error;
  }
};

interface FailedQueue {
  resolve: (value?: unknown) => void;
  reject: (reason?: any) => void;
}

let isRefreshing = false;
let failedQueue: FailedQueue[] = [];

const processQueue = (error: any, token: string | null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });

  failedQueue = [];
};

/**
 * Retries the API call with a refresh token on 401 Unauthorized.
 */
export const handleUnauthorized = async (
  plutoConfig: PlutoConfig,
  error: any,
  failureCallback: () => void
): Promise<AxiosResponse | void> => {
  const originalRequest = error.config;

  // (Backend returns 403 Forbidden when a token is expired instead of 401 Unauthorized
  // therefore the check of 403 Forbidden)
  if (
    !originalRequest._retry &&
    (error.response.status === 401 || error.response.status === 403)
  ) {
    // Handle several incoming http requests that fails on 401 Unauthorized
    // Therefore create a queue of the failing requests
    // and resolve them when refresh token is fetched
    // or reject them if failed to fetch the request token.
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return Axios(originalRequest);
        })
        .catch((error) => {
          return Promise.reject(error);
        });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const data = await refreshToken(plutoConfig);

      sessionStorage.setItem("pluto:access-token", data.access_token);
      sessionStorage.setItem("pluto:refresh-token", data.refresh_token);

      originalRequest.headers.Authorization = `Bearer ${data.access_token}`;
      processQueue(null, data.access_token);
      return Axios(originalRequest);
    } catch (error) {
      failureCallback();
      processQueue(error, null);
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  }
};
