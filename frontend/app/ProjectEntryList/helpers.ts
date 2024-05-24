import Axios from "axios";
import axios from "axios";
import {
  SystemNotifcationKind,
  SystemNotification,
} from "@guardian/pluto-headers";

const API = "/api";
const API_PROJECTS = `${API}/project`;
const API_PROJECTS_FILTER = `${API_PROJECTS}/list`;
const API_FILES = `${API}/file`;

declare var deploymentRootPath: string;

interface ProjectsOnPage {
  page?: number;
  pageSize?: number;
  filterTerms?: FilterTerms;
}

interface PlutoFilesAPIResponse<T> {
  files: T;
}

interface PlutoBucketsAPIResponse<T> {
  buckets: T;
}

interface PlutoDeleteJobAPIResponse<T> {
  job_status: T;
}

interface PlutoItemDeleteDataAPIResponse<T> {
  results: T;
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

export const getOpenUrl = async (entry: FileEntry, id: number) => {
  const storageResult = await getStorageData(entry.storage);
  const isAudition = await isAuditionProject(id);
  const auditionPath = await getAssetFolderPath(id);

  const normalisedPath = auditionPath.value.replace(/^\/srv/, "/Volumes");

  const pathToUse = isAudition
    ? normalisedPath
    : storageResult.clientpath
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

export const getSesxProjectTypeIds = async () => {
  try {
    const { data } = await Axios.get(`${API}/projecttype`);
    if (data.status === "ok") {
      return data.result
        .filter(
          (projectType: { fileExtension: string }) =>
            projectType.fileExtension === ".sesx"
        )
        .map((projectType: { id: any }) => projectType.id);
    } else {
      throw new Error("Failed to fetch project types");
    }
  } catch (error) {
    console.error("Error fetching project types:", error);
    throw error;
  }
};

const isAuditionProject = async (id: number) => {
  try {
    const sesxProjectTypeIds = await getSesxProjectTypeIds();
    console.log("sesxProjectTypeIds", sesxProjectTypeIds);
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<Project>>(`${API_PROJECTS}/${id}`);

    if (status === 200) {
      if (sesxProjectTypeIds.includes(result.projectTypeId)) {
        return true;
      }
    } else {
      return false;
    }
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getAssetFolderPath = async (
  id: number
): Promise<ProjectMetadataResponse> => {
  try {
    const {
      status,
      data: { result },
    } = await Axios.get<PlutoApiResponse<ProjectMetadataResponse>>(
      `${API_PROJECTS}/${id}/assetfolder`
    );

    if (status === 200) {
      return result;
    }

    // Handle the non-200 status without throwing an error
    console.error(
      `Could not get asset folder path for project ${id}. ${status}`
    );
    return Promise.reject(
      new Error(`Could not get asset folder path for project ${id}. ${status}`)
    );
  } catch (error) {
    console.error(error);
    return Promise.reject(error);
  }
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

  return getOpenUrl(fileResult[0], id);
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
  backups: boolean,
  pTR: boolean,
  deliverables: boolean,
  sAN: boolean,
  matrix: boolean,
  s3: boolean,
  buckets: string[],
  bucketBooleans: boolean[]
): Promise<void> => {
  try {
    let bucketsArray = `[`;
    for (const bucket of buckets) {
      bucketsArray = bucketsArray + `"${bucket}",`;
    }
    bucketsArray = bucketsArray.slice(0, -1);
    bucketsArray = bucketsArray + `]`;
    let booleansArray = `[`;
    for (const boolean of bucketBooleans) {
      booleansArray = booleansArray + `${boolean},`;
    }
    booleansArray = booleansArray.slice(0, -1);
    booleansArray = booleansArray + `]`;
    const { status } = await Axios.put<PlutoApiResponse<void>>(
      `${API_PROJECTS}/${id}/deleteData`,
      `{"pluto":${pluto},"file":${file},"backups":${backups},"PTR":${pTR},"deliverables":${deliverables},"SAN":${sAN},"matrix":${matrix},"S3":${s3},"buckets":${bucketsArray},"bucketBooleans":${booleansArray}}`,
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

export const getBuckets = async (): Promise<string[]> => {
  try {
    const {
      status,
      data: { buckets },
    } = await Axios.get<PlutoBucketsAPIResponse<string[]>>(`${API}/buckets`);

    if (status === 200) {
      return buckets;
    }

    throw new Error(`Could not get buckets. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getDeleteJob = async (id: number): Promise<string> => {
  try {
    const {
      status,
      data: { job_status },
    } = await Axios.get<PlutoDeleteJobAPIResponse<string>>(
      `${API_PROJECTS}/${id}/deleteJob`
    );

    if (status === 200) {
      return job_status;
    }

    throw new Error(`Could not get job status for project ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getItemsNotDeleted = async (
  id: number
): Promise<ItemsNotDeleted[]> => {
  try {
    const {
      status,
      data: { results },
    } = await Axios.get<PlutoItemDeleteDataAPIResponse<ItemsNotDeleted[]>>(
      `${API_PROJECTS}/${id}/deleteItems`
    );

    if (status === 200) {
      return results;
    }

    throw new Error(
      `Could not get items not deleted for project ${id}. ${status}`
    );
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const getAssetFolderProjectFiles = async (
  id: number
): Promise<AssetFolderFileEntry[]> => {
  const response = await Axios.get<AssetFolderProjectFilesResponse>(
    `${API_PROJECTS}/${id}/assetFolderFiles?allVersions=false`,
    { validateStatus: (s) => s == 200 || s == 404 }
  );
  switch (response.status) {
    case 200:
      return response.data.files;
    case 404:
      throw `The project with id. ${id} does not exist`;
    default:
      throw `Axios returned an unexpected response code ${response.status}`;
  }
};

export const getAssetFolderFileStorageMetadata = async (
  fileId: number
): Promise<Map<string, string>> => {
  const response = await Axios.get<FileMetadataResponse>(
    `${API_FILES}/${fileId}/assetFolderStorageMetadata`,
    { validateStatus: (s) => s == 200 || s == 404 }
  );

  switch (response.status) {
    case 200:
      return new Map(Object.entries(response.data.metadata));
    case 404:
      throw `There is no file with id. ${fileId}`;
    default:
      throw `Axios returned an unexpected response code ${response.status}`;
  }
};

export const getMissingFiles = async (id: number): Promise<MissingFiles[]> => {
  try {
    const {
      status,
      data: { results },
    } = await Axios.get<PlutoItemDeleteDataAPIResponse<MissingFiles[]>>(
      `${API_PROJECTS}/${id}/missingFiles`
    );

    if (status === 200) {
      return results;
    }

    throw new Error(`Could not get missing files for project ${id}. ${status}`);
  } catch (error) {
    console.error(error);
    throw error;
  }
};

export const downloadProjectFile = async (id: number) => {
  const url = `${deploymentRootPath}${API_PROJECTS}/${id}/fileDownload`;
  window.open(url);
};
