import moxios from "moxios";
import { translatePremiereVersion } from "../../app/ProjectEntryList/helpers";
import { getSesxProjectTypeIds } from "../../app/ProjectEntryList/helpers";

describe("helpers.translatePremiereVersion", () => {
  beforeEach(() => moxios.install());
  afterEach(() => moxios.uninstall());

  it("should call out to retrieve the version translation and return the internal version string", (done) => {
    const resultPromise = translatePremiereVersion(123, true);

    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual("/api/premiereVersion/internal/123");
        req.respondWith({
          status: 200,
          response: {
            status: "ok",
            version: {
              name: "test version",
              internalVersionNumber: 123,
              displayedVersion: "1.2.3",
            } as PremiereVersionTranslation,
          },
        });

        const result = await resultPromise;
        expect(result).toEqual("1.2.3");
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });

  it("should return undefined if the version number is not found", (done) => {
    const resultPromise = translatePremiereVersion(123, true);

    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual("/api/premiereVersion/internal/123");
        req.respondWith({
          status: 404,
          response: {
            status: "error",
            detail: "not_found",
          },
        });

        const result = await resultPromise;
        expect(result).toEqual(undefined);
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });

  it("should return undefined on a server error", (done) => {
    const resultPromise = translatePremiereVersion(123, true);

    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual("/api/premiereVersion/internal/123");
        req.respondWith({
          status: 500,
          response: {
            status: "error",
            detail: "kaboom",
          },
        });

        const result = await resultPromise;
        expect(result).toEqual(undefined);
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });
});

describe("getSesxProjectTypeIds", () => {
  beforeEach(() => {
    moxios.install();
  });

  afterEach(() => {
    moxios.uninstall();
  });

  it("should return project type ids for '.sesx' file extension", async () => {
    const mockData = {
      status: "ok",
      result: [
        { id: 1, fileExtension: ".sesx" },
        { id: 2, fileExtension: ".txt" },
        { id: 3, fileExtension: ".sesx" },
      ],
    };

    moxios.wait(() => {
      const request = moxios.requests.mostRecent();
      request.respondWith({
        status: 200,
        response: mockData,
      });
    });

    const result = await getSesxProjectTypeIds();
    expect(result).toEqual([1, 3]);
  });

  it("should throw an error when the status is not 'ok'", async () => {
    const mockData = {
      status: "error",
      result: [],
    };

    moxios.wait(() => {
      const request = moxios.requests.mostRecent();
      request.respondWith({
        status: 200,
        response: mockData,
      });
    });

    await expect(getSesxProjectTypeIds()).rejects.toThrow(
      "Failed to fetch project types"
    );
  });

  it("should throw an error when the request fails", async () => {
    moxios.wait(() => {
      const request = moxios.requests.mostRecent();
      request.respondWith({
        status: 500,
        response: {},
      });
    });

    await expect(getSesxProjectTypeIds()).rejects.toThrow();
  });
});
