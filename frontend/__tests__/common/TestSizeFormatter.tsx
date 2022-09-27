import React from "react";
import { mount } from "enzyme";
import SizeFormatter from "../../app/common/SizeFormatter";

describe("SizeFormatter", () => {
  it("should scale kilobytes", () => {
    const result = mount(<SizeFormatter bytes="2048" />);
    console.log(result.html());
    expect(result.html()).toEqual("2.00 Kb");
  });

  it("should scale megabytes", () => {
    const result = mount(<SizeFormatter bytes={2048 * 1024} />);
    console.log(result.html());
    expect(result.html()).toEqual("2.00 Mb");
  });

  it("should display 0 bytes when the size is zero", () => {
    const result = mount(<SizeFormatter bytes="0" />);
    console.log(result.html());
    expect(result.html()).toEqual("0 bytes");
  });
});
