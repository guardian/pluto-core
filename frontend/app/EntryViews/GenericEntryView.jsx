import React from "react";
import axios from "axios";
import PropTypes from "prop-types";

class GenericEntryView extends React.Component {
  static propTypes = {
    entryId: PropTypes.number,
    hide: PropTypes.bool,
  };

  constructor(props) {
    super(props);

    this.mounted = false;

    this.endpoint = "/unknown";

    this.state = {
      loading: false,
      content: {},
    };
  }

  loadData() {
    if (!this.mounted || typeof this.props.entryId !== "number") {
      return;
    }

    this.setState({ loading: true }, () => {
      axios
        .get(`${this.endpoint}/${this.props.entryId}`)
        .then((response) => {
          if (this.mounted) {
            this.setState({ content: response.data.result, loading: false });
          }
        })
        .catch((error) => this.setState({ lastError: error, loading: false }));
    });
  }

  componentDidMount() {
    this.mounted = true;
    if (this.props.entryId) this.loadData();
  }

  componentDidUpdate(oldProps, oldState) {
    if (oldProps.entryId !== this.props.entryId) this.loadData();
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  render() {
    if (this.props.hide) return <span />;
    if (this.state.content) return <span>{this.state.content.name}</span>;
    else
      return (
        <span>
          <i>(none)</i>
        </span>
      );
  }
}

export default GenericEntryView;
