import React from "react";
import { shallow, mount } from "enzyme";
import StorageDeleteComponent from "../../app/delete/StorageDeleteComponent";
import moxios from "moxios";
import { act } from "react-dom/test-utils";

describe("StorageDeleteComponent", () => {
  beforeEach(() => moxios.install());
  afterEach(() => moxios.uninstall());

  it("should return an appropriate description", (done) => {
    const rendered = mount(
      <StorageDeleteComponent match={{ params: { itemid: 9 } }} />
    );

    return moxios.wait(async () => {
      const request = moxios.requests.mostRecent();
      expect(request).toBeTruthy();
      expect(request.url).toEqual("/api/storage/9");

      try {
        await act(async () => {
          await request.respondWith({
            status: 200,
            response: {
              status: "ok",
              result: {
                storageType: "something",
                rootpath: "/path/to/storage",
                user: "fred",
              },
            },
          });
        });

        rendered.update();

        expect(rendered.find("#storageType").text()).toEqual("something");
        expect(rendered.find("#storageSubfolder").text()).toEqual(
          "/path/to/storage"
        );
        expect(rendered.find(".login-value").text()).toEqual("fred");
        done();
      } catch (error) {
        console.error(error);
        done.fail(error);
      }
    });
  });
});
