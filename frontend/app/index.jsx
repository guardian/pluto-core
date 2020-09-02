import React from "react";
import { render } from "react-dom";
import { BrowserRouter, Route, Switch } from "react-router-dom";
import { ThemeProvider, createMuiTheme } from "@material-ui/core";
import StorageListComponent from "./StorageComponent.jsx";

import RootComponent from "./RootComponent.jsx";

import ProjectTypeMultistep from "./multistep/ProjectTypeMultistep.jsx";
import FileEntryList from "./FileEntryList.jsx";
import ProjectEntryList from "./ProjectEntryList/ProjectEntryList.tsx";
import ProjectTypeList from "./ProjectTypeList.jsx";

import FileDeleteComponent from "./delete/FileDeleteComponent.jsx";

import StorageMultistep from "./multistep/StorageMultistep.jsx";
import StorageDeleteComponent from "./delete/StorageDeleteComponent.jsx";

import TypeDeleteComponent from "./delete/TypeDeleteComponent.jsx";

import ProjectTemplateIndex from "./ProjectTemplateIndex.jsx";
import ProjectTemplateMultistep from "./multistep/ProjectTemplateMultistep.jsx";
import ProjectTemplateDeleteComponent from "./delete/ProjectTemplateDeleteComponent.jsx";

import ProjectDeleteComponent from "./delete/ProjectEntryDeleteComponent.jsx";

import ProjectCreateMultistep from "./multistep/ProjectCreateMultistep.jsx";
import ProjectEntryEditComponent from "./ProjectEntryList/ProjectEntryEditComponent.tsx";

import PostrunList from "./PostrunList.jsx";
import PostrunMultistep from "./multistep/PostrunMultistep.jsx";
import PostrunDeleteComponent from "./delete/PostrunDeleteComponent.jsx";

import ServerDefaults from "./ServerDefaults.jsx";

import NotLoggedIn from "./NotLoggedIn.jsx";

import axios from "axios";

import { config, library } from "@fortawesome/fontawesome-svg-core";
import { faSearch } from "@fortawesome/free-solid-svg-icons";

window.React = require("react");

import Raven from "raven-js";
import ProjectValidationView from "./ProjectValidationView.jsx";
import CommissionsList from "./CommissionsList/CommissionsList.tsx";
import CommissionCreateMultistep from "./multistep/CommissionCreateMultistep.jsx";
import WorkingGroups from "./WorkingGroups/WorkingGroups.tsx";
import WorkingGroup from "./WorkingGroups/WorkingGroup.tsx";
import SystemNotification from "./SystemNotification.tsx";
import { Header, AppSwitcher, handleUnauthorized } from "pluto-headers";

import "./styles/app.css";

library.add(faSearch);

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

//this is set in the index.scala.html template file and gives us the value of deployment-root from the server config
axios.defaults.baseURL = deploymentRootPath;
axios.interceptors.request.use(function (config) {
  const token = window.localStorage.getItem("pluto:access-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;

  return config;
});

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
    let data;
    try {
      const response = await fetch("/meta/oauth/config.json");

      if (response.status === 200) {
        data = await response.json();
        this.setState({ plutoConfig: data });
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
        isAdmin: loginData ?? loginData[data.adminClaimName],
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
    if (
      !this.state.loading &&
      !this.state.isLoggedIn &&
      window.location.pathname !== "/"
    ) {
      console.log("not logged in, redirecting to route");
      return <NotLoggedIn tokenExpired={this.state.tokenExpired} timeOut={5} />;
    }

    return (
      <ThemeProvider theme={theme}>
        <div className="app">
          <Header></Header>
          <AppSwitcher onLoginValid={this.onLoginValid}></AppSwitcher>

          <div id="mainbody" className="mainbody">
            <Switch>
              <Route
                path="/storage/:itemid/delete"
                component={StorageDeleteComponent}
              />
              <Route path="/storage/:itemid" component={StorageMultistep} />
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
              <Route path="/project/new" component={ProjectCreateMultistep} />
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
                  <ProjectEntryList
                    match={props.match}
                    userName={this.state.currentUsername}
                  />
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
                render={() => (
                  <RootComponent
                    onLoggedOut={this.onLoggedOut}
                    onLoggedIn={this.onLoggedIn}
                    currentUsername={this.state.currentUsername}
                    isLoggedIn={this.state.isLoggedIn}
                    isAdmin={this.state.isAdmin}
                  />
                )}
              />
            </Switch>
          </div>
        </div>
        <SystemNotification></SystemNotification>
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
