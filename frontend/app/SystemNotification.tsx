import React, { useState } from "react";
import { Snackbar, SnackbarContent } from "@material-ui/core";
import { RouteComponentProps } from "react-router-dom";

export enum SystemNotificationKind {
  Success = "success",
  Error = "error",
}

let openSystemNotification: (
  kind: SystemNotificationKind,
  message: string
) => void;

const SystemNotification: React.FC<RouteComponentProps> & {
  open: (kind: SystemNotificationKind, message: string) => void;
} = () => {
  const autoHideDuration = 4000;
  const successColor = "#4caf50";
  const errorColor = "#f44336";

  const [open, setOpen] = useState<boolean>(false);
  const [message, setMessage] = useState<string>("");
  const [kind, setKind] = useState<SystemNotificationKind>(
    SystemNotificationKind.Success
  );

  const close = (): void => {
    setOpen(false);
  };

  openSystemNotification = (kind: SystemNotificationKind, message: string) => {
    setOpen(true);
    setMessage(message);
    setKind(kind);
  };

  return (
    <Snackbar
      open={open}
      autoHideDuration={autoHideDuration}
      onClose={close}
      anchorOrigin={{ vertical: "top", horizontal: "right" }}
    >
      <SnackbarContent
        style={{
          backgroundColor:
            kind === SystemNotificationKind.Success ? successColor : errorColor,
        }}
        message={<span id="client-snackbar">{message}</span>}
      />
    </Snackbar>
  );
};

SystemNotification.open = (kind: SystemNotificationKind, message: string) =>
  openSystemNotification(kind, message);

export default SystemNotification;
