interface ProjectFilterTerms extends FilterTerms {
  commissionId?: number;
  title?: string;
  mine?: string;
}

function buildFilterTerms(filterTerms: ProjectFilterTerms) {
  const oldParams = new Map(
    location.search
      .substr(1)
      .split("&")
      .map((param) => {
        const kv = param.split("=");
        return [kv[0], decodeURIComponent(kv[1])];
      })
  );

  let oldParamsObject = Array.from(oldParams).reduce(
    (obj, [key, value]) => Object.assign(obj, { [key]: value }),
    {}
  );

  let newFilters = Object.assign({}, filterTerms, oldParamsObject);

  if (newFilters["showKilled"] == "false") {
    newFilters["showKilled"] = false;
  }

  if (newFilters["showKilled"] == "true") {
    newFilters["showKilled"] = true;
  }

  return newFilters;
}

export { buildFilterTerms };
