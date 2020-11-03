import React from "react";
import axios from "axios";
import CommonMultistepComponent from "./CommonMultistepComponent.jsx";

class MultistepComponentLoadsOnMount extends CommonMultistepComponent {
  /*mixin class to allow the loading of content on mount*/
  constructor(props) {
    super(props);
    this.endpoint = "/api/unknown";
  }

  componentWillMount() {
    if (this.props.currentEntry) {
      console.log(`Loading existing data for ${this.props.currentEntry}`);
      this.setState({ loading: true }, () => {
        axios
          .get(this.endpoint + "/" + this.props.currentEntry)
          .then((response) =>
            this.setState({ loading: false }, () =>
              this.receivedExistingObject(response.data)
            )
          )
          .catch((error) => {
            console.error("Could not load existing data: ", error);
            this.setState({ loading: false, error: error });
          });
      });
    } else {
      console.log("No current entry to load");
    }
  }

  receivedExistingObject(object) {
    //override this callback in your subclass
  }
}

export default MultistepComponentLoadsOnMount;
