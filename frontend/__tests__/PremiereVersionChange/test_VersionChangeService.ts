import { getBasename } from "../../app/PremiereVersionChange/VersionChangeService";

describe("VersionChangeService.getBasename", () => {
  it("should extract the basename from an absolute filepath", () => {
    expect(getBasename("/path/to/some/file.ext")).toEqual("file.ext");
  });

  it("should extract the basename from a relative filepath", () => {
    expect(getBasename("path/to/some/file.ext")).toEqual("file.ext");
  });

  it("should return a basename unchanged", () => {
    expect(getBasename("file.ext")).toEqual("file.ext");
  });
});
