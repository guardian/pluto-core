type IndexedArray<V> = { [key: string]: V };

type ComparatorValue = -1 | 0 | 1;
type Ordering = "desc" | "asc";

/**
 * returns a comparator value for the given pair.
 * If equal, value is 0. If b < a, then value is -1. If b > a, then value is +1.
 * Both A and B must have the same data structure, i.e. be of the same type.  This type is represented by
 * the generic type value V.
 * @param a a structured data element that is presented as a string-indexed array
 * @param b a structured data element that is presented as a string-indexed array
 * @param orderBy the index key on which to compare the two structured elements. This is a string.
 */
function descendingComparator<V>(
  a: IndexedArray<V>,
  b: IndexedArray<V>,
  orderBy: string
): ComparatorValue {
  if (b[orderBy] < a[orderBy]) {
    return -1;
  }
  if (b[orderBy] > a[orderBy]) {
    return 1;
  }
  return 0;
}

type ComparatorFunc<V> = (
  a: IndexedArray<V>,
  b: IndexedArray<V>
) => ComparatorValue;

/**
 * Factory that returns a comparator function for the given parameters. The comparator function takes two indexed
 * arrays and returns either -1,0 or 1 depending if the arguments are less than, equal or greater than each other.
 *
 * This requires two type parameters, the first is the type used to describe the valid keys of the record type and the
 * second is the record type itself.
 *
 * The first type parameter _must_ be castable to "string" and is intended to be used when you specify a constrained list
 * e.g. if you have type MyColumnHeaders = "col1"|"col2"|"col3"|"col4" you could call `getComparator<MyColumnHeaders, MyRecordType>("asc",orderingValue)`
 * @param order either "asc" or "desc". If "asc" then the comparator return value is negated before return
 * @param orderBy the field name to compare on the indexed arrays. It's assumed that this is castable to a basic string,
 *                but is included as a generic type T so that a more specific typing value can be provided
 * @returns - a function which is used for comparing two records of type V.
 */
function getComparator<T, V>(order: Ordering, orderBy: T): ComparatorFunc<V> {
  return order === "desc"
    ? (a: IndexedArray<V>, b: IndexedArray<V>) =>
        descendingComparator(a, b, orderBy as unknown as string)
    : (a: IndexedArray<V>, b: IndexedArray<V>) =>
        -(descendingComparator(
          a,
          b,
          orderBy as unknown as string
        ) as number) as ComparatorValue;
}

/**
 * performs a stable stort of the given list, i.e. the order is preserved if the sort judges two rows to be equal
 * This requires a type parameter indicating the the record type of the data to be sorted (e.g. if your data records
 * are in an array of MyInterfaceType[] then you call `stableSort<MyInterfaceType>(...)`.
 * FIXME: this is not properly typesafe yet and relies on ts-ignore to transpile correctly
 * @param array data to sort, in the form of an array of rows that are string-indexable (this is not mutated)
 * @param comparator
 */
function stableSort<V>(array: V[], comparator: ComparatorFunc<V>): V[] {
  const stabilizedThis = array.map((el, index) => [el, index]);
  stabilizedThis.sort((a, b) => {
    // @ts-ignore
    const order = comparator(a[0], b[0]);
    if (order !== 0) return order;
    // @ts-ignore
    return a[1] - b[1];
  });
  return stabilizedThis.map((el) => el[0] as V);
}

/**
 * performs an unstable sort of the given list, i.e. the results are indeterminate if the sort judges two rows to be equal.
 * This requires a type parameter of _the indiviual record type_ of the data to be sorted (e.g. of your data records are
 * in an array of MyInterfaceType[] then you call unstableSort<MyInterfaceType>(...).
 * @param array the data to be sorted, in the form of an array of rows that are string-indexable
 * @param comparator comparator function to use. Obtain this by calling `getComparator()`
 */
function unstableSort<V>(
  array: IndexedArray<V>[],
  comparator: ComparatorFunc<V>
) {
  const copiedArray = Object.assign([] as IndexedArray<V>[], array);

  copiedArray.sort((a, b) => comparator(a, b));
}

export { descendingComparator, getComparator, stableSort, unstableSort };
