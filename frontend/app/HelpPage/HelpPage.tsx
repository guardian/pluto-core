import React from "react";
import { RouteComponentProps } from "react-router-dom";
import { Paper, Typography } from "@material-ui/core";
import { Helmet } from "react-helmet";
import { useGuardianStyles } from "~/misc/utils";

const HelpPage: React.FC<RouteComponentProps> = (props) => {
  const classes = useGuardianStyles();

  return (
    <>
      <Helmet>
        <title>Pluto - Help</title>
      </Helmet>
      <Paper elevation={3} style={{ padding: "40px" }}>
        <Typography style={{ fontSize: "2em", fontWeight: "bold" }}>
          Help
        </Typography>
        <br />
        <br />
        <a
          href="https://docs.google.com/document/d/1QG9mOu_HDBoGqQs7Dly0sxifk4w9vaJiDiWdi3Uk1a8"
          target="_blank"
        >
          Pluto Guide
        </a>
        <br />
        <br />
        <a
          href="https://sites.google.com/guardian.co.uk/multimedia/"
          target="_blank"
        >
          Multimedia Production Guides
        </a>
        <br />
        <br />
        <a
          href="https://mail.google.com/mail/?view=cm&fs=1&to=multimediatech@theguardian.com&su=Pluto%20Feedback"
          target="_blank"
        >
          E-mail multimediatech@theguardian.com
        </a>
      </Paper>
    </>
  );
};

export default HelpPage;
