import {
  buildFilterTerms,
  filterTermsToQuerystring,
} from "../../app/filter/terms";

describe("terms.buildFilterTerms", () => {
  it("return the correct value when showKilled is set to true in the URL", () => {
    const objectOutput = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=true"
    );
    expect(objectOutput.showKilled).toBe(true);
  });

  it("return the correct value when showKilled is set to false in the URL", () => {
    const objectOutput2 = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=false"
    );
    expect(objectOutput2.showKilled).toBe(false);
  });

  it("return the correct value when title is set to 'test' in the URL", () => {
    const objectOutput3 = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=false"
    );
    expect(objectOutput3.title).toBe("test");
  });

  it("return the correct value when group is set to '1' in the URL", () => {
    const objectOutput4 = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=false"
    );
    expect(objectOutput4.group).toBe("1");
  });

  const testUser = <PlutoUser>{
    uid: "1",
  };

  it("return the correct value of match when a user object is present and 'mine' is in the URL", () => {
    const objectOutput5 = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=false",
      testUser
    );
    expect(objectOutput5.match).toBe("W_EXACT");
  });

  it("return the correct value of match when no user object is present and 'mine' is not in the URL", () => {
    const objectOutput6 = buildFilterTerms(
      "?mine&title=test&user=mr_flibble&group=1&showKilled=false"
    );
    expect(objectOutput6.match).toBe("W_CONTAINS");
  });
});

describe("terms.filterTermsToQuerystring", () => {
  it("should convert FilterTerms into a query string", () => {
    const terms: ProjectFilterTerms = {
      match: "W_CONTAINS",
      user: "kevin",
      title: "something",
    };

    expect(filterTermsToQuerystring(terms)).toEqual(
      "match=W_CONTAINS&user=kevin&title=something"
    );
  });

  it("should urlencode dodgy characters", () => {
    const terms: ProjectFilterTerms = {
      match: "W_CONTAINS",
      user: "kevin",
      title: "something here",
    };

    expect(filterTermsToQuerystring(terms)).toEqual(
      "match=W_CONTAINS&user=kevin&title=something%20here"
    );
  });

  it("must generate terms that can be read back by buildFilterTerms", () => {
    const terms: ProjectFilterTerms = {
      match: "W_CONTAINS",
      user: "kevin",
      title: "something here",
    };
    //buildFilterTerms gives back explicit defaults in its response
    const expectedResult = Object.assign({}, terms, {
      group: undefined,
      showKilled: false,
    });
    const result = buildFilterTerms(filterTermsToQuerystring(terms));
    expect(result).toEqual(expectedResult);
  });
});
