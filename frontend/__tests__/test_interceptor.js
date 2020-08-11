import axios from "axios";
import moxios from "moxios";
import sinon from "sinon";
import { handleUnauthorized } from "../app/utils/interceptor";

describe("handleUnauthorized", () => {
  let rejectedCb;
  beforeEach(() => {
    rejectedCb = sinon.spy();
    moxios.install();
  });
  afterEach(() => {
    rejectedCb = "";
    moxios.uninstall();
  });

  test("it should execute onRejectedCb on failing to refresh token on 401 Unauthorized", (done) => {
    moxios.stubRequest("/uri/token", {
      status: 400,
      response: {
        count: 1,
        data: { message: "Bad Request" },
      },
    });

    return moxios.wait(() => {
      axios.interceptors.response.use(
        (response) => response,
        (error) => {
          handleUnauthorized(
            { tokenUri: "/uri/token", clientId: "client" },
            error,
            rejectedCb
          ).catch(() => {
            expect(rejectedCb.calledOnce).toBeTruthy();
            done();
          });
        }
      );

      axios.interceptors.response.handlers[0].rejected({
        response: {
          status: 401,
        },
        config: {
          headers: {
            Authorization: "Bearer 123",
          },
        },
      });
    });
  });

  test("it should not execute onRejectedCb on successfully refreshing token on 401 Unauthorized", (done) => {
    moxios.stubRequest("/uri/token", {
      status: 200,
      response: {
        count: 1,
        data: { access_token: "123", refresh_token: "456" },
      },
    });

    return moxios.wait(() => {
      axios.interceptors.response.use(
        (response) => response,
        (error) => {
          handleUnauthorized(
            { tokenUri: "/uri/token", clientId: "client" },
            error,
            rejectedCb
          ).then(() => {
            expect(rejectedCb.notCalled).toBeTruthy();
            done();
          });
        }
      );

      axios.interceptors.response.handlers[1].rejected({
        response: {
          status: 401,
        },
        config: {
          headers: {
            Authorization: "Bearer 123",
          },
        },
      });
    });
  });
});
