import React, { useEffect, useState } from "react";
import { Typography } from "@material-ui/core";

interface SizeFormatterProps {
  bytes?: number | string;
}

const SizeFormatter: React.FC<SizeFormatterProps> = (props) => {
  const [string, setString] = useState("");

  const suffixes = ["bytes", "Kb", "Mb", "Gb", "Tb"];
  useEffect(() => {
    if (props.bytes || props.bytes == 0) {
      try {
        let value =
          typeof props.bytes == "string" ? parseInt(props.bytes) : props.bytes;
        for (let i = 0; i < suffixes.length; i++) {
          if (value < 1024 || i == suffixes.length - 1) {
            setString(`${value.toFixed(2)} ${suffixes[i]}`);
            return;
          } else {
            value = value / 1024;
          }
        }
      } catch (err) {
        console.log("Could not parse bytes value '", props.bytes, "': ", err);
      }
    }
  }, [props.bytes]);

  if (string == "0.00 bytes") {
    return <>0 bytes</>;
  }
  return <>{string}</>;
};

export default SizeFormatter;
