import moment, { Moment } from "moment";

interface JwtDataShape {
  aud: string;
  iss: string;
  iat: number;
  iat_moment: Moment;
  exp: number;
  exp_moment: Moment;
  sub?: string;
  email?: string;
  first_name?: string;
  family_name?: string;
  username?: string;
  location?: string;
  job_title?: string;
  authmethod?: string;
  auth_time?: string;
  ver?: string;
  appid?: string;
}

function JwtData(jwtData: object) {
  return new Proxy(<JwtDataShape>jwtData, {
    get(target, prop) {
      switch (prop) {
        case "iat_moment":
          return moment.utc(target.iat * 1000);
        case "exp_moment":
          return moment.utc(target.exp * 1000);
        default:
          return (<any>target)[prop] ?? null;
      }
    },
  });
}

export type { JwtDataShape };
export { JwtData };
