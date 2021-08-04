import React from "react";
import { shallow, mount } from "enzyme";
import SummaryComponent from "../../../app/multistep/commissioncreate_new/SummaryComponent";

describe("SummaryComponent", () => {
  it("should correctly render the scheduled completion time, title and production office", () => {
    const fakeTime = new Date(2020, 0, 2, 3, 4, 5);

    const rendered = mount(
      <SummaryComponent
        title="test"
        scheduledCompetion={fakeTime}
        productionOffice="UK"
      />
    );

    expect(rendered.find("td#scheduled-completion-value").text()).toEqual(
      "Thursday, 2nd Jan 2020"
    );
    expect(rendered.find("td#title-value").text()).toEqual("test");
    expect(rendered.find("td#productionoffice-value").text()).toEqual("UK");
  });
});
