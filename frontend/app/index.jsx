import React from "react";
import { render } from "react-dom";
import { BrowserRouter, Redirect, Route, Switch } from "react-router-dom";
import { ThemeProvider, createMuiTheme } from "@material-ui/core";
import StorageListComponent from "./StorageComponent.jsx";

import ProjectTypeMultistep from "./multistep/ProjectTypeMultistep.jsx";
import FileEntryList from "./FileEntryList.jsx";
import ProjectEntryList from "./ProjectEntryList/ProjectEntryList.tsx";
import ProjectTypeList from "./ProjectTypeList.jsx";

import FileDeleteComponent from "./delete/FileDeleteComponent.jsx";

import StorageContextComponent from "./delete/StorageDeleteComponent";

import TypeDeleteComponent from "./delete/TypeDeleteComponent.jsx";

import ProjectTemplateIndex from "./ProjectTemplateIndex.jsx";
import ProjectTemplateMultistep from "./multistep/ProjectTemplateMultistep.jsx";
import ProjectTemplateDeleteComponent from "./delete/ProjectTemplateDeleteComponent.jsx";

import ProjectDeleteComponent from "./delete/ProjectEntryDeleteComponent.jsx";

import ProjectEntryEditComponent from "./ProjectEntryList/ProjectEntryEditComponent.tsx";

import PostrunList from "./PostrunList";
import PostrunMultistep from "./multistep/PostrunMultistep.jsx";
import PostrunDeleteComponent from "./delete/PostrunDeleteComponent.jsx";

import ServerDefaults from "./ServerDefaults.jsx";
import axios from "axios";

import { config, library } from "@fortawesome/fontawesome-svg-core";
import { faSearch } from "@fortawesome/free-solid-svg-icons";
import { UserContextProvider } from "./UserContext";

import Raven from "raven-js";
import ProjectValidationView from "./ProjectValidationView.jsx";
import CommissionsList from "./CommissionsList/CommissionsList.tsx";
import CommissionCreateMultistep from "./multistep/CommissionCreateMultistep.jsx";
import WorkingGroups from "./WorkingGroups/WorkingGroups.tsx";
import WorkingGroup from "./WorkingGroups/WorkingGroup.tsx";
import { SystemNotification } from "pluto-headers";
import { Header, AppSwitcher, handleUnauthorized } from "pluto-headers";

import "./styles/app.css";
import CommissionEntryEditComponent from "./CommissionsList/CommissionEntryEditComponent";
import ProjectCreateMultistepNew from "./multistep/ProjectCreateMultistepNew";
import StorageMultistepNew from "./multistep/StorageMultistepNew";

library.add(faSearch);

window.React = require("react");

const theme = createMuiTheme({
  typography: {
    fontFamily: [
      "sans-serif",
      '"Helvetica Neue"',
      "Helvetica",
      "Arial",
      "sans-serif",
    ].join(","),
    fontWeight: 400,
  },
});

