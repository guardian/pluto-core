function buildFilterTerms(currentURL: string, user?: PlutoUser | null) {
  const isMineInURL = currentURL.includes("mine");

  console.log(isMineInURL);

  const urlParams = new Map(
    location.search
      .substr(1)
      .split("&")
      .map((param) => {
        const kv = param.split("=");
        return [kv[0], decodeURIComponent(kv[1])];
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

export { buildFilterTerms };
