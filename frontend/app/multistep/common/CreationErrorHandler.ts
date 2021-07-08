import { AxiosResponse } from "axios";

interface GeneralCreationResult {
  errorMessage: string;
  shouldRetry: boolean;
  createdOk: boolean;
}

function CreationErrorHandler(response: AxiosResponse, thingClass: string) {
  const rqErr = response.data as GenericErrorResponse;
  const errorDetail = rqErr.hasOwnProperty("detail")
    ? rqErr.detail
    : "(no details)"; //handle a malformatted error

  switch (response.status) {
    case 400:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `Bad request - ${errorDetail}. You should go back and double-check all of the values you put in and report this to multimediatech@theguardian.com`,
      };
    case 500:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `A server error occurred: ${errorDetail}. You should report this to multimediatech@theguardian.com.`,
      };
    case 502 | 503 | 504:
      return {
        createdOk: false,
        shouldRetry: true,
        errorMessage:
          "The server is not responding. Usually it will start again in a minute or so.  Retrying automatically...",
      };
    case 401 | 403:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: "Permission denied. Maybe your login expired?",
      };
    case 409:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `A conflict prevented this ${thingClass} from being created. Try changing the name or file`,
      };
    default:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `An unexpected server error happened: ${response.status} ${response.statusText}.  Your ${thingClass} wasn't created, please contact Multimediatech.`,
      };
  }
}

export type { GeneralCreationResult };
export { CreationErrorHandler };
