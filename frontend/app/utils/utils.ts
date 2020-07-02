export type Order = "asc" | "desc";

const descendingComparator = <T>(a: T, b: T, orderBy: keyof T): number => {
  if (b[orderBy] < a[orderBy]) {
    return -1;
  }
  if (b[orderBy] > a[orderBy]) {
    return 1;
  }
  return 0;
};

export const sortListByOrder = <T>(
  listToSort: T[],
  order: Order,
  orderBy: keyof T
) => {
  return listToSort
    .map((item, index) => [item, index] as [T, number])
    .sort((a, b) => {
      const itemOrder =
        order === "desc"
          ? descendingComparator(a[0], b[0], orderBy)
          : -descendingComparator(a[0], b[0], orderBy);

      return itemOrder !== 0 ? itemOrder : a[1] - b[1];
    })
    .map((item) => item[0]);
};
