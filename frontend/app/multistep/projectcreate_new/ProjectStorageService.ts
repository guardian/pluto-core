import axios from "axios";

/**
 * asks the server for the default project storage id. Returns a failed promise if there is none set or a processing error.
 */
async function getProjectsDefaultStorageId(): Promise<number> {
  const response = await axios.get<PlutoApiResponse<PlutoDefault>>(
    "/api/default/project_storage_id"
  );
  return parseInt(response.data.result.value);
}

export { getProjectsDefaultStorageId };
