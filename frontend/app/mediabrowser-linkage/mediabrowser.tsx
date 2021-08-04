import React, { useEffect, useState } from "react";
import { SystemNotifcationKind, SystemNotification } from "pluto-headers";

interface ConfigFileData {
  vidispineBaseUrl: string;
}

const MediabrowserContext = React.createContext<ConfigFileData | undefined>(
  undefined
);

/**
 * context provider that loads in the mediabrowser configuration and allows it to be accessed by subcomponents
 * @param props
 * @constructor
 */
const MediabrowserContextProvider: React.FC = (props) => {
  const [baseUrl, setBaseUrl] = useState<string | undefined>(undefined);

  async function loadMediabrowserConfiguration() {
    const configResponse = await fetch("/config/config.json");

    switch (configResponse.status) {
      case 200:
        const content = await configResponse.json();
        setBaseUrl(content.vidispineBaseUrl);
        break;
      case 502 | 503 | 504:
        SystemNotification.open(
          SystemNotifcationKind.Warning,
          "pluto-mediabrowser is not responding"
        );
        break;
      default:
        const errContent = await configResponse.text();
        console.error(
          "pluto-mediabrowser responded with ",
          configResponse.status,
          ": ",
          errContent
        );
        SystemNotification.open(
          SystemNotifcationKind.Error,
          "pluto-mediabrowser is misconfigured, see browser console"
        );
        break;
    }
  }

  useEffect(() => {
    loadMediabrowserConfiguration().catch((err) => {
      console.error("Could not load media browser configuration: ", err);
    });
  }, []);

  return (
    <MediabrowserContext.Provider
      value={baseUrl ? { vidispineBaseUrl: baseUrl } : undefined}
    >
      {props.children}
    </MediabrowserContext.Provider>
  );
};

export type { ConfigFileData };
export { MediabrowserContextProvider, MediabrowserContext };
