import axios from "axios";

interface ProjectCreationDoc {
  filename: string;
  destinationStorageId: number;
  title: string;
  projectTemplateId: number;
  user: string;
  workingGroupId: number;
  commissionId: number;
  deletable: boolean;
  deepArchive: boolean;
  sensitive: boolean;
  productionOffice: ProductionOffice;
}

interface ProjectCreationResponse {
  createdOk: boolean;
  projectId?: number;
  errorMessage: string;
  shouldRetry: boolean;
}

async function CreateProject(
  source: ProjectCreationDoc
): Promise<ProjectCreationResponse> {
  const response = await axios.put(`/api/project`, source, {
    validateStatus: () => true,
  });

  if (response.status != 200) {
    console.error(
      `Project creation failed: ${response.status} ${response.statusText}`,
      response.data
    );
  }

  switch (response.status) {
    case 200:
      const result = response.data as ProjectCreationResponse;
      return {
        createdOk: true,
        projectId: result.projectId,
        errorMessage: "",
        shouldRetry: false,
      };
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
        errorMessage:
          "A conflict prevented this project from being created. Try changing the project and file names",
      };
    default:
      return {
        createdOk: false,
        shouldRetry: false,
        errorMessage: `An unexpected server error happened: ${response.status} ${response.statusText}.  Your project wasn't created, please contact Multimediatech.`,
      };
  }
}

export type { ProjectCreationDoc };

export { CreateProject };
