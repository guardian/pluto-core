import {
  getBasename,
  performConversion,
} from "../../app/PremiereVersionChange/VersionChangeService";
import moxios from "moxios";

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

describe("VersionChangeService.performConversion", () => {
  beforeEach(() => moxios.install());
  afterEach(() => moxios.uninstall());

  it("should return the updated FileEntry on success", (done) => {
    const promise = performConversion(12345, "5.6.7");
    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual(
          "/api/file/12345/changePremiereVersion?requiredDisplayVersion=5.6.7"
        );
        expect(req.config.method).toEqual("post");
        await req.respondWith({
          status: 200,
          response: {
            status: "ok",
            detail: "test success",
            entry: {
              id: 12345,
            },
          },
        });

        const result = await promise;
        expect(result.id).toEqual(12345);
        done();
      } catch (err) {
        done.fail(err);
      }
    });
  });

  it("should reject with an error message on 400", (done) => {
    const promise = performConversion(12345, "5.6.7");
    moxios.wait(async () => {
      try {
        const req = moxios.requests.mostRecent();
        expect(req.url).toEqual(
          "/api/file/12345/changePremiereVersion?requiredDisplayVersion=5.6.7"
        );
        expect(req.config.method).toEqual("post");
        //don't `await` here otherwise the rejection is carried through
        req.respondWith({
          status: 400,
          response: {
            status: "bad_request",
            detail: "fix yer inputs",
          },
        });

        promise
          .then(() => done.fail("expected promise to reject but it compeleted"))
          .catch((err) => {
            expect(err).toEqual("fix yer inputs");
            done();
          });
      } catch (err) {
        done.fail(err);
      }
    });
  });
});
