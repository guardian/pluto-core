export type SortDirection = "asc" | "desc";

export const sortListByOrder = <T extends Record<keyof T, any>>(
  list: T[],
  orderBy: keyof T,
  order: SortDirection = "asc"
) => list;
