import axios from "axios";
import {
  CreationErrorHandler,
  GeneralCreationResult,
} from "../common/CreationErrorHandler";

interface StorageCreationDoc {
  rootpath: string;
  clientpath: string | undefined;
  storageType: string;
  host: string | undefined;
  port: number | undefined;
  user: string | undefined;
  password: string | undefined;
  device: string | undefined;
  supportsVersions: boolean;
  nickname: string | undefined;
}

interface StorageCreationResult extends GeneralCreationResult {
  storageId?: number;
}

function portNumber(str: string): number | undefined {
  try {
    const numericValue = parseInt(str, 10);
    if (numericValue == 0) {
      return undefined;
    } else {
      return numericValue;
    }
  } catch (err) {
    console.warn("Invalid (non-numeric) port value given: ", str);
    return undefined;
  }
}

function MakeStorageCreationDoc(
  rootpath: string,
  clientpath: string,
  storageType: string,
  host: string,
  port: number,
  user: string,
  password: string,
  device: string,
  supportsVersions: boolean,
  nickname: string
): StorageCreationDoc {
  return {
    rootpath: rootpath,
    clientpath: clientpath == "" ? undefined : clientpath,
    storageType: storageType,
    host: host == "" ? undefined : host,
    port: port == 0 ? undefined : port,
    user: user == "" ? undefined : user,
    password: password == "" ? undefined : password,
    device: device == "" ? undefined : device,
    supportsVersions: supportsVersions,
    nickname: nickname == "" ? undefined : nickname,
  };
}

async function CreateStorage(
  source: StorageCreationDoc,
  existingId: number | undefined
): Promise<StorageCreationResult> {
  const url = existingId ? `/api/storage/${existingId}` : "/api/storage";

  const response = await axios.put<GenericCreateResponse>(url, source, {
    validateStatus: () => true,
  });

  if (response.status != 200) {
    console.warn(
      "Could not create storage: server returned ",
      response.status,
      " ",
      response.statusText,
      " ",
      response.data
    );
  }

  switch (response.status) {
    case 200:
      return {
        createdOk: true,
        storageId: response.data.id,
        errorMessage: "",
        shouldRetry: false,
      };
    default:
      return CreationErrorHandler(response, "project");
  }
}

export { MakeStorageCreationDoc, CreateStorage };
