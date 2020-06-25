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
import { getCommissionsOnPage } from "./helpers";

const tableHeaderTitles = [
  "Title",
  "Projects",
  "Created",
  "Group",
  "Status",
  "Owner",
];

const useStyles = makeStyles({
  table: {
    maxWidth: "100%",
  },
});

const CommissionsList: React.FC = () => {
  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [workingGroups, setWorkingGroups] = useState<Map<number, string>>(
    new Map()
  );

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
      setCommissions: (commissions) => setCommissions(commissions),
      setWorkingGroups: (workingGroups) => setWorkingGroups(workingGroups),
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
              ({ id, title, created, workingGroupId, status, owner }) => (
                <TableRow key={id}>
                  <TableCell>{title}</TableCell>
                  <TableCell>TODO: Project count</TableCell>
                  <TableCell>{new Date(created).toLocaleString()}</TableCell>
                  <TableCell>
                    {workingGroups.get(workingGroupId) ?? "<Unknown>"}
                  </TableCell>
                  <TableCell>{status}</TableCell>
                  <TableCell>{owner}</TableCell>
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
