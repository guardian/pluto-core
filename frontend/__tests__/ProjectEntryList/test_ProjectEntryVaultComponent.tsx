import React from "react";
import fetchMock from "jest-fetch-mock";
import { mount, shallow } from "enzyme";
import ProjectEntryVaultComponent from "../../app/ProjectEntryList/ProjectEntryVaultComponent";
import { VaultdoorProjectSummary } from "../../app/vaultdoor/vaultdoor";
import { act } from "react-dom/test-utils";

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
};

(global as any).vaultdoorURL = "https://vaultdoor-server/";

describe("ProjectEntryVaultComponent", () => {
  beforeEach(() => {
    fetchMock.doMock();
    fetchMock.mockClear();
  });

  afterEach(() => {
    fetchMock.dontMock();
  });

  it("should not load in data at first mount", (done) => {
    fetchMock.mockResponse((req) => {
      return Promise.reject(
        `Request was made to ${req.url} but none was expected`
      );
    });

    const rendered = mount(
      <ProjectEntryVaultComponent project={fakeProject} />
    );

    window.setTimeout(() => {
      expect(rendered.find("#vaults-table").length).toEqual(0);
      done();
    }, 5000); //we need a timeout, since we are not firing any evets we can wait for we should just be able
    //to assert that no ajax request were made in this timeframe.
  });

  it("should load in data when opened", async (done) => {
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
      if (req.url == "https://vaultdoor-server/api/vault") {
        return Promise.resolve<string>(JSON.stringify(vaultList));
      }

      if (!req.url.startsWith("https://vaultdoor-server/api/vault/vault-id-"))
        throw `GET request made to incorrect URL ${req.url}`;

      const outputData = req.url.includes("vault-id-one")
        ? fakeResponseVault1
        : fakeResponseVault2;
      return new Promise<string>((resolve, reject) =>
        resolve(JSON.stringify(outputData))
      );
    });

    const rendered = mount(
      <ProjectEntryVaultComponent project={fakeProject} />
    );
    //console.log("initial render done");

    await act(async () => {
      const expanderButton = rendered.find("button#archive-expander-button");
      //console.log("simulating click...");
      expanderButton.simulate("click");
    });

    //console.log("update should be done");
    rendered.update(); //update the _wrapper_ from the new _component_ state.

    //console.log(rendered.html());
    const tableRows = rendered.find("tr");

    expect(tableRows.length).toBeGreaterThanOrEqual(2);
    const firstVaultRow = rendered.find("tr").at(1); //0 is the header row
    expect(firstVaultRow.find("td").at(0).text()).toEqual("Vault two");
    expect(firstVaultRow.find("td").at(1).text()).toEqual("234");
    expect(firstVaultRow.find("td").at(2).text()).toEqual("567 B");

    expect(tableRows.length).toBeGreaterThanOrEqual(3);
    const secondVaultRow = rendered.find("tr").at(2);
    expect(secondVaultRow.find("td").at(0).text()).toEqual("Vault one");
    expect(secondVaultRow.find("td").at(1).text()).toEqual("12345");
    expect(secondVaultRow.find("td").at(2).text()).toEqual("1.2 MiB");
    done();
  });
});
