import { authenticatedFetch } from "../common/auth";

interface VaultState {
  vaultName: string;
  fileCount: number;
  totalSize: number;
}

interface VaultdoorSummaryEntry {
  count: number;
  size: number;
}

interface VaultdoorProjectSummary {
  gnmType?: Map<string, VaultdoorSummaryEntry>;
  fileType?: Map<string, VaultdoorSummaryEntry>;
  mediaType?: Map<string, VaultdoorSummaryEntry>;
  hiddenFile?: Map<string, VaultdoorSummaryEntry>;
  gnmProject?: Map<string, VaultdoorSummaryEntry>;
  total: VaultdoorSummaryEntry;
}

/**
 * fetches ProjectSummaryInformation from Vaultdoor for the given project in the given vault
 * @param vaultdoorURL base URL for VaultDoor. Must end with a /.
 * @param project [[Project]] record representing the project whose information we need
 * @param vaultId ID of the vault to check
 * @returns a Promise containing either the VaultdoorProjectSummary information or undefined if the server errored. Can
 * also throw an exception if fetch() detects a network error.
 */
async function fetchVaultData(
  vaultdoorURL: string,
  project: Project,
  vaultId: string
): Promise<VaultdoorProjectSummary | undefined> {
  console.log(
    `Checking information about project ${project.id} in vault ${vaultId} at ${vaultdoorURL}`
  );
  const response = await authenticatedFetch(
    `${vaultdoorURL}api/vault/${vaultId}/projectSummary/${project.id}`,
    {}
  );
  switch (response.status) {
    case 200:
      const content = await response.json();
      //FIXME: should validate the json data format here
      return content as VaultdoorProjectSummary;
    default:
      const errorContent = await response.text();
      console.error(errorContent);
      return;
  }
}

/**
 * fetches ProjectSummaryInformation for every vault identified in VaultList by performing multiple parallel
 * fetches via `fetchVaultData`
 * @param vaultDoorURL  base URL for VaultDoor. Must end with a /.
 * @param project [[Project]] record representing the project whose information we need
 * @param vaultList Array of VaultDescription instances indicating the vaults to query
 * @returns a Promise containing a VaultState record for each value of the incoming `vaultList` parameter.  If a vault
 * returns an error that does _not_ cause `fetchVaultData` to reject the promise, then that entry has 0 filecount and totalsize.
 * If an error occurs that _does_ cause `fetchVaultData` to reject, then the entire loadAllVaultData operation fails. This should be
 * caught by the caller.
 */
async function loadAllVaultData(
  vaultDoorURL: string,
  project: Project,
  vaultList: Array<VaultDescription>
): Promise<VaultState[]> {
  const rawData = await Promise.all(
    vaultList.map((vault) =>
      fetchVaultData(vaultDoorURL, project, vault.vaultId)
    )
  );

  return rawData.map((projectSummary, idx) => {
    if (projectSummary) {
      return {
        vaultName: vaultList[idx].name,
        fileCount: projectSummary.total.count,
        totalSize: projectSummary.total.size,
      };
    } else {
      return {
        vaultName: vaultList[idx].name,
        fileCount: 0,
        totalSize: 0,
      };
    }
  });
}

export type { VaultState, VaultdoorProjectSummary, VaultdoorSummaryEntry };
export { fetchVaultData, loadAllVaultData };
