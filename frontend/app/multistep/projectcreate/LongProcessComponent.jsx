import React from "react";
import PropTypes from "prop-types";
import { CircularProgress } from "@material-ui/core";

class LongProcessComponent extends React.Component {
  static propTypes = {
    inProgress: PropTypes.bool.isRequired,
    expectedDuration: PropTypes.number.isRequired,
    operationName: PropTypes.string,
  };

  constructor(props) {
    super(props);

    this.mounted = false;

    this.state = {
      currentTime: 0,
      timerId: null,
    };
  }

  componentDidMount() {
    this.mounted = true;
  }

  componentDidUpdate(oldProps) {
    if (oldProps.inProgress === false && this.props.inProgress === true) {
      this.startTimer();
    } else if (
      oldProps.inProgress === true &&
      this.props.inProgress === false
    ) {
      this.stopTimer();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
    this.stopTimer();
  }

  startTimer() {
    if (!this.mounted) {
      return;
    }

    this.setState({
      timerId: window.setInterval(() => {
        this.setState({ currentTime: this.state.currentTime + 1 });
      }, 1000),
    });
  }

  stopTimer() {
    if (this.state.timerId) {
      window.clearInterval(this.state.timerId);
      this.setState({ timerId: null });
    }
  }

  render() {
    return (
      <div style={{ display: this.props.inProgress ? "inline" : "none" }}>
        <CircularProgress style={{ height: "20px", width: "20px" }} />
        <span style={{ marginLeft: "0.5em" }}>
          {this.props.operationName} in progress. This may take{" "}
          {this.props.expectedDuration} seconds or more, please wait...
          <br />
          You have been waiting for {this.state.currentTime} seconds so far.
        </span>
      </div>
    );
  }
}

export default LongProcessComponent;
