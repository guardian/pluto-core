import React from "react";
import { mount } from "enzyme";
import {
  stableSort,
  getComparator,
  ListTableRow,
} from "../app/MaterialUITable";
import EnhancedTable from "../app/MaterialUITable";
import { Table, TableBody, TableCell, TableRow } from "@material-ui/core";

describe("MaterialUITable", () => {
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
    {
      id: 10,
      filepath: "20200731_yadayadayada.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-07-31T15:07:22.629+0000",
      mtime: "2020-07-31T15:07:22.629+0000",
      atime: "2020-07-31T15:07:22.629+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 60,
      filepath: "20200814_fsdfsdfs.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-14T10:03:21.611+0000",
      mtime: "2020-08-14T10:03:21.611+0000",
      atime: "2020-08-14T10:03:21.611+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 63,
      filepath: "20200814_ggsdgfgs.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-14T10:42:35.525+0000",
      mtime: "2020-08-14T10:42:35.525+0000",
      atime: "2020-08-14T10:42:35.525+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 64,
      filepath: "20200818_dave_test_wibble.plproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-18T10:44:25.097+0000",
      mtime: "2020-08-18T10:44:25.097+0000",
      atime: "2020-08-18T10:44:25.097+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 15,
      filepath: "20200731_yadayadayada23.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-07-31T17:57:32.425+0000",
      mtime: "2020-07-31T17:57:32.425+0000",
      atime: "2020-07-31T17:57:32.425+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 69,
      filepath: "20200818_dave_test_more.plproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-18T10:53:00.687+0000",
      mtime: "2020-08-18T10:53:00.687+0000",
      atime: "2020-08-18T10:53:00.687+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 20,
      filepath: "premiere-basic_template_CC2018_v2_Plutov2.prproj",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-06T16:24:20.377+0000",
      mtime: "2020-08-06T16:24:20.377+0000",
      atime: "2020-08-06T16:24:20.377+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 70,
      filepath: "20200818_test_test_test_test_test.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-18T15:08:06.327+0000",
      mtime: "2020-08-18T15:08:06.327+0000",
      atime: "2020-08-18T15:08:06.327+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 23,
      filepath: "aftereffects_basic_template_CC2018.aep",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-07T13:24:29.995+0000",
      mtime: "2020-08-07T13:24:29.995+0000",
      atime: "2020-08-07T13:24:29.995+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 24,
      filepath: "audition_template_CC2018v1.sesx",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-07T13:25:32.155+0000",
      mtime: "2020-08-07T13:25:32.155+0000",
      atime: "2020-08-07T13:25:32.155+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 25,
      filepath: "prelude_no_clips_CC2018.plproj",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-07T13:26:18.273+0000",
      mtime: "2020-08-07T13:26:18.273+0000",
      atime: "2020-08-07T13:26:18.273+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 26,
      filepath: "Stereo Editing v10.cpr",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-07T13:42:58.053+0000",
      mtime: "2020-08-07T13:42:58.053+0000",
      atime: "2020-08-07T13:42:58.053+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 27,
      filepath: "Studio 1 Standard v10.cpr",
      storage: 1,
      user: "yusuf_parkar@gnm.int",
      version: 1,
      ctime: "2020-08-07T13:43:40.800+0000",
      mtime: "2020-08-07T13:43:40.800+0000",
      atime: "2020-08-07T13:43:40.800+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 72,
      filepath: "20200828_testing_actors.aep",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-28T11:49:07.419+0000",
      mtime: "2020-08-28T11:49:07.419+0000",
      atime: "2020-08-28T11:49:07.419+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 38,
      filepath: "20200811_bvbsgdhsdgghs.aep",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-11T21:03:09.814+0000",
      mtime: "2020-08-11T21:03:09.814+0000",
      atime: "2020-08-11T21:03:09.814+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 73,
      filepath: "20200828_testing_actors_again.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-28T11:49:43.250+0000",
      mtime: "2020-08-28T11:49:43.250+0000",
      atime: "2020-08-28T11:49:43.250+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 93,
      filepath: "20200903_premiere.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-03T18:51:01.049+0000",
      mtime: "2020-09-03T18:51:01.049+0000",
      atime: "2020-09-03T18:51:01.049+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 94,
      filepath: "20200907_gdfgfdgdfsgds.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-07T15:13:35.764+0000",
      mtime: "2020-09-07T15:13:35.764+0000",
      atime: "2020-09-07T15:13:35.764+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 95,
      filepath: "20200907_gfdgds.plproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-07T15:24:08.629+0000",
      mtime: "2020-09-07T15:24:08.629+0000",
      atime: "2020-09-07T15:24:08.629+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 48,
      filepath: "20200812_jgkdhjkghdfg.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:25:48.337+0000",
      mtime: "2020-08-12T15:25:48.337+0000",
      atime: "2020-08-12T15:25:48.337+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 49,
      filepath: "20200812_davetest3.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:32:39.743+0000",
      mtime: "2020-08-12T15:32:39.743+0000",
      atime: "2020-08-12T15:32:39.743+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 51,
      filepath: "20200812_davetest5.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-12T15:36:10.990+0000",
      mtime: "2020-08-12T15:36:10.990+0000",
      atime: "2020-08-12T15:36:10.990+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 83,
      filepath: "20200828_gdfsgdsg.aep",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-28T15:30:55.092+0000",
      mtime: "2020-08-28T15:30:55.092+0000",
      atime: "2020-08-28T15:30:55.092+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 84,
      filepath: "20200828_ahgfhsdfgsdfg.cpr",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-28T15:31:12.062+0000",
      mtime: "2020-08-28T15:31:12.062+0000",
      atime: "2020-08-28T15:31:12.062+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 96,
      filepath: "20200907_healet_test.aep",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-07T17:13:20.182+0000",
      mtime: "2020-09-07T17:13:20.182+0000",
      atime: "2020-09-07T17:13:20.182+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 97,
      filepath: "20200908_yada_yada_premiere_yada.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-08T16:39:34.472+0000",
      mtime: "2020-09-08T16:39:34.472+0000",
      atime: "2020-09-08T16:39:34.472+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 98,
      filepath: "20200908_yada_premiere_again.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-08T16:47:03.829+0000",
      mtime: "2020-09-08T16:47:03.829+0000",
      atime: "2020-09-08T16:47:03.829+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 99,
      filepath: "20200917_the_testy_one.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-17T15:03:41.801+0000",
      mtime: "2020-09-17T15:03:41.801+0000",
      atime: "2020-09-17T15:03:41.801+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 100,
      filepath: "20200918_media_atom_testing_project.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-18T09:03:18.407+0000",
      mtime: "2020-09-18T09:03:18.407+0000",
      atime: "2020-09-18T09:03:18.407+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 90,
      filepath: "20200831_some_kinda_test.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-08-31T17:55:48.158+0000",
      mtime: "2020-08-31T17:55:48.158+0000",
      atime: "2020-08-31T17:55:48.158+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 91,
      filepath: "20200902_testae.aep",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-02T12:43:12.477+0000",
      mtime: "2020-09-02T12:43:12.477+0000",
      atime: "2020-09-02T12:43:12.477+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 101,
      filepath: "20200918_atom_test_2.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-18T13:30:41.943+0000",
      mtime: "2020-09-18T13:30:41.943+0000",
      atime: "2020-09-18T13:30:41.943+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 102,
      filepath: "20200921_another_regular_user_project.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-21T12:51:53.700+0000",
      mtime: "2020-09-21T12:51:53.700+0000",
      atime: "2020-09-21T12:51:53.700+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 103,
      filepath: "20200921_the_healey_one_1.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-21T14:35:29.523+0000",
      mtime: "2020-09-21T14:35:29.523+0000",
      atime: "2020-09-21T14:35:29.523+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 104,
      filepath: "20200922_the_healey_one_2.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-22T10:53:14.437+0000",
      mtime: "2020-09-22T10:53:14.437+0000",
      atime: "2020-09-22T10:53:14.437+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 105,
      filepath: "20200923_sep2020_on_the_ground.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-23T15:09:37.198+0000",
      mtime: "2020-09-23T15:09:37.198+0000",
      atime: "2020-09-23T15:09:37.198+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 106,
      filepath: "20200930_test2.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-09-30T16:04:22.585+0000",
      mtime: "2020-09-30T16:04:22.585+0000",
      atime: "2020-09-30T16:04:22.585+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 119,
      filepath: "20210127_test_project.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-01-27T14:22:15.483+0000",
      mtime: "2021-01-27T14:22:15.483+0000",
      atime: "2021-01-27T14:22:15.483+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 112,
      filepath: "premiere-basic_template_CC2018_v2_Plutov3.prproj",
      storage: 1,
      user: "yusuf_parkar",
      version: 1,
      ctime: "2020-10-20T15:36:44.563+0000",
      mtime: "2020-10-20T15:36:44.563+0000",
      atime: "2020-10-20T15:36:44.563+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 128,
      filepath: "20210302_test_with_single_quote_2.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T17:09:21.071+0000",
      mtime: "2021-03-02T17:09:21.071+0000",
      atime: "2021-03-02T17:09:21.071+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 114,
      filepath: "20201020_test2.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-10-20T15:38:03.768+0000",
      mtime: "2020-10-20T15:38:03.768+0000",
      atime: "2020-10-20T15:38:03.768+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 121,
      filepath: "20210302_test_after_effects.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T16:16:17.617+0000",
      mtime: "2021-03-02T16:16:17.617+0000",
      atime: "2021-03-02T16:16:17.617+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 116,
      filepath: "20201112_test_wibble_etc.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-11-12T13:00:38.820+0000",
      mtime: "2020-11-12T13:00:38.820+0000",
      atime: "2020-11-12T13:00:38.820+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 118,
      filepath: "20201218_testdec.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2020-12-18T11:05:31.957+0000",
      mtime: "2020-12-18T11:05:31.957+0000",
      atime: "2020-12-18T11:05:31.957+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 122,
      filepath: "20210302_test_test.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T17:06:37.785+0000",
      mtime: "2021-03-02T17:06:37.785+0000",
      atime: "2021-03-02T17:06:37.785+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 123,
      filepath: "20210302_test_with_single_quote.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T17:07:03.607+0000",
      mtime: "2021-03-02T17:07:03.607+0000",
      atime: "2021-03-02T17:07:03.607+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 125,
      filepath: "20210302_test_test_2_test.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T17:07:42.393+0000",
      mtime: "2021-03-02T17:07:42.393+0000",
      atime: "2021-03-02T17:07:42.393+0000",
      hasContent: true,
      hasLink: false,
    },
    {
      id: 126,
      filepath: "20210302_test_test_3.prproj",
      storage: 2,
      user: "system",
      version: 1,
      ctime: "2021-03-02T17:08:23.709+0000",
      mtime: "2021-03-02T17:08:23.709+0000",
      atime: "2021-03-02T17:08:23.709+0000",
      hasContent: true,
      hasLink: false,
    },
  ];

  const testColumnData = [
    {
      header: "Id",
      key: "id",
      defaultSorting: "desc",
      dataProps: { className: "align-right" },
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "File path",
      key: "filepath",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Storage",
      key: "storage",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Owner",
      key: "user",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Version",
      key: "version",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Create time",
      key: "ctime",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Modification time",
      key: "mtime",
      headerProps: { className: "dashboardheader" },
    },
    {
      header: "Access time",
      key: "atime",
      headerProps: { className: "dashboardheader" },
    },
    { header: "", key: "links" },
  ];

  test("should render an EnhancedTable", (done) => {
    const result = mount(
      <EnhancedTable tableData={testTableData} columnData={testColumnData} />
    );
    const rendered_table = result.find("EnhancedTable");
    expect(rendered_table).toBeTruthy();
    expect(rendered_table.props().tableData).toEqual(testTableData);
    expect(rendered_table.props().columnData).toEqual(testColumnData);
    done();
  });

  test("should render a table with the correct data", () => {
    const wrapper = mount(
      <EnhancedTable tableData={testTableData} columnData={testColumnData} />
    );
    const firstTableRow = wrapper
      .find(Table)
      .find(TableBody)
      .find(TableRow)
      .first();
    const tableCell = firstTableRow.find(TableCell).first();
    expect(tableCell.props().children).toEqual(5);
    const tableCellTwo = firstTableRow.find(TableCell).at(1);
    expect(tableCellTwo.props().children).toEqual("testleah.prproj");
    const tableCellThree = firstTableRow.find(TableCell).at(2);
    expect(tableCellThree.props().children).toEqual(1);
    const secondTableRow = wrapper
      .find(Table)
      .find(TableBody)
      .find(TableRow)
      .at(1);
    const tableCellFour = secondTableRow.find(TableCell).first();
    expect(tableCellFour.props().children).toEqual(6);
    const tableCellFive = secondTableRow.find(TableCell).at(1);
    expect(tableCellFive.props().children).toEqual(
      "20200626_prexit_one.prproj"
    );
    const tableCellSix = secondTableRow.find(TableCell).at(2);
    expect(tableCellSix.props().children).toEqual(1);
  });

  test("should render a ListTableRow", (done) => {
    const result = mount(<ListTableRow data={[]} columns={testColumnData} />);
    const rendered_row = result.find("ListTableRow");
    expect(rendered_row).toBeTruthy();
    expect(rendered_row.props().data).toEqual([]);
    expect(rendered_row.props().columns).toEqual(testColumnData);
    done();
  });

  test("should render a row with the correct data", () => {
    const wrapper = mount(
      <ListTableRow data={testTableData[0]} columns={testColumnData} />
    );
    const tableCellSeven = wrapper.find(TableCell).first();
    expect(tableCellSeven.props().children).toEqual(53);
    const tableCellEight = wrapper.find(TableCell).at(1);
    expect(tableCellEight.props().children).toEqual(
      "20200812_davetest6.prproj"
    );
    const tableCellNine = wrapper.find(TableCell).at(2);
    expect(tableCellNine.props().children).toEqual(2);
  });
});
