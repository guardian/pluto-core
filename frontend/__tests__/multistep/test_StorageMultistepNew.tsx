import React from "react";
import { shallow, mount } from "enzyme";
import { createMemoryHistory } from "history";
import moxios from "moxios";
import StorageMultistepNew from "../../app/multistep/StorageMultistepNew";
import { act } from "react-dom/test-utils";

describe("StorageMultistepNew", () => {
  beforeEach(() => moxios.install());
  afterEach(() => moxios.uninstall());

  const fakeStorageData: StorageEntry = {
    storageType: "Local",
    supportsVersion: false,
    id: 5,
    rootpath: "/some/path",
    clientpath: "/some/other/path",
  };

  it("should load in data when started with a storage id", (done) => {
    const history = createMemoryHistory();
    const fakeLocation = {
      search: "",
      pathname: "",
      hash: "",
      state: null,
    };

    const fakeMatch = {
      params: {
        itemid: "5",
      },
      isExact: true,
      path: "",
      url: "",
    };

    const rendered = mount(
      <StorageMultistepNew
        history={history}
        location={fakeLocation}
        match={fakeMatch}
      />
    );

    moxios.wait(async () => {
      const storageTypesRequest = moxios.requests.at(0);
      const storageDataRequest = moxios.requests.at(1);

      try {
        expect(storageTypesRequest.url).toEqual("/api/storage/knowntypes");
        expect(storageDataRequest.url).toEqual("/api/storage/5");
        await act(async () => {
          await storageTypesRequest.respondWith({
            status: 200,
            response: {
              status: "ok",
              types: [
                {
                  name: "Local",
                  needsLogin: false,
                  hasSubFolders: true,
                  canVersion: false,
                },
              ],
            },
          });
          rendered.update();
        });

        await act(async () => {
          await storageDataRequest.respondWith({
            status: 200,
            response: {
              status: "ok",
              result: fakeStorageData,
            },
          });
          rendered.update();
        });

        expect(
          rendered.find(".MuiSelect-root").find("#storage_type_selector").text()
        ).toEqual("Local");
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });
});
