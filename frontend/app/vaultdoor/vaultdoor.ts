import { authenticatedFetch } from "../ProjectEntryList/auth";

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
  gnmType?: Record<string, VaultdoorSummaryEntry>;
  fileType?: Record<string, VaultdoorSummaryEntry>;
  mediaType?: Record<string, VaultdoorSummaryEntry>;
  hiddenFile?: Record<string, VaultdoorSummaryEntry>;
  gnmProject?: Record<string, VaultdoorSummaryEntry>;
  total: VaultdoorSummaryEntry;
}

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
      const bodyText = await response.text();
      //FIXME: should validate the json data format here
      return JSON.parse(bodyText) as VaultdoorProjectSummary;
    default:
      const errorContent = await response.text();
      console.error(errorContent);
      return;
  }
}

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
