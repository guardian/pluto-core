import {
  fetchVaultData,
  loadAllVaultData,
  VaultdoorProjectSummary,
} from "../../app/vaultdoor/vaultdoor";
import fetchMock, { MockResponseInit } from "jest-fetch-mock";

const fakeProject: Project = {
  id: 123,
  projectTypeId: 44,
  title: "test",
  created: "",
  user: "",
  workingGroupId: 1,
  commissionId: 1,
  deletable: false,
  deep_archive: false,
  sensitive: false,
  status: "New",
  productionOffice: "UK",
  confidential: false,
};

describe("fetchVaultData", () => {
  beforeEach(() => {
    fetchMock.doMock();
    fetchMock.mockClear();
  });

  afterEach(() => {
    fetchMock.dontMock();
  });

  it("should return a VaultdoorProjectSummary on success", (done) => {
    const fakeResponse: VaultdoorProjectSummary = {
      total: {
        count: 12345,
        size: 1234567,
      },
    };
    fetchMock.mockResponse((req) => {
      if (
        req.url !=
        "https://vaultdoor-server/api/vault/vault-id-here/projectSummary/123"
      )
        throw `GET request made to incorrect URL ${req.url}`;
      return new Promise<string>((resolve, reject) =>
        resolve(JSON.stringify(fakeResponse))
      );
    });

    fetchVaultData("https://vaultdoor-server/", fakeProject, "vault-id-here")
      .then((result) => {
        expect(result?.total.count).toEqual(12345);
        expect(result?.total.size).toEqual(1234567);
        done();
      })
      .catch((err) => done.fail(err));
  });

  it("should return undefined on error", (done) => {
    fetchMock.mockResponse((req) => {
      if (
        req.url !=
        "https://vaultdoor-server/api/vault/vault-id-here/projectSummary/123"
      )
        throw `GET request made to incorrect URL ${req.url}`;
      return new Promise<MockResponseInit>((resolve, reject) =>
        resolve({
          body: `{"status":"error","detail":"kaboom"}`,
          status: 500,
          statusText: "Server Error",
        })
      );
    });

    fetchVaultData("https://vaultdoor-server/", fakeProject, "vault-id-here")
      .then((result) => {
        expect(result).toBeFalsy();
        done();
      })
      .catch((err) => done.fail(err));
  });
});

describe("loadAllVaultData", () => {
  beforeEach(() => {
    fetchMock.doMock();
    fetchMock.mockClear();
  });

  afterEach(() => {
    fetchMock.dontMock();
  });

  it("should make a fetch request for every vault in the list", (done) => {
    const vaultList: Array<VaultDescription> = [
      {
        vaultId: "vault-id-one",
        name: "Vault one",
      },
      {
        vaultId: "vault-id-two",
        name: "Vault two",
      },
    ];
    let vaultsHit: string[] = [];

    const fakeResponseVault1: VaultdoorProjectSummary = {
      total: {
        count: 12345,
        size: 1234567,
      },
    };

    const fakeResponseVault2: VaultdoorProjectSummary = {
      total: {
        count: 234,
        size: 567,
      },
    };
    fetchMock.mockResponse((req) => {
      if (!req.url.startsWith("https://vaultdoor-server/api/vault/vault-id-"))
        throw `GET request made to incorrect URL ${req.url}`;
      const lastUrlPart = req.url.split("/")[-1];
      vaultsHit = vaultsHit.concat(lastUrlPart);

      const outputData = req.url.includes("vault-id-one")
        ? fakeResponseVault1
        : fakeResponseVault2;
      return new Promise<string>((resolve, reject) =>
        resolve(JSON.stringify(outputData))
      );
    });

    loadAllVaultData("https://vaultdoor-server/", fakeProject, vaultList)
      .then((results) => {
        expect(results.length).toEqual(2);
        const vaultOneInfo = results[0];
        expect(vaultOneInfo.totalSize).toEqual(1234567);
        expect(vaultOneInfo.fileCount).toEqual(12345);
        expect(vaultOneInfo.vaultName).toEqual("Vault one");

        const vaultTwoInfo = results[1];
        expect(vaultTwoInfo.totalSize).toEqual(567);
        expect(vaultTwoInfo.fileCount).toEqual(234);
        expect(vaultTwoInfo.vaultName).toEqual("Vault two");
        done();
      })
      .catch((err) => done.fail(err));
  });
});
