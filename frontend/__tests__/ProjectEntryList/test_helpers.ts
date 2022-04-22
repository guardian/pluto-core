import moxios from "moxios";
import { translatePremiereVersion } from "../../app/ProjectEntryList/helpers";

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