axios.interceptors.request.use(function (config) {
  const token = window.localStorage.getItem("pluto:access-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;

  // this is set in the index.scala.html template file and gives us the value of deployment-root from the server config
  // Only apply deployment root when url begins with /api
  if (config.url.startsWith("/api")) {
    config.baseURL = deploymentRootPath;
  }

  return config;
});

function parseBool(str) {
  return /^true$/i.test(str);
}

class App extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isLoggedIn: false,
      tokenExpired: false,
      currentUsername: "",
      isAdmin: false,
      loading: false,
      plutoConfig: {},
    };

    this.onLoggedIn = this.onLoggedIn.bind(this);
    this.onLoggedOut = this.onLoggedOut.bind(this);
    this.handleUnauthorizedFailed = this.handleUnauthorizedFailed.bind(this);
    this.onLoginValid = this.onLoginValid.bind(this);

    axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        handleUnauthorized(
          this.state.plutoConfig,
          error,
          this.handleUnauthorizedFailed
        );

        return Promise.reject(error);
      }
    );

    axios
      .get("/system/publicdsn")
      .then((response) => {
        Raven.config(response.data.publicDsn).install();
        console.log("Sentry initialised for", response.data.publicDsn);
      })
      .catch((error) => {
        console.error("Could not intialise sentry", error);
      });
  }

  handleUnauthorizedFailed() {
    // Redirect to login screen
    this.setState({
      tokenExpired: true,
      isLoggedIn: false,
      loading: false,
      currentUsername: "",
    });
  }

  setStatePromise(newState) {
    return new Promise((resolve, reject) =>
      this.setState(newState, () => resolve())
    );
  }

  componentDidMount() {
    this.setState({
      loading: true,
      haveChecked: true,
    });
  }

  async onLoginValid(valid, loginData) {
    // Fetch the oauth config
    let oAuthConfig;
    try {
      const response = await fetch("/meta/oauth/config.json");

      if (response.status === 200) {
        oAuthConfig = await response.json();
        this.setState({ plutoConfig: oAuthConfig });
      }
    } catch (error) {
      console.error(error);
    }

    if (valid) {
      return this.setState({
        isLoggedIn: true,
        loading: false,
        currentUsername: loginData
          ? loginData.preferred_username ?? loginData.username
          : "",
        isAdmin: loginData
          ? parseBool(loginData[oAuthConfig.adminClaimName])
          : false,
      });
    }

    this.setState({
      isLoggedIn: false,
      loading: false,
      currentUsername: "",
    });
  }

  onLoggedIn(userid, isAdmin) {
    console.log("Logged in as", userid);
    console.log(`${userid} ${isAdmin ? "is" : "is not"} an admin.`);

    this.setState(
      { currentUsername: userid, isAdmin: isAdmin, isLoggedIn: true },
      () => {
        if (!isAdmin) {
          window.location.href = `${deploymentRootPath}/project/?mine`;
        }
      }
    );
  }

  onLoggedOut() {
    this.setState({ currentUsername: "", isLoggedIn: false });
  }

  render() {
    return (
      <ThemeProvider theme={theme}>
        <UserContextProvider
          value={
            this.state.isLoggedIn
              ? {
                  userName: this.state.currentUsername,
                  isAdmin: this.state.isAdmin,
                }
              : null
          }
        >
          <div className="app">
            <Header />
            <AppSwitcher onLoginValid={this.onLoginValid} />

            <div id="mainbody" className="mainbody">
              <Switch>
                <Route
                  path="/storage/:itemid/delete"
                  component={StorageContextComponent}
                />
                <Route
                  path="/storage/:itemid"
                  component={StorageMultistepNew}
                />
                <Route path="/storage/" component={StorageListComponent} />
                <Route
                  path="/template/:itemid/delete"
                  component={ProjectTemplateDeleteComponent}
                />
                <Route
                  path="/template/:itemid"
                  component={ProjectTemplateMultistep}
                />
                <Route path="/template/" component={ProjectTemplateIndex} />
                <Route
                  path="/file/:itemid/delete"
                  component={FileDeleteComponent}
                />
                <Route path="/file/:itemid" component={FileEntryList} />
                <Route path="/file/" component={FileEntryList} />
                <Route
                  path="/type/:itemid/delete"
                  component={TypeDeleteComponent}
                />
                <Route path="/type/:itemid" component={ProjectTypeMultistep} />
                <Route path="/type/" component={ProjectTypeList} />
                <Route
                  path="/project/new"
                  render={(props) => (
                    <ProjectCreateMultistepNew
                      isAdmin={this.state.isAdmin}
                      {...props}
                    />
                  )}
                />
                <Route
                  path="/project/:itemid/delete"
                  component={ProjectDeleteComponent}
                />
                <Route
                  path="/project/:itemid"
                  component={ProjectEntryEditComponent}
                />
                <Route path="/project/" component={ProjectEntryList} />
                <Route
                  path="/commission/new"
                  render={(props) => (
                    <CommissionCreateMultistep
                      match={props.match}
                      userName={this.state.currentUsername}
                    />
                  )}
                />
                <Route
                  path="/commission/:commissionId"
                  render={(props) => (
                    <CommissionEntryEditComponent {...props} />
                  )}
                />
                <Route path="/commission/" component={CommissionsList} />
                <Route path="/working-group/:itemid" component={WorkingGroup} />
                <Route path="/working-group/" component={WorkingGroups} />
                <Route
                  path="/validate/project"
                  component={ProjectValidationView}
                />
                <Route
                  path="/postrun/:itemid/delete"
                  component={PostrunDeleteComponent}
                />
                <Route path="/postrun/:itemid" component={PostrunMultistep} />
                <Route path="/postrun/" component={PostrunList} />
                <Route path="/defaults/" component={ServerDefaults} />
                <Route
                  exact
                  path="/"
                  render={() => <Redirect to="/project/" />}
                />
              </Switch>
            </div>
          </div>
          <SystemNotification />
        </UserContextProvider>
      </ThemeProvider>
    );
  }
}

render(
  <BrowserRouter basename={deploymentRootPath}>
    <App />
  </BrowserRouter>,
  document.getElementById("app")
);
