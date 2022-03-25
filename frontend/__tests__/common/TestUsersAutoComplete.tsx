import React from "react";
import moxios from "moxios";
import { mount } from "enzyme";
import UsersAutoComplete from "../../app/common/UsersAutoComplete";
import sinon from "sinon";
import { act } from "react-dom/test-utils";

describe("UsersAutoComplete", () => {
  beforeEach(() => moxios.install());
  afterEach(() => moxios.uninstall());

  it("should load in user list on typing", (done) => {
    const didChangeCb = sinon.spy();
    const rendered = mount(
      <UsersAutoComplete valueDidChange={didChangeCb} value="fred" />
    );

    //simulated typing needs to go onto the contained input field, not the autocomplete itself
    rendered
      .find("input.MuiInputBase-input")
      .simulate("change", { target: { value: "jane" } });

    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual("/api/valid-users?prefix=jane");

        await act(async () => {
          await req.respondWith({
            status: 200,
            response: {
              status: "ok",
              users: ["jane_smith", "jane_jones"],
            },
          });
        });

        expect(didChangeCb.callCount).toEqual(0);
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });
});
