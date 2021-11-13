/**
 * Creates a ProjectFilterTerms object based on data from the URL and a PlutoUser object or lack thereof.
 * @param  {string} currentURL String containing the data from the URL.
 * @param  {PlutoUser or null} user A PlutoUser object or a null.
 * @return {ProjectFilterTerms} A ProjectFilterTerms object.
 */
function buildFilterTerms(
  currentURL: string,
  user?: PlutoUser | null
): ProjectFilterTerms {
  const isMineInURL = currentURL.includes("mine");

  const urlParams = new Map(
    currentURL
      .substr(0)
      .split("&")
      .map((param) => {
        const kv = param.split("=");
        if (kv[1] === "undefined") {
          return [kv[0], undefined];
        } else {
          return [kv[0], decodeURIComponent(kv[1])];
        }
      })
  );

  return {
    match: <FilterOrderType>(user && isMineInURL ? "W_EXACT" : "W_CONTAINS"),
    user: urlParams.get("user") ?? user?.uid,
    title: urlParams.get("title"),
    group: urlParams.get("group"),
    showKilled: urlParams.get("showKilled") == "true" ?? false,
  };
}

/**
 * creates a query string to represent the given filter terms.
 * Note that a leading ? is _not_ added to the string.
 * @param terms ProjectFilterTerms to represent
 */
function filterTermsToQuerystring(terms: ProjectFilterTerms): string {
  return Object.entries(terms)
    .map(([k, v]) => k + "=" + encodeURIComponent(v))
    .join("&");
}
export { buildFilterTerms, filterTermsToQuerystring };
