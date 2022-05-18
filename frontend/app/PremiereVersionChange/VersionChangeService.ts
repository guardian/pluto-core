import axios from "axios";

async function lookupVersion(
  clientVersionString: string
): Promise<PremiereVersionTranslation> {
  try {
    const response = await axios.get<PremiereVersionTranslationResponse>(
      `/api/premiereVersion/${encodeURIComponent(clientVersionString)}`
    );
    if (response.data.count == 0) {
      return Promise.reject(
        `We did not recognise your installed version of Premiere, ${clientVersionString}. Please report this to Multimedia tech.`
      );
    } else {
      return response.data.result[0];
    }
  } catch (err) {
    console.error(
      `Could not look up client version string ${clientVersionString}: `,
      err
    );
    return Promise.reject(
      "There was a server error trying to find your installed version of Premiere, please retry in a few minutes."
    );
  }
}

function getBasename(filePath: string): string {
  const xtractor = /\/([^\/]+)$/;
  const result = xtractor.exec(filePath);
  if (result) {
    return result[1]; //first capture group
  } else {
    console.log(`warning, file path ${filePath} has no path part`);
    return filePath;
  }
}

async function lookupProjectFile(filePath: string): Promise<FileEntry> {
  try {
    const queryData = { filePath: getBasename(filePath), match: "W_EXACT" };
    const response = await axios.put<ObjectListResponse<FileEntry>>(
      "/api/file/list",
      queryData
    );
    if (response.data.count == 0) {
      return Promise.reject(
        "Could not find this project file in the system. Please report this error to Multimedia tech, along with the page URL above."
      );
    } else {
      const liveProjects = response.data.result.filter((f) => !f.backupOf);
      return liveProjects[0];
    }
  } catch (err) {
    console.error("Could not look up project file information: ", err);
    return Promise.reject(
      "There was a server error trying to find your project. Please try again in a few minutes."
    );
  }
}

/**
 * Asks the backend to change the version in the given premiere file to suit a given premiere version
 * @param fileId numeric identifier of the file to change
 * @param requiredVersion user's installed premiere version, in the form x.y.z
 * @returns Promise<FileEntry> Promise that resolves to an updated FileEntry object for the updated file entry. On error, the promise rejects.
 */
async function performConversion(
  fileId: number,
  requiredVersion: string
): Promise<FileEntry> {
  try {
    const response = await axios.post(
      `/api/file/${fileId}/changePremiereVersion?requiredDisplayVersion=${encodeURIComponent(
        requiredVersion
      )}`,
      null,
      { validateStatus: (status) => status === 200 || status === 400 }
    );
    switch (response.status) {
      case 200: //conversion was done ok
        const data = response.data as ConversionResponse;
        return data.entry;
      case 400: //there was a problem with the input
        const problem = response.data as GenericErrorResponse;
        return Promise.reject(
          problem.detail ?? "Server returned an unknown error"
        );
      default:
        //the construct of the axios call above should guarantee that only 200 or 400 responses come through here
        return Promise.reject(
          `Internal error, unexpected return code ${response.status} should not happen`
        );
    }
  } catch (err) {
    console.error("Could not request conversion: ", err);
    return Promise.reject(
      "Unable to request conversion, please see the javascript console log for details."
    );
  }
}

export { lookupVersion, lookupProjectFile, performConversion, getBasename };
