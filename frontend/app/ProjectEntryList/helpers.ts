import Axios from "axios";
import axios from "axios";
import { SystemNotifcationKind, SystemNotification } from "pluto-headers";

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
}: ProjectsOnPage): Promise<[Project[], number]> => {
  try {
    const {
      status,
      data: { result },
      data: { count },
    } = filterTerms
      ? await Axios.put<PlutoApiResponseWithCount<Project[]>>(
          `${API_PROJECTS_FILTER}?startAt=${
            page * pageSize
          }&length=${pageSize}`,
          filterTerms
        )
      : await Axios.get<PlutoApiResponseWithCount<Project[]>>(
          `${API_PROJECTS}?startAt=${page * pageSize}&length=${pageSize}`
        );

    if (status === 200) {
      return [result, count];
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

export const translatePremiereVersion = async (
  internalVersion: number,
  silenceNotifications: boolean
): Promise<string | undefined> => {
  try {
    const response = await axios.get(
      `/api/premiereVersion/internal/${internalVersion}`,
      { validateStatus: (status) => status === 200 || status === 404 }
    );
    switch (response.status) {
      case 200:
        const content = response.data as {
          version: PremiereVersionTranslation;
        };
        console.log(
          `Premiere version ${internalVersion} corresponds to ${content.version.name} ${content.version.displayedVersion}`
        );
        return content.version.displayedVersion;
      case 404:
        console.warn(
          `Premiere version ${internalVersion} is not known to us, going to attempt to open blind`
        );
        if (!silenceNotifications) {
          //set when testing, as this borks if SystemNotificationComponent is not intialised/rendered
          SystemNotification.open(
            SystemNotifcationKind.Warning,
            "Did not recognise Premiere version, will attempt to open anyway"
          );
        }
        return undefined;
    }
  } catch (err) {
    console.error(
      `Could not look up premiere version ${internalVersion}: `,
      err
    );
    if (!silenceNotifications) {
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Could not look up premiere version, will attempt to open anyway"
      );
    }
    return undefined;
  }
};

export const getOpenUrl = async (entry: FileEntry) => {
  const storageResult = await getStorageData(entry.storage);
  const pathToUse = storageResult.clientpath
    ? storageResult.clientpath
    : storageResult.rootpath;

  const premiereDisplayVersion = entry.premiereVersion
    ? await translatePremiereVersion(entry.premiereVersion, false)
    : undefined;

  const versionPart = premiereDisplayVersion
    ? `?premiereVersion=${premiereDisplayVersion}`
    : "";
  return `pluto:openproject:${pathToUse}/${entry.filepath}${versionPart}`;
};

export const getOpenUrlForId = async (id: number) => {
  const fileResult = await getFileData(id);

  if (fileResult.length == 0) {
    SystemNotification.open(
      SystemNotifcationKind.Error,
      "This project has no suitable files"
    );
    return;
  }

  return getOpenUrl(fileResult[0]);
};

/**
 * Sends a custom URL to PlutoHelperAgent which runs on the user's local machine. The URL contains the path of the project to open.
 * @param id - numeric file ID representing the project to be opened. This will derive the most recent suitable FileEntry and use that
 *            to open the project.
 * @returns - nothing; runs window.open which interrupts the running javascript and opens a new tab
 */
export const openProject = async (id: number) => {
  const url = await getOpenUrlForId(id);
  window.open(url, "_blank");
};

export const getSimpleProjectTypeData = async () => {
  const response = await Axios.get(`${API}/projecttype`, {
    validateStatus: (s) => s == 200,
  });

  switch (response.status) {
    case 200:
      const searchStrings = [
        "Premiere",
        "Cubase",
        "After Effects",
        "Audition",
        "Prelude",
        "Migrated",
      ];
      let typeData: any = {};
      for (const type of response.data.result) {
        for (const currentString of searchStrings) {
          if (type.name.includes(currentString)) {
            typeData[type.id] = currentString;
          }
        }
      }
      return typeData;
    default:
      throw `Axios returned an unexpected response code ${response.status}`;
  }
};

export const startDelete = async (
  id: number,
  pluto: boolean,
  file: boolean,
  deliverables: boolean,
  sAN: boolean,
  matrix: boolean,
  s3: boolean
): Promise<void> => {
  try {
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${id}/deleteData`,
      `{"pluto":${pluto},"file":${file},"deliverables":${deliverables},"SAN":${sAN},"matrix":${matrix},"S3":${s3}}`,
      {
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    if (status !== 200) {
      throw new Error(
        `Could not start deletion of data for project ${id}: server said ${status}`
      );
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};
