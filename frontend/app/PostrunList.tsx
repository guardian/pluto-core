import React, { useEffect, useState } from "react";
import axios from "axios";
import { RouteComponentProps } from "react-router-dom";
import EnhancedTable from "./MaterialUITable";
import GeneralListComponent from "./GeneralListComponent";
import ListActionButtons from "./common/ListActionButtons";
import { Helmet } from "react-helmet";
import { SystemNotification, SystemNotifcationKind } from "pluto-headers";

const PostrunList: React.FC<RouteComponentProps> = (props) => {
  const [tableData, setTableData] = useState<PostrunAction[]>([]);
  const [lastError, setLastError] = useState<string | undefined>(undefined);

  const columns = [
    {
      header: "Id",
      key: "id",
      defaultSorting: "desc",
      dataProps: { className: "align-right" },
      headerProps: { className: "dashboardheader" },
    },
    GeneralListComponent.standardColumn("Title", "title"),
    GeneralListComponent.standardColumn("Description", "description"),
    GeneralListComponent.standardColumn("Script name", "runnable"),
    GeneralListComponent.standardColumn("Version", "version"),
    GeneralListComponent.dateTimeColumn("Created", "ctime"),
    GeneralListComponent.standardColumn("Owner", "owner"),
    {
      header: "Actions",
      key: "id",
      render: (id: number) => <ListActionButtons itemId={id} {...props} />,
    },
  ];

  const loadData = async () => {
    const response = await axios.get<PostrunActionsResponse>(
      "/api/postrun?startAt=0&length=100"
    );
    setTableData(response.data.result);
  };

  useEffect(() => {
    loadData().catch((err) => {
      console.error("Could not load postrun actions: ", err);
      setLastError("Could not load postruns, see console");
    });
  }, []);

  const requestRescan = async () => {
    try {
      await axios.put("/api/postrun/scan");
      SystemNotification.open(
        SystemNotifcationKind.Success,
        "Rescan in progress"
      );
    } catch (e) {
      console.error("Could not request rescan of postruns: ", e);
      SystemNotification.open(
        SystemNotifcationKind.Error,
        "Could not scan postruns, see server logs and console for details"
      );
    }
  };

  return (
    <div>
      <Helmet>
        <title>Postrun Actions - Pluto Projects Admin</title>
      </Helmet>
      <span className="list-title">
        <h2 className="list-title">Postrun Actions</h2>
      </span>
      <span className="banner-control">
        <button id="newElementButton" onClick={requestRescan}>
          Re-scan
        </button>
      </span>

      <EnhancedTable columnData={columns} tableData={tableData} />
    </div>
  );
};

export default PostrunList;
