import React from "react";
import PropTypes from "prop-types";

class NotLoggedIn extends React.Component {
  static propTypes = {
    timeOut: PropTypes.number.isRequired,
  };

  constructor(props) {
    super(props);

    this.mounted = false;

    this.state = {
      timeRemaining: props.timeOut,
      timerId: null,
    };

    this.tick = this.tick.bind(this);
  }

  tick() {
    if (!this.mounted) {
      return;
    }

    this.setState({ timeRemaining: this.state.timeRemaining - 1 });
  }

  componentWillUnmount() {
    this.mounted = false;
    if (this.state.timerId) window.clearInterval(this.state.timerId);
  }

  componentDidMount() {
    this.setState({
      timeRemaining: this.props.timeOut,
      timerId: window.setInterval(this.tick, 1000),
    });

    this.mounted = true;
  }

  render() {
    //deliberately done like this not with <Redirect> so we get sent back to pluto-start/
    //TODO: once refresh is implemented in pluto-start we should jump to a refresh location
    if (this.state.timeRemaining < 1) window.location.assign("/");

    return (
      <div className="inline-dialog">
        <h2 className="inline-dialog-title">Not logged in</h2>
        <p className="inline-dialog-content centered">
          You are not currently logged in as anybody. Redirecting to login page
          in {this.state.timeRemaining} seconds
        </p>
      </div>
    );
  }
}

export default NotLoggedIn;
