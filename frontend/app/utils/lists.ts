export type SortDirection = "asc" | "desc";

export const sortListByOrder = <T>(
  list: T[],
  orderBy: keyof T,
  order: SortDirection = "asc"
) =>
  list.sort((a, b) => {
    const [valueA, valueB] = [a[orderBy], b[orderBy]];
    const sortResult = valueA < valueB ? -1 : valueA > valueB ? 1 : 0;
    // Default to "asc" if bad value.
    return order === "desc" ? -sortResult : sortResult;
  });
