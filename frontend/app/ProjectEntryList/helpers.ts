import Axios from "axios";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_PROJECTS_FILTER = `${API_PROJECTS}/list`;
const API_FILES = `${API}/file`;

interface ProjectsOnPage {
  page?: number;
  pageSize?: number;
  filterTerms?: FilterTerms;
}

interface PlutoFilesAPIResponse<T> {
  files: T;
}

export const getProjectsOnPage = async ({
  page = 0,
  pageSize = 25,
  filterTerms,
}: ProjectsOnPage): Promise<Project[]> => {
  try {
    const {
      status,
      data: { result },
    } = filterTerms
      ? await Axios.put<PlutoApiResponse<Project[]>>(
          `${API_PROJECTS_FILTER}?startAt=${
            page * pageSize
          }&length=${pageSize}`,
          filterTerms
        )
      : await Axios.get<PlutoApiResponse<Project[]>>(
          `${API_PROJECTS}?startAt=${page * pageSize}&length=${pageSize}`
        );

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not retrieve projects. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getProject = async (id: number): Promise<Project> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<Project>>(`${API_PROJECTS}/${id}`);

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not get project ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getFileStorageMetadata = async (
  fileId: number
): Promise<Map<string, string>> => {
  const response = await Axios.get<FileMetadataResponse>(
    `${API_FILES}/${fileId}/storageMetadata`,
    { validateStatus: (s) => s == 200 || s == 404 }
  );

  switch (response.status) {
    case 200:
      return new Map(Object.entries(response.data.metadata));
    case 404:
      throw `There is no file with id ${fileId}`;
    default:
      //this should not happen
      throw `axios returned an unexpected response code ${response.status}`;
  }
};

export const getProjectFiles = async (id: number): Promise<FileEntry[]> => {
  const response = await Axios.get<ProjectFilesResponse>(
    `${API_PROJECTS}/${id}/files?allVersions=true`,
    { validateStatus: (s) => s == 200 || s == 404 }
  );
  switch (response.status) {
    case 200:
      return response.data.files;
    case 404:
      throw `The project with id ${id} does not exist`;
    default:
      //this should not happen
      throw `axios returned an unexpected response code ${response.status}`;
  }
};

export const getProjectType = async (id: number): Promise<ProjectType> => {
  const response = await Axios.get<PlutoApiResponse<ProjectType>>(
    `/api/projecttype/${id}`
  );
  return response.data.result;
};

export const getProjectByVsid = async (vsid: string): Promise<Project> => {
  const response = await Axios.get<PlutoApiResponse<Project>>(
    `${API_PROJECTS}/vsid/${vsid}`
  );
  //if status!=200 we raise
  return response.data.result;
};

export const updateProject = async (project: Project): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${project.id}`,
      project
    );

    if (status !== 200) {
      throw new Error(
        `Could not update project ${project.id}: server said ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const updateProjectOpenedStatus = async (id: number): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${id}/wasopened`
    );

    if (status !== 200) {
      throw new Error(
        `Could not update project opened status ${id}. ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const setProjectStatusToKilled = async (id: number): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${id}/status`,
      { status: "Killed" }
    );

    if (status !== 200) {
      throw new Error(`Could not update project status ${id}. ${status}`);
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

//Calls the backend to retrieve files associated with the given project id. that are not backups.
export const getFileData = async (id: number): Promise<FileEntry[]> => {
  try {
    const {
      status,
      data: { files },
    } = await Axios.get<PlutoFilesAPIResponse<FileEntry[]>>(
      `${API_PROJECTS}/${id}/files`
    );

    if (status === 200) {
      return files;
    }

    throw new Error(`Could not get project data for project ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getStorageData = async (id: number): Promise<StorageEntry> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<StorageEntry>>(`${API}/storage/${id}`);

    if (status === 200) {
      return result;
    }

    throw new Error(`Could not get storage data for storage ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

//Sends a custom URL to PlutoHelperAgent which runs on the user's local machine. The URL contains the path of the project to open.
export const openProject = async (id: number) => {
  const fileResult = await getFileData(id);
  const storageResult = await getStorageData(fileResult[0].storage);
  const pathToUse = storageResult.clientpath
    ? storageResult.clientpath
    : storageResult.rootpath;
  window.open(
    `pluto:openproject:${pathToUse}/${fileResult[0].filepath}`,
    "_blank"
  );
};
