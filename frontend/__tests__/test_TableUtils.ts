import { getComparator, stableSort } from "../app/TableUtils";

describe("TableUtils.stableSort", () => {
  const testTableData = [
    {
      id: 53,
      filepath: "20200812_davetest6.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:37:23.121+0000",
      mtime: "2020-08-12T15:37:23.121+0000",
      atime: "2020-08-12T15:37:23.121+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 56,
      filepath: "20200812_davetest7.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:38:01.872+0000",
      mtime: "2020-08-12T15:38:01.872+0000",
      atime: "2020-08-12T15:38:01.872+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 5,
      filepath: "testleah.prproj",
      storage: 1,
      user: "andy_gallagher@gnm.int",
      version: 1,
      ctime: "2020-06-26T14:44:00.082+0000",
      mtime: "2020-06-26T14:44:00.082+0000",
      atime: "2020-06-26T14:44:00.082+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 6,
      filepath: "20200626_prexit_one.prproj",
      storage: 1,
      user: "system",
      version: 1,
      ctime: "2020-06-26T14:54:59.029+0000",
      mtime: "2020-06-26T14:54:59.029+0000",
      atime: "2020-06-26T14:54:59.029+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 59,
      filepath: "20200812_davetest9.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:39:36.879+0000",
      mtime: "2020-08-12T15:39:36.879+0000",
      atime: "2020-08-12T15:39:36.879+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 9,
      filepath: "20200731_andy_again.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-07-31T11:55:45.485+0000",
      mtime: "2020-07-31T11:55:45.485+0000",
      atime: "2020-07-31T11:55:45.485+0000",
      hasContent: true,
      hasLink: false,
    },
  ];

  it("should sort data correctly with ascending comparator", () => {
    const sortedData = stableSort(testTableData, getComparator("asc", "id"));
    expect(sortedData[0]["id"]).toEqual(5);
    expect(sortedData[1]["id"]).toEqual(6);
    expect(sortedData[2]["id"]).toEqual(9);
  });

  it("should sort data correctly with a descending comparator", () => {
    const sortedDataTwo = stableSort(
      testTableData,
      getComparator("desc", "id")
    );
    expect(sortedDataTwo[0]["id"]).toEqual(59);
    expect(sortedDataTwo[1]["id"]).toEqual(56);
    expect(sortedDataTwo[2]["id"]).toEqual(53);
  });
});
