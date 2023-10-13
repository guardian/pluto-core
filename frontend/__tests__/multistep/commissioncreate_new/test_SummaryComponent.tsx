import React from "react";
import { shallow, mount } from "enzyme";
import { Typography } from "@material-ui/core";

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

    expect(
      rendered
        .find(Typography)
        .find({ id: "scheduled-completion-value" })
        .at(0)
        .text()
    ).toEqual(
      "You can't create a commission with a completion date in the past"
    );
    expect(
      rendered.find(Typography).find({ id: "title-value" }).at(0).text()
    ).toEqual("test");
    expect(
      rendered
        .find(Typography)
        .find({ id: "productionoffice-value" })
        .at(0)
        .text()
    ).toEqual("UK");
  });
});
