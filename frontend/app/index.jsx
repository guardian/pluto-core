import React from "react";
import { render } from "react-dom";
import { BrowserRouter, Link, Route, Switch } from "react-router-dom";
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
import TitleEditComponent from "./multistep/projectcreate/ProjectEntryEditComponent.jsx";

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
import CommissionsList from "./CommissionsList.jsx";
import CommissionCreateMultistep from "./multistep/CommissionCreateMultistep.jsx";
import { loadInSigningKey, validateAndDecode } from "./JwtHelpers";
import WorkingGroups from "./WorkingGroups/WorkingGroups.tsx";
import WorkingGroup from "./WorkingGroups/WorkingGroup.tsx";

import "./styles/app.css";

library.add(faSearch);

const theme = createMuiTheme({
  typography: {
    fontFamily: [
      "Raleway",
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
  const token = window.sessionStorage.getItem("pluto:access-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;

  return config;
});

class App extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isLoggedIn: false,
      currentUsername: "",
      isAdmin: false,
      loading: false,
    };

    this.onLoggedIn = this.onLoggedIn.bind(this);
    this.onLoggedOut = this.onLoggedOut.bind(this);
    axios
      .get("/system/publicdsn")
      .then((response) => {
        Raven.config(response.data.publicDsn).install();
        console.log("Sentry initialised for " + response.data.publicDsn);
      })
      .catch((error) => {
        console.error("Could not intialise sentry", error);
      });
  }

  setStatePromise(newState) {
    return new Promise((resolve, reject) =>
      this.setState(newState, () => resolve())
    );
  }

  checkLogin() {
    // await this.setStatePromise({ loading: true });
    // try {
    //   const signingKey = await loadInSigningKey();
    //   const token = window.sessionStorage.getItem("pluto:access-token");
    //   if (token) {
    //     const decoded = await validateAndDecode(token, signingKey, undefined);
    //     return this.setStatePromise({
    //       isLoggedIn: true,
    //       loading: false,
    //       currentUsername: decoded.username,
    //       isAdmin: true, //FIXME: temporary state so we can see stuff, need to get hold of the server-side config for adminClaim
    //     });
    //   } else {
    //     return this.setStatePromise({
    //       isLoggedIn: false,
    //       loading: false,
    //       currentUsername: "",
    //       isAdmin: false,
    //     });
    //   }
    // } catch (err) {
    //   console.error("Could not validate login: ", err);
    //   return this.setStatePromise({
    //     loading: false,
    //     isAdmin: false,
    //     currentUsername: "",
    //     isLoggedIn: false,
    //   });
    // }
    this.setState({ loading: true, haveChecked: true }, () =>
      axios
        .get("/api/isLoggedIn")
        .then((response) => {
          //200 response means we are logged in
          this.setState({
            isLoggedIn: true,
            loading: false,
            currentUsername: response.data.uid,
            isAdmin: response.data.isAdmin,
          });
        })
        .catch((error) => {
          this.setState({
            isLoggedIn: false,
            loading: false,
            currentUsername: "",
          });
        })
    );
  }

  componentWillMount() {
    this.checkLogin();
  }

  onLoggedIn(userid, isAdmin) {
    console.log("Logged in as " + userid);
    console.log("Is an admin? " + isAdmin);

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

  /*show left menu if logged in*/
  maybeLeftMenu() {
    if (this.state.isLoggedIn) {
      return (
        <ul className="leftmenu">
          <li>
            <Link to="/">Home</Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/storage/">Storages...</Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/type/">Project Types...</Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/template/">Project Templates...</Link>
          </li>
          <li>
            <Link to="/commission/">Commissions...</Link>
          </li>
          <li>
            <Link to="/working-group/">Working Groups</Link>
          </li>
          <li>
            <Link to={this.state.isAdmin ? "/project/" : "/project/?mine"}>
              Projects...
            </Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/validate/project">Validate projectfiles...</Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/postrun/">Postrun Actions...</Link>
          </li>
          <li>
            <Link to={this.state.isAdmin ? "/file/" : "/file/?mine"}>
              Files...
            </Link>
          </li>
          <li style={{ display: this.state.isAdmin ? "inherit" : "none" }}>
            <Link to="/defaults/">Server defaults...</Link>
          </li>
        </ul>
      );
    } else {
      return (
        <ul className="leftmenu">
          <li>
            <i>Not logged in</i>
          </li>
        </ul>
      );
    }
  }

  render() {
    console.debug(
      "loading: ",
      this.state.loading,
      "isLoggedIn",
      this.state.isLoggedIn,
      "path",
      window.location.pathname
    );

    if (
      !this.state.loading &&
      !this.state.isLoggedIn &&
      window.location.pathname !== "/"
    ) {
      console.log("not logged in, redirecting to route");
      return <NotLoggedIn timeOut={5} />;
    }

    return (
      <ThemeProvider theme={theme}>
        <div className="app">
          <div id="leftmenu" className="leftmenu">
            {this.maybeLeftMenu()}
          </div>
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
              <Route path="/project/:itemid" component={TitleEditComponent} />
              <Route path="/project/" component={ProjectEntryList} />
              <Route
                path="/commission/:itemid"
                component={(props) => (
                  <CommissionCreateMultistep
                    match={props.match}
                    userName={this.state.currentUsername}
                  />
                )}
              />
              <Route path="/commission/" component={CommissionsList} />
              <Route
                path="/working-group/:itemid"
                component={(props) => <WorkingGroup {...props} />}
              />
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
                component={() => (
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
