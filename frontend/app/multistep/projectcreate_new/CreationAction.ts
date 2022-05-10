import axios from "axios";
import {
  CreationErrorHandler,
  GeneralCreationResult,
} from "../common/CreationErrorHandler";

interface ProjectCreationDoc {
  filename: string;
  destinationStorageId: number;
  title: string;
  obitProject: string | null;
  projectTemplateId: number;
  user: string;
  workingGroupId: number;
  commissionId: number;
  deletable: boolean;
  deepArchive: boolean;
  sensitive: boolean;
  productionOffice: ProductionOffice;
}

interface ProjectCreationResponse extends GeneralCreationResult {
  projectId?: number;
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
    default:
      return CreationErrorHandler(response, "project");
  }
}

export type { ProjectCreationDoc };

export { CreateProject };
