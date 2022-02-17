import Axios, { AxiosResponse } from "axios";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_PROJECTS_FILTER = `${API_PROJECTS}/list`;

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

export const openProject = async (id: number) => {
  const fileResult = await getFileData(id);
  const storageResult = await getStorageData(fileResult[0].storage);
  const pathToUse = storageResult.clientpath ? storageResult.clientpath : storageResult.rootpath;
  console.log("About to access a project with this path: " + pathToUse);
  console.log(
      "About to access a project with this file name: " + fileResult[0].filepath
  );
  window.open(
      `pluto:openproject:${pathToUse}/${fileResult[0].filepath}`,
      "_blank"
  );
};
