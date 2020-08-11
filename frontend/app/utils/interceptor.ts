import axios, { AxiosResponse } from "axios";
import qs from "querystring";

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

  try {
    const response = await axios.post(tokenUri, qs.stringify(postdata), {
      headers: {
        Accept: "application/json",
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    if (response.status === 200) {
      const data = await response.data;

      return data;
    }

    throw new Error(`Could not fetch refresh token`);
  } catch (error) {
    return Promise.reject(error);
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
          return axios(originalRequest);
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
      return axios(originalRequest);
    } catch (error) {
      if (failureCallback) {
        failureCallback();
      }

      processQueue(error, null);
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  }
};
