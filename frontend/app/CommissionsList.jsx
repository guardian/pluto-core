import React, { useEffect, useState } from "react";
import {
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  Paper,
  makeStyles,
} from "@material-ui/core";
import { Link } from "react-router-dom";
import Axios from "axios";

const API = "/api/pluto";
const API_COMMISSION = `${API}/commission`;
const API_COMMISSION_FILTER = `${API_COMMISSION}/list`;
const API_WORKING_GROUP = `${API}/workinggroup`;

// TODO: Extract this to another file. Perhaps to a 'helpers' or 'api' directory?
const getWorkingGroupNameMap = async (commissions) =>
  new Map(
    await Promise.all(
      commissions.map(async (commission) => {
        try {
          const {
            data: { status, result: { name: workingGroupName } = {} },
          } = await Axios.get(
            `${API_WORKING_GROUP}/${commission.workingGroupId}`
          );

          if (status === "ok") {
            return [commission.workingGroupId, workingGroupName];
          } else {
            throw new Error('status not "ok"');
          }
        } catch (error) {
          console.error(
            "could not fetch working group name for WG with id:",
            commission.workingGroupId
          );
          throw error;
        }
      })
    )
  );

// TODO: Extract this as well.
const getCommissionsOnPage = async ({
  setCommissions,
  setWorkingGroups,
  pageOffset = 0,
  pageSize = 20,
  filterTerms,
}) => {
  const itemOffset = pageOffset * pageSize;
  const {
    data: { status, result: commissions = [] },
  } = await (filterTerms
    ? Axios.put(
        `${API_COMMISSION_FILTER}?startAt=${itemOffset}&length=${pageSize}`,
        filterTerms
      )
    : Axios.get(`${API_COMMISSION}?startAt=${itemOffset}&length=${pageSize}`));

  if (status !== "ok") {
    throw new Error("unable to fetch commissions");
  }

  const wgNameMap = await getWorkingGroupNameMap(commissions);
  setWorkingGroups(wgNameMap);
  setCommissions(commissions);
};

// TODO: for use later?
// const loadDependencies = async ({ setIsAdmin, setUid }) => {
//   try {
//     const response = await Axios.get("/api/isLoggedIn");
//     if (response.data.status !== "ok") {
//       return;
//     }
//
//     setIsAdmin(response.data.isAdmin);
//     setUid(response.data.uid);
//   } catch (error) {
//     setIsAdmin(false);
//
//     if (response?.data?.status === 403) {
//       // 403 -- simply no access, not necessarily an "error".
//       return;
//     }
//
//     throw error;
//   }
// };

// TODO: for use when we have TypeScript.
// interface Commission {
//   title: string;
//   projectCount: number;
//   creationDate: number;
//   workingGroup: string;
//   type: string;
//   status: string;
//   owner: string;
// }
// const CommissionsList: React.FC<{ commissions: Commission[] }> = (props) => {

const tableHeaderTitles = [
  "Title",
  "Projects",
  "Created",
  "Group",
  "Type",
  "Status",
  "Owner",
];

const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
  },
});

const CommissionsList = (props) => {
  const [commissions, setCommissions] = useState([]);
  const [workingGroups, setWorkingGroups] = useState(null);

  // TODO: for use later?
  // const [uid, setUid] = useState(null);
  // const [isAdmin, setIsAdmin] = useState(false);
  // const [filterEnabled, setFilterEnabled] = useState(false);
  // useEffect(async () => {
  //   try {
  //     await loadDependencies({
  //       setIsAdmin,
  //       setUid,
  //     });
  //   } catch (error) {
  //     console.log(error);
  //   }
  // }, []);

  useEffect(() => {
    getCommissionsOnPage({
      setCommissions,
      setWorkingGroups,
    }).catch((error) => {
      console.error(error);
    });
  }, []);

  const classes = useStyles();

  return (
    <>
      <Link to={"/commission/new"}>New</Link>
      <TableContainer component={Paper}>
        <Table className={classes.table}>
          <TableHead>
            <TableRow>
              {tableHeaderTitles.map((title) => (
                <TableCell key={title}>{title}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {commissions.map(
              ({ id, title, created: dateCreated, workingGroupId, status }) => (
                <TableRow key={id}>
                  <TableCell>{title}</TableCell>
                  <TableCell>TODO: Project count</TableCell>
                  <TableCell>{dateCreated}</TableCell>
                  <TableCell>{workingGroups.get(workingGroupId)}</TableCell>
                  <TableCell>TODO: Type</TableCell>
                  <TableCell>{status}</TableCell>
                  <TableCell>TODO: Owner</TableCell>
                </TableRow>
              )
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
};

export default CommissionsList;
