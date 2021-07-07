import { AxiosResponse } from "axios";

interface GeneralCreationResult {
  errorMessage: string;
  shouldRetry: boolean;
  createdOk: boolean;
}

function CreationErrorHandler(response: AxiosResponse, thingClass: string) {
  switch (response.status) {
    case 400:
      const rqErr = response.data as GenericErrorResponse;
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `Bad request - ${rqErr.detail}. You should go back and double-check all of the values you put in to create the project.`,
      };
    case 500:
      const intErr = response.data as GenericErrorResponse;
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `A server error occurred: ${intErr.detail}. You should report this to multimediatech@theguardian.com.`,
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
        errorMessage: `A conflict prevented this ${thingClass} from being created. Try changing the name`,
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
