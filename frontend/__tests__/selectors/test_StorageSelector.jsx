import React from "react";
import { shallow, mount } from "enzyme";
import StorageSelector from "../../app/Selectors/StorageSelector.jsx";
import sinon from "sinon";
import assert from "assert";

describe("StorageSelector", () => {
  it("should present the provided options in a combo box", () => {
    const updateCb = sinon.spy();
    const storages = [
      { id: 1, storageType: "Local", rootpath: "/path1" },
      { id: 2, storageType: "Local", rootpath: "/path2" },
    ];
    const rendered = mount(
      <StorageSelector
        enabled={true}
        selectedStorage={1}
        selectionUpdated={updateCb}
        storageList={storages}
      />
    );

    rendered.update();

    //MUI does not render items that are not visible, so we can't check for them
    const options = rendered.find("div.MuiSelect-selectMenu");
    expect(rendered.find("input").props().value).toEqual(1);
    expect(options.at(0).text()).toEqual("/path1 on Local");

    assert(updateCb.notCalled);
  });

  //removing the click test, because it's really just testing the behaviour of the MUI component not StorageSelector.
});
